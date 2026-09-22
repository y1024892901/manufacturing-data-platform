import axios from 'axios'
export interface ApiResponse<T> { code: number; message: string; data: T }
const http = axios.create({ baseURL: '/api', timeout: 15000 })
http.interceptors.request.use((config) => { const token = localStorage.getItem('mfg_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') return response
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0 && body.code !== 200) return Promise.reject(new Error(body.message || '请求失败'))
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('mfg_token')
      localStorage.removeItem('mfg_user')
      // 路由守卫读的是 store 里的 token，只清 localStorage 不够：
      // 在 /login 等不触发跳转的场景下，store 仍持有失效 token，会被守卫弹回 /portal。
      // 动态 import 是为了避开 http.ts ↔ stores/auth.ts 的模块级循环依赖。
      Promise.resolve()
        .then(() => import('../stores/auth'))
        .then(({ useAuthStore }) => useAuthStore().clearLocal())
        .catch(() => { /* 忽略：localStorage 已清，跳转照常进行 */ })
        .finally(() => {
          if (window.location.pathname !== '/login') window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
        })
    }
    const message = error.response?.data?.message || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  }
)
export default http
