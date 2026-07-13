# ON:CLICK Backend

소상공인 매장의 계정·지점·상품·POS 판매 원장, 대시보드, AI 컨설팅·채팅·마케팅과 Instagram 게시를 제공하는 Spring Boot API입니다.

## 기술 구성

- Java 21, Spring Boot 3.5, Gradle
- Spring MVC, Spring Data JPA, Spring Security JWT
- PostgreSQL, Flyway, Docker Compose
- AI/Instagram provider별 mock 및 HTTP 구현
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
- AI 실연동: `AI_PROVIDER=http`, `AI_BASE_URL`, `AI_INTERNAL_API_KEY`, timeout 및 기능별 path
- 미디어: `MEDIA_PUBLIC_BASE_URL`(Instagram에서 접근 가능한 공개 HTTPS 주소)
- Instagram 실연동: `INSTAGRAM_PROVIDER=http`, client ID/secret, OAuth callback, frontend redirect
- 토큰 암호화: `INSTAGRAM_TOKEN_ENCRYPTION_KEY`(Base64로 인코딩한 32바이트 키, `openssl rand -base64 32`로 생성)

`INSTAGRAM_PROVIDER=http`에서는 암호화 키가 없으면 애플리케이션이 시작되지 않습니다. mock provider에서 키를 생략하면 프로세스마다 임시 키를 사용하므로 재시작 후에는 mock Instagram 연결을 다시 생성해야 합니다.

업로드 이미지는 Docker의 `media-data` 볼륨에 보존됩니다. JPEG/PNG MIME·signature·실제 디코딩 가능 여부·해상도를 검증하며, 콘텐츠에 연결되지 않았거나 DB에 기록되지 않은 파일은 24시간 후 정리됩니다.

## 인증과 공통 규칙

- `POST /auth/signup`, `POST /auth/login`, `GET /public/media/{publicId}`, Instagram OAuth callback은 인증 없이 호출합니다.
- 보호 API는 `Authorization: Bearer {accessToken}`을 요구합니다.
- Access Token은 1시간 유효하며 Refresh Token과 서버 세션은 사용하지 않습니다.
- 매장 데이터 API는 `/stores/{storeId}/...` 형식이며 JWT 사용자와 매장 소유자가 일치해야 합니다.
- 오류 응답은 `{"errorCode":"...","message":"..."}` 형식입니다.

## API 요약

구현된 애플리케이션 엔드포인트 39개의 상세 계약은 [Notion API 명세서](https://app.notion.com/p/39c1b69f30c280179f3fef62d0c29bdb)에 정리되어 있습니다. AI 서버 API 명세서는 별도 계약으로 유지합니다.

- 계정: `GET/PATCH /me`, `PATCH /me/password`
- 매장: `GET/POST /stores`, `PATCH /stores/{storeId}`
- 상품: `GET/POST /stores/{storeId}/products`, 상품 정보·판매 상태 수정
- POS: 판매 등록 및 전체 취소를 `/stores/{storeId}/sales/transactions` 아래 제공
- 대시보드: 오늘 요약, 시간대별 매출·방문자, 마감 매출·내일 방문자 예측
- 컨설팅: `GET /stores/{storeId}/consultings`, 상세 조회
- 채팅: 채팅방 생성·목록·상세·삭제, 메시지 전송·polling
- 미디어: `POST /stores/{storeId}/media`, 미사용 이미지 삭제, 공개 이미지 조회
- 마케팅: AI 초안 생성·목록·상세·수정, `POST .../{marketingId}/approve`
- Instagram: 연결 시작·상태 조회·연결 해제, OAuth callback

## 주요 동작

### POS와 대시보드

판매 등록 시 서버가 `saleId`를 발급합니다. POS 재전송 중복 방지가 필요하면 `clientTransactionId`를 전달합니다. 완료된 결제 한 건을 주문 한 건이자 방문자 한 명으로 집계하며 취소 결제는 모든 집계에서 제외합니다.

### 일일 컨설팅

각 매장의 `timeZone`과 `closingTime`을 기준으로 당일 완료 매출을 집계합니다. `(storeId, targetDate)`별 컨설팅을 한 번만 생성하고 실패 작업은 DB 상태를 이용해 최대 3회 재시도합니다.

### 채팅 polling

메시지 전송은 사용자 메시지와 `PENDING` AI 메시지를 저장한 뒤 `202 Accepted`를 반환합니다. 선택적 `clientMessageId`를 보내면 네트워크 재전송에도 같은 사용자·AI 메시지 쌍을 반환합니다. 프론트는 메시지 조회의 `afterId`를 사용해 `COMPLETED` 또는 `FAILED`가 될 때까지 polling합니다. 컨설팅·마케팅 질문의 AI 응답에는 해당 매장의 기능 경로가 포함됩니다.

### 마케팅과 Instagram

1. 이미지를 `POST /stores/{storeId}/media`로 업로드합니다.
2. 반환된 `mediaId`로 AI 마케팅 초안을 생성합니다.
3. `DRAFT` 상태에서 본문·해시태그·이미지를 수정합니다.
4. Instagram OAuth 연결 후 콘텐츠를 승인합니다.
5. 승인된 콘텐츠는 비동기로 게시되고 `PUBLISHED` 또는 `FAILED` 상태가 됩니다.

OAuth state는 원문 대신 SHA-256 해시만 저장하고 비관적 잠금으로 한 번만 소비합니다. Access token은 AES-256-GCM으로 암호화해 저장하며 만료 7일 전부터 갱신을 시도합니다. 연결 해제 시 provider 권한 폐기를 시도한 뒤 로컬 토큰을 삭제합니다.

Instagram 게시는 DB에서 작업을 원자적으로 claim하고 커밋한 뒤 외부 API를 호출하며, 성공·실패 결과는 별도 트랜잭션으로 확정합니다. 여러 인스턴스의 동시 실행은 잠금으로 차단합니다. 외부 요청 결과가 불명확한 경우 자동 재게시하지 않고 `FAILED`로 멈춰 중복 게시를 피합니다.

## 프론트 연동 변경사항

- 회원가입 요청에 `name`, `email`을 추가하고 매장 `closingTime`은 `HH:mm` 형식으로 처리합니다.
- `StoreResponse.role`은 사용하지 않습니다.
- 판매 등록의 기존 `transactionId` 대신 선택적 `clientTransactionId`를 사용하고 응답의 `saleId`를 취소 요청에 보관합니다.
- 별도 방문자 입력 API는 호출하지 않습니다.
- 채팅 전송에는 가능하면 고유한 `clientMessageId`를 보내고, 응답의 `assistantMessage.status`를 메시지 조회 API로 polling합니다.
- 마케팅은 업로드 → 초안 생성/수정 → OAuth 연결 → 승인 → 게시 상태 polling 순서로 연동합니다.
- 평균 별점은 신뢰할 데이터 provider가 없어 현재 제품 범위에서 제외했습니다.

## 운영 전 점검

- 운영 PostgreSQL을 백업한 뒤 Flyway V2→V4를 사전 적용합니다.
- 운영 배포 전에 별도 복제 DB에서 V1→V4 migration과 Hibernate schema validation을 실행합니다.
- `MEDIA_PUBLIC_BASE_URL`의 이미지가 인터넷에서 HTTPS로 조회되는지 확인합니다.
- Meta 앱의 callback URL과 환경변수의 `INSTAGRAM_OAUTH_CALLBACK_URL`이 정확히 일치해야 합니다.
- mock provider로 전체 흐름을 검증한 뒤 AI와 Instagram provider를 각각 `http`로 전환합니다.
