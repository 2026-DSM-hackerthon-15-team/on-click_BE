#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const DEFAULT_BASE_URL = "https://cowtree28-server.duckdns.org";
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

const config = {
  baseUrl: (process.env.BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, ""),
  accountId: process.env.ACCOUNT_ID,
  password: "namwoo28!",
  storeId: 10,
  days: positiveInteger("DAYS", 30),
  salesPerDay: positiveInteger("SALES_PER_DAY", 50),
  concurrency: positiveInteger("CONCURRENCY", 5),
  cancelRate: numberInRange("CANCEL_RATE", 0.05, 0, 0.5),
  seedVersion: process.env.SEED_VERSION || "v1",
  manifestPath: process.env.MANIFEST_PATH,
  confirm: process.env.CONFIRM_SEED === "YES",
  dryRun: process.env.DRY_RUN === "true",
};

let accessToken = process.env.ACCESS_TOKEN;
let refreshPromise;

main().catch((error) => {
  console.error(`\n실패: ${error.message}`);
  process.exitCode = 1;
});

async function main() {
  if (!config.confirm && !config.dryRun) {
    throw new Error("실제 적재에는 CONFIRM_SEED=YES가 필요합니다.");
  }
  if (!accessToken && (!config.accountId || !config.password)) {
    throw new Error("ACCESS_TOKEN 또는 ACCOUNT_ID와 PASSWORD를 설정해야 합니다.");
  }

  await checkHealth();
  if (!accessToken) {
    await login();
  }

  const store = await resolveStore();
  const storeId = Number(store.id);
  const products = await ensureActiveProducts(store);
  const manifestPath = resolve(
    config.manifestPath || `.pos-seed/pos-${storeId}-${config.seedVersion}.json`,
  );
  const manifest = await loadOrCreateManifest(manifestPath, storeId, products);

  console.log(`서버: ${config.baseUrl}`);
  console.log(`매장 ID: ${storeId}`);
  console.log(`활성 상품: ${products.length}개`);
  console.log(`기간: ${manifest.startDate} ~ ${manifest.endDate}`);
  console.log(`판매: ${manifest.transactions.length}건 (취소 예정 ${manifest.cancelCount}건)`);
  console.log(`manifest: ${manifestPath}`);

  if (config.dryRun) {
    console.log("DRY_RUN=true: 서버에는 판매 데이터를 저장하지 않았습니다.");
    return;
  }

  const result = await createTransactions(storeId, manifest.transactions);
  await verifyTransactions(storeId, manifest.transactions);

  console.log("\nPOS 데이터 적재 완료");
  console.log(`신규 생성: ${result.created}건`);
  console.log(`기존 멱등 응답: ${result.existing}건`);
  console.log(`취소 상태 반영: ${result.cancelled}건`);
}

async function checkHealth() {
  const healthPath = "/actuator/health";
  for (let redirectCount = 0; redirectCount < 5; redirectCount += 1) {
    const healthUrl = `${config.baseUrl}${healthPath}`;
    let response;
    try {
      response = await fetchWithTimeout(healthUrl, {}, 30_000);
    } catch (error) {
      if (error.name === "AbortError") {
        throw new Error(`health 확인 시간 초과: ${healthUrl}`);
      }
      throw new Error(`health 연결 실패: ${healthUrl} - ${error.message}`, { cause: error });
    }

    if (isRedirect(response.status)) {
      const location = response.headers.get("location");
      if (!location) {
        throw new Error(`health 요청이 Location 없이 HTTP ${response.status}를 반환했습니다.`);
      }
      const target = new URL(location, healthUrl);
      if (!target.pathname.endsWith(healthPath)) {
        throw new Error(
          `health 요청이 예상하지 못한 주소로 이동합니다: HTTP ${response.status} -> ${target.href}`,
        );
      }
      const basePath = target.pathname.slice(0, -healthPath.length).replace(/\/+$/, "");
      config.baseUrl = `${target.origin}${basePath}`;
      console.log(`서버 리디렉션 반영: ${config.baseUrl}`);
      continue;
    }
    if (!response.ok) {
      throw new Error(`health 확인 실패: HTTP ${response.status}`);
    }
    return;
  }
  throw new Error("health 요청의 리디렉션 횟수가 너무 많습니다.");
}

async function login() {
  const response = await fetchJson("/auth/login", {
    method: "POST",
    body: {
      accountId: config.accountId,
      password: config.password,
    },
    authenticated: false,
    retryAuth: false,
  });
  if (!response.body?.accessToken) {
    throw new Error("로그인 응답에 accessToken이 없습니다.");
  }
  accessToken = response.body.accessToken;
}

async function refreshLogin() {
  if (!config.accountId || !config.password) {
    throw new Error("토큰이 만료되었습니다. ACCOUNT_ID와 PASSWORD로 다시 실행해 주세요.");
  }
  if (!refreshPromise) {
    refreshPromise = login().finally(() => {
      refreshPromise = undefined;
    });
  }
  await refreshPromise;
}

