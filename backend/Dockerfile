# ── Stage 1: dependency cache ────────────────────────────────────────────────
# Pre-download all Maven dependencies in a separate layer so that subsequent
# builds that only change source code do NOT re-download the internet.
FROM maven:3.9-eclipse-temurin-21-alpine AS deps
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
# Minimal JRE image — no JDK, no Maven, no build tooling.
# eclipse-temurin:21-jre-alpine is ~90 MB vs ~300 MB for the full JDK image.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root with a dedicated system account.
# The 'profitflow' group/user is created here so the UID is consistent across
# rebuilds (important for file-permission audits in finance environments).
RUN addgroup -S profitflow && adduser -S profitflow -G profitflow

WORKDIR /app

# Copy only the fat JAR — no source, no test classes, no POM.
COPY --from=builder --chown=profitflow:profitflow \
     /build/target/profitflow-api-*.jar app.jar

# Drop all default capabilities; the JVM only needs standard network sockets.
# --security-opt no-new-privileges is set at orchestrator level (k8s / compose).
USER profitflow

EXPOSE 8080

# Healthcheck used by Docker Compose, Kubernetes readiness probes,
# and the smoke-test step in the CD pipeline.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

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
