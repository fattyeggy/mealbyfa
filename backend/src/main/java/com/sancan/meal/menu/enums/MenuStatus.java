package com.sancan.meal.menu.enums;

/** 周菜单状态。发布后不可原地修改，只能新建版本（手册 §6「状态与版本」）。 */
public enum MenuStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isDraft() {
        return this == DRAFT;
    }

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
