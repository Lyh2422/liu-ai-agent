<template>
  <section class="message-page container">
    <div class="messenger-shell">
      <aside class="room-rail">
        <div class="rail-head">
          <div>
            <p>同学之间</p>
            <h1>消息</h1>
          </div>
          <div class="rail-actions">
            <button type="button" title="发起单聊" @click="composer = composer === 'direct' ? null : 'direct'">＋</button>
            <button type="button" title="创建群聊" @click="composer = composer === 'group' ? null : 'group'">群</button>
          </div>
        </div>

        <form v-if="composer === 'direct'" class="quick-form" @submit.prevent="openDirect">
          <label>按唯一 ID 发起单聊</label>
          <div><input v-model.trim="directId" class="mono" placeholder="UXXXXXXXXXX" maxlength="20" /><button>开始</button></div>
        </form>
        <form v-if="composer === 'group'" class="quick-form group-form" @submit.prevent="createGroup">
          <label>创建一个新群聊</label>
          <input v-model.trim="groupName" placeholder="群聊名称" maxlength="80" />
          <textarea v-model.trim="groupIds" rows="2" placeholder="成员唯一 ID，多个用空格或逗号分隔" />
          <button>创建群聊</button>
        </form>
        <p v-if="notice" :class="noticeOk ? 'rail-ok' : 'rail-error'">{{ notice }}</p>

        <div class="room-list">
          <button
            v-for="room in rooms"
            :key="room.id"
            :class="['room-item', { active: room.id === activeRoomId }]"
            type="button"
            @click="selectRoom(room.id)"
          >
            <span class="room-avatar-wrap">
              <span :class="['room-mark', room.type.toLowerCase()]">{{ room.type === 'GROUP' ? '群' : room.name.slice(0, 1) }}</span>
              <span
                v-if="room.unreadCount > 0"
                class="room-unread-badge"
                :aria-label="`${room.unreadCount} 条未读消息`"
              >{{ unreadLabel(room.unreadCount) }}</span>
            </span>
            <span class="room-copy">
              <span class="room-line"><strong>{{ room.name }}</strong><time>{{ shortTime(room.lastMessageAt || room.updatedAt) }}</time></span>
              <small>{{ room.lastMessage || (room.type === 'GROUP' ? `${room.members.length} 位成员` : '还没有消息') }}</small>
            </span>
          </button>
          <div v-if="!rooms.length && !loadingRooms" class="empty-rooms">
            <span>信</span>
            <p>还没有会话。用同学 ID 发起单聊，或者建一个小群。</p>
          </div>
        </div>
      </aside>

      <main v-if="activeRoom" class="chat-stage">
        <header class="chat-head">
          <div>
            <div class="title-line">
              <h2>{{ activeRoom.name }}</h2>
              <span>{{ activeRoom.type === 'GROUP' ? '群聊' : '单聊' }}</span>
            </div>
            <p>{{ activeRoom.members.map(member => member.username).join('、') }}</p>
          </div>
          <button
            v-if="canInvite"
            class="invite-toggle"
            type="button"
            @click="showInvite = !showInvite"
          >邀请成员</button>
        </header>

        <form v-if="showInvite && canInvite" class="invite-form" @submit.prevent="inviteMember">
          <span>输入新成员的唯一 ID</span>
          <input v-model.trim="inviteId" class="mono" placeholder="UXXXXXXXXXX" maxlength="20" />
          <button class="btn primary">邀请</button>
        </form>

        <div ref="messageList" class="message-list">
          <div class="date-rule"><span>最近 100 条消息</span></div>
          <article
            v-for="message in chatMessages"
            :key="message.id"
            :class="['message-row', { mine: message.sender.publicId === authState.user?.publicId }]"
          >
            <img :src="avatarSrc(message.sender.avatarUrl)" alt="" />
            <div class="message-content">
              <div class="message-meta">
                <strong>{{ message.sender.username }}</strong>
                <time>{{ fullTime(message.createdAt) }}</time>
              </div>
              <p>{{ message.content }}</p>
            </div>
          </article>
          <div v-if="!chatMessages.length && !loadingMessages" class="empty-chat">
            <span>还没人开口。说句“在吗”也行。</span>
          </div>
        </div>

        <form class="message-composer" @submit.prevent="sendMessage">
          <textarea
            v-model="draft"
            rows="2"
            maxlength="2000"
            placeholder="写下消息，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="sendMessage"
          />
          <div>
            <span>{{ draft.length }}/2000</span>
            <button class="send-button" :disabled="sending || !draft.trim()" type="submit">发送</button>
          </div>
        </form>
      </main>

      <main v-else class="blank-stage">
        <div class="blank-seal">信</div>
        <h2>选一位朋友，开始聊天</h2>
        <p>单聊和群聊都会留在当前账号的会话列表里。</p>
      </main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { socialApi, type SocialMessage, type SocialRoom } from '../api'
