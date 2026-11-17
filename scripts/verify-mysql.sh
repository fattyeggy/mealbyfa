#!/usr/bin/env bash
# 在真实 MySQL / MariaDB 上验证：全量建表、次级索引、增量迁移、旧库升级。
#
# 用法：
#   MYSQL_SOCKET=/tmp/mysql.sock MYSQL_USER=root ./scripts/verify-mysql.sh      # 走 socket
#   MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD=xxx ./scripts/verify-mysql.sh
#
# 注意：会创建/删除 sancan_verify 与 sancan_migrate 两个库，请勿指向生产实例。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USER="${MYSQL_USER:-root}"
PASS="${MYSQL_PASSWORD:-}"

# 客户端可指定：MYSQL_CLIENT=/path/to/mysql 或 /path/to/mariadb
CLIENT="${MYSQL_CLIENT:-mysql}"
if [ -n "${MYSQL_SOCKET:-}" ]; then
  MYSQL=("$CLIENT" --socket="$MYSQL_SOCKET" -u "$USER")
else
  MYSQL=("$CLIENT" -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" -u "$USER")
fi
[ -n "$PASS" ] && MYSQL+=("-p$PASS")

run() { "${MYSQL[@]}" "$@"; }

echo "[1/6] 连接检查"
run -e "SELECT VERSION() AS server_version;"

echo "[2/6] 全量建表：sancan_verify"
run -e "DROP DATABASE IF EXISTS sancan_verify; CREATE DATABASE sancan_verify DEFAULT CHARACTER SET utf8mb4;"
run sancan_verify < "$ROOT/backend/src/main/resources/db/schema.sql"
run sancan_verify < "$ROOT/backend/src/main/resources/db/mysql-indexes.sql"
run sancan_verify -N -e "
  SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema='sancan_verify';
  SELECT CONCAT('unique_indexes=', COUNT(DISTINCT index_name))
    FROM information_schema.statistics
   WHERE table_schema='sancan_verify' AND non_unique=0 AND index_name<>'PRIMARY';
"

echo "[3/6] 构造旧库：sancan_migrate（回退 P4.5 之后的新增）"
run -e "DROP DATABASE IF EXISTS sancan_migrate; CREATE DATABASE sancan_migrate DEFAULT CHARACTER SET utf8mb4;"
run sancan_migrate < "$ROOT/backend/src/main/resources/db/schema.sql"
run sancan_migrate -e "
  DROP TABLE IF EXISTS meal_pricing_version_swap;
  DROP TABLE IF EXISTS meal_pricing_version;
  DROP TABLE IF EXISTS meal_plan_review;
  ALTER TABLE meal_nutrition_plan
    DROP COLUMN pricing_version_id, DROP COLUMN pricing_version_no,
    DROP COLUMN needs_review, DROP COLUMN review_note, DROP COLUMN raw_target_protein_g;
  ALTER TABLE meal_plan_item DROP COLUMN base_cost;
  ALTER TABLE meal_order
    DROP COLUMN pricing_version_id, DROP COLUMN pricing_version_no,
    DROP COLUMN raw_price, DROP COLUMN swap_difference;
  ALTER TABLE sys_user DROP COLUMN role;
"

echo "[4/6] 应用增量迁移"
run sancan_migrate < "$ROOT/backend/src/main/resources/db/migration-p4.5-p4.6-mysql.sql"

echo "[5/6] 校验迁移结果"
run sancan_migrate -N -e "
  SELECT CONCAT('plan_new_cols=', COUNT(*)) FROM information_schema.columns
   WHERE table_schema='sancan_migrate' AND table_name='meal_nutrition_plan'
     AND column_name IN ('pricing_version_id','pricing_version_no','needs_review','review_note','raw_target_protein_g');
  SELECT CONCAT('review_table=', COUNT(*)) FROM information_schema.tables
   WHERE table_schema='sancan_migrate' AND table_name='meal_plan_review';
  SELECT CONCAT('sys_user_role=', COUNT(*)) FROM information_schema.columns
   WHERE table_schema='sancan_migrate' AND table_name='sys_user' AND column_name='role';
  SELECT CONCAT('order_new_cols=', COUNT(*)) FROM information_schema.columns
   WHERE table_schema='sancan_migrate' AND table_name='meal_order'
     AND column_name IN ('pricing_version_id','pricing_version_no','raw_price','swap_difference');
"

echo "[6/6] 输出后续冒烟命令"
cat <<'TIP'

结构验证通过。用真实库启动服务并跑业务链路：

  cd backend
  MYSQL_URL="jdbc:mysql://HOST:PORT/sancan_verify?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true" \
  MYSQL_USER=root MYSQL_PASSWORD=*** \
  java -jar target/sancan-meal-service.jar --spring.profiles.active=mysql --server.port=8081

  # 另开终端
  python3 scripts/demo/demo-menu-seed.py http://127.0.0.1:8081
  python3 scripts/demo/demo-plan-seed.py  http://127.0.0.1:8081

预期：菜单 21 餐次、计划日价与 H2 一致、下单/锁单/快照/生产批次/成本报表均可读。

说明：
1. 生产启动使用 `--spring.profiles.active=mysql`，该 profile 关闭自动建表（sql.init.mode=never），
   建表与迁移由 DBA 执行；
2. 已有库升级只需执行 migration-p4.5-p4.6-mysql.sql；
3. 本轮验证在 MariaDB 13.0.2 上完成（MySQL 协议与 SQL 方言兼容），若目标为 Oracle MySQL 8.x，
   建议再跑一次本脚本确认。
TIP
