<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TabBar from '@/components/TabBar.vue'
import { api } from '@/utils/api'
import { cleanDishName, mealLabel, money, todayIso } from '@/store/session'
import { safeTop } from '@/utils/safe-area'
const topInset = safeTop(16, false)

const loading = ref(true)
const errorText = ref('')
const orders = ref<any[]>([])
const detail = ref<any>(null)
const showDetail = ref(false)
const weekSummary = ref<any>(null)

const showFeedback = ref(false)
const feedbackMeal = ref<any>(null)
const feedbackRatio = ref('EATEN')
const feedbackSatiety = ref(4)
const feedbackContent = ref('')
const submitting = ref(false)

const ratios = [
  { value: 'EATEN', label: '吃完' },
  { value: 'QUARTER', label: '剩四分之一' },
  { value: 'HALF', label: '剩一半' },
  { value: 'NONE', label: '未吃' }
]

onMounted(load)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const page: any = await api.get('/app/orders?pageNo=1&pageSize=30')
    orders.value = page.list || []
    try {
      const today = new Date()
      const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
      weekSummary.value = await api.get(`/app/feedback/week?date=${iso}`)
    } catch (e) {
      weekSummary.value = null
    }
  } catch (e: any) {
    errorText.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function open(order: any) {
  try {
    detail.value = await api.get(`/app/orders/${order.id}`)
    showDetail.value = true
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

async function cancel() {
  if (!detail.value) return
  uni.showModal({
    title: '取消订单',
    content: '锁单前取消会把优惠券退回，确认取消吗？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await api.post(`/app/orders/${detail.value.id}/cancel`, { reason: '用户取消' })
        showDetail.value = false
        await load()
        uni.showToast({ title: '已取消', icon: 'success' })
      } catch (e: any) {
        uni.showModal({ title: '取消失败', content: e.message || '请稍后再试', showCancel: false })
      }
    }
  })
}

function openFeedback(item: any) {
  feedbackMeal.value = item
  feedbackRatio.value = 'EATEN'
  feedbackSatiety.value = 4
  feedbackContent.value = ''
  showFeedback.value = true
}

async function submitFeedback() {
  if (!detail.value || !feedbackMeal.value) return
  submitting.value = true
  try {
    await api.post(`/app/orders/${detail.value.id}/feedback`, {
      mealType: feedbackMeal.value.mealType,
      leftoverRatio: feedbackRatio.value,
      satiety: feedbackSatiety.value,
      content: feedbackContent.value
    })
    showFeedback.value = false
    uni.showToast({ title: '已提交反馈', icon: 'success' })
    await load()
  } catch (e: any) {
    uni.showModal({ title: '暂时不能反馈', content: e.message || '请稍后再试', showCancel: false })
  } finally {
    submitting.value = false
  }
}

/** 列表显示实际要付的金额（标价减券），不是标价 */
function payable(order: any) {
  return Math.max(0, Number(order.originalAmount || 0) - Number(order.couponAmount || 0))
}

/** 只在送达后（或服务日已过）显示反馈入口；未送达点了只会报错 */
function canFeedback(order: any) {
  if (!order || ['PENDING_CONFIRM', 'PENDING_LOCK', 'CANCELLED', 'REFUNDED', 'PAUSED'].includes(order.status)) return false
  return order.serviceDate <= todayIso()
}

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING_CONFIRM: '待确认',
    PENDING_LOCK: '待锁定',
    LOCKED: '已锁定',
    PURCHASE_AGG: '采购汇总',
    PRODUCING: '生产中',
    LUNCH_DELIVERING: '午餐配送中',
    LUNCH_DONE: '午餐已送达',
    DINNER_DELIVERING: '晚餐配送中',
    DINNER_DONE: '晚餐已送达',
    COMPLETED: '已完成',
    PAUSED: '已暂停',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }
  return map[status] || status
}

