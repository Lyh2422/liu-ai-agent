<template>
  <div class="chat">
    <header class="top container">
      <div class="title-block">
        <router-link class="back-link" to="/">返回首页</router-link>
        <slot name="title" />
        <p class="subtitle">{{ subtitle || '河南师大校园场景 · 恋爱沟通 · 情绪支持 · 关系维护' }}</p>
      </div>
      <div class="chat-id muted">{{ chatId ? '会话已保存' : '发送消息，开始新会话' }}</div>
    </header>
    <main class="messages" ref="listRef">
      <div class="container list">
        <section v-if="messages.length === 0" class="empty-state">
          <div class="empty-card">
            <div class="empty-kicker">刚开始聊也没关系</div>
            <h3>{{ emptyTitle || '先从今天的恋爱沟通、情绪波动或关系烦恼里挑一件事' }}</h3>
            <p>每个会话单独保存。可以继续上次的话题，也可以新建会话，从一件新的事情聊起。</p>
            <div v-if="!emptyTitle" class="empty-pills">
              <span>怎么回消息</span>
              <span>情绪有点乱</span>
              <span>想修复关系</span>
            </div>
          </div>
        </section>
        <div v-for="(m, i) in messages" :key="i" class="row" :class="[m.role, { streaming: loading && i === messages.length - 1 && m.role === 'assistant' }]">
          <img class="avatar" :src="m.role === 'assistant' ? assistantAvatar : userAvatar" alt="avatar" />
          <div class="bubble">
            <div class="content">{{ m.content || (m.status === 'STREAMING' ? (m.pendingText || '正在思考…') : '') }}</div>
            <p v-if="m.status === 'FAILED' || m.status === 'INTERRUPTED'" class="message-status" role="status">
              {{ m.status === 'FAILED' ? '回复失败' : '回复已中断' }} · 已保留收到的内容，可继续发送消息
            </p>
          </div>
        </div>
      </div>
    </main>
    <footer class="bottom">
      <form class="input container" @submit.prevent="onSend">
        <input v-model="input" :disabled="loading" :placeholder="placeholder || '说说今天的沟通、心情或关系困扰...'" aria-label="消息内容" maxlength="10000" />
        <button class="btn primary" :disabled="!input.trim() || loading" type="submit">发送</button>
      </form>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  pendingText?: string
  status?: 'STREAMING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED'
}

const props = defineProps<{
  subtitle?: string
  emptyTitle?: string
  placeholder?: string
  chatId?: string
  loading: boolean
  messages: ChatMessage[]
  onSubmit: (text: string) => void
  assistantAvatar?: string
  userAvatar?: string
}>()

const input = ref('')
const listRef = ref<HTMLElement | null>(null)
const assistantAvatar = props.assistantAvatar ?? 'https://api.iconify.design/fluent/emoji-robot.svg?color=%23c86b3f'
const userAvatar = props.userAvatar ?? 'https://api.iconify.design/solar/user-bold-duotone.svg?color=%238e9a56'

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function onSend() {
  if (!input.value.trim() || props.loading) return
  props.onSubmit(input.value.trim())
  input.value = ''
}

watch(() => props.chatId, () => { input.value = ''; scrollToBottom() })
watch(() => props.messages.map((message) => message.content).join(''), scrollToBottom)
</script>

<style scoped>
.chat {
  display: grid;
  grid-template-rows: auto 1fr auto;
  height: 100%;
  min-height: 0;
  min-width: 0;
}

.top {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 22px 14px;
  border-bottom: 1px solid rgba(234, 215, 198, 0.9);
  background: linear-gradient(180deg, rgba(255, 250, 244, 0.78), rgba(255, 250, 244, 0.34));
  backdrop-filter: blur(12px);
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.back-link {
  display: inline-flex;
  align-self: flex-start;
  color: var(--accent-strong);
  text-decoration: none;
  font-size: 12px;
}

.back-link:hover {
  text-decoration: underline;
}

.title-block :deep(h2) {
  font-size: 28px;
  line-height: 1.1;
}

.subtitle {
  color: var(--muted);
  font-size: 13px;
}

.messages {
  overflow-y: auto;
}

.list {
  padding: 18px 22px 20px;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: min(55vh, 480px);
}

.empty-card {
  width: min(100%, 560px);
  padding: 24px;
  border-radius: 24px;
  border: 1px solid rgba(234, 215, 198, 0.95);
  background: linear-gradient(180deg, rgba(255, 250, 244, 0.96), rgba(247, 232, 212, 0.82));
  box-shadow: var(--shadow);
  text-align: left;
}

.empty-kicker {
  color: var(--muted);
  font-size: 12px;
}

.empty-card h3 {
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
}

.empty-card p {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.empty-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.empty-pills span {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(255, 253, 249, 0.92);
  color: var(--primary-strong);
  font-size: 13px;
}

.row {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 12px;
  margin: 12px 0;
  align-items: flex-start;
}

.row.user { direction: rtl; }
.row.user .bubble, .row.assistant .bubble { direction: ltr; }

.avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: rgba(255, 248, 239, 0.92);
  border: 1px solid var(--border);
  box-shadow: 0 8px 18px rgba(128, 83, 48, 0.08);
}

.bubble {
  max-width: 100%;
  padding: 13px 15px;
  border-radius: 18px;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(255, 250, 244, 0.92);
  border: 1px solid rgba(234, 215, 198, 0.95);
  color: var(--text);
  box-shadow: 0 8px 24px rgba(128, 83, 48, 0.06);
}

.content { text-align: left; }
.message-status { margin-top: 10px; color: var(--danger); font-size: 12px; }
.chat-id { font-size: 12px; flex-shrink: 0; }
button:disabled { opacity: .5; cursor: not-allowed; }

.row.streaming .content::after {
  content: '';
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--primary);
  animation: caret .8s steps(1) infinite;
}

.bottom {
  padding: 14px 0 16px;
  border-top: 1px solid rgba(234, 215, 198, 0.9);
  background: linear-gradient(180deg, rgba(247, 239, 229, 0.34), rgba(255, 248, 239, 0.82));
  position: sticky;
  bottom: 0;
  backdrop-filter: blur(14px);
}

.input {
  display: flex;
  gap: 10px;
}

input {
  flex: 1;
  padding: 13px 15px;
  border-radius: 14px;
}

button {
  padding: 12px 18px;
  border-radius: 14px;
}

@keyframes caret {
  50% { opacity: 0; }
}

@media (min-width: 1024px) {
  .list {
    padding: 24px 22px;
  }
}

@media (max-width: 640px) {
  .top {
    flex-direction: column;
    align-items: flex-start;
  }

  .title-block :deep(h2) {
    font-size: 24px;
  }

  .empty-state {
    min-height: 260px;
  }

  .empty-card {
    padding: 20px;
  }

  .empty-card h3 {
    font-size: 21px;
  }

  .row {
    grid-template-columns: 32px 1fr;
    gap: 10px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }
}
</style>
