-- 三餐私教 meal 模块 建表脚本
-- 约束：只使用 MySQL 8 与 H2(MySQL 模式) 的交集语法（不使用 ENGINE、内联 COMMENT、KEY 定义）
-- 次级索引见 db/mysql-indexes.sql（仅生产 MySQL 执行）

CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(100) NOT NULL,
    nickname     VARCHAR(64)  NOT NULL,
    email        VARCHAR(128) DEFAULT NULL,
    mobile       VARCHAR(32)  DEFAULT NULL,
    role         VARCHAR(32)  NOT NULL DEFAULT 'OPS',
    status       TINYINT      NOT NULL DEFAULT 0,
    creator      VARCHAR(64)  DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64)  DEFAULT NULL,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS member_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid       VARCHAR(64)  NOT NULL,
    unionid      VARCHAR(64)  DEFAULT NULL,
    nickname     VARCHAR(64)  DEFAULT NULL,
    avatar       VARCHAR(255) DEFAULT NULL,
    mobile       VARCHAR(32)  DEFAULT NULL,
    status       TINYINT      NOT NULL DEFAULT 0,
    creator      VARCHAR(64)  DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64)  DEFAULT NULL,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_member_user_openid UNIQUE (openid)
);

-- 敏感操作审计：credit 增减、退款、菜单发布、价格覆盖等（手册 §14）
CREATE TABLE IF NOT EXISTS meal_audit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_type VARCHAR(16)  NOT NULL,
    operator_id   BIGINT       NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    target_type   VARCHAR(64)  DEFAULT NULL,
    target_id     VARCHAR(64)  DEFAULT NULL,
    detail        TEXT         DEFAULT NULL,
    ip            VARCHAR(64)  DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========== P1 菜单域 ==========

-- 食材主数据：可食率、单位换算、移动加权价（手册 §10「必须新增的数据」）
CREATE TABLE IF NOT EXISTS meal_ingredient (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(64)   NOT NULL,
    category          VARCHAR(32)   DEFAULT NULL,
    purchase_unit     VARCHAR(16)   NOT NULL,
    usage_unit        VARCHAR(16)   NOT NULL,
    conversion_rate   DECIMAL(12,4) NOT NULL DEFAULT 1,
    edible_rate       DECIMAL(5,4)  NOT NULL DEFAULT 1,
    cooking_loss_rate DECIMAL(5,4)  NOT NULL DEFAULT 0,
    shelf_life_days   INT           DEFAULT NULL,
    allergens         VARCHAR(255)  DEFAULT NULL,
    moving_avg_price  DECIMAL(12,4) NOT NULL DEFAULT 0,
    -- 组件化配餐：蛋白主料 / 主食 / 蔬菜 / 油脂，以及每 100g 营养
    component_type    VARCHAR(16)   DEFAULT 'OTHER',
    kcal_per_100g     DECIMAL(8,2)  DEFAULT NULL,
    protein_per_100g  DECIMAL(8,2)  DEFAULT NULL,
    carb_per_100g     DECIMAL(8,2)  DEFAULT NULL,
    fat_per_100g      DECIMAL(8,2)  DEFAULT NULL,
    status            TINYINT       NOT NULL DEFAULT 0,
    creator           VARCHAR(64)   DEFAULT NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(64)   DEFAULT NULL,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_ingredient_name UNIQUE (name)
);

-- 菜品主数据
CREATE TABLE IF NOT EXISTS meal_dish (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(64)  NOT NULL,
    category         VARCHAR(32)  NOT NULL,
    description      VARCHAR(500) DEFAULT NULL,
    image_url        VARCHAR(255) DEFAULT NULL,
    storage_method   VARCHAR(255) DEFAULT NULL,
    reheat_method    VARCHAR(255) DEFAULT NULL,
    extra_allergens  VARCHAR(255) DEFAULT NULL,
    status           TINYINT      NOT NULL DEFAULT 0,
    creator          VARCHAR(64)  DEFAULT NULL,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(64)  DEFAULT NULL,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_dish_name UNIQUE (name)
);

