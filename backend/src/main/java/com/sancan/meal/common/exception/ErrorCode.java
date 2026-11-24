package com.sancan.meal.common.exception;

/**
 * 错误码。1000 段为通用错误；后续业务域各自开段：
 * 1100 菜单、1200 计划与计价、1300 账户与券、1400 订单、1500 生产与成本。
 */
public enum ErrorCode {

    SUCCESS(0, "成功"),

    // 通用
    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有该操作权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统异常，请稍后重试"),

    // 菜单域
    MENU_NOT_FOUND(1100, "周菜单不存在"),
    MENU_STATUS_INVALID(1101, "当前菜单状态不允许该操作"),
    MENU_PUBLISH_CHECK_FAILED(1102, "发布前检查未通过"),
    MENU_VERSION_DUPLICATED(1103, "该周菜单版本已存在"),
    MENU_ALREADY_PUBLISHED(1104, "菜单已发布，不可原地修改"),
    DISH_NOT_FOUND(1105, "菜品不存在"),
    INGREDIENT_NOT_FOUND(1106, "食材不存在"),

    // 计划与计价
    PROFILE_INCOMPLETE(1200, "请先完成 Onboarding 建档"),
    PLAN_NOT_FOUND(1201, "营养计划不存在"),
    PLAN_DATE_OUT_OF_RANGE(1202, "目标服务日不在菜单有效期内"),
    PROTEIN_SWAP_NOT_ALLOWED(1203, "该餐次不允许替换蛋白质来源"),
    PRICING_RULE_MISSING(1204, "缺少计价规则"),
    PLAN_ITEM_NOT_FOUND(1205, "计划餐次不存在"),
    ALLERGEN_CONFLICT(1206, "菜品含用户过敏原，已阻止"),
    PRICING_VERSION_MISSING(1207, "缺少生效的定价版本"),
    PRICING_COST_MISSING(1208, "缺少有效成本数据，待人工复核"),
    PRICING_SWAP_NOT_ALLOWED(1209, "该替换选项尚未审核开放"),

    // 账户与券
    WALLET_NOT_FOUND(1300, "账户不存在"),
    WALLET_BALANCE_INSUFFICIENT(1301, "账户余额不足"),
    LEDGER_RECORD_IMMUTABLE(1302, "账本流水不可修改或删除，请使用冲正"),
    CREDIT_GRANT_REASON_REQUIRED(1303, "发放 credit 必须填写原因"),
    COUPON_NOT_FOUND(1304, "优惠券不存在"),
    COUPON_UNAVAILABLE(1305, "优惠券不可用"),
    COUPON_THRESHOLD_NOT_REACHED(1306, "未达到优惠券使用门槛"),

    // 订单
    ORDER_NOT_FOUND(1400, "订单不存在"),
    ORDER_STATUS_INVALID(1401, "当前订单状态不允许该操作"),
    ORDER_LOCK_DUPLICATED(1402, "该服务日订单已锁定"),
    ORDER_LOCK_DEADLINE_PASSED(1403, "已超过锁单截止时间"),
    ORDER_ALLERGEN_CONFLICT(1404, "餐次含过敏原，已阻止下单"),

    // 生产与成本
    PRODUCTION_BATCH_NOT_FOUND(1500, "生产批次不存在"),
    INGREDIENT_UNIT_UNCONVERTIBLE(1501, "食材采购单位与使用单位无法换算"),

    // 反馈
    FEEDBACK_MEAL_NOT_DELIVERED(1600, "该餐次尚未送达，暂时不能反馈"),
    FEEDBACK_DUPLICATED(1601, "该餐次已经反馈过了"),

    // 会员
    CLAIM_CODE_REQUIRED(1700, "这个手机号已有后台建好的档案，请输入客服提供的 6 位认领码");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
