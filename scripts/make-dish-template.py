#!/usr/bin/env python3
"""生成《菜品导入模板.xlsx》：食材、调料、做法、出品、摆盘、营养自动计算。"""
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.utils import get_column_letter

OUT = "docs/模板/菜品导入模板.xlsx"
GREEN = "032C0A"
LIGHT = "F2F7EC"

thin = Side(style="thin", color="D9E2D3")
border = Border(left=thin, right=thin, top=thin, bottom=thin)


def style_header(ws, row=1):
    for cell in ws[row]:
        cell.font = Font(bold=True, color="FFFFFF", size=10)
        cell.fill = PatternFill("solid", fgColor=GREEN)
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = border
    ws.row_dimensions[row].height = 28
    ws.freeze_panes = "A2"


def widths(ws, mapping):
    for col, w in mapping.items():
        ws.column_dimensions[col].width = w


wb = Workbook()

# ---------- 说明 ----------
ws = wb.active
ws.title = "填写说明"
lines = [
    ("发发的餐 · 菜品导入模板", 18, True),
    ("", 11, False),
    ("填写顺序：菜品主表 → 食材明细 → 调料明细 → 出品内容（自动汇总）→ 营养计算（自动计算）", 11, False),
    ("", 11, False),
    ("1. 菜品编号：全表唯一，例如 D001；食材/调料/出品都用编号关联。", 10, False),
    ("2. 组件类型：蛋白主料 / 主食 / 蔬菜 / 油脂 / 其他。配餐会按组件分别缩放，必须正确填写。", 10, False),
    ("3. 每份净重：可食状态的克重（去皮去骨后）。每 100g 营养按采购或标准食物成分表填写。", 10, False),
    ("4. 调料：酱汁、油、糖等单独填在调料明细，计入营养但不参与主料缩放。", 10, False),
    ("5. 出品内容：蛋白主料/主食/蔬菜/酱汁油脂的重量由公式自动汇总，摆盘示例手写。", 10, False),
    ("6. 营养计算：按 重量 ÷ 100 × 每100g 营养 自动求和，并校验 热量 ≈ 蛋白×4 + 碳水×4 + 脂肪×9。", 10, False),
    ("6.1 称重状态/出成率：生重原料填「生」和出成率（鸡胸约 0.75），营养按生重算；熟重原料填「熟」、出成率 1。", 10, False),
    ("6.2 出品重量和厨房分装单用「熟重合计」，不要把生重和熟重直接相加。", 10, False),
    ("6.3 调料也要填过敏原与价格；菜品过敏原 = 食材与调料过敏原的并集。", 10, False),
    ("7. 做法与摆盘会导入到菜品描述，供厨房与运营查看；图片可填 URL 或在后台上传。", 10, False),
    ("8. 填完后运行：python3 scripts/import-dish-template.py 模板路径 --api http://127.0.0.1:8080", 10, False),
    ("", 11, False),
    ("组件类型说明", 12, True),
    ("蛋白主料：鸡胸、牛肉、鱼虾、蛋清等，决定蛋白目标。", 10, False),
    ("主食：米饭、藜麦、面包、面等，决定碳水目标。", 10, False),
    ("蔬菜：西兰花、青菜等，基本固定，不随目标缩放。", 10, False),
    ("油脂：橄榄油、黄油、坚果等，决定脂肪目标。", 10, False),
    ("其他：牛奶、酱汁等，按固定量计入。", 10, False),
]
for i, (text, size, bold) in enumerate(lines, start=1):
    c = ws.cell(row=i, column=1, value=text)
    c.font = Font(size=size, bold=bold, color=GREEN if bold else "1C3B2E")
ws.column_dimensions["A"].width = 110

# ---------- 菜品主表 ----------
ws = wb.create_sheet("菜品主表")
headers = ["菜品编号", "菜品名称", "分类", "描述", "做法步骤", "摆盘说明",
           "保存方式", "复热方式", "图片URL", "状态"]
ws.append(headers)
style_header(ws)
widths(ws, {"A": 10, "B": 20, "C": 10, "D": 28, "E": 40, "F": 32,
            "G": 16, "H": 16, "I": 28, "J": 8})
