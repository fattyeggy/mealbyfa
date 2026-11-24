package com.sancan.meal.cook.service;

import com.sancan.meal.cook.controller.dto.CookContent;
import com.sancan.meal.cook.service.CookCalculation.BomLine;
import com.sancan.meal.cook.service.CookCalculation.Nutrients;
import com.sancan.meal.cook.service.CookCalculation.PartResult;
import com.sancan.meal.cook.service.CookCalculation.Plate;
import com.sancan.meal.cook.service.CookCalculation.PlatePart;
import com.sancan.meal.cook.service.CookCalculation.TierTable;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.plan.service.ComponentPortioner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 做菜记录计算，纯函数，便于单测。
 *
 * <p>口径与系统配餐（{@link ComponentPortioner}）一致：
 * <ul>
 *   <li>主料 PROTEIN 与汤汁 SAUCE 一起按蛋白目标缩放；</li>
 *   <li>按档位出品时，主食、蔬菜、油脂保持标准一份（实际配餐里主食还会按每位用户的碳水目标调整）；</li>
 *   <li>缩放倍数限制在 0.30–2.50，超出时标记。</li>
 * </ul>
 */
public final class CookCalculator {

    public static final Set<String> ROLES = Set.of("PROTEIN", "SAUCE", "STAPLE", "VEGETABLE", "FAT", "OTHER");
    public static final List<Integer> DEFAULT_WEIGHT_TARGETS = List.of(300, 400, 500, 600);

    private static final double MIN_FACTOR = ComponentPortioner.MIN_FACTOR.doubleValue();
    private static final double MAX_FACTOR = ComponentPortioner.MAX_FACTOR.doubleValue();

    /** 一个餐次：类型、显示名、占全天蛋白的比例。 */
    public record MealShare(String mealType, String label, double share) {
    }

    private CookCalculator() {
    }