async function resolveStore() {
  const { body: stores } = await fetchJson("/stores");
  if (!Array.isArray(stores) || stores.length === 0) {
    throw new Error("계정에 등록된 매장이 없습니다.");
  }
  if (config.storeId) {
    if (!stores.some((store) => Number(store.id) === config.storeId)) {
      throw new Error(`STORE_ID=${config.storeId}는 로그인 계정 소유 매장이 아닙니다.`);
    }
    return stores.find((store) => Number(store.id) === config.storeId);
  }
  if (stores.length !== 1) {
    const candidates = stores.map((store) => `${store.id}:${store.name}`).join(", ");
    throw new Error(`매장이 여러 개입니다. STORE_ID를 지정해 주세요: ${candidates}`);
  }
  return stores[0];
}

async function ensureActiveProducts(store) {
  const storeId = Number(store.id);
  const { body } = await fetchJson(`/stores/${storeId}/products`);
  if (!Array.isArray(body)) {
    throw new Error("상품 목록 응답 형식이 올바르지 않습니다.");
  }
  const products = [...body];
  const existingNames = new Set(products.map((product) => normalizeProductName(product.name)));
  const missingCatalog = productCatalog(store.industry).filter(
    (product) => !existingNames.has(normalizeProductName(product.name)),
  );

  if (config.dryRun && missingCatalog.length > 0) {
    console.log(`상품 등록 예정: ${missingCatalog.length}개 (DRY_RUN으로 저장하지 않음)`);
  } else {
    for (const product of missingCatalog) {
      const { body: created } = await fetchJson(`/stores/${storeId}/products`, {
        method: "POST",
        body: product,
      });
      products.push(created);
      console.log(`상품 등록: ${created.name} (${created.price}원)`);
    }
  }

  const activeProducts = products
    .filter((product) => product.active)
    .map((product) => ({
      id: Number(product.id),
      name: product.name,
      price: Number(product.price),
    }))
    .sort((left, right) => left.id - right.id);

  if (activeProducts.length === 0) {
    const dryRunHint = config.dryRun
      ? " DRY_RUN을 해제하면 업종별 샘플 상품을 먼저 등록합니다."
      : "";
    throw new Error(`판매 가능한 활성 상품이 없습니다.${dryRunHint}`);
  }
  if (activeProducts.some((product) => !Number.isSafeInteger(product.id)
      || !Number.isSafeInteger(product.price)
      || product.price < 0)) {
    throw new Error("상품 ID 또는 가격이 안전한 정수 범위를 벗어났습니다.");
  }
  return activeProducts;
}

function productCatalog(industry) {
  const catalogs = {
    CAFE: [
      ["아메리카노", 4500],
      ["카페라떼", 5200],
      ["바닐라라떼", 5800],
      ["카라멜마키아토", 6000],
      ["콜드브루", 5500],
      ["레몬에이드", 6000],
      ["딸기스무디", 6500],
      ["초코케이크", 6500],
      ["크루아상", 3800],
      ["샌드위치", 7200],
    ],
    RESTAURANT: [
      ["김치찌개", 9000],
      ["된장찌개", 9000],
      ["제육볶음", 11000],
      ["비빔밥", 9500],
      ["불고기정식", 13000],
      ["냉면", 10000],
      ["만두", 6000],
      ["계란말이", 8000],
      ["공기밥", 1000],
      ["음료", 2000],
    ],
    RETAIL: [
      ["생수", 1000],
      ["탄산음료", 2000],
      ["스낵", 1800],
      ["컵라면", 2200],
      ["도시락", 5500],
      ["샌드위치", 3800],
      ["우유", 2500],
      ["아이스크림", 2000],
      ["휴지", 4500],
      ["세제", 8000],
    ],
    SERVICE: [
      ["기본 서비스", 20000],
      ["프리미엄 서비스", 35000],
      ["상담 30분", 15000],
      ["상담 60분", 28000],
      ["정기 이용권", 80000],
      ["일회 이용권", 12000],
      ["추가 옵션 A", 5000],
      ["추가 옵션 B", 8000],
      ["현장 서비스", 25000],
      ["예약 서비스", 30000],
    ],
    OTHER: [
      ["기본 상품", 5000],
      ["인기 상품", 7500],
      ["프리미엄 상품", 12000],
      ["세트 상품 A", 15000],
      ["세트 상품 B", 18000],
      ["추가 상품 A", 3000],
      ["추가 상품 B", 4500],
      ["시즌 상품", 9000],
      ["할인 상품", 6000],
      ["한정 상품", 20000],
    ],
  };
  return (catalogs[industry] || catalogs.OTHER).map(([name, price]) => ({ name, price }));
}

