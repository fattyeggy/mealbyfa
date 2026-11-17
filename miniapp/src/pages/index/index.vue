<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import TabBar from '@/components/TabBar.vue'
import { api } from '@/utils/api'
import { isOnboarded, mealLabel, money, refreshProfile, refreshWallet, session } from '@/store/session'
import { safeTop } from '@/utils/safe-area'
const topInset = safeTop(12, true)

type Mode = 'guest' | 'need-onboarding' | 'ready' | 'no-plan'

const mode = ref<Mode>('guest')
const loading = ref(true)
const errorText = ref('')
const weekOffset = ref(0)
const selectedIndex = ref(0)
const week = ref<any>(null)
const plan = ref<any>(null)
const publishing = ref(false)
const showOrderSheet = ref(false)
const delivery = ref({ contactName: '', contactPhone: '', address: '' })
const myOrders = ref<any[]>([])
const showWeekly = ref(false)
const weeklyQuote = ref<any>(null)
const weeklyDates = ref<string[]>([])
const weeklyCouponId = ref<number | null>(null)
const weeklyCoupons = ref<any[]>([])
const weeklyBusy = ref(false)
const homeGap = ref<any>(null)
const weekOrderedDays = computed(() => {
  const dates = new Set((days.value || []).map((d: any) => d.serviceDate))
  const counted = new Set(
    myOrders.value
      .filter((o: any) => dates.has(o.serviceDate) && !['CANCELLED', 'REFUNDED'].includes(o.status))
      .map((o: any) => o.serviceDate)
  )
  return counted.size
})
const submitting = ref(false)
const coupons = ref<any[]>([])
const selectedCouponId = ref<number | null>(null)
const quote = ref<any>(null)
const quoteLoading = ref(false)

const days = computed<any[]>(() => {
  if (mode.value === 'ready' || mode.value === 'no-plan') return plan.value?.days || []
  return week.value?.days || []
})

const currentDay = computed<any>(() => days.value[selectedIndex.value] || null)

const weekTitle = computed(() => {
  const source = plan.value || week.value
  if (!source?.startDate && !source?.weekStart) return ''
  const start = source.startDate || source.weekStart
  const end = source.endDate || source.weekEnd
  return `${short(start)}—${short(end)}`
})

const isPublished = computed(() => mode.value === 'ready' || (week.value?.published ?? false))

const hasNextWeek = ref(true)

const dateAnchors = computed(() => {
  const today = new Date()
  const base = new Date(today.getTime() + weekOffset.value * 7 * 86400000)
  return toIso(base)
})

onMounted(async () => {
  const saved = uni.getStorageSync('sancan_delivery') || {}
  const p: any = session.profile
  delivery.value = {
    contactName: (p && p.contactName) || saved.contactName || '',
    contactPhone: (p && p.contactPhone) || saved.contactPhone || '',
    address: (p && p.address) || saved.address || ''
  }
  await load()
})

watch(weekOffset, () => {
  selectedIndex.value = 0
  load()
})

watch(selectedIndex, async () => {
  await loadHomeGap()
})

async function loadHomeGap() {
  if (mode.value !== 'ready' || !currentDay.value?.serviceDate) {
    homeGap.value = null
    return
  }
  try {
    homeGap.value = await api.get(`/app/onboarding/gap?date=${currentDay.value.serviceDate}`)
  } catch (e) {
    homeGap.value = null
  }
}

// 会话（登录 / 建档）就绪后重新加载，避免首屏早于 bootstrap 完成
watch(
  () => [session.ready, session.profile?.userId, session.profile?.onboardingCompleted],
  () => load()
)

