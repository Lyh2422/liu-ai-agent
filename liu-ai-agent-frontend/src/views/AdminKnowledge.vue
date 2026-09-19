<template>
  <section class="knowledge-page container">
    <header class="knowledge-head">
      <div><p class="eyebrow">管理员工作台 / KNOWLEDGE</p><h1>让每一次回答，有据可依。</h1><p class="intro">维护情感陪伴的知识文档。修改成功后，新的内容即可用于问答。</p></div>
      <div class="head-actions">
        <input ref="fileInput" type="file" accept=".md,.txt,text/plain,text/markdown" class="file-input" aria-label="选择知识文档" @change="upload" />
        <button class="btn primary" :disabled="busy" @click="chooseFile">＋ 上传文档</button>
        <span class="upload-note">Markdown / TXT · UTF-8 · 最大 512 KB</span>
      </div>
    </header>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div v-if="notice" class="notice success" role="status">{{ notice }}</div>
    <div v-if="working" class="notice progress" role="status">{{ working }}，正在同步知识库，请稍候…</div>
    <div class="knowledge-workspace" :aria-busy="busy">
      <aside class="document-library" aria-label="知识文档列表">
        <div class="library-heading"><h2>文档库 <span>{{ documents.length }}</span></h2><button class="text-button" :disabled="busy" @click="refresh">刷新</button></div>
        <label class="search-label"><span class="sr-only">搜索文档</span><input v-model="keyword" placeholder="搜索标题或文件名" /></label>
        <p v-if="loading" class="empty-list">正在读取文档…</p>
        <p v-else-if="!filtered.length" class="empty-list">{{ documents.length ? '没有匹配的文档。' : '还没有文档，上传第一份知识吧。' }}</p>
        <nav class="document-list" aria-label="选择文档">
          <button v-for="document in filtered" :key="document.id" class="document-item" :class="{ selected: selected?.id === document.id }"
            :aria-current="selected?.id === document.id ? 'page' : undefined" :disabled="busy" @click="select(document.id)">
            <span class="document-type">{{ document.filename.toLowerCase().endsWith('.md') ? 'MD' : 'TXT' }}</span>
            <span class="document-info"><strong>{{ document.title }}</strong><span class="document-filename">{{ document.filename }}</span><span>{{ formatDate(document.updatedAt) }} · {{ document.characters.toLocaleString() }} 字符</span></span>
          </button>
        </nav>
        <p class="library-note">仅管理员可维护<br>文档随项目数据库持久保存</p>
      </aside>
      <section class="editor-panel" aria-label="文档编辑器">
        <template v-if="selected">
          <header class="editor-heading"><span class="eyebrow">{{ selected.builtin ? '内置文档' : '上传文档' }}</span><span class="save-state">{{ dirty ? '有未保存的修改' : '已保存' }}</span></header>
          <form class="editor-form" @submit.prevent="save">
            <label class="field">文档标题<input v-model="draft.title" maxlength="120" :disabled="busy" required /></label>
            <div class="source-info"><span>{{ selected.filename }}</span><span>更新于 {{ formatDate(selected.updatedAt) }}</span></div>
            <label class="field content-field">文档内容<textarea v-model="draft.content" :disabled="busy" maxlength="200000" spellcheck="false" required placeholder="在这里编写知识内容，支持 Markdown 文本。" /></label>
            <div class="editor-meta"><span>直接编辑原文，保存后用于知识检索</span><span>{{ draft.content.length.toLocaleString() }} / 200,000</span></div>
            <footer class="editor-actions">
              <button class="text-button delete-button" type="button" :disabled="busy" @click="deleteDialog?.showModal()">删除文档</button>
              <div><button class="btn" type="button" :disabled="busy || !dirty" @click="reloadSelected">放弃修改</button><button class="btn primary" type="submit" :disabled="busy || !dirty || !draft.title.trim() || !draft.content.trim()">保存修改</button></div>
            </footer>
          </form>
        </template>
        <div v-else class="editor-empty"><span class="empty-symbol">文</span><h2>从一份文档开始</h2><p>上传校园沟通、关系维护等知识，<br>或在左侧选择文档继续编辑。</p><button class="btn" :disabled="busy" @click="chooseFile">上传第一份文档</button></div>
      </section>
    </div>
    <dialog ref="deleteDialog" class="delete-dialog" aria-labelledby="delete-title" @cancel="busy && $event.preventDefault()">
      <h2 id="delete-title">删除这份文档？</h2><p>「{{ selected?.title }}」将从文档库和知识检索中移除。</p><p class="muted">此操作无法在页面中撤销。</p>
      <div><button class="btn" :disabled="busy" autofocus @click="deleteDialog?.close()">取消</button><button class="btn danger" :disabled="busy" @click="remove">确认删除</button></div>
    </dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { knowledgeApi, type KnowledgeDocument, type KnowledgeDocumentSummary } from '../api'
import { errorMessage } from '../utils/errors'

