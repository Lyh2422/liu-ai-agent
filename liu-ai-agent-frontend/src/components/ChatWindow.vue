<template>
  <div class="chat">
    <header class="top container">
      <div class="title-block">
        <router-link class="back-link" to="/">← 回到首页</router-link>
        <slot name="title" />
        <p class="subtitle">{{ subtitle || '恋爱、友情和那些说不清楚的情绪，都可以慢慢讲' }}</p>
      </div>
      <div class="chat-id"><span />{{ chatId ? '已自动保存' : '发出第一句话后保存' }}</div>
    </header>
    <main class="messages" ref="listRef">
      <div class="container list">
        <section v-if="messages.length === 0" class="empty-state">
          <div class="empty-card">
            <div class="empty-kicker">不用组织好语言</div>
            <h3>{{ emptyTitle || '先从今天的恋爱沟通、情绪波动或关系烦恼里挑一件事' }}</h3>
            <p>{{ mode === 'MANUS' ? '把已有的信息和卡住的地方说出来，我们先理清楚，再往下做。' : '哪怕只写一句“我有点难受”，也可以。后面再一点点补充。' }}</p>
            <div class="empty-pills" aria-label="可以这样开头">
              <button v-for="prompt in starterPrompts" :key="prompt" type="button" @click="usePrompt(prompt)">{{ prompt }}</button>
            </div>
          </div>
        </section>
        <div v-for="(m, i) in messages" :key="i" class="row" :class="[m.role, { streaming: loading && i === messages.length - 1 && m.role === 'assistant' }]">
          <img class="avatar" :src="m.role === 'assistant' ? assistantAvatar : userAvatar" alt="" />
          <div class="bubble">
            <span class="speaker">{{ m.role === 'assistant' ? (mode === 'LOVE' ? '留心' : '校园助手') : '我' }}</span>
            <div v-if="m.role === 'assistant' && renderAssistantMarkdown && visibleContent(m.content)"
              class="content markdown-content" v-html="renderMarkdown(visibleContent(m.content))"></div>
            <div v-else class="content">{{ visibleContent(m.content) || (m.status === 'STREAMING' ? (m.pendingText || '正在思考…') : '') }}</div>
            <div v-if="m.role === 'assistant' && generatedFiles(m.content).length" class="generated-files" aria-label="生成的文件">
              <button v-for="file in generatedFiles(m.content)" :key="file.id" class="download-file" type="button"
                :disabled="downloading === file.id" @click="downloadFile(file)">
                {{ downloading === file.id ? '正在下载…' : `下载 ${file.filename}` }}
              </button>
            </div>
            <p v-if="downloadError && generatedFiles(m.content).some(file => file.id === downloadError?.id)" class="download-error" role="alert">
              {{ downloadError.message }}
            </p>
            <p v-if="m.status === 'FAILED' || m.status === 'INTERRUPTED'" class="message-status" role="status">
              {{ m.status === 'FAILED' ? '这次没能回复' : '连接中断了' }}，已经收到的内容还在，可以继续发送消息
            </p>
          </div>
        </div>
      </div>
    </main>
    <footer class="bottom">
      <form class="input container" @submit.prevent="onSend">
        <textarea v-model="input" :disabled="loading" :placeholder="placeholder || '想到什么就写什么…'" aria-label="消息内容" maxlength="10000" rows="1" @keydown.enter.exact.prevent="onSend" />
        <button class="btn primary" :disabled="!input.trim() || loading" type="submit">发送</button>
      </form>
      <p class="input-hint container">Enter 发送，Shift + Enter 换行 · 重要决定请结合现实情况判断</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import MarkdownIt from 'markdown-it'
import { generatedFileApi } from '../api'
import { authState } from '../stores/auth'
import { avatarSrc } from '../utils/avatar'
import defaultAvatar from '../assets/avatar-default.svg'

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
  mode?: 'LOVE' | 'MANUS'
  renderAssistantMarkdown?: boolean
}>()

const input = ref('')
const listRef = ref<HTMLElement | null>(null)
const downloading = ref('')
const downloadError = ref<{ id: string; message: string } | null>(null)
const assistantAvatar = props.assistantAvatar ?? defaultAvatar
const userAvatar = computed(() => props.userAvatar ?? avatarSrc(authState.user?.avatarUrl))
const starterPrompts = computed(() => props.mode === 'MANUS'
  ? ['帮我理一下这份作业', '社团活动不知道怎么安排', '把这段材料整理清楚']
  : ['TA 突然不回消息了', '吵完架后怎么开口', '最近总在反复想一件事'])
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: true })

function renderMarkdown(content: string) {
  return markdown.render(content)
}

interface GeneratedFile {
  id: string
  filename: string
}

const generatedFilePattern = () => /\[\[generated-file:([0-9a-fA-F-]{36}):([^\]\r\n]+)]]/g

