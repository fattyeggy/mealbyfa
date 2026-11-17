<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '@/utils/api'
import { refreshProfile, refreshWallet, session } from '@/store/session'
import { safeTop } from '@/utils/safe-area'

const topInset = safeTop(12, true)
const phase = ref<'intro' | 'form' | 'blocked' | 'result'>('intro')
const step = ref(1)
const saving = ref(false)
const slides = ref<any[]>([])
const themeName = ref('')
const result = ref<any>(null)
const blocked = ref<any>(null)
const gap = ref<any>(null)
const phoneClaimed = ref(false)
const showPhoneOverlay = ref(false)

const goals = [
  { value: 'cut', label: '减脂', desc: '每周约减 0.4–0.8 kg，尽量保住肌肉' },
  { value: 'maintain', label: '维持', desc: '体重基本不变，吃得规律' },
  { value: 'bulk', label: '增肌', desc: '每周约增 0.1–0.25 kg，配合力量训练' }
]
const activities = [
  { value: 'sedentary', label: '久坐办公', desc: '每天大部分时间坐着' },
  { value: 'light', label: '经常走动', desc: '通勤、外勤、站立工作' },
  { value: 'active', label: '体力劳动', desc: '搬抬、长时间体力工作' }
]
const intensities = [
  { value: 'low', label: '低', desc: '散步、瑜伽' },
  { value: 'medium', label: '中', desc: '慢跑、常规力量' },
  { value: 'high', label: '高', desc: '间歇跑、大重量' }
]
const workoutTypes = [
  { value: 'strength', label: '力量' },
  { value: 'cardio', label: '有氧' },
  { value: 'mixed', label: '混合' }
]
const healthOptions = [
  { value: 'pregnant', label: '孕期' },
  { value: 'breastfeeding', label: '哺乳期' },
  { value: 'diabetes', label: '糖尿病' },
  { value: 'kidney_disease', label: '肾病' },
  { value: 'gout', label: '痛风' },
  { value: 'hypertension', label: '高血压' },
  { value: 'eating_disorder_history', label: '进食障碍史' },
  { value: 'none', label: '以上皆无' }
]
const allergyOptions = ['花生', '坚果', '虾蟹', '鱼', '蛋', '奶', '麸质', '大豆']
const restrictionOptions = ['清真', '素食']
const mealOptions = [
  { value: 'breakfast', label: '早餐', share: 0.25 },
  { value: 'lunch', label: '午餐', share: 0.40 },
  { value: 'dinner', label: '晚餐', share: 0.35 }
]
const blockedText: Record<string, string> = {
  under_18: '暂不支持未成年人',
  over_65: '65 岁以上需专业评估',
  pregnant: '孕期需专业评估',
  breastfeeding: '哺乳期需专业评估',
  kidney_disease: '肾病需遵医嘱，请人工咨询',
  diabetes: '糖尿病需专业评估',
  eating_disorder_history: '进食障碍史需专业评估',
  cut_bmi_too_low: 'BMI 低于 18.5，不建议减脂，请改选维持或增肌'
}

const form = reactive<any>({
  goal: 'cut', sex: 'male', age: 30, heightCm: 175, weightKg: 75, bodyFatPct: null,
  dailyActivity: 'sedentary', workoutsPerWeek: 0, workoutMinutes: 60,
  workoutType: 'strength', workoutIntensity: 'medium',
  healthFlags: [] as string[], allergies: [] as string[], dietRestrictions: [] as string[],
  orderedMeals: ['breakfast', 'lunch', 'dinner'] as string[],
  tastePrefs: { spice: 1, staple: 'rice' },
  privacyAccepted: false
})

const progress = computed(() => Math.round((step.value / 4) * 100))
const coverage = computed(() => {
  const sum = mealOptions.filter(m => form.orderedMeals.includes(m.value))
    .reduce((acc, m) => acc + m.share, 0)
  return Math.round(sum * 100)
})
const price = computed(() => {
  if (!result.value) return 0
  return Math.max(65, Math.round(66 + 1.9 * (result.value.proteinTier - 60) / 10))
})

