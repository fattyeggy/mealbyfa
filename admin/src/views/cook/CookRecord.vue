<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, PageResult } from '../../api'
import IngredientPicker from './IngredientPicker.vue'
import StepCard from './StepCard.vue'
import {
  Calculation,
  CookContent,
  CookDetail,
  CookIngredient,
  CookPart,
  CookStep,
  DishLite,
  fmtDuration,
  hasNutrition,
  LibIngredient,
  num,
  Plate,
  Role,
  roleLabel,
  ROLES,
  uid
} from './cook'
import './cook.css'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

type Tab = 'ingredients' | 'steps' | 'parts' | 'portions'
const tab = ref<Tab>('ingredients')
const loading = ref(true)
const loadError = ref('')

const meta = reactive({ title: '', dishId: null as number | null, cookDate: '', remark: '' })
const content = reactive<CookContent>({ portions: 1, ingredients: [], components: [], steps: [], weightTargets: null })
const info = reactive({ status: 'DRAFT' as 'DRAFT' | 'SYNCED', syncedVersionNo: null as number | null, dishName: '' })
const calc = ref<Calculation | null>(null)

const library = ref<LibIngredient[]>([])
const libById = computed(() => new Map(library.value.map((i) => [i.id, i])))
const dishes = ref<DishLite[]>([])

// ---------------------------------------------------------------- 加载

onMounted(async () => {
  try {
    const [detail, libPage, dishPage] = await Promise.all([
      api.get<CookDetail>(`/meal/cook-records/${id}`),
      api.get<PageResult<LibIngredient>>('/meal/ingredients?pageNo=1&pageSize=1000'),
      api.get<PageResult<DishLite>>('/meal/dishes?pageNo=1&pageSize=500')
    ])
    library.value = libPage.list
    dishes.value = dishPage.list
    applyDetail(detail, true)
    await nextTick()
    ready = true
    await offerBackup()
  } catch (e: any) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
})

function applyDetail(d: CookDetail, replaceContent: boolean) {
  if (replaceContent) {
    meta.title = d.title
    meta.dishId = d.dishId ?? null
    meta.cookDate = d.cookDate
    meta.remark = d.remark || ''
    content.portions = d.content.portions ?? 1
    content.ingredients = d.content.ingredients || []
    content.components = d.content.components || []
    content.steps = d.content.steps || []
    content.weightTargets = d.content.weightTargets ?? null
  }
  info.status = d.status
  info.syncedVersionNo = d.syncedVersionNo ?? null
  info.dishName = d.dishName || ''
  calc.value = d.calculation
}

// ---------------------------------------------------------------- 自动保存
// 厨房网络不稳：每次改动先备份到本机，停手 0.9 秒后保存到后台，保存成功再清掉备份

let ready = false
let timer: number | undefined
let inFlight: Promise<void> | null = null
let again = false
const saveState = ref<'saved' | 'dirty' | 'saving' | 'error'>('saved')
const saveError = ref('')
const backupKey = `sancan-cook-backup-${id}`

watch(
  [() => ({ ...meta }), content],
  () => {
    if (!ready) return
    saveState.value = 'dirty'
    backup()
    window.clearTimeout(timer)
    timer = window.setTimeout(() => save(), 900)
  },
  { deep: true }
)

function payload() {
  return {
    title: meta.title.trim() || '未命名',
    dishId: meta.dishId,
    cookDate: meta.cookDate || null,
    remark: meta.remark || null,
    content: JSON.parse(JSON.stringify(content))
  }
}

function backup() {
  try {
    localStorage.setItem(backupKey, JSON.stringify({ at: Date.now(), ...payload() }))
  } catch {
    /* 存储满了也不影响主流程 */
  }
}

async function save(): Promise<void> {
  window.clearTimeout(timer)
  if (inFlight) {
    again = true
    return inFlight
  }
  saveState.value = 'saving'
  inFlight = (async () => {
    try {
      const d = await api.put<CookDetail>(`/meal/cook-records/${id}`, payload())
      applyDetail(d, false)
      saveError.value = ''
      if (!again) {
        saveState.value = 'saved'
        localStorage.removeItem(backupKey)
      }
    } catch (e: any) {
      saveState.value = 'error'
      saveError.value = e.message
    } finally {
      inFlight = null
    }
  })()
  await inFlight
  if (again) {
    again = false
    await save()
  }
}