function normalizeProductName(name) {
  return String(name || "").trim().toLocaleLowerCase("ko-KR");
}

async function loadOrCreateManifest(path, storeId, products) {
  try {
    const existing = JSON.parse(await readFile(path, "utf8"));
    validateManifest(existing, storeId);
    return existing;
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw error;
    }
  }

  const manifest = buildManifest(storeId, products);
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(manifest, null, 2)}\n`, { flag: "wx" });
  return manifest;
}

function buildManifest(storeId, products) {
  const today = kstDateParts();
  const dates = Array.from({ length: config.days }, (_, index) =>
    addDays(today.date, index - config.days + 1));
  const transactions = [];
  const random = createRandom(hashString(`${storeId}:${config.seedVersion}`));

  for (const date of dates) {
    const weekend = isWeekend(date);
    for (let index = 0; index < config.salesPerDay; index += 1) {
      const soldAt = buildSoldAt(date, index, random, today);
      const maxLines = Math.min(products.length, weekend ? 4 : 3);
      const lineCount = 1 + Math.floor(random() * maxLines);
      const selected = weightedUniqueProducts(products, lineCount, random);
      const items = selected.map((product, lineIndex) => {
        const quantityLimit = weekend || random() > 0.7 ? 3 : 2;
        const quantity = 1 + Math.floor(random() * quantityLimit);
        const discount = pickDiscount(random());
        const paidAmount = roundToHundred(product.price * quantity * (1 - discount));
        return {
          lineNo: lineIndex + 1,
          productId: product.id,
          quantity,
          paidAmount,
        };
      });
      const sequence = String(index + 1).padStart(3, "0");
      transactions.push({
        clientTransactionId: `onclick-pos-seed-${config.seedVersion}-${date.replaceAll("-", "")}-${sequence}`,
        soldAt,
        items,
        cancel: date !== today.date && random() < config.cancelRate,
      });
    }
  }

  return {
    schemaVersion: 1,
    seedVersion: config.seedVersion,
    storeId,
    startDate: dates[0],
    endDate: dates.at(-1),
    days: config.days,
    salesPerDay: config.salesPerDay,
    productSnapshot: products,
    cancelCount: transactions.filter((transaction) => transaction.cancel).length,
    transactions,
  };
}

function validateManifest(manifest, storeId) {
  if (manifest?.schemaVersion !== 1
      || Number(manifest.storeId) !== storeId
      || !Array.isArray(manifest.transactions)
      || manifest.transactions.length === 0) {
    throw new Error("기존 POS manifest가 현재 매장 또는 스키마와 맞지 않습니다.");
  }
}

async function createTransactions(storeId, transactions) {
  const stats = { created: 0, existing: 0, cancelled: 0, processed: 0 };
  await mapLimit(transactions, config.concurrency, async (transaction) => {
    const { cancel, ...requestBody } = transaction;
    const response = await fetchJson(`/stores/${storeId}/sales/transactions`, {
      method: "POST",
      body: requestBody,
    });
    if (response.status === 201) {
      stats.created += 1;
    } else if (response.status === 200) {
      stats.existing += 1;
    } else {
      throw new Error(`판매 등록의 예상하지 못한 상태 코드: HTTP ${response.status}`);
    }

    const saleId = Number(response.body?.saleId);
    if (!Number.isSafeInteger(saleId)) {
      throw new Error(`${transaction.clientTransactionId} 응답에 saleId가 없습니다.`);
    }
    if (cancel) {
      await fetchJson(`/stores/${storeId}/sales/transactions/${saleId}/cancel`, {
        method: "POST",
      });
      stats.cancelled += 1;
    }

    stats.processed += 1;
    if (stats.processed % 50 === 0 || stats.processed === transactions.length) {
      console.log(`진행: ${stats.processed}/${transactions.length}`);
    }
  });
  return stats;
}

async function verifyTransactions(storeId, transactions) {
  const pendingIds = new Set(transactions.map((transaction) => transaction.clientTransactionId));
  let page = 0;
  let hasNext = true;

  while (hasNext && pendingIds.size > 0 && page < 100) {
    const response = await fetchJson(
      `/stores/${storeId}/sales/transactions?page=${page}&size=100&sortBy=saleId&sortDirection=desc`,
    );
    if (!Array.isArray(response.body?.content)) {
      throw new Error("판매 검증 응답 형식이 올바르지 않습니다.");
    }
    for (const sale of response.body.content) {
      pendingIds.delete(sale.clientTransactionId);
    }
    hasNext = Boolean(response.body.hasNext);
    page += 1;
  }

  if (pendingIds.size > 0) {
    throw new Error(
      `검증 실패: seed 거래 ${transactions.length}건 중 ${pendingIds.size}건을 조회하지 못했습니다.`,
    );
  }
  console.log(`검증: seed 거래 ${transactions.length}건 확인`);
}

async function fetchJson(path, options = {}) {
  const {
    method = "GET",
    body,
    authenticated = true,
    retryAuth = true,
  } = options;

  let authRetried = false;
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    if (authenticated) {
      headers.Authorization = `Bearer ${accessToken}`;
    }

    let response;
    try {
      response = await fetchWithTimeout(`${config.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      }, 20_000);
    } catch (error) {
      if (attempt === 5) {
        throw error;
      }
      await delay(backoff(attempt));
      continue;
    }

    const responseBody = await parseBody(response);
    if (isRedirect(response.status)) {
      const location = response.headers.get("location");
      const target = location
        ? new URL(location, `${config.baseUrl}${path}`).href
        : "Location 헤더 없음";
      throw new Error(
        `${method} ${path} 리디렉션 거부: HTTP ${response.status} -> ${target}. BASE_URL을 최종 주소로 설정해 주세요.`,
      );
    }
    if (response.ok) {
      return { status: response.status, body: responseBody };
    }
    if (response.status === 401 && authenticated && retryAuth && !authRetried) {
      authRetried = true;
      await refreshLogin();
      attempt -= 1;
      continue;
    }
    if ([408, 429].includes(response.status) || response.status >= 500) {
      if (attempt < 5) {
        await delay(backoff(attempt));
        continue;
      }
    }

    const detail = responseBody?.message || responseBody?.errorCode || JSON.stringify(responseBody);
    const allow = response.headers.get("allow");
    throw new Error(
      `${method} ${path} 실패: HTTP ${response.status}${allow ? ` (Allow: ${allow})` : ""}${detail ? ` - ${detail}` : ""}`,
    );
  }
  throw new Error(`${method} ${path} 재시도 횟수를 초과했습니다.`);
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, redirect: "manual", signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

