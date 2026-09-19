<template>
  <section class="container admin-page">
    <div class="admin-head">
      <div>
        <p class="muted">管理员</p>
        <h1>用户管理</h1>
      </div>
      <input v-model.trim="keyword" placeholder="搜索用户名或学院" @keyup.enter="load" />
      <button class="btn" type="button" @click="load">搜索</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>用户</th>
            <th>资料</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <div class="identity">
                <img :src="avatarSrc(user.avatarUrl)" alt="" />
                <strong>{{ user.username }}</strong>
              </div>
            </td>
            <td>
              <input v-model="drafts[user.id].grade" placeholder="年级" />
              <input v-model="drafts[user.id].college" placeholder="学院" />
              <textarea v-model="drafts[user.id].signature" rows="2" placeholder="个人签名" />
            </td>
            <td>
              <select v-model="drafts[user.id].role">
                <option value="USER">普通用户</option>
                <option value="ADMIN">管理员</option>
              </select>
            </td>
            <td>
              <label class="toggle">
                <input v-model="drafts[user.id].enabled" type="checkbox" />
                <span>{{ drafts[user.id].enabled ? '启用' : '禁用' }}</span>
              </label>
            </td>
            <td><button class="btn primary" type="button" @click="save(user.id)">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="message" :class="ok ? 'ok' : 'error'">{{ message }}</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi, type User } from '../api'
import { avatarSrc } from '../utils/avatar'
import { errorMessage } from '../utils/errors'

const users = ref<User[]>([])
const keyword = ref('')
const drafts = reactive<Record<number, any>>({})
const message = ref('')
const ok = ref(false)

onMounted(load)

async function load() {
  const { data } = await adminApi.listUsers({ keyword: keyword.value, size: 50 })
  users.value = data.content
  users.value.forEach(seedDraft)
}

function seedDraft(user: User) {
  drafts[user.id] = {
    grade: user.grade || '',
    college: user.college || '',
    signature: user.signature || '',
    avatarUrl: user.avatarUrl || '',
    role: user.role,
    enabled: user.enabled
  }
}

async function save(id: number) {
  message.value = ''
  try {
    const { data } = await adminApi.updateUser(id, drafts[id])
    const idx = users.value.findIndex((item) => item.id === id)
    if (idx >= 0) users.value[idx] = data
    seedDraft(data)
    ok.value = true
    message.value = '用户已更新'
  } catch (e) {
    ok.value = false
    message.value = errorMessage(e, '更新失败')
  }
}
</script>

<style scoped>
.admin-page { padding: 34px 18px; display: grid; gap: 18px; }
.admin-head { display: flex; align-items: end; gap: 12px; flex-wrap: wrap; }
.admin-head h1 { font-size: 32px; }
.admin-head input { min-width: 240px; padding: 11px 14px; }
.table-wrap { overflow: auto; border: 1px solid var(--border); border-radius: 18px; background: var(--panel); box-shadow: var(--shadow); }
table { width: 100%; border-collapse: collapse; min-width: 860px; }
th, td { padding: 14px; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }
th { color: var(--muted); font-size: 13px; font-weight: 600; }
.identity { display: flex; align-items: center; gap: 10px; }
.identity img { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; border: 1px solid var(--border); }
td input, td textarea, select { width: 100%; margin-bottom: 8px; padding: 9px 10px; border: 1px solid var(--border); border-radius: 10px; background: var(--muted-surface); color: var(--text); font: inherit; }
.toggle { display: inline-flex; align-items: center; gap: 8px; color: var(--muted); }
.ok { color: var(--accent-strong); }
.error { color: var(--danger); }
</style>
