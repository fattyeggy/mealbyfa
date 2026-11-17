package com.sancan.meal.member.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.coupon.dal.dataobject.CouponDO;
import com.sancan.meal.coupon.dal.mapper.CouponMapper;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import com.sancan.meal.member.user.dal.mapper.MemberUserMapper;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.plan.dal.dataobject.UserProfileDO;
import com.sancan.meal.plan.dal.mapper.UserProfileMapper;
import com.sancan.meal.wallet.dal.dataobject.WalletAccountDO;
import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import com.sancan.meal.wallet.dal.mapper.WalletAccountMapper;
import com.sancan.meal.wallet.dal.mapper.WalletLedgerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 手机号认领：后台按手机号建的档，用户在微信里输入同一手机号后合并到当前微信账号。
 * 合并范围：档案、钱包余额与流水、券、订单；占位账号随后删除。
 */
@Service
public class MemberBindingService {

    private final MemberUserMapper memberUserMapper;
    private final UserProfileMapper profileMapper;
    private final WalletAccountMapper accountMapper;
    private final WalletLedgerMapper ledgerMapper;
    private final CouponMapper couponMapper;
    private final OrderMapper orderMapper;

    public MemberBindingService(MemberUserMapper memberUserMapper, UserProfileMapper profileMapper,
                                WalletAccountMapper accountMapper, WalletLedgerMapper ledgerMapper,
                                CouponMapper couponMapper, OrderMapper orderMapper) {
        this.memberUserMapper = memberUserMapper;
        this.profileMapper = profileMapper;
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.couponMapper = couponMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public Long bind(Long userId, String rawPhone) {
        String phone = normalize(rawPhone);
        MemberUserDO current = memberUserMapper.selectById(userId);
        if (current == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED);
        }
        MemberUserDO existing = memberUserMapper.selectByMobile(phone);
        if (existing == null || existing.getId().equals(userId)) {
            current.setMobile(phone);
            memberUserMapper.updateById(current);
            return userId;
        }
        if (existing.getOpenid() != null && !existing.getOpenid().startsWith("admin_")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "该手机号已绑定其他微信账号");
        }
        Long placeholderId = existing.getId();

        // 档案：以后台录入的为准
        UserProfileDO currentProfile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserProfileDO>().eq(UserProfileDO::getUserId, userId));
        if (currentProfile != null) {
            profileMapper.deleteById(currentProfile.getId());
        }
        profileMapper.update(null, new LambdaUpdateWrapper<UserProfileDO>()
                .eq(UserProfileDO::getUserId, placeholderId)
                .set(UserProfileDO::getUserId, userId));

        // 钱包
        WalletAccountDO currentAccount = accountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccountDO>().eq(WalletAccountDO::getUserId, userId));
        WalletAccountDO placeholderAccount = accountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccountDO>().eq(WalletAccountDO::getUserId, placeholderId));
        if (placeholderAccount != null) {
            if (currentAccount == null) {
                placeholderAccount.setUserId(userId);
                accountMapper.updateById(placeholderAccount);
                ledgerMapper.transferOwner(placeholderId, userId, placeholderAccount.getId());
            } else {
                currentAccount.setBalance(nz(currentAccount.getBalance()).add(nz(placeholderAccount.getBalance())));
                currentAccount.setCreditBalance(nz(currentAccount.getCreditBalance()).add(nz(placeholderAccount.getCreditBalance())));
                currentAccount.setCashBalance(nz(currentAccount.getCashBalance()).add(nz(placeholderAccount.getCashBalance())));
                accountMapper.updateById(currentAccount);
                ledgerMapper.transferOwner(placeholderId, userId, currentAccount.getId());
                accountMapper.deleteById(placeholderAccount.getId());
            }
        }

        couponMapper.update(null, new LambdaUpdateWrapper<CouponDO>()
                .eq(CouponDO::getUserId, placeholderId).set(CouponDO::getUserId, userId));
        orderMapper.update(null, new LambdaUpdateWrapper<OrderDO>()
                .eq(OrderDO::getUserId, placeholderId).set(OrderDO::getUserId, userId));

        current.setMobile(phone);
        memberUserMapper.updateById(current);
        memberUserMapper.deleteById(placeholderId);
        return userId;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalize(String phone) {
        String value = phone == null ? "" : phone.replaceAll("\\s", "");
        if (!value.matches("1\\d{10}")) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        return value;
    }
}
