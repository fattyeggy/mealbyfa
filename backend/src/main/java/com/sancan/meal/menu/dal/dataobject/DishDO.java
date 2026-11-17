package com.sancan.meal.menu.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 菜品主数据。过敏原由 BOM 食材推导，另可声明 {@code extraAllergens}。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_dish")
public class DishDO extends BaseDO {

    private String name;
    /** 分类标签，如 主食 / 热菜 / 冷食。 */
    private String category;
    private String description;
    private String imageUrl;
    /** 保存方式，如 0-4℃ 冷藏。 */
    private String storageMethod;
    /** 复热方式，如 微波 90 秒。 */
    private String reheatMethod;
    /** 菜品额外声明的过敏原（BOM 之外），逗号分隔。 */
    private String extraAllergens;
    /** 0 启用，1 停用。 */
    private Integer status;
}
