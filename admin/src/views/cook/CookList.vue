<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, PageResult } from '../../api'
import { CookSummary } from './cook'
import './cook.css'

const router = useRouter()
const list = ref<CookSummary[]>([])
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const busy = ref(false)

async function load() {
  loading.value = true
  try {
    const q = keyword.value.trim() ? `&keyword=${encodeURIComponent(keyword.value.trim())}` : ''
    const page = await api.get<PageResult<CookSummary>>(`/meal/cook-records?pageNo=1&pageSize=50${q}`)
    list.value = page.list
    total.value = page.total
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function create() {
  let title = ''
  try {
    const r = await ElMessageBox.prompt('今天做的是什么？', '新建一锅', {
      confirmButtonText: '开始记录',
      cancelButtonText: '取消',
      inputPlaceholder: '如 照烧鸡腿饭',
      inputPattern: /\S/,
      inputErrorMessage: '请填写名字'
    })
    title = r.value.trim()
  } catch {
    return
  }
  busy.value = true
  try {
    const id = await api.post<number>('/meal/cook-records', { title })
    router.push(`/cook/${id}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function again(item: CookSummary) {
  busy.value = true
  try {
    const id = await api.post<number>(`/meal/cook-records/${item.id}/copy`)
    ElMessage.success('已复制食材和步骤，做好后重新称熟重')
    router.push(`/cook/${id}`)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function remove(item: CookSummary) {
  try {
    await ElMessageBox.confirm(`删除「${item.title}」（${item.cookDate}）这条记录和它的照片？已同步的菜品版本不受影响。`, '删除记录', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await api.delete(`/meal/cook-records/${item.id}`)
    await load()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="ck">
    <header class="ck-head">
      <div class="ck-head-row">
        <button class="ck-back" aria-label="返回运营后台" @click="router.push('/catalog')">‹</button>
        <h1 class="h1 ck-grow">做菜记录</h1>
      </div>
      <div class="ck-row" style="margin-top: 6px">
        <input v-model="keyword" class="ck-input ck-grow" placeholder="搜索菜名" aria-label="搜索菜名" @keyup.enter="load" />
        <button class="ck-btn soft" @click="load">搜索</button>
      </div>
    </header>

    <section class="ck-body">
      <div v-if="loading" class="ck-empty">加载中…</div>
      <div v-else-if="!list.length" class="ck-empty">还没有记录。下次做饭时点下面的按钮，边做边记。</div>
      <div v-for="item in list" :key="item.id" class="ck-card item">
        <button class="open" @click="router.push(`/cook/${item.id}`)">
          <span class="t">{{ item.title }}</span>
          <span class="ck-sub">
            <span>{{ item.cookDate }}</span>
            <span v-if="item.dishName">· {{ item.dishName }}</span>
            <span v-if="item.status === 'SYNCED'" class="ck-chip ok">已同步</span>
            <span v-else class="ck-chip">未同步</span>
          </span>
        </button>
        <div class="ck-row ck-between">
          <button class="ck-link" :disabled="busy" @click="again(item)">再做一锅</button>
          <button class="ck-link danger" @click="remove(item)">删除</button>
        </div>
      </div>
      <div v-if="total > list.length" class="ck-muted" style="text-align: center">只显示最近 {{ list.length }} 条，可搜索菜名</div>
    </section>

    <footer class="ck-footer">
      <button class="ck-btn block" :disabled="busy" @click="create">＋ 新建一锅</button>
    </footer>
  </div>
</template>

<style scoped>
.h1 {
  font-size: 22px;
  font-weight: 700;
  margin: 0;
}

.item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.open {
  border: none;
  background: none;
  text-align: left;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 44px;
}

.open .t {
  font-size: 17px;
  font-weight: 600;
}
</style>