function isRedirect(status) {
  return status >= 300 && status < 400;
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text.slice(0, 500);
  }
}

async function mapLimit(items, limit, worker) {
  let cursor = 0;
  async function run() {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      await worker(items[index]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, run));
}

function buildSoldAt(date, index, random, today) {
  const peak = random();
  let startMinute;
  let range;
  if (peak < 0.15) {
    startMinute = 9 * 60;
    range = 120;
  } else if (peak < 0.5) {
    startMinute = 11 * 60;
    range = 180;
  } else if (peak < 0.7) {
    startMinute = 14 * 60;
    range = 210;
  } else {
    startMinute = 17 * 60 + 30;
    range = 240;
  }
  let minute = startMinute + Math.floor(random() * range) + index % 7;
  if (date === today.date) {
    const latest = Math.max(0, today.hour * 60 + today.minute - 2);
    if (minute > latest) {
      minute = Math.floor(random() * (latest + 1));
    }
  }
  const hour = String(Math.floor(minute / 60)).padStart(2, "0");
  const minutePart = String(minute % 60).padStart(2, "0");
  const second = String((index * 17 + Math.floor(random() * 17)) % 60).padStart(2, "0");
  return `${date}T${hour}:${minutePart}:${second}`;
}

function weightedUniqueProducts(products, count, random) {
  const pool = [...products];
  const selected = [];
  while (selected.length < count && pool.length > 0) {
    const weighted = Math.pow(random(), 1.7);
    const index = Math.min(pool.length - 1, Math.floor(weighted * pool.length));
    selected.push(pool.splice(index, 1)[0]);
  }
  return selected;
}

function pickDiscount(value) {
  if (value < 0.08) return 0.1;
  if (value < 0.23) return 0.05;
  return 0;
}

function roundToHundred(value) {
  return Math.max(0, Math.round(value / 100) * 100);
}

function kstDateParts(now = new Date()) {
  const shifted = new Date(now.getTime() + KST_OFFSET_MS);
  return {
    date: shifted.toISOString().slice(0, 10),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
  };
}

function addDays(date, amount) {
  const shifted = new Date(`${date}T00:00:00Z`);
  shifted.setUTCDate(shifted.getUTCDate() + amount);
  return shifted.toISOString().slice(0, 10);
}

function isWeekend(date) {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay();
  return day === 0 || day === 6;
}

function createRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 0x1_0000_0000;
  };
}

function hashString(value) {
  let hash = 2166136261;
  for (const character of value) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function positiveInteger(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < 1) {
    throw new Error(`${name}은 1 이상의 정수여야 합니다.`);
  }
  return value;
}

function optionalPositiveInteger(name) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return undefined;
  return positiveInteger(name);
}

function numberInRange(name, fallback, minimum, maximum) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`${name}은 ${minimum} 이상 ${maximum} 이하여야 합니다.`);
  }
  return value;
}

function backoff(attempt) {
  return Math.min(5_000, 250 * (2 ** (attempt - 1)));
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}
