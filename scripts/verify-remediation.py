#!/usr/bin/env python3
"""真实接口验收：一券多用、连续替换、配送跨日、快照不可变、拒绝无副作用。

前置：后端已启动（开发开关 meal.pricing.allow-unreviewed-swap=true），
并已执行 demo-menu-seed.py 发布 2026-11-16 当周菜单。

用法：python3 scripts/verify-remediation.py http://127.0.0.1:8080
"""
import json
import sys
import threading
import urllib.error
import urllib.request
from decimal import Decimal

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080"
D1, D2 = "2026-11-18", "2026-11-19"
results = []


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def ok(method, path, body=None, token=None):
    r = call(method, path, body, token)
    assert r.get("code") == 0, f"{method} {path} -> {r}"
    return r["data"]


def check(name, passed, detail=""):
    results.append((name, passed, detail))
    print(("PASS " if passed else "FAIL ") + name + ("  " + detail if detail else ""))


def login_admin():
    return ok("POST", "/admin/auth/login", {"username": "admin", "password": "admin123"})["token"]


def member(code, weight=70):
    token = ok("POST", "/app/auth/login", {"code": code, "nickname": code})["token"]
    uid = ok("GET", "/app/me", token=token)["id"]
    ok("PUT", "/app/profile", {
        "gender": "MALE", "birthDate": "1995-06-12", "heightCm": 178, "weightKg": weight,
        "activityLevel": "MODERATE", "goal": "MAINTAIN", "appetiteLevel": "NORMAL", "allergies": ""
    }, token)
    ok("POST", "/app/plan/generate", {"weekDate": "2026-11-16"}, token)
    return token, uid


def grant_coupon(admin, uid, amount=10):
    tpl = ok("POST", "/admin/meal/coupon-templates", {
        "name": f"验收券{amount}", "type": "CASH", "thresholdAmount": 50,
        "discountAmount": amount, "validDays": 30}, token=admin)
    return ok("POST", "/admin/meal/coupons/grant", {"userId": uid, "templateId": tpl}, token=admin)["id"]


def coupon_status(admin, uid, coupon_id):
    rows = ok("GET", f"/admin/meal/coupons?userId={uid}", token=admin)
    return next((c["status"] for c in rows if c["id"] == coupon_id), None)


def credit(admin, uid, amount):
    ok("POST", f"/admin/meal/wallet/{uid}/credit", {"amount": amount, "reason": "验收"}, token=admin)


