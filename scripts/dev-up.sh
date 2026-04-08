#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

cp -n .env.example .env || true

docker compose \
  -f docker-compose.yml \
  -f docker-compose.app.yml \
  up -d --build

echo ""
echo "UI:  http://127.0.0.1:4200"
echo "API: http://127.0.0.1:8080"
echo "Grafana: http://127.0.0.1:3001"
echo "Prometheus: http://127.0.0.1:9090"

