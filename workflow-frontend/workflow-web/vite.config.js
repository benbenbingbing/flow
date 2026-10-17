import { workflowBoundaryPlugin } from '../scripts/workflow-boundary.mjs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { flowExtensionsPlugin } from './build/extensions/vite-plugin.mjs'
import { resolve } from 'path'
import { discoverBusinessModules } from '../scripts/extensions/modules.mjs'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [workflowBoundaryPlugin('pc'), flowExtensionsPlugin(), vue()],
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('bpmn-js') || id.includes('diagram-js') || id.includes('bpmn-auto-layout')) {
            return 'vendor-bpmn'
          }
          if (id.includes('@codemirror') || id.includes('codemirror')) {
            return 'vendor-editor'
          }
          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'vendor-ui'
          }
          if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
            return 'vendor-vue'
          }
          return 'vendor'
        }
      }
    }
  },
  // 自定义包通过原始公开子入口交给 Vue 编译，新业务无需修改应用依赖优化名单。
  optimizeDeps: { exclude: ['@flow/workflow-extensions', '@flow/workflow-core', '@flow/workflow-api', ...[...discoverBusinessModules().values()].map(module => module.name)] },
  resolve: {
    dedupe: ['vue', 'pinia', 'vue-router'],
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
