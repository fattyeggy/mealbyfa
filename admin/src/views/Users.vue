<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const userId = ref<number | null>(null)
const wallet = ref<any>(null)
const ledger = ref<any[]>([])
const loading = ref(false)

const grantVisible = ref(false)
const grantForm = reactive({ amount: 100, reason: '阶段一内部体验' })

const adjustVisible = ref(false)
const adjustForm = reactive({ direction: 'IN', source: 'CASH', amount: 100, reason: '' })

const typeText: Record<string, string> = {
  CREDIT_GRANT: '发放 credit',
  TOPUP: '充值',
  ORDER_PAY: '订单支付',
  REFUND: '退款',
  ADJUST: '人工调整',
  REVERSE: '冲正'
}

async function load() {
  if (!userId.value) {
    ElMessage.warning('请输入用户 ID')
    return
  }
  loading.value = true
  try {
    wallet.value = await api.get<any>(`/meal/wallet/${userId.value}`)
    const page = await api.get<any>(`/meal/wallet/${userId.value}/ledger?pageNo=1&pageSize=20`)
    ledger.value = page.list
  } catch (e: any) {
    wallet.value = null
    ledger.value = []
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function grant() {
  try {
    await api.post(`/meal/wallet/${userId.value}/credit`, grantForm)
    ElMessage.success('已发放，用户端只显示为账户余额')
    grantVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function adjust() {
  if (!adjustForm.reason) {
    ElMessage.warning('人工调整必须填写原因')
    return
  }
  try {
    await api.post(`/meal/wallet/${userId.value}/adjust`, adjustForm)
    ElMessage.success('已调整并记入流水')
    adjustVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function reverseRow(row: any) {
  try {
    const result = await import('element-plus').then((m) =>
      m.ElMessageBox.prompt('冲正原因（生成反向流水，不修改原记录）', '冲正')
    )
    await api.post(`/meal/wallet/ledger/${row.id}/reverse`, { reason: result.value })
    ElMessage.success('已冲正')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}
</script>

<template>
  <div>
    <div class="page-title">用户与余额</div>
    <div class="page-desc">
      赠送 credit 与现金分账管理，退款按原来源退回。账本只增不改，纠错走冲正。
    </div>

    <div class="toolbar">
      <el-input-number v-model="userId" :min="1" :controls="false" placeholder="用户 ID" style="width: 140px" />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button v-if="wallet" @click="grantVisible = true">发放 credit</el-button>
      <el-button v-if="wallet" @click="adjustVisible = true">人工调整</el-button>
    </div>

    <div v-if="wallet" class="cards">
      <div class="kpi panel">
        <div class="kpi-label">总余额</div>
        <div class="kpi-value">¥{{ wallet.balance }}</div>
      </div>
      <div class="kpi panel">
        <div class="kpi-label">套餐余额（credit）</div>
        <div class="kpi-value">¥{{ wallet.creditBalance }}</div>
      </div>
      <div class="kpi panel">
        <div class="kpi-label">现金余额</div>
        <div class="kpi-value">¥{{ wallet.cashBalance }}</div>
      </div>
    </div>

    <div class="panel">
      <el-table :data="ledger" size="small" stripe>
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">{{ typeText[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'IN' ? 'success' : 'warning'">
              {{ row.direction === 'IN' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ row.source === 'CREDIT' ? '套餐' : '现金' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="余额变化" width="160">
          <template #default="{ row }">{{ row.balanceBefore }} → {{ row.balanceAfter }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column prop="operatorType" label="操作人" width="110" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.bizType !== 'REVERSE'" link type="danger" @click="reverseRow(row)">冲正</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!ledger.length" description="暂无流水" />
    </div>

    <el-dialog v-model="grantVisible" title="发放 credit" width="420px">
      <el-form label-width="90px">
        <el-form-item label="金额（元）"><el-input-number v-model="grantForm.amount" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="grantForm.reason" placeholder="必填，记入审计" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantVisible = false">取消</el-button>
        <el-button type="primary" @click="grant">发放</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="adjustVisible" title="人工调整余额" width="440px">
      <el-form label-width="90px">
        <el-form-item label="方向">
          <el-radio-group v-model="adjustForm.direction">
            <el-radio-button label="IN">调增</el-radio-button>
            <el-radio-button label="OUT">调减</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来源">
          <el-radio-group v-model="adjustForm.source">
            <el-radio-button label="CASH">现金</el-radio-button>
            <el-radio-button label="CREDIT">套餐</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额"><el-input-number v-model="adjustForm.amount" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="adjustForm.reason" placeholder="必填，记入审计" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="adjust">确认调整</el-button>
      </template>
    </el-dialog>
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
</style>
