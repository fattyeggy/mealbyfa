<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, downloadCsv, PageResult } from '../api'

interface Order {
  id: number
  orderNo: string
  userId: number
  serviceDate: string
  status: string
  originalAmount: number
  balanceAmount: number
  couponAmount: number
  contactName?: string
  address?: string
  lockFailReason?: string
}

const rows = ref<Order[]>([])
const loading = ref(false)
const status = ref('')
const serviceDate = ref('')
const detailVisible = ref(false)
const detail = ref<any>(null)

const statusText: Record<string, string> = {
  PENDING_LOCK: '待锁定',
  LOCKED: '已锁定',
  PURCHASE_AGG: '采购汇总',
  PRODUCING: '生产中',
  LUNCH_DELIVERING: '午餐配送中',
  LUNCH_DONE: '午餐已送达',
  DINNER_DELIVERING: '晚餐配送中',
  DINNER_DONE: '晚餐已送达',
  COMPLETED: '已完成',
  PAUSED: '已暂停',
  CANCELLED: '已取消',
  REFUNDED: '已退款'
}

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({ pageNo: '1', pageSize: '50' })
    if (status.value) params.set('status', status.value)
    if (serviceDate.value) params.set('serviceDate', serviceDate.value)
    const page = await api.get<PageResult<Order>>(`/meal/orders?${params.toString()}`)
    rows.value = page.list
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function exportDelivery() {
  try {
    await downloadCsv(`/meal/orders/export/delivery?serviceDate=${serviceDate.value}`,
      `配送清单-${serviceDate.value}.csv`)
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function open(row: Order) {
  try {
    detail.value = await api.get<any>(`/meal/orders/${row.id}`)
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function refund() {
  if (!detail.value) return
  try {
    const result = await ElMessageBox.prompt('退款原因（会记入订单）', '确认退款', {
      inputPlaceholder: '如：用户临时取消'
    })
    await api.post(`/meal/orders/${detail.value.id}/refund`, { reason: result.value })
    ElMessage.success('已退款，资金按原来源退回')
    detailVisible.value = false
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

async function markDelivery(id: number, next: string) {
  if (!detail.value) return
  try {
    await api.post(`/meal/orders/${detail.value.id}/delivery`, { deliveryBatch: batch, status: next })
    ElMessage.success('配送状态已更新')
    detail.value = await api.get<any>(`/meal/orders/${detail.value.id}`)
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">订单</div>
    <div class="page-desc">锁单由定时任务执行（每日 12:00 锁次日），失败原因会留在订单上。</div>

    <div class="toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 160px" @change="load">
        <el-option v-for="(label, key) in statusText" :key="key" :label="label" :value="key" />
      </el-select>
      <el-date-picker v-model="serviceDate" type="date" value-format="YYYY-MM-DD" placeholder="服务日" @change="load" />
      <el-button @click="load">刷新</el-button>
      <el-button :disabled="!serviceDate" @click="exportDelivery">导出配送清单</el-button>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="210" />
        <el-table-column prop="userId" label="用户" width="80" />
        <el-table-column prop="serviceDate" label="服务日" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'LOCKED' || row.status === 'COMPLETED' ? 'success' : row.status === 'PENDING_LOCK' ? 'warning' : 'info'">
              {{ statusText[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标价" width="100">
          <template #default="{ row }">¥{{ row.originalAmount }}</template>
        </el-table-column>
        <el-table-column label="实扣" width="100">
          <template #default="{ row }">¥{{ row.balanceAmount }}</template>
        </el-table-column>
        <el-table-column prop="lockFailReason" label="锁单失败原因" min-width="200" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" title="订单详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="服务日">{{ detail.serviceDate }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="标价">¥{{ detail.originalAmount }}</el-descriptions-item>
          <el-descriptions-item label="定价版本">{{ detail.pricingVersionNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="优惠券">¥{{ detail.couponAmount }}</el-descriptions-item>
          <el-descriptions-item label="余额抵扣">¥{{ detail.balanceAmount }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detail.address }}</el-descriptions-item>
        </el-descriptions>

        <div class="section">餐次</div>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="mealLabel" label="餐次" width="70" />
          <el-table-column prop="dishName" label="菜品" min-width="140" />
          <el-table-column prop="weightG" label="克重" width="80" />
          <el-table-column prop="proteinG" label="蛋白" width="80" />
          <el-table-column label="价格" width="90">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
        </el-table>

        <div class="section">配送批次</div>
        <el-table :data="detail.deliveries" size="small">
          <el-table-column prop="deliveryBatch" label="批次" width="140" />
          <el-table-column label="实际配送日" width="120">
            <template #default="{ row }">{{ row.actualDeliveryDate }}</template>
          </el-table-column>
          <el-table-column label="餐次" width="100">
            <template #default="{ row }">{{ row.deliverySlot }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-button link type="primary" @click="markDelivery(row.id, 'DELIVERING')">开始配送</el-button>
              <el-button link type="success" @click="markDelivery(row.id, 'DONE')">完成</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="detail.lockFailReason"
          type="warning"
          :closable="false"
          :title="detail.lockFailReason"
          style="margin-top: 12px"
        />

        <div class="actions">
          <el-button
            v-if="['LOCKED', 'PURCHASE_AGG', 'PRODUCING', 'LUNCH_DELIVERING', 'LUNCH_DONE', 'DINNER_DELIVERING', 'DINNER_DONE', 'COMPLETED'].includes(detail.status)"
            type="danger"
            plain
            @click="refund"
          >
            退款
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.section {
  font-size: 13px;
  font-weight: 600;
  margin: 18px 0 8px;
}

.actions {
  margin-top: 16px;
}
</style>
