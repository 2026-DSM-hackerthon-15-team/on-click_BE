# ON:CLICK 백엔드 작업 현황

마지막 정리: 2026-07-14

## 완료한 작업

### 프로젝트 기반

- [x] Java 21, Spring Boot 3.5, Gradle 프로젝트 구성
- [x] `domain`, `global`, `common` 중심의 패키지 구조 적용
- [x] PostgreSQL, JPA, Flyway 구성
- [x] Docker Compose로 백엔드와 PostgreSQL을 한 번에 실행하도록 구성
- [x] 저장소 루트의 `.env`에서 DB, JWT, CORS, AI provider 환경변수 관리
- [x] 배포 프론트 `https://aaaaaa-ead.pages.dev`와 로컬 프론트 CORS 허용

### 인증과 매장

- [x] 서버 세션 없이 Stateless JWT Access Token 방식 적용
- [x] Access Token 만료 시간 1시간 적용
- [x] 회원가입 시 사용자와 최초 매장 생성
- [x] 로그인, 내 매장 목록, 매장 추가, 매장 정보 수정 구현
- [x] 모든 매장 API에서 `/stores/{storeId}/...` 경로 사용
- [x] 한 사용자가 여러 매장을 소유할 수 있도록 구현
- [x] `StoreRole`, `UserStoreMembership` 제거
- [x] `stores.owner_user_id`로 매장과 소유자를 직접 연결
- [x] JWT 사용자와 매장 소유자가 일치할 때만 접근 허용
- [x] 기존 OWNER 멤버십을 `owner_user_id`로 옮기는 V2 마이그레이션 작성

### 상품

- [x] 매장별 상품 등록과 목록 조회
- [x] 상품 이름과 가격 수정
- [x] 상품 판매 활성 상태 변경
- [x] 다른 매장의 상품 접근 차단
- [x] 판매 당시 상품 이름과 가격을 스냅샷으로 보존

### POS 판매

- [x] 결제 한 건을 나타내는 `SaleTransaction` 구현
- [x] 결제에 포함된 상품을 나타내는 `SaleItem` 구현
- [x] 서버에서 결제 식별자인 `saleId` 생성
- [x] POS 중복 전송 방지용 `clientTransactionId` 선택 입력 지원
- [x] 상품별 수량, 실제 결제 금액, 판매 시각 저장
- [x] 결제 전체 취소 및 최초 취소 시각 보존
- [x] 취소된 결제를 매출, 주문, 방문자 집계에서 제외
- [x] 동일 POS 요청의 순차·동시 재전송 멱등 처리
- [x] PostgreSQL 시간 정밀도에 맞춘 판매·취소 시각 정규화
- [x] 결제 금액과 수량 합산 overflow 검사
- [x] 기존 `sales` 데이터를 거래와 상품 항목으로 옮기는 V3 마이그레이션 작성

### 방문자와 대시보드

- [x] 별도의 `HourlyVisitorCount` 저장 구조와 방문자 입력 API 제거
- [x] 완료된 결제 한 건을 방문자 한 명으로 계산
- [x] 오늘 총매출, 주문 건수, 방문자 수 조회
- [x] 오늘 시간대별 매출, 판매 수량, 주문 건수 조회
- [x] 오늘 시간대별 방문자 수를 24개 버킷으로 조회
- [x] 마감 매출 예측 API 구현
- [x] 내일 방문자 예측 API 구현
- [x] AI 서버 준비 전까지 내부 `MockAiClient` 사용
- [x] 공개 응답에서는 mock 여부를 노출하지 않고 실제 연동과 동일한 계약 유지

### 문서와 검증

- [x] README를 현재 소유권·판매·방문자 구조에 맞게 수정
- [x] 일반 Notion API 명세의 매장, 판매, 대시보드 계약 수정
- [x] 제거된 방문자 입력 API를 Notion에서 제거 상태로 표시
- [x] Notion에 있던 사용자 콜아웃 보존
- [x] V1 → V2 → V3 데이터 이관 테스트 작성
- [x] POS 동시 등록·동시 취소 통합 테스트 작성
- [x] 전체 Gradle clean build 및 자동화 테스트 통과

## 현재 API에서 달라진 점

- `StoreResponse`에서 `role` 필드가 제거됨
- 판매 등록 요청의 필수 `transactionId`가 선택적 `clientTransactionId`로 변경됨
- 판매 등록 응답에 서버 생성 `saleId`가 포함됨
- 판매 취소 경로가 `/stores/{storeId}/sales/transactions/{saleId}/cancel`로 변경됨
- `PUT /stores/{storeId}/visitors/hourly` 방문자 입력 API가 제거됨
- `/stores/{storeId}/dashboard/hourly-visitors` 응답 형식은 유지되며 완료 결제 건수로 계산됨
- 주문 건수와 방문자 수는 현재 같은 값이며, 둘 다 완료된 결제 거래 수를 의미함

## 이번 구현에서 완료한 작업

### Git 정리

- [x] 사용자 파일 `Agents.md`를 수정하거나 커밋하지 않음
- [x] 기존 소유권·POS 리팩터링 커밋: `b418751`
- [x] 계정·매장·V4 workflow 스키마 커밋: `b8cb206`
- [x] AI HTTP adapter·컨설팅·채팅 커밋: `4016b2c`
- [x] 미디어·마케팅·Instagram 보안 연동 커밋: `a9e769f`

### 마이페이지와 계정 관리

- [x] `GET /me` 내 계정 정보 조회
- [x] 현재 비밀번호 확인이 필요한 `PATCH /me` 계정 수정
- [x] `PATCH /me/password` 비밀번호 변경
- [x] 회원가입 이름·이메일 및 아이디·이메일 중복 검사
- [x] 여러 매장 목록·추가·수정과 매장별 `closingTime` 관리
- [x] `closingTime`의 엄격한 `HH:mm` 검증과 기본값 `22:00`

