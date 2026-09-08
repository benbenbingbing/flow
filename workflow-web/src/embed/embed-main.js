import { createApp, defineComponent, h } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import EmbedApp from './EmbedApp.vue'
import { registerApplicationExtensions } from '@/extensions/register'
import { configureElementPlusPopupDefaults } from '@/shared/element-plus-defaults'
import { enableEphemeralUserStoreRuntime } from '@/stores/user'

const EmptyEmbedRoute = defineComponent({
  name: 'EmptyEmbedRoute',
  render: () => h('span', { hidden: true })
})

function createEmbedMemoryRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{
      path: '/:pathMatch(.*)*',
      component: EmptyEmbedRoute
    }]
  })
}

/**
 * 独立 Embed 入口。它注册与 Flow 主应用相同的扩展，并为原生运行时提供隔离的
 * Pinia/内存路由；不会挂载管理 Layout、登录路由，也不会恢复浏览器登录态。
 */
export function mountEmbedApp(target = '#app') {
  // 必须早于 Shell/useUserStore 实例化，彻底隔离管理端 session/local storage。
  enableEphemeralUserStoreRuntime()
  configureElementPlusPopupDefaults()
  registerApplicationExtensions({
    enableDemo: import.meta.env.DEV
      || import.meta.env.VITE_ENABLE_DEMO_EXTENSIONS === 'true'
  })
  const app = createApp(EmbedApp)
  // 与 Flow 正式运行态保持同一 Element Plus locale、图标和主题入口。
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }
  app.use(createPinia())
  app.use(createEmbedMemoryRouter())
  app.use(ElementPlus, { locale: zhCn })
  return app.mount(target)
}

if (typeof document !== 'undefined' && document.querySelector('#app')) {
  mountEmbedApp('#app')
}
