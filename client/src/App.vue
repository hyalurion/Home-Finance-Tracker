<template>
    <Suspense>
      <router-view />
      <template #fallback>
        <div class="loading">{{ t('app.loading') }}</div>
      </template>
    </Suspense>

</template>

<script setup>
import { watchEffect, onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

const { t } = useI18n();
const router = useRouter();

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
  color: var(--text-primary) !important;
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
    --text-primary: #e0e0e0 !important;
    --bg-primary: #121212 !important;
    --border-light: #333 !important;
  }

  html, body {
    background: var(--bg-primary) !important;
    color: var(--text-primary) !important;
    transition: background-color var(--transition-time) ease, color var(--transition-time) ease !important;
  }
  
  .app-container {
    background: #1e1e1e !important;
    color: var(--text-primary) !important;
  }

  header {
    border-bottom: 1px solid var(--border-light) !important;
  }

  /* ===== 深色模式日期选择器优化 ===== */
  input[type="date"] {
    background-color: #1e1e1e !important;
    color: var(--text-primary) !important;
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

  input[type="date"]::-webkit-datetime-edit {
    color: var(--text-primary) !important;
  }

  input[type="date"]::-webkit-datetime-edit-fields-wrapper {
    background: transparent !important;
  }

  input[type="date"]::-webkit-datetime-edit-text {
    color: #999 !important;
    padding: 0 2px !important;
  }

  /* ===== Element Plus 组件深色模式适配 ===== */
  /* 对话框组件 */
  .el-dialog {
    background-color: #1e1e1e !important;
    border-color: #333 !important;
    border-radius: var(--border-radius) !important;
    box-shadow: var(--box-shadow) !important;
  }

  .el-dialog__title {
    color: var(--text-primary) !important;
    font-weight: 600 !important;
  }

  .el-dialog__body {
    color: var(--text-primary) !important;
    background-color: #1e1e1e !important;
  }

  .el-card {
    background-color: #1e1e1e !important;
    border-color: #333 !important;
    border-radius: var(--border-radius) !important;
    box-shadow: var(--box-shadow) !important;
  }

  .el-message {
    background-color: #1e1e1e !important;
    color: var(--text-primary) !important;
    border-color: #333 !important;
    border-radius: var(--border-radius) !important;
    box-shadow: var(--box-shadow) !important;
  }

  /* 下拉菜单组件 */
  .el-select, 
  .el-select__wrapper {
    --el-select-bg-color: #1e1e1e !important;
    --el-select-text-color: var(--text-primary) !important;
    --el-select-border-color: #333 !important;
    background-color: var(--el-select-bg-color) !important;
    transition: all var(--transition-time) ease !important;
  }

  .el-select__dropdown {
    background-color: var(--el-select-bg-color) !important;
    border-color: var(--el-select-border-color) !important;
    color: var(--el-select-text-color) !important;
    border-radius: var(--border-radius) !important;
    box-shadow: var(--box-shadow) !important;
  }

  .el-select__dropdown .el-select-dropdown__list {
    background-color: var(--el-select-bg-color) !important;
  }

  .el-popper {
    background-color: #1e1e1e !important;
    border-color: #333 !important;
    border-radius: var(--border-radius) !important;
  }

  .el-option {
    color: var(--el-select-text-color) !important;
    background-color: var(--el-select-bg-color) !important;
    transition: all var(--transition-time) ease !important;
  }

  .el-option:hover {
    background-color: #2d2d2d !important;
    color: #ffffff !important;
  }

  .el-option.is-selected {
    background-color: #3366cc !important;
    color: #ffffff !important;
  }

  .el-select__placeholder {
    background-color: var(--el-select-bg-color) !important;
    color: white !important;
  }

  /* 输入框组件 */
  .el-input__wrapper {
    background-color: #1e1e1e !important;
    --el-input-bg-color: #1e1e1e !important;
    --el-input-text-color: var(--text-primary) !important;
    --el-input-border-color: #333 !important;
    transition: all var(--transition-time) ease !important;
    border-radius: var(--border-radius) !important;
  }

  .el-input__inner {
    background-color: #1e1e1e !important;
    color: var(--text-primary) !important;
  }

  .el-input__inner::placeholder {
    color: #999 !important;
  }

  .el-input__wrapper.is-focus {
    border-color: #3366cc !important;
    box-shadow: 0 0 0 2px rgba(51, 102, 204, 0.2) !important;
  }

  .el-input-group__append, 
  .el-input-group__prepend {
    background-color: #333 !important;
  }

  .el-select__wrapper .el-tooltip__trigger .el-tooltip__content {
    background-color: #333 !important;
    color: var(--text-primary) !important;
    border: 1px solid #444 !important;
  }

  /* 下拉菜单滚动条样式 */
  .el-select__dropdown .el-scrollbar__bar {
    background-color: #444 !important;
  }

  .el-select__dropdown .el-scrollbar__thumb {
    background-color: #666 !important;
    border-radius: 10px !important;
  }

  /* 下拉菜单分隔线样式 */
  .el-select__dropdown .el-divider {
    background-color: #333 !important;
  }

  /* 多选下拉菜单标签样式 */
  .el-select__tags {
    background-color: #1e1e1e !important;
  }

  .el-select__tag {
    background-color: #333 !important;
    color: var(--text-primary) !important;
    border-color: #444 !important;
    border-radius: 16px !important;
  }

  .el-select__tag-close {
    color: #999 !important;
  }

  .el-select__tag-close:hover {
    color: var(--text-primary) !important;
  }

  /* 表单组件 */
  .el-form-item__label {
    color: var(--text-primary) !important;
  }

  /* 下拉菜单悬停样式 */
  .el-popper .el-select-dropdown__item:hover {
    background-color: #2d2d2d !important;
  }

  /* 下拉菜单组件 */
  .el-dropdown__popper.el-popper {
    background-color: #1e1e1e !important;
    border-color: #333 !important;
  }

  .el-dropdown-menu {
    background-color: #1e1e1e !important;
  }

  .el-dropdown-menu__item {
    color: var(--text-primary) !important;
    background-color: #1e1e1e !important;
    transition: all var(--transition-time) ease !important;
  }

  .el-dropdown-menu__item:hover {
    background-color: #2d2d2d !important;
  }

  /* 文本域组件 */
  .el-textarea__inner {
    background-color: #1e1e1e !important;
    color: var(--text-primary) !important;
    border-color: #333 !important;
    border-radius: var(--border-radius) !important;
    transition: all var(--transition-time) ease !important;
  }

  .el-textarea__inner::placeholder {
    color: #999 !important;
  }

  .el-textarea__wrapper.is-focus .el-textarea__inner {
    border-color: #3366cc !important;
    box-shadow: 0 0 0 2px rgba(51, 102, 204, 0.2) !important;
  }

  /* 单选按钮组 */
  .el-radio-button__inner {
    background-color: #1e1e1e !important;
    color: var(--text-primary) !important;
    border-color: #333 !important;
    transition: all var(--transition-time) ease !important;
  }

  .el-radio-button__inner:hover {
    background-color: #2d2d2d !important;
  }

  .el-radio-button.is-active .el-radio-button__inner {
    background-color: #3366cc !important;
    color: #ffffff !important;
    border-color: #3366cc !important;
  }

  .el-table {
    background-color: #20222a !important;
  }
  .el-table th,
  .el-table td {
    background-color: #20222a !important;
    color: #fff !important;
  }
  .el-tag {
    background-color: #333 !important;
    color: var(--text-primary) !important;
    border-color: #444 !important;
    border-radius: 16px !important;
  }
}
</style>
