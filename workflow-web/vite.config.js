import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
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
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
