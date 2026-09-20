import { defineStore } from 'pinia'
import http from '../api/http'

export interface DemoAccount { username: string; realName: string }
export interface LoginUser {
  userId?: number
  username: string
  realName: string
  deptCode?: string
  deptName?: string
  positionName?: string
  roles?: string[]
  roleNames?: string[]
  systems?: string[]
  permissions?: string[]
}

const USER_KEY = 'mfg_user'
const TOKEN_KEY = 'mfg_token'
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('mfg-auth') : null

function savedUser(): LoginUser | null {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ token: localStorage.getItem(TOKEN_KEY) || '', user: savedUser(), syncReady: false }),
  getters: {
    isAdmin: state => Boolean(state.user?.roles?.includes('ADMIN')),
    canAccess: state => (system: string) => Boolean(state.user?.roles?.includes('ADMIN') || state.user?.systems?.includes(system))
  },
  actions: {
    persist() {
      if (this.token) localStorage.setItem(TOKEN_KEY, this.token); else localStorage.removeItem(TOKEN_KEY)
      if (this.user) localStorage.setItem(USER_KEY, JSON.stringify(this.user)); else localStorage.removeItem(USER_KEY)
    },
    async login(username: string, password: string) {
      const { data } = await http.post('/auth/login', { username, password })
      this.token = data.data.token
      this.user = data.data
      this.persist()
      channel?.postMessage({ type: 'LOGIN' })
    },
    async loadMe() {
      if (!this.token) return
      const { data } = await http.get('/auth/me')
      const u = data.data
      this.user = { userId: u.userId, username: u.username, realName: u.realName, deptCode: u.deptCode, positionName: u.positionName, systems: u.systems, permissions: u.permissions, roles: u.roleCodes }
      this.persist()
    },
    clearLocal() { this.token = ''; this.user = null; this.persist() },
    async logout() {
      try { if (this.token) await http.post('/auth/logout') } finally {
        this.clearLocal()
        channel?.postMessage({ type: 'LOGOUT' })
      }
    },
    initCrossTabSync(onLogout: () => void) {
      if (this.syncReady) return
      this.syncReady = true
      const handle = () => { if (!localStorage.getItem(TOKEN_KEY)) { this.clearLocal(); onLogout() } }
      window.addEventListener('storage', event => { if (event.key === TOKEN_KEY) handle() })
      channel?.addEventListener('message', event => { if (event.data?.type === 'LOGOUT') handle() })
    }
  }
})
