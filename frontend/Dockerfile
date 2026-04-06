# ── Stage 1: build ───────────────────────────────────────────────────────────
# Use the official Node LTS image on Alpine for the smallest possible builder.
# node:20-alpine is ~55 MB vs ~380 MB for node:20.
FROM node:20-alpine AS builder

# Alpine OS patches (e.g. libpng): apk upgrade, then pin libpng when the mirror provides it.
RUN apk update && apk upgrade -a --no-cache
RUN apk add --no-cache libpng=1.6.56-r0 || apk add --no-cache -u libpng

WORKDIR /app

# Copy lock file first to leverage Docker layer caching.
# 'npm ci' is strictly reproducible (respects package-lock.json exactly) —
# mandatory for finance applications where dependency pinning is audited.
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit

# Copy only files required for ng build (docker:S6470 — avoid COPY . .).
# Tests, proxy, Karma, and runtime nginx.conf are not part of the builder image.
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY postcss.config.js tailwind.config.js ./
COPY .editorconfig ./
COPY src ./src
RUN npm run build -- --configuration=production

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
# Official unprivileged nginx on Alpine 3.23+ (libavif ≥1.3.0; nginx 1.29.x apk, not 1.27.x).
FROM nginxinc/nginx-unprivileged:1.29.5-alpine AS runtime

# Temporarily escalate to root to modify the filesystem (the base image defaults to a non-root user).
USER root

RUN apk update && apk upgrade -a --no-cache
RUN apk add --no-cache libpng=1.6.56-r0 || apk add --no-cache -u libpng
RUN apk add --no-cache libavif=1.3.0-r0 || apk add --no-cache -u libavif
RUN apk add --no-cache -u tiff libtiff || apk add --no-cache -u tiff || true
RUN apk add --no-cache -u nginx nghttp2-libs curl libcurl || true

# Document root for the SPA (nginx.conf root directive). Owned by nginx; static files made read-only below (docker:S6504).
RUN mkdir -p /tmp/nginx/html && chown -R nginx:nginx /tmp/nginx

# Copy the compiled Angular app into the nginx document root.
# Angular CLI outputs to dist/<projectName>/browser for SSR-capable projects.
COPY --from=builder --chown=nginx:nginx /app/dist/frontend/browser /tmp/nginx/html
# Strip write bits so nginx cannot mutate shipped assets at runtime (dirs stay traversable: 755→555, files 644→444).
RUN chmod -R a-w /tmp/nginx/html

# Copy the hardened nginx configuration.
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chmod a-w /etc/nginx/conf.d/default.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/index.html || exit 1

USER nginx

CMD ["nginx", "-g", "daemon off;"]
