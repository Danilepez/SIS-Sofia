#!/usr/bin/env bash
# Equivalente de test.ps1 sin Java local: corre la suite en un contenedor Maven contra sofia_test.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
docker compose up -d --wait db
docker compose exec -T db sh -c "psql -U sofia -tAc \"SELECT 1 FROM pg_database WHERE datname='sofia_test'\" | grep -q 1 || createdb -U sofia sofia_test"
docker run --rm --network "$(basename "$PWD" | tr '[:upper:]' '[:lower:]')_default" -v "$PWD/backend":/app -v sofia_m2:/root/.m2 -w /app \
 -e DB_URL=jdbc:postgresql://db:5432/sofia_test -e DB_PASSWORD -e OPERATOR_PASSWORD -e QUALITY_PASSWORD -e READER_PASSWORD -e SUPERVISOR_PASSWORD \
 maven:3.9-eclipse-temurin-21 mvn -B verify
