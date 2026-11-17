#!/usr/bin/env python3
"""演示脚本：生产合并 → 食材需求 → 领料与产量 → 三层成本报表（P4 后场域端到端）。

前置：服务已启动，且已执行 demo-menu-seed.py / demo-plan-seed.py。
用法：python3 scripts/demo/demo-kitchen-seed.py [服务地址]
"""
import json, sys, urllib.request, urllib.error
BASE=sys.argv[1] if len(sys.argv)>1 else "http://127.0.0.1:8080"
TOKEN=None
def call(method, path, body=None, auth=True, token=None):
    data=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(BASE+path, data=data, method=method)
    req.add_header("Content-Type","application/json")
    t=token or TOKEN
    if auth and t: req.add_header("Authorization","Bearer "+t)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e: return json.loads(e.read().decode())
def data(r,label):
    if r.get("code")!=0: raise SystemExit(f"[FAIL] {label}: {r}")
    return r["data"]

admin=data(call("POST","/admin/auth/login",{"username":"admin","password":"admin123"},auth=False),"admin")["token"]
member=data(call("POST","/app/auth/login",{"code":"demo-member-001"},auth=False),"member")["token"]
uid=data(call("GET","/app/me",token=member),"me")["id"]
print(f"[1] 会员 userId={uid}")

data(call("POST",f"/admin/meal/wallet/{uid}/credit",{"amount":200,"reason":"后场演示"},token=admin),"grant")
order=data(call("POST","/app/orders",{"serviceDate":"2026-11-22","contactName":"张三","contactPhone":"13800000000","address":"新乡市红旗区示例路 1 号"},token=member),"order")
print(f"[2] 下单 {order['orderNo']} 原价 ¥{order['originalAmount']}")
run=data(call("POST","/admin/meal/orders/lock-run",{"serviceDate":"2026-11-22"},token=admin),"lock")
print(f"[3] 锁单 成功={run['locked']} 失败={run['failed']}")

s=data(call("POST","/admin/meal/kitchen/generate?serviceDate=2026-11-22",token=admin),"gen")
print(f"[4] 生产汇总 批次={len(s['batches'])} 计划份数={s['totalPlanQty']} 分装标签={len(s['labels'])}")
for b in s["batches"]:
    print(f"    {b['batchLabel']:<12} {b['dishName'][:16]:<18} 计划{b['planQty']}份 状态={b['status']}")
print("    标签示例:", {k:s['labels'][0][k] for k in ('orderNo','mealLabel','dishName','weightG','allergens')})

d=data(call("GET","/admin/meal/kitchen/material-demand?serviceDate=2026-11-22",token=admin),"demand")
print(f"[5] 食材需求 合计成本 ¥{d['totalCost']}")
for it in d["items"]:
    print(f"    {it['ingredientName'][:18]:<20} 可食{it['edibleG']}g → 采购{it['purchaseG']}g ¥{it['cost']}")

b0=s["batches"][0]
data(call("POST",f"/admin/meal/kitchen/batches/{b0['id']}/output",{"actualQty":b0['planQty'],"wasteQty":0,"remark":"正常出餐"},token=admin),"output")
ing=data(call("GET","/admin/meal/kitchen/material-demand?serviceDate=2026-11-22",token=admin),"demand2")["items"][0]
data(call("POST",f"/admin/meal/kitchen/batches/{b0['id']}/issue",{"ingredientId":ing["ingredientId"],"actualG":float(ing["edibleG"])+15,"wasteG":10,"reason":"边角料"},token=admin),"issue")
s2=data(call("GET","/admin/meal/kitchen/summary?serviceDate=2026-11-22",token=admin),"summary2")
print(f"[6] 登记后 批次状态={s2['batches'][0]['status']} 实做={s2['batches'][0]['actualQty']} 报废={s2['batches'][0]['wasteQty']}")

rep=data(call("GET","/admin/meal/cost/report?start=2026-11-16&end=2026-11-22",token=admin),"report")
t=rep["totals"]
print(f"[7] 成本报表 {rep['start']}~{rep['end']}")
print(f"    订单={t['orders']} 餐次={t['meals']} 实收=¥{t['revenue']}")
print(f"    食材=¥{t['foodCost']} 包装=¥{t['packageCost']} 配送=¥{t['deliveryCost']} 手续费=¥{t['paymentFee']} 固定=¥{t['fixedCost']}")
print(f"    总成本=¥{t['totalCost']} 毛利=¥{t['grossProfit']} 食材成本率={float(t['foodCostRate'])*100:.2f}% 总成本率={float(t['totalCostRate'])*100:.2f}%")
for row in rep["days"]:
    print(f"    {row['serviceDate']}: 订单{row['orders']} 实收¥{row['revenue']} 总成本¥{row['totalCost']} 毛利¥{row['grossProfit']}")
