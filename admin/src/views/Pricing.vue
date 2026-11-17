<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

interface Version {
  id: number
  versionNo: string
  status: string
  effectiveAt?: string
  dailyBasePrice: number
  includedProteinG: number
  extraProteinPricePer10g: number
  minDailyPrice: number
  minTargetProteinG: number
  maxTargetProteinG: number
  swapCostDivisor: number
  reviewedBy?: string
  reviewedTime?: string
  publishTime?: string
}

const rows = ref<Version[]>([])
const loading = ref(false)
const versions = ref<Version[]>([])
const dishVersions = ref<any[]>([])

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({
  versionNo: '',
  dailyBasePrice: 66,
  extraProteinPricePer10g: 1.9,
  minDailyPrice: 65,
  minTargetProteinG: 80,
  maxTargetProteinG: 220,
  swapCostDivisor: 0.77,
  remark: ''
})

const swapVisible = ref(false)
const swapVersion = ref<Version | null>(null)
const swaps = ref<any[]>([])
const swapForm = reactive({ mealType: 'LUNCH', fromDishVersionId: null as number | null, toDishVersionId: null as number | null })

const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
const statusType = (s: string) => (s === 'PUBLISHED' ? 'success' : s === 'DRAFT' ? 'warning' : 'info')

async function load() {
  loading.value = true
  try {
    rows.value = await api.get<Version[]>('/meal/pricing/versions')
    versions.value = rows.value
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadDishVersions() {
  if (dishVersions.value.length) return
  try {
    dishVersions.value = await api.get<any[]>('/meal/dishes/versions/options')
  } catch (e) {
    dishVersions.value = []
  }
}

function openCreate() {
  form.versionNo = 'V1.' + (rows.value.length + 1)
  createVisible.value = true
}

async function create() {
  creating.value = true
  try {
    await api.post('/meal/pricing/versions', form)
    ElMessage.success('已创建草稿版本')
    createVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

async function review(row: Version) {
  try {
    await ElMessageBox.confirm('复核将记录复核人与时间，之后才能发布。确认复核通过？', '复核版本')
  } catch {
    return
  }
  try {
    await api.post(`/meal/pricing/versions/${row.id}/review`)
    ElMessage.success('已复核')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function publish(row: Version) {
  try {
    await ElMessageBox.confirm('发布后旧版本归档，新计划将使用新价格。确认发布？', '发布定价版本', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.post(`/meal/pricing/versions/${row.id}/publish`)
    ElMessage.success('已发布')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function openSwaps(row: Version) {
  swapVersion.value = row
  swapVisible.value = true
  await loadDishVersions()
  swaps.value = await api.get<any[]>(`/meal/pricing/versions/${row.id}/swaps`)
}

async function addSwap() {
  if (!swapVersion.value || !swapForm.fromDishVersionId || !swapForm.toDishVersionId) {
    ElMessage.warning('请选择原餐次与替换餐次')
    return
  }
  try {
    await api.post(`/meal/pricing/versions/${swapVersion.value.id}/swaps`, swapForm)
    ElMessage.success('已加入替换选项（审核时冻结成本）')
    swaps.value = await api.get<any[]>(`/meal/pricing/versions/${swapVersion.value.id}/swaps`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">定价版本</div>
    <div class="page-desc">
      改价必须新建版本：草稿 → 复核 → 发布。已发布版本不可修改，已确认订单不受影响。
    </div>

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建版本</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="versionNo" label="版本" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="基础日价" width="100">
          <template #default="{ row }">¥{{ row.dailyBasePrice }}</template>
        </el-table-column>
        <el-table-column label="含蛋白" width="90">
          <template #default="{ row }">{{ row.includedProteinG }}g</template>
        </el-table-column>
        <el-table-column label="加量单价" width="110">
          <template #default="{ row }">¥{{ row.extraProteinPricePer10g }}/10g</template>
        </el-table-column>
        <el-table-column label="蛋白范围" width="120">
          <template #default="{ row }">{{ row.minTargetProteinG }}–{{ row.maxTargetProteinG }}g</template>
        </el-table-column>
        <el-table-column label="复核" width="150">
          <template #default="{ row }">
            {{ row.reviewedBy ? row.reviewedBy.replace('ADMIN:', '管理员 ') : '未复核' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="review(row)">复核</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row)">发布</el-button>
            <el-button link type="primary" @click="openSwaps(row)">替换选项</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建定价版本" width="560px">
      <el-form label-width="120px">
        <el-form-item label="版本号"><el-input v-model="form.versionNo" /></el-form-item>
        <el-form-item label="基础日价（元）"><el-input-number v-model="form.dailyBasePrice" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="含基础蛋白（g）">
          <el-input-number v-model="form.includedProteinG" :min="0" :max="200" />
        </el-form-item>
        <el-form-item label="加量单价（元/10g）">
          <el-input-number v-model="form.extraProteinPricePer10g" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="最低日价（元）"><el-input-number v-model="form.minDailyPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="蛋白范围（g）">
          <el-input-number v-model="form.minTargetProteinG" :min="1" />
          <span style="margin: 0 6px">–</span>
          <el-input-number v-model="form.maxTargetProteinG" :min="1" />
        </el-form-item>
        <el-form-item label="成本折算系数">
          <el-input-number v-model="form.swapCostDivisor" :min="0.01" :precision="2" :step="0.01" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建草稿</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="swapVisible" :title="`替换选项 · ${swapVersion?.versionNo}`" size="620px">
      <el-alert
        type="info"
        :closable="false"
        title="审核即冻结成本：之后改菜谱或采购价，不会改变已审核选项的差价依据。"
        style="margin-bottom: 12px"
      />
      <el-table :data="swaps" size="small">
        <el-table-column prop="mealType" label="餐次" width="90" />
        <el-table-column label="原 → 替换" min-width="180">
          <template #default="{ row }">#{{ row.fromDishVersionId }} → #{{ row.toDishVersionId }}</template>
        </el-table-column>
        <el-table-column label="冻结成本差" width="120">
          <template #default="{ row }">¥{{ row.costDelta }}</template>
        </el-table-column>
      </el-table>

      <el-divider>新增选项</el-divider>
      <el-form label-width="90px">
        <el-form-item label="餐次">
          <el-select v-model="swapForm.mealType" style="width: 160px">
            <el-option label="早餐" value="BREAKFAST" />
            <el-option label="午餐" value="LUNCH" />
            <el-option label="晚餐" value="DINNER" />
          </el-select>
        </el-form-item>
        <el-form-item label="原餐次">
          <el-select v-model="swapForm.fromDishVersionId" filterable style="width: 100%">
            <el-option
              v-for="opt in dishVersions"
              :key="opt.id"
              :label="`${opt.dishName} v${opt.version} · ${opt.proteinG}g`"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="替换为">
          <el-select v-model="swapForm.toDishVersionId" filterable style="width: 100%">
            <el-option
              v-for="opt in dishVersions"
              :key="opt.id"
              :label="`${opt.dishName} v${opt.version} · ${opt.proteinG}g`"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="addSwap">加入替换选项</el-button>
      </el-form>
    </el-drawer>
  </div>
</template>
