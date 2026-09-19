<template>
  <div class="app">
    <template v-if="isPublic">
      <router-view />
    </template>
    <template v-else>
      <header class="topbar">
        <router-link class="brand" to="/">校园 AI 情感陪伴</router-link>
        <nav class="nav-links">
          <router-link to="/love">情感陪伴</router-link>
          <router-link to="/manus">智能问答</router-link>
          <router-link v-if="isAdmin()" to="/admin/knowledge">知识库</router-link>
        </nav>
        <div class="account">
          <router-link class="account-link" to="/profile">
            <img :src="avatarSrc(authState.user?.avatarUrl)" alt="" />
            <span>{{ authState.user?.username }}</span>
          </router-link>
          <router-link v-if="isAdmin()" class="admin-link" to="/admin/users">用户管理</router-link>
          <button class="logout" type="button" @click="logout">退出</button>
        </div>
      </header>
      <main class="page-content">
        <router-view />
      </main>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, clearAuth, isAdmin } from './stores/auth'
import './styles/theme.css'

const route = useRoute()
const router = useRouter()
const isPublic = computed(() => route.meta.public === true)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

function avatarSrc(url?: string | null) {
  return url ? (url.startsWith('http') ? url : `${API_BASE_URL}${url}`) : 'https://api.iconify.design/solar/user-bold-duotone.svg?color=%23a54f2d'
}

function logout() {
  clearAuth()
  router.push({ name: 'login' })
}
</script>

<style>
html, body, #app, .app {
  height: 100%;
  margin: 0;
}
.app {
  background: transparent;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 68px;
  padding: 0 28px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 250, 244, 0.9);
  backdrop-filter: blur(14px);
}
.brand {
  color: var(--text);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}
.nav-links {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav-links a, .admin-link {
  color: var(--muted);
  text-decoration: none;
  font-size: 14px;
}
.nav-links a.router-link-active, .admin-link.router-link-active {
  color: var(--primary-strong);
}
.account {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.account-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
  text-decoration: none;
  font-weight: 600;
}
.account-link img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--border);
  background: var(--muted-surface);
}
.logout {
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font: inherit;
}
.logout:hover { color: var(--danger); }
.page-content { min-height: calc(100vh - 68px); }
@media (max-width: 760px) {
  .topbar { padding: 0 14px; gap: 12px; flex-wrap: wrap; min-height: 78px; }
  .nav-links { order: 3; flex-basis: 100%; padding-bottom: 10px; }
  .account { margin-left: auto; }
  .account .admin-link { display: none; }
}
</style>