async function offerBackup() {
  const raw = localStorage.getItem(backupKey)
  if (!raw) return
  try {
    const b = JSON.parse(raw)
    if (JSON.stringify(b.content) === JSON.stringify(content) && b.title === meta.title) {
      localStorage.removeItem(backupKey)
      return
    }
    const when = new Date(b.at).toLocaleString('zh-CN', { hour12: false })
    await ElMessageBox.confirm(`这台手机上有 ${when} 没保存成功的修改，要恢复吗？`, '恢复未保存的修改', {
      confirmButtonText: '恢复',
      cancelButtonText: '不要了'
    })
    meta.title = b.title
    meta.dishId = b.dishId
    meta.cookDate = b.cookDate
    meta.remark = b.remark || ''
    Object.assign(content, b.content)
  } catch {
    localStorage.removeItem(backupKey)
  }
}

function beforeUnload(e: BeforeUnloadEvent) {
  if (saveState.value === 'dirty' || saveState.value === 'saving' || saveState.value === 'error') {
    e.preventDefault()
    e.returnValue = ''
  }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

onBeforeRouteLeave(async () => {
  stepCards.value.forEach((c) => c?.stop?.())
  if (saveState.value !== 'saved') await save()
  return true
})

const saveText = computed(() => {
  switch (saveState.value) {
    case 'dirty':
      return '待保存'
    case 'saving':
      return '保存中…'
    case 'error':
      return '保存失败，点重试'
    default:
      return '已保存'
  }
})

// ---------------------------------------------------------------- 食材

const pickerOpen = ref(false)
const pickerTarget = ref<string | null>(null)

const groups = computed(() => {
  const list = content.components.map((p) => ({
    part: p as CookPart | null,
    items: content.ingredients.filter((i) => i.componentKey === p.key)
  }))
  const loose = content.ingredients.filter((i) => !i.componentKey || !content.components.some((p) => p.key === i.componentKey))
  if (loose.length) list.push({ part: null, items: loose })
  return list
})

function openPicker(partKey: string | null) {
  pickerTarget.value = partKey
  pickerOpen.value = true
}

const pickerLabel = computed(() => content.components.find((p) => p.key === pickerTarget.value)?.name)

function onPick(item: LibIngredient) {
  pickerOpen.value = false
  content.ingredients.push({
    key: uid('i'),
    ingredientId: item.id,
    name: item.name,
    rawG: null,
    componentKey: pickerTarget.value
  })
  const key = content.ingredients[content.ingredients.length - 1].key
  nextTick(() => document.querySelector<HTMLInputElement>(`input[data-key="${key}"]`)?.focus())
}

function onCreated(item: LibIngredient) {
  library.value = [...library.value, item].sort((a, b) => a.name.localeCompare(b.name, 'zh'))
}

function lib(i: CookIngredient) {
  return i.ingredientId ? libById.value.get(i.ingredientId) : undefined
}

function contribution(i: CookIngredient) {
  const l = lib(i)
  const g = i.rawG || 0
  if (!l || !hasNutrition(l) || !g) return ''
  return `≈ ${Math.round((g / 100) * Number(l.kcalPer100g))} kcal · 蛋白 ${Math.round((g / 100) * Number(l.proteinPer100g) * 10) / 10}g`
}

async function removeIngredient(i: CookIngredient) {
  try {
    await ElMessageBox.confirm(`删除「${lib(i)?.name || i.name}」？`, '删除食材', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  content.ingredients = content.ingredients.filter((x) => x !== i)
}

// 补营养：食材库里缺每 100g 营养时，在这里直接补上
const fix = reactive({ open: false, id: 0, name: '', kcal: '' as any, protein: '' as any, carb: '' as any, fat: '' as any, saving: false })

function openFix(i: CookIngredient) {
  const l = lib(i)
  if (!l) return
  Object.assign(fix, {
    open: true,
    id: l.id,
    name: l.name,
    kcal: l.kcalPer100g ?? '',
    protein: l.proteinPer100g ?? '',
    carb: l.carbPer100g ?? '',
    fat: l.fatPer100g ?? '',
    saving: false
  })
}

async function saveFix() {
  const vals = [num(fix.kcal), num(fix.protein), num(fix.carb), num(fix.fat)]
  if (vals.some((v) => v === null || (v as number) < 0)) return ElMessage.warning('请填齐 4 项（可以填 0）')
  fix.saving = true
  try {
    const full = await api.get<any>(`/meal/ingredients/${fix.id}`)
    await api.put(`/meal/ingredients/${fix.id}`, {
      ...full,
      kcalPer100g: vals[0],
      proteinPer100g: vals[1],
      carbPer100g: vals[2],
      fatPer100g: vals[3]
    })
    const l = libById.value.get(fix.id)
    if (l) Object.assign(l, { kcalPer100g: vals[0], proteinPer100g: vals[1], carbPer100g: vals[2], fatPer100g: vals[3] })
    fix.open = false
    ElMessage.success('营养已补齐')
    await save()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    fix.saving = false
  }
}

function setPortions(n: number | null) {
  content.portions = n && n > 0 ? Math.min(500, Math.round(n)) : 1
}

// ---------------------------------------------------------------- 步骤

const stepCards = ref<any[]>([])
const totalTime = computed(() => content.steps.reduce((s, x) => s + (x.durationSec || 0), 0))

function addStep() {
  content.steps.push({ key: uid('s'), text: '', heat: '', durationSec: 0, photoIds: [] })
}

function patchStep(step: CookStep, patch: Partial<CookStep>) {
  Object.assign(step, patch)
}

async function removeStep(index: number) {
  const s = content.steps[index]
  if (s.text || s.photoIds?.length || s.durationSec) {
    try {
      await ElMessageBox.confirm(`删除第 ${index + 1} 步？`, '删除步骤', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    } catch {
      return
    }
  }
  content.steps.splice(index, 1)
}

function moveStep(index: number, delta: number) {
  const to = index + delta
  if (to < 0 || to >= content.steps.length) return
  const [s] = content.steps.splice(index, 1)
  content.steps.splice(to, 0, s)
}

// ---------------------------------------------------------------- 出品组件

const partResult = (key: string) => calc.value?.parts.find((p) => p.key === key)

function standardPlaceholder(p: CookPart) {
  if (!p.cookedG || !content.portions) return '熟重 ÷ 份数'
  return `默认 ${Math.round(p.cookedG / content.portions)}`
}

async function addPart() {
  try {
    const { value } = await ElMessageBox.prompt('组件名，如 蛋、玉米、酱汁', '加出品组件', {
      confirmButtonText: '添加',
      cancelButtonText: '取消',
      inputPattern: /\S/,
      inputErrorMessage: '请填写名字'
    })
    content.components.push({ key: uid('p'), name: value.trim(), role: 'OTHER', cookedG: null, standardG: null })
  } catch {
    /* 取消 */
  }
}

async function removePart(p: CookPart) {
  const used = content.ingredients.filter((i) => i.componentKey === p.key).length
  try {
    await ElMessageBox.confirm(
      used ? `「${p.name}」下有 ${used} 样食材，删除后它们会变成未归类。` : `删除「${p.name}」？`,
      '删除组件',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  content.ingredients.forEach((i) => {
    if (i.componentKey === p.key) i.componentKey = null
  })
  content.components = content.components.filter((x) => x !== p)
}

// ---------------------------------------------------------------- 分量

const meal = ref('LUNCH')
const tierTable = computed(() => calc.value?.tierTables.find((t) => t.mealType === meal.value))
const weightTargets = computed(() => (content.weightTargets?.length ? content.weightTargets : [300, 400, 500, 600]))
const newWeight = ref('')

function addWeight() {
  const w = num(newWeight.value)
  if (!w || w < 50 || w > 3000) return ElMessage.warning('请输入 50–3000 之间的克数')
  const set = new Set([...weightTargets.value, Math.round(w)])
  content.weightTargets = [...set].sort((a, b) => a - b)
  newWeight.value = ''
}

function removeWeight(w: number) {
  const list = weightTargets.value.filter((x) => x !== w)
  content.weightTargets = list.length ? list : null
}

function partsLine(plate: Plate) {
  return plate.parts.filter((p) => p.grams > 0)
}

const blockerCount = computed(() => calc.value?.blockers.length || 0)

// ---------------------------------------------------------------- 菜品与同步

const dishSheet = reactive({ open: false, keyword: '', creating: false, name: '', category: '主食', saving: false })
const CATEGORIES = ['主食', '热菜', '冷食', '早餐']
const dishName = computed(() => dishes.value.find((d) => d.id === meta.dishId)?.name || info.dishName)
const dishResults = computed(() => {
  const k = dishSheet.keyword.trim()
  return (k ? dishes.value.filter((d) => d.name.includes(k)) : dishes.value).slice(0, 50)
})

function openDishSheet() {
  Object.assign(dishSheet, { open: true, keyword: '', creating: false, name: meta.title, category: '主食', saving: false })
}

function chooseDish(d: DishLite) {
  meta.dishId = d.id
  info.dishName = d.name
  dishSheet.open = false
}

async function createDish() {
  if (!dishSheet.name.trim()) return ElMessage.warning('请填写菜品名')
  dishSheet.saving = true
  try {
    const newId = await api.post<number>('/meal/dishes', {
      name: dishSheet.name.trim(),
      category: dishSheet.category,
      status: 0
    })
    const d = { id: newId, name: dishSheet.name.trim(), category: dishSheet.category }
    dishes.value = [d, ...dishes.value]
    chooseDish(d)
    ElMessage.success('菜品已新建')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    dishSheet.saving = false
  }
}

const syncing = ref(false)

async function sync() {
  stepCards.value.forEach((c) => c?.stop?.())
  if (saveState.value !== 'saved') await save()
  if (saveState.value === 'error') return ElMessage.error('先保存成功才能同步：' + saveError.value)
  if (blockerCount.value) {
    tab.value = 'portions'
    return ElMessage.warning('还有 ' + blockerCount.value + ' 项需要补齐，见「分量」页顶部')
  }
  if (!meta.dishId) {
    openDishSheet()
    return ElMessage.info('先选择要同步到哪个菜品')
  }
  const std = calc.value?.standard
  try {
    await ElMessageBox.confirm(
      `将为「${dishName.value}」生成新版本：标准一份 ${std?.weightG}g · ${std?.nutrients.kcal} kcal · 蛋白 ${std?.nutrients.proteinG}g，并写入配方。` +
        '原来的生效版本会归档，已发布的菜单不受影响。',
      '同步到后台',
      { confirmButtonText: '同步', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  syncing.value = true
  try {
    const r = await api.post<{ dishId: number; versionId: number; versionNo: number }>(`/meal/cook-records/${id}/sync`, {
      dishId: meta.dishId
    })
    info.status = 'SYNCED'
    info.syncedVersionNo = r.versionNo
    ElMessage.success(`已同步为「${dishName.value}」第 ${r.versionNo} 版`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    syncing.value = false
  }
}

const tabs: { value: Tab; label: string }[] = [
  { value: 'ingredients', label: '食材' },
  { value: 'steps', label: '步骤' },
  { value: 'parts', label: '出品' },
  { value: 'portions', label: '分量' }
]

// 切换分页时回到顶部，否则会停在上一页的滚动位置
watch(tab, () => window.scrollTo({ top: 0 }))

function setRole(p: CookPart, r: Role) {
  p.role = r
}
</script>

<template>
  <div class="ck">
    <div v-if="loading" class="ck-empty">加载中…</div>
    <div v-else-if="loadError" class="ck-body">
      <div class="ck-notice bad">{{ loadError }}</div>
      <button class="ck-btn ghost" @click="router.push('/cook')">返回列表</button>
    </div>

    <template v-else>
      <header class="ck-head">
        <div class="ck-head-row">
          <button class="ck-back" aria-label="返回做菜记录列表" @click="router.push('/cook')">‹</button>
          <input v-model="meta.title" class="ck-title-input" aria-label="这锅菜的名字" maxlength="64" />
        </div>
        <div class="ck-sub">
          <input v-model="meta.cookDate" type="date" class="date" aria-label="做菜日期" />
          <button class="ck-chip" @click="openDishSheet">{{ dishName ? '菜品：' + dishName : '未关联菜品' }} ›</button>
          <span v-if="info.status === 'SYNCED'" class="ck-chip ok">已同步 · 第 {{ info.syncedVersionNo }} 版</span>
          <span v-else-if="info.syncedVersionNo" class="ck-chip warn">改过，未同步（上次第 {{ info.syncedVersionNo }} 版）</span>
        </div>
        <nav class="ck-tabs" aria-label="记录分区">
          <button v-for="t in tabs" :key="t.value" class="ck-tab" :class="{ on: tab === t.value }" @click="tab = t.value">
            {{ t.label }}<span v-if="t.value === 'portions' && blockerCount" class="dot" aria-label="有待补齐项" />
          </button>
        </nav>
      </header>

      <!-- ============ 食材 ============ -->
      <section v-if="tab === 'ingredients'" class="ck-body">
        <div class="ck-card ck-row ck-between">
          <div>
            <div><strong>这一锅分几份</strong></div>
            <div class="ck-muted">标准一份 = 熟重 ÷ 份数，可在「出品」单独改</div>
          </div>
          <div class="ck-row">
            <button class="ck-icon-btn" aria-label="少一份" @click="setPortions((content.portions || 1) - 1)">−</button>
            <input
              class="ck-input portions ck-num"
              inputmode="numeric"
              :value="content.portions"
              aria-label="份数"
              @change="setPortions(num(($event.target as HTMLInputElement).value))"
            />
            <button class="ck-icon-btn" aria-label="多一份" @click="setPortions((content.portions || 1) + 1)">＋</button>
          </div>
        </div>

        <div class="ck-muted">称的是<strong>下锅前的可食生重</strong>（去皮去骨后），按出品时在哪一块分组。</div>

        <template v-for="g in groups" :key="g.part?.key || 'loose'">
          <div class="ck-section-title">
            {{ g.part ? (g.part.name === roleLabel(g.part.role) ? g.part.name : g.part.name + ' · ' + roleLabel(g.part.role)) : '未归类（请选出品组件）' }}
          </div>
          <div v-for="i in g.items" :key="i.key" class="ck-card ing">
            <div class="ck-row ck-between">
              <strong>{{ lib(i)?.name || i.name || '未选择' }}</strong>
              <button class="ck-link danger" @click="removeIngredient(i)">删除</button>
            </div>
            <div class="ck-weigh">
              <input
                type="number"
                inputmode="decimal"
                min="0"
                step="any"
                :value="i.rawG ?? ''"
                :data-key="i.key"
                placeholder="0"
                :aria-label="(lib(i)?.name || '') + ' 生重克数'"
                @input="i.rawG = num(($event.target as HTMLInputElement).value)"
              />
              <span class="unit">g 生重</span>
            </div>
            <div v-if="!hasNutrition(lib(i))" class="ck-notice warn ck-row ck-between">
              <span>食材库里缺每 100g 营养</span>
              <button class="ck-link" @click="openFix(i)">补营养</button>
            </div>
            <div v-else-if="contribution(i)" class="ck-muted ck-num">{{ contribution(i) }}</div>
            <div class="ck-pills" role="group" aria-label="归到哪个出品组件">
              <button
                v-for="p in content.components"
                :key="p.key"
                class="ck-pill"
                :class="{ on: i.componentKey === p.key }"
                @click="i.componentKey = p.key"
              >{{ p.name }}</button>
            </div>
            <input v-model="i.note" class="ck-input" placeholder="备注（选填），如 去皮 / 切丁" aria-label="备注" />
          </div>
          <button v-if="g.part" class="ck-btn ghost" @click="openPicker(g.part.key)">＋ 加到「{{ g.part.name }}」</button>
        </template>
        <button v-if="!content.components.length" class="ck-btn ghost" @click="openPicker(null)">＋ 加食材</button>
      </section>

      <!-- ============ 步骤 ============ -->
      <section v-else-if="tab === 'steps'" class="ck-body">
        <div class="ck-row ck-between">
          <span class="ck-muted">共 {{ content.steps.length }} 步</span>
          <span class="ck-muted ck-num">累计 {{ fmtDuration(totalTime) }}</span>
        </div>
        <StepCard
          v-for="(s, idx) in content.steps"
          :key="s.key"
          :ref="(el: any) => (stepCards[idx] = el)"
          :step="s"
          :index="idx"
          :total="content.steps.length"
          :record-id="id"
          @update="(patch) => patchStep(s, patch)"
          @remove="removeStep(idx)"
          @move="(d) => moveStep(idx, d)"
        />
        <div v-if="!content.steps.length" class="ck-empty">还没有步骤，边做边加</div>
        <button class="ck-btn ghost block" @click="addStep">＋ 加一步</button>
      </section>

      <!-- ============ 出品 ============ -->
      <section v-else-if="tab === 'parts'" class="ck-body">
        <div class="ck-muted">做好后，把每一块<strong>单独称整锅熟重</strong>：肉捞出来称、汤汁倒出来称、米饭整锅称。</div>
        <div v-for="p in content.components" :key="p.key" class="ck-card part">
          <div class="ck-row">
            <input v-model="p.name" class="ck-input ck-grow" aria-label="组件名" maxlength="16" />
            <button class="ck-icon-btn" aria-label="删除组件" @click="removePart(p)">✕</button>
          </div>
          <div class="ck-pills" role="group" aria-label="组件类型">
            <button v-for="r in ROLES" :key="r.value" class="ck-pill" :class="{ on: p.role === r.value }" @click="setRole(p, r.value)">
              {{ r.label }}
            </button>
          </div>
          <div class="ck-muted">{{ ROLES.find((r) => r.value === p.role)?.hint }}</div>
          <div class="grid2">
            <label class="ck-field">
              <span class="ck-label">整锅熟重</span>
              <span class="ck-weigh">
                <input
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="any"
                  :value="p.cookedG ?? ''"
                  placeholder="0"
                  @input="p.cookedG = num(($event.target as HTMLInputElement).value)"
                />
                <span class="unit">g</span>
              </span>
            </label>
            <label class="ck-field">
              <span class="ck-label">标准一份放</span>
              <span class="ck-weigh">
                <input
                  type="number"
                  inputmode="decimal"
                  min="0"
                  step="any"
                  :value="p.standardG ?? ''"
                  :placeholder="standardPlaceholder(p)"
                  @input="p.standardG = num(($event.target as HTMLInputElement).value)"
                />
                <span class="unit">g</span>
              </span>
            </label>
          </div>
          <template v-if="partResult(p.key)">
            <div class="ck-muted ck-num">
              生重 {{ partResult(p.key)!.rawG }}g
              <template v-if="partResult(p.key)!.cookedG"> → 熟重 {{ partResult(p.key)!.cookedG }}g · 出成率 {{ Math.round(partResult(p.key)!.yieldRate! * 100) }}%</template>
            </div>
            <div v-if="partResult(p.key)!.per100g" class="ck-stats">
              <div class="ck-stat"><span class="v">{{ partResult(p.key)!.per100g!.kcal }}</span><span class="k">kcal/100g</span></div>
              <div class="ck-stat"><span class="v">{{ partResult(p.key)!.per100g!.proteinG }}</span><span class="k">蛋白 g</span></div>
              <div class="ck-stat"><span class="v">{{ partResult(p.key)!.per100g!.carbG }}</span><span class="k">碳水 g</span></div>
              <div class="ck-stat"><span class="v">{{ partResult(p.key)!.per100g!.fatG }}</span><span class="k">脂肪 g</span></div>
            </div>
          </template>
          <div v-else class="ck-muted">还没有食材归到这里</div>
        </div>
        <button class="ck-btn ghost block" @click="addPart">＋ 加出品组件</button>
      </section>

      <!-- ============ 分量 ============ -->
      <section v-else class="ck-body">
        <div v-if="calc?.blockers.length" class="ck-notice bad">
          <strong>还需要补齐 {{ calc.blockers.length }} 项才能同步</strong>
          <ul><li v-for="b in calc.blockers" :key="b">{{ b }}</li></ul>
        </div>
        <div v-if="calc?.warnings.length" class="ck-notice warn">
          <ul><li v-for="w in calc.warnings" :key="w">{{ w }}</li></ul>
        </div>

        <template v-if="calc?.standard">
          <div class="ck-card dark">
            <div class="ck-muted">标准一份</div>
            <div class="big ck-num">{{ calc.standard.weightG }}<small>g</small></div>
            <div class="plate-parts">
              <span v-for="pp in partsLine(calc.standard)" :key="pp.key">{{ pp.name }} <b class="ck-num">{{ pp.grams }}g</b></span>
            </div>
            <div class="ck-stats" style="margin-top: 12px">
              <div class="ck-stat"><span class="v">{{ calc.standard.nutrients.kcal }}</span><span class="k">kcal</span></div>
              <div class="ck-stat"><span class="v">{{ calc.standard.nutrients.proteinG }}</span><span class="k">蛋白 g</span></div>
              <div class="ck-stat"><span class="v">{{ calc.standard.nutrients.carbG }}</span><span class="k">碳水 g</span></div>
              <div class="ck-stat"><span class="v">{{ calc.standard.nutrients.fatG }}</span><span class="k">脂肪 g</span></div>
            </div>
          </div>

          <template v-if="calc.tierTables.length">
            <div class="ck-section-title">按蛋白档位出品</div>
            <div class="ck-tabs three">
              <button
                v-for="t in calc.tierTables"
                :key="t.mealType"
                class="ck-tab"
                :class="{ on: meal === t.mealType }"
                @click="meal = t.mealType"
              >{{ t.mealLabel }}</button>
            </div>
            <div class="ck-muted">
              这一餐占全天蛋白 {{ Math.round((tierTable?.share || 0) * 100) }}%。主料和汤汁一起按蛋白目标放大缩小，主食、蔬菜按标准一份；
              系统给每位用户配餐时，主食还会按其碳水目标再调整。
            </div>
            <div v-for="row in tierTable?.rows || []" :key="row.label" class="ck-card plate">
              <div class="ck-row ck-between">
                <strong>{{ row.label }}</strong>
                <span class="ck-muted ck-num">这餐蛋白目标 {{ row.targetProteinG }}g</span>
              </div>
              <div class="plate-grid">
                <div v-for="pp in partsLine(row)" :key="pp.key" class="pg">
                  <span class="k">{{ pp.name }}</span>
                  <span class="v ck-num">{{ pp.grams }}<small>g</small></span>
                </div>
              </div>
              <div class="ck-muted ck-num">
                共 {{ row.weightG }}g · {{ row.nutrients.kcal }} kcal · 蛋白 {{ row.nutrients.proteinG }} · 碳水 {{ row.nutrients.carbG }} · 脂肪 {{ row.nutrients.fatG }}
              </div>
              <div v-if="row.clamped" class="ck-chip warn">倍数 {{ row.factor }}，超出系统 0.3–2.5 倍范围，会进复核</div>
            </div>
          </template>

          <div class="ck-section-title">按成品重量出品</div>
          <div class="ck-card">
            <div class="ck-pills">
              <span v-for="w in weightTargets" :key="w" class="ck-chip">
                {{ w }}g <button class="x" :aria-label="'去掉 ' + w + 'g'" @click="removeWeight(w)">✕</button>
              </span>
            </div>
            <div class="ck-row" style="margin-top: 8px">
              <input v-model="newWeight" class="ck-input ck-grow" inputmode="numeric" placeholder="加一个克数，如 450" @keyup.enter="addWeight" />
              <button class="ck-btn soft" @click="addWeight">添加</button>
            </div>
          </div>
          <div v-for="row in calc.weightRows" :key="row.label" class="ck-card plate">
            <strong>一份 {{ row.label }}</strong>
            <div class="plate-grid">
              <div v-for="pp in partsLine(row)" :key="pp.key" class="pg">
                <span class="k">{{ pp.name }}</span>
                <span class="v ck-num">{{ pp.grams }}<small>g</small></span>
              </div>
            </div>
            <div class="ck-muted ck-num">
              {{ row.nutrients.kcal }} kcal · 蛋白 {{ row.nutrients.proteinG }} · 碳水 {{ row.nutrients.carbG }} · 脂肪 {{ row.nutrients.fatG }}
            </div>
          </div>

          <details class="ck-card">
            <summary><strong>同步后写入的配方（标准一份的可食生重）</strong></summary>
            <div v-for="b in calc.bom" :key="b.ingredientId + b.componentType" class="ck-row ck-between bom">
              <span>{{ b.name }} <span class="ck-chip">{{ roleLabel(b.componentType) }}</span></span>
              <span class="ck-num">{{ b.rawG }}g</span>
            </div>
          </details>
        </template>
        <div v-else-if="!calc?.blockers.length" class="ck-empty">加了食材、称了熟重之后，这里会出分量表</div>

        <label class="ck-field">
          <span class="ck-label">备注</span>
          <textarea v-model="meta.remark" class="ck-textarea" maxlength="255" placeholder="这锅的心得，如 盐可以少 2g" />
        </label>
      </section>

      <footer class="ck-footer">
        <button
          class="ck-btn soft save"
          :class="saveState"
          :disabled="saveState === 'saving'"
          :title="saveError"
          @click="save()"
        >{{ saveText }}</button>
        <button class="ck-btn ck-grow" :disabled="syncing" @click="sync">
          {{ syncing ? '同步中…' : info.status === 'SYNCED' ? '已同步，可再次同步' : '同步到后台' }}
        </button>
      </footer>

      <IngredientPicker
        :open="pickerOpen"
        :library="library"
        :target-label="pickerLabel"
        @close="pickerOpen = false"
        @pick="onPick"
        @created="onCreated"
      />

      <!-- 补营养 -->
      <div v-if="fix.open" class="ck-sheet-mask" @click.self="fix.open = false">
        <div class="ck-sheet" role="dialog" aria-label="补营养">
          <strong>补齐「{{ fix.name }}」每 100g 生重营养</strong>
          <div class="grid2">
            <label class="ck-field"><span class="ck-label">热量 kcal</span><input v-model="fix.kcal" class="ck-input" inputmode="decimal" /></label>
            <label class="ck-field"><span class="ck-label">蛋白 g</span><input v-model="fix.protein" class="ck-input" inputmode="decimal" /></label>
            <label class="ck-field"><span class="ck-label">碳水 g</span><input v-model="fix.carb" class="ck-input" inputmode="decimal" /></label>
            <label class="ck-field"><span class="ck-label">脂肪 g</span><input v-model="fix.fat" class="ck-input" inputmode="decimal" /></label>
          </div>
          <div class="ck-muted">会更新食材库，其他用到这个食材的菜品下次建版本时也会用新数据。</div>
          <div class="ck-row">
            <button class="ck-btn soft" @click="fix.open = false">取消</button>
            <button class="ck-btn ck-grow" :disabled="fix.saving" @click="saveFix">{{ fix.saving ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>

      <!-- 选菜品 -->
      <div v-if="dishSheet.open" class="ck-sheet-mask" @click.self="dishSheet.open = false">
        <div class="ck-sheet" role="dialog" aria-label="选择菜品">
          <div class="ck-row ck-between">
            <strong>{{ dishSheet.creating ? '新建菜品' : '同步到哪个菜品' }}</strong>
            <button class="ck-link" @click="dishSheet.open = false">关闭</button>
          </div>
          <template v-if="!dishSheet.creating">
            <input v-model="dishSheet.keyword" class="ck-input" placeholder="搜索菜品" aria-label="搜索菜品" />
            <button
              v-for="d in dishResults"
              :key="d.id"
              class="ck-list-btn"
              :class="{ chosen: d.id === meta.dishId }"
              @click="chooseDish(d)"
            >
              <span>{{ d.name }} <span class="ck-muted">· {{ d.category }}</span></span>
              <span v-if="d.id === meta.dishId" aria-label="当前">✓</span>
            </button>
            <div v-if="!dishResults.length" class="ck-empty">没有找到</div>
            <button class="ck-btn ghost block" @click="dishSheet.creating = true">新建菜品</button>
          </template>
          <template v-else>
            <label class="ck-field">
              <span class="ck-label">菜品名</span>
              <input v-model="dishSheet.name" class="ck-input" maxlength="64" />
            </label>
            <div class="ck-pills">
              <button v-for="c in CATEGORIES" :key="c" class="ck-pill" :class="{ on: dishSheet.category === c }" @click="dishSheet.category = c">{{ c }}</button>
            </div>
            <div class="ck-row">
              <button class="ck-btn soft" @click="dishSheet.creating = false">返回</button>
              <button class="ck-btn ck-grow" :disabled="dishSheet.saving" @click="createDish">{{ dishSheet.saving ? '保存中…' : '新建并选择' }}</button>
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.date {
  border: 1px solid var(--ck-line);
  border-radius: 999px;
  background: var(--ck-surface);
  padding: 2px 10px;
  min-height: 32px;
  font-size: 13px;
}

.portions {
  width: 64px;
  text-align: center;
  font-size: 18px;
  font-weight: 700;
}

.ing,
.part,
.plate {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.grid2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.big {
  font-size: 48px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--ck-lime);
}

.big small {
  font-size: 18px;
  margin-left: 2px;
}

.plate-parts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 6px;
}

/* 主菜常见 4 块（肉 / 汤汁 / 主食 / 蔬菜）在手机上排成一行 */
.plate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  gap: 6px;
}

.pg {
  display: flex;
  flex-direction: column;
  background: var(--ck-track);
  border-radius: 12px;
  padding: 8px;
}

.pg .k {
  font-size: 13px;
  color: var(--ck-muted);
}

.pg .v {
  font-size: 22px;
  font-weight: 700;
}

.pg .v small {
  font-size: 13px;
  font-weight: 400;
  margin-left: 1px;
}

.ck-tabs.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 0;
}

.x {
  border: none;
  background: none;
  padding: 0 0 0 4px;
  min-width: 24px;
  min-height: 24px;
}

.bom {
  padding: 8px 0;
  border-bottom: 1px solid var(--ck-line);
}

summary {
  cursor: pointer;
  min-height: 32px;
}

.save {
  min-width: 112px;
}

.save.error {
  background: #fbe6e2;
  color: var(--ck-tomato) !important;
}

.save.dirty {
  background: var(--ck-amber-tint);
  color: var(--ck-amber) !important;
}

.ck-list-btn.chosen {
  outline: 1.5px solid var(--ck-forest);
}
</style>
