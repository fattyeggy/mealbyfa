<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminName, adminRoleLabel, clearToken } from '../api'

const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/menus', label: '每周菜单', hint: '发布与检查' },
  { path: '/catalog', label: '菜品与食材', hint: '版本与 BOM' },
  { path: '/cook', label: '做菜记录', hint: '手机边做边记' },
  { path: '/reviews', label: '复核事项', hint: '待人工确认' },
  { path: '/pricing', label: '定价版本', hint: '价格与替换' },
  { path: '/orders', label: '订单', hint: '锁定与配送' },
  { path: '/kitchen', label: '厨房生产', hint: '批次与领料' },
  { path: '/cost', label: '成本报表', hint: '三层成本' },
  { path: '/members', label: '会员建档', hint: '按手机号建档' },
  { path: '/users', label: '用户与余额', hint: 'credit 与流水' },
  { path: '/feedback', label: '完餐反馈', hint: '进食率与饱腹感' },
  { path: '/audit', label: '审计日志', hint: '敏感操作留痕' }
]

const title = computed(() => (route.meta.title as string) || '运营后台')

// 昵称与角色同名（如「超级管理员」）时不重复显示
const displayName = computed(() => {
  const name = adminName()
  const role = adminRoleLabel()
  return name === role ? role : `${name} · ${role}`
})

function logout() {
  clearToken()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <aside class="side">
      <div class="brand">
        <img class="brand-logo" src="../assets/brand/meal-by-fa-logo.png" alt="发发的餐" />
        <div class="brand-name">发发的餐</div>
        <div class="brand-en">meal by fa · ops</div>
      </div>
      <nav class="nav">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-hint">{{ item.hint }}</span>
        </router-link>
      </nav>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="topbar-title">{{ title }}</div>
        <div class="topbar-right">
          <span class="admin">{{ displayName }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </header>
      <section class="content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.side {
  width: 236px;
  background: linear-gradient(180deg, #1f5b40 0%, #16432f 100%);
  color: #eef3ee;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  gap: 26px;
  flex-shrink: 0;
}

.brand-small {
  font-size: 9px;
  letter-spacing: 2px;
  opacity: 0.7;
}

.brand-logo {
  width: 108px;
  height: auto;
  margin-bottom: 6px;
  filter: brightness(0) invert(1);
}

.brand-en {
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 2px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  margin-top: 4px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 10px;
  color: #cfe0d5;
  text-decoration: none;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.nav-label {
  font-size: 14px;
  font-weight: 600;
}

.nav-hint {
  font-size: 11px;
  opacity: 0.65;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 60px;
  background: rgba(255, 255, 255, 0.8);
  border-bottom: 1px solid #e9ede7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.topbar-title {
  font-size: 15px;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin {
  font-size: 13px;
  color: #6d7f75;
}

.content {
  padding: 22px 24px 40px;
}
</style>
