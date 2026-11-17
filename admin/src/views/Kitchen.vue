<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, downloadCsv } from '../api'

const serviceDate = ref('')
const summary = ref<any>(null)
const demand = ref<any>(null)
const loading = ref(false)
const generating = ref(false)

const outputVisible = ref(false)
const outputBatch = ref<any>(null)
const outputForm = reactive({ actualQty: 0, wasteQty: 0, remark: '' })

const issueVisible = ref(false)
const issueBatch = ref<any>(null)
const ingredients = ref<any[]>([])
const issueForm = reactive({ ingredientId: null as number | null, actualG: 0, wasteG: 0, reason: '' })

async function load() {
  if (!serviceDate.value) {
    ElMessage.warning('请先选择服务日')
    return
  }
  loading.value = true
  try {
    summary.value = await api.get<any>(`/meal/kitchen/summary?serviceDate=${serviceDate.value}`)
    demand.value = await api.get<any>(`/meal/kitchen/material-demand?serviceDate=${serviceDate.value}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function exportProduction() {
  try {
    await downloadCsv(`/meal/kitchen/export/production?serviceDate=${serviceDate.value}`,
      `生产单-${serviceDate.value}.csv`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function exportLabels() {
  try {
    await downloadCsv(`/meal/kitchen/export/labels?serviceDate=${serviceDate.value}`,
      `分装标签-${serviceDate.value}.csv`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function generate() {
  if (!serviceDate.value) {
    ElMessage.warning('请先选择服务日')
    return
  }
  generating.value = true
  try {
    summary.value = await api.post<any>(`/meal/kitchen/generate?serviceDate=${serviceDate.value}`)
    demand.value = await api.get<any>(`/meal/kitchen/material-demand?serviceDate=${serviceDate.value}`)
    ElMessage.success('已按已锁订单生成生产批次')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    generating.value = false
  }
}

function openOutput(row: any) {
  outputBatch.value = row
  outputForm.actualQty = row.actualQty || row.planQty
  outputForm.wasteQty = row.wasteQty || 0
  outputForm.remark = ''
  outputVisible.value = true
}

async function saveOutput() {
  try {
    await api.post(`/meal/kitchen/batches/${outputBatch.value.id}/output`, outputForm)
    ElMessage.success('已登记产量')
    outputVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function openIssue(row: any) {
  issueBatch.value = row
  issueForm.ingredientId = null
  issueForm.actualG = 0
  issueForm.wasteG = 0
  issueForm.reason = ''
  issueVisible.value = true
  if (!demand.value) await load()
}

async function saveIssue() {
  if (!issueForm.ingredientId) {
    ElMessage.warning('请选择食材')
    return
  }
  try {
    await api.post(`/meal/kitchen/batches/${issueBatch.value.id}/issue`, issueForm)
    ElMessage.success('已登记领料')
    issueVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  const d = new Date(Date.now() + 86400000)
  serviceDate.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  await load()
})
</script>

<template>
  <div>
    <div class="page-title">厨房生产</div>
    <div class="page-desc">
      按「服务日 + 配送批次 + 菜品」合并生产，不按订单逐单制作。理论用量已按个人份量折算。
    </div>

    <div class="toolbar">
      <el-date-picker v-model="serviceDate" type="date" value-format="YYYY-MM-DD" placeholder="服务日" />
      <el-button type="primary" :loading="generating" @click="generate">生成生产批次</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-button :disabled="!serviceDate" @click="exportProduction">导出生产单</el-button>
      <el-button :disabled="!serviceDate" @click="exportLabels">导出分装标签</el-button>
    </div>

    <div v-if="summary" class="toolbar">
      <span class="metric">计划 {{ summary.totalPlanQty }} 份</span>
      <span class="metric">实做 {{ summary.totalActualQty }} 份</span>
      <span class="metric">报废 {{ summary.totalWasteQty }} 份</span>
      <span class="metric">标签 {{ summary.labels?.length || 0 }} 张</span>
    </div>

    <div class="panel">
      <el-table :data="summary?.batches || []" v-loading="loading" stripe>
        <el-table-column label="批次" width="170">
          <template #default="{ row }">{{ row.batchLabel }}</template>
        </el-table-column>
        <el-table-column prop="dishName" label="菜品" min-width="180" />
        <el-table-column prop="planQty" label="计划" width="80" />
        <el-table-column prop="actualQty" label="实做" width="80" />
        <el-table-column prop="wasteQty" label="报废" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DONE' ? 'success' : row.status === 'PREPARING' ? 'warning' : 'info'">
              {{ row.status === 'PLANNED' ? '待开工' : row.status === 'PREPARING' ? '制作中' : '已完成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openOutput(row)">登记产量</el-button>
            <el-button link type="primary" @click="openIssue(row)">登记领料</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="section-title">食材需求</div>
    <div class="panel">
      <el-table :data="demand?.items || []" size="small" stripe>
        <el-table-column prop="ingredientName" label="食材" min-width="140" />
        <el-table-column prop="edibleG" label="可食生重(g)" width="110" />
        <el-table-column prop="purchaseG" label="采购重量(g)" width="110" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.cost }}</template>
        </el-table-column>
        <el-table-column label="实际领料" width="110">
          <template #default="{ row }">{{ row.actualG ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="损耗" width="100">
          <template #default="{ row }">{{ row.wasteG ?? '—' }}</template>
        </el-table-column>
      </el-table>
      <div v-if="demand" class="muted total">合计成本 ¥{{ demand.totalCost }}</div>
    </div>

    <el-dialog v-model="outputVisible" title="登记成品产量" width="420px">
      <el-form label-width="90px">
        <el-form-item label="菜品">{{ outputBatch?.dishName }}</el-form-item>
        <el-form-item label="实做份数"><el-input-number v-model="outputForm.actualQty" :min="0" /></el-form-item>
        <el-form-item label="报废份数"><el-input-number v-model="outputForm.wasteQty" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="outputForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outputVisible = false">取消</el-button>
        <el-button type="primary" @click="saveOutput">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="issueVisible" title="登记领料" width="460px">
      <el-form label-width="90px">
        <el-form-item label="菜品">{{ issueBatch?.dishName }}</el-form-item>
        <el-form-item label="食材">
          <el-select v-model="issueForm.ingredientId" filterable style="width: 100%">
            <el-option
              v-for="item in demand?.items || []"
              :key="item.ingredientId"
              :label="`${item.ingredientName}（理论 ${item.edibleG}g）`"
              :value="item.ingredientId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实际用量(g)"><el-input-number v-model="issueForm.actualG" :min="0" /></el-form-item>
        <el-form-item label="损耗(g)"><el-input-number v-model="issueForm.wasteG" :min="0" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="issueForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" @click="saveIssue">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.metric {
  font-size: 13px;
  color: #4a6a58;
  background: #eef3ee;
  border-radius: 8px;
  padding: 6px 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 20px 0 10px;
}

.muted {
  color: #8a9a91;
  font-size: 12px;
}

.total {
  margin-top: 10px;
  text-align: right;
}
</style>
