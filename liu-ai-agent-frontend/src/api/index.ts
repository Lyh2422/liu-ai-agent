import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { authState, clearAuth } from '../stores/auth'

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

export function getAuthToken() {
  return authState.token || localStorage.getItem('liu_ai_token') || ''
}

export function authHeaders(): Record<string, string> {
  const token = getAuthToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function handleUnauthorized() {
  clearAuth()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    const token = getAuthToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  },
  (error) => {
    console.error('API Request Error:', error.message)
    return Promise.reject(error)
  }
)

api.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    const errInfo = error.response 
      ? `[${error.response.status}] ${error.response.config.url}`
      : error.message
    console.error('API Response Error:', errInfo)
    if (error.response?.status === 401) handleUnauthorized()
    return Promise.reject(error)
  }
)

export interface LoveAppChatParams {
  message: string
  chatId: string
}

export interface ManusAppChatParams {
  message: string
}

export interface User {
  id: number
  publicId: string
  username: string
  role: 'ADMIN' | 'USER'
  enabled: boolean
  grade?: string | null
  college?: string | null
  signature?: string | null
  avatarUrl?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface LoginResponse {
  token: string
  user: User
}

export interface RegisterPayload {
  username: string
  password: string
  grade?: string
  college?: string
  signature?: string
}

export interface ProfilePayload {
  grade?: string
  college?: string
  signature?: string
  avatarUrl?: string | null
}

export type AppType = 'LOVE' | 'MANUS'
export type MessageStatus = 'STREAMING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED'
export interface Conversation {
  id: string
  appType: AppType
  title: string
  createdAt: string
  updatedAt: string
}
export interface HistoryMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: MessageStatus
  createdAt: string
}
export interface ConversationDetail {
  conversation: Conversation
  messages: HistoryMessage[]
}
export const conversationApi = {
  list: (appType: AppType) => api.get<Conversation[]>('/ai/conversations', { params: { appType } }),
  create: (appType: AppType) => api.post<Conversation>('/ai/conversations', { appType }),
  detail: (id: string) => api.get<ConversationDetail>(`/ai/conversations/${encodeURIComponent(id)}`),
  remove: (id: string) => api.delete<void>(`/ai/conversations/${encodeURIComponent(id)}`),
  stream: (appType: AppType, chatId: string, message: string, signal: AbortSignal) =>
    fetch(`${api.defaults.baseURL}/ai/${appType === 'LOVE' ? 'love_app/chat/sse' : 'manus/chat'}`, {
      method: 'POST',
      signal,
      headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ chatId, message })
    })
}

export const systemApi = {
  health: () => api.get<string>('/health', { responseType: 'text', timeout: 5000 })
}

export const generatedFileApi = {
  download: async (id: string) => {
    const response = await fetch(`${API_BASE_URL}/ai/generated-files/${encodeURIComponent(id)}`, {
      headers: { Accept: 'text/markdown', ...authHeaders() }
    })
    if (response.status === 401) handleUnauthorized()
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || `文件下载失败（HTTP ${response.status}）`)
    }
    return response.blob()
  }
}

export const http = {
  get: <T = any>(url: string, params?: Record<string, any>) => 
    api.get<T>(url, { params }),
  
  post: <T = any>(url: string, data?: any) => 
    api.post<T>(url, data),
  
  put: <T = any>(url: string, data?: any) => 
    api.put<T>(url, data),
  
  delete: <T = any>(url: string) => 
    api.delete<T>(url)
}

export const authApi = {
  login: (payload: { username: string; password: string }) =>
    api.post<LoginResponse>('/auth/login', payload),
  register: (payload: RegisterPayload) =>
    api.post<User>('/auth/register', payload),
  me: () => api.get<User>('/auth/me'),
  updateProfile: (payload: ProfilePayload) => api.put<User>('/auth/me', payload),
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<User>('/auth/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export interface PublicUser {
  publicId: string
  username: string
  avatarUrl?: string | null
  signature?: string | null
}

export type SocialRoomType = 'DIRECT' | 'GROUP'

export interface SocialRoom {
  id: string
  type: SocialRoomType
  name: string
  ownerPublicId?: string | null
  members: PublicUser[]
  lastMessage?: string | null
  lastMessageAt?: string | null
  unreadCount: number
  createdAt: string
  updatedAt: string
}

export interface SocialMessage {
  id: string
  roomId: string
  sender: PublicUser
  content: string
  createdAt: string
}

export const socialApi = {
  friends: () => api.get<PublicUser[]>('/social/friends'),
  addFriend: (publicId: string) => api.post<PublicUser>('/social/friends', { publicId }),
  removeFriend: (publicId: string) => api.delete(`/social/friends/${encodeURIComponent(publicId)}`),
  rooms: () => api.get<SocialRoom[]>('/social/chats'),
  unreadCount: () => api.get<{ unreadCount: number }>('/social/unread-count'),
  room: (roomId: string) => api.get<SocialRoom>(`/social/chats/${encodeURIComponent(roomId)}`),
  openDirect: (publicId: string) => api.post<SocialRoom>('/social/chats/direct', { publicId }),
  createGroup: (name: string, memberPublicIds: string[]) =>
    api.post<SocialRoom>('/social/chats/groups', { name, memberPublicIds }),
  invite: (roomId: string, publicId: string) =>
    api.post<SocialRoom>(`/social/chats/${encodeURIComponent(roomId)}/members`, { publicId }),
  messages: (roomId: string) =>
    api.get<SocialMessage[]>(`/social/chats/${encodeURIComponent(roomId)}/messages`),
  sendMessage: (roomId: string, content: string) =>
    api.post<SocialMessage>(`/social/chats/${encodeURIComponent(roomId)}/messages`, { content })
}

export const adminApi = {
  listUsers: (params?: { keyword?: string; page?: number; size?: number }) =>
    api.get<{ content: User[]; page: number; size: number; totalElements: number; totalPages: number }>('/admin/users', { params }),
  updateUser: (id: number, payload: Partial<ProfilePayload> & { role?: User['role']; enabled?: boolean }) =>
    api.put<User>(`/admin/users/${id}`, payload)
}

export interface KnowledgeDocumentSummary {
  id: string
  version: number
  title: string
  filename: string
  characters: number
  builtin: boolean
  updatedBy: number | null
  updatedAt: string
}
export interface KnowledgeDocument extends Omit<KnowledgeDocumentSummary, 'characters'> {
  content: string
}
export const knowledgeApi = {
  list: () => api.get<KnowledgeDocumentSummary[]>('/admin/knowledge/documents'),
  detail: (id: string) => api.get<KnowledgeDocument>(`/admin/knowledge/documents/${encodeURIComponent(id)}`),
  upload: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return api.post<KnowledgeDocument>('/admin/knowledge/documents', data, {
      headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000
    })
  },
  update: (id: string, data: { title: string; content: string; version: number }) =>
    api.put<KnowledgeDocument>(`/admin/knowledge/documents/${encodeURIComponent(id)}`, data, { timeout: 180000 }),
  delete: (id: string, version: number) =>
    api.delete(`/admin/knowledge/documents/${encodeURIComponent(id)}`, { params: { version }, timeout: 180000 })
}

export default api
