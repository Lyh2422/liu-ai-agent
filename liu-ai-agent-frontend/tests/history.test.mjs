import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { transformWithEsbuild } from 'vite'

const source = await readFile(new URL('../src/utils/history.ts', import.meta.url), 'utf8')
const { code } = await transformWithEsbuild(source, 'history.ts', { loader: 'ts', target: 'es2020' })
const { mergeHistory } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const message = (id, role = 'user', content = id) => ({ id: `${id}-${role}`, role, content, status: 'COMPLETED' })

test('旧页按完整轮次插入，并对重叠消息去重', () => {
  const merged = mergeHistory([message('3', 'assistant'), message('3')], [message('2', 'assistant'), message('2'), message('3')])
  assert.deepEqual(merged.map(item => item.id), ['2-user', '2-assistant', '3-user', '3-assistant'])
})

test('回复完成后的最新页同步保留已展开的旧页，并替换本地临时消息', () => {
  const merged = mergeHistory([message('1'), message('2', 'assistant', '生成中'), { role: 'user', content: '临时问题' }],
    [message('2', 'assistant', '完整回复'), message('3'), message('3', 'assistant')])
  assert.equal(merged[0].id, '1-user')
  assert.equal(merged[1].content, '完整回复')
  assert.equal(merged.length, 4)
  assert.ok(merged.every(item => item.id))
})

test('较大的数据库轮次 ID 仍能精确排序', () => {
  const merged = mergeHistory([message('9007199254740993')], [message('9007199254740992')])
  assert.equal(merged[0].id, '9007199254740992-user')
})
