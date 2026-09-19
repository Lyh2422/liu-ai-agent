import { reactive } from 'vue'
import { authApi, type User } from '../api'

const TOKEN_KEY = 'liu_ai_token'
const USER_KEY = 'liu_ai_user'

function readUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) as User : null
  } catch {
    return null
  }
}

export const authState = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser() as User | null,
  loading: false
})

export function setAuth(token: string, user: User) {
  authState.token = token
  authState.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function setUser(user: User) {
  authState.user = user
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  authState.token = ''
  authState.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function loadCurrentUser() {
  if (!authState.token || authState.loading) return authState.user
  authState.loading = true
  try {
    const { data } = await authApi.me()
    setUser(data)
    return data
  } catch {
    clearAuth()
    return null
  } finally {
    authState.loading = false
  }
}

export function isAdmin() {
  return authState.user?.role === 'ADMIN'
}
