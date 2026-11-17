-- P4.5 / P4.6 生产库增量迁移（仅 MySQL 8）
-- 开发与测试环境的 H2 由 schema.sql 直接建新表，不需要执行本文件。
-- 执行前请备份；本文件假定数据库已按 P4 之前的版本建好。

-- ---------- P4.5 定价版本 ----------
CREATE TABLE IF NOT EXISTS meal_pricing_version (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_no                    VARCHAR(32)   NOT NULL,
    status                        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    effective_at                  DATETIME      NOT NULL,
    daily_base_price              DECIMAL(10,2) NOT NULL,
    included_protein_g            INT           NOT NULL DEFAULT 60,
    extra_protein_price_per_10g   DECIMAL(10,2) NOT NULL,
    min_daily_price               DECIMAL(10,2) NOT NULL DEFAULT 65,
    min_target_protein_g          INT           NOT NULL DEFAULT 80,
    max_target_protein_g          INT           NOT NULL DEFAULT 220,
    manual_adjust_step_g          INT           NOT NULL DEFAULT 10,
    swap_cost_divisor             DECIMAL(6,4)  NOT NULL DEFAULT 0.7700,
    breakfast_ratio               DECIMAL(5,4)  NOT NULL DEFAULT 0.2500,
    lunch_ratio                   DECIMAL(5,4)  NOT NULL DEFAULT 0.4000,
    rounding_mode                 VARCHAR(32)   NOT NULL DEFAULT 'HALF_UP_INTEGER',
    created_by                    VARCHAR(64)   DEFAULT NULL,
    reviewed_by                   VARCHAR(64)   DEFAULT NULL,
    reviewed_time                 DATETIME      DEFAULT NULL,
    published_by                  VARCHAR(64)   DEFAULT NULL,
    publish_time                  DATETIME      DEFAULT NULL,
    remark                        VARCHAR(255)  DEFAULT NULL,
    creator                       VARCHAR(64)   DEFAULT NULL,
    create_time                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                       VARCHAR(64)   DEFAULT NULL,
    update_time                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                       TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_pricing_version UNIQUE (version_no)
);

CREATE TABLE IF NOT EXISTS meal_pricing_version_swap (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    pricing_version_id   BIGINT        NOT NULL,
    meal_type            VARCHAR(16)   NOT NULL,
    from_dish_version_id BIGINT        NOT NULL,
    to_dish_version_id   BIGINT        NOT NULL,
    cost_delta           DECIMAL(12,4) NOT NULL DEFAULT 0,
    from_version_cost    DECIMAL(12,4) DEFAULT NULL,
    to_version_cost      DECIMAL(12,4) DEFAULT NULL,
    cost_frozen_time     DATETIME      DEFAULT NULL,
    status               VARCHAR(16)   NOT NULL DEFAULT 'APPROVED',
    remark               VARCHAR(255)  DEFAULT NULL,
    creator              VARCHAR(64)   DEFAULT NULL,
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(64)   DEFAULT NULL,
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_pricing_version_swap UNIQUE (pricing_version_id, meal_type, from_dish_version_id, to_dish_version_id)
);

ALTER TABLE meal_nutrition_plan
    ADD COLUMN pricing_version_id  BIGINT      DEFAULT NULL,
    ADD COLUMN pricing_version_no  VARCHAR(32) DEFAULT NULL,
    ADD COLUMN needs_review        TINYINT     NOT NULL DEFAULT 0,
    ADD COLUMN review_note         TEXT        DEFAULT NULL,
    ADD COLUMN raw_target_protein_g DECIMAL(8,2) DEFAULT NULL;

ALTER TABLE meal_plan_item
    ADD COLUMN base_cost DECIMAL(12,4) NOT NULL DEFAULT 0;

ALTER TABLE meal_order
    ADD COLUMN pricing_version_id BIGINT      DEFAULT NULL,
    ADD COLUMN pricing_version_no VARCHAR(32) DEFAULT NULL,
    ADD COLUMN raw_price          DECIMAL(10,2) DEFAULT NULL,
    ADD COLUMN swap_difference    DECIMAL(10,2) NOT NULL DEFAULT 0;

-- ---------- P4.6 复核事项 ----------
CREATE TABLE IF NOT EXISTS meal_plan_review (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id         BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    service_date    DATE         DEFAULT NULL,
    meal_type       VARCHAR(16)  DEFAULT NULL,
    review_type     VARCHAR(24)  NOT NULL,
    review_message  VARCHAR(500) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    resolved_by     BIGINT       DEFAULT NULL,
    resolved_time   DATETIME     DEFAULT NULL,
    resolution_note VARCHAR(255) DEFAULT NULL,
    creator         VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)  DEFAULT NULL,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_meal_pricing_version_status ON meal_pricing_version (status, effective_at);
CREATE INDEX idx_meal_plan_review_plan ON meal_plan_review (plan_id, status);
CREATE INDEX idx_meal_plan_review_scope ON meal_plan_review (plan_id, service_date, meal_type);

