// UI 冒烟测试使用模拟 API，不调用付费模型，也不改动真实账号和会话。
// PLAYWRIGHT_MODULE=/path/to/playwright node scripts/conversation-ui-smoke.cjs
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      localStorage.setItem('liu_ai_token', 'ui-fixture');
      localStorage.setItem('liu_ai_user', JSON.stringify({ id: 1, username: '界面测试', role: 'USER', enabled: true }));
    });
    const conversations = [];
    const messages = new Map();
    await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname.replace(/^\/api/, '');
      if (request.method() === 'OPTIONS') return route.fulfill({ status: 204 });
      let body;
      if (path === '/ai/conversations' && request.method() === 'POST') {
        body = { id: 'fixture-' + (conversations.length + 1), appType: request.postDataJSON().appType, title: '新会话', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
        conversations.unshift(body); messages.set(body.id, []);
      } else if (path === '/ai/conversations') body = conversations.filter(item => item.appType === url.searchParams.get('appType')).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
      else if (path.startsWith('/ai/conversations/')) {
        const id = path.split('/').pop(); body = { conversation: conversations.find(item => item.id === id), messages: messages.get(id) };
      } else if (path.endsWith('/chat/sse') || path.endsWith('/manus/chat')) {
        const { chatId, message } = request.postDataJSON();
        const conversation = conversations.find(item => item.id === chatId);
        if (conversation.title === '新会话') conversation.title = message.slice(0, 40);
        conversation.updatedAt = new Date().toISOString();
        const history = messages.get(chatId);
        const answer = message.includes('叫什么') ? '你叫小林，记得你想在周五表白。' : '收到，我们一起把这件事想清楚。';
        history.push({ role: 'user', content: message, status: 'COMPLETED' }, { role: 'assistant', content: answer, status: 'COMPLETED' });
        return route.fulfill({ contentType: 'text/event-stream', body: `event: ack\ndata: thinking\n\nevent: delta\ndata: ${answer}\n\nevent: done\ndata: completed\n\n` });
      } else if (path === '/auth/me') body = { id: 1, username: '界面测试', role: 'USER', enabled: true };
      else return route.fulfill({ status: 404, json: { message: 'unexpected fixture route' } });
      return route.fulfill({ json: body });
    });
    await page.goto((process.env.UI_BASE_URL || 'http://localhost:5173') + '/love');
    await page.waitForLoadState('networkidle');
    console.log('Initial buttons:', await page.getByRole('button').allTextContents());
    await page.getByRole('button', { name: '＋ 新建会话', exact: true }).click();
    await page.getByRole('textbox', { name: '消息内容' }).fill('我叫小林，周五想表白');
    await page.getByRole('button', { name: '发送', exact: true }).click();
    await page.getByText('收到，我们一起把这件事想清楚。', { exact: true }).waitFor();
    await page.getByRole('button', { name: '＋ 新建会话', exact: true }).click();
    await page.locator('.empty-state').waitFor();
    assert.equal(await page.locator('.row').count(), 0);
    await page.getByRole('textbox', { name: '消息内容' }).fill('这是另一个独立话题');
    await page.getByRole('button', { name: '发送', exact: true }).click();
    await page.getByText('收到，我们一起把这件事想清楚。', { exact: true }).waitFor();
    await page.getByRole('button', { name: /我叫小林，周五想表白/ }).click();
    await page.locator('.row.user').filter({ hasText: '我叫小林，周五想表白' }).waitFor();
    assert.equal(await page.locator('.row.user').innerText(), '我叫小林，周五想表白');
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.locator('.row.user').filter({ hasText: '我叫小林，周五想表白' }).waitFor();
    assert.equal(await page.locator('.row.user').innerText(), '我叫小林，周五想表白');
    await page.getByRole('textbox', { name: '消息内容' }).fill('我叫什么？');
    await page.getByRole('button', { name: '发送', exact: true }).click();
    await page.getByText('你叫小林，记得你想在周五表白。', { exact: true }).waitFor();
    assert.equal(await page.locator('.row').count(), 4);
    fs.mkdirSync('output/history-chat', { recursive: true });
    await page.screenshot({ path: 'output/history-chat/desktop.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'output/history-chat/mobile.png', fullPage: true });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile must not overflow');
    await page.goto((process.env.UI_BASE_URL || 'http://localhost:5173') + '/manus');
    await page.waitForLoadState('networkidle');
    assert.equal(await page.locator('.history-item').count(), 0);
    await page.getByRole('textbox', { name: '消息内容' }).fill('帮我安排学习计划');
    await page.getByRole('button', { name: '发送', exact: true }).click();
    await page.getByText('收到，我们一起把这件事想清楚。', { exact: true }).waitFor();
    await page.reload();
    await page.waitForLoadState('networkidle');
    assert.equal(await page.locator('.row').count(), 2);
    assert.deepEqual(errors, []);
    console.log('PASS: create, send, switch, reload, continuation UI, mobile layout, app isolation, Manus history.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
