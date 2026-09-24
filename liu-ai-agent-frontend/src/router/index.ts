import type { RouteRecordRaw } from 'vue-router'
import { loadCurrentUser, authState, isAdmin } from '../stores/auth'

const Home = () => import('../views/Home.vue')
const LoveApp = () => import('../views/LoveApp.vue')
const ManusApp = () => import('../views/ManusApp.vue')
const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const Profile = () => import('../views/Profile.vue')
const Messages = () => import('../views/Messages.vue')
const AdminKnowledge = () => import('../views/AdminKnowledge.vue')
const AdminUsers = () => import('../views/AdminUsers.vue')

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: Login, meta: { public: true } },
  { path: '/register', name: 'register', component: Register, meta: { public: true } },
  { path: '/', name: 'home', component: Home, meta: { requiresAuth: true } },
  { path: '/love', name: 'love', component: LoveApp, meta: { requiresAuth: true } },
  { path: '/manus', name: 'manus', component: ManusApp, meta: { requiresAuth: true } },
  { path: '/profile', name: 'profile', component: Profile, meta: { requiresAuth: true } },
  { path: '/messages/:roomId?', name: 'messages', component: Messages, meta: { requiresAuth: true } },
  { path: '/admin/knowledge', name: 'admin-knowledge', component: AdminKnowledge, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/users', name: 'admin-users', component: AdminUsers, meta: { requiresAuth: true, requiresAdmin: true } }
]

export default routes

export async function guardRoute(to: { meta: Record<string, unknown> }) {
  if (to.meta.public) return true
  if (!authState.token) return { name: 'login' }
  if (!authState.user || to.meta.requiresAdmin) await loadCurrentUser()
  if (!authState.user) return { name: 'login' }
  if (to.meta.requiresAdmin && !isAdmin()) return { name: 'home' }
  return true
}

