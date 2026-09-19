<template>
  <div class="conversation-layout">
    <aside class="history" aria-label="历史会话">
      <div class="history-heading"><span class="history-kicker">留住每一次交流</span><h2>我的会话</h2></div>
      <button class="btn primary new-chat" :disabled="busy" @click="createConversation">＋ 新建会话</button>
      <div class="history-label"><span>历史对话 · {{ conversations.length }}</span><button class="refresh" :disabled="busy" @click="initialize">刷新</button></div>
      <p v-if="initializing" class="history-hint" role="status">正在读取历史…</p>
      <p v-else-if="!conversations.length" class="history-hint">还没有会话。<br>从右侧发出第一条消息吧。</p>
      <nav class="history-list" aria-label="选择会话">
        <button v-for="item in conversations" :key="item.id" class="history-item"
          :class="{ active: item.id === chatId }" :aria-current="item.id === chatId ? 'page' : undefined"
          :disabled="busy" @click="selectConversation(item.id)">
          <span class="conversation-title">{{ item.title }}</span>
          <time :datetime="item.updatedAt">{{ formatDate(item.updatedAt) }}</time>
        </button>
      </nav>
      <p class="history-footnote">会话跟随当前账号保存<br>下次回来，可以接着聊</p>
    </aside>
    <section class="workspace" aria-label="对话窗口">
      <div v-if="error" class="error-banner" role="alert">{{ error }}<button @click="error = ''" aria-label="关闭提示">×</button></div>
      <ChatWindow :chat-id="chatId" :messages="messages" :loading="busy || remoteGenerating"
        :on-submit="send" :assistant-avatar="assistantAvatar"
        :subtitle="appType === 'MANUS' ? '整理思路 · 拆解任务 · 智能协作' : undefined"
        :empty-title="appType === 'MANUS' ? '今天有什么事情想一起解决？' : undefined"
        :placeholder="appType === 'MANUS' ? '描述你的问题或任务…' : undefined">
        <template #title><h2>{{ title }}</h2></template>
      </ChatWindow>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatWindow, { type ChatMessage } from './ChatWindow.vue'
import { conversationApi, type AppType, type Conversation } from '../api'
import { streamGetEvents } from '../utils/stream'

const props = defineProps<{ appType: AppType; title: string; assistantAvatar: string }>()
const route = useRoute()
const router = useRouter()
const conversations = ref<Conversation[]>([])
const messages = ref<ChatMessage[]>([])
const chatId = ref('')
const initializing = ref(true)
const loading = ref(false)
const navigating = ref(false)
const error = ref('')
const busy = computed(() => initializing.value || loading.value || navigating.value)
const remoteGenerating = computed(() => messages.value.some(message => message.status === 'STREAMING'))
let controller: AbortController | null = null
let alive = true
let poll: ReturnType<typeof setTimeout> | undefined

