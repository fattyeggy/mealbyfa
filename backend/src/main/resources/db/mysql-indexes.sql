-- 生产 MySQL 次级索引（H2 不支持 / 不必要，故与 schema.sql 分离）
-- 部署脚本在首次建库后执行本文件；重复执行会因索引已存在报错，属预期行为。

CREATE INDEX idx_member_user_mobile ON member_user (mobile);
CREATE INDEX idx_meal_audit_log_create_time ON meal_audit_log (create_time);
CREATE INDEX idx_meal_audit_log_target ON meal_audit_log (target_type, target_id);

-- P1 菜单域
CREATE INDEX idx_meal_ingredient_category ON meal_ingredient (category);
CREATE INDEX idx_meal_dish_status ON meal_dish (status);
CREATE INDEX idx_meal_dish_version_dish ON meal_dish_version (dish_id, status);
CREATE INDEX idx_meal_recipe_bom_version ON meal_recipe_bom (dish_version_id);
CREATE INDEX idx_meal_week_menu_region ON meal_week_menu (region, start_date);
CREATE INDEX idx_meal_menu_day_menu ON meal_menu_day (menu_id);
CREATE INDEX idx_meal_menu_meal_menu ON meal_menu_meal (menu_id, delivery_batch);

-- P2 计划与计价
CREATE INDEX idx_meal_nutrition_plan_user ON meal_nutrition_plan (user_id, week_start);
CREATE INDEX idx_meal_plan_item_plan ON meal_plan_item (plan_id);
CREATE INDEX idx_meal_plan_item_user_date ON meal_plan_item (user_id, service_date);

-- P3 交易域
CREATE INDEX idx_meal_wallet_ledger_user ON meal_wallet_ledger (user_id, create_time);
CREATE INDEX idx_meal_coupon_user ON meal_coupon (user_id, status);
CREATE INDEX idx_meal_order_status_date ON meal_order (status, service_date);
CREATE INDEX idx_meal_order_user ON meal_order (user_id, service_date DESC);
CREATE INDEX idx_meal_delivery_batch ON meal_delivery (delivery_batch, status);

-- P4 后场域
CREATE INDEX idx_meal_production_batch_date ON meal_production_batch (service_date, delivery_batch);
CREATE INDEX idx_meal_material_issue_date ON meal_material_issue (service_date, delivery_batch);
CREATE INDEX idx_meal_substitution_order ON meal_substitution (order_id);

-- P4.5 定价版本
CREATE INDEX idx_meal_pricing_version_status ON meal_pricing_version (status, effective_at);
CREATE INDEX idx_meal_pricing_version_swap_pair ON meal_pricing_version_swap (pricing_version_id, meal_type);
CREATE INDEX idx_meal_order_pricing_version ON meal_order (pricing_version_id);

-- P4.6 复核事项
CREATE INDEX idx_meal_plan_review_plan ON meal_plan_review (plan_id, status);
CREATE INDEX idx_meal_plan_review_scope ON meal_plan_review (plan_id, service_date, meal_type);
