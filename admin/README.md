# 三餐私教 · 运营后台

Vue3 + Element Plus，对接本仓库 `backend/`。覆盖手册 §13–§14 的运营动作：菜单发布、复核处理、定价版本、订单、厨房、成本、用户余额。

## 运行

```bash
# 1) 启动后端（默认 8080）
cd ../backend && mvn -DskipTests package && java -jar target/sancan-meal-service.jar

# 2) 启动后台（5174，/admin 已代理到 8080）
npm --cache ../.npm-cache install
npm --cache ../.npm-cache run dev
```

打开 http://localhost:5174 ，默认账号 `admin / admin123`（部署前务必改密）。

## 构建

```bash
npm run build            # 产物 dist/，可直接用静态服务器托管
VITE_API_BASE=https://api.example.com npm run build
```

## 页面

| 路由 | 能力 |
|---|---|
| `/menus` | 周菜单列表、新建草稿、发布前检查、发布 |
| `/menus/:id` | 7 天餐次明细、逐日编辑早/午/晚菜品版本、检查与发布 |
| `/catalog` | 食材与菜品维护、菜品版本与 BOM 编辑 |
| `/reviews` | 全局复核事项（蛋白越界 / 份量触界 / 价格兜底），处理后可放行下单 |
| `/pricing` | 定价版本：新建草稿 → 复核 → 发布；维护已审核替换选项（审核即冻结成本） |
| `/orders` | 订单筛选、详情、退款、配送批次状态 |
| `/kitchen` | 生产批次生成、登记产量与领料、食材需求 |
| `/cost` | 三层成本报表与成本参数 |
| `/users` | 查询用户余额、发放 credit、人工调整、冲正 |

## 约定

- 所有写操作都要求登录（Bearer 令牌），401 自动跳回登录页。
- 改价、发 credit、退款、冲正等敏感操作在后端均留审计或流水。