function h5Tip() { uni.showToast({ title: '请在微信中打开', icon: 'none' }) }
function startOnboarding() { phase.value = 'form'; step.value = 1 }
function toggleHealth(value: string) {
  if (value === 'none') { form.healthFlags = ['none']; return }
  form.healthFlags = form.healthFlags.filter((v: string) => v !== 'none')
  const i = form.healthFlags.indexOf(value)
  if (i >= 0) form.healthFlags.splice(i, 1)
  else form.healthFlags.push(value)
}
function toggleInList(list: string[], value: string) {
  const i = list.indexOf(value)
  if (i >= 0) list.splice(i, 1)
  else list.push(value)
}
function validateStep(): string | null {
  if (step.value === 2) {
    if (!form.age || form.age < 18 || form.age > 65) return '年龄需在 18–65 岁'
    if (!form.heightCm || form.heightCm < 130 || form.heightCm > 220) return '身高需在 130–220cm'
    if (!form.weightKg || form.weightKg < 35 || form.weightKg > 200) return '体重需在 35–200kg'
    if (form.bodyFatPct && (form.bodyFatPct < 5 || form.bodyFatPct > 60)) return '体脂率需在 5–60%'
    if (form.workoutsPerWeek > 0 && (!form.workoutMinutes || form.workoutMinutes < 10 || form.workoutMinutes > 180)) {
      return '单次运动时长需在 10–180 分钟'
    }
    if (!form.privacyAccepted) return '请先同意隐私授权'
  }
  if (step.value === 3 && !form.healthFlags.length) return '请选择「以上皆无」或具体情况'
  if (step.value === 4 && !form.orderedMeals.length) return '请至少选择一个订购餐次'
  return null
}
function next() {
  const error = validateStep()
  if (error) { uni.showToast({ title: error, icon: 'none' }); return }
  if (step.value < 4) step.value++
  else submit()
}
function prev() {
  if (step.value > 1) step.value--
  else phase.value = 'intro'
}

async function submit() {
  saving.value = true
  try {
    const resp: any = await api.post('/app/onboarding', { ...form, birthDate: null, privacyAccepted: true })
    if (resp.status === 'blocked') { blocked.value = resp; phase.value = 'blocked'; return }
    result.value = resp.plan
    phase.value = 'result'
    await refreshProfile()
    try {
      await api.post('/app/plan/generate', { weekDate: todayIso() })
      gap.value = await api.get(`/app/onboarding/gap?date=${todayIso()}`)
    } catch (e) { gap.value = null }
  } catch (e: any) {
    uni.showModal({ title: '提交失败', content: e.message || '请稍后再试', showCancel: false })
  } finally { saving.value = false }
}
function finish() { refreshWallet(); uni.reLaunch({ url: '/pages/index/index' }) }

async function onGetPhone(e: any) {
  const code = e?.detail?.code
  if (!code) {
    uni.showToast({ title: '未获得手机号授权', icon: 'none' })
    return
  }
  try {
    await api.post('/app/auth/wechat-phone', { code })
    phoneClaimed.value = true
    await refreshProfile()
    showPhoneOverlay.value = false
    if (session.profile?.onboardingCompleted) {
      uni.showToast({ title: '已登录', icon: 'success' })
      setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 500)
    } else {
      phase.value = 'intro'
      uni.showToast({ title: '手机号已绑定', icon: 'success' })
    }
  } catch (err: any) {
    uni.showModal({
      title: '手机号授权失败',
      content: (err.message || '请稍后再试') + '\n可以先建档，稍后在“我的”里再绑定手机号。',
      confirmText: '先建档',
      cancelText: '重试',
      success: (res: any) => {
        if (res.confirm) {
          showPhoneOverlay.value = false
          phase.value = 'intro'
        }
      }
    })
  }
}

