import { createApp } from 'vue'
import EmbedApp from './EmbedApp.vue'

/**
 * 独立 Embed 入口。该模块只创建最小 Vue 应用，不导入普通 router、store、
 * 登录恢复、后台扩展注册或通用 request。
 */
export function mountEmbedApp(target = '#app') {
  return createApp(EmbedApp).mount(target)
}

if (typeof document !== 'undefined' && document.querySelector('#app')) {
  mountEmbedApp('#app')
}
