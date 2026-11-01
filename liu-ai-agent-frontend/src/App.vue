<template>
  <div class="app">
    <router-view v-if="isPublic" />

    <template v-else>
      <header class="site-header">
        <router-link class="brand" to="/" aria-label="留心首页">
          <span class="brand-mark" aria-hidden="true">✤</span>
          <span class="brand-copy"><strong>留心</strong><small>校园情感陪伴</small></span>
        </router-link>

        <nav class="nav-links" aria-label="主要导航">
          <router-link to="/love"><span class="nav-mark">心</span><span>心事小屋</span></router-link>
          <router-link to="/manus"><span class="nav-mark">问</span><span>校园问答</span></router-link>
          <router-link class="message-nav-link" to="/messages">
            <span class="nav-mark">信</span><span>同学消息</span>
            <span v-if="unreadCount > 0" class="nav-unread-badge" :aria-label="`${unreadCount} 条未读消息`">{{ unreadLabel }}</span>
          </router-link>
          <router-link v-if="isAdmin()" to="/admin/knowledge"><span class="nav-mark">册</span><span>知识库</span></router-link>
        </nav>

        <div class="account">
          <span class="service-state" :class="healthStatus" :title="healthLabel"><i />{{ healthLabel }}</span>
          <router-link class="account-link" to="/profile">
            <img :src="avatarSrc(authState.user?.avatarUrl)" alt="" />
            <span>{{ authState.user?.username }}</span>
          </router-link>
          <router-link v-if="isAdmin()" class="admin-link" to="/admin/users">管理</router-link>
          <button class="logout" type="button" @click="logout">退出</button>
        </div>
      </header>
      <main class="page-content"><router-view /></main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { socialApi, systemApi } from './api'
import { authState, clearAuth, isAdmin } from './stores/auth'
import { avatarSrc } from './utils/avatar'
import './styles/theme.css'

const route = useRoute()
const router = useRouter()
const isPublic = computed(() => route.meta.public === true)
const unreadCount = ref(0)
const unreadLabel = computed(() => unreadCount.value > 99 ? '99+' : String(unreadCount.value))
const healthStatus = ref<'checking' | 'online' | 'offline'>('checking')
const healthLabel = computed(() => ({ checking: '连接中', online: '服务正常', offline: '服务离线' })[healthStatus.value])
let unreadPollTimer: number | undefined
let healthTimer: number | undefined

onMounted(() => {
  window.addEventListener('social-unread-changed', refreshUnreadCount)
  syncUnreadPolling()
  void checkHealth()
  healthTimer = window.setInterval(checkHealth, 60000)
})

onBeforeUnmount(() => {
  stopUnreadPolling()
  if (healthTimer) window.clearInterval(healthTimer)
  window.removeEventListener('social-unread-changed', refreshUnreadCount)
})

watch(() => [authState.token, isPublic.value], syncUnreadPolling)
watch(() => route.fullPath, () => refreshUnreadCount())

async function checkHealth() {
  try {
    const { data } = await systemApi.health()
    healthStatus.value = data === 'ok' ? 'online' : 'offline'
  } catch {
    healthStatus.value = 'offline'
  }
}

async function refreshUnreadCount() {
  if (!authState.token || isPublic.value) {
    unreadCount.value = 0
    return
  }
  try { unreadCount.value = (await socialApi.unreadCount()).data.unreadCount }
  catch { unreadCount.value = 0 }
}

function syncUnreadPolling() {
  stopUnreadPolling()
  if (!authState.token || isPublic.value) {
    unreadCount.value = 0
    return
  }
  void refreshUnreadCount()
  unreadPollTimer = window.setInterval(refreshUnreadCount, 10000)
}

function stopUnreadPolling() {
  if (unreadPollTimer) window.clearInterval(unreadPollTimer)
  unreadPollTimer = undefined
}

function logout() {
  clearAuth()
  router.push({ name: 'login' })
}
</script>

