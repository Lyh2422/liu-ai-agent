<template>
  <section class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <div>
        <p class="eyebrow">欢迎回来</p>
        <h1>登录校园 AI 平台</h1>
      </div>
      <label>
        <span>用户名</span>
        <input v-model.trim="form.username" autocomplete="username" required />
      </label>
      <label>
        <span>密码</span>
        <input v-model="form.password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn primary" :disabled="loading" type="submit">{{ loading ? '登录中...' : '登录' }}</button>
      <router-link class="switch" to="/register">还没有账号？去注册</router-link>
    </form>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api'
import { setAuth } from '../stores/auth'
import { errorMessage } from '../utils/errors'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '' })

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await authApi.login(form)
    setAuth(data.token, data.user)
    router.push({ name: 'home' })
  } catch (e) {
    error.value = errorMessage(e, '用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100%; display: grid; place-items: center; padding: 24px; }
.auth-card { width: min(420px, 100%); display: grid; gap: 16px; padding: 28px; border: 1px solid var(--border); border-radius: 18px; background: var(--panel); box-shadow: var(--shadow); }
.eyebrow { color: var(--primary-strong); font-size: 13px; margin-bottom: 8px; }
h1 { font-size: 30px; }
label { display: grid; gap: 8px; color: var(--muted); font-size: 14px; }
input { width: 100%; padding: 12px 14px; }
.error { color: var(--danger); font-size: 14px; }
.switch { justify-self: center; color: var(--primary-strong); text-decoration: none; font-size: 14px; }
button:disabled { opacity: .65; cursor: progress; }
</style>
