<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TabBar from '@/components/TabBar.vue'
import { api, switchDemoAccount } from '@/utils/api'
import { couponRule, money, refreshProfile, refreshWallet, session } from '@/store/session'
// 演示账号切换只在开发构建里出现，正式版不显示
const isDev = !!(import.meta as any).env?.DEV
import { safeTop } from '@/utils/safe-area'
const topInset = safeTop(16, false)

const coupons = ref<any[]>([])
const ledger = ref<any[]>([])
const loading = ref(true)

onMounted(load)

async function load() {
  loading.value = true
  try {
    await refreshProfile()
    await refreshWallet()
    const page: any = await api.get('/app/wallet/ledger?pageNo=1&pageSize=8')
    ledger.value = page.list || []
    coupons.value = await api.get('/app/coupons?status=UNUSED')
  } catch (e) {
    // 未登录或网络异常时保持空态
  } finally {
    loading.value = false
  }
}

function ledgerLabel(type: string) {
  const map: Record<string, string> = {
    CREDIT_GRANT: '赠送余额',
    TOPUP: '充值',
    ORDER_PAY: '订单支付',
    REFUND: '退款',
    ADJUST: '人工调整',
    REVERSE: '冲正'
  }
  return map[type] || type
}

function signOf(row: any) {
  return row.direction === 'IN' ? '+' : '-'
}

function goOnboarding() {
  uni.navigateTo({ url: '/pages/onboarding/index' })
}

function switchAccount() {
  uni.showModal({
    title: '切换演示账号',
    content: '会新建一个演示账号，便于查看不同体重的计划与价格。',
    success: async (res) => {
      if (!res.confirm) return
      await switchDemoAccount()
      uni.reLaunch({ url: '/pages/index/index' })
    }
  })
}

const goalText: Record<string, string> = { LOSE: '减脂', MAINTAIN: '维持', GAIN: '增肌', cut: '减脂', maintain: '维持', bulk: '增肌' }
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="head">
      <view>
        <view class="brand">A C C O U N T</view>
        <view class="title">我的</view>
      </view>
    </view>

    <view class="wallet">
      <view class="wallet-label">账户余额</view>
      <view class="wallet-amount">¥{{ money(session.wallet.balance) }}</view>
      <view class="wallet-split">
        <text>套餐余额 {{ money(session.wallet.creditBalance) }}</text>
        <text>现金 {{ money(session.wallet.cashBalance) }}</text>
      </view>
    </view>

    <view class="section-title">我的档案</view>
    <view v-if="session.profile" class="card profile">
      <view class="row between">
        <text>{{ session.profile.heightCm }}cm · {{ session.profile.weightKg }}kg</text>
        <text class="muted">{{ goalText[session.profile.goal] || session.profile.goal }}</text>
      </view>
      <view class="row between profile-row">
        <text class="muted">过敏原</text>
        <text class="muted">{{ session.profile.allergies?.length ? session.profile.allergies.join('、') : '无' }}</text>
      </view>
    </view>
    <view v-else class="card profile empty">
      <text class="muted">还没有完成建档</text>
      <view class="btn" @click="goOnboarding">去建档</view>
    </view>

    <view class="section-title">优惠券</view>
    <view v-if="!coupons.length" class="card empty-coupon"><text class="muted">暂无可用优惠券</text></view>
    <view v-else class="coupon-list">
      <view v-for="coupon in coupons" :key="coupon.id" class="coupon">
        <view class="coupon-amount">券</view>
        <view class="coupon-main">
          <text class="coupon-code">{{ coupon.name }}</text>
          <text class="muted">{{ couponRule(coupon) }} · 有效期至 {{ coupon.endTime?.slice(0, 10) }}</text>
        </view>
      </view>
    </view>

    <view class="section-title">余额明细</view>
    <view class="card ledger">
      <view v-if="!ledger.length" class="muted">暂无记录</view>
      <view v-for="row in ledger" :key="row.id" class="ledger-row">
        <view class="row between">
          <text class="ledger-label">{{ ledgerLabel(row.bizType) }}</text>
          <text class="ledger-amount" :class="{ minus: row.direction !== 'IN' }">
            {{ signOf(row) }}{{ money(row.amount) }}
          </text>
        </view>
        <view class="muted ledger-sub">
          {{ row.createTime?.replace('T', ' ').slice(5, 16) }} · 余额 {{ money(row.balanceAfter) }}
        </view>
      </view>
    </view>

    <view v-if="isDev" class="btn btn-ghost switch" @click="switchAccount">切换演示账号</view>

    <TabBar current="me" />
  </view>
</template>

<style scoped>
.head {
  padding: 24px 0 14px;
}

.wallet {
  background: #032C0A;
  border-radius: 20px;
  padding: 22px;
  color: #F4F1E9;
}

.wallet-label {
  font-size: 13px;
  opacity: 0.75;
}

.wallet-amount {
  font-size: 34px;
  font-weight: 700;
  margin-top: 6px;
  letter-spacing: 1px;
}

.wallet-split {
  display: flex;
  gap: 14px;
  font-size: 13px;
  opacity: 0.8;
  margin-top: 10px;
}

.profile {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-row {
  font-size: 13px;
}

.empty {
  align-items: center;
  gap: 12px;
}

.empty-coupon {
  padding: 18px;
  text-align: center;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.coupon {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 20px;
  padding: 12px 14px;
}

.coupon-amount {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #fbf0e2;
  color: #875A1F;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.coupon-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.coupon-code {
  font-size: 13px;
  font-weight: 600;
}

.ledger {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ledger-row {
  border-bottom: 1px solid #EEF3EE;
  padding-bottom: 8px;
}

.ledger-row:last-child {
  border-bottom: none;
}

.ledger-label {
  font-size: 14px;
}

.ledger-amount {
  font-size: 14px;
  font-weight: 600;
  color: #032C0A;
}

.ledger-amount.minus {
  color: #875A1F;
}

.ledger-sub {
  margin-top: 4px;
}

.switch {
  margin-top: 18px;
}
</style>
