import { workflowBoundaryPlugin } from '../../scripts/workflow-boundary.mjs'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [workflowBoundaryPlugin('mobile'), vue()],
  build: {
    lib: { entry: fileURLToPath(new URL('./src/index.js', import.meta.url)), formats: ['es'], fileName: 'index', cssFileName: 'style' },
    rollupOptions: { external: id => ['vue', 'vant', 'dompurify'].includes(id) || id.startsWith('@flow/workflow-core') }
  }
})
