package com.sancan.meal.pricing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.audit.service.AuditLogService;
import com.sancan.meal.common.security.UserContext;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionDO;
import com.sancan.meal.pricing.dal.dataobject.PricingVersionSwapDO;
import com.sancan.meal.pricing.dal.mapper.PricingVersionMapper;
import com.sancan.meal.pricing.dal.mapper.PricingVersionSwapMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定价版本生命周期（计价规则 V1.0 §8）。
 *
 * <p>发布后不可修改；改价必须新建版本并发布，历史订单继续引用旧版本。
 */
@Service
public class PricingVersionService {

    private final PricingVersionMapper versionMapper;
    private final PricingVersionSwapMapper swapMapper;
    private final DishVersionMapper dishVersionMapper;
    private final AuditLogService auditLogService;

    public PricingVersionService(PricingVersionMapper versionMapper,
                                 PricingVersionSwapMapper swapMapper,
                                 DishVersionMapper dishVersionMapper,
                                 AuditLogService auditLogService) {
        this.versionMapper = versionMapper;
        this.swapMapper = swapMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.auditLogService = auditLogService;
    }

    /** 新建版本入参。 */
    public record DraftReq(String versionNo, BigDecimal dailyBasePrice, Integer includedProteinG,
                           BigDecimal extraProteinPricePer10g, BigDecimal minDailyPrice,
                           Integer minTargetProteinG, Integer maxTargetProteinG, Integer manualAdjustStepG,
                           BigDecimal swapCostDivisor, BigDecimal breakfastRatio, BigDecimal lunchRatio,
                           String remark) {
    }

    /** 首次启动落一个符合 V1.0 的正式基线版本。 */
    @PostConstruct
    @Transactional
    public void seedBaseline() {
        Long count = versionMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        PricingVersionDO version = new PricingVersionDO();
        version.setVersionNo("V1.0");
        version.setStatus("PUBLISHED");
        version.setEffectiveAt(LocalDateTime.now());
        version.setDailyBasePrice(new BigDecimal("66.00"));
        version.setIncludedProteinG(60);
        version.setExtraProteinPricePer10g(new BigDecimal("1.90"));
        version.setMinDailyPrice(new BigDecimal("65.00"));
        version.setMinTargetProteinG(80);
        version.setMaxTargetProteinG(220);
        version.setManualAdjustStepG(10);
        version.setSwapCostDivisor(new BigDecimal("0.7700"));
        version.setBreakfastRatio(new BigDecimal("0.2500"));
        version.setLunchRatio(new BigDecimal("0.4000"));
        version.setRoundingMode("HALF_UP_INTEGER");
        version.setCreatedBy("system");
        version.setReviewedBy("system");
        version.setPublishedBy("system");
        version.setPublishTime(LocalDateTime.now());
        version.setRemark("依据《三餐私教计价规则与开发验收 V1.0》建立的基线版本");
        versionMapper.insert(version);
    }

    /** 当前生效版本：已发布且生效时间已到，取最新。 */
    public PricingVersionDO active() {
        PricingVersionDO version = versionMapper.selectOne(new LambdaQueryWrapper<PricingVersionDO>()
                .eq(PricingVersionDO::getStatus, "PUBLISHED")
                .le(PricingVersionDO::getEffectiveAt, LocalDateTime.now())
                .orderByDesc(PricingVersionDO::getEffectiveAt)
                .last("LIMIT 1"));
        if (version == null) {
            throw new ServiceException(ErrorCode.PRICING_VERSION_MISSING);
        }
        return version;
    }

    public PricingVersionDO require(Long id) {
        PricingVersionDO version = id == null ? null : versionMapper.selectById(id);
        if (version == null) {
            throw new ServiceException(ErrorCode.PRICING_VERSION_MISSING);
        }
        return version;
    }

    public List<PricingVersionDO> list() {
        return versionMapper.selectList(new LambdaQueryWrapper<PricingVersionDO>()
                .orderByDesc(PricingVersionDO::getId));
    }

