#!/usr/bin/env python3
"""演示脚本：账户 → 下单 → 锁单 → 账本 → 退款 → 余额不足（P3 交易域端到端）。

前置：服务已启动，且已执行 demo-menu-seed.py 与 demo-plan-seed.py。
用法：python3 scripts/demo/demo-order-seed.py [服务地址]
"""
import json, sys, urllib.request, urllib.error
BASE=sys.argv[1] if len(sys.argv)>1 else "http://127.0.0.1:8080"
TOKEN=None
def call(method, path, body=None, auth=True, token=None):
    data=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(BASE+path, data=data, method=method)
    req.add_header("Content-Type","application/json")
    t = token or TOKEN
    if auth and t: req.add_header("Authorization","Bearer "+t)
    try:
        with urllib.request.urlopen(req) as r: return json.loads(r.read().decode())
    except urllib.error.HTTPError as e: return json.loads(e.read().decode())
def data(r,label):
    if r.get("code")!=0: raise SystemExit(f"[FAIL] {label}: {r}")
    return r["data"]

admin=data(call("POST","/admin/auth/login",{"username":"admin","password":"admin123"},auth=False),"admin")["token"]
member=data(call("POST","/app/auth/login",{"code":"demo-member-001"},auth=False),"login")["token"]
uid=data(call("GET","/app/me",token=member),"me")["id"]
print(f"[1] 会员 userId={uid}")

# 发 credit
data(call("POST",f"/admin/meal/wallet/{uid}/credit",{"amount":500,"reason":"阶段一内部体验发放"},token=admin),"grant")
w=data(call("GET","/app/wallet",token=member),"wallet")
print(f"[2] 发放 credit 500 → 账户余额 {w['balance']}（赠送 {w['creditBalance']} / 现金 {w['cashBalance']}）")

# 下单
order=data(call("POST","/app/orders",{"serviceDate":"2026-11-19","contactName":"张三","contactPhone":"13800000000","address":"新乡市红旗区示例路 1 号"},token=member),"order")
oid=order["id"]
print(f"[3] 下单 {order['orderNo']} 状态={order['statusLabel']} 原价 ¥{order['originalAmount']}")

# 锁单
run=data(call("POST","/admin/meal/orders/lock-run",{"serviceDate":"2026-11-19"},token=admin),"lock")
print(f"[4] 锁单任务: 总数={run['total']} 成功={run['locked']} 失败={run['failed']} 跳过={run['skipped']}")
d=data(call("GET",f"/admin/meal/orders/{oid}",token=admin),"detail")
print(f"[5] 订单状态={d['statusLabel']} 余额抵扣 ¥{d['balanceAmount']}（赠送 ¥{d['creditAmount']} / 现金 ¥{d['cashAmount']}） 快照={d['hasSnapshot']} 配送批次={[x['deliveryBatch'] for x in d['deliveries']]}")

w=data(call("GET","/app/wallet",token=member),"wallet2")
print(f"[6] 锁单后余额 {w['balance']}（赠送 {w['creditBalance']}）")

# 重复锁单
run2=data(call("POST","/admin/meal/orders/lock-run",{"serviceDate":"2026-11-19"},token=admin),"lock2")
print(f"[7] 重复锁单任务: 总数={run2['total']}（幂等，无重复扣款）")

# 账本
led=data(call("GET","/app/wallet/ledger?pageNo=1&pageSize=5",token=member),"ledger")
print("[8] 账本流水:")
for row in led["list"]:
    print(f"    {row['bizType']:<12} {row['direction']:<3} {row['source']:<6} ¥{row['amount']:>8}  余额 {row['balanceBefore']} → {row['balanceAfter']}  {row['reason']}")

# 退款
data(call("POST",f"/admin/meal/orders/{oid}/refund",{"reason":"运营取消"},token=admin),"refund")
w=data(call("GET","/app/wallet",token=member),"wallet3")
d=data(call("GET",f"/admin/meal/orders/{oid}",token=admin),"detail2")
print(f"[9] 退款后订单状态={d['statusLabel']} 余额={w['balance']}（赠送 {w['creditBalance']} / 现金 {w['cashBalance']}）")

# 余额不足用例
m2=data(call("POST","/app/auth/login",{"code":"demo-member-poor","nickname":"余额不足用户"},auth=False),"login2")["token"]
# 用正常体型建档，避免触发蛋白范围复核把下单拦住（那是 review-gate 脚本的演示内容）
data(call("PUT","/app/profile",{"gender":"FEMALE","birthDate":"1998-05-01","heightCm":165,"weightKg":62,
    "activityLevel":"MODERATE","goal":"MAINTAIN","appetiteLevel":"NORMAL","allergies":""},token=m2),"onboard2")
data(call("POST","/app/plan/generate",{"weekDate":"2026-11-16"},token=m2),"plan2")
data(call("POST",f"/admin/meal/wallet/{data(call('GET','/app/me',token=m2),'me2')['id']}/credit",{"amount":5,"reason":"仅 5 元"},token=admin),"grant2")
o2=data(call("POST","/app/orders",{"serviceDate":"2026-11-20","contactName":"李四","contactPhone":"13900000000","address":"新乡市卫滨区示例路 2 号"},token=m2),"order2")
run3=data(call("POST","/admin/meal/orders/lock-run",{"serviceDate":"2026-11-20"},token=admin),"lock3")
d2=data(call("GET",f"/admin/meal/orders/{o2['id']}",token=admin),"detail3")
print(f"[10] 余额不足场景: 锁单结果 成功={run3['locked']} 失败={run3['failed']}；订单仍为 {d2['statusLabel']}，原因：{d2['lockFailReason']}")
