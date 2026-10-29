import type { ChatMessage } from '../components/ChatWindow.vue'
import type { HistoryMessage } from '../api'

/** 用持久化 ID 合并旧页和最新页；同步后用服务端记录替换本地的临时消息。 */
export function mergeHistory(current: ChatMessage[], incoming: HistoryMessage[]): ChatMessage[] {
  const messages = new Map<string, ChatMessage>()
  for (const message of current) if (message.id) messages.set(message.id, message)
  for (const message of incoming) messages.set(message.id, message)
  return [...messages.values()].sort((left, right) => {
    const [leftTurn, leftRole] = left.id!.split('-')
    const [rightTurn, rightRole] = right.id!.split('-')
    const difference = BigInt(leftTurn) - BigInt(rightTurn)
    if (difference !== 0n) return difference > 0n ? 1 : -1
    return leftRole === rightRole ? 0 : leftRole === 'user' ? -1 : 1
  })
}
