<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TabBar from '@/components/TabBar.vue'
import { api } from '@/utils/api'
import { mealLabel, money, session } from '@/store/session'
import { safeTop } from '@/utils/safe-area'
const topInset = safeTop(16, false)

const loading = ref(true)
const plan = ref<any>(null)
const errorText = ref('')
const expanded = ref<string>('')

const showSwap = ref(false)
const swapContext = ref<{ serviceDate: string; mealType: string; dishName: string } | null>(null)

function cleanName(name: string) {
  return (name || '').replace(/-?P\d+$/i, '').replace(/P\d+$/i, '')
}
const options = ref<any[]>([])
const preview = ref<any>(null)
const previewing = ref(false)
const applying = ref(false)

const weekPrice = computed(() => money(plan.value?.weekPrice || 0))

/** 原始目标与计划目标相差 1g 以上才算触界（取整误差不算） */
const proteinClamped = computed(() => {
  const raw = Number(plan.value?.rawTargetProteinG || 0)
  const target = Number(plan.value?.dailyProteinG || 0)
  return raw > 0 && Math.abs(raw - target) >= 1
})

onMounted(load)

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    plan.value = await loadWeek(0)
  } catch (e: any) {
    // 本周没有计划时，自动尝试下周
    try {
      plan.value = await loadWeek(7)
    } catch (inner: any) {
      errorText.value = inner.message || '加载失败'
    }
  } finally {
    if (!expanded.value && plan.value?.days?.length) {
      expanded.value = plan.value.days[0].serviceDate
    }
    loading.value = false
  }
}

function loadWeek(offsetDays: number) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  return api.get<any>(`/app/plan/week?date=${iso}`)
}

function toggle(date: string) {
  expanded.value = expanded.value === date ? '' : date
}

async function openSwap(day: any, meal: any) {
  swapContext.value = { serviceDate: day.serviceDate, mealType: meal.mealType, dishName: meal.dishName }
  options.value = []
  preview.value = null
  showSwap.value = true
  try {
    options.value = await api.get(
      `/app/plan/swap/options?serviceDate=${day.serviceDate}&mealType=${meal.mealType}`
    )
  } catch (e: any) {
    options.value = []
  }
}

async function choose(option: any) {
  if (!swapContext.value) return
  previewing.value = true
  preview.value = null
  try {
    preview.value = await api.post('/app/plan/swap/preview', {
      serviceDate: swapContext.value.serviceDate,
      mealType: swapContext.value.mealType,
      dishVersionId: option.dishVersionId
    })
  } catch (e: any) {
    uni.showModal({ title: '无法替换', content: e.message || '请稍后再试', showCancel: false })
    showSwap.value = false
  } finally {
    previewing.value = false
  }
}

async function confirmSwap() {
  if (!swapContext.value || !preview.value) return
  applying.value = true
  try {
    await api.post('/app/plan/swap', {
      serviceDate: swapContext.value.serviceDate,
      mealType: swapContext.value.mealType,
      dishVersionId: preview.value.toDishVersionId
    })
    showSwap.value = false
    await load()
    uni.showToast({ title: '已替换', icon: 'success' })
  } catch (e: any) {
    uni.showModal({ title: '替换失败', content: e.message || '请稍后再试', showCancel: false })
  } finally {
    applying.value = false
  }
}

