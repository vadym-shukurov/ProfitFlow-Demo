# ── Stage 1: build ───────────────────────────────────────────────────────────
# Use the official Node LTS image on Alpine for the smallest possible builder.
# node:20-alpine is ~55 MB vs ~380 MB for node:20.
FROM node:20-alpine AS builder

# Alpine OS patches (e.g. libpng): refresh index, upgrade, then pin patched libpng when available.
RUN apk update && apk upgrade --no-cache \
    && (apk add --no-cache libpng=1.6.56-r0 || apk add --no-cache -u libpng)

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
# Official unprivileged nginx — runs as non-root; listens on 8080 (see nginx.conf).
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

# Temporarily escalate to root to modify the filesystem (the base image defaults to a non-root user).
USER root

RUN apk update && apk upgrade --no-cache \
    && (apk add --no-cache libpng=1.6.56-r0 || apk add --no-cache -u libpng)

# Prepare writable web root (nginx.conf points here).
RUN mkdir -p /tmp/nginx/html && chown -R nginx:nginx /tmp/nginx

# Copy the compiled Angular app into the nginx document root.
# Angular CLI outputs to dist/<projectName>/browser for SSR-capable projects.
COPY --from=builder --chown=nginx:nginx /app/dist/frontend/browser /tmp/nginx/html

# Copy the hardened nginx configuration.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/index.html || exit 1

USER nginx

CMD ["nginx", "-g", "daemon off;"]