def main():
    admin = login_admin()

    # 1) 同一张券不能用于第二张订单
    tb, uid = member("verify-coupon")
    credit(admin, uid, 500)
    coupon = grant_coupon(admin, uid)
    first = ok("POST", "/app/orders", {"serviceDate": D1, "couponId": coupon,
                                       "contactName": "甲", "contactPhone": "13800000001",
                                       "address": "验收路 1 号"}, token=tb)
    second = call("POST", "/app/orders", {"serviceDate": D2, "couponId": coupon,
                                          "contactName": "甲", "contactPhone": "13800000001",
                                          "address": "验收路 1 号"}, token=tb)
    check("一券只能成功使用一次", second.get("code") != 0 and "优惠券" in second.get("msg", ""),
          f"第二单 code={second.get('code')} msg={second.get('msg')}")
    check("首单已按券优惠", Decimal(str(first["couponAmount"])) > 0,
          f"couponAmount={first['couponAmount']}")
    check("券状态为 RESERVED", coupon_status(admin, uid, coupon) == "RESERVED")

    # 2) 并发使用同一张券只能成功一次
    tc, uidc = member("verify-concurrent")
    credit(admin, uidc, 500)
    coupon_c = grant_coupon(admin, uidc)
    outcomes = []

    def place(date):
        outcomes.append(call("POST", "/app/orders", {
            "serviceDate": date, "couponId": coupon_c,
            "contactName": "乙", "contactPhone": "13800000002", "address": "验收路 2 号"}, token=tc))

    threads = [threading.Thread(target=place, args=(d,)) for d in (D1, D2)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    successes = [o for o in outcomes if o.get("code") == 0]
    check("并发使用同一张券只成功一次", len(successes) == 1,
          f"成功 {len(successes)} / 尝试 {len(outcomes)}")

    # 3) 锁单失败不核销券，取消后释放
    td, uidd = member("verify-lockfail")
    credit(admin, uidd, 1)
    coupon_d = grant_coupon(admin, uidd, 5)
    order_d = ok("POST", "/app/orders", {"serviceDate": D1, "couponId": coupon_d,
                                         "contactName": "丙", "contactPhone": "13800000003",
                                         "address": "验收路 3 号"}, token=td)
    run = ok("POST", "/admin/meal/orders/lock-run", {"serviceDate": D1}, token=admin)
    check("锁单失败", run["failed"] >= 1, f"locked={run['locked']} failed={run['failed']}")
    check("锁单失败券未被核销", coupon_status(admin, uidd, coupon_d) == "RESERVED")
    ok("POST", f"/app/orders/{order_d['id']}/cancel", {"reason": "验收取消"}, token=td)
    check("取消后券释放", coupon_status(admin, uidd, coupon_d) == "UNUSED")

    # 4) A→B→A 回到原价
    te, uide = member("verify-swap")
    day = ok("GET", f"/app/plan/day?date={D1}", token=te)
    original = int(day["dailyPrice"])
    meals = day["meals"]
    lunch = next(m for m in meals if m["mealType"] == "LUNCH")
    dinner = next(m for m in meals if m["mealType"] == "DINNER")
    ok("POST", "/app/plan/swap", {"serviceDate": D1, "mealType": "LUNCH",
                                  "dishVersionId": dinner["dishVersionId"]}, token=te)
    after_out = int(ok("GET", f"/app/plan/day?date={D1}", token=te)["dailyPrice"])
    ok("POST", "/app/plan/swap", {"serviceDate": D1, "mealType": "LUNCH",
                                  "dishVersionId": lunch["dishVersionId"]}, token=te)
    after_back = ok("GET", f"/app/plan/day?date={D1}", token=te)
    check("替换后价格变化", after_out != original, f"{original} → {after_out}")
    check("换回原菜价格回到原价", int(after_back["dailyPrice"]) == original,
          f"{after_out} → {after_back['dailyPrice']}")
    check("换回后替换差价归零", Decimal(str(after_back["swapDifference"])) == 0)

    # 5) 配送跨日归属
    tf, uidf = member("verify-delivery")
    credit(admin, uidf, 500)
    order_f = ok("POST", "/app/orders", {"serviceDate": D1, "contactName": "丁",
                                         "contactPhone": "13800000004", "address": "验收路 4 号"}, token=tf)
    ok("POST", "/admin/meal/orders/lock-run", {"serviceDate": D1}, token=admin)
    detail = ok("GET", f"/admin/meal/orders/{order_f['id']}", token=admin)
    deliveries = detail["deliveries"]
    breakfast = next((d for d in deliveries if d.get("deliverySlot") == "BREAKFAST"), None)
    check("配送记录按实际日期拆分", len(deliveries) == 3, f"deliveries={len(deliveries)}")
    check("早餐归属前一天晚餐批次",
          breakfast is not None and breakfast["deliveryBatch"] == "BATCH_2"
          and breakfast["actualDeliveryDate"] == "2026-11-17",
          str(breakfast))

    # 5.1 配送状态按单条更新：完成晚餐不影响次日早餐
    dinner = next(d for d in deliveries if d.get("deliverySlot") == "DINNER")
    ok("POST", f"/admin/meal/orders/{order_f['id']}/delivery",
       {"deliveryId": dinner["id"], "status": "DONE"}, token=admin)
    after_status = ok("GET", f"/admin/meal/orders/{order_f['id']}", token=admin)["deliveries"]
    by_slot = {d["deliverySlot"]: d for d in after_status}
    check("完成晚餐只更新该条配送",
          by_slot["DINNER"]["status"] == "DONE" and by_slot["BREAKFAST"]["status"] == "PENDING"
          and by_slot["LUNCH"]["status"] == "PENDING",
          str({k: v["status"] for k, v in by_slot.items()}))

    # 6) 快照不可变：改价后再读快照
    snap_before = ok("GET", f"/admin/meal/orders/{order_f['id']}/snapshot", token=admin)["payload"]
    amount_before = ok("GET", f"/admin/meal/orders/{order_f['id']}", token=admin)["originalAmount"]
    v = ok("POST", "/admin/meal/pricing/versions", {"versionNo": "VERIFY-" + str(uidf),
                                                    "dailyBasePrice": 70}, token=admin)
    ok("POST", f"/admin/meal/pricing/versions/{v}/review", token=admin)
    ok("POST", f"/admin/meal/pricing/versions/{v}/publish", token=admin)
    snap_after = ok("GET", f"/admin/meal/orders/{order_f['id']}/snapshot", token=admin)["payload"]
    amount_after = ok("GET", f"/admin/meal/orders/{order_f['id']}", token=admin)["originalAmount"]
    check("改价后快照不变", snap_before == snap_after)
    check("改价后订单金额不变", Decimal(str(amount_before)) == Decimal(str(amount_after)))
    check("快照含 BOM 与营养", '"bom"' in snap_before and '"nutrition"' in snap_before)

    # 7) 被拒绝的下单无副作用
    balance_before = Decimal(str(ok("GET", f"/admin/meal/wallet/{uid}", token=admin)["balance"]))
    batches_before = len(ok("GET", f"/admin/meal/kitchen/summary?serviceDate={D2}", token=admin)["batches"])
    rejected = call("POST", "/app/orders", {"serviceDate": D2, "couponId": coupon,
                                            "contactName": "甲", "contactPhone": "13800000001",
                                            "address": "验收路 1 号"}, token=tb)
    balance_after = Decimal(str(ok("GET", f"/admin/meal/wallet/{uid}", token=admin)["balance"]))
    batches_after = len(ok("GET", f"/admin/meal/kitchen/summary?serviceDate={D2}", token=admin)["batches"])
    check("拒绝下单不扣款", rejected.get("code") != 0 and balance_before == balance_after,
          f"balance {balance_before} → {balance_after}")
    check("拒绝下单不生成生产记录", batches_before == batches_after,
          f"batches {batches_before} → {batches_after}")

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} 项通过")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
