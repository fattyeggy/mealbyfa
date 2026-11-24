package com.sancan.meal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 业务配置项，前缀 {@code meal}。 */
@ConfigurationProperties(prefix = "meal")
public class MealProperties {

    private final Security security = new Security();
    private final Wx wx = new Wx();
    private final Scheduling scheduling = new Scheduling();
    private final MenuCheck menuCheck = new MenuCheck();
    private final Pricing pricing = new Pricing();

    public Pricing getPricing() {
        return pricing;
    }

    public MenuCheck getMenuCheck() {
        return menuCheck;
    }

    public Security getSecurity() {
        return security;
    }

    public Wx getWx() {
        return wx;
    }

    public Scheduling getScheduling() {
        return scheduling;
    }

    public static class Security {
        /** 令牌有效期（分钟）。 */
        private long tokenTtlMinutes = 720;
        /** 认领码密钥（HMAC）。为空时使用微信 AppSecret；都为空时使用开发密钥。 */
        private String claimSecret = "";

        public String getClaimSecret() {
            return claimSecret;
        }

        public void setClaimSecret(String claimSecret) {
            this.claimSecret = claimSecret;
        }

        public long getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(long tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }
    }

    public static class Wx {
        /** 是否启用 mock 登录（本机联调用）。生产置为 false 走 code2Session。 */
        private boolean mockEnabled = true;
        private String appId = "";
        private String appSecret = "";
        /**
         * 额外信任的 CA 证书（PEM 文件路径，可含多张）。云托管开启「开放接口服务」时，
         * 出网请求会被平台代理用自己的证书接管，需要信任该证书；关闭该服务则不需要。
         */
        private String caCertPath = "";
        /** 应急开关：完全不校验微信接口证书。会让 AppSecret 与登录结果可被中间人截获或伪造，默认关闭。 */
        private boolean insecureSkipTlsVerify = false;

        public String getCaCertPath() {
            return caCertPath;
        }

        public void setCaCertPath(String caCertPath) {
            this.caCertPath = caCertPath;
        }

        public boolean isInsecureSkipTlsVerify() {
            return insecureSkipTlsVerify;
        }

        public void setInsecureSkipTlsVerify(boolean insecureSkipTlsVerify) {
            this.insecureSkipTlsVerify = insecureSkipTlsVerify;
        }

        public boolean isMockEnabled() {
            return mockEnabled;
        }

        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }
    }

    public static class Scheduling {
        /** 锁单定时任务的 cron 表达式，默认每日 12:00，对应手册 §8「前一日 12 点锁单」。 */
        private String lockCron = "0 0 12 * * ?";

        /** 锁单任务是否开启。 */
        private boolean lockEnabled = true;

        public String getLockCron() {
            return lockCron;
        }

        public void setLockCron(String lockCron) {
            this.lockCron = lockCron;
        }

        public boolean isLockEnabled() {
            return lockEnabled;
        }

        public void setLockEnabled(boolean lockEnabled) {
            this.lockEnabled = lockEnabled;
        }
    }

    /** 计价开关。 */
    public static class Pricing {
        /**
         * 是否允许未经审核的替换选项。计价规则 V1.0 §6.4 要求第一阶段只开放少量已审核选项，
         * 因此生产应置为 false，仅开发/测试环境放开。
         */
        private boolean allowUnreviewedSwap = true;

        public boolean isAllowUnreviewedSwap() {
            return allowUnreviewedSwap;
        }

        public void setAllowUnreviewedSwap(boolean allowUnreviewedSwap) {
            this.allowUnreviewedSwap = allowUnreviewedSwap;
        }
    }

    /**
     * 周菜单发布前检查阈值（手册 §6「发布前检查」）。
     *
     * <p>手册要求「每日营养点、蛋白质、三大营养素和全天食物重量在允许范围」，
     * 但未给出具体数值。以下为初始口径，全部可在配置中覆盖，
     * 已列入待业务确认清单。
     */
    public static class MenuCheck {
        private int dailyWeightMinG = 1200;
        private int dailyWeightMaxG = 2200;
        private double standardMenuProteinMinG = 80;
        private double standardMenuProteinMaxG = 180;
        private int dailyKcalMin = 1400;
        private int dailyKcalMax = 2600;
        /** 预计食材成本率预警线，0.40 表示 40%。 */
        private double costRateWarn = 0.40;

        public int getDailyWeightMinG() {
            return dailyWeightMinG;
        }

        public void setDailyWeightMinG(int dailyWeightMinG) {
            this.dailyWeightMinG = dailyWeightMinG;
        }

        public int getDailyWeightMaxG() {
            return dailyWeightMaxG;
        }

        public void setDailyWeightMaxG(int dailyWeightMaxG) {
            this.dailyWeightMaxG = dailyWeightMaxG;
        }

        public double getStandardMenuProteinMinG() {
            return standardMenuProteinMinG;
        }

        public void setStandardMenuProteinMinG(double standardMenuProteinMinG) {
            this.standardMenuProteinMinG = standardMenuProteinMinG;
        }

        public double getStandardMenuProteinMaxG() {
            return standardMenuProteinMaxG;
        }

        public void setStandardMenuProteinMaxG(double standardMenuProteinMaxG) {
            this.standardMenuProteinMaxG = standardMenuProteinMaxG;
        }

        public int getDailyKcalMin() {
            return dailyKcalMin;
        }

        public void setDailyKcalMin(int dailyKcalMin) {
            this.dailyKcalMin = dailyKcalMin;
        }

        public int getDailyKcalMax() {
            return dailyKcalMax;
        }

        public void setDailyKcalMax(int dailyKcalMax) {
            this.dailyKcalMax = dailyKcalMax;
        }

        public double getCostRateWarn() {
            return costRateWarn;
        }

        public void setCostRateWarn(double costRateWarn) {
            this.costRateWarn = costRateWarn;
        }
    }
}
