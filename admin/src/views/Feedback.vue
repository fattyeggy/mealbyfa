<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

interface Feedback {
  id: number
  orderId: number
  userId: number
  serviceDate: string
  mealType: string
  leftoverRatio: string
  satiety?: number
  trainingState?: string
  content?: string
  createTime?: string
}

const rows = ref<Feedback[]>([])
const loading = ref(false)
const serviceDate = ref('')

const ratioLabel: Record<string, string> = {
  EATEN: '吃完',
  QUARTER: '剩四分之一',
  HALF: '剩一半',
  NONE: '未吃'
}

const mealLabel: Record<string, string> = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }

async function load() {
  loading.value = true
  try {
    const query = serviceDate.value ? `?serviceDate=${serviceDate.value}` : ''
    rows.value = await api.get<Feedback[]>(`/meal/feedback${query}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function eatenRate(row: Feedback) {
  const map: Record<string, number> = { EATEN: 100, QUARTER: 75, HALF: 50, NONE: 0 }
  return (map[row.leftoverRatio] ?? 0) + '%'
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">完餐反馈</div>
    <div class="page-desc">
      每餐的剩余比例、饱腹感与训练状态。这是周复盘的唯一数据来源，第一阶段由运营人工据此调整计划。
    </div>

    <div class="toolbar">
      <el-date-picker v-model="serviceDate" type="date" value-format="YYYY-MM-DD" placeholder="服务日" @change="load" />
      <el-button @click="load">刷新</el-button>
      <span class="muted">共 {{ rows.length }} 条</span>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="serviceDate" label="服务日" width="120" />
        <el-table-column label="餐次" width="80">
          <template #default="{ row }">{{ mealLabel[row.mealType] || row.mealType }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.leftoverRatio === 'EATEN' ? 'success' : row.leftoverRatio === 'NONE' ? 'danger' : 'warning'">
              {{ ratioLabel[row.leftoverRatio] || row.leftoverRatio }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进食率" width="90">
          <template #default="{ row }">{{ eatenRate(row) }}</template>
        </el-table-column>
        <el-table-column label="饱腹感" width="90">
          <template #default="{ row }">{{ row.satiety ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="trainingState" label="训练状态" width="140" />
        <el-table-column prop="content" label="备注" min-width="200" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="orderId" label="订单" width="80" />
      </el-table>
      <el-empty v-if="!rows.length" description="该服务日还没有反馈" />
    </div>
  </div>
</template>

<style scoped>
.muted {
  color: #8a9a91;
  font-size: 12px;
}
</style>