function statusClass(status: string) {
  if (status === 'LOCKED' || status === 'COMPLETED') return 'ok'
  if (status === 'CANCELLED' || status === 'REFUNDED') return 'off'
  return 'wait'
}

const mealOrder = ['BREAKFAST', 'LUNCH', 'DINNER']
function sortedItems(items: any[]) {
  return [...(items || [])].sort(
    (a, b) => mealOrder.indexOf(a.mealType) - mealOrder.indexOf(b.mealType)
  )
}
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="head">
      <view>
        <view class="brand">R E C O R D</view>
        <view class="title">用餐记录</view>
      </view>
    </view>

    <view v-if="weekSummary && weekSummary.summary?.totalMeals" class="card summary">
      <view class="row between">
        <text class="summary-title">本周已反馈 {{ weekSummary.summary.totalMeals }} 餐</text>
        <text class="muted">平均饱腹 {{ weekSummary.summary.avgSatiety }} / 5</text>
      </view>
      <view class="bar">
        <view class="bar-fill" :style="{ width: Math.round(weekSummary.summary.eatenRatio * 100) + '%' }" />
      </view>
      <text class="muted">平均进食率 {{ Math.round(weekSummary.summary.eatenRatio * 100) }}%</text>
    </view>

    <view v-if="loading" class="card tip"><text class="muted">加载中…</text></view>
    <view v-else-if="errorText" class="card tip">
      <text class="muted">{{ errorText }}</text>
      <view class="btn btn-ghost retry" @click="load">重新加载</view>
    </view>
    <view v-else-if="!orders.length" class="card tip">
      <text class="muted">还没有订单</text>
      <text class="muted">回到首页确认方案后即可下单</text>
    </view>

    <view v-for="order in orders" :key="order.id" class="card order" @click="open(order)">
      <view class="row between">
        <text class="order-date">{{ order.serviceDate }}</text>
        <text class="status" :class="statusClass(order.status)">{{ statusText(order.status) }}</text>
      </view>
      <view class="row between order-meta">
        <text class="muted">{{ order.orderNo }}</text>
        <text class="order-amount">¥{{ money(payable(order)) }}</text>
      </view>
    </view>

    <TabBar current="orders" />

    <view v-if="showDetail && detail" class="sheet-mask" @click.self="showDetail = false">
      <view class="sheet">
        <view class="row between">
          <text class="sheet-title">{{ detail.serviceDate }} 的订单</text>
          <text class="status" :class="statusClass(detail.status)">{{ detail.statusLabel }}</text>
        </view>

        <view class="divider" />
        <view v-for="item in sortedItems(detail.items)" :key="item.mealType" class="item">
          <view class="row between">
            <view class="row">
              <text class="meal-badge">{{ item.mealLabel || mealLabel(item.mealType) }}</text>
              <text class="item-name">{{ cleanDishName(item.dishName) }}</text>
            </view>
            <text>¥{{ money(item.price) }}</text>
          </view>
          <view class="row between">
            <text class="muted">{{ item.weightG }}g · {{ item.proteinG }}g 蛋白 · {{ item.kcal }} kcal</text>
            <text v-if="canFeedback(detail)" class="feedback-link" @click="openFeedback(item)">反馈</text>
          </view>
        </view>

        <view class="divider" />
        <view class="row between amount-line"><text class="muted">标价</text><text>¥{{ money(detail.originalAmount) }}</text></view>
        <view class="row between amount-line" v-if="Number(detail.couponAmount)">
          <text class="muted">优惠券</text><text>-¥{{ money(detail.couponAmount) }}</text>
        </view>
        <view class="row between amount-line" v-if="Number(detail.balanceAmount)">
          <text class="muted">余额抵扣</text><text>¥{{ money(detail.balanceAmount) }}</text>
        </view>
        <view class="row between amount-line">
          <text class="muted">应付</text><text>¥{{ money(payable(detail)) }}</text>
        </view>
        <view class="muted" v-if="detail.lockFailReason">{{ detail.lockFailReason }}</view>
        <view class="muted">配送：{{ detail.contactName }} {{ detail.contactPhone }}，{{ detail.address }}</view>

        <view v-if="detail.canCancel" class="btn btn-ghost" @click="cancel">取消订单</view>
        <view class="sheet-cancel" @click="showDetail = false">关闭</view>
      </view>
    </view>

    <view v-if="showFeedback" class="sheet-mask" @click.self="showFeedback = false">
      <view class="sheet">
        <view class="sheet-title">{{ feedbackMeal?.mealLabel || '' }} {{ cleanDishName(feedbackMeal?.dishName) }}</view>
        <view class="sheet-tip">反馈用于周复盘，运营会据此调整后续计划</view>

        <view class="ratio-row">
          <view
            v-for="r in ratios"
            :key="r.value"
            class="ratio"
            :class="{ on: feedbackRatio === r.value }"
            @click="feedbackRatio = r.value"
          >{{ r.label }}</view>
        </view>

        <view class="row between satiety-row">
          <text class="muted">饱腹感</text>
          <view class="stars">
            <text
              v-for="n in 5"
              :key="n"
              class="star"
              :class="{ on: n <= feedbackSatiety }"
              @click="feedbackSatiety = n"
            >★</text>
          </view>
        </view>

        <input v-model="feedbackContent" class="field" placeholder="口味、份量或其他备注（可选）" />
        <view class="btn" :class="{ disabled: submitting }" @click="submitFeedback">
          {{ submitting ? '提交中…' : '提交反馈' }}
        </view>
        <view class="sheet-cancel" @click="showFeedback = false">取消</view>
      </view>
    </view>
  </view>
