/**
 * Vite配置
 * @module vite.config
 * @description 客户端Vite构建配置
 */
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import path from 'path';
import { VitePWA } from 'vite-plugin-pwa';

/**
 * Vite配置对象
 * @type {import('vite').UserConfig}
 */

import replace from '@rollup/plugin-replace';
import pkg from './package.json' with { type: 'json' };

export default defineConfig({
  build: {
    target: 'esnext', // 提升目标环境版本以支持现代CSS特性（如嵌套语法）
    rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['vue', 'vue-router', 'pinia'],
            utils: ['axios', 'dayjs'],
            // 拆分大型依赖包
            excel: ['xlsx'],
            csv: ['papaparse'],
            markdown: ['marked'],
            highlight: ['highlight.js']
          }
        }
    },
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  },

  base: '/', // 确保构建资源路径为根目录
  assetsDir: 'assets', // 静态资源存放目录（与服务器public目录结构一致）
  server: {

    host: '0.0.0.0', // 监听所有网络接口，支持局域网访问
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://192.168.0.197:3010', // 替换为后端实际局域网IP
        secure: false,
        changeOrigin: true
      }
    },
    historyApiFallback: true
  },
  plugins: [
    replace({
      preventAssignment: true,
      __VERSION__: pkg.version,
      __BUILD_TIME__: new Date().toLocaleString()
    }),
    vue(),
    AutoImport({
      // 基本配置，确保插件能正常工作
      imports: ['vue'], // 自动导入Vue的ref、reactive等
      dts: 'auto-imports.d.ts'
    }),
    Components({
      // 基本配置，确保插件能正常工作
      dts: 'components.d.ts'
    }),
    VitePWA({
      injectRegister: 'autoUpdate',
      manifest: false,
      workbox: {
        runtimeCaching: [
          {
            urlPattern: /^https?:\/\/192.168.0.197:3010\/api\//,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-cache',
              networkTimeoutSeconds: 10,
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 60 * 60 * 24 * 7
              },
              cacheableResponse: {
                statuses: [0, 200]
              }
            }
          },
          {
            urlPattern: /\.(?:js|css|html|png|jpg|jpeg|svg|ico)$/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'assets-cache',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 60 * 60 * 24 * 30
              }
            }
          }
        ]
      }
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      vue: path.resolve(__dirname, 'node_modules/vue/dist/vue.esm-bundler.js'),
      // 强制使用本地内存缓存实现，覆盖第三方冲突的CacheStore类
      CacheStore: path.resolve(__dirname, './src/utils/CacheStore.js')
    }
  }
});
