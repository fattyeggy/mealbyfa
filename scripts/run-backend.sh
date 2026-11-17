#!/usr/bin/env bash
# 启动后端服务（默认 8080，可通过 PORT 覆盖）
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$DIR/env.sh"
PORT="${PORT:-8080}"
cd "$DIR/../backend"

if [ ! -f target/sancan-meal-service.jar ]; then
  echo "[run] 未发现构建产物，先执行打包..."
  mvn -B -ntp -DskipTests package
fi

echo "[run] 启动 sancan-meal-service，端口 $PORT，数据目录 $(pwd)/data"
exec java -jar target/sancan-meal-service.jar --server.port="$PORT"
