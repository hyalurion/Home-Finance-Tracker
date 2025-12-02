<template>
  <div class="android-page">
    <!-- 页面标题 -->
    <Header title="其他服务" />
    <div class="service-list">
      <a
        class="service-item"
        href="http://192.168.0.197:3010/photo.html"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="service-icon">📷</span>
        <span class="service-label">导出本月数据图片</span>
      </a>

      <a
        class="service-item"
        href="http://192.168.0.197:3010/donation"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span class="service-icon">💝</span>
        <span class="service-label">{{ $t('donation.title') }}</span>
      </a>
    </div>
  </div>
</template>

<script>
import { useI18n } from 'vue-i18n';

export default {
  name: 'AndroidPage',
  setup() {
    const { t } = useI18n();
    return {
      t
    }
  }
}
</script>

<style scoped>
.android-page {
  max-width: 425px;
  margin: 0 auto;
  padding: 16px;
  background: #fafafa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin-bottom: 24px;
  text-align: center;
}

.service-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.service-item {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  text-decoration: none;
  color: #333;
  transition: box-shadow 0.2s;
}

.service-item:active {
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.service-icon {
  font-size: 24px;
  margin-right: 12px;
}

.service-label {
  font-size: 16px;
}

@media (prefers-color-scheme: dark) {
  .android-page {
    background: #333;
  }
  .page-title {
    color: #fff;
  }
  .service-item {
    background: #444;
    color: #fff;
  }
}
</style>
