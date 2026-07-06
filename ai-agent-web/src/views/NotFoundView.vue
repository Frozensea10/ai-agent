<template>
  <div class="not-found-view">
    <div class="hand-card not-found-card">
      <div class="not-found-emoji">404</div>
      <h1 class="not-found-title">页面走丢啦</h1>
      <p class="not-found-desc">你访问的页面不存在，可能已被移动或删除。</p>
      <button class="btn-pill btn-primary-pill" type="button" @click="goHome">
        返回首页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.not-found-view {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--cream, #FFF8E7);
}

.not-found-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 48px 32px;
  max-width: 420px;
  width: 100%;
}

.not-found-emoji {
  font-size: 64px;
  font-weight: 900;
  color: var(--coral, #FF6B6B);
  margin-bottom: 16px;
  letter-spacing: 2px;
}

.not-found-title {
  font-size: 24px;
  font-weight: 900;
  color: var(--text-dark, #2D2D2D);
  margin: 0 0 8px 0;
}

.not-found-desc {
  font-size: 14px;
  color: var(--text-medium, #6B6B6B);
  font-weight: 600;
  line-height: 1.6;
  margin: 0 0 24px 0;
}
</style>