### 일일 컨설팅

- [x] 매장별 컨설팅 엔티티·테이블과 제목·본문·대상일·상태·시각 저장
- [x] 컨설팅 목록·상세 조회 API
- [x] 매장 시간대와 마감시간을 기준으로 판매 데이터를 취합하는 스케줄러
- [x] `(storeId, targetDate)` 중복 방지, DB claim lease, 최대 3회 재시도
- [x] mock/HTTP `AiClient`에서 동일한 공개 계약 사용

### 채팅과 채팅방

- [x] 매장별 채팅방 생성·목록·상세·삭제와 메시지 cascade 삭제
- [x] USER/ASSISTANT 역할과 `PENDING`, `COMPLETED`, `FAILED` 상태 저장
- [x] 사용자 메시지와 PENDING 응답을 먼저 저장한 뒤 커밋 후 AI 비동기 생성
- [x] `afterId` 기반 프론트 polling
- [x] `clientMessageId` 기반 재전송 멱등 처리와 매장별 동시성 잠금
- [x] 컨설팅·마케팅 질문에 매장 기능 경로 링크 반환
- [x] 실패 작업 DB lease·복구·최대 3회 재시도

### 미디어·Instagram 마케팅

- [x] JPEG/PNG 업로드·공개 조회·미사용 파일 삭제 API
- [x] MIME·signature·실제 디코딩·해상도·크기 검증과 UUID 저장명
- [x] DB rollback/commit과 물리 파일 정합성, 24시간 orphan 정리
- [x] 마케팅 AI 초안 생성·목록·상세·수정·승인과 상태 관리
- [x] carousel 이미지 순서 영속화
- [x] 매장과 `InstagramAccount`의 1:1 연결 및 계정 ID·평문 비밀번호 저장
- [x] 매장 소유자 전용 Instagram 계정 등록·조회 API
- [x] 기존 Meta OAuth·Graph API·callback·토큰 저장 및 자동 게시 경로 제거
- [x] 마케팅 승인은 외부 게시 없이 `APPROVED` 상태까지 동기 처리
- [x] AI mock/HTTP provider 전환 설정

### 실제 AI 서버 연결 준비

- [x] 예측·컨설팅·채팅·마케팅용 `AiClient` DTO와 HTTP 구현체
- [x] base URL, 내부 API 키, connect/read timeout, 기능별 path 환경변수화
- [x] 일시 오류 retry와 최종 `AI_SERVICE_UNAVAILABLE` 변환
- [x] mock과 HTTP provider가 같은 프론트 공개 응답 사용

### 문서와 검증

- [x] README에 설정, 흐름, 프론트 변경사항, 운영 점검 추가
- [x] Notion 일반 API 데이터베이스를 실제 구현 39개 endpoint로 전면 갱신
- [x] 기존 API 문서 템플릿(`개요 → Endpoint → Request → Response → 동작 → 예외`) 유지
- [x] 구형 법률·자동화·매장 검색·방문자 입력·컨설팅 callback 문서를 보관함으로 이동
- [x] 기존 컨설팅 페이지의 사용자 💡 콜아웃 보존
- [x] AI 서버 API 명세서 데이터베이스는 수정하지 않음
- [x] Notion 역조회 결과: 39개, 구현 완료 39개, endpoint 누락·중복 0개
- [x] Java 21 main/test 전체 컴파일 성공
- [x] Flyway V1→V4, Hibernate schema validation 포함 자동화 테스트 112개 전부 통과
- [x] `docker compose config --quiet`와 `git diff --check` 통과

## 외부 환경에서 남은 작업

- [ ] 운영 PostgreSQL 백업 후 복제 환경에서 V2→V4 migration smoke test
  - 현재 실행 환경은 Docker socket 접근이 거부되어 실제 PostgreSQL 컨테이너 실행은 불가능했다.
  - H2 PostgreSQL 호환 모드의 V1→V4 이관·제약·schema validation 테스트는 통과했다.
- [ ] 프론트 저장소에서 `role`, 기존 `transactionId`, 방문자 입력 API 제거
- [ ] 프론트에서 `saleId`, `clientMessageId`와 Instagram 계정 등록 흐름 연결
- [ ] 브라우저 MCP가 승인 콘텐츠와 저장된 Instagram 계정으로 게시하는 자동화 연결
- [ ] 실제 AI provider staging end-to-end 검증
- [ ] 표준 Gradle 실행이 허용된 환경에서 `./gradlew clean build` 재실행
  - 현재 sandbox에서는 Gradle cache 쓰기와 로컬 lock listener가 제한되어, 동일 의존성 classpath로 `javac`와 JUnit Platform 전체 테스트를 직접 실행했다.

## 확정한 범위 결정

- 평균 별점은 신뢰할 리뷰 데이터 출처가 없어 이번 제품 범위에서 제외한다. 임의 값은 응답하지 않는다.
- 실제 POS 장비 연결은 범위 밖이며 POS처럼 판매 데이터를 넣는 API를 제공한다.
- 법률 조언·사용자 자동화·매장 검색은 `Agents.md` 상세 기능 밖이므로 구현하지 않고 구형 명세를 보관했다.
- AI 서버 전용 API 명세는 해당 서버 소유 계약으로 유지하며 이 백엔드에서는 수정하지 않았다.
- 백엔드는 AI 서버가 필요로 하는 매장 데이터를 DTO로 취합해 outbound HTTP 요청으로 전달한다. 사용자 JWT가 필요한 내부 GET API를 AI 서버에 노출하지 않는다.
