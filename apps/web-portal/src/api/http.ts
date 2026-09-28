import axios from 'axios'
import { showErrorDialog } from '../shared/errorDialog'
export interface ApiResponse<T> { code: number; message: string; data: T }
const http = axios.create({ baseURL: '/api', timeout: 15000 })
http.interceptors.request.use((config) => { const token = localStorage.getItem('mfg_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
function showRequestError(message: unknown) {
  const text = typeof message === 'string' && message.trim() ? message : '请求失败，请稍后重试'
  showErrorDialog(text)
}
http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') return response
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0 && body.code !== 200) {
      const message = body.message || '请求失败'
      showRequestError(message)
      return Promise.reject(new Error(message))
    }
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
    const serverMessage = error.response?.data?.message
    const statusMessages: Record<number, string> = {
      400: '请求参数有误，请检查后重试',
      401: '登录状态已失效，请重新登录',
      403: '当前账号没有执行此操作的权限',
      404: '请求的数据或服务不存在',
      405: '当前操作不受支持',
      409: '数据状态已变化，请刷新后重试',
      422: '数据校验未通过，请检查填写内容',
      500: '服务器处理失败，请稍后重试',
      502: '服务暂不可用，请稍后重试',
      503: '服务正在维护，请稍后重试'
    }
    const message = typeof serverMessage === 'string' && serverMessage.trim()
      ? serverMessage
      : error.code === 'ECONNABORTED'
        ? '请求超时，请检查服务状态后重试'
        : error.response?.status
          ? statusMessages[error.response.status] || `请求失败（${error.response.status}）`
          : '网络连接失败，请检查网络或服务状态'
    showRequestError(message)
    return Promise.reject(new Error(message))
  }
)
export default http
