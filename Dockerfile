FROM gradle:8.10.1-jdk17 AS builder

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

COPY src ./src

RUN chmod +x ./gradlew

RUN ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
