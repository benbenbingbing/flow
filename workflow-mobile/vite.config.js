import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import { workflowBoundaryPlugin } from '../scripts/workflow-boundary.mjs'
import { mobileExtensionsPlugin } from '../scripts/mobile-extensions.mjs'

const envDir = fileURLToPath(new URL('..', import.meta.url))
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, envDir, '')
  return {
    base: '/m/', envDir,
    define: { 'import.meta.env.VITE_WEB_PORT': JSON.stringify(process.env.WEB_PORT || env.WEB_PORT || '3000') },
    optimizeDeps: { exclude: ['@flow/workflow-core', '@flow/workflow-api', '@flow/workflow-mobile-ui'] },
    plugins: [workflowBoundaryPlugin('mobile'), mobileExtensionsPlugin(), vue()],
    resolve: { dedupe: ['vue', 'pinia', 'vue-router'] },
    server: {
      host: '0.0.0.0', port: Number(process.env.MOBILE_PORT || env.MOBILE_PORT || 3001), strictPort: true,
      proxy: { '/api': { target: process.env.VITE_API_PROXY_TARGET || env.VITE_API_PROXY_TARGET || `http://127.0.0.1:${env.SERVER_PORT || 8080}`, changeOrigin: true } }
    }
  }
})
