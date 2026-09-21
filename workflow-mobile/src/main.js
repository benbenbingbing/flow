import { createApp } from 'vue'
import 'vant/lib/index.css'
import '@flow/workflow-mobile-ui/style.css'
import './styles/app.css'
import App from './App.vue'
import { router } from './router.js'
import 'virtual:flow-mobile-extensions'
import { loadSystemTheme } from './theme/index.js'

// 主题在组件挂载前读取，避免登录页和首屏先显示旧色；失败由加载器回退默认主题。
loadSystemTheme().then(() => createApp(App).use(router).mount('#app'))
