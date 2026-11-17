<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, PageResult } from '../api'

interface WeekMenu {
  id: number
  themeName: string
  region: string
  startDate: string
  endDate: string
  version: string
  status: string
  publishTime?: string
  standardCost?: number
}

const router = useRouter()
const loading = ref(false)
const rows = ref<WeekMenu[]>([])
const status = ref('')
const total = ref(0)

const createVisible = ref(false)
const creating = ref(false)
const form = reactive({ themeName: '', region: '新乡', startDate: '', version: 'V1.0' })

const checkVisible = ref(false)
const checkResult = ref<any>(null)
const checking = ref(false)

const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }

async function load() {
  loading.value = true
  try {
    const query = `?pageNo=1&pageSize=50${status.value ? `&status=${status.value}` : ''}`
    const page = await api.get<PageResult<WeekMenu>>(`/meal/menus${query}`)
    rows.value = page.list
    total.value = page.total
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.themeName = ''
  form.startDate = ''
  form.version = 'V1.' + (rows.value.length + 1)
  createVisible.value = true
}

async function create() {
  if (!form.themeName || !form.startDate) {
    ElMessage.warning('请填写主题与开始日期')
    return
  }
  creating.value = true
  try {
    const id = await api.post<number>('/meal/menus', form)
    ElMessage.success('已创建草稿菜单')
    createVisible.value = false
    await load()
    router.push(`/menus/${id}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

async function check(row: WeekMenu) {
  checking.value = true
  checkVisible.value = true
  checkResult.value = null
  try {
    checkResult.value = await api.get<any>(`/meal/menus/${row.id}/check`)
  } catch (e: any) {
    ElMessage.error(e.message)
    checkVisible.value = false
  } finally {
    checking.value = false
  }
}

async function publish(row: WeekMenu) {
  try {
    await ElMessageBox.confirm(
      '发布后菜单将冻结，不能再原地修改。确认发布？',
      '发布菜单',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await api.post(`/meal/menus/${row.id}/publish`)
    ElMessage.success('已发布')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

const statusType = (s: string) => (s === 'PUBLISHED' ? 'success' : s === 'DRAFT' ? 'warning' : 'info')

onMounted(load)
</script>

<template>
  <div>
    <div class="page-title">每周菜单</div>
    <div class="page-desc">菜单是内容与生产的起点。发布前必须通过检查，发布后不可原地修改。</div>

    <div class="toolbar">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" @click="openCreate">新建周菜单</el-button>
      <span class="muted">共 {{ total }} 个</span>
    </div>

    <div class="panel">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="themeName" label="主题" min-width="140" />
        <el-table-column prop="region" label="地区" width="100" />
        <el-table-column label="起止" width="190">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishTime ? row.publishTime.replace('T', ' ').slice(0, 16) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/menus/${row.id}`)">详情</el-button>
            <el-button link type="primary" @click="check(row)">发布前检查</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="publish(row)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建周菜单" width="460px">
      <el-form label-width="90px">
        <el-form-item label="主题">
          <el-input v-model="form.themeName" placeholder="如：东京主题周" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.region" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="必须是周一" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="发布前检查" width="640px">
      <div v-loading="checking">
        <template v-if="checkResult">
          <div class="check-head">
            <el-tag :type="checkResult.passed ? 'success' : 'danger'">
              {{ checkResult.passed ? '通过' : '未通过' }}
            </el-tag>
            <span class="muted">标准成本 ¥{{ checkResult.standardCost }}</span>
          </div>
          <el-table :data="checkResult.items" size="small" style="margin-top: 12px">
            <el-table-column prop="name" label="检查项" width="220" />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.status === 'PASS' ? 'success' : row.status === 'FAIL' ? 'danger' : 'info'"
                >
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
.muted {
  color: #8a9a91;
  font-size: 12px;
}

.check-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