async function loadSlides() {
  try {
    const week: any = await api.get(`/app/menu/week?date=${todayIso()}`)
    themeName.value = week?.themeName || ''
    const meals: any[] = []
    for (const day of week?.days || []) for (const m of day.meals || []) meals.push(m)
    const seen = new Set<string>()
    slides.value = meals.filter(m => {
      if (!m.dishName || seen.has(m.dishName)) return false
      seen.add(m.dishName); return true
    }).slice(0, 6)
  } catch (e) { slides.value = [] }
}
function todayIso() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function gapText(line: any) {
  if (line.status === 'SHORT') return '还差 ' + line.gapG + 'g'
  if (line.status === 'OVER') return '超出 ' + line.excessG + 'g'
  return '已达标'
}
onMounted(async () => {
  await loadSlides()
  try {
    const status: any = await api.get('/app/auth/status')
    if (status.onboardingCompleted) {
      uni.reLaunch({ url: '/pages/index/index' })
      return
    }
    if (!status.phoneBound) {
      showPhoneOverlay.value = true
    }
  } catch (e) {
    if (session.profile?.onboardingCompleted) phase.value = 'form'
  }
})
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view v-if="showPhoneOverlay" class="phone-mask">
      <view class="phone-card">
        <view class="phone-title">微信手机号登录</view>
        <view class="phone-desc">用于识别你的档案；老用户直接进入，新用户继续建档。</view>
        <!-- #ifdef MP-WEIXIN -->
        <button class="phone-btn" open-type="getPhoneNumber" @getphonenumber="onGetPhone">微信手机号快捷登录</button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view class="phone-btn" @click="h5Tip">请在微信中打开</view>
        <!-- #endif -->
        <view class="phone-skip" @click="showPhoneOverlay = false; phase = 'intro'">先建档，稍后绑定</view>
      </view>
    </view>

    <view v-if="phase === 'intro'" class="deck">
      <view class="deck-brand">
        <image class="deck-logo" src="/static/brand/meal-by-fa-logo.png" mode="aspectFit" />
        <view><view class="deck-name">发发的餐</view><view class="deck-en">meal by fa</view></view>
      </view>
      <view class="deck-title">先看看这周吃什么</view>
      <view class="deck-sub">{{ themeName || '每周菜单' }} · 按你的目标配好克重与营养</view>
      <swiper class="deck-swiper" indicator-dots indicator-color="rgba(255,255,255,0.3)"
              indicator-active-color="#73C308" circular autoplay :interval="4200">
        <swiper-item v-for="(s, i) in slides" :key="i">
          <view class="slide">
            <image v-if="s.imageUrl" :src="s.imageUrl" class="slide-img" mode="aspectFill" />
            <view v-else class="slide-ph">
              <text class="ph-meal">{{ s.mealLabel }}</text>
              <text class="ph-name">{{ s.dishName }}</text>
            </view>
            <view class="slide-info">
              <text class="slide-meal">{{ s.mealLabel }}</text>
              <text class="slide-name">{{ s.dishName }}</text>
              <text class="slide-meta">{{ s.weightG }}g · {{ s.proteinG }}g 蛋白 · {{ s.kcal }} kcal</text>
            </view>
          </view>
        </swiper-item>
      </swiper>
      <view v-if="!slides.length" class="deck-empty">本周菜单准备中，建档后即可查看</view>
      <view class="deck-foot">
        <view class="deck-cta" @click="startOnboarding">开始定制我的三餐</view>
        <view class="deck-tip">约 2 分钟 · 建档完成后才能下单</view>
      </view>
    </view>

    <view v-else-if="phase === 'form'" class="form-wrap">
      <view class="nav"><text class="back" @click="prev">‹</text><text class="nav-title">第 {{ step }} / 4 步</text><view class="nav-space" /></view>
      <view class="progress"><view class="progress-bar" :style="{ width: progress + '%' }" /></view>

      <view v-if="step === 1">
        <view class="h1">你的目标是？</view>
        <view class="h2">目标决定热量与蛋白系数，之后可修改</view>
        <view class="goal-list">
          <view v-for="g in goals" :key="g.value" class="goal card" :class="{ on: form.goal === g.value }" @click="form.goal = g.value">
            <text class="goal-name">{{ g.label }}</text><text class="goal-desc">{{ g.desc }}</text>
          </view>
        </view>
      </view>

      <view v-if="step === 2">
        <view class="h1">身体数据与活动</view>
        <view class="privacy" @click="form.privacyAccepted = !form.privacyAccepted">
          <view class="radio" :class="{ on: form.privacyAccepted }" />
          <text>我同意将身体数据与健康信息用于配餐与营养计算（可随时删除）</text>
        </view>
        <view class="card form">
          <view class="field-row"><text class="label">性别</text>
            <view class="segmented">
              <view class="seg" :class="{ on: form.sex === 'male' }" @click="form.sex = 'male'">男</view>
              <view class="seg" :class="{ on: form.sex === 'female' }" @click="form.sex = 'female'">女</view>
            </view>
          </view>
          <view class="field-row"><text class="label">年龄</text><input v-model.number="form.age" class="input" type="number" /></view>
          <view class="field-row"><text class="label">身高（cm）</text><input v-model.number="form.heightCm" class="input" type="digit" /></view>
          <view class="field-row"><text class="label">体重（kg）</text><input v-model.number="form.weightKg" class="input" type="digit" /></view>
          <view class="field-row"><text class="label">体脂率（%，选填）</text><input v-model.number="form.bodyFatPct" class="input" type="digit" placeholder="如 20" /></view>
        </view>
        <view class="card form" style="margin-top: 12px">
          <view v-for="a in activities" :key="a.value" class="choice" :class="{ on: form.dailyActivity === a.value }" @click="form.dailyActivity = a.value">
            <text class="choice-title">{{ a.label }}</text><text class="choice-desc">{{ a.desc }}</text>
          </view>
          <view class="field-row"><text class="label">每周运动次数</text><input v-model.number="form.workoutsPerWeek" class="input" type="number" /></view>
          <template v-if="form.workoutsPerWeek > 0">
            <view class="field-row"><text class="label">单次时长（分钟）</text><input v-model.number="form.workoutMinutes" class="input" type="number" /></view>
            <view class="field-row"><text class="label">运动类型</text>
              <view class="segmented"><view v-for="t in workoutTypes" :key="t.value" class="seg" :class="{ on: form.workoutType === t.value }" @click="form.workoutType = t.value">{{ t.label }}</view></view>
            </view>
            <view class="field-row"><text class="label">强度</text>
              <view class="segmented"><view v-for="i in intensities" :key="i.value" class="seg" :class="{ on: form.workoutIntensity === i.value }" @click="form.workoutIntensity = i.value">{{ i.label }}</view></view>
            </view>
            <view class="hint">低：散步、瑜伽；中：慢跑、常规力量；高：间歇跑、大重量</view>
          </template>
        </view>
      </view>

      <view v-if="step === 3">
        <view class="h1">健康筛查</view>
        <view class="h2">有以下情况吗？我们会据此调整方案</view>
        <view class="card form">
          <view v-for="h in healthOptions" :key="h.value" class="check-row" :class="{ on: form.healthFlags.includes(h.value) }" @click="toggleHealth(h.value)">
            <text>{{ h.label }}</text><text class="check">{{ form.healthFlags.includes(h.value) ? '✓' : '' }}</text>
          </view>
        </view>
      </view>

      <view v-if="step === 4">
        <view class="h1">饮食偏好与餐次</view>
        <view class="card form">
          <view class="section">过敏与不耐受</view>
          <view class="tags"><view v-for="a in allergyOptions" :key="a" class="tag" :class="{ on: form.allergies.includes(a) }" @click="toggleInList(form.allergies, a)">{{ a }}</view></view>
          <view class="section">饮食禁忌</view>
          <view class="tags"><view v-for="r in restrictionOptions" :key="r" class="tag" :class="{ on: form.dietRestrictions.includes(r) }" @click="toggleInList(form.dietRestrictions, r)">{{ r }}</view></view>
          <view class="section">订购餐次（默认三餐）</view>
          <view class="tags"><view v-for="m in mealOptions" :key="m.value" class="tag" :class="{ on: form.orderedMeals.includes(m.value) }" @click="toggleInList(form.orderedMeals, m.value)">{{ m.label }}</view></view>
          <view class="hint">只订部分餐次时，页面会显示缺口；订满三餐才能覆盖全天目标</view>
        </view>
      </view>

      <view class="footer">
        <view class="btn" :class="{ disabled: saving }" @click="next">{{ saving ? '计算中…' : step < 4 ? '下一步' : '生成我的方案' }}</view>
      </view>
    </view>

    <view v-else-if="phase === 'blocked'" class="blocked">
      <view class="blocked-icon">!</view>
      <view class="h1">需要专业人员评估</view>
      <view class="h2">{{ blockedText[blocked?.reason] || blocked?.reason }}</view>
      <view class="blocked-card"><text>为了安全，这种情况不会自动生成方案。</text><text>建议先咨询医生或注册营养师。</text></view>
      <view class="btn" @click="phase = 'form'; step = 3">返回修改</view>
    </view>

    <view v-else class="result">
      <view class="h2">你的每日目标</view>
      <view class="protein-card">
        <text class="protein-label">每日蛋白档位</text>
        <view class="protein-value">{{ result.proteinTier }}<text class="unit">g</text></view>
      </view>
      <view class="kcal-row">
        <text class="kcal-number">{{ result.kcal }}</text><text class="unit">kcal / 天</text>
        <text class="muted">每日消耗约 {{ Math.round(result.tdee) }} kcal</text>
      </view>
      <view class="macros">
        <view class="macro"><text class="m-label">碳水</text><text class="m-value">{{ result.carbG }}g</text></view>
        <view class="macro"><text class="m-label">脂肪</text><text class="m-value">{{ result.fatG }}g</text></view>
        <view class="macro"><text class="m-label">蛋白</text><text class="m-value">{{ result.proteinTier }}g</text></view>
      </view>
      <view class="expect">{{ result.expectedWeeklyChange }}</view>
      <view class="price-card">
        <view class="row between"><text>三餐参考价</text><text class="price">约 ¥{{ price }}/天</text></view>
        <text class="muted">按蛋白档位计价，最终以下单页为准</text>
      </view>
      <view class="coverage">
        <text v-if="coverage < 100">当前订 {{ coverage }}% 的餐次；{{ coverage < 100 ? '加早餐即可覆盖全天蛋白' : '' }}</text>
        <text v-else>三餐已覆盖全天目标</text>
      </view>
      <view v-if="gap && gap.lines?.length" class="gap card">
        <view class="section">今日进度</view>
        <view v-for="line in gap.lines" :key="line.nutrient" class="gap-line">
          <view class="row between">
            <text>{{ line.label }}</text>
            <text :class="{ ok: line.status === 'OK', over: line.status === 'OVER' }">{{ gapText(line) }}</text>
          </view>
          <view class="bar"><view class="bar-fill" :class="{ over: line.status === 'OVER' }" :style="{ width: Math.min(100, line.targetG ? line.actualG / line.targetG * 100 : 0) + '%' }" /></view>
        </view>
      </view>
      <view class="disclaimer">本方案为一般性营养建议，不构成医疗诊断或治疗方案；如有疾病或正在用药，请先咨询医生。</view>
      <view class="btn" @click="finish">开始下单</view>
      <view class="link" @click="phase = 'form'; step = 1">修改档案</view>
    </view>
  </view>
