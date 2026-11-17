package com.sancan.meal.menu.controller.dto;

import java.util.List;

/** 发布前检查结果（对应手册 §6「发布前检查」与示意图中的检查表）。 */
public record MenuCheckResp(
        boolean passed,
        List<Item> items,
        /** 全周单份标准食材成本合计。 */
        java.math.BigDecimal standardCost,
        java.math.BigDecimal costRate) {

    public enum Status {
        /** 通过。 */
        PASS,
        /** 未通过，阻止发布。 */
        FAIL,
        /** 无法自动判定，需人工确认；不阻止发布，但会记录下来。 */
        MANUAL
    }

    public record Item(String name, Status status, String detail) {
    }

    public static List<String> ruleNames() {
        return List.of(
                "21 个餐次配置完整",
                "菜品版本与菜谱 BOM 完整",
                "营养与全天重量在允许范围",
                "过敏原、保存、复热与配送批次完整",
                "预计食材成本率未超预警线",
                "厨房设备与产能可承接");
    }
}
