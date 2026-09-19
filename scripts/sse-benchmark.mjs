import { performance } from 'node:perf_hooks'

const API_BASE = (process.env.API_BASE || 'http://localhost:8123/api').replace(/\/$/, '')
const API_TOKEN = process.env.API_TOKEN
const CONCURRENCY = Number(process.env.CONCURRENCY || 1)
const REQUESTS = Number(process.env.REQUESTS || 4)
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS || 120000)
const MESSAGE = process.env.MESSAGE || '恋爱中如何平衡学习和约会？'
const headers = { Authorization: `Bearer ${API_TOKEN}`, 'Content-Type': 'application/json' }
if (!API_TOKEN) throw new Error('请通过 API_TOKEN 环境变量提供登录 token；脚本会新建会话并调用聊天接口')
for (const [name, value] of Object.entries({ CONCURRENCY, REQUESTS, TIMEOUT_MS })) {
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer`)
}

function percentile(values, ratio) {
  const sorted = values.filter(Number.isFinite).sort((a, b) => a - b)
  return sorted.length ? sorted[Math.max(0, Math.ceil(sorted.length * ratio) - 1)] : null
}
const ms = value => value == null ? '-' : `${value.toFixed(1)} ms`

async function runOne() {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    // 每次使用同一账号的新会话，使历史上下文相同；不伪造不存在的 chatId。
    const created = await fetch(`${API_BASE}/ai/conversations`, {
      method: 'POST', headers, body: JSON.stringify({ appType: 'LOVE' }), signal: controller.signal
    })
    if (!created.ok) throw new Error(`新建会话 HTTP ${created.status}`)
    const conversation = await created.json()
    const started = performance.now()
    const response = await fetch(`${API_BASE}/ai/love_app/chat/sse`, {
      method: 'POST', headers: { ...headers, Accept: 'text/event-stream' },
      body: JSON.stringify({ chatId: conversation.id, message: MESSAGE }), signal: controller.signal
    })
    if (!response.ok || !response.body) throw new Error(`聊天 HTTP ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = '', firstByte = null, ack = null, firstDelta = null, terminal = false, characters = 0
    function consume(frame) {
      let event = 'message'
      const data = []
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      const content = data.join('\n')
      const elapsed = performance.now() - started
      if (event === 'ack') ack ??= elapsed
      if (event === 'delta' && content) { firstDelta ??= elapsed; characters += [...content].length }
      if (event === 'error') throw new Error(`SSE error: ${content}`)
      if (event === 'done') terminal = true
    }
    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        if (value.length) firstByte ??= performance.now() - started
        buffer += decoder.decode(value, { stream: true })
        let match
        while ((match = /\r?\n\r?\n/.exec(buffer))) {
          consume(buffer.slice(0, match.index))
          buffer = buffer.slice(match.index + match[0].length)
        }
      }
      buffer += decoder.decode()
      if (buffer.trim()) consume(buffer)
      if (!terminal) throw new Error('SSE 连接中断，未收到 done')
      return { ok: true, firstByte, ack, firstDelta, total: performance.now() - started, characters }
    } finally { await reader.cancel().catch(() => {}) }
  } catch (error) {
    return { ok: false, error: error.name === 'AbortError' ? `timeout after ${TIMEOUT_MS} ms` : error.message }
  } finally { clearTimeout(timer) }
}

const results = []
let next = 0
await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
  while (true) {
    const index = next++
    if (index >= REQUESTS) return
    const result = await runOne()
    results[index] = result
    console.log(result.ok
      ? `#${index + 1} first-body-byte=${ms(result.firstByte)} | ack=${ms(result.ack)} | first-delta=${ms(result.firstDelta)} | total=${ms(result.total)} | answer-chars=${result.characters}`
      : `#${index + 1} failed: ${result.error}`)
  }
}))
const successful = results.filter(result => result.ok)
console.log(`success=${successful.length}/${REQUESTS}`)
for (const field of ['firstByte', 'ack', 'firstDelta', 'total']) {
  const values = successful.map(result => result[field])
  console.log(`${field} p50/p95: ${ms(percentile(values, .5))} / ${ms(percentile(values, .95))}`)
}
if (successful.length !== REQUESTS) process.exitCode = 1
