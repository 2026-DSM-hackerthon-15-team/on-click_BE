# ON:CLICK Backend

소상공인 매장의 상품, POS형 판매·방문자 원장과 대시보드를 제공하는 Spring Boot API입니다.

## 기술 구성

- Java 21, Spring Boot 3.5, Gradle
- Spring MVC, Spring Data JPA, Spring Security JWT
- PostgreSQL, Flyway
- 테스트: JUnit 5, H2

## 실행

PostgreSQL을 준비한 뒤 환경 변수를 설정합니다.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/onclick
export DB_USERNAME=onclick
export DB_PASSWORD=onclick
export JWT_SECRET=32바이트_이상의_안전한_비밀키
./gradlew bootRun
```

전체 검증은 다음 명령으로 실행합니다.

```bash
./gradlew build
```

## 구현 API

- 인증: `POST /auth/signup`, `POST /auth/login`
- 지점: `GET/POST /stores`, `PATCH /stores/{storeId}`
- 상품: `/stores/{storeId}/products`
- POS 판매: `/stores/{storeId}/sales/transactions`
- 방문자 입력: `PUT /stores/{storeId}/visitors/hourly`
- 대시보드: `/stores/{storeId}/dashboard/*`

보호 API는 `Authorization: Bearer {accessToken}`을 요구합니다. 경로의 `storeId`는 JWT 사용자의 지점 멤버십과 항상 대조합니다.

## 외부 연동 경계

실제 POS와 AI 서버는 아직 연동하지 않습니다. POS 대신 판매·방문자 입력 API를 사용하며, 마감 매출 및 내일 방문자 예측은 `app.ai.provider=mock`의 `MockAiClient`가 응답합니다. 향후 `AiClient`의 HTTP 구현으로 교체할 수 있습니다.
