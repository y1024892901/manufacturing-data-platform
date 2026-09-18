import axios from 'axios'
export interface ApiResponse<T> { code: number; message: string; data: T }
const http = axios.create({ baseURL: '/api', timeout: 15000 })
http.interceptors.request.use((config) => { const token = localStorage.getItem('mfg_token'); if (token) config.headers.Authorization = `Bearer ${token}`; return config })
http.interceptors.response.use((response) => { const body = response.data as ApiResponse<unknown>; if (body.code !== 0 && body.code !== 200) return Promise.reject(new Error(body.message || '请求失败')); return response })
export default http
