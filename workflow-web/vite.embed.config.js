import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * Embed Runtime 独立构建。
 *
 * 动态 Entry HTML 由后端按 Launch 的父 Origin 输出精确 CSP，因此这里必须提供
 * 不带 hash 的稳定同源资源名。该构建不导入后台 router/store，也不会恢复普通登录态。
 */
export default defineConfig({
  plugins: [vue()],
  // Library mode 不会像普通 Vite 应用一样替换 Vue bundler 的 Node 环境探测；
  // 若保留裸 process.env，独立 iframe 会在浏览器启动阶段直接失败。
  define: {
    'process.env.NODE_ENV': JSON.stringify('production')
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist/embed-assets',
    emptyOutDir: false,
    cssCodeSplit: true,
    lib: {
      entry: resolve(__dirname, 'src/embed/embed-main.js'),
      formats: ['es'],
      fileName: () => 'embed-main.js',
      cssFileName: 'embed-main'
    }
  }
})
