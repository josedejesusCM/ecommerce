# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1: build the executable jar with the Temurin 21 JDK
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests need a live PostgreSQL, so they are run in CI, not during the image build.
RUN ./mvnw -B -q clean package -DskipTests \
 && cp target/ecommerce-0.0.1-SNAPSHOT.jar /workspace/app.jar

# ---------------------------------------------------------------------------
# Stage 2: runtime image with only the Temurin 21 JRE
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# curl is used only by the HEALTHCHECK below.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Dedicated unprivileged user and group; the app never runs as root.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080

# Database settings are supplied at runtime, e.g.
#   SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
