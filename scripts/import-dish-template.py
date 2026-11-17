#!/usr/bin/env python3
"""把《菜品导入模板.xlsx》导入后台：食材（含组件与营养）→ 菜品 → 菜品版本与 BOM。

用法：
    python3 scripts/import-dish-template.py docs/模板/菜品导入模板.xlsx \
        --api http://127.0.0.1:8080 --user admin --password admin123
    --dry-run 只校验与打印，不写库
"""
import argparse
import re
import json
import sys
import urllib.error
import urllib.request
from decimal import Decimal, ROUND_HALF_UP

from openpyxl import load_workbook

STATUS = {"上架": 0, "停用": 1}
COMPONENTS = {"蛋白主料": "PROTEIN", "主食": "STAPLE", "蔬菜": "VEGETABLE",
              "油脂": "FAT", "其他": "OTHER"}


def d(value, default="0"):
    if value is None or value == "":
        return Decimal(default)
    return Decimal(str(value))


def call(api, method, path, body=None, token=None):
    data = json.dumps(body, default=lambda o: float(o) if isinstance(o, Decimal) else str(o)).encode() \
        if body is not None else None
    req = urllib.request.Request(api + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def ok(api, method, path, body=None, token=None):
    r = call(api, method, path, body, token)
    if r.get("code") != 0:
        raise RuntimeError(f"{method} {path} 失败: {r}")
    return r.get("data")


def sheet_rows(wb, name, required):
    ws = wb[name]
    rows = list(ws.iter_rows(values_only=True))
    header = [str(c).strip() if c is not None else "" for c in rows[0]]
    idx = {h: i for i, h in enumerate(header)}
    missing = [r for r in required if r not in idx]
    if missing:
        raise SystemExit(f"工作表《{name}》缺少列：{missing}")
    result = []
    for row in rows[1:]:
        if row is None or all(c is None or str(c).strip() == "" for c in row):
            continue
        result.append({h: row[i] if i < len(row) else None for h, i in idx.items()})
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("--api", default="http://127.0.0.1:8080")
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    wb = load_workbook(args.xlsx, data_only=True)
    dishes = sheet_rows(wb, "菜品主表", ["菜品编号", "菜品名称", "分类"])
    ingredients = sheet_rows(wb, "食材明细", ["菜品编号", "食材名称", "组件类型", "每份净重(g)"])
    seasonings = sheet_rows(wb, "调料明细", ["菜品编号", "调料名称", "用量(g)"])

    # 组装：菜品 → 组件行
    by_dish = {}
    ingredient_master = {}
    for row in ingredients + seasonings:
        code = str(row["菜品编号"]).strip()
        is_seasoning = "用量(g)" in row
        name = str(row["调料名称"] if is_seasoning else row["食材名称"]).strip()
        grams = d(row["用量(g)"] if is_seasoning else row["每份净重(g)"])
        component = "OTHER" if is_seasoning else COMPONENTS.get(str(row["组件类型"]).strip(), "OTHER")
        kcal = d(row.get("每100g热量"))
        protein = d(row.get("每100g蛋白"))
        carb = d(row.get("每100g碳水"))
        fat = d(row.get("每100g脂肪"))
        key = name
        state = str(row.get("称重状态(生/熟)") or "熟").strip()
        outturn = d(row.get("出成率"), "1")
        cooked_grams = grams * (outturn if state == "生" else Decimal("1"))
        if key not in ingredient_master:
            ingredient_master[key] = {
                "name": name,
                "component": component,
                "kcal": kcal, "protein": protein, "carb": carb, "fat": fat,
                "purchaseUnit": "kg", "usageUnit": "kg", "conversionRate": 1,
                "edibleRate": d(row.get("可食率"), "1") if not is_seasoning else Decimal("1"),
                "price": d(row.get("每kg价格(元)")) if not is_seasoning else d(row.get("每kg价格(元)")),
                "allergens": (row.get("过敏原") or "") if not is_seasoning else (row.get("过敏原") or ""),
                "seasoning": is_seasoning,
                "state": state, "outturn": outturn,
            }
        else:
            if not is_seasoning and ingredient_master[key]["component"] != component:
                print(f"警告：食材 {name} 在不同菜品中组件类型不一致，按首次为准")
        by_dish.setdefault(code, []).append({"name": name, "grams": grams,
                                             "cooked": cooked_grams})

    # 营养合计
    computed = {}
    for code, lines in by_dish.items():
        kcal = sum((ingredient_master[l["name"]]["kcal"] * l["grams"] / 100 for l in lines), Decimal("0"))
        protein = sum((ingredient_master[l["name"]]["protein"] * l["grams"] / 100 for l in lines), Decimal("0"))
        carb = sum((ingredient_master[l["name"]]["carb"] * l["grams"] / 100 for l in lines), Decimal("0"))
        fat = sum((ingredient_master[l["name"]]["fat"] * l["grams"] / 100 for l in lines), Decimal("0"))
        weight = sum((l["cooked"] for l in lines), Decimal("0"))
        computed[code] = {
            "kcal": int(kcal.quantize(Decimal("1"), ROUND_HALF_UP)),
            "protein": protein.quantize(Decimal("0.1"), ROUND_HALF_UP),
            "carb": carb.quantize(Decimal("0.1"), ROUND_HALF_UP),
            "fat": fat.quantize(Decimal("0.1"), ROUND_HALF_UP),
            "weight": int(weight.quantize(Decimal("1"), ROUND_HALF_UP)),
        }

    print(f"菜品 {len(dishes)} 道，食材/调料 {len(ingredient_master)} 种")
    for dish in dishes:
        code = str(dish["菜品编号"]).strip()
        c = computed.get(code)
        print(f"  {code} {dish['菜品名称']} → {c['kcal']} kcal / "
              f"P{c['protein']} C{c['carb']} F{c['fat']} / {c['weight']}g")

    if args.dry_run:
        return

    token = ok(args.api, "POST", "/admin/auth/login",
               {"username": args.user, "password": args.password})["token"]

    id_by_name = {}
    for name, item in ingredient_master.items():
        body = {
            "name": name,
            "category": "调料" if item["seasoning"] else "组件",
            "purchaseUnit": item["purchaseUnit"],
            "usageUnit": item["usageUnit"],
            "conversionRate": item["conversionRate"],
            "edibleRate": item["edibleRate"],
            "movingAvgPrice": item["price"],
            "componentType": item["component"],
            "kcalPer100g": item["kcal"],
            "proteinPer100g": item["protein"],
            "carbPer100g": item["carb"],
            "fatPer100g": item["fat"],
            "allergens": item["allergens"],
            "status": 0,
        }
        id_by_name[name] = ok(args.api, "POST", "/admin/meal/ingredients", body, token)
    print(f"已导入食材 {len(id_by_name)} 种")

    for dish in dishes:
        code = str(dish["菜品编号"]).strip()
        c = computed[code]
        description = "\n".join(filter(None, [
            str(dish.get("描述") or ""),
            f"做法：{dish.get('做法步骤')}" if dish.get("做法步骤") else "",
            f"摆盘：{dish.get('摆盘说明')}" if dish.get("摆盘说明") else "",
        ]))
        allergens = sorted({a.strip() for l in by_dish[code]
                            for a in re.split(r"[,，、/]", ingredient_master[l["name"]]["allergens"] or "")
                            if a.strip()})
        dish_id = ok(args.api, "POST", "/admin/meal/dishes", {
            "name": dish["菜品名称"],
            "category": dish["分类"],
            "description": description,
            "storageMethod": dish.get("保存方式"),
            "reheatMethod": dish.get("复热方式"),
            "imageUrl": dish.get("图片URL"),
            "extraAllergens": ",".join(allergens),
            "status": STATUS.get(str(dish.get("状态") or "上架"), 0),
        }, token)
        bom = [{"ingredientId": id_by_name[l["name"]], "netWeightG": l["grams"]}
               for l in by_dish[code]]
        ok(args.api, "POST", f"/admin/meal/dishes/{dish_id}/versions", {
            "netWeightG": c["weight"],
            "kcal": c["kcal"],
            "proteinG": c["protein"],
            "carbG": c["carb"],
            "fatG": c["fat"],
            "remark": f"由模板 {code} 导入",
            "bom": bom,
        }, token)
        print(f"已导入菜品 {code} {dish['菜品名称']}（{len(bom)} 个 BOM）")

    print("导入完成")


if __name__ == "__main__":
    main()
