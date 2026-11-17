<script setup lang="ts">
const props = defineProps<{ current: 'home' | 'plan' | 'orders' | 'me' }>()

const items = [
  { key: 'home', label: '首页', path: '/pages/index/index' },
  { key: 'plan', label: '计划', path: '/pages/plan/index' },
  { key: 'orders', label: '记录', path: '/pages/orders/index' },
  { key: 'me', label: '我的', path: '/pages/me/index' }
]

function go(item: { key: string; path: string }) {
  if (item.key === props.current) return
  uni.reLaunch({ url: item.path })
}
</script>

<template>
  <view class="tabbar">
    <view
      v-for="item in items"
      :key="item.key"
      class="tabbar-item"
      :class="{ active: item.key === current }"
      @click="go(item)"
    >
      <view class="dot" />
      <text class="tabbar-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<style scoped>
.tabbar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: 520px;
  height: calc(68px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid #EEF3EE;
  display: flex;
  align-items: center;
  justify-content: space-around;
  backdrop-filter: blur(8px);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 18px;
  height: 3px;
  border-radius: 2px;
  background: transparent;
}

.tabbar-item.active .dot {
  background: #032C0A;
}

.tabbar-label {
  font-size: 12px;
  color: #556A5E;
}

.tabbar-item.active .tabbar-label {
  color: #1c3b2e;
  font-weight: 600;
}
</style>
