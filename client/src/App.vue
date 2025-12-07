<template>
    <Suspense>
      <router-view />
      <template #fallback>
        <div class="loading">{{ t('app.loading') }}</div>
      </template>
    </Suspense>

</template>

<script setup>
import { watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

watchEffect(() => {
  document.title = t('app.title');
});
</script>

<style>
/* 全局基础样式 */
#app {
  /* 确保应用内容不会被强制弹窗覆盖 */
  position: relative !important;
  z-index: 1 !important;
  -webkit-font-smoothing: antialiased !important;
  -moz-osx-font-smoothing: grayscale !important;
  transition: background-color var(--transition-time), color var(--transition-time) !important;
  position: relative !important;
  background: var(--bg-primary) !important;
}

/* 全局滚动条隐藏样式 */
html, body {
  /* 隐藏滚动条但保留功能 */
  -ms-overflow-style: none;  /* IE 和 Edge */
  scrollbar-width: none;     /* Firefox */
  overflow-x: hidden;        /* 防止水平滚动条 */
}

/* Chrome, Safari 和 Opera */
html::-webkit-scrollbar,
body::-webkit-scrollbar,
.app-container::-webkit-scrollbar {
  display: none;
}

/* 确保滚动功能仍然正常工作 */
html, body {
  -webkit-overflow-scrolling: touch;  /* iOS Safari 平滑滚动 */
}

.contain {
  background-color: transparent !important;
}

.app-container {
  transition: background var(--transition-time) ease, color var(--transition-time) ease !important;
  min-height: 100vh !important;
  display: flex !important;
  flex-direction: column !important;
}

header {
  padding: 1rem !important;
  border-bottom: 1px solid #eee !important;
  transition: border-color var(--transition-time) ease !important;
}

main {
  padding: 2rem !important;
  flex: 1 !important;
  position: relative !important;
}

button {
  border-radius: var(--border-radius) !important;
  cursor: pointer !important;
  transition: all var(--transition-time) ease !important;
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #121212 !important;
    --border-light: #333 !important;
  }

  html, body {
    background: var(--bg-primary) !important;
    transition: background-color var(--transition-time) ease, color var(--transition-time) ease !important;
  }
  
  .app-container {
    background: #1e1e1e !important;
  }

  header {
    border-bottom: 1px solid var(--border-light) !important;
  }

  /* ===== 深色模式日期选择器优化 ===== */
  input[type="date"] {
    background-color: #1e1e1e !important;
    border: 1px solid #333 !important;
    border-radius: var(--border-radius) !important;
    padding: 8px 12px !important;
    -webkit-appearance: none !important;
    appearance: none !important;
    transition: all var(--transition-time) ease !important;
    outline: none !important;
    color-scheme: dark !important;
  }

  input[type="date"]:focus {
    border-color: #4d90fe !important;
    box-shadow: 0 0 0 2px rgba(77, 144, 254, 0.5) !important;
  }

  input[type="date"]::-webkit-calendar-picker-indicator {
    filter: invert(0.8) brightness(1.2) contrast(1.5) !important;
    cursor: pointer !important;
    width: 20px !important;
    height: 20px !important;
  }

  input[type="date"]::-webkit-datetime-edit-fields-wrapper {
    background: transparent !important;
  }

  input[type="date"]::-webkit-datetime-edit-text {
    color: #999 !important;
    padding: 0 2px !important;
  }
}
</style>
