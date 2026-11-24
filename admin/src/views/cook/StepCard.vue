<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, uploadFile } from '../../api'
import AuthImg from './AuthImg.vue'
import { compressImage, CookStep, fmtDuration, HEATS } from './cook'

// 一个步骤：做了什么、火候、计时、拍照
const props = defineProps<{ step: CookStep; index: number; total: number; recordId: number }>()
const emit = defineEmits<{
  (e: 'update', patch: Partial<CookStep>): void
  (e: 'remove'): void
  (e: 'move', delta: number): void
}>()

const running = ref(false)
const startedAt = ref(0)
const now = ref(Date.now())
const uploading = ref(0)
let tick: number | undefined
let wakeLock: any = null

const shown = computed(() => {
  const extra = running.value ? Math.floor((now.value - startedAt.value) / 1000) : 0
  return (props.step.durationSec || 0) + extra
})

const customHeat = computed(() => !!props.step.heat && !HEATS.includes(props.step.heat))

async function start() {
  running.value = true
  startedAt.value = Date.now()
  now.value = startedAt.value
  tick = window.setInterval(() => (now.value = Date.now()), 500)
  // 计时时保持屏幕常亮，手上沾油不用反复解锁
  try {
    wakeLock = await (navigator as any).wakeLock?.request('screen')
  } catch {
    wakeLock = null
  }
}

function stop() {
  if (!running.value) return
  const add = Math.floor((Date.now() - startedAt.value) / 1000)
  running.value = false
  window.clearInterval(tick)
  releaseWake()
  emit('update', { durationSec: (props.step.durationSec || 0) + add })
}

function releaseWake() {
  try {
    wakeLock?.release?.()
  } catch {
    /* 忽略 */
  }
  wakeLock = null
}

async function editDuration() {
  stop()
  try {
    const { value } = await ElMessageBox.prompt('这一步用了几分钟？', '修改用时', {
      inputValue: String(Math.round(((props.step.durationSec || 0) / 60) * 10) / 10),
      inputPattern: /^\d+(\.\d+)?$/,
      inputErrorMessage: '请输入数字',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    emit('update', { durationSec: Math.round(Number(value) * 60) })
  } catch {
    /* 取消 */
  }
}

function setHeat(h: string) {
  emit('update', { heat: props.step.heat === h ? '' : h })
}

async function onFiles(ev: Event) {
  const input = ev.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    uploading.value++
    try {
      const blob = await compressImage(file)
      const id = await uploadFile<number>(`/meal/cook-records/${props.recordId}/photos`, blob, 'step.jpg')
      emit('update', { photoIds: [...(props.step.photoIds || []), id] })
    } catch (e: any) {
      ElMessage.error(e.message || '上传失败')
    } finally {
      uploading.value--
    }
  }
}

async function removePhoto(id: number) {
  try {
    await ElMessageBox.confirm('删除这张照片？', '删除照片', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return
  }
  emit('update', { photoIds: (props.step.photoIds || []).filter((p) => p !== id) })
  try {
    await api.delete(`/meal/cook-records/photos/${id}`)
  } catch {
    /* 已从步骤移除，库里残留不影响使用 */
  }
}

onBeforeUnmount(() => {
  if (running.value) stop()
  window.clearInterval(tick)
  releaseWake()
})

defineExpose({ stop })
</script>

<template>
  <div class="ck-card step">
    <div class="ck-row ck-between">
      <strong>第 {{ index + 1 }} 步</strong>
      <div class="ck-row">
        <button class="ck-icon-btn" :disabled="index === 0" aria-label="上移" @click="emit('move', -1)">↑</button>
        <button class="ck-icon-btn" :disabled="index === total - 1" aria-label="下移" @click="emit('move', 1)">↓</button>
        <button class="ck-icon-btn" aria-label="删除这一步" @click="emit('remove')">✕</button>
      </div>
    </div>

    <textarea
      class="ck-textarea"
      rows="2"
      :value="step.text"
      placeholder="做了什么，如：鸡胸切 3cm 块，加酱油糖腌"
      aria-label="步骤内容"
      @input="emit('update', { text: ($event.target as HTMLTextAreaElement).value })"
    />

    <div class="ck-field">
      <span class="ck-label">火候</span>
      <div class="ck-pills">
        <button v-for="h in HEATS" :key="h" class="ck-pill" :class="{ on: step.heat === h }" @click="setHeat(h)">{{ h }}</button>
      </div>
      <input
        class="ck-input"
        :value="customHeat ? step.heat : ''"
        placeholder="或自己写，如 烤箱 200℃ / 油温六成热"
        aria-label="火候说明"
        @change="emit('update', { heat: ($event.target as HTMLInputElement).value.trim() })"
      />
    </div>

    <div class="timer" :class="{ running }">
      <button class="time ck-num" aria-label="修改用时" @click="editDuration">{{ fmtDuration(shown) }}</button>
      <button v-if="!running" class="ck-btn" @click="start">{{ step.durationSec ? '继续计时' : '开始计时' }}</button>
      <button v-else class="ck-btn lime" @click="stop">停止</button>
    </div>

    <div class="photos">
      <div v-for="id in step.photoIds || []" :key="id" class="photo">
        <AuthImg :photo-id="id" />
        <button class="photo-del" aria-label="删除照片" @click="removePhoto(id)">✕</button>
      </div>
      <label class="photo add">
        <input type="file" accept="image/*" capture="environment" multiple hidden @change="onFiles" />
        <span>{{ uploading ? `上传中 ${uploading}…` : '＋ 拍照' }}</span>
      </label>
    </div>
  </div>
</template>

<style scoped>
.step {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.timer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #eef3ee;
  border-radius: 14px;
  padding: 8px 8px 8px 14px;
}

.timer.running {
  background: #032c0a;
}

.time {
  border: none;
  background: none;
  font-size: 30px;
  font-weight: 700;
  min-height: 44px;
  padding: 0;
}

.timer.running .time {
  color: #73c308 !important;
}

.photos {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.photo {
  position: relative;
  aspect-ratio: 1;
}

.photo.add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px dashed #c9d1c4;
  border-radius: 12px;
  color: #032c0a;
  font-weight: 600;
  cursor: pointer;
  background: #fff;
}

.photo-del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(20, 35, 28, 0.65);
  color: #fff !important;
}
</style>
