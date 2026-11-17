# 三餐私教 · 小程序端（UniApp Vue3）

微信小程序端的 H5 可运行版本，接口对接本仓库 `backend/`。

## 运行

```bash
# 1) 先启动后端（默认 8080）
cd ../backend && mvn -DskipTests package && java -jar target/sancan-meal-service.jar

# 2) 启动 H5 开发态（5173，/api 已代理到 8080）
npm --cache ../.npm-cache install
npm --cache ../.npm-cache run dev:h5
```

打开 http://localhost:5173 即可；用 Chrome DevTools 的手机模拟（430×932）查看效果最接近小程序。

## 依赖版本（不要随意升级）

uni-app 对 Vue 版本敏感，本工程锁定：

```
vue 3.4.21
@dcloudio/uni-app / uni-h5 / vite-plugin-uni / uni-mp-weixin / uni-mp-vue
  = 3.0.0-4080720251210001
vite 5.2.8   （该 uni-app 版本的 peer 要求）
```

升级 Vue 或 uni-app 前，请先同时验证 `build:h5` 与 `build:mp-weixin`，否则容易出现
`isInSSRComponentSetup is not exported` 这类运行时导出不匹配。

## 构建

```bash
npm run build:h5          # 产物 dist/build/h5
npm run build:mp-weixin   # 微信小程序产物 dist/build/mp-weixin，用微信开发者工具导入
```

小程序构建需要填写 `src/manifest.json` 的 `mp-weixin.appid`；接口地址通过环境变量注入：

```bash
VITE_API_BASE=https://api.example.com npm run build:mp-weixin
```

## 演示账号

`.env.local` 里的 `VITE_DEMO_CODE` 决定演示账号（默认 `demo-h5-001`）。
换账号时清掉浏览器 localStorage，或用「我的 → 切换演示账号」。

`scripts/demo/` 下的演示脚本可生成菜单、计划、订单与本页面所需的替换选项：

```bash
python3 ../scripts/demo/demo-menu-seed.py http://127.0.0.1:8080
python3 ../scripts/demo/demo-plan-seed.py http://127.0.0.1:8080
```

## 目录

```
src/
├── pages/index        首页（未建档 / 已建档两态，周菜单 Slide、日价下单）
├── pages/onboarding   建档
├── pages/plan         周计划、逐餐替换与试算
├── pages/orders       订单记录与取消
├── pages/me           余额、优惠券、流水、档案
├── components/TabBar  自定义底部导航
├── store/session.ts   会话与档案状态
└── utils/api.ts       接口封装（自动登录、401 重试）
```

## 价格展示规则

严格按《计价规则 V1.0》§9：展示「计划蛋白 / 基础三餐 66 元 / 加量费 / 日价 / 替换补差 / 优惠券 / 实付」，
不出现「总重量 × 单价」，餐价只作为日价拆分。
