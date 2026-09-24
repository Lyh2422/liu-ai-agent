<template>
  <section class="container admin-page">
    <div class="admin-head">
      <div>
        <p class="muted">校园账号</p>
        <h1>用户管理</h1>
      </div>
      <input v-model.trim="keyword" placeholder="按用户名或学院查找" @keyup.enter="search" />
      <button class="btn" type="button" :disabled="loading" @click="search">查找</button>
      <span class="user-count">{{ totalElements }} 个账号</span>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>用户</th>
            <th>唯一 ID</th>
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
              <button
                class="public-id mono"
                type="button"
                :title="`复制 ${user.publicId}`"
                @click="copyPublicId(user.publicId)"
              >
                <span>{{ user.publicId }}</span>
                <small>{{ copiedId === user.publicId ? '已复制' : '点击复制' }}</small>
              </button>
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
            <td><button class="btn primary" type="button" :disabled="savingId === user.id" @click="save(user.id)">{{ savingId === user.id ? '保存中…' : '保存' }}</button></td>
          </tr>
          <tr v-if="!users.length && !loading"><td class="empty-table" colspan="6">没有找到符合条件的账号</td></tr>
        </tbody>
      </table>
    </div>
    <footer class="pager">
      <span>第 {{ totalPages ? page + 1 : 0 }} / {{ totalPages }} 页</span>
      <div><button class="btn" :disabled="loading || page <= 0" @click="load(page - 1)">上一页</button><button class="btn" :disabled="loading || page + 1 >= totalPages" @click="load(page + 1)">下一页</button></div>
    </footer>
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
const copiedId = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(false)
const savingId = ref<number | null>(null)

onMounted(load)

async function load(targetPage = page.value) {
  loading.value = true
  message.value = ''
  try {
    const { data } = await adminApi.listUsers({ keyword: keyword.value, page: targetPage, size: 20 })
    users.value = data.content
    page.value = data.page
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
    users.value.forEach(seedDraft)
  } catch (e) {
    ok.value = false
    message.value = errorMessage(e, '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() { void load(0) }

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
  savingId.value = id
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

async function copyPublicId(publicId: string) {
  if (!publicId) return
  try {
    await navigator.clipboard.writeText(publicId)
    copiedId.value = publicId
    window.setTimeout(() => {
      if (copiedId.value === publicId) copiedId.value = ''
    }, 1600)
  } catch (e) {
    ok.value = false
    message.value = errorMessage(e, '复制失败，请手动选择 ID')
  } finally { savingId.value = null }
}
</script>

<style scoped>
.admin-page { padding: 34px 18px; display: grid; gap: 18px; }
.admin-head { display: flex; align-items: end; gap: 12px; flex-wrap: wrap; }
.admin-head h1 { font-size: 32px; }
.admin-head input { min-width: 240px; padding: 11px 14px; }
.user-count { margin-left: auto; color: var(--muted); font-size: 12px; }
.table-wrap { overflow: auto; border: 1px solid var(--border); border-radius: 4px 20px 4px 20px; background: var(--panel); box-shadow: var(--shadow); }
table { width: 100%; border-collapse: collapse; min-width: 1040px; }
th, td { padding: 14px; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }
th { color: var(--muted); font-size: 13px; font-weight: 600; }
.identity { display: flex; align-items: center; gap: 10px; }
.identity img { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; border: 1px solid var(--border); }
.public-id { display: grid; gap: 4px; min-width: 142px; padding: 10px 12px; text-align: left; border: 1px solid rgba(200, 107, 63, .24); border-radius: 12px; color: var(--primary-strong); cursor: pointer; background: linear-gradient(135deg, rgba(248, 230, 208, .72), rgba(255, 253, 249, .92)); transition: transform .15s ease, border-color .15s ease; }
.public-id:hover { transform: translateY(-1px); border-color: rgba(165, 79, 45, .52); }
.public-id span { font-weight: 700; letter-spacing: .045em; }
.public-id small { color: var(--muted); font: 10px ui-sans-serif, system-ui, sans-serif; }
td input, td textarea, select { width: 100%; margin-bottom: 8px; padding: 9px 10px; border: 1px solid var(--border); border-radius: 10px; background: var(--muted-surface); color: var(--text); font: inherit; }
.toggle { display: inline-flex; align-items: center; gap: 8px; color: var(--muted); }
.empty-table { padding: 52px; color: var(--muted); text-align: center; }
.pager { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--muted); font-size: 12px; }
.pager > div { display: flex; gap: 8px; }
.pager .btn { min-height: 36px; padding: 7px 12px; }
.ok { color: var(--accent-strong); }
.error { color: var(--danger); }
</style>
