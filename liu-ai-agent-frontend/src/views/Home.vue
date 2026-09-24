<template>
  <div class="home">
    <section class="hero container">
      <div class="hero-copy">
        <p class="date-line">{{ dateLabel }}，{{ authState.user?.username || '同学' }}</p>
        <h1>有些话，<br><em>先在这里说说。</em></h1>
        <p class="lead">关系里的别扭、没来由的难过，或者一条怎么都回不好的消息。你可以慢慢讲，不用先把自己整理好。</p>
        <div class="hero-actions">
          <router-link class="btn primary" to="/love">去心事小屋</router-link>
          <router-link class="text-link" to="/messages">找同学聊聊 <span>→</span></router-link>
        </div>
        <p class="privacy-note"><span>私</span> 对话只保存在你的账号里。它会陪你梳理，但不会替你做决定。</p>
      </div>

      <div class="notice-board" aria-label="今日校园便签">
        <span class="tape tape-one" />
        <span class="tape tape-two" />
        <article class="paper-note main-note">
          <p class="note-label">今天可以聊</p>
          <h2>“明明很在意，<br>为什么一开口就吵架？”</h2>
          <div class="note-lines">
            <span>先分清情绪和事实</span>
            <span>再想想真正想让对方知道什么</span>
          </div>
          <router-link to="/love">带着这件事去聊 →</router-link>
        </article>
        <article class="paper-note side-note">
          <p>校园小事也能问</p>
          <strong>课程安排、社团方案、文档整理……</strong>
          <router-link to="/manus">去校园问答</router-link>
        </article>
        <span class="scribble">慢一点，也没关系</span>
      </div>
    </section>

    <section class="campus-entry container" aria-labelledby="entry-title">
      <div class="section-heading">
        <div><p>从你现在最需要的地方开始</p><h2 id="entry-title">今天来这里做什么？</h2></div>
        <span>三种入口，互不打扰</span>
      </div>

      <div class="entry-grid">
        <router-link class="entry-card heart-card" to="/love">
          <span class="card-index">01</span>
          <div class="card-symbol">心</div>
          <div><h3>说说心事</h3><p>恋爱、友情、情绪，想到哪儿说到哪儿。</p></div>
          <span class="card-go">进入小屋 →</span>
        </router-link>
        <router-link class="entry-card campus-card" to="/manus">
          <span class="card-index">02</span>
          <div class="card-symbol">问</div>
          <div><h3>处理校园事务</h3><p>把任务和资料丢过来，一起理清下一步。</p></div>
          <span class="card-go">开始问答 →</span>
        </router-link>
        <router-link class="entry-card friend-card" to="/messages">
          <span class="card-index">03</span>
          <div class="card-symbol">信</div>
          <div><h3>联系同学</h3><p>按唯一 ID 找到朋友，也可以拉个小群聊。</p></div>
          <span class="card-go">打开消息 →</span>
        </router-link>
      </div>
    </section>

    <section class="safety-strip container">
      <span class="safety-mark">!</span>
      <p><strong>需要现实中的帮助时，请先找人。</strong> 如果你正面临人身安全风险或强烈的心理危机，请尽快联系身边可信任的同学、老师、家人或当地紧急援助服务。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { authState } from '../stores/auth'

const dateLabel = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 19) return '下午好'
  return '晚上好'
})
</script>

