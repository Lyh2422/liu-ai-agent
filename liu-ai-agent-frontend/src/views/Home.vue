<template>
  <div class="home">
    <div class="hero container">
      <div class="hero-copy">
        <div class="eyebrow">河南师范大学 · 校园情感陪伴与智能问答</div>
        <h1>面向校园恋爱沟通、情绪困扰与关系维护的 AI 情感陪伴系统</h1>
        <p class="subtitle">结合大模型对话、流式输出、多轮记忆和知识库增强能力，为河南师大学生提供更自然、低压力、可持续的情感建议服务。</p>
        <div class="cta">
          <router-link class="btn primary" to="/love">进入情感陪伴</router-link>
          <router-link class="btn" to="/manus">进入智能问答</router-link>
        </div>
        <div class="pill-row">
          <span class="pill">恋爱沟通</span>
          <span class="pill">情绪疏导</span>
          <span class="pill">关系维护</span>
          <span class="pill">校园问答</span>
        </div>
      </div>
      <div class="hero-board ring">
        <div class="board-card board-main">
          <div class="board-head">
            <span class="board-label">服务场景</span>
            <span class="board-chip">校园生活咨询</span>
          </div>
          <div class="board-title">表白前后 · 吵架后 · 心里堵的时候</div>
          <div class="schedule">
            <div class="schedule-item">
              <span>恋爱沟通</span>
              <strong>怎么开口更自然</strong>
            </div>
            <div class="schedule-item">
              <span>情绪困扰</span>
              <strong>先把心情说清楚</strong>
            </div>
            <div class="schedule-item">
              <span>关系维护</span>
              <strong>把误会慢慢理顺</strong>
            </div>
          </div>
        </div>
        <div class="board-card board-side">
          <div class="mini-title">项目目标</div>
          <p>让学生在低压力、可持续的对话里，获得更自然的情感建议和校园场景支持。</p>
          <div class="mini-stats">
            <span>大模型对话</span>
            <span>多轮记忆</span>
            <span>知识库增强</span>
          </div>
        </div>
      </div>
    </div>

    <div class="container grid">
      <router-link class="app-card app-card-love" to="/love">
        <div class="app-icon love">恋</div>
        <div class="app-body">
          <h3>AI 情感陪伴</h3>
          <p class="muted">恋爱沟通 · 情绪支持 · 关系维护</p>
        </div>
      </router-link>
      <router-link class="app-card app-card-manus" to="/manus">
        <div class="app-icon manus">智</div>
        <div class="app-body">
          <h3>AI 智能问答</h3>
          <p class="muted">知识库增强 · 流式输出 · 多轮记忆</p>
        </div>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.home {
  padding-bottom: 44px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 24px;
  padding: 56px 0 24px;
  align-items: center;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.eyebrow {
  display: inline-flex;
  align-self: flex-start;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(255, 250, 244, 0.85);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0;
}

.hero-copy h1 {
  font-size: 42px;
  line-height: 1.08;
  max-width: 12em;
}

.subtitle {
  color: var(--muted);
  line-height: 1.7;
  max-width: 34rem;
}

.cta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.pill {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(255, 253, 249, 0.92);
  color: var(--muted);
  font-size: 13px;
}

.hero-board {
  display: grid;
  gap: 14px;
}

.board-card {
  position: relative;
  border-radius: 24px;
  border: 1px solid var(--border);
  background: var(--card);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.board-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), transparent 48%);
  pointer-events: none;
}

.board-main {
  min-height: 275px;
  padding: 22px;
}

.board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.board-label,
.board-chip,
.mini-title {
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0;
}

.board-chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(248, 230, 208, 0.9);
  border: 1px solid var(--border);
}

.board-title {
  margin-top: 18px;
  font-size: 30px;
  line-height: 1.1;
  max-width: 8em;
}

.schedule {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.schedule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 252, 247, 0.88);
  border: 1px solid rgba(234, 215, 198, 0.88);
}

.schedule-item span {
  color: var(--muted);
  font-size: 12px;
}

.schedule-item strong {
  font-size: 14px;
  font-weight: 600;
}

.board-side {
  padding: 18px 20px;
  min-height: 112px;
}

.board-side p {
  margin-top: 8px;
  color: var(--text);
  line-height: 1.6;
}

.mini-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.mini-stats span {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(243, 228, 206, 0.9);
  color: var(--primary-strong);
  font-size: 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.app-card {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: rgba(255, 250, 244, 0.92);
  text-decoration: none;
  color: inherit;
  transition: transform .16s ease, box-shadow .2s ease, border-color .2s ease;
  box-shadow: var(--shadow);
}

.app-card:hover {
  transform: translateY(-2px);
  border-color: #d8b89a;
  box-shadow: 0 22px 42px rgba(128, 83, 48, 0.14);
}

.app-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #fffaf5;
  letter-spacing: 0;
}

.app-icon.love {
  background: linear-gradient(180deg, #d97a54, #bb5f38);
}

.app-icon.manus {
  background: linear-gradient(180deg, #8f9b57, #637238);
}

.app-body h3 {
  font-size: 21px;
  line-height: 1.2;
}

.app-body p {
  margin-top: 6px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .hero-copy h1 {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .hero {
    padding-top: 34px;
  }

  .hero-copy h1 {
    font-size: 32px;
  }

  .board-main {
    min-height: 240px;
    padding: 18px;
  }

  .board-title {
    font-size: 26px;
  }

  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