-- 菜品版本：营养与成本随版本冻结，历史订单只引用版本
CREATE TABLE IF NOT EXISTS meal_dish_version (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_id       BIGINT        NOT NULL,
    version       INT           NOT NULL,
    net_weight_g  INT           NOT NULL DEFAULT 0,
    kcal          INT           NOT NULL DEFAULT 0,
    protein_g     DECIMAL(10,2) NOT NULL DEFAULT 0,
    carb_g        DECIMAL(10,2) NOT NULL DEFAULT 0,
    fat_g         DECIMAL(10,2) NOT NULL DEFAULT 0,
    cost          DECIMAL(12,4) NOT NULL DEFAULT 0,
    status        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    remark        VARCHAR(255)  DEFAULT NULL,
    creator       VARCHAR(64)   DEFAULT NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(64)   DEFAULT NULL,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_dish_version UNIQUE (dish_id, version)
);

-- 菜谱 BOM：每份所需原料的「可食生重」
CREATE TABLE IF NOT EXISTS meal_recipe_bom (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_version_id BIGINT        NOT NULL,
    ingredient_id   BIGINT        NOT NULL,
    net_weight_g    DECIMAL(10,2) NOT NULL DEFAULT 0,
    sort_no         INT           NOT NULL DEFAULT 0,
    note            VARCHAR(255)  DEFAULT NULL,
    -- 配方行组件类型，覆盖食材上的 component_type；做菜记录同步时写入（SAUCE 汤汁跟主料缩放）
    component_type  VARCHAR(16)   DEFAULT NULL,
    -- 本行在标准一份里的熟重；为空时按版本净重等比折算
    cooked_weight_g DECIMAL(10,2) DEFAULT NULL,
    creator         VARCHAR(64)   DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)   DEFAULT NULL,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_recipe_bom UNIQUE (dish_version_id, ingredient_id, component_type)
);

-- 周菜单：一个自然周的地区主题，包含 7 天 21 餐次
CREATE TABLE IF NOT EXISTS meal_week_menu (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    theme_name         VARCHAR(64)   NOT NULL,
    region             VARCHAR(64)   NOT NULL,
    start_date         DATE          NOT NULL,
    end_date           DATE          NOT NULL,
    version            VARCHAR(32)   NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    publish_time       DATETIME      DEFAULT NULL,
    standard_cost      DECIMAL(12,4) DEFAULT NULL,
    expected_cost_rate DECIMAL(6,4)  DEFAULT NULL,
    remark             VARCHAR(255)  DEFAULT NULL,
    creator            VARCHAR(64)   DEFAULT NULL,
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(64)   DEFAULT NULL,
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_week_menu_version UNIQUE (start_date, version)
);

-- 服务日：每天一行，汇总当日营养与重量
CREATE TABLE IF NOT EXISTS meal_menu_day (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id          BIGINT        NOT NULL,
    service_date     DATE          NOT NULL,
    day_of_week      INT           NOT NULL,
    total_weight_g   INT           NOT NULL DEFAULT 0,
    total_protein_g  DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_kcal       INT           NOT NULL DEFAULT 0,
    total_points     DECIMAL(10,2) NOT NULL DEFAULT 0,
    creator          VARCHAR(64)   DEFAULT NULL,
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(64)   DEFAULT NULL,
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_menu_day UNIQUE (menu_id, service_date)
);

-- 餐次：一天早/午/晚三条，指向菜品版本与配送批次
CREATE TABLE IF NOT EXISTS meal_menu_meal (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id         BIGINT      NOT NULL,
    menu_day_id     BIGINT      NOT NULL,
    meal_type       VARCHAR(16) NOT NULL,
    dish_version_id BIGINT      NOT NULL,
    delivery_batch  VARCHAR(16) NOT NULL,
    sort_no         INT         NOT NULL DEFAULT 0,
    creator         VARCHAR(64) DEFAULT NULL,
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64) DEFAULT NULL,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_menu_meal UNIQUE (menu_day_id, meal_type)
);

-- ========== P2 计划与计价 ==========

