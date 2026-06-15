#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_REPO="${MAVEN_REPO:-/Users/zhangzhuang/Documents/develop/maven_repository}"

cd "$ROOT_DIR"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export SERVER_PORT="${SERVER_PORT:-8080}"

mvn -Dmaven.repo.local="$MAVEN_REPO" spring-boot:run
