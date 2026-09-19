<template>
  <section class="container profile-page">
    <div class="panel">
      <div class="profile-head">
        <img :src="avatarSrc(authState.user?.avatarUrl)" alt="" />
        <div>
          <p class="muted">个人资料</p>
          <h1>{{ authState.user?.username }}</h1>
          <span class="role">{{ authState.user?.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
        </div>
      </div>
      <form class="form" @submit.prevent="save">
        <div class="two">
          <label><span>年级</span><input v-model.trim="form.grade" maxlength="32" /></label>
          <label><span>学院</span><input v-model.trim="form.college" maxlength="80" /></label>
        </div>
        <label><span>个人签名</span><textarea v-model.trim="form.signature" maxlength="300" rows="4" /></label>
        <label><span>头像</span><input type="file" accept="image/*" @change="upload" /></label>
        <p v-if="message" :class="ok ? 'ok' : 'error'">{{ message }}</p>
        <button class="btn primary" :disabled="loading" type="submit">保存资料</button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { authApi } from '../api'
import { authState, loadCurrentUser, setUser } from '../stores/auth'
import { avatarSrc } from '../utils/avatar'
import { errorMessage } from '../utils/errors'

const loading = ref(false)
const message = ref('')
const ok = ref(false)
const form = reactive({ grade: '', college: '', signature: '' })

onMounted(async () => {
  await loadCurrentUser()
  fill()
})

function fill() {
  form.grade = authState.user?.grade || ''
  form.college = authState.user?.college || ''
  form.signature = authState.user?.signature || ''
}

async function save() {
  loading.value = true
  message.value = ''
  try {
    const { data } = await authApi.updateProfile(form)
    setUser(data)
    ok.value = true
    message.value = '资料已保存'
  } catch (e) {
    ok.value = false
    message.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  loading.value = true
  message.value = ''
  try {
    const { data } = await authApi.uploadAvatar(file)
    setUser(data)
    ok.value = true
    message.value = '头像已更新'
  } catch (e) {
    ok.value = false
    message.value = errorMessage(e, '头像上传失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.profile-page { padding: 34px 18px; }
.panel { display: grid; gap: 24px; padding: 26px; border: 1px solid var(--border); border-radius: 18px; background: var(--panel); box-shadow: var(--shadow); }
.profile-head { display: flex; align-items: center; gap: 18px; }
.profile-head img { width: 76px; height: 76px; border-radius: 50%; object-fit: cover; border: 1px solid var(--border); background: var(--muted-surface); }
h1 { font-size: 32px; }
.role { display: inline-block; margin-top: 8px; color: var(--primary-strong); font-size: 13px; }
.form { display: grid; gap: 16px; }
.two { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
label { display: grid; gap: 8px; color: var(--muted); font-size: 14px; }
input, textarea { width: 100%; padding: 12px 14px; resize: vertical; }
.ok { color: var(--accent-strong); }
.error { color: var(--danger); }
@media (max-width: 640px) { .two { grid-template-columns: 1fr; } }
</style>
