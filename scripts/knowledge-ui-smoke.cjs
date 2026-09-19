// 使用本地模拟 API 验证管理员知识库界面，不调用真实模型或改动项目数据。
// PLAYWRIGHT_MODULE=/path/to/playwright node scripts/knowledge-ui-smoke.cjs
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    let role = 'ADMIN';
    const user = () => ({ id: 1, username: '知识库管理员', role, enabled: true });
    const docs = [
      { id: 'one', version: 0, title: '校园沟通指南', filename: '校园沟通指南.md', content: '# 校园沟通指南\n\n先了解对方的感受，再一起寻找下一步。\n\n## 日常相处\n\n留出独处时间，也为彼此安排专注交流的时刻。', builtin: true, updatedAt: new Date().toISOString() },
      { id: 'two', version: 0, title: '情绪支持', filename: '情绪支持.txt', content: '在情绪激动时，先暂停一下，再说明自己的需求。', builtin: false, updatedAt: new Date().toISOString() }
    ];
    let saves = 0, deletes = 0, managementRequests = 0, failSave = false;
    await context.addInitScript(() => {
      localStorage.setItem('liu_ai_token', 'fixture-token');
      localStorage.setItem('liu_ai_user', JSON.stringify({ id: 1, username: '知识库管理员', role: 'ADMIN', enabled: true }));
    });
    await context.route(/^https?:\/\/[^/]+\/api\//, async route => {
      const req = route.request(); const url = new URL(req.url()); const path = url.pathname.replace(/^\/api/, '');
      if (path === '/auth/me') return route.fulfill({ json: user() });
      if (!path.startsWith('/admin/knowledge/documents')) return route.fulfill({ json: [] });
      managementRequests++;
      if (role !== 'ADMIN') return route.fulfill({ status: 403, json: { message: '没有权限' } });
      const method = req.method(); const id = path.split('/')[4]; const doc = docs.find(item => item.id === id);
      if (method === 'GET') return route.fulfill({ json: id ? doc : docs.map(item => ({ ...item, content: undefined, characters: item.content.length })) });
      if (method === 'POST') {
        assert.ok(req.postData().includes('新知识.md'));
        const created = { id: 'new', version: 0, title: '新知识', filename: '新知识.md', content: '# 新知识\n周六开放校园读书会。', builtin: false, updatedAt: new Date().toISOString() };
        docs.unshift(created); return route.fulfill({ status: 201, json: created });
      }
      if (method === 'PUT') {
        if (failSave) return route.fulfill({ status: 503, json: { message: '知识索引更新失败，本次修改未保存，请稍后重试' } });
        const data = req.postDataJSON(); assert.equal(data.version, doc.version);
        Object.assign(doc, data, { version: doc.version + 1 }); saves++;
        return route.fulfill({ json: doc });
      }
      if (method === 'DELETE') {
        assert.equal(Number(url.searchParams.get('version')), doc.version);
        docs.splice(docs.indexOf(doc), 1); deletes++; return route.fulfill({ status: 204 });
      }
    });
    const page = await context.newPage(); const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto((process.env.UI_BASE_URL || 'http://localhost:5173') + '/admin/knowledge');
    await page.waitForLoadState('networkidle');
    console.log('Initial controls:', await page.getByRole('button').allTextContents());
    await page.getByLabel('文档标题', { exact: true }).fill('校园沟通手册');
    await page.getByLabel('文档内容', { exact: true }).fill('# 校园沟通手册\n\n周六下午一起在图书馆阅读。');
    await page.getByRole('button', { name: '保存修改', exact: true }).click();
    await page.getByText('文档已保存，问答知识已更新。', { exact: true }).waitFor();
    assert.equal(saves, 1);
    await page.reload(); await page.waitForLoadState('networkidle');
    assert.equal(await page.getByLabel('文档标题', { exact: true }).inputValue(), '校园沟通手册');
    failSave = true;
    await page.getByLabel('文档内容', { exact: true }).fill('更新失败时保留这段未保存的草稿。');
    await page.getByRole('button', { name: '保存修改', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: '本次修改未保存' }).waitFor();
    assert.equal(await page.getByLabel('文档内容', { exact: true }).inputValue(), '更新失败时保留这段未保存的草稿。');
    failSave = false;
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: '放弃修改', exact: true }).click();
    await page.getByLabel('文档内容', { exact: true }).filter({ visible: true }).waitFor();
    await page.waitForFunction(() => document.querySelector('textarea')?.value.includes('图书馆'));
    await page.getByLabel('选择知识文档', { exact: true }).setInputFiles({ name: '新知识.md', mimeType: 'text/markdown', buffer: Buffer.from('# 新知识\n周六开放校园读书会。') });
    await page.getByText('文档已上传，问答知识已更新。', { exact: true }).waitFor();
    await page.getByRole('button', { name: '删除文档', exact: true }).click();
    await page.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(deletes, 0);
    await page.getByRole('button', { name: '删除文档', exact: true }).click();
    await page.getByRole('button', { name: '确认删除', exact: true }).click();
    await page.getByText('文档已删除，相关内容已从知识检索中移除。', { exact: true }).waitFor();
    await page.waitForLoadState('networkidle'); assert.equal(deletes, 1);
    await page.getByLabel('搜索文档').fill('情绪支持'); assert.equal(await page.locator('.document-item').count(), 1);
    await page.getByLabel('搜索文档').fill('');
    fs.mkdirSync('output/knowledge-admin', { recursive: true });
    await page.screenshot({ path: 'output/knowledge-admin/desktop.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'output/knowledge-admin/mobile.png', fullPage: true });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'no mobile horizontal overflow');
    // 即便浏览器缓存仍伪装成 ADMIN，进入管理页时会按服务端最新角色重新检查。
    role = 'USER'; managementRequests = 0;
    await page.goto((process.env.UI_BASE_URL || 'http://localhost:5173') + '/admin/knowledge');
    await page.waitForURL('**/'); await page.waitForLoadState('networkidle');
    assert.equal(await page.getByRole('link', { name: '知识库', exact: true }).count(), 0);
    assert.equal(managementRequests, 0);
    assert.deepEqual(errors, []);
    console.log('PASS: admin upload/edit/delete/search, persisted reload UI, failed-save draft, delete confirmation, mobile layout, ordinary-user invisibility and route guard.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
