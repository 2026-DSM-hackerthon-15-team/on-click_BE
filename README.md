# ON:CLICK Backend

소상공인 매장의 계정·지점·상품·POS 판매 원장, 대시보드, AI 컨설팅·채팅·마케팅과 Instagram 계정 관리를 제공하는 Spring Boot API입니다.

## 기술 구성

- Java 21, Spring Boot 3.5, Gradle
- Spring MVC, Spring Data JPA, Spring Security JWT
- PostgreSQL, Flyway, Docker Compose
- AI provider별 mock 및 HTTP 구현
- 테스트: JUnit 5, H2 PostgreSQL 호환 모드

## 실행

저장소 루트의 `.env`에 환경변수를 준비한 뒤 PostgreSQL과 백엔드를 함께 실행합니다.

```bash
docker compose up -d --build
```

백엔드는 기본적으로 `http://localhost:8080`에서 실행됩니다. 전체 검증은 `./gradlew clean build`로 수행합니다.

필수 운영 설정은 다음과 같습니다.

- 데이터베이스: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- 보안: `JWT_SECRET`(32바이트 이상), `CORS_ALLOWED_ORIGINS`
- AI 실연동: `AI_PROVIDER=http`, `AI_BASE_URL`, timeout 및 기능별 path (`AI_CHAT_PATH`, `AI_DAILY_CONSULTING_PATH` 등)
- 미디어: `MEDIA_PUBLIC_BASE_URL`

업로드 이미지는 Docker의 `media-data` 볼륨에 보존됩니다. JPEG/PNG MIME·signature·실제 디코딩 가능 여부·해상도를 검증하며, 콘텐츠에 연결되지 않았거나 DB에 기록되지 않은 파일은 24시간 후 정리됩니다.

## 인증과 공통 규칙

- `POST /auth/signup`, `POST /auth/login`, `GET /public/media/{publicId}`는 인증 없이 호출합니다.
- 보호 API는 `Authorization: Bearer {accessToken}`을 요구합니다.
- Access Token은 1시간 유효하며 Refresh Token과 서버 세션은 사용하지 않습니다.
- 매장 데이터 API는 `/stores/{storeId}/...` 형식이며 JWT 사용자와 매장 소유자가 일치해야 합니다.
- 국내 매장만 지원하며, 저장·응답 타임스탬프는 한국 시간 `LocalDateTime`(`YYYY-MM-DDTHH:mm:ss[.SSSSSS]`)을 사용합니다.
- 오류 응답은 `{"errorCode":"...","message":"..."}` 형식입니다.

## API 요약

