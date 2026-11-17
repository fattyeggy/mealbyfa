#!/usr/bin/env python3
"""从源码生成交付文档：接口清单、数据字典。

接口清单：扫描 controller 的 @RequestMapping / @GetMapping 等注解。
数据字典：解析 schema.sql 的 CREATE TABLE 语句与 Java 枚举。

用法：
    python3 scripts/gen-delivery-docs.py
输出：
    docs/交付/接口清单.md
    docs/交付/数据字典.md
"""
import os
import re
import sys
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOT = os.path.join(ROOT, "backend", "src", "main", "java")
SCHEMA = os.path.join(ROOT, "backend", "src", "main", "resources", "db", "schema.sql")
OUT_DIR = os.path.join(ROOT, "docs", "交付")

MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete)Mapping(?:\(([^)]*)\))?')
CLASS_MAPPING_RE = re.compile(r'@RequestMapping\("([^"]+)"\)')
PERMISSION_RE = re.compile(r'@RequirePermission\(Permission\.(\w+)\)')
METHOD_RE = re.compile(r'\n\s*public\s+[\w<>,\[\]\.\? ]+\s+(\w+)\s*\(')


def parse_controllers():
    """返回 [(controller 类名, 基础路径, [(HTTP 方法, 路径, 权限, Java 方法名)])]。"""
    controllers = []
    for dirpath, _, files in os.walk(JAVA_ROOT):
        for name in files:
            if not name.endswith("Controller.java"):
                continue
            path = os.path.join(dirpath, name)
            src = open(path, encoding="utf-8").read()
            class_match = CLASS_MAPPING_RE.search(src)
            base = class_match.group(1) if class_match else ""
            class_name = name.replace(".java", "")
            rows = []
            # 逐行扫描：注解 -> 注解 -> 方法名
            pending = []
            pending_permission = None
            for line in src.splitlines():
                stripped = line.strip()
                # 权限注解可能写在映射注解之前或之后，两种顺序都要认
                perm = PERMISSION_RE.match(stripped)
                if perm:
                    if pending:
                        pending[-1].append(perm.group(1))
                    else:
                        pending_permission = perm.group(1)
                    continue
                m = MAPPING_RE.match(stripped)
                if m:
                    verb = {"Get": "GET", "Post": "POST", "Put": "PUT", "Delete": "DELETE"}[m.group(1)]
                    arg = m.group(2) or ""
                    sub = re.search(r'"([^"]*)"', arg)
                    row = [verb, sub.group(1) if sub else ""]
                    if pending_permission:
                        row.append(pending_permission)
                        pending_permission = None
                    pending.append(row)
                    continue
                mm = METHOD_RE.search("\n" + line)
                if mm and pending:
                    method_name = mm.group(1)
                    for row in pending:
                        while len(row) < 3:
                            row.append("登录即可")
                        rows.append((row[0], row[1], row[2], method_name))
                    pending = []
            controllers.append((class_name, base, rows))
    return sorted(controllers, key=lambda c: c[0])


def parse_schema():
    """返回 [(表名, [(列名, 类型, 约束)], [约束])]。"""
    if not os.path.exists(SCHEMA):
        sys.exit("找不到 schema.sql")
    sql = open(SCHEMA, encoding="utf-8").read()
    tables = []
    for match in re.finditer(r'CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\);', sql, re.S):
        table = match.group(1)
        body = match.group(2)
        columns = []
        constraints = []
        for raw in body.splitlines():
            line = raw.strip().rstrip(",")
            if not line or line.startswith("--"):
                continue
            if line.upper().startswith("CONSTRAINT"):
                constraints.append(line)
                continue
            parts = line.split(None, 1)
            if len(parts) < 2:
                continue
            col, rest = parts[0], parts[1]
            upper = rest.upper()
            note = ""
            if "PRIMARY KEY" in upper:
                note = "主键"
            elif "UNIQUE" in upper:
                note = "唯一"
            columns.append((col, rest, note))
        tables.append((table, columns, constraints))
    return tables


def parse_enums():
    enums = []
    for dirpath, _, files in os.walk(JAVA_ROOT):
        for name in files:
            if not name.endswith(".java"):
                continue
            src = open(os.path.join(dirpath, name), encoding="utf-8").read()
            m = re.search(r'public enum (\w+)\s*\{(.*?)\n\}', src, re.S)
            if not m:
                continue
            enum_name = m.group(1)
            body = m.group(2)
            values = []
            for vm in re.finditer(r'^\s{4}([A-Z][A-Z0-9_]*)\s*(\([^)]*\))?\s*[,;]', body, re.M):
                values.append(vm.group(1))
            if values:
                enums.append((enum_name, sorted(set(values))))
    return sorted(enums)