    public static CookCalculation calculate(CookContent raw, Map<Long, IngredientDO> library,
                                            List<Integer> tiers, List<MealShare> meals) {
        CookContent content = raw == null ? CookContent.starter().normalized() : raw.normalized();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---------- 1. 组件 ----------
        Map<String, PartAcc> parts = new LinkedHashMap<>();
        for (CookContent.Part p : content.components()) {
            if (p.key() == null || p.key().isBlank()) {
                continue;
            }
            String role = p.role() == null ? "OTHER" : p.role().trim().toUpperCase();
            if (!ROLES.contains(role)) {
                warnings.add("组件「" + p.name() + "」的类型无法识别，按「其他（固定量）」处理");
                role = "OTHER";
            }
            parts.put(p.key(), new PartAcc(p, role));
        }

        // ---------- 2. 食材归到组件，累加生重与营养 ----------
        for (CookContent.Ingredient ing : content.ingredients()) {
            String name = ing.name() == null ? "未命名食材" : ing.name();
            if (ing.ingredientId() == null) {
                blockers.add("「" + name + "」还没有从食材库选择");
                continue;
            }
            IngredientDO lib = library.get(ing.ingredientId());
            if (lib == null) {
                blockers.add("「" + name + "」在食材库里已不存在");
                continue;
            }
            name = lib.getName();
            if (ing.rawG() == null || ing.rawG().signum() <= 0) {
                warnings.add("「" + name + "」还没称重，暂不计入");
                continue;
            }
            if (lib.getKcalPer100g() == null || lib.getProteinPer100g() == null
                    || lib.getCarbPer100g() == null || lib.getFatPer100g() == null) {
                blockers.add("「" + name + "」在食材库里缺少每 100g 营养，请先补齐（盐、水可以填 0）");
                continue;
            }
            PartAcc part = ing.componentKey() == null ? null : parts.get(ing.componentKey());
            if (part == null) {
                blockers.add("「" + name + "」还没归到出品组件（肉 / 汤汁 / 主食…）");
                continue;
            }
            double g = ing.rawG().doubleValue();
            part.rawG += g;
            part.kcal += g / 100.0 * lib.getKcalPer100g().doubleValue();
            part.protein += g / 100.0 * lib.getProteinPer100g().doubleValue();
            part.carb += g / 100.0 * lib.getCarbPer100g().doubleValue();
            part.fat += g / 100.0 * lib.getFatPer100g().doubleValue();
            part.lines.add(new LineAcc(lib.getId(), name, g, ing.note()));
        }

        // ---------- 3. 熟重、出成率、标准一份 ----------
        Integer portions = content.portions();
        List<PartAcc> active = new ArrayList<>();
        for (PartAcc part : parts.values()) {
            String pname = part.def.name() == null ? part.def.key() : part.def.name();
            if (part.lines.isEmpty()) {
                if (part.def.cookedG() != null && part.def.cookedG().signum() > 0) {
                    warnings.add("「" + pname + "」称了熟重但没有食材，不计入");
                }
                continue;
            }
            if (part.def.cookedG() == null || part.def.cookedG().signum() <= 0) {
                blockers.add("「" + pname + "」还没称做好后的熟重");
                continue;
            }
            part.cookedG = part.def.cookedG().doubleValue();
            if (part.def.standardG() != null && part.def.standardG().signum() > 0) {
                part.standardG = part.def.standardG().doubleValue();
            } else if (portions != null && portions > 0) {
                part.standardG = part.cookedG / portions;
            } else {
                blockers.add("「" + pname + "」请填写标准一份放多少克，或填写这一锅分几份");
                continue;
            }
            if (part.standardG > part.cookedG + 1e-6) {
                warnings.add("「" + pname + "」标准一份 " + Math.round(part.standardG) + "g 超过了整锅熟重 "
                        + Math.round(part.cookedG) + "g，请确认");
            }
            active.add(part);
        }

        List<PartResult> partResults = new ArrayList<>();
        for (PartAcc part : parts.values()) {
            if (part.lines.isEmpty()) {
                continue;
            }
            boolean ok = active.contains(part);
            partResults.add(new PartResult(
                    part.def.key(), part.def.name(), part.role,
                    dec(part.rawG, 0),
                    ok ? dec(part.cookedG, 0) : null,
                    ok && part.rawG > 0 ? dec(part.cookedG / part.rawG, 2) : null,
                    ok ? nutrients(part.kcal / part.cookedG * 100, part.protein / part.cookedG * 100,
                            part.carb / part.cookedG * 100, part.fat / part.cookedG * 100) : null,
                    nutrients(part.kcal, part.protein, part.carb, part.fat),
                    ok ? dec(part.standardG, 0) : null));
        }

        if (active.isEmpty()) {
            if (content.ingredients().isEmpty()) {
                blockers.add("还没有添加食材");
            }
            return new CookCalculation(dedupe(blockers), dedupe(warnings), partResults, null,
                    List.of(), List.of(), List.of());
        }

        boolean hasProtein = active.stream().anyMatch(p -> "PROTEIN".equals(p.role));
        boolean hasStaple = active.stream().anyMatch(p -> "STAPLE".equals(p.role));
        if (!hasProtein) {
            warnings.add("没有「主料（肉）」组件：不出档位表，系统配餐会按蛋白把整份等比放大缩小");
        } else if (!hasStaple) {
            warnings.add("没有「主食」组件：系统配餐会按蛋白把整份等比放大缩小，碳水不能单独调");
        }

        // ---------- 4. 标准一份 ----------
        Map<String, Double> standardGrams = new LinkedHashMap<>();
        for (PartAcc p : active) {
            standardGrams.put(p.def.key(), p.standardG);
        }
        Plate standard = plate("标准一份", null, null, 1.0, false, active, standardGrams);

        // ---------- 5. 蛋白档位表 ----------
        List<TierTable> tierTables = new ArrayList<>();
        double groupProtein = 0;
        double otherProtein = 0;
        for (PartAcc p : active) {
            double perGram = p.protein / p.cookedG;
            if (followsProtein(p.role)) {
                groupProtein += perGram * p.standardG;
            } else {
                otherProtein += perGram * p.standardG;
            }
        }
        if (hasProtein && groupProtein > 0) {
            for (MealShare meal : meals) {
                List<Plate> rows = new ArrayList<>();
                for (Integer tier : tiers) {
                    double target = tier * meal.share();
                    double needed = (target - otherProtein) / groupProtein;
                    double f = clamp(needed);
                    Map<String, Double> grams = new LinkedHashMap<>();
                    for (PartAcc p : active) {
                        grams.put(p.def.key(), followsProtein(p.role) ? p.standardG * f : p.standardG);
                    }
                    rows.add(plate(tier + "g 档", dec(target, 1), null, f, Math.abs(needed - f) > 1e-9,
                            active, grams));
                }
                tierTables.add(new TierTable(meal.mealType(), meal.label(), dec(meal.share(), 2), rows));
            }
        }

        // ---------- 6. 成品克重表 ----------
        List<Plate> weightRows = new ArrayList<>();
        double standardWeight = standardGrams.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Integer> targets = content.weightTargets() == null || content.weightTargets().isEmpty()
                ? DEFAULT_WEIGHT_TARGETS : content.weightTargets();
        for (Integer w : targets) {
            if (w == null || w <= 0 || standardWeight <= 0) {
                continue;
            }
            double f = w / standardWeight;
            Map<String, Double> grams = new LinkedHashMap<>();
            for (PartAcc p : active) {
                grams.put(p.def.key(), p.standardG * f);
            }
            weightRows.add(plate(w + "g", null, w, f, false, active, grams));
        }

        // ---------- 7. 同步用配方：标准一份所需可食生重 ----------
        Map<String, BomAcc> bom = new LinkedHashMap<>();
        for (PartAcc p : active) {
            double ratio = p.standardG / p.cookedG;
            for (LineAcc line : p.lines) {
                String key = line.ingredientId + "#" + p.role;
                BomAcc acc = bom.computeIfAbsent(key, k -> new BomAcc(line.ingredientId, line.name, p.role));
                acc.rawG += line.rawG * ratio;
                // 组件出成率按生重均摊到每样食材
                acc.cookedG += line.rawG * ratio * (p.cookedG / p.rawG);
                String note = p.def.name() + (line.note == null || line.note.isBlank() ? "" : "：" + line.note);
                if (!acc.notes.contains(note)) {
                    acc.notes.add(note);
                }
            }
        }
        List<BomLine> bomLines = new ArrayList<>();
        for (BomAcc acc : bom.values()) {
            BigDecimal g = dec(acc.rawG, 1);
            if (g.signum() <= 0) {
                continue;
            }
            String note = String.join("；", acc.notes);
            bomLines.add(new BomLine(acc.ingredientId, acc.name, acc.role, g, dec(acc.cookedG, 1),
                    note.length() > 255 ? note.substring(0, 255) : note));
        }

        return new CookCalculation(dedupe(blockers), dedupe(warnings), partResults, standard,
                tierTables, weightRows, bomLines);
    }