-- 用户档案：Onboarding 结果（手册 §5 第 4 步）
CREATE TABLE IF NOT EXISTS meal_user_profile (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    gender            VARCHAR(16)   DEFAULT NULL,
    birth_date        DATE          DEFAULT NULL,
    height_cm         DECIMAL(5,1)  DEFAULT NULL,
    weight_kg         DECIMAL(5,1)  DEFAULT NULL,
    target_weight_kg  DECIMAL(5,1)  DEFAULT NULL,
    activity_level    VARCHAR(16)   DEFAULT NULL,
    goal              VARCHAR(16)   DEFAULT NULL,
    training_state    VARCHAR(32)   DEFAULT NULL,
    appetite_level    VARCHAR(16)   DEFAULT NULL,
    dislikes          VARCHAR(255)  DEFAULT NULL,
    allergies         VARCHAR(255)  DEFAULT NULL,
    protein_pref      VARCHAR(255)  DEFAULT NULL,
    contact_name      VARCHAR(64)   DEFAULT NULL,
    contact_phone     VARCHAR(32)   DEFAULT NULL,
    address           VARCHAR(255)  DEFAULT NULL,
    -- 开发说明：身体与活动明细、健康筛查、饮食偏好、计算结果
    body_fat_pct      DECIMAL(4,1)  DEFAULT NULL,
    daily_activity    VARCHAR(16)   DEFAULT NULL,
    workouts_per_week INT           DEFAULT NULL,
    workout_minutes   INT           DEFAULT NULL,
    workout_type      VARCHAR(16)   DEFAULT NULL,
    workout_intensity VARCHAR(16)   DEFAULT NULL,
    health_flags      VARCHAR(255)  DEFAULT NULL,
    diet_restrictions VARCHAR(255)  DEFAULT NULL,
    ordered_meals     VARCHAR(64)   DEFAULT NULL,
    taste_prefs       VARCHAR(255)  DEFAULT NULL,
    target_protein_tier INT         DEFAULT NULL,
    target_kcal       INT           DEFAULT NULL,
    target_fat_g      INT           DEFAULT NULL,
    target_carb_g     INT           DEFAULT NULL,
    nutrition_config_version VARCHAR(32) DEFAULT NULL,
    onboarding_status TINYINT       NOT NULL DEFAULT 0,
    onboarding_time   DATETIME      DEFAULT NULL,
    creator           VARCHAR(64)   DEFAULT NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(64)   DEFAULT NULL,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_user_profile UNIQUE (user_id)
);

-- 个人营养计划：一周一版
CREATE TABLE IF NOT EXISTS meal_nutrition_plan (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    week_start       DATE          NOT NULL,
    target_protein_g DECIMAL(8,2)  NOT NULL DEFAULT 0,
    raw_target_protein_g DECIMAL(8,2) DEFAULT NULL,
    target_kcal      INT           NOT NULL DEFAULT 0,
    target_fat_g     INT           DEFAULT NULL,
    target_carb_g    INT           DEFAULT NULL,
    protein_tier     INT           DEFAULT NULL,
    config_version   VARCHAR(32)   DEFAULT NULL,
    input_snapshot   TEXT          DEFAULT NULL,
    daily_points     DECIMAL(8,2)  NOT NULL DEFAULT 0,
    pricing_version_id BIGINT      DEFAULT NULL,
    pricing_version_no VARCHAR(32) DEFAULT NULL,
    needs_review     TINYINT       NOT NULL DEFAULT 0,
    review_note      TEXT          DEFAULT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version          INT           NOT NULL DEFAULT 1,
    creator          VARCHAR(64)   DEFAULT NULL,
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(64)   DEFAULT NULL,
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_nutrition_plan UNIQUE (user_id, week_start, version)
    ,
    source_menu_id      BIGINT      DEFAULT NULL,
    source_menu_version VARCHAR(32) DEFAULT NULL
);

-- 计划餐次：个人克重、营养点、价格与成本随计划固定
CREATE TABLE IF NOT EXISTS meal_plan_item (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id               BIGINT        NOT NULL,
    user_id               BIGINT        NOT NULL,
    service_date          DATE          NOT NULL,
    meal_type             VARCHAR(16)   NOT NULL,
    dish_version_id       BIGINT        NOT NULL,
    base_dish_version_id  BIGINT        NOT NULL,
    target_protein_g      DECIMAL(8,2)  NOT NULL DEFAULT 0,
    weight_g              INT           NOT NULL DEFAULT 0,
    protein_g             DECIMAL(8,2)  NOT NULL DEFAULT 0,
    carb_g                DECIMAL(8,2)  DEFAULT NULL,
    fat_g                 DECIMAL(8,2)  DEFAULT NULL,
    kcal                  INT           NOT NULL DEFAULT 0,
    points                DECIMAL(8,2)  NOT NULL DEFAULT 0,
    price                 DECIMAL(10,2) NOT NULL DEFAULT 0,
    cost                  DECIMAL(12,4) NOT NULL DEFAULT 0,
    base_cost             DECIMAL(12,4) NOT NULL DEFAULT 0,
    allergen_conflict     TINYINT       NOT NULL DEFAULT 0,
    creator               VARCHAR(64)   DEFAULT NULL,
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(64)   DEFAULT NULL,
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_plan_item UNIQUE (plan_id, service_date, meal_type)
);

