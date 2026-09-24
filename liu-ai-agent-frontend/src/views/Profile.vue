<template>
  <section class="container profile-page">
    <div class="profile-grid">
    <div class="panel profile-panel">
      <div class="profile-head">
        <img :src="avatarSrc(authState.user?.avatarUrl)" alt="" />
        <div>
          <p class="muted">我的校园名片</p>
          <h1>{{ authState.user?.username }}</h1>
          <span class="role">{{ authState.user?.role === 'ADMIN' ? '管理员' : (authState.user?.college || '在校同学') }}</span>
        </div>
      </div>
      <button class="identity-card" type="button" @click="copyPublicId">
        <span>我的同学 ID</span>
        <strong class="mono">{{ authState.user?.publicId || '生成中…' }}</strong>
        <small>{{ copied ? '已经复制到剪贴板' : '点一下复制，发给想添加你的同学' }}</small>
      </button>
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
    <aside class="panel friends-panel">
      <div class="section-head">
        <div>
          <p class="muted">校园联络簿</p>
          <h2>我的好友 <span>{{ friends.length }}</span></h2>
        </div>
      </div>
      <form class="friend-add" @submit.prevent="addFriend">
        <input v-model.trim="friendId" class="mono" maxlength="20" placeholder="输入好友唯一 ID" />
        <button class="btn primary" :disabled="friendLoading" type="submit">添加</button>
      </form>
      <p v-if="friendMessage" :class="friendOk ? 'ok' : 'error'">{{ friendMessage }}</p>
      <div v-if="friends.length" class="friend-list">
        <article v-for="friend in friends" :key="friend.publicId" class="friend-card">
          <img :src="avatarSrc(friend.avatarUrl)" alt="" />
          <div class="friend-copy">
            <strong>{{ friend.username }}</strong>
            <span class="mono">{{ friend.publicId }}</span>
            <small>{{ friend.signature || '这位同学还没写签名' }}</small>
          </div>
          <div class="friend-actions">
            <button class="chat-button" type="button" title="发起单聊" @click="chatWith(friend.publicId)">聊</button>
            <button class="remove-button" type="button" title="删除好友" :disabled="removingId === friend.publicId" @click="removeFriend(friend)">删</button>
          </div>
        </article>
      </div>
      <div v-else class="empty-friends">
        <span>友</span>
        <p>输入对方的同学 ID。添加后，就能从这里发起单聊。</p>
      </div>
    </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, socialApi, type PublicUser } from '../api'
import { authState, loadCurrentUser, setUser } from '../stores/auth'
import { avatarSrc } from '../utils/avatar'
import { errorMessage } from '../utils/errors'

const loading = ref(false)
const message = ref('')
const ok = ref(false)
const form = reactive({ grade: '', college: '', signature: '' })
const router = useRouter()
const friends = ref<PublicUser[]>([])
const friendId = ref('')
const friendLoading = ref(false)
const friendMessage = ref('')
const friendOk = ref(false)
const copied = ref(false)
const removingId = ref('')
let friendPollTimer: number | undefined

onMounted(async () => {
  await loadCurrentUser()
  fill()
  await loadFriends()
  friendPollTimer = window.setInterval(() => loadFriends(false), 5000)
  window.addEventListener('focus', refreshFriends)
})

onBeforeUnmount(() => {
  if (friendPollTimer) window.clearInterval(friendPollTimer)
  window.removeEventListener('focus', refreshFriends)
})

function refreshFriends() {
  void loadFriends(false)
}

async function loadFriends(showError = true) {
  try {
    friends.value = (await socialApi.friends()).data
  } catch (e) {
    if (showError) {
      friendOk.value = false
      friendMessage.value = errorMessage(e, '好友列表加载失败')
    }
  }
}

async function copyPublicId() {
  const publicId = authState.user?.publicId
  if (!publicId) return
  await navigator.clipboard.writeText(publicId)
  copied.value = true
  window.setTimeout(() => { copied.value = false }, 1600)
}

async function removeFriend(friend: PublicUser) {
  if (!window.confirm(`确定从好友中删除 ${friend.username} 吗？已有聊天记录不会被删除。`)) return
  removingId.value = friend.publicId
  friendMessage.value = ''
  try {
    await socialApi.removeFriend(friend.publicId)
    friendOk.value = true
    friendMessage.value = `已从好友中删除 ${friend.username}`
    await loadFriends()
  } catch (e) {
    friendOk.value = false
    friendMessage.value = errorMessage(e, '删除好友失败')
  } finally {
    removingId.value = ''
  }
}

