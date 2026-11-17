export const AUTH_TOKEN_KEY = 'liu_ai_token'
export const AUTH_USER_KEY = 'liu_ai_user'

type AuthStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export function readAuthToken(storage: AuthStorage) {
  return storage.getItem(AUTH_TOKEN_KEY) || ''
}

export function readAuthUser<T>(storage: AuthStorage): T | null {
  try {
    const raw = storage.getItem(AUTH_USER_KEY)
    return raw ? JSON.parse(raw) as T : null
  } catch {
    return null
  }
}

export function writeAuth(storage: AuthStorage, token: string, user: unknown) {
  storage.setItem(AUTH_TOKEN_KEY, token)
  storage.setItem(AUTH_USER_KEY, JSON.stringify(user))
}

export function writeAuthUser(storage: AuthStorage, user: unknown) {
  storage.setItem(AUTH_USER_KEY, JSON.stringify(user))
}

export function clearStoredAuth(storage: AuthStorage) {
  storage.removeItem(AUTH_TOKEN_KEY)
  storage.removeItem(AUTH_USER_KEY)
}