function closeSwap() {
  showSwap.value = false
  preview.value = null
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
    <view class="head">
      <view>
        <view class="brand">P L A N</view>
        <view class="title">我的计划</view>
      </view>
      <text class="muted">¥{{ weekPrice }} / 周</text>
    </view>

    <view v-if="loading" class="card tip"><text class="muted">加载中…</text></view>
    <view v-else-if="errorText" class="card tip">
      <text class="muted">{{ errorText }}</text>
      <view class="btn btn-ghost retry" @click="load">重新加载</view>
    </view>

    <template v-else-if="plan">
      <view class="card meta">
        <view class="row between">
          <text class="muted">计划蛋白 {{ plan.dailyProteinG }}g / 天</text>
        </view>
        <view class="muted" v-if="proteinClamped">
          按 {{ plan.dailyProteinG }}g 蛋白档位配餐
        </view>
        <view v-if="plan.needsReview" class="review">
          <text class="tag tag-warn">待复核</text>
          <text class="review-text">{{ plan.reviewNotes?.[0] }}</text>
        </view>
      </view>

      <view v-for="day in plan.days" :key="day.serviceDate" class="card day">
        <view class="row between day-head" @click="toggle(day.serviceDate)">
          <view class="row">
            <view class="day-info">
              <view class="row">
                <text class="day-date">{{ day.serviceDate.slice(5) }}</text>
                <text class="muted">周{{ ['一', '二', '三', '四', '五', '六', '日'][day.dayOfWeek - 1] }}</text>
              </view>
              <text class="day-sub">{{ day.totalProteinG }}g 蛋白 · {{ day.totalWeightG }}g · {{ day.totalKcal }} kcal</text>
            </view>
          </view>
          <view class="row">
            <text class="day-price">¥{{ money(day.dailyPrice) }}</text>
            <text class="chev">{{ expanded === day.serviceDate ? '⌃' : '⌄' }}</text>
          </view>
        </view>

        <view v-if="expanded === day.serviceDate" class="day-body">
          <view class="divider" />
          <view v-for="meal in sortedMeals(day)" :key="meal.mealType" class="meal">
            <view class="row between">
              <view class="row">
                <text class="meal-badge">{{ meal.mealLabel || mealLabel(meal.mealType) }}</text>
                <text class="meal-name">{{ cleanName(meal.dishName) }}</text>
              </view>
              <text class="muted">¥{{ money(meal.price) }}</text>
            </view>
            <view class="row between meal-sub">
              <text class="muted">{{ meal.weightG }}g · {{ meal.proteinG }}g 蛋白 · {{ meal.kcal }} kcal</text>
              <text class="swap" @click="openSwap(day, meal)">替换</text>
            </view>
          </view>
          <view class="divider" />
          <view class="row between">
            <text class="muted">基础 {{ money(day.basePrice) }} + 加量 {{ money(day.extraFee) }}</text>
            <text class="muted" v-if="Number(day.swapDifference)">
              替换补差 {{ money(day.swapDifference) }}
            </text>
          </view>
        </view>
      </view>
    </template>

    <TabBar current="plan" />

    <view v-if="showSwap" class="sheet-mask" @click.self="closeSwap">
      <view class="sheet">
        <view class="sheet-title">替换 {{ swapContext?.dishName }}</view>
        <view class="sheet-tip">只列出已审核的替换选项，补差按变动成本计算</view>

        <template v-if="!preview">
          <view v-if="!options.length" class="muted empty-opt">暂无可替换选项</view>
          <view v-for="opt in options" :key="opt.dishVersionId" class="option" @click="choose(opt)">
            <text class="option-name">{{ cleanName(opt.dishName) }}</text>
            <text class="muted">{{ opt.standardWeightG }}g · {{ opt.standardProteinG }}g 蛋白</text>
          </view>
          <view class="muted" v-if="previewing">试算中…</view>
        </template>

        <template v-else>
          <view class="preview-row">
            <text class="muted">替换为</text>
            <text class="option-name">{{ preview.toDishName }}</text>
          </view>
          <view class="preview-row">
            <text class="muted">份量</text>
            <text>{{ preview.before.weightG }}g → {{ preview.after.weightG }}g</text>
          </view>
          <view class="preview-row">
            <text class="muted">蛋白</text>
            <text>{{ preview.before.proteinG }}g → {{ preview.after.proteinG }}g</text>
          </view>
          <view class="preview-row">
            <text class="muted">替换补差</text>
            <text :class="{ up: Number(preview.priceDelta) > 0 }">
              {{ Number(preview.priceDelta) > 0 ? '+' : '' }}{{ money(preview.priceDelta) }} 元
            </text>
          </view>
          <view class="preview-row">
            <text class="muted">日价</text>
            <text>{{ money(preview.before.dailyPrice) }} → {{ money(preview.after.dailyPrice) }} 元</text>
          </view>
          <view class="btn" :class="{ disabled: applying }" @click="confirmSwap">
            {{ applying ? '提交中…' : '确认替换' }}
          </view>
        </template>

        <view class="sheet-cancel" @click="closeSwap">取消</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 24px 0 14px;
}

.tip {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  padding: 26px 16px;
}

.retry {
  height: 40px;
  width: 100%;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.review {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #FBF0E2;
  border-radius: 12px;
  padding: 10px;
}

.review-text {
  font-size: 13px;
  color: #875A1F;
}

.day {
  margin-bottom: 10px;
  padding: 14px 16px;
}

.day-head {
  cursor: pointer;
}

.day-date {
  font-size: 16px;
  font-weight: 700;
  margin-right: 6px;
}

.day-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.day-sub {
  font-size: 13px;
  color: #556A5E;
}

.day-price {
  font-size: 15px;
  font-weight: 700;
}

.chev {
  margin-left: 6px;
  color: #556A5E;
}

.meal {
  padding: 8px 0;
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

.meal-name {
  font-size: 14px;
  font-weight: 600;
}

.meal-sub {
  margin-top: 4px;
}

.swap {
  font-size: 12px;
  color: #032C0A;
  border: 1px solid #E2E6DC;
  border-radius: 999px;
  padding: 2px 10px;
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
  gap: 10px;
}

.sheet-title {
  font-size: 17px;
  font-weight: 700;
}

.sheet-tip {
  font-size: 13px;
  color: #556A5E;
}

.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 12px;
  background: #EEF3EE;
}

.option-name {
  font-size: 14px;
  font-weight: 600;
}

.empty-opt {
  padding: 14px 0;
}

.preview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding: 6px 0;
}

.up {
  color: #875A1F;
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
