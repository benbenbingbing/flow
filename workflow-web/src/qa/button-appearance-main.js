import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ButtonAppearanceQa from './ButtonAppearanceQa.vue'

createApp(ButtonAppearanceQa)
  .use(ElementPlus)
  .mount('#app')
