#!/usr/bin/env bash
# 本地用真实微信凭据启动后端（H2 + 关闭 mock 登录）
# 凭据来自 backend/.env.local（不入库），不要写进命令行或文档。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ ! -f "$ROOT/backend/.env.local" ]; then
  echo "缺少 backend/.env.local（需包含 WX_APP_ID / WX_APP_SECRET）" >&2
  exit 1
fi
# 载入便携 JDK（本机没有系统 Java）
# shellcheck disable=SC1091
source "$ROOT/scripts/env.sh"
# shellcheck disable=SC1091
source "$ROOT/backend/.env.local"
: "${WX_APP_ID:?未设置 WX_APP_ID}"
: "${WX_APP_SECRET:?未设置 WX_APP_SECRET}"

cd "$ROOT/backend"
exec java -jar target/sancan-meal-service.jar \
  --spring.profiles.active=local \
  --meal.wx.mock-enabled=false \
  --meal.wx.app-id="$WX_APP_ID" \
  --meal.wx.app-secret="$WX_APP_SECRET" \
  --server.port=8080