상세 계약은 [Notion API 명세서](https://app.notion.com/p/39c1b69f30c280179f3fef62d0c29bdb)에 정리되어 있습니다. AI 서버 API 명세서는 별도 계약으로 유지합니다.

- 계정: `GET/PATCH /me`, `PATCH /me/password`
- 매장: `GET/POST /stores`, `PATCH /stores/{storeId}`
- 상품: `GET/POST /stores/{storeId}/products`, 상품 정보·판매 상태 수정
- POS: 페이지네이션·정렬 판매 기록 조회, 판매 등록 및 전체 취소를 `/stores/{storeId}/sales/transactions` 아래 제공
- 대시보드: 오늘 요약, 시간대별 매출·방문자, 마감 매출·내일 방문자 예측
- 컨설팅: `POST/GET /stores/{storeId}/consultings`, 상세 조회
- 채팅: 채팅방 생성·목록·상세·삭제, 메시지 전송·polling
- 미디어: `POST /stores/{storeId}/media`, 미사용 이미지 삭제, 공개 이미지 조회
- 마케팅: AI 초안 생성·목록·상세·수정, `POST .../{marketingId}/approve`
- Instagram 계정: `PUT/GET /stores/{storeId}/instagram-account`

## 주요 동작

### POS와 대시보드

판매 등록 시 서버가 `saleId`를 발급합니다. POS 재전송 중복 방지가 필요하면 `clientTransactionId`를 전달합니다. 완료된 결제 한 건을 주문 한 건이자 방문자 한 명으로 집계하며 취소 결제는 모든 집계에서 제외합니다.

판매 기록은 `GET /stores/{storeId}/sales/transactions?page=0&size=20&sortBy=soldAt&sortDirection=desc`로 조회합니다. `size`는 최대 100이며, 정렬 필드는 `soldAt`, `createdAt`, `saleId`, `status`를 지원합니다.

### 일일 컨설팅

한국 시간과 매장 `closingTime`을 기준으로 마감된 영업일의 완료 매출을 집계합니다. 자동 생성 외에도 `POST /stores/{storeId}/consultings`로 특정 영업일의 생성을 요청할 수 있습니다. 최초 요청은 `202 Accepted`와 `PENDING` 컨설팅을 반환하며, 같은 `(storeId, targetDate)` 재요청은 기존 결과를 반환합니다. 실패 작업은 DB 상태를 이용해 최대 3회 재시도합니다.

### 채팅 polling

메시지 전송은 사용자 메시지와 `PENDING` AI 메시지를 저장한 뒤 `202 Accepted`를 반환합니다. 선택적 `clientMessageId`를 보내면 네트워크 재전송에도 같은 사용자·AI 메시지 쌍을 반환합니다. 커밋 후 비동기 worker가 AI 서버의 `POST /ai/chat`을 호출하고 결과를 같은 assistant 메시지에 반영합니다. 프론트는 응답받은 `assistantMessage.id`를 `afterId`로 polling해도 같은 메시지의 최신 `PENDING`, `COMPLETED`, `FAILED` 상태를 조회할 수 있습니다.

기본 `AI_PROVIDER=mock`에서는 외부 AI HTTP 요청을 보내지 않고 프로세스 내부 mock 응답을 사용합니다. 실제 AI 서버를 호출하려면 `AI_PROVIDER=http`과 `AI_BASE_URL`을 설정합니다. 별도의 내부 API Key 헤더는 사용하지 않습니다. 채팅은 `POST /ai/chat`, 일일 컨설팅은 `POST /ai/consultings/daily`, 마케팅 문구 생성은 `POST /ai/marketings/copy`를 호출합니다. 일일 컨설팅 요청은 `userId`, `storeId`, ISO 날짜 `targetDate`, `reportFormat=DAILY_V1`을 전송합니다. 마케팅 요청은 소유자 `userId`, 외부 HTTPS 이미지 URL 1~10개, 2000자 이하 초안과 선택적 태그·톤·추가 요청을 전송합니다. 요청 시작·성공 로그에는 path와 시도 횟수를, 실패 로그에는 본문을 제외한 path·upstream 상태·예외 종류를 기록합니다. 현재 AI 서버 계약은 동기 JSON 응답 방식이며, 메인 백엔드가 채팅·컨설팅 호출을 비동기 worker에서 처리해 결과를 저장한 뒤 프론트 polling에 노출합니다.

AI HTTP 기본 read timeout은 60초이고 408·429·5xx 및 연결/읽기 실패만 HTTP 계층에서 재시도합니다. 4xx 계약 거절과 잘못된 성공 응답은 작업을 즉시 실패 처리합니다. HTTP 2회 시도의 최악 실행시간보다 lease가 길도록 채팅·컨설팅 기본 lease는 4분이며, 운영에서 timeout 또는 시도 횟수를 늘릴 때 lease도 함께 늘려야 합니다.

### 마케팅과 Instagram 계정

1. 이미지를 `POST /stores/{storeId}/media`로 업로드합니다.
2. 반환된 `mediaId`로 AI 마케팅 초안을 생성합니다.
3. `DRAFT` 상태에서 본문·해시태그·이미지를 수정합니다.
4. 콘텐츠를 승인하면 `APPROVED` 상태로 확정됩니다.
5. 브라우저 MCP 등 외부 자동화가 승인 콘텐츠와 Instagram 계정 정보를 조회해 게시합니다.

매장별 Instagram 로그인 정보는 `PUT /stores/{storeId}/instagram-account`로 등록·교체합니다. 한 매장에는 계정 한 개만 연결되며, `GET /stores/{storeId}/instagram-account`는 해당 매장 소유자에게 ID와 비밀번호를 반환합니다. 브라우저 자동화를 위한 해커톤 구현이므로 비밀번호는 평문 저장되며 조회 응답에는 `Cache-Control: no-store`가 적용됩니다.

Meta OAuth, Graph API 게시, callback 및 access token 저장·갱신 기능은 사용하지 않습니다.

`AI_PROVIDER=http`에서 마케팅 문구 생성을 사용하려면 `MEDIA_PUBLIC_BASE_URL`을 AI 서버가 인터넷에서 조회할 수 있는 HTTPS 주소로 설정해야 합니다. 승인 API는 게시 작업을 예약하거나 AI 서버의 OAuth 게시 API를 호출하지 않습니다.

## 프론트 연동 변경사항

- 회원가입은 `storeId`를 받지 않고 `storeName`, `industry`, `roadAddress`, `closingTime`으로 최초 매장을 함께 생성하며, 서버가 발급한 `storeId`를 응답합니다.
- 매장 `industry`는 `RESTAURANT`, `CAFE`, `RETAIL`, `SERVICE`, `OTHER` 중 하나이며, 생략하면 `OTHER`로 저장합니다. `roadAddress`는 선택 입력입니다.
- 국내 전용 API이므로 매장 `timeZone` 필드는 사용하지 않습니다. `closingTime`은 `HH:mm`, 판매·생성·수정 시각은 오프셋 없는 한국 `LocalDateTime` 형식으로 처리합니다.
- `StoreResponse.role`은 사용하지 않습니다.
- 판매 등록의 기존 `transactionId` 대신 선택적 `clientTransactionId`를 사용하고 응답의 `saleId`를 취소 요청에 보관합니다.
- 별도 방문자 입력 API는 호출하지 않습니다.
- 채팅 전송에는 가능하면 고유한 `clientMessageId`를 보내고, 응답의 `assistantMessage.status`를 메시지 조회 API로 polling합니다.
- 마케팅은 업로드 → 초안 생성/수정 → 승인 순서로 연동합니다. 실제 Instagram 게시는 별도 브라우저 자동화가 담당합니다.
- 평균 별점은 신뢰할 데이터 provider가 없어 현재 제품 범위에서 제외했습니다.

## 운영 전 점검

- 운영 PostgreSQL을 백업한 뒤 Flyway V2→V8을 사전 적용합니다.
- 운영 배포 전에 별도 복제 DB에서 V1→V8 migration과 Hibernate schema validation을 실행합니다. V5는 기존 절대시각을 한국 현지시각으로 변환하고 `stores.time_zone`을 제거하며, V6는 매장 업종과 도로명주소 필드를 추가하고, V7은 판매 기록 정렬 인덱스를 추가하고, V8은 기존 Instagram OAuth 테이블을 매장 1:1 로그인 계정 테이블로 교체합니다.
- `MEDIA_PUBLIC_BASE_URL`의 이미지가 인터넷에서 HTTPS로 조회되는지 확인합니다.
- AI mock 전체 흐름을 검증한 뒤 실제 AI 연동이 필요하면 provider를 `http`로 전환합니다.