import { authState, loadCurrentUser } from '../stores/auth'
import { avatarSrc } from '../utils/avatar'
import { errorMessage } from '../utils/errors'

const route = useRoute()
const router = useRouter()
const rooms = ref<SocialRoom[]>([])
const activeRoomId = ref('')
const chatMessages = ref<SocialMessage[]>([])
const composer = ref<'direct' | 'group' | null>(null)
const directId = ref('')
const groupName = ref('')
const groupIds = ref('')
const inviteId = ref('')
const showInvite = ref(false)
const notice = ref('')
const noticeOk = ref(false)
const draft = ref('')
const sending = ref(false)
const loadingRooms = ref(false)
const loadingMessages = ref(false)
const messageList = ref<HTMLElement | null>(null)
let pollTimer: number | undefined

const activeRoom = computed(() => rooms.value.find(room => room.id === activeRoomId.value) || null)
const canInvite = computed(() => activeRoom.value?.type === 'GROUP'
  && activeRoom.value.ownerPublicId === authState.user?.publicId)

onMounted(async () => {
  await loadCurrentUser()
  await refreshRooms()
  const requested = typeof route.params.roomId === 'string' ? route.params.roomId : ''
  const initial = rooms.value.some(room => room.id === requested) ? requested : rooms.value[0]?.id
  if (initial) await selectRoom(initial, requested !== initial)
  pollTimer = window.setInterval(poll, 2500)
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

watch(() => route.params.roomId, async value => {
  if (typeof value === 'string' && value && value !== activeRoomId.value) {
    await selectRoom(value, false)
  }
})

async function poll() {
  if (activeRoomId.value) await loadMessages(false)
  await refreshRooms(false)
}

async function refreshRooms(showError = true) {
  loadingRooms.value = true
  try {
    rooms.value = (await socialApi.rooms()).data
  } catch (e) {
    if (showError) setNotice(errorMessage(e, '会话加载失败'), false)
  } finally {
    loadingRooms.value = false
  }
}

async function selectRoom(roomId: string, navigate = true) {
  activeRoomId.value = roomId
  showInvite.value = false
  chatMessages.value = []
  if (navigate) await router.replace({ name: 'messages', params: { roomId } })
  try {
    const detail = (await socialApi.room(roomId)).data
    const index = rooms.value.findIndex(room => room.id === roomId)
    if (index >= 0) rooms.value[index] = detail
  } catch (e) {
    setNotice(errorMessage(e, '聊天信息加载失败'), false)
  }
  await loadMessages(true)
  await refreshRooms(false)
  notifyUnreadChanged()
}

async function loadMessages(forceScroll: boolean) {
  if (!activeRoomId.value) return
  const previousLastId = chatMessages.value.at(-1)?.id
  loadingMessages.value = true
  try {
    const next = (await socialApi.messages(activeRoomId.value)).data
    chatMessages.value = next
    const nextLastId = next.at(-1)?.id
    if (forceScroll || previousLastId !== nextLastId) {
      await nextTick()
      messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: forceScroll ? 'auto' : 'smooth' })
    }
  } catch (e) {
    setNotice(errorMessage(e, '消息加载失败'), false)
  } finally {
    loadingMessages.value = false
  }
}

async function openDirect() {
  if (!directId.value) return
  try {
    const room = (await socialApi.openDirect(directId.value)).data
    directId.value = ''
    composer.value = null
    await refreshRooms()
    await selectRoom(room.id)
  } catch (e) {
    setNotice(errorMessage(e, '无法发起单聊'), false)
  }
}

async function createGroup() {
  if (!groupName.value) return
  const ids = groupIds.value.split(/[\s,，;；]+/).map(value => value.trim()).filter(Boolean)
  try {
    const room = (await socialApi.createGroup(groupName.value, ids)).data
    groupName.value = ''
    groupIds.value = ''
    composer.value = null
    await refreshRooms()
    await selectRoom(room.id)
    setNotice('群聊已创建', true)
  } catch (e) {
    setNotice(errorMessage(e, '群聊创建失败'), false)
  }
}

async function inviteMember() {
  if (!activeRoomId.value || !inviteId.value) return
  try {
    await socialApi.invite(activeRoomId.value, inviteId.value)
    inviteId.value = ''
    showInvite.value = false
    await refreshRooms()
    setNotice('成员已加入群聊', true)
  } catch (e) {
    setNotice(errorMessage(e, '邀请失败'), false)
  }
}

