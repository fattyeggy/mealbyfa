#!/usr/bin/env python3
"""演示数据脚本：会员建档 + 个人计划（P2 计划与计价端到端）。

前置：服务已启动，且已执行 demo-menu-seed.py（需要已发布的周菜单）。
用法：python3 scripts/demo/demo-plan-seed.py [服务地址]
"""
import json, urllib.request, urllib.error
import sys
BASE=sys.argv[1] if len(sys.argv)>1 else "http://127.0.0.1:8080"; TOKEN=None
def call(method, path, body=None, auth=True):
    data=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(BASE+path, data=data, method=method)
    req.add_header("Content-Type","application/json")
    if auth and TOKEN: req.add_header("Authorization","Bearer "+TOKEN)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e: return json.loads(e.read().decode())
def data(r,label):
    if r.get("code")!=0: raise SystemExit(f"[FAIL] {label}: {r}")
    return r["data"]

TOKEN=data(call("POST","/app/auth/login",{"code":"demo-member-001","nickname":"演示会员"},auth=False),"member login")["token"]
print("[1] 小程序登录成功")

prof=data(call("PUT","/app/profile",{"gender":"MALE","birthDate":"1996-03-08","heightCm":175,"weightKg":75,
    "targetWeightKg":70,"activityLevel":"MODERATE","goal":"LOSE","trainingState":"每周三次力量训练",
    "appetiteLevel":"NORMAL","dislikes":"香菜","allergies":"花生","proteinPref":"鸡胸肉,鱼"}),"onboard")
print(f"[2] Onboarding 完成，过敏原={prof['allergies']}")

plan=data(call("POST","/app/plan/generate",{"weekDate":"2026-11-16"}),"generate")
print(f"[3] 计划生成 planId={plan['planId']} 周起始={plan['weekStart']}")
print(f"    每日目标 蛋白={plan['dailyProteinG']}g 热量={plan['dailyKcal']}kcal 营养点={plan['dailyPoints']}点")
print(f"    全周价格合计 ¥{plan['weekPrice']}")
d=plan["days"][2]
print(f"[4] {d['serviceDate']} 合计 重量={d['totalWeightG']}g 蛋白={d['totalProteinG']}g 热量={d['totalKcal']}kcal 营养点={d['totalPoints']} 价格=¥{d['totalPrice']}")
for m in d["meals"]:
    print(f"    {m['mealLabel']:<3} {m['dishName'][:14]:<16} {m['weightG']}g {m['proteinG']}g蛋白 {m['points']}点 ¥{m['price']}")
print(f"[5] 过敏冲突提示: {plan['warnings'] or '无'}")

week=data(call("GET","/app/plan/week?date=2026-11-18"),"week")
print(f"[6] 周计划读取: 天数={len(week['days'])} 总价=¥{week['weekPrice']}")

# 用晚餐菜品版本替换午餐做试算
dinner_version = d["meals"][2]["dishVersionId"]
try:
    pv=data(call("POST","/app/plan/swap/preview",{"serviceDate":d["serviceDate"],"mealType":"LUNCH","dishVersionId":dinner_version}),"swap")
    print(f"[7] 替换试算 {pv['fromDishName'][:12]} → {pv['toDishName'][:12]}: {pv['before']['weightG']}g→{pv['after']['weightG']}g 差价¥{pv['priceDelta']}")
except SystemExit as e:
    msg=str(e)
    if "1209" in msg or "尚未审核" in msg:
        print(f"[7] 替换试算被拒（预期：替换选项未审核）: {msg}")
    else:
        print(f"[7] 替换试算被拒（可能是过敏拦截）: {msg}")

# 未建档用户
TOKEN=data(call("POST","/app/auth/login",{"code":"demo-member-noprofile"},auth=False),"login2")["token"]
r=call("POST","/app/plan/generate",{"weekDate":"2026-11-16"})
print(f"[8] 未建档生成计划 → code={r['code']} msg={r['msg']}")
