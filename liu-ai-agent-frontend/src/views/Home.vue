<template>
  <div class="home">
    <span class="page-doodle doodle-one" aria-hidden="true">✦</span>
    <span class="page-doodle doodle-two" aria-hidden="true">✤</span>
    <span class="page-doodle doodle-three" aria-hidden="true">☆</span>

    <section class="hero container">
      <div class="hero-copy">
        <p class="eyebrow"><span>✦</span> LUCKY DAY · {{ dateLabel }}</p>
        <h1>把心事轻轻放下，<br><em>好运会慢慢长出来。</em></h1>
        <p class="lead">关系里的别扭、忽然涌上来的难过，或者一条怎么都回不好的消息。你可以从任何一句开始，不用先把自己整理好。</p>
        <div class="hero-actions">
          <router-link class="btn primary" to="/love">去心事小屋 <span>→</span></router-link>
          <router-link class="soft-link" to="/messages">找同学聊聊</router-link>
        </div>
        <div class="tiny-promises" aria-label="服务特点">
          <span><i>♡</i> 只属于你的对话</span>
          <span><i>✿</i> 慢慢说也没关系</span>
          <span><i>⌁</i> 随时接着上次聊</span>
        </div>
      </div>

      <div class="wish-scene" aria-label="今日愿望卡片">
        <span class="spark spark-one" aria-hidden="true">✦</span>
        <span class="spark spark-two" aria-hidden="true">✧</span>
        <span class="clover clover-one" aria-hidden="true">✤</span>
        <span class="clover clover-two" aria-hidden="true">✤</span>
        <article class="wish-card">
          <span class="tape" aria-hidden="true" />
          <p class="wish-label">TODAY'S LITTLE WISH</p>
          <img :src="dreamCat" alt="手绘小猫和文字：梦想是做最幸福的小猫" />
          <footer>
            <span>给今天留一点软乎乎的期待</span>
            <b>{{ cardDate }}</b>
          </footer>
        </article>
        <p class="hand-note">愿你今天也遇见<br>一件小小的好事 ♡</p>
      </div>
    </section>

    <section class="entry-section container" aria-labelledby="entry-title">
      <div class="section-heading">
        <div>
          <p>CHOOSE YOUR CORNER</p>
          <h2 id="entry-title">今天想从哪里开始？</h2>
        </div>
        <span>三处小角落，各自安放不同的事</span>
      </div>

      <div class="entry-grid">
        <router-link class="entry-card heart-card" to="/love">
          <span class="card-no">01</span>
          <span class="card-sticker">♡</span>
          <div>
            <p class="card-kicker">HEART ROOM</p>
            <h3>说说心事</h3>
            <p>恋爱、友情、情绪，想到哪儿就说到哪儿。</p>
          </div>
          <span class="card-go">进入小屋 <b>↗</b></span>
        </router-link>

        <router-link class="entry-card campus-card" to="/manus">
          <span class="card-no">02</span>
          <span class="card-sticker">✤</span>
          <div>
            <p class="card-kicker">CAMPUS HELPER</p>
            <h3>处理校园事务</h3>
            <p>作业思路、社团方案、资料整理，一起理清下一步。</p>
          </div>
          <span class="card-go">开始问答 <b>↗</b></span>
        </router-link>

        <router-link class="entry-card friend-card" to="/messages">
          <span class="card-no">03</span>
          <span class="card-sticker">✉</span>
          <div>
            <p class="card-kicker">FRIEND MAIL</p>
            <h3>联系同学</h3>
            <p>按唯一 ID 找到朋友，也可以拉个小群聊。</p>
          </div>
          <span class="card-go">打开消息 <b>↗</b></span>
        </router-link>
      </div>
    </section>

    <section class="kind-note container">
      <div class="note-clover" aria-hidden="true">✤</div>
      <div>
        <p>留给你的温柔提醒</p>
        <h2>你不必永远表现得没事。</h2>
      </div>
      <p class="note-copy">当你需要现实中的帮助，请优先联系身边可信任的同学、老师或家人；如果正面临人身安全风险或强烈心理危机，请尽快联系当地紧急援助服务。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import dreamCat from '../assets/dream-cat.jpg'

