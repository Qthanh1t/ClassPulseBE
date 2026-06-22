# syntax=docker/dockerfile:1
# =============================================================================
# ClassPulse Backend — Spring Boot 3.5 / Java 21
# Multi-stage: Gradle build → slim JRE runtime
# =============================================================================

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper + build scripts first (layer cache for dependencies)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# gradlew may carry CRLF from Windows → strip so the shebang works on Linux
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Source
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Spring Boot fat jar
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
