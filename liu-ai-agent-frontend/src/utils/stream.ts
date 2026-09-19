import { authHeaders, handleUnauthorized } from '../api'

function normalizeSseBuffer(buffer: string) {
  return buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

function parseSseData(raw: string) {
  const dataLines: string[] = []
  for (const line of normalizeSseBuffer(raw).split('\n')) {
    if (!line.startsWith('data:')) continue
    let data = line.slice(5)
    if (data.startsWith(' ')) data = data.slice(1)
    dataLines.push(data)
  }
  return dataLines.join('\n')
}

function parseSseEvent(raw: string) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of normalizeSseBuffer(raw).split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      let data = line.slice(5)
      if (data.startsWith(' ')) data = data.slice(1)
      dataLines.push(data)
    }
  }
  return { event, data: dataLines.join('\n') }
}

function findEventBoundary(buffer: string) {
  const match = /(?:\r\n|\r|\n)(?:\r\n|\r|\n)/.exec(buffer)
  return match ? { index: match.index, length: match[0].length } : undefined
}

// Simple SSE reader over fetch ReadableStream
export async function streamGet(url: string, params: Record<string, string>, response?: Response) {
  let resp: Response
  if (response) {
    resp = response
  } else {
    const qs = new URLSearchParams(params).toString()
    resp = await fetch(`${url}?${qs}`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...authHeaders()
      }
    })
  }
  if (resp.status === 401) handleUnauthorized()
  if (!resp.ok) {
    const body = await resp.json().catch(() => null)
    throw new Error(body?.message || `HTTP ${resp.status}`)
  }
  if (!resp.body) throw new Error('服务器未返回消息流')
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  return {
    async read(onMessage: (data: string) => void) {
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          buffer += decoder.decode()
          const data = parseSseData(buffer)
          if (data) onMessage(data)
          break
        }
        buffer += decoder.decode(value, { stream: true })
        let boundary
        while ((boundary = findEventBoundary(buffer))) {
          const raw = buffer.slice(0, boundary.index)
          buffer = buffer.slice(boundary.index + boundary.length)
          const data = parseSseData(raw)
          if (data) onMessage(data)
        }
      }
    },
    async cancel() {
      try { await reader.cancel() } catch {}
    }
  }
}

// Advanced SSE reader that extracts event names and attempts JSON parsing
export async function streamGetEvents(
  url: string,
  params: Record<string, string>,
  response?: Response
) {
  let resp: Response
  if (response) {
    resp = response
  } else {
    const qs = new URLSearchParams(params).toString()
    resp = await fetch(`${url}?${qs}`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...authHeaders()
      }
    })
  }
  if (resp.status === 401) handleUnauthorized()
  if (!resp.ok) {
    const body = await resp.json().catch(() => null)
    throw new Error(body?.message || `HTTP ${resp.status}`)
  }
  if (!resp.body) throw new Error('服务器未返回消息流')
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  return {
    async read(onEvent: (evt: { event: string; data: string; json?: any }) => void) {
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          buffer += decoder.decode()
          const { event, data } = parseSseEvent(buffer)
          if (data) {
            let json: any | undefined
            try { json = JSON.parse(data) } catch {}
            onEvent({ event, data, json })
          }
          break
        }
        buffer += decoder.decode(value, { stream: true })
        let boundary
        while ((boundary = findEventBoundary(buffer))) {
          const chunk = buffer.slice(0, boundary.index)
          buffer = buffer.slice(boundary.index + boundary.length)
          const { event, data } = parseSseEvent(chunk)
          if (!data) continue
          let json: any | undefined
          try { json = JSON.parse(data) } catch {}
          onEvent({ event, data, json })
        }
      }
    },
    async cancel() {
      try { await reader.cancel() } catch {}
    }
  }
}
