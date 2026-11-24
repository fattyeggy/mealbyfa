// 做菜记录：类型、文案与工具函数

export type Role = 'PROTEIN' | 'SAUCE' | 'STAPLE' | 'VEGETABLE' | 'FAT' | 'OTHER'

export const ROLES: { value: Role; label: string; hint: string }[] = [
  { value: 'PROTEIN', label: '主料', hint: '肉、蛋、豆腐，跟蛋白档位走' },
  { value: 'SAUCE', label: '汤汁', hint: '跟主料按比例走' },
  { value: 'STAPLE', label: '主食', hint: '米饭、面、薯类' },
  { value: 'VEGETABLE', label: '蔬菜', hint: '固定量' },
  { value: 'FAT', label: '油脂酱料', hint: '另外淋的油、酱' },
  { value: 'OTHER', label: '其他', hint: '固定量' }
]

export function roleLabel(role?: string) {
  return ROLES.find((r) => r.value === role)?.label || '其他'
}

export const HEATS = ['大火', '中火', '小火', '焖', '烤箱', '静置']

export interface CookIngredient {
  key: string
  ingredientId?: number | null
  name?: string
  rawG?: number | null
  componentKey?: string | null
  note?: string
}

export interface CookPart {
  key: string
  name: string
  role: Role
  cookedG?: number | null
  standardG?: number | null
}

export interface CookStep {
  key: string
  text?: string
  heat?: string
  durationSec?: number | null
  photoIds?: number[]
}

export interface CookContent {
  portions?: number | null
  ingredients: CookIngredient[]
  components: CookPart[]
  steps: CookStep[]
  weightTargets?: number[] | null
}

export interface Nutrients {
  kcal: number
  proteinG: number
  carbG: number
  fatG: number
}

export interface PartResult {
  key: string
  name: string
  role: Role
  rawG: number
  cookedG?: number
  yieldRate?: number
  per100g?: Nutrients
  batchTotal: Nutrients
  standardG?: number
}

export interface PlatePart {
  key: string
  name: string
  role: Role
  grams: number
}

export interface Plate {
  label: string
  targetProteinG?: number
  targetWeightG?: number
  factor: number
  clamped: boolean
  parts: PlatePart[]
  weightG: number
  nutrients: Nutrients
}

export interface TierTable {
  mealType: string
  mealLabel: string
  share: number
  rows: Plate[]
}

export interface Calculation {
  blockers: string[]
  warnings: string[]
  parts: PartResult[]
  standard?: Plate
  tierTables: TierTable[]
  weightRows: Plate[]
  bom: { ingredientId: number; name: string; componentType: Role; rawG: number; note?: string }[]
}

export interface CookDetail {
  id: number
  title: string
  dishId?: number | null
  dishName?: string
  cookDate: string
  status: 'DRAFT' | 'SYNCED'
  syncedVersionId?: number
  syncedVersionNo?: number
  syncedTime?: string
  remark?: string
  content: CookContent
  calculation: Calculation
}

export interface CookSummary {
  id: number
  title: string
  dishId?: number
  dishName?: string
  cookDate: string
  status: 'DRAFT' | 'SYNCED'
  syncedVersionId?: number
  updateTime?: string
}

export interface LibIngredient {
  id: number
  name: string
  componentType?: string
  kcalPer100g?: number | null
  proteinPer100g?: number | null
  carbPer100g?: number | null
  fatPer100g?: number | null
  allergens?: string
}

export interface DishLite {
  id: number
  name: string
  category: string
}

export function uid(prefix: string) {
  return prefix + Math.random().toString(36).slice(2, 8) + Date.now().toString(36).slice(-3)
}

export function hasNutrition(i?: LibIngredient | null) {
  return !!i && i.kcalPer100g != null && i.proteinPer100g != null && i.carbPer100g != null && i.fatPer100g != null
}

export function fmtDuration(sec?: number | null) {
  const s = Math.max(0, Math.round(sec || 0))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

export function num(v: any): number | null {
  if (v === '' || v === null || v === undefined) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/** 手机拍的照片动辄 3–8MB：压到长边 1280px 的 JPEG 再上传。 */
export async function compressImage(file: File, maxSide = 1280, quality = 0.82): Promise<Blob> {
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image()
      el.onload = () => resolve(el)
      el.onerror = () => reject(new Error('这张照片无法读取，请换一张'))
      el.src = url
    })
    const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight))
    const w = Math.max(1, Math.round(img.naturalWidth * scale))
    const h = Math.max(1, Math.round(img.naturalHeight * scale))
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('浏览器不支持压缩照片')
    ctx.drawImage(img, 0, 0, w, h)
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('照片压缩失败'))), 'image/jpeg', quality)
    )
  } finally {
    URL.revokeObjectURL(url)
  }
}
