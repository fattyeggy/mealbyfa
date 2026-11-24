-- 做菜记录（工作说明 28）生产库增量迁移（仅 MySQL 8）
-- 开发与测试环境的 H2 由 schema.sql 直接建新表，不需要执行本文件。
-- 必须先执行本文件，再部署新版本后端：新代码读取 meal_recipe_bom.component_type。
-- 执行前请备份。

-- 1. 配方行可单独指定组件类型；同一食材可在不同组件各出现一次
ALTER TABLE meal_recipe_bom
    ADD COLUMN component_type VARCHAR(16) DEFAULT NULL AFTER note,
    ADD COLUMN cooked_weight_g DECIMAL(10,2) DEFAULT NULL AFTER component_type;
ALTER TABLE meal_recipe_bom
    DROP INDEX uk_meal_recipe_bom,
    ADD CONSTRAINT uk_meal_recipe_bom UNIQUE (dish_version_id, ingredient_id, component_type);

-- 2. 做菜记录
CREATE TABLE IF NOT EXISTS meal_cook_record (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_id            BIGINT        DEFAULT NULL,
    title              VARCHAR(64)   NOT NULL,
    cook_date          DATE          NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    content            MEDIUMTEXT    DEFAULT NULL,
    synced_version_id  BIGINT        DEFAULT NULL,
    synced_time        DATETIME      DEFAULT NULL,
    remark             VARCHAR(255)  DEFAULT NULL,
    creator            VARCHAR(64)   DEFAULT NULL,
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(64)   DEFAULT NULL,
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    KEY idx_meal_cook_record_date (cook_date)
) DEFAULT CHARSET = utf8mb4;

-- 3. 做菜步骤照片（存库，云托管容器磁盘不持久）
CREATE TABLE IF NOT EXISTS meal_cook_photo (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id     BIGINT        NOT NULL,
    content_type  VARCHAR(64)   NOT NULL,
    size_bytes    INT           NOT NULL DEFAULT 0,
    data          MEDIUMBLOB    NOT NULL,
    creator       VARCHAR(64)   DEFAULT NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(64)   DEFAULT NULL,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    KEY idx_meal_cook_photo_record (record_id)
) DEFAULT CHARSET = utf8mb4;
