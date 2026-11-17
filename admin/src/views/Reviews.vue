<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

interface Review {
  id: number
  planId: number
  userId: number
  serviceDate?: string
  mealType?: string
  reviewType: string
  reviewMessage: string
  status: string
  resolutionNote?: string
  createTime?: string
}

const rows = ref<Review[]>([])
const loading = ref(false)
const status = ref('PENDING')

const typeText: Record<string, string> = {
  PROTEIN_RANGE: '蛋白越界',
  SCALE_CLAMP: '份量触界',
  PRICE_FLOOR: '价格兜底',
  COST_MISSING: '成本缺失',
  MANUAL: '人工确认'
}

const mealText: Record<string, string> = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }

async function load() {
  loading.value = true
  try {
    const query = status.value ? `?status=${status.value}&limit=200` : '?limit=200'
    rows.value = await api.get<Review[]>(`/meal/reviews${query}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function resolve(row: Review) {
  let note = ''
  try {
    const result = await ElMessageBox.prompt('填写处理说明（会记入复核记录）', '处理复核事项', {
      inputPlaceholder: '例如：已电话确认，按边界蛋白执行'
    })
    note = result.value
  } catch {
    return
  }
  try {
    await api.post(`/meal/reviews/${row.id}/resolve`, { note })
    ElMessage.success('已处理')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">复核事项</div>
    <div class="page-desc">
      计划里所有需要人工确认的问题都在这里。存在未处理事项时，用户无法下单。
    </div>

    <div class="toolbar">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button label="PENDING">待处理</el-radio-button>
        <el-radio-button label="RESOLVED">已处理</el-radio-button>
        <el-radio-button label="">全部</el-radio-button>
      </el-radio-group>
      <el-button @click="load">刷新</el-button>
      <span class="muted">共 {{ rows.length }} 条</span>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="planId" label="计划" width="80" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column label="范围" width="170">
          <template #default="{ row }">
            {{ row.serviceDate || '计划级' }}
            <span v-if="row.mealType"> · {{ mealText[row.mealType] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reviewType === 'SCALE_CLAMP' ? 'warning' : 'info'">
              {{ typeText[row.reviewType] || row.reviewType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewMessage" label="原因" min-width="280" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PENDING' ? 'danger' : 'success'">
              {{ row.status === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="resolve(row)">处理</el-button>
            <span v-else class="muted">{{ row.resolutionNote || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: #8a9a91;
  font-size: 12px;
}
</style>