async function addFriend() {
  if (!friendId.value) return
  friendLoading.value = true
  friendMessage.value = ''
  try {
    const { data } = await socialApi.addFriend(friendId.value)
    friendOk.value = true
    friendMessage.value = `已添加 ${data.username}`
    friendId.value = ''
    await loadFriends()
  } catch (e) {
    friendOk.value = false
    friendMessage.value = errorMessage(e, '添加好友失败')
  } finally {
    friendLoading.value = false
  }
}

async function chatWith(publicId: string) {
  friendLoading.value = true
  try {
    const { data } = await socialApi.openDirect(publicId)
    await router.push({ name: 'messages', params: { roomId: data.id } })
  } catch (e) {
    friendOk.value = false
    friendMessage.value = errorMessage(e, '单聊创建失败')
  } finally {
    friendLoading.value = false
  }
}

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
.profile-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(340px, .92fr); gap: 18px; align-items: start; }
.panel { display: grid; gap: 24px; padding: 26px; border: 1px solid var(--border); border-radius: 18px; background: var(--panel); box-shadow: var(--shadow); }
.profile-head { display: flex; align-items: center; gap: 18px; }
.profile-head img { width: 76px; height: 76px; border-radius: 50%; object-fit: cover; border: 1px solid var(--border); background: var(--muted-surface); }
h1 { font-size: 32px; }
.role { display: inline-block; margin-top: 8px; color: var(--primary-strong); font-size: 13px; }
.identity-card { position: relative; display: grid; gap: 6px; width: 100%; overflow: hidden; padding: 18px 20px; text-align: left; color: #fffaf5; border: 0; border-radius: 4px 20px 4px 20px; cursor: pointer; background: var(--green); box-shadow: 0 10px 28px rgba(36, 69, 60, .18); }
.identity-card::after { content: ''; position: absolute; right: -24px; top: -38px; width: 115px; height: 115px; border: 1px solid rgba(255,255,255,.18); border-radius: 50%; }
.identity-card span, .identity-card small { opacity: .8; }
.identity-card strong { font-size: 23px; letter-spacing: .08em; }
.form { display: grid; gap: 16px; }
.two { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
label { display: grid; gap: 8px; color: var(--muted); font-size: 14px; }
input, textarea { width: 100%; padding: 12px 14px; resize: vertical; }
.ok { color: var(--accent-strong); }
.error { color: var(--danger); }
.friends-panel { gap: 18px; }
.section-head h2 { margin-top: 3px; font-size: 25px; }
.section-head h2 span { color: var(--primary); font: 600 13px ui-sans-serif, system-ui; }
.friend-add { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.friend-add input { min-width: 0; padding: 11px 13px; text-transform: uppercase; }
.friend-list { display: grid; gap: 10px; max-height: 480px; overflow: auto; }
.friend-card { display: grid; grid-template-columns: 46px 1fr auto; gap: 12px; align-items: center; padding: 12px; border-radius: 4px 15px 4px 15px; border: 1px solid var(--border); background: rgba(255, 255, 255, .46); }
.friend-card img { width: 46px; height: 46px; border-radius: 50%; object-fit: cover; background: var(--muted-surface); }
.friend-copy { display: grid; min-width: 0; gap: 2px; }
.friend-copy span { color: var(--primary-strong); font-size: 11px; }
.friend-copy small { overflow: hidden; color: var(--muted); text-overflow: ellipsis; white-space: nowrap; }
.chat-button { width: 38px; height: 38px; border: 0; border-radius: 13px; color: #fff; cursor: pointer; background: var(--accent-strong); }
.friend-actions { display: flex; gap: 5px; }
.remove-button { width: 30px; height: 38px; border: 0; color: var(--muted); cursor: pointer; background: transparent; font-size: 11px; }
.remove-button:hover { color: var(--danger); }
.remove-button:disabled { opacity: .5; cursor: wait; }
.empty-friends { display: grid; place-items: center; gap: 10px; min-height: 210px; padding: 26px; text-align: center; color: var(--muted); border: 1px dashed var(--border); border-radius: 16px; }
.empty-friends span { display: grid; place-items: center; width: 54px; height: 54px; color: #fff; border-radius: 50%; background: var(--muted-surface); color: var(--primary-strong); font: 700 22px ui-serif, serif; }
.empty-friends p { max-width: 17em; line-height: 1.6; }
@media (max-width: 880px) { .profile-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .two { grid-template-columns: 1fr; } .panel { padding: 20px; } }
</style>