-- ========== P3 交易域 ==========

-- 账户：赠送 credit 与真实余额分账管理，退款按原来源退回（手册 §7）
CREATE TABLE IF NOT EXISTS meal_wallet_account (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    balance        DECIMAL(12,2) NOT NULL DEFAULT 0,
    credit_balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    cash_balance   DECIMAL(12,2) NOT NULL DEFAULT 0,
    version        INT           NOT NULL DEFAULT 0,
    creator        VARCHAR(64)   DEFAULT NULL,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64)   DEFAULT NULL,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_wallet_account UNIQUE (user_id)
);

-- 账本流水：只允许 INSERT。冲正生成反向流水并关联原流水，绝不修改历史
CREATE TABLE IF NOT EXISTS meal_wallet_ledger (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    biz_type       VARCHAR(24)   NOT NULL,
    direction      VARCHAR(8)    NOT NULL,
    source         VARCHAR(16)   NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    balance_before DECIMAL(12,2) NOT NULL,
    balance_after  DECIMAL(12,2) NOT NULL,
    ref_type       VARCHAR(32)   DEFAULT NULL,
    ref_id         BIGINT        DEFAULT NULL,
    reverse_of_id  BIGINT        DEFAULT NULL,
    reason         VARCHAR(255)  DEFAULT NULL,
    operator_type  VARCHAR(16)   NOT NULL,
    operator_id    BIGINT        DEFAULT NULL,
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_wallet_ledger_biz UNIQUE (biz_type, ref_type, ref_id, source)
);

-- 券模板
CREATE TABLE IF NOT EXISTS meal_coupon_template (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(64)   NOT NULL,
    type                VARCHAR(16)   NOT NULL DEFAULT 'CASH',
    threshold_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount_rate       DECIMAL(5,4)  DEFAULT NULL,
    max_discount_amount DECIMAL(10,2) DEFAULT NULL,
    valid_days          INT           NOT NULL DEFAULT 30,
    status              TINYINT       NOT NULL DEFAULT 0,
    creator             VARCHAR(64)   DEFAULT NULL,
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(64)   DEFAULT NULL,
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT       NOT NULL DEFAULT 0
);

-- 用户券
CREATE TABLE IF NOT EXISTS meal_coupon (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    code        VARCHAR(32) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    start_time  DATETIME    NOT NULL,
    end_time    DATETIME    NOT NULL,
    used_order_id BIGINT    DEFAULT NULL,
    used_time   DATETIME    DEFAULT NULL,
    creator     VARCHAR(64) DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(64) DEFAULT NULL,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_coupon_code UNIQUE (code)
);

-- 订单。active_key 为 1 表示有效订单，取消后置空：
-- 唯一索引 (user_id, service_date, active_key) 保证「用户 + 服务日」只有一张有效订单，
-- 同时允许取消后重新下单（NULL 不参与唯一性冲突，MySQL 与 H2 行为一致）
-- 周订单组：一键订整周时把多张日订单归为一组，优惠按日分摊
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

CREATE TABLE IF NOT EXISTS meal_order (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no          VARCHAR(32)   NOT NULL,
    user_id           BIGINT        NOT NULL,
    service_date      DATE          NOT NULL,
    plan_id           BIGINT        DEFAULT NULL,
    status            VARCHAR(24)   NOT NULL DEFAULT 'PENDING_LOCK',
    original_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    pricing_version_id BIGINT       DEFAULT NULL,
    pricing_version_no VARCHAR(32)  DEFAULT NULL,
    raw_price         DECIMAL(10,2) DEFAULT NULL,
    swap_difference   DECIMAL(10,2) NOT NULL DEFAULT 0,
    coupon_id         BIGINT        DEFAULT NULL,
    coupon_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    balance_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
    credit_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    cash_amount       DECIMAL(10,2) NOT NULL DEFAULT 0,
    pay_amount        DECIMAL(10,2) NOT NULL DEFAULT 0,
    contact_name      VARCHAR(64)   DEFAULT NULL,
    contact_phone     VARCHAR(32)   DEFAULT NULL,
    address           VARCHAR(255)  DEFAULT NULL,
    active_key        TINYINT       DEFAULT NULL,
    group_id          BIGINT        DEFAULT NULL,
    lock_time         DATETIME      DEFAULT NULL,
    lock_fail_reason  VARCHAR(255)  DEFAULT NULL,
    cancel_time       DATETIME      DEFAULT NULL,
    cancel_reason     VARCHAR(255)  DEFAULT NULL,
    refund_time       DATETIME      DEFAULT NULL,
    remark            VARCHAR(255)  DEFAULT NULL,
    creator           VARCHAR(64)   DEFAULT NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(64)   DEFAULT NULL,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_order_no UNIQUE (order_no),
    CONSTRAINT uk_meal_order_active UNIQUE (user_id, service_date, active_key),
    source_menu_id     BIGINT      DEFAULT NULL,
    source_menu_version VARCHAR(32) DEFAULT NULL
);

