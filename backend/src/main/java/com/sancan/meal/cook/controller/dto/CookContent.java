package com.sancan.meal.cook.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 一锅菜的记录内容，整体以 JSON 存储。
 *
 * <p>称重口径：
 * <ul>
 *   <li>食材 {@code rawG}：下锅前的可食生重（已去皮去骨）；</li>
 *   <li>出品组件 {@code cookedG}：做好后这个组件的总熟重，例如整锅肉捞出来称、汤汁单独称；</li>
 *   <li>出品组件 {@code standardG}：标准一份里放多少克这个组件，留空则按 熟重 ÷ 份数。</li>
 * </ul>
 * 每样食材归到一个出品组件，组件的营养 = 归到它的食材营养之和，再按熟重折算成每克营养。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CookContent(
        /** 这一锅分几份，用来推算标准一份；各组件填了 standardG 时以 standardG 为准。 */
        Integer portions,
        List<Ingredient> ingredients,
        List<Part> components,
        List<Step> steps,
        /** 克重表要算的成品总重，留空用默认 300/400/500/600。 */
        List<Integer> weightTargets) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ingredient(
            String key,
            Long ingredientId,
            String name,
            BigDecimal rawG,
            String componentKey,
            String note) {
    }

    /**
     * 出品组件。role：PROTEIN 主料（肉）/ SAUCE 汤汁（跟主料走）/ STAPLE 主食 /
     * VEGETABLE 蔬菜 / FAT 油脂或额外酱料 / OTHER 其他（固定量）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            String key,
            String name,
            String role,
            BigDecimal cookedG,
            BigDecimal standardG) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String key,
            String text,
            /** 火候，自由填写，如 大火 / 中小火 / 烤箱 200℃。 */
            String heat,
            /** 实际用时（秒），计时器记录或手填。 */
            Integer durationSec,
            List<Long> photoIds) {
    }

    /** 新建记录时的默认组件：主菜最常见的四块，用不上的可以删。 */
    public static CookContent starter() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("meat", "肉", "PROTEIN", null, null));
        parts.add(new Part("sauce", "汤汁", "SAUCE", null, null));
        parts.add(new Part("staple", "米饭", "STAPLE", null, null));
        parts.add(new Part("veg", "蔬菜", "VEGETABLE", null, null));
        return new CookContent(1, new ArrayList<>(), parts, new ArrayList<>(), null);
    }

    /** 把缺省的列表补成空列表，后续代码不用到处判空。 */
    public CookContent normalized() {
        return new CookContent(
                portions,
                ingredients == null ? List.of() : ingredients,
                components == null ? List.of() : components,
                steps == null ? List.of() : steps,
                weightTargets);
    }
}
