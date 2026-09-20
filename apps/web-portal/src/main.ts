import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
const app=createApp(App);const pinia=createPinia();app.use(pinia).use(router).use(ElementPlus)
useAuthStore(pinia).initCrossTabSync(()=>{ if(router.currentRoute.value.path!=='/login') router.replace('/login') })
app.mount('#app')