-- 订单明细：价格与营养在下单时固定
CREATE TABLE IF NOT EXISTS meal_order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT        NOT NULL,
    meal_type       VARCHAR(16)   NOT NULL,
    dish_version_id BIGINT        NOT NULL,
    dish_name       VARCHAR(64)   NOT NULL,
    delivery_batch  VARCHAR(16)   NOT NULL,
    weight_g        INT           NOT NULL DEFAULT 0,
    protein_g       DECIMAL(8,2)  NOT NULL DEFAULT 0,
    kcal            INT           NOT NULL DEFAULT 0,
    points          DECIMAL(8,2)  NOT NULL DEFAULT 0,
    price           DECIMAL(10,2) NOT NULL DEFAULT 0,
    cost            DECIMAL(12,4) NOT NULL DEFAULT 0,
    creator         VARCHAR(64)   DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)   DEFAULT NULL,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_order_item UNIQUE (order_id, meal_type)
);

-- 锁单快照：只在锁单事务内创建一次
CREATE TABLE IF NOT EXISTS meal_order_snapshot (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    menu_id       BIGINT        DEFAULT NULL,
    menu_version  VARCHAR(32)   DEFAULT NULL,
    total_price   DECIMAL(10,2) NOT NULL DEFAULT 0,
    payload       TEXT          NOT NULL,
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_meal_order_snapshot UNIQUE (order_id)
);

-- 配送：每日两个批次
CREATE TABLE IF NOT EXISTS meal_delivery (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id             BIGINT      NOT NULL,
    delivery_batch       VARCHAR(16) NOT NULL,
    -- 服务日：餐次所属的用餐日期；实际配送日可能早一天（D 日早餐随 D-1 晚餐送达）
    service_date         DATE        DEFAULT NULL,
    actual_delivery_date DATE        DEFAULT NULL,
    -- LUNCH / DINNER / BREAKFAST
    delivery_slot        VARCHAR(16) DEFAULT NULL,
    status               VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    contact_name   VARCHAR(64) DEFAULT NULL,
    contact_phone  VARCHAR(32) DEFAULT NULL,
    address        VARCHAR(255) DEFAULT NULL,
    deliver_time   DATETIME    DEFAULT NULL,
    remark         VARCHAR(255) DEFAULT NULL,
    creator        VARCHAR(64) DEFAULT NULL,
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64) DEFAULT NULL,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_delivery UNIQUE (order_id, delivery_batch, actual_delivery_date)
);

-- 配送明细：一个配送批次实际承运哪些餐次（跨日早餐归属靠这张表表达）
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

-- ========== P4 后场域 ==========

-- 生产批次：按「服务日 + 配送批次 + 菜品」合并，不按订单逐单制作（手册 §9）
CREATE TABLE IF NOT EXISTS meal_production_batch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_date    DATE          NOT NULL,
    delivery_batch  VARCHAR(16)   NOT NULL,
    dish_version_id BIGINT        NOT NULL,
    dish_name       VARCHAR(64)   NOT NULL,
    plan_qty        INT           NOT NULL DEFAULT 0,
    actual_qty      INT           NOT NULL DEFAULT 0,
    waste_qty       INT           NOT NULL DEFAULT 0,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PLANNED',
    remark          VARCHAR(255)  DEFAULT NULL,
    creator         VARCHAR(64)   DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)   DEFAULT NULL,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_production_batch UNIQUE (service_date, delivery_batch, dish_version_id)
);

