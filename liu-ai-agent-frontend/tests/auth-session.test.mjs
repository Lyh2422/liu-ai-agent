import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { transformWithEsbuild } from 'vite'

const source = await readFile(new URL('../src/utils/authSession.ts', import.meta.url), 'utf8')
const { code } = await transformWithEsbuild(source, 'authSession.ts', { loader: 'ts', target: 'es2020' })
const authSession = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)

class MemoryStorage {
  values = new Map()
  getItem(key) { return this.values.get(key) ?? null }
  setItem(key, value) { this.values.set(key, String(value)) }
  removeItem(key) { this.values.delete(key) }
}

test('两个窗口的登录状态在刷新恢复后仍互相隔离', () => {
  const windowA = new MemoryStorage()
  const windowB = new MemoryStorage()

  authSession.writeAuth(windowA, 'token-a', { username: '账号甲' })
  authSession.writeAuth(windowB, 'token-b', { username: '账号乙' })

  assert.equal(authSession.readAuthToken(windowA), 'token-a')
  assert.equal(authSession.readAuthUser(windowA).username, '账号甲')
  assert.equal(authSession.readAuthToken(windowB), 'token-b')
  assert.equal(authSession.readAuthUser(windowB).username, '账号乙')

  authSession.clearStoredAuth(windowA)
  assert.equal(authSession.readAuthToken(windowA), '')
  assert.equal(authSession.readAuthToken(windowB), 'token-b')
  assert.equal(authSession.readAuthUser(windowB).username, '账号乙')
})

test('损坏的用户缓存不会阻止页面恢复', () => {
  const storage = new MemoryStorage()
  storage.setItem(authSession.AUTH_USER_KEY, '{broken json')
  assert.equal(authSession.readAuthUser(storage), null)
})
