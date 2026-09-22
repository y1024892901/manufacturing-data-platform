import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { zh, zhKey } from './shared/display'
const app=createApp(App);const pinia=createPinia();app.use(pinia).use(router).use(ElementPlus,{locale:zhCn})
app.config.globalProperties.$zh=zh
app.config.globalProperties.$zhKey=zhKey
useAuthStore(pinia).initCrossTabSync(()=>{ if(router.currentRoute.value.path!=='/login') router.replace('/login') })
app.mount('#app')
