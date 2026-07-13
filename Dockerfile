FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system onclick \
    && useradd --system --gid onclick --home-dir /app onclick

WORKDIR /app

COPY --from=builder --chown=onclick:onclick /workspace/build/libs/app.jar app.jar

USER onclick

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
