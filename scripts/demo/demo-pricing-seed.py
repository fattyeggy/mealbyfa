#!/usr/bin/env python3
"""演示脚本：计价规则 V1.0 端到端（P4.5）。

会打印标准购价表、个人计划日价与三餐拆分、替换差价试算，并下一单。
前置：服务已启动，且已执行 demo-menu-seed.py。
用法：python3 scripts/demo/demo-pricing-seed.py [服务地址]
"""
import json, sys, urllib.request, urllib.error
BASE=sys.argv[1] if len(sys.argv)>1 else "http://127.0.0.1:8080"
def call(method, path, body=None, token=None):
    data=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(BASE+path, data=data, method=method)
    req.add_header("Content-Type","application/json")
    if token: req.add_header("Authorization","Bearer "+token)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e: return json.loads(e.read().decode())
def data(r,label):
    if r.get("code")!=0: raise SystemExit(f"[FAIL] {label}: {r}")
    return r["data"]

admin=data(call("POST","/admin/auth/login",{"username":"admin","password":"admin123"}),"admin")["token"]
ver=data(call("GET","/admin/meal/pricing/active",token=admin),"active")
print(f"[1] 生效定价版本 {ver['versionNo']}：基础 {ver['dailyBasePrice']} 元 / 含 {ver['includedProteinG']}g / 加量 {ver['extraProteinPricePer10g']} 元每 10g / 系数 {ver['swapCostDivisor']}")

print("[2] 标准购价表（服务端实算）")
print("     计划蛋白   未取整   参考日价   三餐拆分(早/午/晚)")
for p in (80,100,120,135,150,180,200,220):
    q=data(call("GET",f"/admin/meal/pricing/quote?proteinG={p}",token=admin),"quote")
    print(f"     {p:>3}g {float(q['rawReferencePrice']):>10.2f} {q['dailyPrice']:>8}   {q['breakfastPrice']}/{q['lunchPrice']}/{q['dinnerPrice']}")

member=data(call("POST","/app/auth/login",{"code":"demo-pricing-001","nickname":"计价演示"}),"member")["token"]
data(call("PUT","/app/profile",{"gender":"MALE","birthDate":"1996-03-08","heightCm":175,"weightKg":75,
    "targetWeightKg":70,"activityLevel":"MODERATE","goal":"MAINTAIN","appetiteLevel":"NORMAL","allergies":"花生"},token=member),"onboard")
# 从 /app/plan/generate 拿计划
plan=data(call("POST","/app/plan/generate",{"weekDate":"2026-11-16"},token=member),"plan")
print(f"[3] 建档生成计划：计划蛋白 {plan['dailyProteinG']}g  版本 {plan['pricingVersionNo']}  待复核={plan['needsReview']}")
day=plan["days"][2]
print(f"[4] {day['serviceDate']} 日价 ¥{day['dailyPrice']}（未取整 {day['rawPrice']}，基础 {day['basePrice']} + 加量 {day['extraFee']}，替换差价 {day['swapDifference']}）")
for m in day["meals"]:
    print(f"     {m['mealLabel']} {m['dishName'][:14]:<16} {m['weightG']}g {m['proteinG']}g {m['points']}点 展示价 ¥{m['price']}")
print(f"     三餐展示价合计 = ¥{sum(float(m['price']) for m in day['meals']):.2f}")

# 换菜试算：用晚餐菜品替换午餐
dinner=day["meals"][2]
pv=call("POST","/app/plan/swap/preview",{"serviceDate":day["serviceDate"],"mealType":"LUNCH","dishVersionId":dinner["dishVersionId"]},token=member)
if pv.get("code")==0:
    pv=pv["data"]
    print(f"[5] 替换试算 {pv['fromDishName'][:12]} → {pv['toDishName'][:12]}")
    print(f"     变动成本 {pv['fromVariableCost']} → {pv['toVariableCost']}  差价 {pv['swapDifference']}")
    print(f"     日价 {pv['before']['dailyPrice']} → {pv['after']['dailyPrice']}  变化 {pv['priceDelta']}")
else:
    print(f"[5] 替换试算被拒（预期）：code={pv['code']} {pv['msg']}")
    print("     说明：替换选项必须由运营在定价版本中审核开放，生产开关默认关闭")

uid=data(call("GET","/app/me",token=member),"me")["id"]
data(call("POST",f"/admin/meal/wallet/{uid}/credit",{"amount":200,"reason":"计价演示"},token=admin),"credit")
order=data(call("POST","/app/orders",{"serviceDate":day["serviceDate"],"contactName":"张三","contactPhone":"13800000000","address":"新乡市示例路 1 号"},token=member),"order")
print(f"[6] 下单 {order['orderNo']} 标价 ¥{order['originalAmount']} 版本 {order['pricingVersionNo']} 未取整 {order['rawPrice']}")
