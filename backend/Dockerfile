# ── Stage 1: dependency cache ────────────────────────────────────────────────
# Pre-download all Maven dependencies in a separate layer so that subsequent
# builds that only change source code do NOT re-download the internet.
# Use the Debian-based Maven image (not Alpine) to avoid apk-only OS noise in SBOMs.
FROM maven:3.9-eclipse-temurin-21 AS deps
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# ── Stage 2: build ────────────────────────────────────────────────────────────
# Compile and package the fat JAR.  Tests are skipped here; they run in CI
# before this image is built (fail-fast in the pipeline, not in Docker).
FROM deps AS builder
COPY src ./src
# Single predictable path for runtime COPY — avoids globs (Sonar docker:S6504 parses COPY reliably).
RUN mvn package -DskipTests -B -q \
    && cp /build/target/profitflow-api-*.jar /build/app.jar

# ── Stage 3: runtime ─────────────────────────────────────────────────────────
# Distroless Debian 12 bundles fixed .deb sets with no apt — it can lag
# bookworm-security (e.g. libpng16-16 deb12u3 vs deb12u4) and fail strict image scans. Use
# Eclipse Temurin on Ubuntu Noble so security updates apply at image build time.
FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y \
    && pkgs=$(dpkg-query -W -f='${Package}\n' 2>/dev/null \
         | grep -E '^(libpng|libssl|openssl|ncurses|libncurses|libtinfo|libexpat|libnghttp2|dpkg|locales|libc6|libc-bin)' || true) \
    && if [ -n "$pkgs" ]; then \
         DEBIAN_FRONTEND=noninteractive apt-get install -y --only-upgrade $pkgs; \
       fi \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 65532 nonroot \
    && useradd --uid 65532 --gid 65532 --shell /usr/sbin/nologin --create-home nonroot

WORKDIR /app

# Copy only the fat JAR — no source, no test classes, no POM.
# --chmod=444: no write bits on the artifact (docker:S6504); one COPY line so scanners see the flag.
COPY --from=builder --chown=nonroot:nonroot --chmod=444 /build/app.jar app.jar

EXPOSE 8080

USER nonroot

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
