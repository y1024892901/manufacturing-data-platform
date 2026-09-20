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
      if (window.location.pathname !== '/login') window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
    }
    const message = error.response?.data?.message || error.message || '网络请求失败'
    return Promise.reject(new Error(message))
  }
)
export default http
