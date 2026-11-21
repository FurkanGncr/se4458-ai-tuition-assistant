# syntax=docker/dockerfile:1

# ---- Build stage: compile the Spring Boot jar with the Maven wrapper ----
# Runs on the build machine's own platform: the jar is platform-independent, so a
# linux/amd64 image can be built on an arm64 Mac without running Maven under emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download dependencies first so they are cached between source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run separately with ./mvnw test (they need no database).
RUN ./mvnw -B -q -DskipTests package && cp target/tuition-api-*.jar app.jar

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /workspace/app.jar app.jar
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
