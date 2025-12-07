<template>
  <div ref="headerRef" :class="['header']">
    <h1>{{ title }}</h1>

    <div class="language-buttons">
      <GlassButton
        v-for="lang in languages"
        :key="lang.code"
        @click="switchLanguage(lang.code)"
        :class="['language-btn', { 'active': currentLang.value === lang.code }]"
        :aria-label="`切换到${lang.label}`"
      >
        {{ lang.shortLabel }}
      </GlassButton>
    </div>

  </div>
</template>

<script setup>
// 恢复导入你原有的 useLanguageSwitch composable
import { useLanguageSwitch } from '@/composables/useLanguageSwitch';
import { ref, onMounted, onUnmounted } from 'vue';
import { useI18n } from 'vue-i18n';

defineOptions({ name: 'AppHeader' });
useI18n();

// 定义组件接收的 props
const props = defineProps({ title: String });

// 调用 useLanguageSwitch 获取语言切换函数和当前语言
const { switchLanguage: originalSwitchLanguage, currentLang } = useLanguageSwitch();

// 增强版语言切换函数，添加日志记录
const switchLanguage = (langCode) => {
  console.log('Language switch requested:', { from: currentLang.value, to: langCode });
  try {
    originalSwitchLanguage(langCode);
    console.log('Language switch successful:', { currentLanguage: langCode });
  } catch (error) {
    console.error('Language switch failed:', { error: error.message, requestedLang: langCode });
  }
};

// 定义支持的语言列表
const languages = [
  { code: 'en-US', label: 'English', shortLabel: 'EN' },
  { code: 'zh-CN', label: '简体中文', shortLabel: '简' },
  { code: 'zh-TW', label: '繁體中文', shortLabel: '繁' }
];

// 监听滚动事件，添加滚动效果
const headerRef = ref(null);

const handleScroll = () => {
  if (headerRef.value) {
    const shouldBeScrolled = window.scrollY > 50;
    const isCurrentlyScrolled = headerRef.value.classList.contains('scrolled');
    
    if (shouldBeScrolled && !isCurrentlyScrolled) {
      headerRef.value.classList.add('scrolled');
      console.log('Header scroll effect activated:', { scrollY: window.scrollY });
    } else if (!shouldBeScrolled && isCurrentlyScrolled) {
      headerRef.value.classList.remove('scrolled');
      console.log('Header scroll effect deactivated:', { scrollY: window.scrollY });
    }
  }
};

// 生命周期钩子
onMounted(() => {
  console.log('Header component mounted:', { initialLanguage: currentLang.value });
  window.addEventListener('scroll', handleScroll);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<style scoped>
/* 头部容器的基础样式 */
.header {
  display: flex; /* 使用 Flexbox 布局 */
  justify-content: space-between; /* 子元素两端对齐 */
  align-items: center; /* 子元素垂直居中 */
  padding: 1rem 1.5rem; /* 内边距 */
  background: linear-gradient(135deg, rgba(255,255,255,0.8) 0%, rgba(250,250,250,0.9) 100%); /* 渐变背景 */
  backdrop-filter: blur(10px); /* 毛玻璃效果 */
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05); /* 底部阴影 */
  border-bottom: 1px solid var(#e4e7ed); /* 底部边框 */
  position: sticky; /* 粘性定位，使其在滚动时保持在顶部 */
  top: 0; /* 距离顶部0 */
  z-index: 100; /* 确保在其他内容之上 */
  transition: all 0.3s ease; /* 所有属性的过渡效果 */
}

/* 滚动时的效果 */
.header.scrolled {
  padding: 0.8rem 1.5rem; /* 减小滚动时的内边距 */
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08); /* 增加阴影 */
}

/* 标题样式 */
.header h1 {
  font-size: 1.8rem; /* 标题字体大小 */
  color: var(#303133); /* 标题文本颜色 */
  margin: 0; /* 移除默认外边距 */
  flex-grow: 1; /* 允许标题占据可用空间 */
  text-align: left; /* 文本左对齐 */
  font-weight: 600; /* 字体粗细 */
  background: linear-gradient(90deg, #409eff, #7928ca); /* 文本渐变背景 */
  -webkit-background-clip: text; /* 背景裁剪到文本 */
  background-clip: text;
  -webkit-text-fill-color: transparent; /* 文本填充透明，显示背景渐变 */
  letter-spacing: -0.02em; /* 字母间距 */
  transition: all 0.3s ease; /* 过渡效果 */
  position: relative;
  z-index: 1;
}

/* 语言按钮容器 */
.language-buttons {
  display: flex; /* 使用 Flexbox 布局 */
  gap: 0.5rem; /* 按钮之间的间距 */
  margin-left: 1rem; /* 左侧外边距 */
}

/* 语言按钮样式 */
.language-btn {
  padding: 8px 16px; /* 内边距 */
  border: 1px solid #dcdfe6; /* 边框 */
  border-radius: 6px; /* 圆角 */
  background-color: #ffffff; /* 背景色 */
  color: #606266; /* 文本颜色 */
  font-size: 14px; /* 字体大小 */
  font-weight: 500; /* 字体粗细 */
  cursor: pointer; /* 鼠标悬停时显示手型光标 */
  transition: all 0.3s ease; /* 过渡效果 */
  white-space: nowrap; /* 禁止文本换行 */
  outline: none; /* 移除默认轮廓 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); /* 阴影效果 */
}

/* 移动端适配 */
@media (max-width: 768px) {
  .header {
    padding: 0.8rem 1rem; /* 减小移动端内边距 */
  }

  .header h1 {
    font-size: 1.3rem; /* 减小移动端标题字体大小 */
  }

  .earth-icon {
    font-size: 24px; /* 减小移动端图标大小 */
    width: 40px;
    height: 40px;
  }
}

@media (max-width: 480px) {
  .header h1 {
    font-size: 1.2rem; /* 进一步减小极小屏幕的标题字体大小 */
  }

  /* 移动端下拉菜单宽度调整 */
  .header {
    min-width: 200px !important;
    max-width: 90vw !important;
  }
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .header {
    background: linear-gradient(135deg, rgba(30,30,30,0.8) 0%, rgba(24,24,24,0.9) 100%); /* 深色渐变背景 */
    border-bottom: 1px solid #333;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  }

  .header h1 {
    color: #e0e0e0;
    background: linear-gradient(90deg, #79bbff, #a78bfa); /* 深色模式下的文本渐变 */
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .language-btn {
    background-color: #333;
    color: #e0e0e0;
    border-color: #555;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }
}

</style>
