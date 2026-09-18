import { defineStore } from 'pinia'
import http from '../api/http'
export interface DemoAccount { username: string; realName: string }
export interface LoginUser { username: string; realName: string; roles?: string[]; systems?: string[]; permissions?: string[] }
export const useAuthStore = defineStore('auth', { state: () => ({ token: localStorage.getItem('mfg_token') || '', user: null as LoginUser | null }), actions: { async login(username: string, password: string) { const { data } = await http.post('/auth/login', { username, password }); this.token = data.data.token; this.user = data.data; localStorage.setItem('mfg_token', this.token) }, async loadMe() { const { data } = await http.get('/auth/me'); const u = data.data; this.user = { username: u.username, realName: u.realName, systems: u.systems, permissions: u.permissions, roles: u.roleCodes } }, logout() { this.token = ''; this.user = null; localStorage.removeItem('mfg_token') } } })