<style scoped>
.home { padding: 64px 0 46px; overflow: hidden; }
.hero { display: grid; grid-template-columns: minmax(0, .92fr) minmax(480px, 1.08fr); gap: 76px; align-items: center; }
.hero-copy { position: relative; z-index: 1; animation: rise-in .6s ease-out both; }
.date-line { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; color: var(--green); font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.date-line::before { content: ''; width: 34px; height: 2px; background: var(--coral); }
.hero h1 { max-width: 8em; font-size: clamp(48px, 6vw, 78px); line-height: 1.04; }
.hero h1 em { color: var(--coral-dark); font-style: normal; }
.lead { max-width: 32rem; margin-top: 24px; color: var(--muted); font-size: 17px; line-height: 1.9; }
.hero-actions { display: flex; align-items: center; gap: 24px; margin-top: 30px; }
.hero-actions .btn { min-width: 138px; }
.text-link { color: var(--green); text-decoration: none; font-weight: 700; }
.text-link span { display: inline-block; margin-left: 4px; transition: transform .16s ease; }
.text-link:hover span { transform: translateX(4px); }
.privacy-note { display: flex; align-items: center; gap: 9px; max-width: 29rem; margin-top: 28px; color: var(--muted); font-size: 12px; line-height: 1.6; }
.privacy-note span { display: grid; flex: 0 0 auto; place-items: center; width: 25px; height: 25px; border: 1px solid var(--green); border-radius: 50%; color: var(--green); font: 700 12px "Songti SC", serif; }

.notice-board { position: relative; min-height: 470px; border: 14px solid #785944; border-radius: 6px; background: #c8b697; box-shadow: 0 28px 64px rgba(56, 49, 39, .18), inset 0 0 0 2px rgba(255, 255, 255, .18); transform: rotate(.4deg); animation: board-in .7s .08s ease-out both; }
.notice-board::before { content: ''; position: absolute; inset: 0; opacity: .28; background: radial-gradient(circle at 20% 30%, #846b4e 0 1px, transparent 1.5px), radial-gradient(circle at 70% 68%, #846b4e 0 1px, transparent 1.4px); background-size: 17px 19px, 23px 29px; }
.paper-note { position: absolute; padding: 28px; border: 1px solid rgba(91, 79, 62, .18); background: #fffdf4; box-shadow: 4px 7px 18px rgba(70, 56, 38, .16); }
.main-note { top: 50px; left: 42px; width: 67%; min-height: 322px; transform: rotate(-1.7deg); }
.main-note::after { content: ''; position: absolute; right: 19px; bottom: 22px; width: 45px; height: 26px; border-bottom: 3px solid var(--coral); border-radius: 50%; transform: rotate(-12deg); }
.note-label { color: var(--coral-dark); font-size: 12px; font-weight: 700; letter-spacing: .14em; }
.main-note h2 { margin-top: 20px; font-size: clamp(24px, 3vw, 34px); line-height: 1.35; }
.note-lines { display: grid; gap: 9px; margin-top: 24px; color: var(--muted); font-size: 13px; }
.note-lines span { display: flex; gap: 8px; align-items: center; }
.note-lines span::before { content: ''; width: 5px; height: 5px; border-radius: 50%; background: var(--green); }
.main-note a { position: absolute; left: 28px; bottom: 24px; color: var(--green); text-decoration: none; font-size: 12px; font-weight: 700; }
.side-note { right: -28px; bottom: 27px; width: 43%; min-height: 150px; padding: 20px; background: #e7eddf; transform: rotate(3.8deg); }
.side-note p { color: var(--green); font-size: 11px; letter-spacing: .1em; }
.side-note strong { display: block; margin: 12px 0 16px; font: 700 16px/1.55 "Songti SC", serif; }
.side-note a { color: var(--coral-dark); font-size: 12px; font-weight: 700; text-decoration: none; }
.tape { position: absolute; z-index: 3; width: 78px; height: 25px; background: rgba(235, 219, 175, .78); box-shadow: inset 0 0 0 1px rgba(130, 108, 64, .08); }
.tape-one { top: 36px; left: 34%; transform: rotate(3deg); }
.tape-two { right: 7px; bottom: 158px; transform: rotate(-8deg); }
.scribble { position: absolute; right: 21px; top: 27px; color: rgba(49, 89, 77, .78); font: 700 14px "Kaiti SC", "STKaiti", serif; transform: rotate(5deg); }

.campus-entry { margin-top: 88px; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.section-heading p { margin-bottom: 7px; color: var(--coral-dark); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.section-heading h2 { font-size: 34px; }
.section-heading > span { color: var(--muted); font-size: 12px; }
.entry-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.entry-card { position: relative; display: grid; grid-template-columns: 58px 1fr; gap: 18px; min-height: 230px; padding: 26px; overflow: hidden; border: 1px solid var(--border); border-radius: 4px 24px 4px 24px; color: var(--ink); background: var(--surface); box-shadow: var(--shadow-small); text-decoration: none; transition: transform .2s ease, box-shadow .2s ease; }
.entry-card::after { content: ''; position: absolute; right: -32px; bottom: -54px; width: 130px; height: 130px; border: 1px solid currentColor; border-radius: 50%; opacity: .12; }
.entry-card:hover { transform: translateY(-5px) rotate(-.3deg); box-shadow: var(--shadow); }
.entry-card:nth-child(2):hover { transform: translateY(-5px) rotate(.3deg); }
.card-index { position: absolute; top: 17px; right: 20px; color: var(--border-strong); font: 700 11px monospace; }
.card-symbol { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 18px 6px 18px 6px; color: #fff; background: var(--coral); font: 700 23px "Songti SC", serif; }
.campus-card .card-symbol { background: var(--green); }
.friend-card .card-symbol { color: var(--ink); background: var(--yellow); }
.entry-card h3 { margin-top: 5px; font-size: 24px; }
.entry-card p { margin-top: 10px; color: var(--muted); line-height: 1.7; }
.card-go { grid-column: 1 / -1; align-self: end; color: var(--green); font-size: 12px; font-weight: 700; }

.safety-strip { display: grid; grid-template-columns: 36px 1fr; gap: 14px; align-items: center; margin-top: 34px; padding: 17px 20px; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); color: var(--muted); background: rgba(255, 255, 255, .28); font-size: 12px; line-height: 1.7; }
.safety-strip strong { color: var(--ink); }
.safety-mark { display: grid; place-items: center; width: 32px; height: 32px; border: 1px solid var(--coral); border-radius: 50%; color: var(--coral-dark); font-weight: 800; }

@keyframes rise-in { from { opacity: 0; transform: translateY(18px); } }
@keyframes board-in { from { opacity: 0; transform: translate(24px, 12px) rotate(1.5deg); } }
@media (max-width: 980px) {
  .home { padding-top: 42px; }
  .hero { grid-template-columns: 1fr; gap: 48px; }
  .hero h1 { max-width: 10em; }
  .notice-board { width: min(680px, 100%); justify-self: center; }
  .entry-grid { grid-template-columns: 1fr; }
  .entry-card { min-height: 170px; }
}
@media (max-width: 640px) {
  .home { padding-top: 30px; }
  .hero h1 { font-size: 45px; }
  .lead { font-size: 15px; }
  .notice-board { min-height: 420px; border-width: 9px; }
  .main-note { top: 38px; left: 18px; width: 80%; min-height: 292px; padding: 22px; }
  .main-note h2 { font-size: 24px; }
  .main-note a { left: 22px; }
  .side-note { right: -8px; width: 53%; }
  .scribble { display: none; }
  .campus-entry { margin-top: 62px; }
  .section-heading { align-items: start; flex-direction: column; }
  .section-heading h2 { font-size: 29px; }
  .entry-card { grid-template-columns: 50px 1fr; padding: 22px; }
  .card-symbol { width: 48px; height: 48px; }
}
</style>
