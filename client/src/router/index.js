import { createRouter, createWebHistory } from 'vue-router';
import i18n from '@/locales/i18n';
import HomeView from '@/views/HomeView.vue';
import NotFoundView from '@/views/NotFoundView.vue';

/**
 * 应用路由配置
 * @module router
 * @desc 使用Vue Router管理前端路由，包含历史模式配置和路由守卫
 */

import ChartsView from '@/views/ChartsView.vue';
import MembershipView from '@/views/MembershipView.vue';

const router = createRouter({
  history: createWebHistory(), // 启用History模式（去除URL中的#号）
  routes: [
    {
      path: '/',
      name: 'home',
      meta: { title: 'app.title' }, // 路由元信息：对应i18n的键
      component: HomeView
    },
    {
      path: '/charts',
      name: 'charts',
      meta: { title: 'chart.title' },
      component: ChartsView
    },
    {
      path: '/membership',
      name: 'membership',
      meta: { title: 'membership.title' },
      component: MembershipView
    },
    {
      path: '/photo.html',
      name: 'photo',
      meta: { title: 'photo.title' },
      component: () => import('@/../photo.html')
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView
    }
  ],
  scrollBehavior (_to, _from, savedPosition) {
    return savedPosition || { top: 0 };
  }
});

// 全局前置守卫：整合所有路由控制逻辑
router.beforeEach(async (to, _from, next) => {
  try {
    const userAgent = navigator.userAgent || 'unknown';
    // 更新页面标题
    if (to.meta.title) {
      document.title = i18n.global.t(to.meta.title);
    }

    // 记录路由访问日志
    console.log(`[Route Access] ${to.name} route accessed - User-Agent: ${userAgent}`);

    next();
  } catch (error) {
    console.error('路由守卫错误:', error);
    next();
  }
});

export default router;
