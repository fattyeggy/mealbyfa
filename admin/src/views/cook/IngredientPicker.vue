<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { hasNutrition, LibIngredient, num } from './cook'

// 选食材的底部弹层：搜索食材库；找不到就现场新建（带每 100g 营养）
const props = defineProps<{ open: boolean; library: LibIngredient[]; targetLabel?: string }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'pick', item: LibIngredient): void
  (e: 'created', item: LibIngredient): void
}>()

const keyword = ref('')
const creating = ref(false)
const saving = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)

const TYPES = [
  { value: 'PROTEIN', label: '肉蛋豆' },
  { value: 'STAPLE', label: '主食' },
  { value: 'VEGETABLE', label: '蔬菜' },
  { value: 'FAT', label: '油脂' },
  { value: 'OTHER', label: '调料其他' }
]

const form = reactive({
  name: '',
  componentType: 'OTHER',
  kcal: '' as any,
  protein: '' as any,
  carb: '' as any,
  fat: '' as any,
  pricePerKg: '' as any,
  allergens: ''
})

const results = computed(() => {
  const k = keyword.value.trim()
  const list = k ? props.library.filter((i) => i.name.includes(k)) : props.library
  return list.slice(0, 60)
})

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    keyword.value = ''
    creating.value = false
    await nextTick()
    searchInput.value?.focus()
  }
)

function startCreate() {
  creating.value = true
  form.name = keyword.value.trim()
  form.componentType = 'OTHER'
  form.kcal = form.protein = form.carb = form.fat = form.pricePerKg = ''
  form.allergens = ''
}

async function create() {
  const kcal = num(form.kcal)
  const protein = num(form.protein)
  const carb = num(form.carb)
  const fat = num(form.fat)
  if (!form.name.trim()) return ElMessage.warning('请填写食材名')
  if ([kcal, protein, carb, fat].some((v) => v === null || v < 0)) {
    return ElMessage.warning('请填齐每 100g 的热量、蛋白、碳水、脂肪（盐、水可以填 0）')
  }
  saving.value = true
  try {
    const id = await api.post<number>('/meal/ingredients', {
      name: form.name.trim(),
      category: '做菜记录新建',
      purchaseUnit: 'kg',
      usageUnit: 'g',
      conversionRate: 1000,
      edibleRate: 1,
      cookingLossRate: 0,
      allergens: form.allergens.trim() || null,
      movingAvgPrice: num(form.pricePerKg) ?? 0,
      componentType: form.componentType,
      kcalPer100g: kcal,
      proteinPer100g: protein,
      carbPer100g: carb,
      fatPer100g: fat,
      status: 0
    })
    const item: LibIngredient = {
      id,
      name: form.name.trim(),
      componentType: form.componentType,
      kcalPer100g: kcal,
      proteinPer100g: protein,
      carbPer100g: carb,
      fatPer100g: fat,
      allergens: form.allergens.trim()
    }
    emit('created', item)
    emit('pick', item)
    ElMessage.success('已加入食材库')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="ck-sheet-mask" @click.self="emit('close')">
    <div class="ck-sheet" role="dialog" aria-label="选择食材">
      <div class="ck-row ck-between">
        <strong>{{ creating ? '新建食材' : '加食材' }}<span v-if="targetLabel" class="ck-muted"> · 加到「{{ targetLabel }}」</span></strong>
        <button class="ck-link" @click="emit('close')">关闭</button>
      </div>

      <template v-if="!creating">
        <input ref="searchInput" v-model="keyword" class="ck-input" placeholder="搜索食材库，如 鸡胸" aria-label="搜索食材" />
        <button
          v-for="item in results"
          :key="item.id"
          class="ck-list-btn"
          @click="emit('pick', item)"
        >
          <span class="ck-grow">
            <span>{{ item.name }}</span>
            <span v-if="hasNutrition(item)" class="ck-muted ck-num" style="display: block">
              每 100g {{ item.kcalPer100g }} kcal · 蛋白 {{ item.proteinPer100g }}g
            </span>
            <span v-else class="ck-chip warn" style="margin-top: 2px">缺营养，选后可补</span>
          </span>
          <span aria-hidden="true">＋</span>
        </button>
        <div v-if="!results.length" class="ck-empty">食材库里没有「{{ keyword }}」</div>
        <button class="ck-btn ghost block" @click="startCreate">没找到？新建食材</button>
      </template>

      <template v-else>
        <label class="ck-field">
          <span class="ck-label">食材名</span>
          <input v-model="form.name" class="ck-input" placeholder="如 鸡腿肉（去皮）" />
        </label>
        <div class="ck-field">
          <span class="ck-label">类型</span>
          <div class="ck-pills">
            <button
              v-for="t in TYPES"
              :key="t.value"
              class="ck-pill"
              :class="{ on: form.componentType === t.value }"
              @click="form.componentType = t.value"
            >{{ t.label }}</button>
          </div>
        </div>
        <div class="ck-label">每 100g 生重营养（包装营养表或食物成分表）</div>
        <div class="grid4">
          <label class="ck-field"><span class="ck-label">热量 kcal</span><input v-model="form.kcal" class="ck-input" inputmode="decimal" /></label>
          <label class="ck-field"><span class="ck-label">蛋白 g</span><input v-model="form.protein" class="ck-input" inputmode="decimal" /></label>
          <label class="ck-field"><span class="ck-label">碳水 g</span><input v-model="form.carb" class="ck-input" inputmode="decimal" /></label>
          <label class="ck-field"><span class="ck-label">脂肪 g</span><input v-model="form.fat" class="ck-input" inputmode="decimal" /></label>
        </div>
        <div class="grid2">
          <label class="ck-field"><span class="ck-label">进价 元/kg（选填）</span><input v-model="form.pricePerKg" class="ck-input" inputmode="decimal" /></label>
          <label class="ck-field"><span class="ck-label">过敏原（选填）</span><input v-model="form.allergens" class="ck-input" placeholder="如 大豆、小麦" /></label>
        </div>
        <div class="ck-row">
          <button class="ck-btn soft" @click="creating = false">返回搜索</button>
          <button class="ck-btn ck-grow" :disabled="saving" @click="create">{{ saving ? '保存中…' : '保存并加入' }}</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.grid4 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.grid2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
</style>
