<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const start = ref('')
const end = ref('')
const report = ref<any>(null)
const rules = ref<any[]>([])
const loading = ref(false)
const editing = ref<Record<string, number>>({})

function iso(offsetDays: number) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    if (start.value) params.set('start', start.value)
    if (end.value) params.set('end', end.value)
    report.value = await api.get<any>(`/meal/cost/report?${params.toString()}`)
    rules.value = await api.get<any[]>('/meal/cost/rules')
    editing.value = {}
    for (const rule of rules.value) editing.value[rule.ruleKey] = Number(rule.ruleValue)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function saveRule(rule: any) {
  try {
    await api.put(`/meal/cost/rules/${rule.ruleKey}`, { value: editing.value[rule.ruleKey] })
    ElMessage.success('已更新')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function pct(value: any) {
  return (Number(value || 0) * 100).toFixed(2) + '%'
}

onMounted(() => {
  // 默认看最近一周 + 未来两周，便于覆盖已排期的服务日
  start.value = iso(-7)
  end.value = iso(14)
  load()
})
</script>

<template>
  <div>
    <div class="page-title">成本报表</div>
    <div class="page-desc">
      三层成本：食材 / 包装分摊 / 配送分摊，另计支付手续费与固定成本。参数为初始假设，需经营方确认。
    </div>

    <div class="toolbar">
      <el-date-picker v-model="start" type="date" value-format="YYYY-MM-DD" placeholder="开始" />
      <span>至</span>
      <el-date-picker v-model="end" type="date" value-format="YYYY-MM-DD" placeholder="结束" />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
    </div>

    <div v-if="report" class="cards">
      <div class="kpi panel">
        <div class="kpi-label">实收</div>
        <div class="kpi-value">¥{{ report.totals.revenue }}</div>
      </div>
      <div class="kpi panel">
        <div class="kpi-label">总成本</div>
        <div class="kpi-value">¥{{ report.totals.totalCost }}</div>
      </div>
      <div class="kpi panel">
        <div class="kpi-label">毛利</div>
        <div class="kpi-value">¥{{ report.totals.grossProfit }}</div>
      </div>
      <div class="kpi panel">
        <div class="kpi-label">食材成本率</div>
        <div class="kpi-value">{{ pct(report.totals.foodCostRate) }}</div>
      </div>
    </div>

    <div class="panel">
      <el-table :data="report?.days || []" size="small" stripe>
        <el-table-column prop="serviceDate" label="服务日" width="120" />
        <el-table-column prop="orders" label="订单" width="70" />
        <el-table-column prop="meals" label="餐次" width="70" />
        <el-table-column label="实收" width="100">
          <template #default="{ row }">¥{{ row.revenue }}</template>
        </el-table-column>
        <el-table-column label="食材" width="100">
          <template #default="{ row }">¥{{ row.foodCost }}</template>
        </el-table-column>
        <el-table-column label="包装" width="90">
          <template #default="{ row }">¥{{ row.packageCost }}</template>
        </el-table-column>
        <el-table-column label="配送" width="90">
          <template #default="{ row }">¥{{ row.deliveryCost }}</template>
        </el-table-column>
        <el-table-column label="手续费" width="90">
          <template #default="{ row }">¥{{ row.paymentFee }}</template>
        </el-table-column>
        <el-table-column label="固定" width="90">
          <template #default="{ row }">¥{{ row.fixedCost }}</template>
        </el-table-column>
        <el-table-column label="总成本" width="100">
          <template #default="{ row }">¥{{ row.totalCost }}</template>
        </el-table-column>
        <el-table-column label="毛利" width="100">
          <template #default="{ row }">¥{{ row.grossProfit }}</template>
        </el-table-column>
        <el-table-column label="总成本率" width="100">
          <template #default="{ row }">{{ pct(row.totalCostRate) }}</template>
        </el-table-column>
        <el-table-column label="理论用料（已登记）" width="140">
          <template #default="{ row }">¥{{ row.materialTheoreticalCost }}</template>
        </el-table-column>
        <el-table-column label="实际用料" width="110">
          <template #default="{ row }">¥{{ row.materialActualCost }}</template>
        </el-table-column>
        <el-table-column label="用料差异" width="110">
          <template #default="{ row }">
            <span :class="{ over: Number(row.materialDiff) > 0 }">
              {{ row.materialDiff === null || row.materialDiff === undefined ? '未登记' : '¥' + row.materialDiff }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="report && !report.days.length" description="该区间没有已锁订单" />
    </div>

    <div class="section-title">成本参数</div>
    <div class="panel">
      <el-table :data="rules" size="small">
        <el-table-column prop="ruleKey" label="参数" width="240" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="值" width="180">
          <template #default="{ row }">
            <el-input-number v-model="editing[row.ruleKey]" :min="0" :precision="4" :step="0.1" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="saveRule(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.kpi-label {
  font-size: 12px;
  color: #8a9a91;
}

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 6px;
}

.over {
  color: #b04a3a;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 22px 0 10px;
}
</style>