async function load() {
  // 未建档不允许进入首页
  if (session.ready && !session.profile?.onboardingCompleted) {
    uni.reLaunch({ url: '/pages/onboarding/index' })
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    if (!isOnboarded()) {
      mode.value = session.profile ? 'need-onboarding' : 'guest'
      week.value = await api.get(`/app/menu/week?date=${dateAnchors.value}`)
      plan.value = null
      // 本周还没发布、但下周已发布时，直接展示下周，避免首屏空白
      if (!week.value?.published && weekOffset.value === 0 && hasNextWeek.value) {
        weekOffset.value = 1
        return
      }
    } else {
      try {
        plan.value = await api.get(`/app/plan/week?date=${dateAnchors.value}`)
        mode.value = 'ready'
        try {
          const page: any = await api.get('/app/orders?pageNo=1&pageSize=30')
          myOrders.value = page.list || []
        } catch (e) {
          myOrders.value = []
        }
      } catch (e: any) {
        if (String(e.message || '').includes('尚未生成')) {
          mode.value = 'no-plan'
          plan.value = null
          week.value = await api.get(`/app/menu/week?date=${dateAnchors.value}`)
          if (!week.value?.published && weekOffset.value === 0) {
            weekOffset.value = 1
            return
          }
        } else {
          throw e
        }
      }
    }
    selectedIndex.value = Math.min(selectedIndex.value, Math.max(days.value.length - 1, 0))
    await loadHomeGap()
  } catch (e: any) {
    errorText.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function generatePlan() {
  loading.value = true
  try {
    await api.post('/app/plan/generate', { weekDate: dateAnchors.value })
    await refreshProfile()
    await load()
  } catch (e: any) {
    uni.showToast({ title: e.message || '生成失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function goOnboarding() {
  uni.navigateTo({ url: '/pages/onboarding/index' })
}

async function openOrderSheet() {
  showOrderSheet.value = true
  selectedCouponId.value = null
  await refreshQuote()
  try {
    coupons.value = await api.get('/app/coupons?status=UNUSED')
  } catch (e) {
    coupons.value = []
  }
}

async function refreshQuote() {
  if (!currentDay.value?.serviceDate) return
  quoteLoading.value = true
  try {
    quote.value = await api.post('/app/orders/quote', {
      serviceDate: currentDay.value.serviceDate,
      couponId: selectedCouponId.value
    })
  } catch (e: any) {
    uni.showToast({ title: e.message || '试算失败', icon: 'none' })
  } finally {
    quoteLoading.value = false
  }
}

async function pickCoupon(id: number | null) {
  selectedCouponId.value = id
  await refreshQuote()
}

function cleanName(name: string) {
  return (name || '').replace(/-?P\d+$/i, '').replace(/P\d+$/i, '')
}

function weekStartOf() {
  const start = plan.value?.weekStart || week.value?.startDate
  if (start) return start
  const d = new Date()
  const day = (d.getDay() + 6) % 7
  d.setDate(d.getDate() - day)
  return toIso(d)
}

async function openWeekly() {
  showWeekly.value = true
  weeklyCouponId.value = null
  try {
    weeklyCoupons.value = await api.get('/app/coupons?status=UNUSED')
  } catch (e) { weeklyCoupons.value = [] }
  await refreshWeekly()
}

async function refreshWeekly() {
  const body: any = { weekStart: weekStartOf() }
  if (weeklyDates.value.length) body.dates = weeklyDates.value
  if (weeklyCouponId.value) body.couponId = weeklyCouponId.value
  try {
    weeklyQuote.value = await api.post('/app/orders/weekly/quote', body)
    weeklyDates.value = (weeklyQuote.value.days || []).map((d: any) => d.serviceDate)
  } catch (e: any) {
    uni.showToast({ title: e.message || '试算失败', icon: 'none' })
  }
}

async function toggleWeekDay(date: string) {
  const i = weeklyDates.value.indexOf(date)
  if (i >= 0) weeklyDates.value.splice(i, 1)
  else weeklyDates.value.push(date)
  await refreshWeekly()
}

async function pickWeeklyCoupon(id: number | null) {
  weeklyCouponId.value = id
  await refreshWeekly()
}

async function submitWeekly() {
  if (!weeklyDates.value.length) {
    uni.showToast({ title: '请至少选择一天', icon: 'none' })
    return
  }
  if (!delivery.value.contactName || !delivery.value.contactPhone || !delivery.value.address) {
    uni.showToast({ title: '请填写配送信息', icon: 'none' })
    return
  }
  weeklyBusy.value = true
  try {
    await api.post('/app/orders/weekly', {
      weekStart: weekStartOf(),
      dates: weeklyDates.value,
      couponId: weeklyCouponId.value,
      ...delivery.value
    })
    uni.setStorageSync('sancan_delivery', delivery.value)
    showWeekly.value = false
    uni.showToast({ title: '已预订，待锁单', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/orders/index' }), 600)
  } catch (e: any) {
    uni.showModal({ title: '预订失败', content: e.message || '请稍后再试', showCancel: false })
  } finally {
    weeklyBusy.value = false
  }
}

async function submitOrder() {
  if (!delivery.value.contactName || !delivery.value.contactPhone || !delivery.value.address) {
    uni.showToast({ title: '请填写完整的配送信息', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await api.post('/app/orders', {
      serviceDate: currentDay.value.serviceDate,
      couponId: selectedCouponId.value,
      ...delivery.value
    })
    uni.setStorageSync('sancan_delivery', delivery.value)
    showOrderSheet.value = false
    await refreshWallet()
    uni.showToast({ title: '已提交，待锁单', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/orders/index' }), 600)
  } catch (e: any) {
    const msg = e.message || '请稍后再试'
    if (msg.includes('券')) {
      await refreshQuote()
      uni.showModal({ title: '金额已变化', content: msg + '，请确认新的应付金额', showCancel: false })
    } else {
      uni.showModal({ title: '暂时无法下单', content: msg, showCancel: false })
    }
  } finally {
    submitting.value = false
  }
}

function short(iso: string) {
  if (!iso) return ''
  const [, m, d] = iso.split('-')
  return `${Number(m)}.${Number(d)}`
}

function toIso(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function dayNumber(iso: string) {
  return Number(iso?.split('-')[2] || 0)
}

function weekdayText(index: number) {
  return ['一', '二', '三', '四', '五', '六', '日'][index] || ''
}

const mealOrder = ['BREAKFAST', 'LUNCH', 'DINNER']
function sortedMeals(day: any) {
  return [...(day?.meals || [])].sort(
    (a: any, b: any) => mealOrder.indexOf(a.mealType) - mealOrder.indexOf(b.mealType)
  )
}
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <view class="brand-row">
        <image class="brand-logo" src="/static/brand/meal-by-fa-logo.png" mode="aspectFit" />
        <view>
          <view class="title">发发的餐</view>
          <view class="brand-en">meal by fa</view>
        </view>
      </view>
      <view class="login-pill">
        <text>{{ session.nickname || '未登录' }}</text>
      </view>
    </view>

    <view v-if="errorText" class="card error-card">
      <text class="muted">{{ errorText }}</text>
      <view class="btn btn-ghost retry" @click="load">重新加载</view>
    </view>

    <template v-else>
      <view class="hero">
        <view class="hero-week">{{ weekTitle }} · {{ weekOffset === 0 ? '本周' : '下周' }}菜单</view>
        <view class="hero-theme">{{ plan?.themeName || week?.themeName || '等待发布' }}</view>
        <view class="hero-desc">
          {{ mode === 'ready' ? '已按你的计划生成个人份量' : '照烧、味噌、芝麻与荞麦的清爽组合' }}
        </view>
      </view>

      <view class="switcher">
        <view class="pill" :class="{ 'pill-active': weekOffset === 0 }" @click="weekOffset = 0">本周</view>
        <view class="pill" :class="{ 'pill-active': weekOffset === 1 }" @click="weekOffset = 1">下周</view>
        <view class="grow" />
        <text v-if="mode === 'ready' && days.length" class="muted progress-text">
          已订 {{ weekOrderedDays }}/{{ days.length }} 天
        </text>
        <text class="muted">{{ isPublished ? '已发布' : '未发布' }}</text>
      </view>

      <scroll-view class="dates" scroll-x :show-scrollbar="false">
        <view
          v-for="(day, index) in days"
          :key="day.serviceDate"
          class="date-cell"
          :class="{ active: index === selectedIndex }"
          @click="selectedIndex = index"
        >
          <text class="date-week">{{ weekdayText(index) }}</text>
          <text class="date-num">{{ dayNumber(day.serviceDate) }}</text>
        </view>
      </scroll-view>

      <view v-if="!days.length" class="card empty">
        <text class="muted">{{ weekOffset === 0 ? '本周菜单尚未发布' : '下周菜单尚未发布' }}</text>
        <text v-if="week?.expectedPublishTime" class="muted">
          预计 {{ week.expectedPublishTime.replace('T', ' ').slice(0, 16) }} 发布
        </text>
      </view>

      <template v-else-if="currentDay">
        <view v-if="mode === 'ready' && plan?.needsReview" class="card review">
          <text class="tag tag-warn">待人工复核</text>
          <text class="review-text">{{ plan.reviewNotes?.[0] }}</text>
        </view>

        <view class="meal-list">
          <view v-for="meal in sortedMeals(currentDay)" :key="meal.mealType" class="meal card">
            <view class="meal-head">
              <text class="meal-badge">{{ meal.mealLabel || mealLabel(meal.mealType) }}</text>
              <text class="meal-name">{{ cleanName(meal.dishName) }}</text>
            </view>
            <view class="meal-meta">
              <text>{{ meal.weightG }}g</text>
              <text>·</text>
              <text>{{ meal.proteinG }}g 蛋白</text>
              <text>·</text>
              <text>{{ meal.kcal }} kcal</text>
            </view>
            <view v-if="meal.allergens?.length" class="meal-allergen">
              过敏原：{{ meal.allergens.join('、') }}
            </view>
            <view class="meal-foot" v-if="mode === 'ready'">
              <text class="meal-price">¥{{ money(meal.price) }}</text>
              <text class="muted">{{ meal.mealLabel }}</text>
            </view>
          </view>
        </view>

        <view v-if="homeGap && homeGap.lines?.length" class="card gap-card">
          <view class="gap-title">今日营养进度</view>
          <view v-for="line in homeGap.lines" :key="line.nutrient" class="gap-row">
            <text>{{ line.label }}</text>
            <text :class="{ over: line.status === 'OVER' }">
              {{ line.status === 'SHORT' ? '还差 ' + line.gapG + 'g'
                 : line.status === 'OVER' ? '超出 ' + line.excessG + 'g' : '已达标' }}
            </text>
          </view>
        </view>

        <view class="cta-space" />
        <view class="cta-bar">
          <view v-if="mode === 'ready'" class="price-card">
            <view class="row between">
              <view>
                <view class="price-label">日价</view>
                <view class="price-breakdown">
                  基础 {{ money(currentDay.basePrice) }} · 加量 {{ money(currentDay.extraFee) }}
                  <text v-if="Number(currentDay.swapDifference)">
                    · 补差 {{ money(currentDay.swapDifference) }}
                  </text>
                </view>
              </view>
              <text class="price-value">¥{{ money(currentDay.dailyPrice) }}</text>
            </view>
            <view class="row between price-foot">
              <text class="muted">账户余额 ¥{{ money(session.wallet.balance) }}</text>

            </view>
          </view>
          <view v-if="mode === 'ready'" class="btn" @click="openWeekly">
            一键预订本周 · 已订 {{ weekOrderedDays }}/{{ days.length }} 天
          </view>
          <view v-else-if="mode === 'no-plan'" class="btn" @click="generatePlan">生成本周个人计划</view>
          <view v-else class="btn" @click="goOnboarding">获取我的三餐方案</view>
        </view>
      </template>
    </template>

    <TabBar current="home" />

    <view v-if="showWeekly" class="sheet-mask" @click.self="showWeekly = false">
      <view class="sheet">
        <view class="sheet-title">一键预订本周</view>
        <view class="sheet-tip">{{ weekStartOf() }} 起 · 可取消其中几天</view>
        <view class="week-days">
          <view v-for="d in (weeklyQuote?.days || [])" :key="d.serviceDate" class="week-day"
                :class="{ on: weeklyDates.includes(d.serviceDate) }" @click="toggleWeekDay(d.serviceDate)">
            <text>{{ short(d.serviceDate) }}</text>
            <text class="muted">¥{{ money(d.originalAmount) }}</text>
          </view>
        </view>
        <view v-if="weeklyQuote" class="amount">
          <view class="row between"><text class="muted">合计</text><text>¥{{ money(weeklyQuote.originalTotal) }}</text></view>
          <view class="row between"><text class="muted">优惠</text><text class="discount">-¥{{ money(weeklyQuote.couponDiscount) }}</text></view>
          <view class="row between amount-total"><text>应付</text><text class="amount-value">¥{{ money(weeklyQuote.payableTotal) }}</text></view>
        </view>
        <view v-if="weeklyCoupons.length" class="coupon-list">
          <view class="coupon" :class="{ on: weeklyCouponId === null }" @click="pickWeeklyCoupon(null)">不使用优惠券</view>
          <view v-for="c in weeklyCoupons" :key="c.id" class="coupon" :class="{ on: weeklyCouponId === c.id }" @click="pickWeeklyCoupon(c.id)">
            <text>{{ c.name }}</text><text class="coupon-rule">满 {{ money(c.thresholdAmount) }} 减 {{ money(c.discountAmount) }}</text>
          </view>
        </view>
        <input v-model="delivery.contactName" class="field" placeholder="联系人" />
        <input v-model="delivery.contactPhone" class="field" placeholder="联系电话" type="number" />
        <input v-model="delivery.address" class="field" placeholder="配送地址" />
        <view class="btn" :class="{ disabled: weeklyBusy }" @click="submitWeekly">
          {{ weeklyBusy ? '提交中…' : '确认预订 · ¥' + money(weeklyQuote?.payableTotal) }}
        </view>
        <view class="sheet-cancel" @click="showWeekly = false">取消</view>
      </view>
    </view>

    <view v-if="showOrderSheet" class="sheet-mask" @click.self="showOrderSheet = false">
      <view class="sheet">
        <view class="sheet-title">确认订单</view>
        <view class="sheet-tip">{{ currentDay?.serviceDate }} · {{ weekOffset === 0 ? '本周' : '下周' }}</view>

        <view class="amount">
          <view class="row between">
            <text class="muted">餐费</text>
            <text>¥{{ money(quote?.originalAmount ?? currentDay?.dailyPrice) }}</text>
          </view>
          <view class="row between">
            <text class="muted">优惠券</text>
            <text class="discount">-¥{{ money(quote?.couponDiscount ?? 0) }}</text>
          </view>
          <view class="row between amount-total">
            <text>应付</text>
            <text class="amount-value">¥{{ money(quote?.payable ?? currentDay?.dailyPrice) }}</text>
          </view>
          <view v-if="quote && !quote.couponValid" class="coupon-warn">{{ quote.couponMessage }}</view>
        </view>

        <view v-if="coupons.length" class="coupon-list">
          <view class="coupon" :class="{ on: selectedCouponId === null }" @click="pickCoupon(null)">
            不使用优惠券
          </view>
          <view
            v-for="c in coupons"
            :key="c.id"
            class="coupon"
            :class="{ on: selectedCouponId === c.id }"
            @click="pickCoupon(c.id)"
          >
            <text class="coupon-name">{{ c.name }}</text>
            <text class="coupon-rule">
              {{ c.type === 'CASH' ? '满 ' + money(c.thresholdAmount) + ' 减 ' + money(c.discountAmount)
                                   : '立减 ' + money(c.discountAmount) }}
            </text>
          </view>
        </view>

        <input v-model="delivery.contactName" class="field" placeholder="联系人" />
        <input v-model="delivery.contactPhone" class="field" placeholder="联系电话" type="number" />
        <input v-model="delivery.address" class="field" placeholder="配送地址" />
        <view class="btn" :class="{ disabled: submitting }" @click="submitOrder">
          {{ submitting ? '提交中…' : '确认支付 ¥' + money(quote?.payable ?? currentDay?.dailyPrice) }}
        </view>
        <view class="sheet-cancel" @click="showOrderSheet = false">取消</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 64px;
  height: 40px;
}

.brand-en {
  font-size: 12px;
  letter-spacing: 2px;
  color: #556A5E;
  text-transform: lowercase;
}

.amount {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: #F4F1E9;
  border-radius: 12px;
}

.amount-total {
  border-top: 1px dashed #E2E6DC;
  padding-top: 8px;
  font-weight: 600;
}

.amount-value {
  color: #032C0A;
  font-size: 20px;
}

.discount {
  color: #032C0A;
}

.week-days { display: flex; flex-wrap: wrap; gap: 8px; }
.week-day { padding: 8px 10px; border-radius: 12px; border: 1px solid #e2e6dc; display: flex; flex-direction: column; gap: 2px; font-size: 12px; min-width: 64px; }
.week-day.on { border-color: #032C0A; background: #f2f7ec; }

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
}

.coupon {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #e2e6dc;
  font-size: 13px;
}

.coupon.on {
  border-color: #032C0A;
  background: #f2f7ec;
}

.coupon-rule {
  color: #556A5E;
  font-size: 13px;
}

.coupon-warn {
  color: #b04a3a;
  font-size: 13px;
}

.login-pill {
  background: #032C0A;
  color: #F4F1E9;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 12px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-card {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.retry {
  height: 40px;
}

.gap-card { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.gap-title { font-size: 13px; font-weight: 600; }
.gap-row { display: flex; justify-content: space-between; font-size: 13px; }
.gap-row .over { color: #B04A3A; }

.progress-text {
  margin-right: 10px;
}

.switcher {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 12px;
}

.dates {
  width: 100%;
  white-space: nowrap;
  margin-bottom: 14px;
}

.date-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 58px;
  margin-right: 8px;
  border-radius: 12px;
  background: #fff;
  color: #556A5E;
}

.date-cell.active {
  background: #032C0A;
  color: #fff;
}

.date-week {
  font-size: 12px;
  opacity: 0.8;
}

.date-num {
  font-size: 17px;
  font-weight: 700;
  margin-top: 2px;
}

.empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 28px 16px;
}

.review {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  background: #FBF0E2;
}

.review-text {
  font-size: 13px;
  color: #875A1F;
  line-height: 1.5;
}

.meal-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meal-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meal-badge {
  min-width: 34px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  border-radius: 8px;
  background: #eef3ee;
  color: #032C0A;
  font-size: 12px;
  font-weight: 600;
}

.meal-name {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}

.meal-meta {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  font-size: 13px;
  color: #556A5E;
}

.meal-allergen {
  margin-top: 6px;
  font-size: 13px;
  color: #875A1F;
}

.meal-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.meal-price {
  font-size: 16px;
  font-weight: 700;
  color: #1c3b2e;
}



.price-label {
  font-size: 13px;
  color: #556A5E;
}

.price-value {
  font-size: 24px;
  font-weight: 700;
}



.cta-space {
  height: 210px;
}

.cta-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 68px;
  width: 100%;
  max-width: 520px;
  padding: 10px 20px 12px;
  background: linear-gradient(180deg, rgba(244, 241, 233, 0) 0%, #f4f1e9 26%);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.price-card {
  background: #fff;
  border-radius: 20px;
  padding: 12px 16px;
  box-shadow: 0 6px 20px rgba(28, 59, 46, 0.06);
}

.price-label {
  font-size: 13px;
  color: #556A5E;
}

.price-breakdown {
  font-size: 13px;
  color: #556A5E;
  margin-top: 3px;
}

.price-value {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.price-foot {
  margin-top: 6px;
  font-size: 13px;
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
  gap: 12px;
}

.sheet-title {
  font-size: 17px;
  font-weight: 700;
}

.sheet-tip {
  font-size: 13px;
  color: #556A5E;
}

.field {
  height: 46px;
  border-radius: 12px;
  background: #EEF3EE;
  padding: 0 14px;
  font-size: 14px;
}

.sheet-cancel {
  text-align: center;
  font-size: 13px;
  color: #556A5E;
  padding: 6px;
}

.disabled {
  opacity: 0.6;
}
</style>