-- ---------- P8 履约与快照 ----------
ALTER TABLE meal_nutrition_plan
    ADD COLUMN source_menu_id BIGINT DEFAULT NULL,
    ADD COLUMN source_menu_version VARCHAR(32) DEFAULT NULL;

ALTER TABLE meal_delivery
    ADD COLUMN service_date DATE DEFAULT NULL,
    ADD COLUMN actual_delivery_date DATE DEFAULT NULL,
    ADD COLUMN delivery_slot VARCHAR(16) DEFAULT NULL;

ALTER TABLE meal_delivery DROP INDEX uk_meal_delivery;
ALTER TABLE meal_delivery
    ADD CONSTRAINT uk_meal_delivery UNIQUE (order_id, delivery_batch, actual_delivery_date);

CREATE TABLE IF NOT EXISTS meal_delivery_item (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id    BIGINT      NOT NULL,
    order_id       BIGINT      NOT NULL,
    order_item_id  BIGINT      NOT NULL,
    meal_type      VARCHAR(16) NOT NULL,
    service_date   DATE        NOT NULL,
    creator        VARCHAR(64) DEFAULT NULL,
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64) DEFAULT NULL,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_delivery_item UNIQUE (delivery_id, order_item_id)
);

-- ---------- P7 角色权限 ----------
ALTER TABLE sys_user
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'OPS' AFTER mobile;

-- ---------- 清理：P4.5 起售价不再用键值表，旧表无代码引用 ----------
DROP TABLE IF EXISTS meal_pricing_rule;

-- 建档默认配送信息
ALTER TABLE meal_user_profile
    ADD COLUMN contact_name VARCHAR(64) DEFAULT NULL,
    ADD COLUMN contact_phone VARCHAR(32) DEFAULT NULL,
    ADD COLUMN address VARCHAR(255) DEFAULT NULL;

-- 营养模型 v1：建档字段与计划目标
ALTER TABLE meal_user_profile
    ADD COLUMN body_fat_pct DECIMAL(4,1) DEFAULT NULL,
    ADD COLUMN daily_activity VARCHAR(16) DEFAULT NULL,
    ADD COLUMN workouts_per_week INT DEFAULT NULL,
    ADD COLUMN workout_minutes INT DEFAULT NULL,
    ADD COLUMN workout_type VARCHAR(16) DEFAULT NULL,
    ADD COLUMN workout_intensity VARCHAR(16) DEFAULT NULL,
    ADD COLUMN health_flags VARCHAR(255) DEFAULT NULL,
    ADD COLUMN diet_restrictions VARCHAR(255) DEFAULT NULL,
    ADD COLUMN ordered_meals VARCHAR(64) DEFAULT NULL,
    ADD COLUMN taste_prefs VARCHAR(255) DEFAULT NULL,
    ADD COLUMN target_protein_tier INT DEFAULT NULL,
    ADD COLUMN target_kcal INT DEFAULT NULL,
    ADD COLUMN target_fat_g INT DEFAULT NULL,
    ADD COLUMN target_carb_g INT DEFAULT NULL,
    ADD COLUMN nutrition_config_version VARCHAR(32) DEFAULT NULL;

ALTER TABLE meal_plan_item
    ADD COLUMN carb_g DECIMAL(6,2) DEFAULT NULL,
    ADD COLUMN fat_g DECIMAL(6,2) DEFAULT NULL;

ALTER TABLE meal_nutrition_plan
    ADD COLUMN target_fat_g INT DEFAULT NULL,
    ADD COLUMN target_carb_g INT DEFAULT NULL,
    ADD COLUMN protein_tier INT DEFAULT NULL,
    ADD COLUMN config_version VARCHAR(32) DEFAULT NULL,
    ADD COLUMN input_snapshot TEXT DEFAULT NULL;

-- 组件化配餐：食材组件类型与每 100g 营养
ALTER TABLE meal_ingredient
    ADD COLUMN component_type VARCHAR(16) DEFAULT 'OTHER',
    ADD COLUMN kcal_per_100g DECIMAL(8,2) DEFAULT NULL,
    ADD COLUMN protein_per_100g DECIMAL(8,2) DEFAULT NULL,
    ADD COLUMN carb_per_100g DECIMAL(8,2) DEFAULT NULL,
    ADD COLUMN fat_per_100g DECIMAL(8,2) DEFAULT NULL;

-- 周订单组
CREATE TABLE IF NOT EXISTS meal_order_group (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    week_start       DATE          NOT NULL,
    coupon_id        BIGINT        DEFAULT NULL,
    coupon_discount  DECIMAL(10,2) NOT NULL DEFAULT 0,
    original_total   DECIMAL(10,2) NOT NULL DEFAULT 0,
    payable_total    DECIMAL(10,2) NOT NULL DEFAULT 0,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    creator          VARCHAR(64)   DEFAULT NULL,
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(64)   DEFAULT NULL,
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0
);
ALTER TABLE meal_order ADD COLUMN group_id BIGINT DEFAULT NULL;
