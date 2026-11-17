package com.sancan.meal.nutrition;

import com.sancan.meal.nutrition.service.NutritionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 按开发说明第 10 节的 6 组基准用例验收计算引擎。 */
class NutritionEngineTest {

    private final NutritionEngine.Config cfg = NutritionEngine.defaultConfig();

    private NutritionEngine.Result run(String goal, String sex, int age, double h, double w,
                                       Double fatPct, String activity, int times, int minutes,
                                       String intensity, Set<String> flags) {
        return NutritionEngine.calculate(new NutritionEngine.Input(
                goal, sex, age, h, w, fatPct, activity, times, minutes, intensity, flags), cfg);
    }

    @Test
    @DisplayName("用例1：男 30/175/75 久坐 240分钟中强度 减脂")
    void case1() {
        var r = run("cut", "male", 30, 175, 75, null, "sedentary", 4, 60, "medium", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1699, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.activityFactor()).isEqualTo(1.55);
        assertThat(r.tdee()).isCloseTo(2633, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(2110);
        assertThat(r.proteinRaw()).isEqualTo(150);
        assertThat(r.proteinTier()).isEqualTo(160);
        assertThat(r.fatG()).isEqualTo(60);
        assertThat(r.carbG()).isEqualTo(232);
    }

    @Test
    @DisplayName("用例2：女 28/162/55 久坐 120分钟中强度 维持（向下归档）")
    void case2() {
        var r = run("maintain", "female", 28, 162, 55, null, "sedentary", 2, 60, "medium", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1262, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.activityFactor()).isEqualTo(1.375);
        assertThat(r.tdee()).isCloseTo(1735, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(1730);
        assertThat(r.proteinRaw()).isEqualTo(88);
        assertThat(r.proteinTier()).isEqualTo(80);
        assertThat(r.fatG()).isEqualTo(50);
        assertThat(r.carbG()).isEqualTo(240);
    }

    @Test
    @DisplayName("用例3：男 24/180/68 经常走动 300分钟高强度 增肌（强度系数1.2）")
    void case3() {
        var r = run("bulk", "male", 24, 180, 68, null, "light", 5, 60, "high", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1690, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.activityFactor()).isEqualTo(1.72);
        assertThat(r.tdee()).isCloseTo(2907, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(3200);
        assertThat(r.proteinRaw()).isEqualTo(150);
        assertThat(r.proteinTier()).isEqualTo(160);
        assertThat(r.fatG()).isEqualTo(61);
        assertThat(r.carbG()).isEqualTo(503);
    }

    @Test
    @DisplayName("用例4：女 35/160/78 不运动 减脂（BMI=30.5，参考体重 61.4kg）")
    void case4() {
        var r = run("cut", "female", 35, 160, 78, null, "sedentary", 0, 0, "medium", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1444, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.activityFactor()).isEqualTo(1.2);
        assertThat(r.tdee()).isCloseTo(1733, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(1390);
        assertThat(r.referenceWeightKg()).isEqualTo(61.4);
        assertThat(r.proteinRaw()).isEqualTo(123);
        assertThat(r.proteinTier()).isEqualTo(130);
        assertThat(r.fatG()).isEqualTo(49);
        assertThat(r.carbG()).isEqualTo(107);
    }

    @Test
    @DisplayName("用例5：女 45/155/50 不运动 减脂（触发热量下限）")
    void case5() {
        var r = run("cut", "female", 45, 155, 50, null, "sedentary", 0, 0, "medium", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1083, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.tdee()).isCloseTo(1299, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(1200);
        assertThat(r.flags()).contains("kcal_floor_applied");
        assertThat(r.proteinRaw()).isEqualTo(100);
        assertThat(r.proteinTier()).isEqualTo(100);
        assertThat(r.fatG()).isEqualTo(40);
        assertThat(r.carbG()).isEqualTo(110);
    }

    @Test
    @DisplayName("用例6：男 40/170/90 体力劳动 400分钟高强度 减脂（AF封顶1.9，参考体重69.4kg）")
    void case6() {
        var r = run("cut", "male", 40, 170, 90, null, "active", 5, 80, "high", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(1768, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.activityFactor()).isEqualTo(1.9);
        assertThat(r.tdee()).isCloseTo(3358, org.assertj.core.data.Offset.offset(1.0));
        assertThat(r.kcal()).isEqualTo(2690);
        assertThat(r.referenceWeightKg()).isEqualTo(69.4);
        assertThat(r.proteinRaw()).isEqualTo(153);
        assertThat(r.proteinTier()).isEqualTo(160);
        assertThat(r.fatG()).isEqualTo(55);
        assertThat(r.carbG()).isEqualTo(389);
    }

    @Test
    @DisplayName("拦截：未成年、超龄、孕期、肾病、糖尿病、进食障碍、过瘦减脂")
    void blocks() {
        assertThat(run("cut", "male", 17, 175, 70, null, "sedentary", 0, 0, "medium", Set.of("none")).blockReason())
                .isEqualTo("under_18");
        assertThat(run("cut", "male", 66, 175, 70, null, "sedentary", 0, 0, "medium", Set.of("none")).blockReason())
                .isEqualTo("over_65");
        assertThat(run("cut", "female", 30, 165, 60, null, "sedentary", 0, 0, "medium", Set.of("pregnant")).blockReason())
                .isEqualTo("pregnant");
        assertThat(run("cut", "male", 30, 175, 70, null, "sedentary", 0, 0, "medium", Set.of("kidney_disease")).blockReason())
                .isEqualTo("kidney_disease");
        assertThat(run("cut", "male", 30, 175, 70, null, "sedentary", 0, 0, "medium", Set.of("diabetes")).blockReason())
                .isEqualTo("diabetes");
        assertThat(run("cut", "female", 30, 165, 60, null, "sedentary", 0, 0, "medium", Set.of("eating_disorder_history")).blockReason())
                .isEqualTo("eating_disorder_history");
        assertThat(run("cut", "female", 30, 165, 48, null, "sedentary", 0, 0, "medium", Set.of("none")).blockReason())
                .isEqualTo("cut_bmi_too_low");
    }

    @Test
    @DisplayName("痛风：蛋白系数压到 1.6 g/kg")
    void goutCapsProtein() {
        var normal = run("cut", "male", 30, 175, 75, null, "sedentary", 0, 0, "medium", Set.of("none"));
        var gout = run("cut", "male", 30, 175, 75, null, "sedentary", 0, 0, "medium", Set.of("gout"));
        assertThat(gout.proteinRaw()).isEqualTo(120);
        assertThat(gout.proteinRaw()).isLessThan(normal.proteinRaw());
    }

    @Test
    @DisplayName("体脂率：改用 Katch-McArdle 公式")
    void bodyFatUsesKatch() {
        var r = run("cut", "male", 30, 175, 75, 20.0, "sedentary", 0, 0, "medium", Set.of("none"));
        assertThat(r.bmr()).isCloseTo(370 + 21.6 * 75 * 0.8, org.assertj.core.data.Offset.offset(1.0));
    }
}