-- 实际领料与损耗：理论用量由 BOM × 份数推导，实际用量由厨房登记
CREATE TABLE IF NOT EXISTS meal_material_issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id        BIGINT        NOT NULL,
    service_date    DATE          NOT NULL,
    delivery_batch  VARCHAR(16)   NOT NULL,
    ingredient_id   BIGINT        NOT NULL,
    ingredient_name VARCHAR(64)   NOT NULL,
    theoretical_g   DECIMAL(12,2) NOT NULL DEFAULT 0,
    actual_g        DECIMAL(12,2) NOT NULL DEFAULT 0,
    waste_g         DECIMAL(12,2) NOT NULL DEFAULT 0,
    reason          VARCHAR(255)  DEFAULT NULL,
    creator         VARCHAR(64)   DEFAULT NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)   DEFAULT NULL,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_material_issue UNIQUE (batch_id, ingredient_id)
);

-- 成本参数：包装、配送、支付手续费与固定分摊（手册 §10 三层成本）
CREATE TABLE IF NOT EXISTS meal_cost_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_key    VARCHAR(64)   NOT NULL,
    rule_value  DECIMAL(12,4) NOT NULL,
    description VARCHAR(255)  DEFAULT NULL,
    creator     VARCHAR(64)   DEFAULT NULL,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(64)   DEFAULT NULL,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_cost_rule UNIQUE (rule_key)
);

-- 临时替换：锁单后履约偏差记录，保留审批与用户通知状态（快照不随之改变）
CREATE TABLE IF NOT EXISTS meal_substitution (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT      NOT NULL,
    service_date        DATE        NOT NULL,
    meal_type           VARCHAR(16) NOT NULL,
    from_dish_version_id BIGINT     NOT NULL,
    to_dish_version_id  BIGINT      NOT NULL,
    reason              VARCHAR(255) DEFAULT NULL,
    approval_by         BIGINT      DEFAULT NULL,
    approval_time       DATETIME    DEFAULT NULL,
    notify_status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    notify_time         DATETIME    DEFAULT NULL,
    creator             VARCHAR(64) DEFAULT NULL,
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(64) DEFAULT NULL,
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT     NOT NULL DEFAULT 0
);

-- ========== P4.5 定价版本 ==========

-- 定价版本：发布后不可修改，历史订单可解释其计算依据（计价规则 V1.0 §8）
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

-- 已审核的替换选项：追溯至具体成本口径（计价规则 V1.0 §6.4、§8）
CREATE TABLE IF NOT EXISTS meal_pricing_version_swap (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    pricing_version_id BIGINT        NOT NULL,
    meal_type          VARCHAR(16)   NOT NULL,
    from_dish_version_id BIGINT      NOT NULL,
    to_dish_version_id BIGINT        NOT NULL,
    cost_delta         DECIMAL(12,4) NOT NULL DEFAULT 0,
    from_version_cost  DECIMAL(12,4) DEFAULT NULL,
    to_version_cost    DECIMAL(12,4) DEFAULT NULL,
    cost_frozen_time   DATETIME      DEFAULT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'APPROVED',
    remark             VARCHAR(255)  DEFAULT NULL,
    creator            VARCHAR(64)   DEFAULT NULL,
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(64)   DEFAULT NULL,
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_pricing_version_swap UNIQUE (pricing_version_id, meal_type, from_dish_version_id, to_dish_version_id)
);

-- ========== P4.6 复核事项 ==========

-- 复核事项：一个计划可有多条，覆盖蛋白越界、份量触界、价格兜底、成本缺失等
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

-- ========== P8 完餐反馈 ==========

-- 每餐完成后记录剩余比例、饱腹感与训练状态（手册 §5「收餐到反馈」）
CREATE TABLE IF NOT EXISTS meal_feedback (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT      NOT NULL,
    user_id        BIGINT      NOT NULL,
    service_date   DATE        NOT NULL,
    meal_type      VARCHAR(16) NOT NULL,
    leftover_ratio VARCHAR(16) NOT NULL,
    satiety        TINYINT     DEFAULT NULL,
    training_state VARCHAR(32) DEFAULT NULL,
    content        VARCHAR(500) DEFAULT NULL,
    creator        VARCHAR(64) DEFAULT NULL,
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64) DEFAULT NULL,
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_meal_feedback UNIQUE (order_id, meal_type)
);

-- 做菜记录：一锅菜的食材生重、出品组件熟重、步骤与照片 ID（JSON），可同步成菜品版本
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
    deleted            TINYINT       NOT NULL DEFAULT 0
);

-- 做菜步骤照片：存库而非本地磁盘（云托管容器磁盘不持久）
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
    deleted       TINYINT       NOT NULL DEFAULT 0
);
