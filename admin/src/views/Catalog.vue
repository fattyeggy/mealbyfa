<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, PageResult } from '../api'

interface Ingredient {
  id: number
  name: string
  category?: string
  purchaseUnit: string
  usageUnit: string
  conversionRate: number
  edibleRate: number
  cookingLossRate?: number
  shelfLifeDays?: number
  allergens?: string
  movingAvgPrice: number
  status: number
}

interface Dish {
  id: number
  name: string
  category: string
  description?: string
  storageMethod?: string
  reheatMethod?: string
  extraAllergens?: string
  status: number
}

interface DishVersion {
  id: number
  version: number
  netWeightG: number
  kcal?: number
  proteinG?: number
  carbG?: number
  fatG?: number
  cost?: number
  remark?: string
}

const tab = ref('ingredients')
const loading = ref(false)

const ingredients = ref<Ingredient[]>([])
const dishes = ref<Dish[]>([])
const keyword = ref('')

const showIngredient = ref(false)
const editingIngredient = ref<Ingredient | null>(null)
const ingredientForm = reactive<any>({
  name: '', category: '主料', purchaseUnit: 'kg', usageUnit: 'kg',
  conversionRate: 1, edibleRate: 0.9, cookingLossRate: 0, shelfLifeDays: 3,
  allergens: '', movingAvgPrice: 0, status: 0
})

const showDish = ref(false)
const editingDish = ref<Dish | null>(null)
const dishForm = reactive<any>({
  name: '', category: '午餐', description: '', storageMethod: '0-4℃ 冷藏',
  reheatMethod: '微波 90 秒', extraAllergens: '', status: 0
})

const showVersions = ref(false)
const currentDish = ref<Dish | null>(null)
const versions = ref<DishVersion[]>([])

const showVersionForm = ref(false)
const versionForm = reactive<any>({
  netWeightG: 500, kcal: 600, proteinG: 35, carbG: 60, fatG: 15, remark: '',
  bom: [{ ingredientId: null, netWeightG: 100, note: '' }]
})

const ingredientOptions = computed(() => ingredients.value.map(i => ({ value: i.id, label: i.name })))

