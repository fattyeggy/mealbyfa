#!/usr/bin/env python3
"""演示脚本：P4.6 边界与复核门禁端到端。

覆盖：150kg 用户生成计划（不再 500）、复核明细、下单拒绝与放行、
重建后复核重新打开、未审核替换拒绝、非法定价参数拒绝。

前置：服务已启动并执行 demo-menu-seed.py。
用法：python3 scripts/demo/demo-review-gate.py [服务地址]
"""
import json, sys, urllib.request, urllib.error
BASE=sys.argv[1]
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
tok=data(call("POST","/app/auth/login",{"code":"p46-150kg","nickname":"150kg"}),"login")["token"]
uid=data(call("GET","/app/me",token=tok),"me")["id"]
data(call("PUT","/app/profile",{"gender":"MALE","birthDate":"1996-01-01","heightCm":175,"weightKg":150,
    "activityLevel":"MODERATE","goal":"MAINTAIN","appetiteLevel":"NORMAL","allergies":""},token=tok),"onboard")
data(call("POST",f"/admin/meal/wallet/{uid}/credit",{"amount":500,"reason":"P4.6 演练"},token=admin),"credit")

plan=call("POST","/app/plan/generate",{"weekDate":"2026-11-16"},token=tok)
print(f"[1] 150kg 生成计划 code={plan['code']}")
p=plan["data"]
print(f"    rawTarget={p['rawTargetProteinG']}g  计划目标={p['dailyProteinG']}g  needsReview={p['needsReview']}")
print(f"    复核事项 {len(p['reviews'])} 条，前三条：")
for r in p["reviews"][:3]:
    print(f"      - {r['reviewType']} {r.get('serviceDate') or ''} {r.get('mealType') or ''}：{r['message']}")
print(f"    摘要：{p['reviewNotes'][0][:80]}...")

o=call("POST","/app/orders",{"serviceDate":"2026-11-18","contactName":"张三","contactPhone":"13800000000","address":"新乡市示例路1号"},token=tok)
print(f"[2] 下单被拒 code={o['code']} msg={o['msg'][:70]}")
w=data(call("GET","/app/wallet",token=tok),"wallet")
print(f"    钱包未扣款：余额 {w['balance']}")

pid=p["planId"]
revs=data(call("GET",f"/admin/meal/plans/{pid}/reviews?status=PENDING",token=admin),"reviews")
for r in revs:
    data(call("POST",f"/admin/meal/reviews/{r['id']}/resolve",{"note":"运营已确认"},token=admin),"resolve")
print(f"[3] 已处理 {len(revs)} 条复核")
o2=call("POST","/app/orders",{"serviceDate":"2026-11-18","contactName":"张三","contactPhone":"13800000000","address":"新乡市示例路1号"},token=tok)
print(f"[4] 复核完成后下单 code={o2['code']} 状态={o2['data']['statusLabel'] if o2['code']==0 else o2['msg']}")

plan2=data(call("POST","/app/plan/generate",{"weekDate":"2026-11-16"},token=tok),"regen")
print(f"[5] 再次重建计划 → 新 planId={plan2['planId']} needsReview={plan2['needsReview']} 复核 {len(plan2['reviews'])} 条（重新打开）")

# R08：默认配置下未审核替换被拒
day=plan2["days"][2]
dinner=day["meals"][2]
sw=call("POST","/app/plan/swap/preview",{"serviceDate":day["serviceDate"],"mealType":"LUNCH","dishVersionId":dinner["dishVersionId"]},token=tok)
print(f"[6] 未审核替换（生产开关默认关闭）code={sw['code']} msg={sw['msg'][:50]}")

# R11 非法定价参数
bad=call("POST","/admin/meal/pricing/versions",{"versionNo":"BAD-E2E","dailyBasePrice":66,"minTargetProteinG":300,"maxTargetProteinG":100},token=admin)
print(f"[7] 非法定价参数 code={bad['code']} msg={bad['msg']}")