</template>

<style scoped>
.page { min-height: 100vh; padding: 0 20px 110px; }
.deck { min-height: calc(100vh - 40px); margin: 0 -20px; padding: 8px 20px 28px; background: #032C0A; color: #F4F1E9; display: flex; flex-direction: column; }
.deck-brand { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; }
.deck-logo { width: 64px; height: 40px; filter: brightness(0) invert(1); }
.deck-name { font-size: 18px; font-weight: 600; }
.deck-en { font-size: 12px; letter-spacing: 3px; color: rgba(244,241,233,.55); }
.deck-title { font-size: 26px; font-weight: 600; }
.deck-sub { margin-top: 8px; font-size: 13px; color: rgba(244,241,233,.7); line-height: 1.6; }
.deck-swiper { height: 400px; margin-top: 22px; }
.slide { height: 100%; border-radius: 20px; overflow: hidden; background: #0B3A12; display: flex; flex-direction: column; }
.slide-img { width: 100%; height: 300px; }
.slide-ph { height: 300px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: linear-gradient(160deg,#14501C 0%,#0B3A12 60%,#06260B 100%); }
.ph-meal { font-size: 12px; letter-spacing: 3px; color: #73C308; }
.ph-name { font-size: 26px; font-weight: 600; text-align: center; padding: 0 24px; }
.slide-info { padding: 14px 18px; display: flex; flex-direction: column; gap: 4px; }
.slide-meal { font-size: 12px; color: #73C308; letter-spacing: 2px; }
.slide-name { font-size: 17px; font-weight: 600; }
.slide-meta { font-size: 13px; color: rgba(244,241,233,.65); }
.deck-empty { margin-top: 50px; text-align: center; font-size: 13px; color: rgba(244,241,233,.6); }
.deck-foot { margin-top: auto; padding-top: 22px; }
.phone-mask { position: fixed; inset: 0; z-index: 50; background: rgba(3,44,10,.96); display: flex; align-items: center; justify-content: center; padding: 24px; }
.phone-card { width: 100%; background: #F4F1E9; border-radius: 20px; padding: 26px 22px; display: flex; flex-direction: column; gap: 12px; }
.phone-title { font-size: 22px; font-weight: 600; color: #032C0A; }
.phone-desc { font-size: 13px; color: #6d7f75; line-height: 1.6; }
.phone-btn { margin-top: 8px; height: 50px; line-height: 50px; border-radius: 25px; background: #032C0A; color: #fff; font-size: 15px; font-weight: 600; text-align: center; }
.phone-btn::after { border: none; }
.phone-skip { text-align: center; font-size: 12px; color: #8a9a91; padding: 6px; }
.deck-cta { height: 52px; border-radius: 999px; background: #73C308; color: #032C0A; font-size: 16px; font-weight: 600; display: flex; align-items: center; justify-content: center; }
.deck-tip { margin-top: 10px; text-align: center; font-size: 13px; color: rgba(244,241,233,.5); }
.nav { display: flex; align-items: center; justify-content: space-between; height: 44px; }
.back { font-size: 26px; color: #1c3b2e; padding: 0 8px; }
.nav-title { font-size: 13px; color: #556A5E; }
.nav-space { width: 24px; }
.progress { height: 4px; background: #EEF3EE; border-radius: 2px; overflow: hidden; margin-bottom: 18px; }
.progress-bar { height: 100%; background: #73C308; }
.h1 { font-size: 22px; font-weight: 600; color: #032C0A; margin: 8px 0 6px; }
.h2 { font-size: 13px; color: #556A5E; line-height: 1.6; margin-bottom: 16px; }
.card { background: #fff; border-radius: 20px; padding: 14px 16px; border: 1px solid #E2E6DC; }
.form { display: flex; flex-direction: column; }
.field-row { display: flex; align-items: center; justify-content: space-between; min-height: 52px; border-bottom: 1px solid #EEF3EE; }
.field-row:last-child { border-bottom: none; }
.label { font-size: 13px; color: #556A5E; }
.input { flex: 1; text-align: right; font-size: 14px; color: #1c3b2e; }
.segmented { display: flex; background: #EEF3EE; border-radius: 12px; padding: 3px; }
.seg { padding: 6px 12px; border-radius: 8px; font-size: 13px; color: #556A5E; }
.seg.on { background: #032C0A; color: #fff; }
.goal-list { display: flex; flex-direction: column; gap: 12px; }
.goal { display: flex; flex-direction: column; gap: 4px; }
.goal.on { border-color: #032C0A; background: #f2f7ec; }
.goal-name { font-size: 16px; font-weight: 600; }
.goal-desc { font-size: 13px; color: #556A5E; }
.choice { padding: 12px 0; border-bottom: 1px solid #EEF3EE; display: flex; flex-direction: column; gap: 2px; }
.choice:last-child { border-bottom: none; }
.choice.on .choice-title { color: #032C0A; font-weight: 600; }
.choice-title { font-size: 14px; }
.choice-desc { font-size: 13px; color: #556A5E; }
.check-row { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid #EEF3EE; font-size: 14px; }
.check-row.on { color: #032C0A; font-weight: 600; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.tag { padding: 6px 12px; border-radius: 999px; background: #EEF3EE; font-size: 12px; color: #556A5E; }
.tag.on { background: #032C0A; color: #fff; }
.section { font-size: 13px; font-weight: 600; margin: 12px 0 8px; }
.hint { font-size: 13px; color: #556A5E; margin-top: 6px; }
.privacy { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: #556A5E; margin-bottom: 12px; line-height: 1.5; }
.radio { width: 16px; height: 16px; border-radius: 50%; border: 1px solid #556A5E; margin-top: 2px; }
.radio.on { background: #032C0A; border-color: #032C0A; }
.footer { position: fixed; left: 0; right: 0; bottom: 0; padding: 12px 20px calc(16px + env(safe-area-inset-bottom)); background: rgba(244,241,233,.96); }
.btn { height: 50px; border-radius: 999px; background: #032C0A; color: #fff; font-size: 15px; font-weight: 600; display: flex; align-items: center; justify-content: center; }
.disabled { opacity: .6; }
.blocked { padding-top: 60px; display: flex; flex-direction: column; gap: 14px; }
.blocked-icon { width: 44px; height: 44px; border-radius: 50%; background: #FBE6E2; color: #B04A3A; font-size: 24px; display: flex; align-items: center; justify-content: center; }
.blocked-card { background: #fff; border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: #556A5E; line-height: 1.6; }
.result { padding-top: 16px; display: flex; flex-direction: column; gap: 14px; }
.protein-card { background: #032C0A; color: #F4F1E9; border-radius: 20px; padding: 18px; }
.protein-label { font-size: 13px; color: rgba(244,241,233,.65); }
.protein-value { font-size: 48px; font-weight: 700; color: #73C308; }
.unit { font-size: 14px; color: #556A5E; margin-left: 4px; }
.protein-card .unit { color: rgba(244,241,233,.65); }
.kcal-row { display: flex; align-items: baseline; gap: 8px; }
.kcal-number { font-size: 34px; font-weight: 700; color: #032C0A; }
.muted { font-size: 13px; color: #556A5E; }
.macros { display: flex; gap: 10px; }
.macro { flex: 1; background: #fff; border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
.m-label { font-size: 13px; color: #556A5E; }
.m-value { font-size: 18px; font-weight: 600; color: #032C0A; }
.expect { font-size: 13px; color: #032C0A; background: #F2F7EC; border-radius: 12px; padding: 12px 14px; }
.price-card { background: #fff; border-radius: 12px; padding: 14px; display: flex; flex-direction: column; gap: 6px; }
.row { display: flex; } .between { justify-content: space-between; }
.price { font-size: 20px; font-weight: 700; color: #032C0A; }
.coverage { font-size: 13px; color: #556A5E; }
.gap { display: flex; flex-direction: column; }
.gap-line { margin-bottom: 10px; }
.bar { height: 6px; background: #eef3ee; border-radius: 3px; margin-top: 6px; overflow: hidden; }
.bar-fill { height: 100%; background: #73C308; }
.bar-fill.over { background: #B04A3A; }
.ok { color: #032C0A; }
.over { color: #B04A3A; }
.disclaimer { font-size: 13px; color: #556A5E; line-height: 1.6; }
.link { text-align: center; font-size: 13px; color: #556A5E; padding: 8px; }
</style>
