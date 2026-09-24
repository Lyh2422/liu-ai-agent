<template>
  <main class="auth-page">
    <section class="auth-intro">
      <router-link class="auth-brand" to="/"><span>留</span><strong>留心</strong></router-link>
      <div><p>新同学登记</p><h1>先认识一下，<br>以后好好聊天。</h1><span>年级、学院和签名都可以之后再改。</span></div>
      <ul><li>每位用户会得到一个唯一 ID</li><li>用 ID 添加同学，不用公开手机号</li><li>历史对话跟随账号保存</li></ul>
    </section>

    <section class="auth-form-wrap">
      <form class="auth-card" @submit.prevent="submit">
        <header><p>创建账号</p><h2>加入留心</h2></header>
        <label><span>用户名</span><input v-model.trim="form.username" required minlength="2" maxlength="32" placeholder="2 至 32 个字符" autocomplete="username" /></label>
        <label><span>密码</span><input v-model="form.password" type="password" required minlength="8" maxlength="72" placeholder="至少 8 个字符" autocomplete="new-password" /></label>
        <div class="two">
          <label><span>年级（选填）</span><input v-model.trim="form.grade" maxlength="32" placeholder="例如：2026 级" /></label>
          <label><span>学院（选填）</span><input v-model.trim="form.college" maxlength="80" placeholder="例如：计算机学院" /></label>
        </div>
        <label><span>想写给别人的一句话（选填）</span><textarea v-model.trim="form.signature" maxlength="300" rows="3" placeholder="好友会在你的资料卡上看到" /></label>
        <p v-if="error" class="form-message" role="alert">{{ error }}</p>
        <button class="btn primary submit" :disabled="loading" type="submit">{{ loading ? '正在创建…' : '创建账号' }}</button>
        <p class="switch">已经注册？<router-link to="/login">直接登录</router-link></p>
      </form>
    </section>
  </main>
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
    error.value = errorMessage(e, '账号没有创建成功，请检查填写内容')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { display: grid; grid-template-columns: minmax(360px, .8fr) 1.2fr; min-height: 100vh; background: var(--surface); }
.auth-intro { position: relative; display: flex; min-height: 100vh; flex-direction: column; justify-content: space-between; overflow: hidden; padding: 42px 9%; color: #fffaf2; background: var(--coral-dark); }
.auth-intro::before { content: ''; position: absolute; right: -130px; top: 23%; width: 350px; height: 350px; border: 70px solid rgba(255,255,255,.055); border-radius: 50%; }
.auth-intro > * { position: relative; z-index: 1; }
.auth-brand { display: flex; align-items: center; gap: 10px; color: inherit; text-decoration: none; }
.auth-brand span { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid rgba(255,255,255,.55); border-radius: 14px 5px; font: 700 20px "Songti SC", serif; }
.auth-brand strong { font: 700 22px "Songti SC", serif; letter-spacing: .08em; }
.auth-intro > div p { color: #f1d5cb; font-size: 12px; font-weight: 700; letter-spacing: .12em; }
.auth-intro h1 { margin-top: 16px; font-size: clamp(42px, 4.5vw, 62px); line-height: 1.14; }
.auth-intro > div span { display: block; margin-top: 24px; color: #f3ddd5; line-height: 1.7; }
.auth-intro ul { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; color: #f3ddd5; font-size: 12px; }
.auth-intro li::before { content: '✓'; margin-right: 9px; color: var(--yellow); font-weight: 800; }
.auth-form-wrap { display: grid; place-items: center; padding: 40px; background: radial-gradient(circle at 100% 0, rgba(49,89,77,.12), transparent 30%), var(--surface); }
.auth-card { display: grid; gap: 16px; width: min(560px, 100%); padding-block: 20px; }
.auth-card header { margin-bottom: 3px; }
.auth-card header p { color: var(--coral-dark); font-size: 12px; font-weight: 700; letter-spacing: .1em; }
.auth-card h2 { margin-top: 7px; font-size: 35px; }
label { display: grid; gap: 7px; color: var(--ink); font-size: 13px; font-weight: 700; }
input, textarea { width: 100%; padding: 12px 13px; resize: vertical; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.submit { width: 100%; margin-top: 4px; }
.switch { color: var(--muted); text-align: center; font-size: 13px; }
.switch a { color: var(--green); font-weight: 700; }
.form-message { padding: 10px 12px; border-radius: 9px; color: var(--danger); background: #f7e5e0; font-size: 13px; }
@media (max-width: 760px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-intro { min-height: 250px; padding: 28px 24px; }
  .auth-intro h1 { font-size: 37px; }
  .auth-intro > div > span, .auth-intro ul { display: none; }
  .auth-form-wrap { padding: 34px 24px; }
}
@media (max-width: 520px) { .two { grid-template-columns: 1fr; } }
</style>
