<template>
  <div class="error-page">
    <h1 class="error-code">404 {{ $t('app.notFound') }}</h1>
    <p class="error-message">{{ $t('app.pageNotFound') }}</p>
    <router-link
      class="home-link"
      :to="{ name: 'home' }"
    >
      ← {{ $t('app.backToHome') }}
    </router-link>
  </div>
</template>

<script>
/**
 * 全局错误页面组件
 * @displayName 错误页面
 */
export default {
  name: 'NotFoundView',
  metaInfo: {
    title: '页面未找到'
  }
};
</script>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}
</style>
