#!/usr/bin/env bash
# 后端全量测试
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$DIR/env.sh"
cd "$DIR/../backend"
# 先 clean：工作区位于 iCloud 同步目录，target 里可能出现「xxx 2.class」冲突副本，
# 残留会触发「找不到唯一主类」或重复 Bean 定义。
exec mvn -B -ntp clean test "$@"
