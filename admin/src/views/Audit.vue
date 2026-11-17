<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, PageResult } from '../api'

interface AuditLog {
  id: number
  operatorType: string
  operatorId: number
  action: string
  targetType: string
  targetId: string
  detail: string
  createTime: string
}

const rows = ref<AuditLog[]>([])
const total = ref(0)
const loading = ref(false)
const action = ref('')

const actions: Record<string, string> = {
  CREDIT_GRANT: '发放 credit',
  BALANCE_ADJUST: '人工调整余额',
  ORDER_REFUND: '订单退款',
  LEDGER_REVERSE: '流水冲正',
  MENU_PUBLISH: '菜单发布',
  PRICING_PUBLISH: '定价发布',
  REVIEW_RESOLVE: '复核处理'
}

async function load() {
  loading.value = true
  try {
    const query = `?pageNo=1&pageSize=100${action.value ? `&action=${action.value}` : ''}`
    const page = await api.get<PageResult<AuditLog>>(`/meal/audit-logs${query}`)
    rows.value = page.list
    total.value = page.total
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">审计日志</div>
    <div class="page-desc">
      发放、调整、退款、冲正、发布与复核都会留痕。日志只增不改，用于事后追溯。
    </div>

    <div class="toolbar">
      <el-select v-model="action" placeholder="全部动作" clearable style="width: 180px" @change="load">
        <el-option v-for="(label, key) in actions" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="#" width="80" />
        <el-table-column label="动作" width="150">
          <template #default="{ row }">{{ actions[row.action] || row.action }}</template>
        </el-table-column>
        <el-table-column label="对象" width="220">
          <template #default="{ row }">{{ row.targetType }} #{{ row.targetId }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="说明" min-width="280" />
        <el-table-column label="操作人" width="150">
          <template #default="{ row }">{{ row.operatorType }} {{ row.operatorId ?? '' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
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
