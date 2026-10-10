<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore, type DemoAccount } from '../stores/auth'
const router = useRouter(); const auth = useAuthStore(); const accounts = ref<DemoAccount[]>([]); const username = ref(''); const password = ref(''); const loading = ref(false)
const accountGroups = computed(() => {
  const groups = new Map<string, DemoAccount[]>()
  for (const account of accounts.value) {
    const key = account.deptName || account.deptCode || '其他权限域'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(account)
  }
  return [...groups.entries()].map(([name, items]) => ({ name, items }))
})
onMounted(async () => { const { data } = await http.get('/auth/demo-accounts'); accounts.value = data.data })
async function login() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请先选择或输入账号，并输入密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    ElMessage.success('登录成功')
    const redirect = typeof router.currentRoute.value.query.redirect === 'string'
      ? router.currentRoute.value.query.redirect
      : '/portal'
    router.replace(redirect)
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查网络、账号和密码')
  } finally {
    loading.value = false
  }
}
</script>
<template><main class="login"><section class="intro"><div class="intro-brand"><span>M</span><b>智造运营中台</b></div><div class="intro-content"><div class="eyebrow">MANUFACTURING COMMAND CENTER</div><h1>让每一次业务动作<br/>都成为可追溯的数据。</h1><p>统一主数据、十系统协同、自研审批和经营驾驶舱，串联从客户需求到制造交付的完整业务过程。</p><div class="capabilities"><div><b>10</b><span>业务系统协同</span></div><div><b>1</b><span>统一主数据中心</span></div><div><b>∞</b><span>可追溯业务链路</span></div></div></div><div class="intro-flow">主数据 → 订单 → 计划 → 制造 → 质量 → 经营分析</div></section><section class="login-panel"><div class="login-card"><div class="panel-header"><h2>欢迎登录</h2><p>按权限域选择岗位，进入对应业务工作台</p></div><el-alert title="演示账号默认密码：Test@123456" type="info" :closable="false"/><el-form label-position="top" @submit.prevent="login"><el-form-item label="账号"><el-input v-model="username" prefix-icon="User" placeholder="请输入账号" autocomplete="username"/></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" show-password placeholder="请输入密码" autocomplete="current-password" @keyup.enter="login"/></el-form-item><el-button type="primary" :loading="loading" @click="login">进入运营工作台</el-button></el-form><div class="account-title">按权限域选择演示身份</div><div class="account-groups"><div v-for="group in accountGroups" :key="group.name" class="account-group"><div class="group-title">{{group.name}}</div><div class="accounts"><el-button v-for="a in group.items" :key="a.username" plain @click="username=a.username"><span>{{a.realName}}</span><small>{{a.positionName || a.username}}</small><small v-if="a.roleNames?.length">{{a.roleNames.join(' / ')}}</small></el-button></div></div></div></div></section></main></template>
<style scoped>
.login { display: grid; min-height: 100vh; grid-template-columns: minmax(0, 1.08fr) minmax(400px, 0.92fr); align-items: start; background: var(--vyre-surface-background); }
.intro { position: sticky; top: 0; display: flex; height: 100vh; min-height: 100vh; flex-direction: column; overflow: hidden; padding: var(--vyre-space-8) clamp(var(--vyre-space-6), 8vw, 112px); color: var(--vyre-content-inverse); background: radial-gradient(ellipse at 82% 14%, var(--vyre-color-navy-700) 0%, var(--vyre-color-navy-900) 43%, var(--vyre-color-navy-950) 100%); }
.intro::after { position: absolute; right: -150px; bottom: -180px; width: 480px; height: 480px; border: 1px solid var(--vyre-surface-login-glow); border-radius: 50%; box-shadow: 0 0 0 55px var(--vyre-surface-login-ring), 0 0 0 112px var(--vyre-surface-login-ring-outer); content: ""; pointer-events: none; }
.intro-brand { position: relative; z-index: 1; display: flex; align-items: center; gap: var(--vyre-space-3); }
.intro-brand span { display: grid; width: 40px; height: 40px; place-items: center; border-radius: var(--vyre-radius-sm); background: var(--vyre-color-blue-600); color: var(--vyre-content-inverse); font-weight: 800; }
.intro-brand b { font-size: 16px; }
.intro-content { position: relative; z-index: 1; width: min(100%, 600px); margin: auto 0; padding: var(--vyre-space-8) 0; }
.eyebrow { color: var(--vyre-color-blue-200); font-size: 11px; letter-spacing: 2px; }
.intro h1 { margin-top: var(--vyre-space-5); font-size: clamp(32px, 3vw, 44px); letter-spacing: 1px; line-height: 1.38; }
.intro p { max-width: 510px; margin-top: var(--vyre-space-5); color: var(--vyre-content-login-copy); font-size: 14px; line-height: 1.9; }
.capabilities { display: flex; flex-wrap: wrap; gap: var(--vyre-space-8); margin-top: 42px; }
.capabilities div { display: flex; flex-direction: column; gap: var(--vyre-space-1); }
.capabilities b { color: var(--vyre-color-blue-200); font-size: 25px; }
.capabilities span { color: #d3e1f1; font-size: 12px; }
.intro-flow { position: relative; z-index: 1; color: #c1d5eb; font-size: 12px; letter-spacing: 0.5px; }
.login-panel { display: grid; min-height: 100vh; place-items: center; padding: var(--vyre-space-8) clamp(var(--vyre-space-5), 5vw, 72px); }
.login-card { width: min(100%, 480px); }
.panel-header h2 { color: var(--vyre-color-navy-900); font-size: 28px; }
.panel-header p { margin: var(--vyre-space-2) 0 var(--vyre-space-5); color: var(--vyre-content-secondary); font-size: 13px; line-height: 1.6; }
.login-card :deep(.el-alert) { margin-bottom: var(--vyre-space-3); border-radius: var(--vyre-radius-sm); }
.login-card :deep(.el-form-item) { margin-bottom: var(--vyre-space-3); }
.login-card :deep(.el-input) { margin: 0; }
.login-card :deep(.el-input__wrapper) { min-height: 44px; }
.login-card :deep(.el-button--primary) { width: 100%; min-height: 46px; margin-top: var(--vyre-space-3); font-weight: 650; }
.account-title { margin: var(--vyre-space-6) 0 var(--vyre-space-3); color: var(--vyre-color-slate-700); font-size: 13px; font-weight: 700; }
.account-group { margin-bottom: var(--vyre-space-4); }
.group-title { margin-bottom: var(--vyre-space-2); padding-left: var(--vyre-space-3); border-left: 3px solid var(--vyre-color-blue-600); color: var(--vyre-color-slate-700); font-size: 12px; font-weight: 700; }
.accounts { display: flex; max-height: 210px; flex-wrap: wrap; gap: var(--vyre-space-2); overflow: auto; padding: 2px; }
.accounts .el-button { display: flex; min-height: 50px; height: auto; flex-direction: column; align-items: flex-start; gap: 2px; margin: 0; padding: var(--vyre-space-2) var(--vyre-space-3); color: var(--vyre-color-slate-700); text-align: left; }
.accounts .el-button:hover { border-color: var(--vyre-color-blue-600); background: var(--vyre-color-blue-100); }
.accounts small { color: var(--vyre-content-secondary); font-size: 10px; }
@media (max-width: 900px) {
  .login { grid-template-columns: minmax(0, 0.85fr) minmax(360px, 1.15fr); }
  .intro { padding: var(--vyre-space-6); }
  .intro h1 { font-size: 32px; }
  .capabilities { gap: var(--vyre-space-5); }
}
@media (max-width: 680px) {
  .login { grid-template-columns: 1fr; }
  .intro { position: relative; height: auto; min-height: auto; padding: var(--vyre-space-5) var(--vyre-space-4); }
  .intro-content { margin: var(--vyre-space-8) 0 var(--vyre-space-5); padding: 0; }
  .intro h1 { margin-top: var(--vyre-space-3); font-size: 28px; }
  .intro p { margin-top: var(--vyre-space-3); font-size: 12px; }
  .capabilities { gap: var(--vyre-space-5); margin-top: var(--vyre-space-5); }
  .intro-flow { display: none; }
  .login-panel { min-height: auto; padding: var(--vyre-space-6) var(--vyre-space-4); }
  .panel-header h2 { font-size: 24px; }
  .accounts { max-height: 240px; }
}
</style>
