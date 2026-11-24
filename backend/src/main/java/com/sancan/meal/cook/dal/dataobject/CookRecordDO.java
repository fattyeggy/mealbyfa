package com.sancan.meal.cook.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 做菜记录：一锅（一个批次）的完整流程。食材生重、出品组件熟重、步骤与照片 ID 以 JSON 存在
 * {@code content}，计算结果不落库，每次读取时按当时的食材库重新计算；同步到菜品时才冻结成菜品版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_cook_record")
public class CookRecordDO extends BaseDO {

    /** 关联菜品，可为空（先记录、后关联）。 */
    private Long dishId;
    private String title;
    private LocalDate cookDate;
    /** DRAFT 未同步 / SYNCED 已同步。同步后再修改会回到 DRAFT，syncedVersionId 保留上次结果。 */
    private String status;
    /** {@link com.sancan.meal.cook.controller.dto.CookContent} 的 JSON。 */
    private String content;
    private Long syncedVersionId;
    private LocalDateTime syncedTime;
    private String remark;
}
