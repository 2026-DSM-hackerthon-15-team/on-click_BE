# ON:CLICK Backend

소상공인 매장의 상품, POS형 판매·방문자 원장과 대시보드를 제공하는 Spring Boot API입니다.

## 기술 구성

- Java 21, Spring Boot 3.5, Gradle
- Spring MVC, Spring Data JPA, Spring Security JWT
- PostgreSQL, Flyway
- 테스트: JUnit 5, H2

## 실행

### Docker Compose

저장소 루트의 `.env` 값을 사용해 PostgreSQL과 백엔드를 함께 실행합니다.

```bash
docker compose up -d --build
```

백엔드는 기본적으로 `http://localhost:8080`, PostgreSQL은 `localhost:5432`에서 열립니다. 포트와 계정 정보는 `.env`에서 변경할 수 있습니다.

`CORS_ALLOWED_ORIGINS`에는 백엔드 API를 호출할 프론트 origin을 쉼표로 구분해 적습니다. 기본 구성은 배포된 Pages 주소와 로컬 Vite 개발 서버만 허용합니다.

종료할 때는 다음 명령을 사용합니다. 데이터베이스 데이터는 Docker 볼륨에 유지됩니다.

```bash
docker compose down
```

볼륨까지 초기화하려면 `docker compose down -v`를 사용합니다.

### 로컬 실행

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

POS 데이터는 판매·방문자 입력 API를 통해 수집합니다. 마감 매출과 내일 방문자 예측은 `AiClient`를 통해 제공하며, 실행 환경의 provider와 관계없이 프론트에는 동일한 정상 응답 계약을 보장합니다.
