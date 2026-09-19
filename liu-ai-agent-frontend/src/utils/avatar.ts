const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

export function avatarSrc(url?: string | null) {
  return url ? (url.startsWith('http') ? url : `${API_BASE_URL}${url}`) : 'https://api.iconify.design/solar/user-bold-duotone.svg?color=%23a54f2d'
}