const dateLabel = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 19) return '下午好'
  return '晚上好'
})

const cardDate = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit'
}).format(new Date()).replace('/', ' / '))
</script>

<style scoped>
.home { position: relative; padding: 72px 0 52px; overflow: hidden; }
.page-doodle { position: absolute; z-index: -1; color: rgba(214, 152, 179, .22); font-family: Georgia, serif; pointer-events: none; }
.doodle-one { top: 70px; left: 4%; font-size: 68px; transform: rotate(14deg); }
.doodle-two { top: 520px; right: 3%; color: rgba(159, 210, 99, .22); font-size: 108px; transform: rotate(-11deg); }
.doodle-three { top: 910px; left: 2%; font-size: 92px; transform: rotate(-8deg); }

.hero { display: grid; grid-template-columns: minmax(0, 1fr) minmax(420px, .84fr); gap: clamp(50px, 7vw, 96px); align-items: center; }
.hero-copy { position: relative; z-index: 2; animation: rise-in .7s ease-out both; }
.eyebrow { display: inline-flex; align-items: center; gap: 9px; margin-bottom: 23px; color: #9a7183; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
.eyebrow span { color: #89b957; font-size: 17px; animation: twinkle 2.8s ease-in-out infinite; }
.hero h1 { max-width: 9.5em; color: #443b43; font-size: clamp(50px, 6vw, 78px); line-height: 1.12; letter-spacing: -.05em; }
.hero h1 em { position: relative; color: #af637f; font-style: normal; white-space: nowrap; }
.hero h1 em::after { position: absolute; right: 0; bottom: -9px; left: 4%; height: 8px; content: ''; border-radius: 50%; background: url("data:image/svg+xml,%3Csvg width='300' height='8' viewBox='0 0 300 8' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M2 5.5C70 1.3 180 1.2 298 4' fill='none' stroke='%23efb6c8' stroke-width='3' stroke-linecap='round'/%3E%3C/svg%3E") center / 100% 100% no-repeat; opacity: .8; }
.lead { max-width: 37rem; margin-top: 32px; color: #726a73; font-size: 16px; line-height: 2; }
.hero-actions { display: flex; align-items: center; gap: 24px; margin-top: 32px; }
.hero-actions .btn { min-width: 158px; }
.hero-actions .btn span { transition: transform .18s ease; }
.hero-actions .btn:hover span { transform: translateX(4px); }
.soft-link { position: relative; padding: 8px 2px; color: #9d5f77; font-weight: 750; text-decoration: none; }
.soft-link::after { position: absolute; right: 0; bottom: 3px; left: 0; height: 1px; content: ''; background: #e9afc2; transform-origin: left; transition: transform .2s ease; }
.soft-link:hover::after { transform: scaleX(.35); }
.tiny-promises { display: flex; flex-wrap: wrap; gap: 10px 18px; margin-top: 36px; color: #827a82; font-size: 11px; }
.tiny-promises span { display: inline-flex; align-items: center; gap: 6px; }
.tiny-promises i { color: #7fa753; font-family: Georgia, serif; font-style: normal; font-size: 15px; }

.wish-scene { position: relative; min-height: 520px; animation: scene-in .8s .08s ease-out both; }
.wish-scene::before { position: absolute; inset: 45px -35px 15px 30px; content: ''; border: 1px dashed rgba(203, 160, 180, .35); border-radius: 45% 55% 48% 52%; background: radial-gradient(circle at 40% 34%, rgba(247, 197, 217, .54), rgba(255, 255, 255, .15) 60%); transform: rotate(-5deg); }
.wish-card { position: absolute; z-index: 2; top: 10px; right: 30px; width: min(410px, calc(100% - 35px)); padding: 20px 20px 15px; border: 1px solid rgba(221, 205, 213, .92); border-radius: 7px; background: rgba(255, 255, 255, .94); box-shadow: 0 30px 70px rgba(149, 108, 128, .2); transform: rotate(2.4deg); transition: transform .3s ease; }
.wish-card:hover { transform: rotate(.5deg) translateY(-5px); }
.wish-card::before { position: absolute; inset: 9px; content: ''; pointer-events: none; border: 1px solid rgba(239, 182, 200, .42); border-radius: 3px; }
.wish-card img { display: block; width: 100%; aspect-ratio: 1.08; margin-top: 10px; object-fit: cover; border-radius: 3px; filter: contrast(1.03) saturate(.9); }
.wish-label { position: relative; z-index: 1; color: #9c7685; font-size: 9px; font-weight: 800; letter-spacing: .22em; text-align: center; }
.wish-card footer { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 13px 5px 2px; color: #8a7d84; font: 13px "Kaiti SC", "STKaiti", serif; }
.wish-card footer b { color: #87a963; font: 700 10px "SFMono-Regular", monospace; letter-spacing: .08em; }
.tape { position: absolute; z-index: 3; top: -14px; left: 50%; width: 94px; height: 29px; background: rgba(215, 240, 178, .72); box-shadow: inset 0 0 0 1px rgba(137, 174, 88, .08); transform: translateX(-50%) rotate(-3deg); }
.hand-note { position: absolute; z-index: 3; right: -8px; bottom: 13px; padding: 13px 18px; color: #9b6b7e; background: #fff1f6; box-shadow: 0 10px 24px rgba(138, 93, 113, .12); font: 15px/1.5 "Kaiti SC", "STKaiti", serif; transform: rotate(-4deg); }
.spark, .clover { position: absolute; z-index: 4; font-family: Georgia, serif; }
.spark { color: #e7acc1; }
.spark-one { top: 62px; left: 4px; font-size: 34px; transform: rotate(12deg); }
.spark-two { right: -5px; top: 105px; color: #d8c5cb; font-size: 44px; }
.clover { color: #aee26f; text-shadow: 0 7px 15px rgba(126, 175, 71, .15); }
.clover-one { left: 0; bottom: 75px; font-size: 70px; transform: rotate(-11deg); }
.clover-two { right: 0; top: 4px; font-size: 38px; transform: rotate(13deg); }

.entry-section { margin-top: 96px; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 26px; }
.section-heading > div > p { margin-bottom: 8px; color: #ad6d85; font-size: 10px; font-weight: 800; letter-spacing: .2em; }
.section-heading h2 { color: #4a4148; font-size: 37px; }
.section-heading > span { color: #968b92; font: 14px "Kaiti SC", "STKaiti", serif; }
.entry-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.entry-card { position: relative; display: flex; min-height: 292px; flex-direction: column; justify-content: space-between; overflow: hidden; padding: 28px; border: 1px solid rgba(230, 214, 222, .9); border-radius: 30px 12px 30px 12px; color: var(--ink); background: rgba(255, 255, 255, .76); box-shadow: 0 15px 40px rgba(125, 90, 108, .1); text-decoration: none; backdrop-filter: blur(14px); transition: transform .25s ease, box-shadow .25s ease; }
.entry-card::before { position: absolute; right: -28px; bottom: -35px; width: 130px; height: 130px; content: ''; border: 1px dashed currentColor; border-radius: 50%; opacity: .12; }
.entry-card::after { position: absolute; right: 24px; top: 23px; content: '✦'; color: rgba(183, 111, 140, .22); font: 24px Georgia, serif; }
.entry-card:hover { transform: translateY(-8px) rotate(-.5deg); box-shadow: 0 24px 60px rgba(125, 90, 108, .16); }
.entry-card:nth-child(2):hover { transform: translateY(-8px) rotate(.5deg); }
.heart-card { background: linear-gradient(145deg, rgba(255, 255, 255, .92), rgba(255, 237, 244, .82)); }
.campus-card { background: linear-gradient(145deg, rgba(255, 255, 255, .92), rgba(239, 250, 221, .88)); }
.friend-card { background: linear-gradient(145deg, rgba(255, 255, 255, .92), rgba(255, 248, 224, .86)); }
.card-no { color: #b8a9b0; font: 700 10px "SFMono-Regular", monospace; letter-spacing: .15em; }
.card-sticker { display: grid; place-items: center; width: 61px; height: 61px; margin: 18px 0; border: 2px solid #fff; border-radius: 50%; color: #9d5f77; background: #f9cfe0; box-shadow: 0 7px 18px rgba(179, 108, 138, .18), 0 0 0 1px #edbfd1; font: 700 29px Georgia, serif; transform: rotate(-5deg); }
.campus-card .card-sticker { color: #658d47; border-radius: 43% 57% 48% 52%; background: #d8f3ad; box-shadow: 0 7px 18px rgba(127, 170, 72, .16), 0 0 0 1px #c7e89a; }
.friend-card .card-sticker { color: #9d7847; background: #f7e4b7; box-shadow: 0 7px 18px rgba(171, 134, 77, .14), 0 0 0 1px #edd7a7; }
.card-kicker { color: #a78090; font-size: 9px; font-weight: 800; letter-spacing: .18em; }
.entry-card h3 { margin-top: 6px; font-size: 27px; }
.entry-card div > p:last-child { max-width: 17rem; margin-top: 10px; color: var(--muted); line-height: 1.75; }
.card-go { display: flex; align-items: center; justify-content: space-between; margin-top: 22px; color: #866778; font-size: 12px; font-weight: 750; }
.card-go b { display: grid; place-items: center; width: 31px; height: 31px; border: 1px solid rgba(168, 134, 150, .34); border-radius: 50%; font-size: 15px; transition: transform .2s ease; }
.entry-card:hover .card-go b { transform: rotate(45deg); }

.kind-note { display: grid; grid-template-columns: auto minmax(250px, .7fr) minmax(320px, 1.3fr); gap: 24px; align-items: center; margin-top: 28px; padding: 28px 32px; border: 1px solid rgba(220, 229, 199, .92); border-radius: 12px 32px 12px 32px; background: rgba(249, 255, 239, .78); box-shadow: 0 12px 36px rgba(102, 132, 76, .08); backdrop-filter: blur(12px); }
.note-clover { display: grid; place-items: center; width: 58px; height: 58px; color: #85b655; border-radius: 50%; background: #e5f7c8; font: 31px Georgia, serif; }
.kind-note > div:nth-child(2) p { color: #829466; font-size: 10px; font-weight: 800; letter-spacing: .15em; }
.kind-note h2 { margin-top: 4px; font-size: 24px; }
.note-copy { color: #756f75; font-size: 12px; line-height: 1.8; }

@keyframes rise-in { from { opacity: 0; transform: translateY(22px); } }
@keyframes scene-in { from { opacity: 0; transform: translate(22px, 10px) rotate(2deg); } }
@keyframes twinkle { 50% { opacity: .4; transform: rotate(18deg) scale(.82); } }

@media (max-width: 980px) {
  .home { padding-top: 48px; }
  .hero { grid-template-columns: 1fr; gap: 42px; }
  .hero-copy { text-align: center; }
  .hero h1, .lead { margin-inline: auto; }
  .hero-actions, .tiny-promises { justify-content: center; }
  .wish-scene { width: min(540px, 100%); justify-self: center; }
  .entry-grid { grid-template-columns: 1fr; }
  .entry-card { min-height: 230px; }
  .kind-note { grid-template-columns: auto 1fr; }
  .note-copy { grid-column: 1 / -1; }
}

@media (max-width: 640px) {
  .home { padding-top: 36px; }
  .hero h1 { font-size: 43px; }
  .hero h1 em { white-space: normal; }
  .lead { margin-top: 26px; font-size: 14px; line-height: 1.9; }
  .hero-actions { flex-direction: column; gap: 12px; }
  .hero-actions .btn { width: 100%; }
  .tiny-promises { display: grid; justify-content: start; margin-inline: auto; width: max-content; text-align: left; }
  .wish-scene { min-height: 440px; }
  .wish-card { right: 8px; width: calc(100% - 20px); padding: 14px 14px 10px; }
  .wish-card footer { font-size: 11px; }
  .hand-note { right: 0; bottom: 0; font-size: 13px; }
  .clover-one { left: -9px; bottom: 56px; font-size: 50px; }
  .section-heading { align-items: flex-start; flex-direction: column; }
  .section-heading h2 { font-size: 29px; }
  .entry-section { margin-top: 70px; }
  .entry-card { min-height: 250px; padding: 24px; }
  .kind-note { grid-template-columns: 1fr; justify-items: start; padding: 24px; }
  .note-copy { grid-column: auto; }
}
</style>