<style>
html, body, #app, .app { min-height: 100%; }
.app { min-height: 100vh; }
.site-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  min-height: 76px;
  padding: 0 30px;
  border-bottom: 1px solid rgba(232, 215, 224, .82);
  background: rgba(255, 253, 254, .76);
  box-shadow: 0 8px 30px rgba(133, 103, 118, .06);
  backdrop-filter: blur(22px) saturate(1.15);
}
.brand { display: flex; align-items: center; gap: 10px; color: var(--ink); text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 42px; height: 42px; color: #6f9d51; border: 1px solid #c9e9a0; border-radius: 50% 45% 52% 42%; background: linear-gradient(145deg, #f4ffe6, #d8f3b5); box-shadow: 0 7px 18px rgba(154, 201, 97, .2), inset 0 0 0 4px rgba(255, 255, 255, .5); font: 700 24px Georgia, serif; transform: rotate(-7deg); }
.brand-copy { display: grid; line-height: 1.05; }
.brand-copy strong { font: 700 22px "Kaiti SC", "STKaiti", serif; letter-spacing: .08em; }
.brand-copy small { margin-top: 4px; color: #a78391; font-size: 9px; letter-spacing: .14em; }
.nav-links { display: flex; align-items: center; justify-content: center; gap: 4px; }
.nav-links a { position: relative; display: flex; align-items: center; gap: 7px; padding: 9px 13px; border-radius: 999px; color: var(--muted); font-size: 13px; text-decoration: none; transition: color .16s ease, background .16s ease, transform .16s ease; }
.nav-links a:hover { color: var(--ink); background: rgba(255, 244, 248, .8); transform: translateY(-1px); }
.nav-links a.router-link-active { color: #87546a; background: linear-gradient(135deg, #fff, #fff0f5); box-shadow: inset 0 0 0 1px #edd8e1, 0 6px 16px rgba(174, 111, 138, .1); }
.nav-mark { display: grid; place-items: center; width: 23px; height: 23px; border: 1px solid #d8eab9; border-radius: 50%; color: #698c53; background: #f5ffe8; font: 700 11px "Kaiti SC", "STKaiti", serif; }
.message-nav-link { position: relative; }
.nav-unread-badge { position: absolute; top: 3px; right: 1px; display: grid; place-items: center; min-width: 17px; height: 17px; padding: 0 4px; border: 2px solid var(--paper); border-radius: 99px; color: #fff; background: var(--danger); font-size: 9px; font-weight: 800; }
.account { display: flex; align-items: center; gap: 10px; }
.service-state { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); font-size: 10px; }
.service-state i { width: 7px; height: 7px; border-radius: 50%; background: #a4aaa7; }
.service-state.online i { background: #5c8b6d; box-shadow: 0 0 0 3px rgba(92, 139, 109, .12); }
.service-state.offline i { background: var(--danger); }
.account-link { display: flex; align-items: center; gap: 8px; padding: 5px 8px 5px 5px; border-radius: 99px; color: var(--ink); text-decoration: none; font-size: 13px; font-weight: 600; }
.account-link:hover { background: rgba(255, 240, 246, .72); }
.account-link img { width: 34px; height: 34px; border: 2px solid #fff; border-radius: 50%; object-fit: cover; box-shadow: 0 0 0 1px var(--border), 0 4px 12px rgba(118, 83, 100, .1); }
.admin-link, .logout { color: var(--muted); background: none; border: 0; text-decoration: none; cursor: pointer; font-size: 12px; }
.admin-link:hover, .logout:hover { color: var(--coral-dark); }
.page-content { min-height: calc(100vh - 76px); }

@media (max-width: 980px) {
  .site-header { grid-template-columns: auto 1fr; padding: 10px 18px; }
  .nav-links { grid-column: 1 / -1; grid-row: 2; justify-content: flex-start; overflow-x: auto; padding-top: 8px; }
  .nav-links a { flex: 0 0 auto; }
  .account { justify-self: end; }
  .page-content { min-height: calc(100vh - 126px); }
}
@media (max-width: 640px) {
  .site-header { padding: 8px 12px; }
  .brand-copy small, .service-state, .account-link span, .admin-link { display: none; }
  .nav-links { gap: 2px; }
  .nav-links a { padding: 8px 9px; font-size: 12px; }
  .nav-mark { display: none; }
  .logout { padding: 8px 4px; }
}
</style>