async function sendMessage() {
  const content = draft.value.trim()
  if (!content || !activeRoomId.value || sending.value) return
  sending.value = true
  try {
    const message = (await socialApi.sendMessage(activeRoomId.value, content)).data
    chatMessages.value.push(message)
    draft.value = ''
    await refreshRooms(false)
    notifyUnreadChanged()
    await nextTick()
    messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
  } catch (e) {
    setNotice(errorMessage(e, '发送失败'), false)
  } finally {
    sending.value = false
  }
}

function setNotice(message: string, ok: boolean) {
  notice.value = message
  noticeOk.value = ok
  window.setTimeout(() => { if (notice.value === message) notice.value = '' }, 3200)
}

function shortTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const today = new Date()
  return date.toDateString() === today.toDateString()
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function fullTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function unreadLabel(count: number) {
  return count > 99 ? '99+' : String(count)
}

function notifyUnreadChanged() {
  window.dispatchEvent(new Event('social-unread-changed'))
}
</script>

<style scoped>
.message-page { height: calc(100vh - 72px); padding-top: 24px; padding-bottom: 24px; }
.messenger-shell { display: grid; grid-template-columns: 330px minmax(0, 1fr); height: 100%; min-height: 590px; overflow: hidden; border: 1px solid var(--border); border-radius: 5px 24px 5px 24px; background: rgba(255, 253, 249, .94); box-shadow: var(--shadow); }
.room-rail { display: flex; min-width: 0; flex-direction: column; border-right: 1px solid var(--border); background: rgba(238, 232, 220, .78); }
.rail-head { display: flex; align-items: flex-end; justify-content: space-between; padding: 24px 20px 16px; }
.rail-head p { color: var(--muted); font-size: 11px; letter-spacing: .14em; }
.rail-head h1 { margin-top: 3px; font-size: 34px; }
.rail-actions { display: flex; gap: 7px; }
.rail-actions button { width: 36px; height: 36px; border: 1px solid var(--border); border-radius: 12px; color: var(--primary-strong); cursor: pointer; background: rgba(255, 253, 249, .82); font: 700 15px ui-serif, serif; }
.quick-form { display: grid; gap: 8px; margin: 0 14px 12px; padding: 13px; border: 1px solid var(--border); border-radius: 15px; background: rgba(255, 253, 249, .88); animation: reveal .18s ease-out; }
.quick-form label { color: var(--muted); font-size: 12px; }
.quick-form > div { display: grid; grid-template-columns: 1fr auto; gap: 6px; }
.quick-form input, .quick-form textarea { min-width: 0; width: 100%; padding: 9px 10px; resize: none; text-transform: none; }
.quick-form button { padding: 9px 12px; border: 0; border-radius: 10px; color: #fff; cursor: pointer; background: var(--primary-strong); }
.quick-form div input { text-transform: uppercase; }
.rail-ok, .rail-error { margin: 0 18px 10px; font-size: 12px; }
.rail-ok { color: var(--accent-strong); } .rail-error { color: var(--danger); }
.room-list { flex: 1; min-height: 0; padding: 4px 10px 16px; overflow-y: auto; }
.room-item { display: grid; grid-template-columns: 46px minmax(0, 1fr); gap: 11px; width: 100%; padding: 11px 10px; text-align: left; border: 0; border-radius: 15px; color: var(--text); cursor: pointer; background: transparent; transition: background .18s ease, transform .18s ease; }
.room-item:hover { transform: translateX(2px); background: rgba(255, 255, 255, .58); }
.room-item.active { background: #fffdf9; box-shadow: 0 8px 22px rgba(119, 78, 45, .1); }
.room-avatar-wrap { position: relative; width: 46px; height: 46px; }
.room-mark { display: grid; place-items: center; width: 46px; height: 46px; border-radius: 16px 5px 16px 5px; color: #fff; font: 700 18px ui-serif, serif; background: var(--coral-dark); }
.room-mark.group { background: var(--green); }
.room-unread-badge {
  position: absolute;
  top: -5px;
  right: -6px;
  display: grid;
  place-items: center;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  border: 2px solid #fff9f2;
  border-radius: 999px;
  color: #fff;
  background: #d73a36;
  box-shadow: 0 4px 10px rgba(181, 38, 34, .3);
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.room-copy { display: grid; min-width: 0; gap: 5px; align-content: center; }
.room-line { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.room-line strong, .room-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.room-line time { flex: 0 0 auto; color: #9c8271; font-size: 10px; }
.room-copy small { color: var(--muted); font-size: 12px; }
.empty-rooms { display: grid; gap: 10px; place-items: center; padding: 50px 28px; text-align: center; color: var(--muted); line-height: 1.6; }
.empty-rooms span { color: var(--primary); font-size: 28px; }
.chat-stage { display: grid; min-width: 0; min-height: 0; grid-template-rows: auto auto 1fr auto; background: rgba(251, 249, 243, .86); }
.chat-head { display: flex; align-items: center; justify-content: space-between; gap: 18px; min-height: 82px; padding: 17px 24px; border-bottom: 1px solid var(--border); }
.title-line { display: flex; align-items: center; gap: 9px; }
.title-line h2 { font-size: 24px; }
.title-line span { padding: 4px 7px; border-radius: 999px; color: var(--accent-strong); background: rgba(142, 154, 86, .14); font-size: 10px; }
.chat-head p { max-width: 640px; margin-top: 5px; overflow: hidden; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.invite-toggle { flex: 0 0 auto; padding: 9px 12px; border: 1px solid var(--border); border-radius: 11px; color: var(--primary-strong); cursor: pointer; background: var(--panel); }
.invite-form { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding: 10px 24px; border-bottom: 1px solid var(--border); background: rgba(246, 231, 212, .7); }
.invite-form span { color: var(--muted); font-size: 12px; }
.invite-form input { padding: 9px 11px; text-transform: uppercase; }
.invite-form .btn { padding: 9px 13px; }
.message-list { min-height: 0; overflow-y: auto; padding: 20px 26px 28px; scroll-behavior: smooth; }
.date-rule { display: flex; align-items: center; gap: 12px; margin: 2px 0 22px; color: #a28876; font-size: 10px; }
.date-rule::before, .date-rule::after { content: ''; height: 1px; flex: 1; background: var(--border); }
.message-row { display: flex; align-items: flex-start; gap: 10px; margin: 0 0 17px; animation: message-in .2s ease-out; }
.message-row > img { width: 36px; height: 36px; border-radius: 13px; object-fit: cover; background: var(--muted-surface); }
.message-content { max-width: min(72%, 620px); }
.message-meta { display: flex; align-items: baseline; gap: 8px; margin: 0 3px 5px; }
.message-meta strong { font-size: 12px; }.message-meta time { color: #a28876; font-size: 10px; }
.message-content > p { padding: 11px 14px; border: 1px solid var(--border); border-radius: 5px 16px 16px 16px; background: #fffdf9; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.message-row.mine { flex-direction: row-reverse; }
.message-row.mine .message-meta { justify-content: flex-end; }
.message-row.mine .message-content > p { color: #fffaf5; border-color: transparent; border-radius: 16px 5px 16px 16px; background: var(--green); }
.empty-chat { display: grid; place-items: center; height: 70%; color: var(--muted); font-family: ui-serif, Georgia, serif; }
.message-composer { display: grid; gap: 8px; padding: 15px 20px 17px; border-top: 1px solid var(--border); background: rgba(255, 250, 244, .86); }
.message-composer textarea { width: 100%; min-height: 54px; padding: 12px 14px; resize: none; background: rgba(255, 253, 249, .92); }
.message-composer > div { display: flex; align-items: center; justify-content: space-between; color: var(--muted); font-size: 10px; }
.send-button { padding: 9px 22px; border: 0; border-radius: 12px; color: #fff; cursor: pointer; background: var(--green); box-shadow: 0 3px 0 var(--green-dark); }
.send-button:disabled { cursor: default; opacity: .45; }
.blank-stage { display: grid; align-content: center; justify-items: center; gap: 12px; padding: 40px; text-align: center; color: var(--muted); background: radial-gradient(circle, rgba(240, 217, 190, .6), transparent 48%); }
.blank-stage h2 { color: var(--text); font-size: 27px; }.blank-stage p { line-height: 1.7; }
.blank-seal { display: grid; place-items: center; width: 72px; height: 72px; margin-bottom: 6px; color: #fff8ed; border-radius: 24px 8px 24px 8px; background: var(--primary-strong); font: 700 29px ui-serif, serif; transform: rotate(-4deg); }
@keyframes reveal { from { opacity: 0; transform: translateY(-5px); } }
@keyframes message-in { from { opacity: 0; transform: translateY(4px); } }
@media (max-width: 760px) {
  .message-page { height: auto; min-height: calc(100vh - 126px); padding: 12px 10px; }
  .messenger-shell { grid-template-columns: 1fr; height: auto; min-height: 760px; }
  .room-rail { max-height: 330px; border-right: 0; border-bottom: 1px solid var(--border); }
  .chat-stage { min-height: 600px; }
  .invite-form { grid-template-columns: 1fr auto; }.invite-form span { grid-column: 1 / -1; }
  .message-content { max-width: 84%; }
}
</style>