</template>


<style scoped>
.head {
  padding: 24px 0 14px;
}

.tip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 26px 16px;
}

.retry {
  height: 40px;
  width: 100%;
}

.order {
  margin-bottom: 10px;
}

.order-date {
  font-size: 16px;
  font-weight: 700;
}

.order-meta {
  margin-top: 8px;
}

.order-amount {
  font-size: 16px;
  font-weight: 700;
}

.status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
}

.status.ok {
  background: #F2F7EC;
  color: #032C0A;
}

.status.wait {
  background: #fbf0e2;
  color: #875A1F;
}

.status.off {
  background: #EEF3EE;
  color: #556A5E;
}

.sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(20, 35, 28, 0.45);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 20;
}

.sheet {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding: 22px 20px 28px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 86vh;
  overflow-y: auto;
}

.sheet-title {
  font-size: 17px;
  font-weight: 700;
}

.item {
  padding: 6px 0;
}

.meal-badge {
  min-width: 32px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 8px;
  background: #eef3ee;
  color: #032C0A;
  font-size: 12px;
  margin-right: 8px;
}

.item-name {
  font-size: 14px;
  font-weight: 600;
}

.amount-line {
  font-size: 13px;
  padding: 3px 0;
}

.summary {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-title {
  font-size: 14px;
  font-weight: 600;
}

.bar {
  height: 8px;
  border-radius: 4px;
  background: #eef3ee;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #032C0A;
}

.feedback-link {
  font-size: 12px;
  color: #032C0A;
  border: 1px solid #E2E6DC;
  border-radius: 999px;
  padding: 2px 10px;
}

.ratio-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ratio {
  flex: 1;
  min-width: 72px;
  text-align: center;
  padding: 10px 6px;
  border-radius: 12px;
  background: #EEF3EE;
  font-size: 13px;
  color: #556A5E;
}

.ratio.on {
  background: #032C0A;
  color: #fff;
}

.satiety-row {
  margin: 4px 0;
}

.stars {
  display: flex;
  gap: 6px;
}

.star {
  font-size: 22px;
  color: #E2E6DC;
}

.star.on {
  color: #875A1F;
}

.sheet-cancel {
  text-align: center;
  font-size: 13px;
  color: #556A5E;
  padding: 8px;
}
</style>
