# ── Stage 1: dependency cache ────────────────────────────────────────────────
# Pre-download all Maven dependencies in a separate layer so that subsequent
# builds that only change source code do NOT re-download the internet.
FROM maven:3.9-eclipse-temurin-21-alpine AS deps
# Patch Alpine packages in build stages too (some scanners surface transitive base layers).
RUN apk update && apk upgrade --no-cache
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# ── Stage 2: build ────────────────────────────────────────────────────────────
# Compile and package the fat JAR.  Tests are skipped here; they run in CI
# before this image is built (fail-fast in the pipeline, not in Docker).
FROM deps AS builder
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 3: runtime ─────────────────────────────────────────────────────────
# Distroless Java runtime (Debian 12) — removes shell/BusyBox/coreutils and the
# package manager, significantly reducing OS-package CVEs and attack surface.
FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

WORKDIR /app

# Copy only the fat JAR — no source, no test classes, no POM.
COPY --from=builder --chown=nonroot:nonroot \
     /build/target/profitflow-api-*.jar app.jar

EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport          — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0         — use 75% of container RAM for heap
#   -XX:+ExitOnOutOfMemoryError       — crash-fast on OOM (let orchestrator restart)
#   -Djava.security.egd=…urandom      — non-blocking entropy (faster startup)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