examples = [
    ("D001", "照烧鸡胸饭", "午餐",
     "照烧鸡胸配杂粮饭与西兰花，甜咸口",
     "1. 鸡胸切块，用照烧汁腌 20 分钟；\n2. 平底锅少油煎至两面金黄；\n3. 倒入剩余照烧汁收汁；\n4. 米饭蒸熟，西兰花焯水 60 秒；\n5. 按出品内容装盒。",
     "米饭打底，鸡胸切 5 片扇形铺开，西兰花 3 朵围边，一勺照烧汁淋面，撒白芝麻。",
     "0-4℃ 冷藏", "微波 90 秒", "", "上架"),
    ("D002", "三文鱼鸡胸双拼藜麦碗", "晚餐",
     "三文鱼与鸡胸双蛋白，配藜麦与西兰花",
     "1. 三文鱼 50g、鸡胸 120g 分别煎熟；\n2. 藜麦提前浸泡 30 分钟，煮 15 分钟；\n3. 西兰花焯水；\n4. 味噌酱单独装小盒。",
     "藜麦铺底，三文鱼与鸡胸分两侧，西兰花填空隙，味噌酱一小盒另放。",
     "0-4℃ 冷藏", "微波 90 秒", "", "上架"),
    ("D003", "厚蛋烧全麦吐司", "早餐",
     "蛋清厚蛋烧配全麦吐司与牛奶",
     "1. 蛋清打散，平底锅小火卷成厚蛋烧；\n2. 全麦面包烤 2 分钟；\n3. 牛奶单独装瓶；\n4. 番茄酱小盒另放。",
     "吐司对角切开放盘，厚蛋烧切 4 段叠放，番茄酱画一道弧线，牛奶另瓶。",
     "0-4℃ 冷藏", "微波 60 秒", "", "上架"),
]
for row in examples:
    ws.append(row)
for row in ws.iter_rows(min_row=2, max_row=1 + len(examples)):
    for cell in row:
        cell.border = border
        cell.alignment = Alignment(wrap_text=True, vertical="top")
ws.row_dimensions[2].height = 80
ws.row_dimensions[3].height = 80
ws.row_dimensions[4].height = 70

# ---------- 食材明细 ----------
ws = wb.create_sheet("食材明细")
headers = ["菜品编号", "食材名称", "组件类型", "每份净重(g)",
           "每100g热量", "每100g蛋白", "每100g碳水", "每100g脂肪",
           "采购单位", "使用单位", "换算率", "可食率", "每kg价格(元)", "过敏原", "备注",
           "称重状态(生/熟)", "出成率", "熟重(g)"]
ws.append(headers)
style_header(ws)
widths(ws, {"A": 10, "B": 18, "C": 12, "D": 12, "E": 11, "F": 11, "G": 11, "H": 11,
            "I": 10, "J": 10, "K": 9, "L": 9, "M": 12, "N": 16, "O": 20,
            "P": 14, "Q": 8, "R": 10})

ingredients = [
    ("D001", "鸡胸肉", "蛋白主料", 150, 120, 22.5, 0, 2.6, "kg", "kg", 1, 0.95, 26, "", "去皮", "生", 0.75),
    ("D001", "米饭", "主食", 300, 130, 2.7, 28.5, 0.3, "kg", "kg", 1, 1, 4, "", "熟重", "熟", 1),
    ("D001", "西兰花", "蔬菜", 100, 39, 2.6, 6.3, 0.4, "kg", "kg", 1, 0.85, 12, "", "", "生", 0.90),
    ("D001", "橄榄油", "油脂", 8, 884, 0, 0, 100, "kg", "kg", 1, 1, 60, "", "", "熟", 1),
    ("D002", "三文鱼", "蛋白主料", 50, 206, 22.1, 0, 12.35, "kg", "kg", 1, 0.95, 68, "鱼", "", "生", 0.75),
    ("D002", "鸡胸肉", "蛋白主料", 120, 120, 22.5, 0, 2.6, "kg", "kg", 1, 0.95, 26, "", "", "生", 0.75),
    ("D002", "藜麦饭", "主食", 250, 120, 4.4, 21.3, 1.9, "kg", "kg", 1, 1, 18, "", "熟重", "熟", 1),
    ("D002", "西兰花", "蔬菜", 100, 39, 2.6, 6.3, 0.4, "kg", "kg", 1, 0.85, 12, "", "", "生", 0.90),
    ("D002", "橄榄油", "油脂", 5, 884, 0, 0, 100, "kg", "kg", 1, 1, 60, "", "", "熟", 1),
    ("D003", "蛋清", "蛋白主料", 120, 52, 10.9, 0.7, 0.2, "kg", "kg", 1, 1, 14, "蛋", "", "生", 0.90),
    ("D003", "全麦面包", "主食", 100, 247, 13, 41, 3.4, "kg", "kg", 1, 1, 20, "麸质", "", "熟", 1),
    ("D003", "牛奶", "其他", 100, 42, 3.4, 5, 1, "kg", "kg", 1, 1, 8, "奶", "", "熟", 1),
    ("D003", "橄榄油", "油脂", 8, 884, 0, 0, 100, "kg", "kg", 1, 1, 60, "", "", "熟", 1),
]
for row in ingredients:
    ws.append(row)
