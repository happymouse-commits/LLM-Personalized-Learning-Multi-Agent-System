import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { ArrowDown, FullScreen } from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import scrollReveal from './utils/scrollReveal.js'
import Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-python'

const app = createApp(App)
app.use(ElementPlus)
app.use(createPinia())
app.use(router)

// 按需注册实际用到的图标（只注册了两个，不再全量注册300+个图标组件）
app.component('ArrowDown', ArrowDown)
app.component('FullScreen', FullScreen)

// 全局滚动渐入动画指令 v-reveal
app.directive('reveal', scrollReveal)

app.mount('#app')

// 全局代码高亮：仅在包含代码块的页面执行
router.afterEach(() => {
  setTimeout(() => {
    if (document.querySelector('pre[class*="language-"], code[class*="language-"]')) {
      Prism.highlightAll()
    }
  }, 100)
})
