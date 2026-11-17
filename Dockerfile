# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a non-root user.
RUN groupadd -r app && useradd -r -g app -u 1001 app
COPY --from=build /app/target/nest-crm.jar /app/app.jar
USER app
# Railway injects PORT; the app binds to it (defaults to 8080 locally).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
