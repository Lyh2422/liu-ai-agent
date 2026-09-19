<template>
  <section class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <div>
        <p class="eyebrow">创建账号</p>
        <h1>注册普通用户</h1>
      </div>
      <label><span>用户名</span><input v-model.trim="form.username" required maxlength="32" /></label>
      <label><span>密码</span><input v-model="form.password" type="password" required minlength="8" maxlength="72" /></label>
      <div class="two">
        <label><span>年级</span><input v-model.trim="form.grade" maxlength="32" placeholder="2026级" /></label>
        <label><span>学院</span><input v-model.trim="form.college" maxlength="80" placeholder="计算机学院" /></label>
      </div>
      <label><span>个人签名</span><textarea v-model.trim="form.signature" maxlength="300" rows="3" /></label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn primary" :disabled="loading" type="submit">{{ loading ? '注册中...' : '注册并去登录' }}</button>
      <router-link class="switch" to="/login">已有账号？去登录</router-link>
    </form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api'
import { errorMessage } from '../utils/errors'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', grade: '', college: '', signature: '' })

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await authApi.register(form)
    router.push({ name: 'login' })
  } catch (e) {
    error.value = errorMessage(e, '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100%; display: grid; place-items: center; padding: 24px; }
.auth-card { width: min(520px, 100%); display: grid; gap: 15px; padding: 28px; border: 1px solid var(--border); border-radius: 18px; background: var(--panel); box-shadow: var(--shadow); }
.eyebrow { color: var(--primary-strong); font-size: 13px; margin-bottom: 8px; }
h1 { font-size: 30px; }
label { display: grid; gap: 8px; color: var(--muted); font-size: 14px; }
input, textarea { width: 100%; padding: 12px 14px; resize: vertical; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.error { color: var(--danger); font-size: 14px; }
.switch { justify-self: center; color: var(--primary-strong); text-decoration: none; font-size: 14px; }
button:disabled { opacity: .65; cursor: progress; }
@media (max-width: 560px) { .two { grid-template-columns: 1fr; } }
</style>