function errorMessage(cause: any) {
  return cause.response?.data?.message || cause.message || '请求失败，请稍后重试'
}
function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
async function refreshList() {
  const { data } = await conversationApi.list(props.appType)
  if (alive) conversations.value = data
}
async function loadDetail(id: string) {
  const { data } = await conversationApi.detail(id)
  if (!alive || (chatId.value && chatId.value !== id)) return
  messages.value = data.messages
  clearTimeout(poll)
  if (remoteGenerating.value && !loading.value) {
    poll = setTimeout(() => { if (alive && chatId.value === id) loadDetail(id).catch(cause => { error.value = errorMessage(cause) }) }, 1500)
  }
}
async function selectConversation(id: string) {
  if (loading.value || navigating.value) return
  navigating.value = true
  error.value = ''
  clearTimeout(poll)
  try {
    const { data } = await conversationApi.detail(id)
    if (!alive) return
    chatId.value = id
    messages.value = data.messages
    await router.replace({ query: { ...route.query, chat: id } })
    if (remoteGenerating.value) poll = setTimeout(() => loadDetail(id).catch(cause => { error.value = errorMessage(cause) }), 1500)
  } catch (cause) { error.value = errorMessage(cause) }
  finally { navigating.value = false }
}
async function initialize() {
  initializing.value = true
  error.value = ''
  try {
    await refreshList()
    if (!alive) return
    const preferred = chatId.value || String(route.query.chat || '')
    const selected = conversations.value.find(item => item.id === preferred) || conversations.value[0]
    if (selected) await selectConversation(selected.id)
  } catch (cause) { error.value = '历史加载失败：' + errorMessage(cause) }
  finally { initializing.value = false }
}
async function createConversation() {
  if (busy.value) return
  navigating.value = true
  error.value = ''
  clearTimeout(poll)
  try {
    const { data } = await conversationApi.create(props.appType)
    if (!alive) return
    conversations.value.unshift(data)
    chatId.value = data.id
    messages.value = []
    await router.replace({ query: { ...route.query, chat: data.id } })
  } catch (cause) { error.value = '新建失败：' + errorMessage(cause) }
  finally { navigating.value = false }
}
async function send(text: string) {
  if (busy.value || remoteGenerating.value) return
  if (!chatId.value) await createConversation()
  if (!chatId.value || !alive) return
  loading.value = true
  error.value = ''
  const id = chatId.value
  messages.value.push({ role: 'user', content: text, status: 'COMPLETED' })
  const assistant = ref<ChatMessage>({ role: 'assistant', content: '', status: 'STREAMING', pendingText: '正在发送…' })
  messages.value.push(assistant.value)
  controller = new AbortController()
  let terminal = false
  try {
    const response = await conversationApi.stream(props.appType, id, text, controller.signal)
    const stream = await streamGetEvents('', {}, response)
    await stream.read(event => {
      if (!alive) return
      if (event.event === 'ack') assistant.value.pendingText = '已收到，正在整理回复…'
      if (event.event === 'delta') assistant.value.content += event.data
      if (event.event === 'done') { terminal = true; assistant.value.status = 'COMPLETED' }
      if (event.event === 'error') { terminal = true; assistant.value.status = 'FAILED'; error.value = event.data }
    })
    if (!terminal && alive) { assistant.value.status = 'INTERRUPTED'; error.value = '连接已中断，正在恢复已保存的内容' }
  } catch (cause) {
    if (alive) { assistant.value.status = 'FAILED'; error.value = errorMessage(cause) }
  } finally {
    loading.value = false
    controller = null
    if (alive) {
      try { await loadDetail(id); await refreshList() }
      catch (cause) { error.value = '历史同步失败，请点击刷新：' + errorMessage(cause) }
    }
  }
}
onMounted(initialize)
onUnmounted(() => { alive = false; controller?.abort(); clearTimeout(poll) })
</script>

<style scoped>
.conversation-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); height: calc(100dvh - 68px); overflow: hidden; }
.history { display: flex; flex-direction: column; gap: 18px; min-height: 0; padding: 30px 20px 20px; background: var(--bg-soft); border-right: 1px solid var(--border); }
.history-kicker { display: block; color: var(--accent-strong); font-size: 11px; letter-spacing: .12em; margin-bottom: 8px; }
.history-heading h2 { font-size: 26px; }
.new-chat { justify-content: center; width: 100%; }
.history-label { display: flex; align-items: center; justify-content: space-between; color: var(--muted); font-size: 12px; margin-top: 8px; }
.refresh { border: 0; background: transparent; color: var(--accent-strong); cursor: pointer; padding: 5px; }
.history-list { overflow-y: auto; min-height: 0; flex: 1; }
.history-item { display: flex; flex-direction: column; gap: 8px; width: 100%; padding: 14px 12px; margin-bottom: 8px; text-align: left; border: 1px solid transparent; border-radius: 12px; color: var(--text); background: transparent; cursor: pointer; }
.history-item:hover { background: var(--muted-surface); }
.history-item.active { background: #f3e4d6; border-color: var(--border); box-shadow: inset 3px 0 var(--primary); }
.conversation-title { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.history-item time { font-size: 11px; color: var(--muted); }
.history-hint, .history-footnote { color: var(--muted); font-size: 12px; line-height: 1.8; }
.history-footnote { margin-top: auto; border-top: 1px solid var(--border); padding-top: 16px; }
.workspace { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.workspace :deep(.chat) { flex: 1; }
.error-banner { display: flex; justify-content: space-between; padding: 10px 20px; color: var(--danger); background: #fff1ea; font-size: 13px; }
.error-banner button { background: transparent; border: 0; color: inherit; cursor: pointer; }
button:disabled { opacity: .55; cursor: not-allowed; }
button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (max-width: 760px) {
  .conversation-layout { height: calc(100dvh - 78px); }
}
@media (max-width: 700px) {
  .conversation-layout { grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); }
  .history { padding: 12px 14px; gap: 10px; border-right: 0; border-bottom: 1px solid var(--border); display: grid; grid-template-columns: 1fr auto; }
  .history-heading h2 { font-size: 18px; }
  .history-kicker, .history-footnote { display: none; }
  .history-label { grid-column: 1 / -1; margin-top: 0; }
  .new-chat { padding: 8px 12px; }
  .history-list { display: flex; gap: 8px; grid-column: 1 / -1; overflow-x: auto; }
  .history-item { flex: 0 0 150px; padding: 8px 10px; margin: 0; }
  .history-hint { grid-column: 1 / -1; }
}
</style>
