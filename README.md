# 发发的餐（原三餐私教）（sancan-coach）

在线三餐私教系统的开发实现，依据《三餐私教微信小程序与 RuoYi 后台开发交接手册 V1.0》。

## 目录

| 路径 | 内容 |
|---|---|
| `backend/` | meal 自研模块后端服务（Java 17 / Spring Boot 3 / MyBatis-Plus） |
| `miniapp/` | 微信小程序（UniApp Vue3，H5 可运行） |
| `admin/` | 运营后台（Vue3 + Element Plus，8 个页面可用） |
| `docs/` | 开发计划、架构决策、各阶段工作说明 |
| `scripts/` | 工具链与环境脚本 |
| `tools/` | 便携 JDK 与 Maven（不纳入版本管理） |

## 快速开始

```bash
source scripts/env.sh          # 载入便携 JDK 17 + Maven 3.10
cd backend
mvn test                        # 全量测试
mvn spring-boot:run            # 启动服务，默认 http://localhost:8080
curl http://localhost:8080/ping
```

后台默认账号（仅本机开发，部署前必须修改）：

| 账号 | 密码 | 角色 | 能做什么 |
|---|---|---|---|
| admin | admin123 | 超级管理员 | 全部 |
| ops | ops123 | 运营 | 菜单、订单、券、credit、复核 |
| finance | finance123 | 财务 | 退款、账本冲正、成本、定价 |
| auditor | auditor123 | 只读审计 | 审计日志与报表只读 |
数据库默认使用工作区内的 H2 文件库 `backend/data/sancan.mv.db`，删除该文件即可重建。

## 数据库

- 开发/测试默认 H2 文件库（`backend/data/`），无需安装数据库；
- 生产使用 MySQL：`--spring.profiles.active=mysql`，并注入 `MYSQL_URL / MYSQL_USER / MYSQL_PASSWORD`；
- 结构与迁移脚本：`backend/src/main/resources/db/{schema.sql,mysql-indexes.sql,migration-p4.5-p4.6-mysql.sql}`；
- 真实库自检：`MYSQL_CLIENT=mysql MYSQL_SOCKET=/tmp/mysql.sock ./scripts/verify-mysql.sh`
  （会验证全量建表、索引与旧库升级迁移，请勿指向生产实例）。

## 交付文档

`docs/交付/` 是给开发与运营的交付包：

| 文件 | 用途 |
|---|---|
| [交付清单核对表](docs/交付/交付清单核对表.md) | 对照手册 §18 的交付物索引 |
| [接口清单](docs/交付/接口清单.md) | 86 个接口与鉴权（脚本生成） |
| [数据字典](docs/交付/数据字典.md) | 29 张表与 20 个枚举（脚本生成） |
| [操作手册](docs/交付/操作手册.md) | 运营/营养/厨房/财务日常操作 |
| [部署手册](docs/交付/部署手册.md) | H2 体验、MySQL 生产部署、构建与 Nginx |
| [测试与回滚](docs/交付/测试与回滚.md) | 测试账号、测试清单、已知问题、回滚 |
| [LICENSE](docs/交付/LICENSE.md) / [THIRD_PARTY_NOTICES](docs/交付/THIRD_PARTY_NOTICES.md) | 授权与第三方依赖 |

## 文档

- [开发计划](docs/00-开发计划.md)
- [工作说明 01：环境与工程奠基](docs/work-logs/工作说明-01-环境与工程奠基.md)
- [工作说明 02：菜单域](docs/work-logs/工作说明-02-菜单域.md)
- [工作说明 03：计划与计价](docs/work-logs/工作说明-03-计划与计价.md)
- [工作说明 04：交易域](docs/work-logs/工作说明-04-交易域.md)
- [工作说明 05：后场域](docs/work-logs/工作说明-05-后场域.md)
- [工作说明 06：计价规则整改](docs/work-logs/工作说明-06-计价规则整改.md)
- [工作说明 07：计价边界与复核门禁](docs/work-logs/工作说明-07-计价边界与复核门禁.md)
- [工作说明 08：替换选项成本冻结](docs/work-logs/工作说明-08-替换选项成本冻结.md)
- [工作说明 09：小程序端](docs/work-logs/工作说明-09-小程序端.md)
- [工作说明 10：运营后台](docs/work-logs/工作说明-10-运营后台.md)
- [工作说明 11：验收与交付准备](docs/work-logs/工作说明-11-验收与交付准备.md)
- [工作说明 12：角色权限](docs/work-logs/工作说明-12-角色权限.md)
- [工作说明 13：真实数据库验证](docs/work-logs/工作说明-13-真实数据库验证.md)
- [工作说明 14：完餐反馈与导出](docs/work-logs/工作说明-14-完餐反馈与导出.md)
- [工作说明 15：领料差异入报表](docs/work-logs/工作说明-15-领料差异入报表.md)
- [工作说明 16：交付文档](docs/work-logs/工作说明-16-交付文档.md)
- [工作说明 17：菜品界面与备份演练](docs/work-logs/工作说明-17-菜品界面与备份演练.md)
- [工作说明 18：总体交付验收](docs/work-logs/工作说明-18-总体交付验收.md)
- [工作说明 19：资金与履约整改 + 微信凭据](docs/work-logs/工作说明-19-资金履约整改与微信凭据.md)
- [工作说明 20：履约收尾](docs/work-logs/工作说明-20-履约收尾.md)
- [工作说明 21：营养模型与六步建档](docs/work-logs/工作说明-21-营养模型与六步建档.md)
- [验收对照表 P7](docs/验收对照表_P7.md)
- [发发的餐 · 整改对照表](docs/发发的餐_整改对照表.md)
- [发发的餐 · 阶段一验收说明](docs/交付/验收说明.md)

## 小程序端

```bash
cd miniapp
npm --cache ../.npm-cache install
npm --cache ../.npm-cache run dev:h5     # http://localhost:5173
```

截图见 [docs/screenshots](docs/screenshots)，说明见 [miniapp/README.md](miniapp/README.md)。

## 运营后台

```bash
cd admin
npm --cache ../.npm-cache install
npm --cache ../.npm-cache run dev      # http://localhost:5174，默认 admin / admin123
```

## 演示数据

服务启动后依次执行：

```bash
python3 scripts/demo/demo-menu-seed.py http://127.0.0.1:8080   # 建食材/菜品/周菜单并发布
python3 scripts/demo/demo-plan-seed.py http://127.0.0.1:8080   # 会员建档并生成个人计划
python3 scripts/demo/demo-order-seed.py http://127.0.0.1:8080  # 发 credit、下单、锁单、退款
python3 scripts/demo/demo-kitchen-seed.py http://127.0.0.1:8080 # 生产汇总、食材需求、成本报表
python3 scripts/demo/demo-pricing-seed.py http://127.0.0.1:8080 # 计价规则 V1.0 购价表与日价
python3 scripts/demo/demo-review-gate.py http://127.0.0.1:8080 # 高蛋白计划、复核门禁、替换开关
```