for i in range(2, 2 + len(ingredients)):
    ws.cell(row=i, column=18, value=f"=IF(P{i}=\"生\",D{i}*Q{i},D{i})")
for row in ws.iter_rows(min_row=2, max_row=1 + len(ingredients)):
    for cell in row:
        cell.border = border

dv = DataValidation(type="list", formula1='"蛋白主料,主食,蔬菜,油脂,其他"', allow_blank=False,
                    showDropDown=False)
ws.add_data_validation(dv)
dv.add(f"C2:C200")

# ---------- 调料明细 ----------
ws = wb.create_sheet("调料明细")
headers = ["菜品编号", "调料名称", "用量(g)", "每100g热量", "每100g蛋白",
           "每100g碳水", "每100g脂肪", "备注", "过敏原", "每kg价格(元)"]
ws.append(headers)
style_header(ws)
widths(ws, {"A": 10, "B": 18, "C": 10, "D": 12, "E": 12, "F": 12, "G": 12, "H": 24, "I": 16, "J": 12})
seasonings = [
    ("D001", "照烧汁", 20, 89, 1.8, 5.9, 0.1, "含糖，注意碳水", "大豆,小麦", 28),
    ("D002", "味噌酱", 15, 200, 12, 25, 6, "单独小盒", "大豆", 36),
    ("D003", "番茄酱", 10, 100, 1.5, 25, 0.1, "单独小盒", "", 18),
]
for row in seasonings:
    ws.append(row)
for row in ws.iter_rows(min_row=2, max_row=1 + len(seasonings)):
    for cell in row:
        cell.border = border

# ---------- 出品内容 ----------
ws = wb.create_sheet("出品内容")
headers = ["菜品编号", "菜品名称", "蛋白主料(g)", "主食(g)", "蔬菜(g)",
           "酱汁油脂(g)", "总重(g)", "摆盘示例", "餐盒建议", "熟重合计(g)"]
ws.append(headers)
style_header(ws)
widths(ws, {"A": 10, "B": 20, "C": 12, "D": 10, "E": 10, "F": 12, "G": 10, "H": 46, "I": 24, "J": 12})
for i, (code, name, plating, box) in enumerate([
    ("D001", "照烧鸡胸饭", "米饭打底，鸡胸扇形铺开，西兰花围边，一勺照烧汁淋面", "750ml 分格盒"),
    ("D002", "三文鱼鸡胸双拼藜麦碗", "藜麦铺底，双蛋白分两侧，味噌酱另盒", "750ml 分格盒 + 酱料盒"),
    ("D003", "厚蛋烧全麦吐司", "吐司对角切，厚蛋烧叠放，番茄酱画弧", "500ml 盒 + 牛奶瓶"),
], start=2):
    ws.cell(row=i, column=1, value=code)
    ws.cell(row=i, column=2, value=name)
    ws.cell(row=i, column=3, value=f"=SUMIFS('食材明细'!$D:$D,'食材明细'!$A:$A,$A{i},'食材明细'!$C:$C,\"蛋白主料\")")
    ws.cell(row=i, column=4, value=f"=SUMIFS('食材明细'!$D:$D,'食材明细'!$A:$A,$A{i},'食材明细'!$C:$C,\"主食\")")
    ws.cell(row=i, column=5, value=f"=SUMIFS('食材明细'!$D:$D,'食材明细'!$A:$A,$A{i},'食材明细'!$C:$C,\"蔬菜\")")
    ws.cell(row=i, column=6, value=(f"=SUMIFS('食材明细'!$D:$D,'食材明细'!$A:$A,$A{i},'食材明细'!$C:$C,\"油脂\")"
                                    f"+SUMIFS('调料明细'!$C:$C,'调料明细'!$A:$A,$A{i})"))
    ws.cell(row=i, column=7, value=f"=SUMIFS('食材明细'!$D:$D,'食材明细'!$A:$A,$A{i})+SUMIFS('调料明细'!$C:$C,'调料明细'!$A:$A,$A{i})")
    ws.cell(row=i, column=8, value=plating)
    ws.cell(row=i, column=9, value=box)
    ws.cell(row=i, column=10, value=f"=SUMIFS('食材明细'!$R:$R,'食材明细'!$A:$A,$A{i})+SUMIFS('调料明细'!$C:$C,'调料明细'!$A:$A,$A{i})")
    for col in range(1, 11):
        ws.cell(row=i, column=col).border = border

