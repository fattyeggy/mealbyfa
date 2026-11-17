#!/usr/bin/env python3
"""演示数据脚本：东京主题周（P1 菜单域端到端）。

用法：先启动后端服务，再执行
    python3 scripts/demo/demo-menu-seed.py [服务地址]

会依次创建 4 条食材、3 组菜品版本、1 个周菜单（21 餐次），
跑发布前检查并发布，最后验证公共菜单与发布后冻结行为。
"""
import json, sys, urllib.request, urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
TOKEN = None

def call(method, path, body=None, auth=True):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if auth and TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode("utf-8"))

def data(resp, label):
    if resp.get("code") != 0:
        raise SystemExit(f"[FAIL] {label}: {resp}")
    return resp["data"]

# 1. 登录
TOKEN = data(call("POST", "/admin/auth/login", {"username": "admin", "password": "admin123"}, auth=False), "login")["token"]
print("[1] 后台登录成功")

# 2. 食材
def ing(name, price, edible, usage="kg", conv=1):
    return data(call("POST", "/admin/meal/ingredients", {
        "name": name, "category": "蛋白", "purchaseUnit": "kg", "usageUnit": usage,
        "conversionRate": conv, "edibleRate": edible, "movingAvgPrice": price, "status": 0}), f"ingredient {name}")

chicken = ing("鸡胸肉P1", 26.00, 0.90)
rice    = ing("杂粮米P1", 8.00, 1.0)
broccoli= ing("西兰花P1", 12.00, 0.80)
salmon  = ing("味噌三文鱼P1", 68.00, 0.95)
print("[2] 食材建档 4 条")

# 3. 菜品 + 版本 + BOM
def dish(name, category, storage="0-4℃ 冷藏", reheat="微波 90 秒"):
    return data(call("POST", "/admin/meal/dishes", {
        "name": name, "category": category, "description": name + " 描述",
        "storageMethod": storage, "reheatMethod": reheat, "status": 0}), f"dish {name}")

def version(dish_id, weight, kcal, protein, bom):
    return data(call("POST", f"/admin/meal/dishes/{dish_id}/versions", {
        "netWeightG": weight, "kcal": kcal, "proteinG": protein, "carbG": protein*2, "fatG": 15,
        "bom": [{"ingredientId": i, "netWeightG": w} for i, w in bom]}), "version")

d1 = dish("日式厚蛋鸡肉三明治P1", "早餐")
v1 = version(d1, 320, 420, 28, [(chicken, 120)])
d2 = dish("照烧鸡腿杂粮饭P1", "午餐")
v2 = version(d2, 720, 760, 42, [(chicken, 150), (rice, 180), (broccoli, 100)])
d3 = dish("味噌三文鱼P1", "晚餐")
v3 = version(d3, 640, 700, 45, [(salmon, 160), (rice, 120), (broccoli, 80)])
print("[3] 菜品与版本 3 组，成本自动计算")

# 4. 周菜单
menu = data(call("POST", "/admin/meal/menus", {
    "themeName": "东京主题周", "region": "新乡", "startDate": "2026-11-16", "version": "V1.0"}), "menu")
print(f"[4] 周菜单创建 id={menu}")

slots = []
for i in range(7):
    date = f"2026-11-{16+i:02d}"
    for t, v in (("BREAKFAST", v1), ("LUNCH", v2), ("DINNER", v3)):
        slots.append({"serviceDate": date, "mealType": t, "dishVersionId": v})
data(call("PUT", f"/admin/meal/menus/{menu}/meals", {"meals": slots}), "meals")
print(f"[5] 写入 {len(slots)} 个餐次")

# 5. 检查 + 发布
chk = data(call("GET", f"/admin/meal/menus/{menu}/check"), "check")
print(f"[6] 发布前检查 passed={chk['passed']} 标准成本={chk['standardCost']}")
for it in chk["items"]:
    print(f"     {it['status']:<7} {it['name']} — {it['detail']}")
data(call("POST", f"/admin/meal/menus/{menu}/publish"), "publish")
print("[7] 已发布")

# 6. 发布后不可改
after = call("PUT", f"/admin/meal/menus/{menu}", {"themeName": "改名"})
print(f"[8] 发布后改名 → code={after['code']} msg={after['msg']}")

# 7. 公共菜单（未登录）
pub = data(call("GET", "/app/menu/week?date=2026-11-18", auth=False), "public week")
total = sum(len(d["meals"]) for d in pub["days"])
print(f"[9] 未登录读公共菜单: 主题={pub['themeName']} 已发布={pub['published']} 天数={len(pub['days'])} 餐次={total}")
print(f"    首日合计 重量={pub['days'][0]['totalWeightG']}g 蛋白={pub['days'][0]['totalProteinG']}g 热量={pub['days'][0]['totalKcal']}kcal 营养点={pub['days'][0]['totalPoints']}")

# 8. 未发布周
draft = data(call("GET", "/app/menu/week?date=2026-11-25", auth=False), "draft week")
print(f"[10] 未发布周: 已发布={draft['published']} 餐次={sum(len(d['meals']) for d in draft['days'])} 预计发布={draft['expectedPublishTime']}")