async function loadIngredients() {
  loading.value = true
  try {
    const page = await api.get<PageResult<Ingredient>>(
      `/meal/ingredients?pageNo=1&pageSize=200${keyword.value ? `&keyword=${encodeURIComponent(keyword.value)}` : ''}`
    )
    ingredients.value = page.list
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadDishes() {
  loading.value = true
  try {
    const page = await api.get<PageResult<Dish>>(
      `/meal/dishes?pageNo=1&pageSize=200${keyword.value ? `&keyword=${encodeURIComponent(keyword.value)}` : ''}`
    )
    dishes.value = page.list
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function refresh() {
  tab.value === 'ingredients' ? loadIngredients() : loadDishes()
}

function openIngredient(row?: Ingredient) {
  editingIngredient.value = row || null
  Object.assign(ingredientForm, row
    ? { ...row }
    : {
        name: '', category: '主料', purchaseUnit: 'kg', usageUnit: 'kg',
        conversionRate: 1, edibleRate: 0.9, cookingLossRate: 0, shelfLifeDays: 3,
        allergens: '', movingAvgPrice: 0, status: 0
      })
  showIngredient.value = true
}

async function saveIngredient() {
  try {
    if (editingIngredient.value) {
      await api.put(`/meal/ingredients/${editingIngredient.value.id}`, ingredientForm)
    } else {
      await api.post('/meal/ingredients', ingredientForm)
    }
    showIngredient.value = false
    ElMessage.success('已保存')
    await loadIngredients()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function removeIngredient(row: Ingredient) {
  try {
    await ElMessageBox.confirm(`确认删除食材「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/meal/ingredients/${row.id}`)
    ElMessage.success('已删除')
    await loadIngredients()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

function openDish(row?: Dish) {
  editingDish.value = row || null
  Object.assign(dishForm, row
    ? { ...row }
    : {
        name: '', category: '午餐', description: '', storageMethod: '0-4℃ 冷藏',
        reheatMethod: '微波 90 秒', extraAllergens: '', status: 0
      })
  showDish.value = true
}

async function saveDish() {
  try {
    if (editingDish.value) {
      await api.put(`/meal/dishes/${editingDish.value.id}`, dishForm)
    } else {
      await api.post('/meal/dishes', dishForm)
    }
    showDish.value = false
    ElMessage.success('已保存')
    await loadDishes()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function openVersions(row: Dish) {
  currentDish.value = row
  showVersions.value = true
  try {
    versions.value = await api.get<DishVersion[]>(`/meal/dishes/${row.id}/versions`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function openVersionForm() {
  Object.assign(versionForm, {
    netWeightG: 500, kcal: 600, proteinG: 35, carbG: 60, fatG: 15, remark: '',
    bom: [{ ingredientId: null, netWeightG: 100, note: '' }]
  })
  showVersionForm.value = true
}

function addBomLine() {
  versionForm.bom.push({ ingredientId: null, netWeightG: 50, note: '' })
}

function removeBomLine(index: number) {
  versionForm.bom.splice(index, 1)
}

async function saveVersion() {
  if (!currentDish.value) return
  const bom = versionForm.bom.filter((line: any) => line.ingredientId && line.netWeightG > 0)
  if (!bom.length) {
    ElMessage.warning('BOM 至少需要一行有效食材')
    return
  }
  try {
    await api.post(`/meal/dishes/${currentDish.value.id}/versions`, { ...versionForm, bom })
    showVersionForm.value = false
    ElMessage.success('版本已创建')
    await openVersions(currentDish.value)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadIngredients()
  loadDishes()
})
</script>

<template>
  <div>
    <div class="page-title">菜品与食材</div>
    <div class="page-desc">
      食材的可食率决定成本口径；菜品必须有版本与 BOM 才能被排进菜单。
    </div>

    <el-tabs v-model="tab" @tab-change="refresh">
      <el-tab-pane label="食材" name="ingredients">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索名称" clearable style="width: 200px" @keyup.enter="loadIngredients" />
          <el-button @click="loadIngredients">查询</el-button>
          <el-button type="primary" @click="openIngredient()">新建食材</el-button>
        </div>
        <div class="panel">
          <el-table :data="ingredients" v-loading="loading" stripe>
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column label="单位" width="150">
              <template #default="{ row }">{{ row.purchaseUnit }} → {{ row.usageUnit }}（×{{ row.conversionRate }}）</template>
            </el-table-column>
            <el-table-column label="可食率" width="90">
              <template #default="{ row }">{{ Math.round(row.edibleRate * 100) }}%</template>
            </el-table-column>
            <el-table-column label="单价" width="100">
              <template #default="{ row }">¥{{ row.movingAvgPrice }}</template>
            </el-table-column>
            <el-table-column prop="allergens" label="过敏原" width="130" />
            <el-table-column prop="shelfLifeDays" label="保质(天)" width="90" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="openIngredient(row)">编辑</el-button>
                <el-button link type="danger" @click="removeIngredient(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="菜品" name="dishes">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索名称" clearable style="width: 200px" @keyup.enter="loadDishes" />
          <el-button @click="loadDishes">查询</el-button>
          <el-button type="primary" @click="openDish()">新建菜品</el-button>
        </div>
        <div class="panel">
          <el-table :data="dishes" v-loading="loading" stripe>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="storageMethod" label="保存" width="140" />
            <el-table-column prop="reheatMethod" label="复热" width="140" />
            <el-table-column prop="extraAllergens" label="额外过敏原" width="140" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDish(row)">编辑</el-button>
                <el-button link type="primary" @click="openVersions(row)">版本 / BOM</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 食材编辑 -->
    <el-dialog v-model="showIngredient" :title="editingIngredient ? '编辑食材' : '新建食材'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="名称"><el-input v-model="ingredientForm.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="ingredientForm.category" /></el-form-item>
        <el-form-item label="采购单位"><el-input v-model="ingredientForm.purchaseUnit" /></el-form-item>
        <el-form-item label="使用单位"><el-input v-model="ingredientForm.usageUnit" /></el-form-item>
        <el-form-item label="换算率">
          <el-input-number v-model="ingredientForm.conversionRate" :min="0.0001" :step="0.1" />
          <span class="hint">1 采购单位 = 多少使用单位</span>
        </el-form-item>
        <el-form-item label="可食率">
          <el-input-number v-model="ingredientForm.edibleRate" :min="0.01" :max="1" :step="0.01" :precision="3" />
          <span class="hint">去根、去皮后的可用比例</span>
        </el-form-item>
        <el-form-item label="烹饪损耗">
          <el-input-number v-model="ingredientForm.cookingLossRate" :min="0" :max="1" :step="0.01" :precision="3" />
        </el-form-item>
        <el-form-item label="移动加权价">
          <el-input-number v-model="ingredientForm.movingAvgPrice" :min="0" :step="0.5" :precision="2" />
          <span class="hint">元 / 采购单位</span>
        </el-form-item>
        <el-form-item label="保质天数"><el-input-number v-model="ingredientForm.shelfLifeDays" :min="0" /></el-form-item>
        <el-form-item label="过敏原"><el-input v-model="ingredientForm.allergens" placeholder="花生、虾…" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showIngredient = false">取消</el-button>
        <el-button type="primary" @click="saveIngredient">保存</el-button>
      </template>
    </el-dialog>

    <!-- 菜品编辑 -->
    <el-dialog v-model="showDish" :title="editingDish ? '编辑菜品' : '新建菜品'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="名称"><el-input v-model="dishForm.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="dishForm.category" /></el-form-item>
        <el-form-item label="介绍"><el-input v-model="dishForm.description" /></el-form-item>
        <el-form-item label="保存方式"><el-input v-model="dishForm.storageMethod" /></el-form-item>
        <el-form-item label="复热方式"><el-input v-model="dishForm.reheatMethod" /></el-form-item>
        <el-form-item label="额外过敏原"><el-input v-model="dishForm.extraAllergens" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDish = false">取消</el-button>
        <el-button type="primary" @click="saveDish">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本列表 -->
    <el-drawer v-model="showVersions" :title="`${currentDish?.name || ''} · 版本与 BOM`" size="620px">
      <div class="toolbar">
        <el-button type="primary" @click="openVersionForm">新建版本</el-button>
      </div>
      <el-table :data="versions">
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="netWeightG" label="净重(g)" width="90" />
        <el-table-column prop="kcal" label="热量" width="80" />
        <el-table-column prop="proteinG" label="蛋白(g)" width="90" />
        <el-table-column label="成本" width="100">
          <template #default="{ row }">{{ row.cost == null ? '—' : '¥' + Number(row.cost).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
      </el-table>
      <el-empty v-if="!versions.length" description="还没有版本，先建一个再排菜单" />

      <el-dialog v-model="showVersionForm" title="新建菜品版本" width="620px" append-to-body>
        <el-form label-width="90px">
          <el-form-item label="净重(g)"><el-input-number v-model="versionForm.netWeightG" :min="1" :step="10" /></el-form-item>
          <el-form-item label="热量">
            <el-input-number v-model="versionForm.kcal" :min="0" :step="10" />
            <span class="hint">kcal / 份</span>
          </el-form-item>
          <el-form-item label="蛋白(g)"><el-input-number v-model="versionForm.proteinG" :min="0" :step="1" /></el-form-item>
          <el-form-item label="碳水/脂肪">
            <el-input-number v-model="versionForm.carbG" :min="0" :step="1" />
            <el-input-number v-model="versionForm.fatG" :min="0" :step="1" />
          </el-form-item>
          <el-form-item label="BOM">
            <div class="bom">
              <div v-for="(line, index) in versionForm.bom" :key="index" class="bom-line">
                <el-select v-model="line.ingredientId" placeholder="食材" filterable style="width: 180px">
                  <el-option v-for="opt in ingredientOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-input-number v-model="line.netWeightG" :min="1" :step="10" />
                <span class="hint">g 可食生重</span>
                <el-button link type="danger" @click="removeBomLine(index)">移除</el-button>
              </div>
              <el-button link type="primary" @click="addBomLine">+ 添加食材</el-button>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showVersionForm = false">取消</el-button>
          <el-button type="primary" @click="saveVersion">创建</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </div>
</template>

<style scoped>
.hint {
  margin-left: 10px;
  color: #8a9a91;
  font-size: 12px;
}

.bom {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.bom-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
