#!/usr/bin/env bash
# Equivalente de init.ps1 para macOS/Linux: genera claves locales en .env sin reemplazar las existentes.
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$project_root/.env"
umask 077
touch "$env_file"
added=()
for name in DB_PASSWORD OPERATOR_PASSWORD QUALITY_PASSWORD READER_PASSWORD SUPERVISOR_PASSWORD; do
  if ! grep -q "^$name=" "$env_file"; then echo "$name=$(openssl rand -base64 24)" >> "$env_file"; added+=("$name"); fi
done
if [ ${#added[@]} -eq 0 ]; then echo '.env ya existe; se conservan sus claves.'; else echo "Claves agregadas en .env: ${added[*]}. Consúltelas localmente; nunca las suba a GitHub."; fi
