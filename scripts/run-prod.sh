#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-logistics-agent:prod}"
ENV_FILE="${ENV_FILE:-.env}"

cd "$ROOT_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy .env.example and fill values through your deployment secret channel." >&2
  exit 1
fi

docker compose --env-file "$ENV_FILE" -f docker-compose.prod.example.yml build
docker compose --env-file "$ENV_FILE" -f docker-compose.prod.example.yml up -d

echo "Started $IMAGE_NAME. Health: http://localhost:${SERVER_PORT:-8080}/actuator/health"
