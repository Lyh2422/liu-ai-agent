<template>
  <main class="auth-page">
    <section class="auth-story">
      <router-link class="auth-brand" to="/"><span>留</span><strong>留心</strong></router-link>
      <div class="story-copy">
        <p>给校园里的心事留个位置</p>
        <h1>不用想好<br>怎么说再来。</h1>
        <blockquote>“我只是有点乱，想先找个地方讲清楚。”</blockquote>
      </div>
      <p class="story-foot">校园情感陪伴 · 同学联络 · 日常问答</p>
    </section>

    <section class="auth-form-wrap">
      <form class="auth-card" @submit.prevent="submit">
        <header><p>欢迎回来</p><h2>登录你的账号</h2><span>之前的对话和消息还在原处。</span></header>
        <label><span>用户名</span><input v-model.trim="form.username" autocomplete="username" required placeholder="输入用户名" /></label>
        <label><span>密码</span><input v-model="form.password" type="password" autocomplete="current-password" required placeholder="输入密码" /></label>
        <p v-if="error" class="form-message error" role="alert">{{ error }}</p>
        <button class="btn primary submit" :disabled="loading" type="submit">{{ loading ? '正在登录…' : '登录' }}</button>
        <p class="switch">第一次来？<router-link to="/register">创建账号</router-link></p>
      </form>
    </section>
  </main>
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
    error.value = errorMessage(e, '用户名或密码不对，再试一次')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: grid; grid-template-columns: minmax(390px, .92fr) 1.08fr; min-height: 100vh; background: rgba(255, 253, 253, .74); }
.auth-story { position: relative; display: flex; min-height: 100vh; flex-direction: column; justify-content: space-between; overflow: hidden; padding: 42px 9%; color: var(--ink); background: linear-gradient(rgba(248, 255, 235, .3), rgba(255, 237, 245, .36)), url('../assets/clover-wallpaper.jpg') center top / cover; }
.auth-story::before { content: '✦'; position: absolute; right: 12%; top: 10%; color: rgba(197, 118, 151, .36); font: 72px Georgia, serif; transform: rotate(12deg); }
.auth-story::after { content: '✤'; position: absolute; right: -30px; bottom: 8%; color: rgba(148, 204, 81, .42); font: 260px Georgia, serif; transform: rotate(-9deg); }
.auth-brand, .story-copy, .story-foot { position: relative; z-index: 1; }
.auth-brand { display: flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; }
.auth-brand span { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid #cbe9a5; border-radius: 50%; color: #6b9650; background: rgba(244, 255, 228, .9); box-shadow: 0 8px 20px rgba(127, 168, 80, .12); font: 700 20px "Kaiti SC", serif; }
.auth-brand strong { font: 700 22px "Songti SC", serif; letter-spacing: .08em; }
.story-copy > p { color: #7e9b62; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.story-copy h1 { margin-top: 18px; font-size: clamp(44px, 5vw, 68px); line-height: 1.12; }
.story-copy blockquote { max-width: 26rem; margin: 36px 0 0; padding: 15px 18px; border-left: 3px solid #a9d76c; color: #786d75; background: rgba(255, 255, 255, .48); font: 16px/1.8 "Kaiti SC", "STKaiti", serif; }
.story-foot { color: #9a7d89; font-size: 11px; letter-spacing: .09em; }
.auth-form-wrap { display: grid; place-items: center; padding: 42px; background: radial-gradient(circle at 90% 10%, rgba(248, 202, 219, .22), transparent 34%), rgba(255, 253, 253, .68); }
.auth-card { display: grid; gap: 20px; width: min(450px, 100%); padding: 42px; border: 1px solid rgba(234, 218, 226, .9); border-radius: 36px 14px 36px 14px; background: rgba(255, 255, 255, .76); box-shadow: 0 24px 65px rgba(130, 96, 112, .12); backdrop-filter: blur(16px); }
.auth-card header { margin-bottom: 8px; }
.auth-card header p { color: var(--coral-dark); font-size: 12px; font-weight: 700; letter-spacing: .1em; }
.auth-card h2 { margin-top: 8px; font-size: 36px; }
.auth-card header span { display: block; margin-top: 10px; color: var(--muted); font-size: 13px; }
label { display: grid; gap: 8px; color: var(--ink); font-size: 13px; font-weight: 700; }
input { width: 100%; padding: 13px 14px; }
.submit { width: 100%; margin-top: 2px; }
.switch { color: var(--muted); text-align: center; font-size: 13px; }
.switch a { color: var(--green); font-weight: 700; }
.form-message { padding: 10px 12px; border-radius: 9px; font-size: 13px; }
.error { color: var(--danger); background: #f7e5e0; }
@media (max-width: 760px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-story { min-height: 270px; padding: 28px 24px; }
  .story-copy h1 { font-size: 39px; }
  .story-copy > p, .story-copy blockquote, .story-foot { display: none; }
  .auth-form-wrap { padding: 34px 18px; }
  .auth-card { padding: 30px 24px; border-radius: 28px 10px 28px 10px; }
}
</style>
