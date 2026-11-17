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
