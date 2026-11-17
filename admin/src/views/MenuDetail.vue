<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const detail = ref<any>(null)
const options = ref<any[]>([])
const checkVisible = ref(false)
const checkResult = ref<any>(null)
const checking = ref(false)

const editVisible = ref(false)
const saving = ref(false)
const editDay = ref('')
const editMeals = reactive<Record<string, number | null>>({
  BREAKFAST: null,
  LUNCH: null,
  DINNER: null
})

const slotMap: Record<string, Record<string, number>> = reactive({})

const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }

const isDraft = computed(() => detail.value?.status === 'DRAFT')

async function load() {
  loading.value = true
  try {
    detail.value = await api.get<any>(`/meal/menus/${id}`)
    Object.keys(slotMap).forEach((k) => delete slotMap[k])
    for (const day of detail.value.days) {
      const map: Record<string, number> = {}
      for (const meal of day.meals) {
        map[meal.mealType] = meal.dishVersionId
      }
      slotMap[day.serviceDate] = map
    }
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    options.value = await api.get<any[]>('/meal/dishes/versions/options')
  } catch (e) {
    options.value = []
  }
}

function openEdit(day: any) {
  editDay.value = day.serviceDate
  editMeals.BREAKFAST = slotMap[day.serviceDate]?.BREAKFAST ?? null
  editMeals.LUNCH = slotMap[day.serviceDate]?.LUNCH ?? null
  editMeals.DINNER = slotMap[day.serviceDate]?.DINNER ?? null
  editVisible.value = true
}

async function saveMeals() {
  if (!editMeals.BREAKFAST || !editMeals.LUNCH || !editMeals.DINNER) {
    ElMessage.warning('请为早/午/晚都选择菜品版本')
    return
  }
  const slots: any[] = []
  for (const day of detail.value.days) {
    const values: Record<string, number> = day.serviceDate === editDay.value
      ? { BREAKFAST: editMeals.BREAKFAST!, LUNCH: editMeals.LUNCH!, DINNER: editMeals.DINNER! }
      : (slotMap[day.serviceDate] || {})
    for (const mealType of ['BREAKFAST', 'LUNCH', 'DINNER']) {
      if (values[mealType]) {
        slots.push({ serviceDate: day.serviceDate, mealType, dishVersionId: values[mealType] })
      }
    }
  }
  saving.value = true
  try {
    await api.put(`/meal/menus/${id}/meals`, { meals: slots })
    ElMessage.success('已保存餐次')
    editVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function check() {
  checkVisible.value = true
  checking.value = true
  checkResult.value = null
  try {
    checkResult.value = await api.get<any>(`/meal/menus/${id}/check`)
  } catch (e: any) {
    ElMessage.error(e.message)
    checkVisible.value = false
  } finally {
    checking.value = false
  }
}

async function publish() {
  try {
    await ElMessageBox.confirm('发布后菜单冻结，不能再原地修改。确认发布？', '发布菜单', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.post(`/meal/menus/${id}/publish`)
    ElMessage.success('已发布')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await load()
  await loadOptions()
})
</script>

<template>
  <div v-loading="loading">
    <div class="head">
      <el-button link @click="router.push('/menus')">← 返回</el-button>
    </div>

    <template v-if="detail">
      <div class="page-title">{{ detail.themeName }}</div>
      <div class="page-desc">
        {{ detail.startDate }} ~ {{ detail.endDate }} · 版本 {{ detail.version }} ·
        <el-tag size="small" :type="detail.status === 'PUBLISHED' ? 'success' : 'warning'">
          {{ statusText[detail.status] }}
        </el-tag>
      </div>

      <div class="toolbar">
        <span class="metric">已配置 {{ detail.filledMeals }} / 21 餐次</span>
        <span class="metric">标准成本 ¥{{ detail.standardCost ?? '—' }}</span>
        <el-button @click="check">发布前检查</el-button>
        <el-button v-if="isDraft" type="primary" @click="publish">发布</el-button>
      </div>

      <div class="days">
        <div v-for="day in detail.days" :key="day.serviceDate" class="day panel">
          <div class="day-head">
            <div>
              <span class="day-date">{{ day.serviceDate }}</span>
              <span class="muted">周{{ ['一', '二', '三', '四', '五', '六', '日'][day.dayOfWeek - 1] }}</span>
            </div>
            <div class="day-right">
              <span class="muted">{{ day.totalWeightG }}g · {{ day.totalProteinG }}g 蛋白 · {{ day.totalPoints }} 点</span>
              <el-button v-if="isDraft" link type="primary" @click="openEdit(day)">编辑餐次</el-button>
            </div>
          </div>

          <el-table :data="day.meals" size="small">
            <el-table-column prop="mealLabel" label="餐次" width="70" />
            <el-table-column prop="dishName" label="菜品" min-width="160" />
            <el-table-column prop="weightG" label="克重" width="80" />
            <el-table-column prop="proteinG" label="蛋白" width="80" />
            <el-table-column prop="points" label="营养点" width="80" />
            <el-table-column label="成本" width="90">
              <template #default="{ row }">¥{{ row.cost }}</template>
            </el-table-column>
            <el-table-column prop="deliveryBatch" label="批次" width="100" />
          </el-table>
        </div>
      </div>
    </template>

    <el-dialog v-model="editVisible" :title="`编辑 ${editDay} 的餐次`" width="520px">
      <el-form label-width="70px">
        <el-form-item v-for="type in ['BREAKFAST', 'LUNCH', 'DINNER']" :key="type" :label="{ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }[type]">
          <el-select v-model="editMeals[type]" filterable placeholder="选择菜品版本" style="width: 100%">
            <el-option
              v-for="opt in options"
              :key="opt.id"
              :label="`${opt.dishName} v${opt.version} · ${opt.proteinG}g 蛋白 · ¥${opt.cost}`"
              :value="opt.id"
              :disabled="opt.status !== 'ACTIVE'"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMeals">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="发布前检查" width="640px">
      <div v-loading="checking">
        <template v-if="checkResult">
          <el-tag :type="checkResult.passed ? 'success' : 'danger'">
            {{ checkResult.passed ? '通过' : '未通过' }}
          </el-tag>
          <el-table :data="checkResult.items" size="small" style="margin-top: 12px">
            <el-table-column prop="name" label="检查项" width="220" />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PASS' ? 'success' : row.status === 'FAIL' ? 'danger' : 'info'">
                  {{ row.status === 'PASS' ? '通过' : row.status === 'FAIL' ? '未通过' : '人工' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="说明" />
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  margin-bottom: 8px;
}

.metric {
  font-size: 13px;
  color: #4a6a58;
  background: #eef3ee;
  border-radius: 8px;
  padding: 6px 10px;
}

.muted {
  color: #8a9a91;
  font-size: 12px;
}

.days {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 14px;
}

.day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.day-date {
  font-size: 15px;
  font-weight: 700;
  margin-right: 6px;
}

.day-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
