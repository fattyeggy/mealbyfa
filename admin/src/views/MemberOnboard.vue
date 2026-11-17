<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const loading = ref(false)
const result = ref<any>(null)
const form = reactive<any>({
  phone: '', nickname: '',
  goal: 'cut', sex: 'male', age: 30, heightCm: 175, weightKg: 75,
  dailyActivity: 'sedentary', workoutsPerWeek: 0, workoutMinutes: 60,
  workoutType: 'strength', workoutIntensity: 'medium',
  healthFlags: ['none'], allergies: [], dietRestrictions: [],
  orderedMeals: ['breakfast', 'lunch', 'dinner']
})

async function submit() {
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请输入 11 位手机号')
    return
  }
  loading.value = true
  try {
    const profile = { ...form, privacyAccepted: true, birthDate: null }
    delete profile.phone
    delete profile.nickname
    result.value = await api.post<any>('/meal/members/onboard', {
      phone: form.phone,
      nickname: form.nickname,
      profile
    })
    if (result.value.blocked) {
      ElMessage.warning('该档案需人工咨询：' + result.value.reason)
    } else {
      ElMessage.success(`建档完成：蛋白档位 ${result.value.proteinTier}g / ${result.value.kcal} kcal`)
    }
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-title">会员建档</div>
    <div class="page-desc">
      用户只需提供微信手机号。建档后，用户在微信里输入同一手机号即可认领档案与余额。
    </div>

    <div class="panel form-panel">
      <el-form label-width="110px">
        <el-form-item label="手机号" required>
          <el-input v-model="form.phone" placeholder="11 位手机号" style="width: 240px" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可留空" style="width: 240px" />
        </el-form-item>
        <el-form-item label="目标">
          <el-radio-group v-model="form.goal">
            <el-radio value="cut">减脂</el-radio>
            <el-radio value="maintain">维持</el-radio>
            <el-radio value="bulk">增肌</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio value="male">男</el-radio>
            <el-radio value="female">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄 / 身高 / 体重">
          <el-input-number v-model="form.age" :min="18" :max="65" />
          <span class="gap" />
          <el-input-number v-model="form.heightCm" :min="130" :max="220" />
          <span class="gap" />
          <el-input-number v-model="form.weightKg" :min="35" :max="200" />
        </el-form-item>
        <el-form-item label="日常活动">
          <el-select v-model="form.dailyActivity" style="width: 240px">
            <el-option label="久坐办公" value="sedentary" />
            <el-option label="经常走动" value="light" />
            <el-option label="体力劳动" value="active" />
          </el-select>
        </el-form-item>
        <el-form-item label="每周运动">
          <el-input-number v-model="form.workoutsPerWeek" :min="0" :max="14" />
          <span class="gap" />
          <el-input-number v-model="form.workoutMinutes" :min="10" :max="180" />
          <span class="gap" />
          <el-select v-model="form.workoutIntensity" style="width: 120px">
            <el-option label="低强度" value="low" />
            <el-option label="中强度" value="medium" />
            <el-option label="高强度" value="high" />
          </el-select>
        </el-form-item>
        <el-form-item label="健康筛查">
          <el-select v-model="form.healthFlags" multiple style="width: 360px">
            <el-option label="以上皆无" value="none" />
            <el-option label="孕期" value="pregnant" />
            <el-option label="哺乳期" value="breastfeeding" />
            <el-option label="糖尿病" value="diabetes" />
            <el-option label="肾病" value="kidney_disease" />
            <el-option label="痛风" value="gout" />
            <el-option label="高血压" value="hypertension" />
            <el-option label="进食障碍史" value="eating_disorder_history" />
          </el-select>
        </el-form-item>
        <el-form-item label="订购餐次">
          <el-checkbox-group v-model="form.orderedMeals">
            <el-checkbox value="breakfast">早餐</el-checkbox>
            <el-checkbox value="lunch">午餐</el-checkbox>
            <el-checkbox value="dinner">晚餐</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="loading" @click="submit">建档</el-button>

      <div v-if="result && !result.blocked" class="result">
        <div>会员 ID：{{ result.memberId }}</div>
        <div>蛋白档位：{{ result.proteinTier }}g / 热量 {{ result.kcal }} kcal / 碳水 {{ result.carbG }}g / 脂肪 {{ result.fatG }}g</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-panel { max-width: 760px; }
.gap { display: inline-block; width: 8px; }
.result { margin-top: 16px; padding: 12px 16px; background: #f2f7ec; border-radius: 10px; line-height: 1.8; }
</style>
