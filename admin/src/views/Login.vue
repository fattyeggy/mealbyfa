<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号与密码')
    return
  }
  loading.value = true
  try {
    await login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/menus')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="card">
      <img class="brand-logo" src="../assets/brand/meal-by-fa-logo.png" alt="发发的餐" />
      <h1 class="title">发发的餐 · 运营后台</h1>
      <div class="brand-en">meal by fa</div>
      <p class="desc">菜单、定价、订单、生产与成本都在这里操作。</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
      </el-form>
      <p class="hint">开发环境默认账号 admin / admin123，部署前请改密。</p>
    </div>
  </div>
</template>

<style scoped>
.brand-logo {
  width: 140px;
  height: auto;
  margin-bottom: 8px;
}

.brand-en {
  font-size: 11px;
  letter-spacing: 3px;
  color: #7c8b80;
  margin-bottom: 18px;
}
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #1f5b40 0%, #14402c 100%);
}

.card {
  width: 380px;
  background: #fff;
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
}

.brand {
  font-size: 10px;
  letter-spacing: 2px;
  color: #8a9a91;
}

.title {
  font-size: 22px;
  margin: 8px 0 6px;
}

.desc {
  font-size: 12px;
  color: #8a9a91;
  margin-bottom: 20px;
}

.hint {
  font-size: 11px;
  color: #a9b5ad;
  margin-top: 16px;
}
</style>
