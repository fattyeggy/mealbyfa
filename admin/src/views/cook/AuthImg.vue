<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchBlobUrl } from '../../api'

// 照片接口需要登录凭证，<img src> 带不了请求头，所以先取成 blob 再显示
const props = defineProps<{ photoId: number; alt?: string }>()
const src = ref('')
const failed = ref(false)

async function load() {
  release()
  failed.value = false
  try {
    src.value = await fetchBlobUrl(`/meal/cook-records/photos/${props.photoId}`)
  } catch {
    failed.value = true
  }
}

function release() {
  if (src.value) URL.revokeObjectURL(src.value)
  src.value = ''
}

watch(() => props.photoId, load, { immediate: true })
onBeforeUnmount(release)
</script>

<template>
  <img v-if="src" :src="src" :alt="alt || '步骤照片'" class="auth-img" />
  <div v-else class="auth-img ph">{{ failed ? '加载失败' : '…' }}</div>
</template>

<style scoped>
.auth-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 12px;
}

.ph {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef3ee;
  color: #556a5e;
  font-size: 13px;
}
</style>
