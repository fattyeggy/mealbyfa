import { reactive } from 'vue'
import { api, hasSession, login } from '@/utils/api'

export interface Profile {
  userId: number
  gender: string
  birthDate: string
  heightCm: number
  weightKg: number
  activityLevel: string
  goal: string
  trainingState?: string
  appetiteLevel: string
  dislikes?: string
  allergies: string[]
  proteinPref?: string
  contactName?: string
  contactPhone?: string
  address?: string
  onboardingCompleted: boolean
}

export interface Quote {
  versionNo: string
  dailyPrice: number
  rawPrice: number
  basePrice: number
  extraFee: number
  swapDifference: number
  breakfastPrice: number
  lunchPrice: number
  dinnerPrice: number
  rawTargetProteinG: number
  targetProteinG: number
  needsReview: boolean
  reviews: Array<{ reviewType: string; serviceDate?: string; mealType?: string; message: string }>
}

export const session = reactive({
  ready: false,
  userId: 0,
  nickname: '',
  profile: null as Profile | null,
  wallet: { balance: 0, creditBalance: 0, cashBalance: 0 }
})

/** 应用启动时保证有会话并拉取档案。 */
export async function bootstrap() {
  try {
    if (!hasSession()) {
      await login()
    }
    const me: any = await api.get('/app/me')
    session.userId = me.id
    session.nickname = me.nickname
    await refreshProfile()
    await refreshWallet()
  } catch (e) {
    // 首屏失败不阻塞渲染，页面会展示重试入口
    console.warn('[session] bootstrap failed', e)
  } finally {
    session.ready = true
  }
}

export async function refreshProfile() {
  try {
    const profile = await api.get<Profile>('/app/profile')
    session.profile = profile && profile.userId ? profile : null
  } catch (e) {
    session.profile = null
  }
  return session.profile
}

export async function refreshWallet() {
  try {
    const wallet = await api.get<any>('/app/wallet')
    session.wallet = {
      balance: Number(wallet.balance || 0),
      creditBalance: Number(wallet.creditBalance || 0),
      cashBalance: Number(wallet.cashBalance || 0)
    }
  } catch (e) {
    // 未登录态忽略
  }
  return session.wallet
}

export function isOnboarded() {
  return !!(session.profile && session.profile.onboardingCompleted)
}

export function money(value: number | string) {
  const n = Number(value || 0)
  return n.toFixed(2)
}

export function mealLabel(mealType: string) {
  return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' } as Record<string, string>)[mealType] || mealType
}

export function weekdayLabel(index: number) {
  return ['一', '二', '三', '四', '五', '六', '日'][index] || ''
}

/** 菜名去掉内部编号后缀（如「照烧鸡胸饭-P1」）。 */
export function cleanDishName(name?: string) {
  return (name || '').replace(/-?P\d+$/i, '')
}

/** 券规则文案：满减券 / 折扣券。 */
export function couponRule(c: any) {
  if (!c) return ''
  const threshold = Number(c.thresholdAmount || 0)
  const prefix = threshold > 0 ? '满 ' + money(threshold) + ' ' : ''
  if (c.type === 'RATE') {
    const pct = Math.round(Number(c.discountRate || 0) * 100)
    const cap = Number(c.maxDiscountAmount || 0) > 0 ? '，最多减 ' + money(c.maxDiscountAmount) : ''
    return prefix + '减 ' + pct + '%' + cap
  }
  return (threshold > 0 ? prefix + '减 ' : '立减 ') + money(c.discountAmount)
}

/** 由 yyyy-MM-dd 得到「一」…「日」，不依赖列表下标。 */
export function weekdayOf(iso?: string) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  const idx = (new Date(y, m - 1, d).getDay() + 6) % 7
  return ['一', '二', '三', '四', '五', '六', '日'][idx]
}

export function todayIso() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