    @Transactional
    public Long createDraft(DraftReq req) {
        PricingVersionDO draft = new PricingVersionDO();
        draft.setVersionNo(req.versionNo() == null || req.versionNo().isBlank()
                ? nextVersionNo() : req.versionNo().trim());
        if (versionMapper.selectCount(new LambdaQueryWrapper<PricingVersionDO>()
                .eq(PricingVersionDO::getVersionNo, draft.getVersionNo())) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "版本号已存在：" + draft.getVersionNo());
        }
        draft.setStatus("DRAFT");
        draft.setEffectiveAt(LocalDateTime.now());
        draft.setDailyBasePrice(req.dailyBasePrice() == null ? new BigDecimal("66.00") : req.dailyBasePrice());
        draft.setIncludedProteinG(req.includedProteinG() == null ? 60 : req.includedProteinG());
        draft.setExtraProteinPricePer10g(req.extraProteinPricePer10g() == null
                ? new BigDecimal("1.90") : req.extraProteinPricePer10g());
        draft.setMinDailyPrice(req.minDailyPrice() == null ? new BigDecimal("65.00") : req.minDailyPrice());
        draft.setMinTargetProteinG(req.minTargetProteinG() == null ? 80 : req.minTargetProteinG());
        draft.setMaxTargetProteinG(req.maxTargetProteinG() == null ? 220 : req.maxTargetProteinG());
        draft.setManualAdjustStepG(req.manualAdjustStepG() == null ? 10 : req.manualAdjustStepG());
        draft.setSwapCostDivisor(req.swapCostDivisor() == null
                ? new BigDecimal("0.7700") : req.swapCostDivisor());
        draft.setBreakfastRatio(req.breakfastRatio() == null
                ? new BigDecimal("0.2500") : req.breakfastRatio());
        draft.setLunchRatio(req.lunchRatio() == null ? new BigDecimal("0.4000") : req.lunchRatio());
        validate(draft);
        draft.setRoundingMode("HALF_UP_INTEGER");
        draft.setCreatedBy(UserContext.currentActor());
        draft.setRemark(req.remark());
        versionMapper.insert(draft);
        return draft.getId();
    }

    /** 草稿参数校验（P4.6 §7）。 */
    private void validate(PricingVersionDO draft) {
        requirePositive(draft.getDailyBasePrice(), "基础日价");
        requirePositive(draft.getExtraProteinPricePer10g(), "加量单价");
        requirePositive(draft.getMinDailyPrice(), "最低日价");
        requirePositive(draft.getSwapCostDivisor(), "成本折算系数");
        if (draft.getIncludedProteinG() == null || draft.getIncludedProteinG() < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "基础蛋白不能为负");
        }
        if (draft.getMinTargetProteinG() == null || draft.getMaxTargetProteinG() == null
                || draft.getMinTargetProteinG() > draft.getMaxTargetProteinG()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "蛋白范围不合法：最小值不得大于最大值");
        }
        if (draft.getManualAdjustStepG() == null || draft.getManualAdjustStepG() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "蛋白调整步长必须大于 0");
        }
        BigDecimal breakfast = draft.getBreakfastRatio();
        BigDecimal lunch = draft.getLunchRatio();
        if (breakfast == null || lunch == null || breakfast.signum() < 0 || lunch.signum() < 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "三餐比例不能为负");
        }
        if (breakfast.add(lunch).compareTo(BigDecimal.ONE) > 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "早餐与午餐比例之和不得超过 100%");
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, field + "必须大于 0");
        }
    }

    /** 复核：发布前必须有复核人与复核时间。 */
    @Transactional
    public void review(Long id) {
        PricingVersionDO version = require(id);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "已发布的版本不能再复核");
        }
        version.setReviewedBy(UserContext.currentActor());
        version.setReviewedTime(LocalDateTime.now());
        versionMapper.updateById(version);
        auditLogService.record("PRICING_PUBLISH", "PRICING_VERSION", id,
                "发布定价版本 " + version.getVersionNo() + "，基础日价 " + version.getDailyBasePrice());
    }

    /** 发布：归档旧版本，新版本对之后新生成的计划生效，已确认订单不受影响。 */
    @Transactional
    public void publish(Long id) {
        PricingVersionDO version = require(id);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该版本已发布");
        }
        if (version.getReviewedTime() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "发布前必须先完成复核");
        }
        versionMapper.update(null, new LambdaUpdateWrapper<PricingVersionDO>()
                .eq(PricingVersionDO::getStatus, "PUBLISHED")
                .set(PricingVersionDO::getStatus, "ARCHIVED"));
        version.setStatus("PUBLISHED");
        version.setPublishedBy(UserContext.currentActor());
        version.setPublishTime(LocalDateTime.now());
        if (version.getEffectiveAt() == null) {
            version.setEffectiveAt(LocalDateTime.now());
        }
        versionMapper.updateById(version);
    }

    public List<PricingVersionSwapDO> swaps(Long versionId) {
        return swapMapper.selectList(new LambdaQueryWrapper<PricingVersionSwapDO>()
                .eq(PricingVersionSwapDO::getPricingVersionId, versionId)
                .orderByDesc(PricingVersionSwapDO::getId));
    }

    @Transactional
    public Long addSwap(Long versionId, String mealType, Long fromDishVersionId,
                        Long toDishVersionId, BigDecimal costDelta, String remark) {
        PricingVersionDO version = require(versionId);
        if ("PUBLISHED".equals(version.getStatus())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "已发布的定价版本不能追加替换选项，请新建版本");
        }
        // 审核即冻结成本：口径来自当时的菜品版本标准份成本，
        // 之后重算成本或改采购价都不会改变已审核选项的差价依据。
        DishVersionDO fromVersion = dishVersionMapper.selectById(fromDishVersionId);
        DishVersionDO toVersion = dishVersionMapper.selectById(toDishVersionId);
        if (fromVersion == null || toVersion == null) {
            throw new ServiceException(ErrorCode.DISH_NOT_FOUND, "替换选项引用的菜品版本不存在");
        }
        BigDecimal fromCost = fromVersion.getCost();
        BigDecimal toCost = toVersion.getCost();
        if (fromCost == null || fromCost.signum() <= 0 || toCost == null || toCost.signum() <= 0) {
            throw new ServiceException(ErrorCode.PRICING_COST_MISSING,
                    "原餐次或替换餐次缺少有效成本，不能审核为可售替换选项");
        }

        PricingVersionSwapDO option = new PricingVersionSwapDO();
        option.setPricingVersionId(versionId);
        option.setMealType(mealType == null ? null : mealType.trim().toUpperCase());
        option.setFromDishVersionId(fromDishVersionId);
        option.setToDishVersionId(toDishVersionId);
        option.setFromVersionCost(fromCost);
        option.setToVersionCost(toCost);
        option.setCostFrozenTime(LocalDateTime.now());
        option.setCostDelta(toCost.subtract(fromCost));
        option.setStatus("APPROVED");
        option.setRemark(remark);
        swapMapper.insert(option);
        return option.getId();
    }

    public Optional<PricingVersionSwapDO> findSwap(Long versionId, String mealType,
                                                   Long fromDishVersionId, Long toDishVersionId) {
        return Optional.ofNullable(swapMapper.selectOne(new LambdaQueryWrapper<PricingVersionSwapDO>()
                .eq(PricingVersionSwapDO::getPricingVersionId, versionId)
                .eq(PricingVersionSwapDO::getMealType, mealType)
                .eq(PricingVersionSwapDO::getFromDishVersionId, fromDishVersionId)
                .eq(PricingVersionSwapDO::getToDishVersionId, toDishVersionId)
                .eq(PricingVersionSwapDO::getStatus, "APPROVED")
                .last("LIMIT 1")));
    }

    private String nextVersionNo() {
        List<PricingVersionDO> versions = list();
        int maxMinor = 0;
        for (PricingVersionDO version : versions) {
            String no = version.getVersionNo();
            if (no != null && no.startsWith("V1.")) {
                try {
                    maxMinor = Math.max(maxMinor, Integer.parseInt(no.substring(3)));
                } catch (NumberFormatException ignored) {
                    // 忽略非 V1.x 命名的历史数据
                }
            }
        }
        return "V1." + (maxMinor + 1);
    }
}
