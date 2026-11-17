import { reactive } from 'vue'
import { authApi, type User } from '../api'
import {
  clearStoredAuth,
  readAuthToken,
  readAuthUser,
  writeAuth,
  writeAuthUser
} from '../utils/authSession'

const authStorage = sessionStorage

// localStorage 会在同源窗口之间共享，导致一个窗口登录后覆盖另一个窗口的账号。
// 会话改由 sessionStorage 保存：刷新仍可恢复，但每个窗口互相隔离。
clearStoredAuth(localStorage)

function readUser(): User | null {
  return readAuthUser<User>(authStorage)
}

export const authState = reactive({
  token: readAuthToken(authStorage),
  user: readUser() as User | null,
  loading: false
})

export function setAuth(token: string, user: User) {
  authState.token = token
  authState.user = user
  writeAuth(authStorage, token, user)
}

export function setUser(user: User) {
  authState.user = user
  writeAuthUser(authStorage, user)
}

export function clearAuth() {
  authState.token = ''
  authState.user = null
  clearStoredAuth(authStorage)
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
