#!/usr/bin/env bash
# Atajo para la demo: equivale a "docker compose --profile demo up -d --build".
# --reset borra sofia_demo para que demo-seed la regenere. No toca la base normal sofia.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = "--reset" ]; then
  docker compose up -d --wait db
  docker compose --profile demo stop backend-demo >/dev/null 2>&1 || true
  docker compose exec -T db dropdb -U sofia --if-exists sofia_demo
fi
docker compose --profile demo up -d --build
docker compose --profile demo logs -f demo-seed &
docker compose --profile demo wait demo-seed >/dev/null; kill %1 2>/dev/null || true
echo "Demo lista en http://localhost:8089 (mismas credenciales del .env)."