function visibleContent(content: string) {
  return content.replace(generatedFilePattern(), '').trimEnd()
}

function generatedFiles(content: string): GeneratedFile[] {
  const files = new Map<string, GeneratedFile>()
  for (const match of content.matchAll(generatedFilePattern())) {
    files.set(match[1], { id: match[1], filename: match[2] })
  }
  return [...files.values()]
}

async function downloadFile(file: GeneratedFile) {
  downloading.value = file.id
  downloadError.value = null
  try {
    const blob = await generatedFileApi.download(file.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (cause: any) {
    downloadError.value = { id: file.id, message: cause?.message || '文件下载失败，请稍后重试' }
  } finally {
    downloading.value = ''
  }
}

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

function usePrompt(prompt: string) {
  input.value = prompt
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
.markdown-content { white-space: normal; overflow-wrap: anywhere; line-height: 1.7; }
.markdown-content :deep(p + p),
.markdown-content :deep(p + ul),
.markdown-content :deep(p + ol),
.markdown-content :deep(ul + p),
.markdown-content :deep(ol + p),
.markdown-content :deep(blockquote + p),
.markdown-content :deep(pre + p) { margin-top: 10px; }
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) { margin: 12px 0 6px; line-height: 1.35; }
.markdown-content :deep(h1:first-child),
.markdown-content :deep(h2:first-child),
.markdown-content :deep(h3:first-child),
.markdown-content :deep(h4:first-child) { margin-top: 0; }
.markdown-content :deep(ul),
.markdown-content :deep(ol) { padding-left: 1.5em; margin: 8px 0; }
.markdown-content :deep(li + li) { margin-top: 4px; }
.markdown-content :deep(blockquote) { border-left: 3px solid var(--primary); padding-left: 12px; margin: 10px 0; color: var(--muted); }
.markdown-content :deep(code) { padding: 2px 4px; border-radius: 4px; background: var(--muted-surface); font-size: .9em; }
.markdown-content :deep(pre) { overflow-x: auto; padding: 10px 12px; border-radius: 9px; background: var(--muted-surface); white-space: pre; }
.markdown-content :deep(pre code) { padding: 0; background: transparent; }
.markdown-content :deep(a) { color: var(--accent-strong); text-decoration: underline; }
.markdown-content :deep(img) { max-width: 100%; height: auto; }
.generated-files { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.download-file {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  color: var(--accent-strong);
  background: rgba(255, 253, 249, .96);
  cursor: pointer;
  font: inherit;
}

.download-file:hover { border-color: var(--primary); background: var(--muted-surface); }
.download-file:disabled { opacity: .55; cursor: wait; }
.download-error { margin-top: 8px; color: var(--danger); font-size: 12px; }
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

/* Campus notebook treatment */
.top { border-bottom-color: var(--border); background: rgba(251, 249, 243, .76); }
.back-link { color: var(--coral-dark); }
.empty-card { border-color: var(--border); border-radius: 5px 28px 5px 28px; background: var(--surface); box-shadow: var(--shadow-small); }
.empty-kicker { color: var(--coral-dark); font-weight: 700; letter-spacing: .1em; }
.empty-pills button { padding: 8px 12px; border: 1px solid var(--border); border-radius: 10px; color: var(--green); background: rgba(255,255,255,.76); cursor: pointer; font-size: 13px; }
.empty-pills button:hover { border-color: var(--green); background: var(--green-pale); }
.avatar { border-radius: 14px 5px 14px 5px; }
.bubble { max-width: min(82%, 760px); border-color: var(--border); border-radius: 4px 18px 18px 18px; background: var(--surface); }
.row.user .bubble { border-color: var(--green-dark); border-radius: 18px 4px 18px 18px; color: #fffdf7; background: var(--green); }
.speaker { display: block; margin-bottom: 6px; color: var(--coral-dark); font-size: 10px; font-weight: 800; letter-spacing: .12em; }
.row.user .speaker { color: #cfe0d8; text-align: right; }
.chat-id { display: flex; align-items: center; gap: 7px; color: var(--muted); font-size: 11px; }
.chat-id span { width: 7px; height: 7px; border-radius: 50%; background: #6a9578; box-shadow: 0 0 0 3px rgba(106,149,120,.12); }
.bottom { padding: 14px 0 12px; border-top-color: var(--border); background: rgba(244, 240, 231, .9); }
.input { display: grid; grid-template-columns: 1fr auto; }
.input textarea { width: 100%; min-height: 48px; max-height: 140px; padding: 13px 15px; border-radius: 14px; resize: vertical; line-height: 1.5; }
.input-hint { margin-top: 7px; color: var(--muted); font-size: 10px; text-align: right; }

@media (max-width: 640px) {
  .bubble { max-width: 92%; }
  .input-hint { text-align: left; }
}
</style>