    /** 汤汁与主料一起跟着蛋白目标走。 */
    public static boolean followsProtein(String role) {
        return "PROTEIN".equals(role) || "SAUCE".equals(role);
    }

    private static Plate plate(String label, BigDecimal targetProtein, Integer targetWeight, double factor,
                               boolean clamped, List<PartAcc> active, Map<String, Double> grams) {
        List<PlatePart> plateParts = new ArrayList<>();
        int total = 0;
        double kcal = 0, protein = 0, carb = 0, fat = 0;
        for (PartAcc p : active) {
            int g = (int) Math.round(grams.getOrDefault(p.def.key(), 0.0));
            plateParts.add(new PlatePart(p.def.key(), p.def.name(), p.role, g));
            total += g;
            // 营养按实际放的整数克算，保证厨房照着放和页面显示一致
            kcal += p.kcal / p.cookedG * g;
            protein += p.protein / p.cookedG * g;
            carb += p.carb / p.cookedG * g;
            fat += p.fat / p.cookedG * g;
        }
        return new Plate(label, targetProtein, targetWeight, dec(factor, 2), clamped, plateParts, total,
                nutrients(kcal, protein, carb, fat));
    }

    private static double clamp(double v) {
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, v));
    }

    private static Nutrients nutrients(double kcal, double protein, double carb, double fat) {
        return new Nutrients((int) Math.round(kcal), dec(protein, 1), dec(carb, 1), dec(fat, 1));
    }

    private static BigDecimal dec(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }

    private static List<String> dedupe(List<String> list) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(list));
    }

    private static final class PartAcc {
        final CookContent.Part def;
        final String role;
        final List<LineAcc> lines = new ArrayList<>();
        double rawG, kcal, protein, carb, fat, cookedG, standardG;

        PartAcc(CookContent.Part def, String role) {
            this.def = def;
            this.role = role;
        }
    }

    private record LineAcc(Long ingredientId, String name, double rawG, String note) {
    }

    private static final class BomAcc {
        final Long ingredientId;
        final String name;
        final String role;
        final List<String> notes = new ArrayList<>();
        double rawG;
        double cookedG;

        BomAcc(Long ingredientId, String name, String role) {
            this.ingredientId = ingredientId;
            this.name = name;
            this.role = role;
        }
    }
}