PUBLIC_PREFIXES = ("/app/menu/", "/ping")
PUBLIC_EXACT = ("/admin/auth/login", "/app/auth/login")


def scope_of(path, permission):
    """把「谁可以调用」翻译成人能看懂的标签。"""
    if path in PUBLIC_EXACT or path.startswith(PUBLIC_PREFIXES):
        return "公开"
    if permission != "登录即可":
        return permission
    if path.startswith("/app/"):
        return "会员令牌"
    if path.startswith("/admin/"):
        return "后台登录"
    return "公开"


def write_api_docs(controllers):
    total = sum(len(c[2]) for c in controllers)
    lines = ["# 接口清单", "",
             "> 由 `scripts/gen-delivery-docs.py` 从源码生成，请勿手改；改完代码重新生成即可。", "",
             f"共 {total} 个接口，来自 {len(controllers)} 个控制器。", "",
             "## 鉴权规则", "",
             "| 标记 | 含义 |",
             "|---|---|",
             "| 公开 | 无需登录（公共菜单、健康检查、登录接口） |",
             "| 会员令牌 | `Authorization: Bearer {token}`，小程序会员 |",
             "| 后台登录 | 后台令牌即可，不再细分角色 |",
             "| 权限点 | 后台角色必须具备该权限，见「权限模型」 |",
             "",
             "## 权限模型", "",
             "| 角色 | 权限点 |",
             "|---|---|",
             "| SUPER_ADMIN 超级管理员 | 全部 |",
             "| OPS 运营 | 菜单读写、订单读/锁单、券、credit、复核、厨房读 |",
             "| NUTRITIONIST 营养计划 | 菜单读、菜品写、档案读、复核 |",
             "| KITCHEN 厨房负责人 | 生产读写、订单读、菜单读 |",
             "| PURCHASER 采购 | 菜品写、成本读、厨房读 |",
             "| FINANCE 财务 | 退款、账本冲正、成本读写、定价读写、审计读 |",
             "| COURIER 配送 | 订单读、配送写 |",
             "| AUDITOR 只读审计 | 审计、成本、订单、菜单、定价、余额只读 |",
             ""]
    for class_name, base, rows in controllers:
        if not rows:
            continue
        lines.append(f"## {class_name}")
        lines.append("")
        lines.append("| 方法 | 路径 | 鉴权 | 处理方法 |")
        lines.append("|---|---|---|---|")
        for verb, sub, perm, method_name in rows:
            full = (base + sub) or base
            lines.append(f"| {verb} | `{full}` | {scope_of(full, perm)} | `{method_name}` |")
        lines.append("")
    os.makedirs(OUT_DIR, exist_ok=True)
    open(os.path.join(OUT_DIR, "接口清单.md"), "w", encoding="utf-8").write("\n".join(lines))


def write_data_docs(tables, enums):
    lines = ["# 数据字典", "",
             "> 由 `scripts/gen-delivery-docs.py` 从 `schema.sql` 与 Java 枚举生成。", "",
             f"共 {len(tables)} 张表。开发/测试用 H2，生产用 MySQL 8（见 `db/migration-p4.5-p4.6-mysql.sql`）。", ""]
    for table, columns, constraints in tables:
        lines.append(f"## {table}")
        lines.append("")
        lines.append("| 字段 | 定义 | 说明 |")
        lines.append("|---|---|---|")
        for col, rest, note in columns:
            lines.append(f"| `{col}` | `{rest}` | {note} |")
        if constraints:
            lines.append("")
            lines.append("约束：")
            for c in constraints:
                lines.append(f"- `{c}`")
        lines.append("")
    lines.append("## 枚举")
    lines.append("")
    for name, values in enums:
        lines.append(f"- **{name}**：{' / '.join(values)}")
    lines.append("")
    open(os.path.join(OUT_DIR, "数据字典.md"), "w", encoding="utf-8").write("\n".join(lines))


def main():
    controllers = parse_controllers()
    tables = parse_schema()
    enums = parse_enums()
    write_api_docs(controllers)
    write_data_docs(tables, enums)
    print(f"接口清单：{sum(len(c[2]) for c in controllers)} 个接口 / {len(controllers)} 控制器")
    print(f"数据字典：{len(tables)} 张表 / {len(enums)} 个枚举")


if __name__ == "__main__":
    main()
