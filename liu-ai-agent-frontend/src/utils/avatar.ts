import defaultAvatar from '../assets/avatar-default.svg'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

export function avatarSrc(url?: string | null) {
  return url ? (url.startsWith('http') ? url : `${API_BASE_URL}${url}`) : defaultAvatar
}
