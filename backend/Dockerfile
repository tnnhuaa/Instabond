# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven wrapper and POM first for dependency caching
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml .

# Fix potential CRLF line-endings on the wrapper script
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-download dependencies (cached as a separate layer)
RUN ./mvnw dependency:go-offline -B -q

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