# ---------- 营养计算 ----------
ws = wb.create_sheet("营养计算")
headers = ["菜品编号", "菜品名称", "热量(kcal)", "蛋白(g)", "碳水(g)", "脂肪(g)",
           "总重(g)", "热量校验", "蛋白档参考", "备注"]
ws.append(headers)
style_header(ws)
widths(ws, {"A": 10, "B": 20, "C": 12, "D": 10, "E": 10, "F": 10, "G": 10, "H": 14, "I": 12, "J": 24})
for i in range(2, 5):
    ws.cell(row=i, column=1, value=f"='菜品主表'!A{i}")
    ws.cell(row=i, column=2, value=f"='菜品主表'!B{i}")
    ws.cell(row=i, column=3, value=(f"=ROUND(SUMPRODUCT(('食材明细'!$A$2:$A$200=$A{i})*'食材明细'!$D$2:$D$200*'食材明细'!$E$2:$E$200)/100"
                                    f"+SUMPRODUCT(('调料明细'!$A$2:$A$200=$A{i})*'调料明细'!$C$2:$C$200*'调料明细'!$D$2:$D$200)/100,0)"))
    ws.cell(row=i, column=4, value=(f"=ROUND(SUMPRODUCT(('食材明细'!$A$2:$A$200=$A{i})*'食材明细'!$D$2:$D$200*'食材明细'!$F$2:$F$200)/100"
                                    f"+SUMPRODUCT(('调料明细'!$A$2:$A$200=$A{i})*'调料明细'!$C$2:$C$200*'调料明细'!$E$2:$E$200)/100,1)"))
    ws.cell(row=i, column=5, value=(f"=ROUND(SUMPRODUCT(('食材明细'!$A$2:$A$200=$A{i})*'食材明细'!$D$2:$D$200*'食材明细'!$G$2:$G$200)/100"
                                    f"+SUMPRODUCT(('调料明细'!$A$2:$A$200=$A{i})*'调料明细'!$C$2:$C$200*'调料明细'!$F$2:$F$200)/100,1)"))
    ws.cell(row=i, column=6, value=(f"=ROUND(SUMPRODUCT(('食材明细'!$A$2:$A$200=$A{i})*'食材明细'!$D$2:$D$200*'食材明细'!$H$2:$H$200)/100"
                                    f"+SUMPRODUCT(('调料明细'!$A$2:$A$200=$A{i})*'调料明细'!$C$2:$C$200*'调料明细'!$G$2:$G$200)/100,1)"))
    ws.cell(row=i, column=7, value=f"='出品内容'!G{i}")
    ws.cell(row=i, column=8, value=f"=IF(ABS(C{i}-(D{i}*4+E{i}*4+F{i}*9))<=C{i}*0.05,\"OK\",\"检查\")")
    ws.cell(row=i, column=9, value=f"=ROUND(D{i}/10,1)&\" 个营养点\"")
    for col in range(1, 10):
        ws.cell(row=i, column=col).border = border

wb.save(OUT)
print("saved", OUT)