const documents = ref<KnowledgeDocumentSummary[]>([])
const selected = ref<KnowledgeDocument | null>(null)
const draft = reactive({ title: '', content: '' })
const keyword = ref('')
const loading = ref(false)
const working = ref('')
const busy = computed(() => loading.value || !!working.value)
const dirty = computed(() => !!selected.value && (draft.title !== selected.value.title || draft.content !== selected.value.content))
const error = ref('')
const notice = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const deleteDialog = ref<HTMLDialogElement | null>(null)
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return documents.value.filter(document => `${document.title} ${document.filename}`.toLowerCase().includes(query))
})
let alive = true
function formatDate(value: string) { return new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
function discardDraft() { return !dirty.value || window.confirm('当前文档有未保存的修改，确定放弃吗？') }
function apply(document: KnowledgeDocument) { selected.value = document; draft.title = document.title; draft.content = document.content }
function resetFeedback() { error.value = ''; notice.value = '' }
async function loadList() { const { data } = await knowledgeApi.list(); if (alive) documents.value = data }
async function refresh() {
  if (busy.value || !discardDraft()) return
  resetFeedback(); loading.value = true
  try {
    await loadList()
    const id = documents.value.find(document => document.id === selected.value?.id)?.id || documents.value[0]?.id
    if (id) { const { data } = await knowledgeApi.detail(id); if (alive) apply(data) }
    else selected.value = null
  } catch (cause) { error.value = errorMessage(cause, '文档加载失败，请重试') }
  finally { loading.value = false }
}
async function select(id: string) {
  if (busy.value || id === selected.value?.id || !discardDraft()) return
  resetFeedback(); loading.value = true
  try { const { data } = await knowledgeApi.detail(id); if (alive) apply(data) }
  catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
async function reloadSelected() { await refresh() }
function chooseFile() { if (!busy.value && discardDraft()) fileInput.value?.click() }
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]; input.value = ''
  if (!file || busy.value) return
  resetFeedback()
  if (!/\.(md|txt)$/i.test(file.name) || file.size > 512 * 1024 || file.size === 0) { error.value = '请上传非空的 Markdown / TXT 文件，大小不超过 512 KB'; return }
  working.value = '上传文档'
  try {
    const { data } = await knowledgeApi.upload(file)
    if (!alive) return
    apply(data); keyword.value = ''; notice.value = '文档已上传，问答知识已更新。'
    await loadList()
  } catch (cause) { error.value = errorMessage(cause) }
  finally { working.value = '' }
}
async function save() {
  if (busy.value || !selected.value || !dirty.value) return
  resetFeedback(); working.value = '保存修改'
  try {
    const { data } = await knowledgeApi.update(selected.value.id, { ...draft, version: selected.value.version })
    if (!alive) return
    apply(data); notice.value = '文档已保存，问答知识已更新。'; await loadList()
  } catch (cause) { error.value = errorMessage(cause) }
  finally { working.value = '' }
}
async function remove() {
  if (busy.value || !selected.value) return
  resetFeedback(); working.value = '删除文档'
  try {
    await knowledgeApi.delete(selected.value.id, selected.value.version)
    if (!alive) return
    selected.value = null; deleteDialog.value?.close(); notice.value = '文档已删除，相关内容已从知识检索中移除。'
    await loadList()
    if (documents.value[0]) { const { data } = await knowledgeApi.detail(documents.value[0].id); if (alive) apply(data) }
  } catch (cause) { deleteDialog.value?.close(); error.value = errorMessage(cause) }
  finally { working.value = '' }
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || working.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(() => working.value ? false : discardDraft())
onMounted(() => { refresh(); window.addEventListener('beforeunload', beforeUnload) })
onUnmounted(() => { alive = false; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<style scoped>
.knowledge-page { max-width: 1400px; padding: 36px 28px 40px; }
.knowledge-head { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.eyebrow { font-size: 11px; letter-spacing: .1em; color: var(--accent-strong); }
h1 { font-size: clamp(25px, 3vw, 36px); margin: 10px 0 12px; }
.intro { color: var(--muted); font-size: 14px; line-height: 1.7; }
.head-actions { display: flex; flex-direction: column; align-items: end; gap: 10px; flex-shrink: 0; }
.upload-note { color: var(--muted); font-size: 11px; }
.file-input, .sr-only { position: absolute; width: 1px; height: 1px; opacity: 0; overflow: hidden; }
.notice { padding: 12px 16px; border-radius: 10px; margin-bottom: 14px; font-size: 14px; }
.error { background: #fae6e0; color: var(--danger); }
.success { background: #edf0df; color: var(--accent-strong); }
.progress { background: var(--muted-surface); color: var(--muted); }
.knowledge-workspace { display: grid; grid-template-columns: 310px minmax(0, 1fr); min-height: 660px; border: 1px solid var(--border); border-radius: 20px; overflow: hidden; background: var(--panel); box-shadow: 0 14px 48px #8053300a; }
.document-library { padding: 24px 16px; background: #fbf5ed; border-right: 1px solid var(--border); display: flex; flex-direction: column; min-width: 0; }
.library-heading { display: flex; justify-content: space-between; align-items: center; padding: 0 8px; margin-bottom: 20px; }
.library-heading h2 { font-size: 20px; }
.library-heading h2 span { font-family: sans-serif; color: var(--muted); font-size: 12px; margin-left: 6px; }
.text-button { border: 0; background: transparent; color: var(--accent-strong); cursor: pointer; padding: 8px; font: inherit; font-size: 13px; }
.search-label input { padding: 11px 12px; width: 100%; background: var(--panel); font-size: 13px; }
.document-list { max-height: 510px; overflow-y: auto; margin: 16px 0; }
.document-item { width: 100%; display: flex; gap: 12px; align-items: start; text-align: left; padding: 16px 10px; border: 1px solid transparent; background: transparent; border-radius: 12px; margin-bottom: 6px; cursor: pointer; color: var(--text); }
.document-item:hover { background: var(--muted-surface); }
.document-item.selected { border-color: var(--border); background: #f2e3d4; }
.document-type { padding: 7px 4px; min-width: 32px; font-size: 10px; border: 1px solid #d7bfa9; border-radius: 6px; color: var(--primary-strong); text-align: center; }
.document-info { display: flex; flex-direction: column; min-width: 0; gap: 8px; }
.document-info strong { font-size: 14px; line-height: 1.5; overflow-wrap: anywhere; }
.document-info span { font-size: 11px; color: var(--muted); }
.document-filename { text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 205px; }
.library-note { font-size: 11px; line-height: 1.8; margin-top: auto; padding: 18px 8px 0; color: var(--muted); border-top: 1px solid var(--border); }
.empty-list { font-size: 13px; line-height: 1.8; color: var(--muted); margin: 24px 8px; }
.editor-panel { padding: 28px 32px; min-width: 0; }
.editor-heading { display: flex; justify-content: space-between; margin-bottom: 24px; }
.save-state { font-size: 12px; color: var(--muted); }
.field { display: flex; flex-direction: column; gap: 10px; color: var(--muted); font-size: 12px; }
.field input { padding: 13px 14px; background: var(--bg-soft); color: var(--text); font-size: 18px; }
.source-info { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin: 12px 0 24px; font-size: 11px; color: var(--muted); overflow-wrap: anywhere; }
.field textarea { width: 100%; min-height: 370px; resize: vertical; background: var(--panel); padding: 18px; font-family: ui-monospace, 'PingFang SC', monospace; font-size: 14px; line-height: 1.9; }
.editor-meta { display: flex; justify-content: space-between; gap: 12px; margin-top: 10px; color: var(--muted); font-size: 11px; }
.editor-actions { display: flex; justify-content: space-between; gap: 12px; align-items: center; border-top: 1px solid var(--border); padding-top: 22px; margin-top: 22px; }
.editor-actions > div { display: flex; gap: 10px; }
.delete-button { color: var(--danger); }
.editor-empty { display: flex; align-items: center; justify-content: center; flex-direction: column; text-align: center; min-height: 530px; gap: 20px; }
.editor-empty p { color: var(--muted); line-height: 1.9; font-size: 14px; }
.empty-symbol { width: 68px; height: 78px; display: grid; place-items: center; border: 1px solid var(--border); border-radius: 8px 20px 8px 8px; background: var(--bg-soft); font: 30px serif; color: var(--primary); }
.delete-dialog { max-width: min(450px, calc(100vw - 32px)); padding: 28px; border: 1px solid var(--border); border-radius: 18px; color: var(--text); background: var(--panel); box-shadow: var(--shadow); }
.delete-dialog::backdrop { background: #30241a70; backdrop-filter: blur(3px); }
.delete-dialog h2 { font-size: 24px; margin-bottom: 16px; }
.delete-dialog p { line-height: 1.8; overflow-wrap: anywhere; }
.delete-dialog > div { display: flex; justify-content: end; gap: 10px; margin-top: 24px; }
.danger { color: white; background: var(--danger); }
button:disabled { opacity: .5; cursor: not-allowed; transform: none; }
button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (max-width: 900px) { .knowledge-head { align-items: start; flex-direction: column; } .head-actions { align-items: start; } .knowledge-workspace { grid-template-columns: 260px minmax(0, 1fr); } .editor-panel { padding: 24px 20px; } }
@media (max-width: 680px) { .knowledge-page { padding: 24px 14px; } .knowledge-workspace { grid-template-columns: 1fr; } .document-library { border-right: 0; border-bottom: 1px solid var(--border); } .document-list { max-height: 240px; } .library-note { display: none; } .editor-actions { flex-wrap: wrap; } .editor-meta { flex-wrap: wrap; } .field textarea { min-height: 340px; } .editor-empty { min-height: 320px; } .source-info { flex-direction: column; } }
</style>
