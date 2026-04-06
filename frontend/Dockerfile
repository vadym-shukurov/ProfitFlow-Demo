# ── Stage 1: build ───────────────────────────────────────────────────────────
# Use the official Node LTS image on Alpine for the smallest possible builder.
# node:20-alpine is ~55 MB vs ~380 MB for node:20.
FROM node:20-alpine AS builder

WORKDIR /app

# Copy lock file first to leverage Docker layer caching.
# 'npm ci' is strictly reproducible (respects package-lock.json exactly) —
# mandatory for finance applications where dependency pinning is audited.
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit

# Copy source and build the production bundle.
# --configuration=production enables AOT, tree-shaking, and minification.
COPY . .
RUN npm run build -- --configuration=production

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
# Minimal nginx image — no Node, no npm, no source code.
# nginx:1.27-alpine is ~10 MB.
FROM nginx:1.27-alpine AS runtime

# Refresh Alpine packages — base tag may lag security fixes (openssl, expat, libpng, curl, …).
RUN set -eux; \
    apk update; \
    apk upgrade --no-cache; \
    apk add --no-cache --upgrade \
      zlib \
      libpng \
      expat \
      openssl \
      libssl3 \
      libcrypto3 \
      curl \
      libcurl

# Security: remove default nginx pages that could reveal version info.
RUN rm -rf /usr/share/nginx/html/*

# Copy the compiled Angular app into the nginx document root.
# Angular CLI outputs to dist/<projectName>/browser for SSR-capable projects.
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

# Copy the hardened nginx configuration.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Security: nginx master process runs as root (binds port 80) but worker
# processes run as the 'nginx' user — standard nginx security model.
# For rootless deployments, switch to port 8080 and run as a named user.
EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost/index.html || exit 1

CMD ["nginx", "-g", "daemon off;"]
