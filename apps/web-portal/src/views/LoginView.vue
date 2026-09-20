<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore, type DemoAccount } from '../stores/auth'
const router = useRouter(); const auth = useAuthStore(); const accounts = ref<DemoAccount[]>([]); const username = ref(''); const password = ref(''); const loading = ref(false)
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
<template><main class="login"><section class="intro"><div class="intro-brand"><span>M</span><b>智造运营中台</b></div><div class="intro-content"><div class="eyebrow">MANUFACTURING COMMAND CENTER</div><h1>让每一次业务动作<br/>都成为可追溯的数据。</h1><p>统一主数据、十系统协同、自研审批和经营驾驶舱，串联从客户需求到制造交付的完整业务过程。</p><div class="capabilities"><div><b>10</b><span>业务系统协同</span></div><div><b>1</b><span>统一主数据中心</span></div><div><b>∞</b><span>可追溯业务链路</span></div></div></div><div class="intro-flow">主数据 → 订单 → 计划 → 制造 → 质量 → 经营分析</div></section><section class="login-panel"><div class="login-card"><div class="panel-header"><h2>欢迎登录</h2><p>选择演示身份，体验不同岗位的业务视角</p></div><el-alert title="选择身份后请输入对应演示密码。" type="info" :closable="false"/><el-form @submit.prevent="login"><el-input v-model="username" prefix-icon="User" placeholder="账号"/><el-input v-model="password" type="password" show-password placeholder="密码" @keyup.enter="login"/><el-button type="primary" :loading="loading" @click="login">进入运营工作台</el-button></el-form><div class="account-title">快捷选择演示身份</div><div class="accounts"><el-button v-for="a in accounts" :key="a.username" plain @click="username=a.username">{{a.realName}}<small>{{a.username}}</small></el-button></div></div></section></main></template>
<style scoped>.login{display:grid;grid-template-columns:56% 44%;min-height:100vh;background:#f7f9fc}.intro{position:relative;overflow:hidden;padding:45px 9%;color:#fff;background:radial-gradient(circle at 77% 15%,#2f78cc 0,#173e76 35%,#0a1d38 80%)}.intro:after{content:"";position:absolute;right:-150px;bottom:-180px;width:480px;height:480px;border:1px solid rgba(126,194,255,.25);border-radius:50%;box-shadow:0 0 0 55px rgba(126,194,255,.05),0 0 0 112px rgba(126,194,255,.04)}.intro-brand{display:flex;align-items:center;gap:10px}.intro-brand span{display:grid;place-items:center;width:35px;height:35px;border-radius:10px;background:#4c9dff;font-weight:800}.intro-brand b{font-size:16px}.intro-content{position:absolute;top:25%;max-width:600px}.eyebrow{letter-spacing:2px;font-size:11px;color:#82bdf8}.intro h1{margin-top:18px;font-size:42px;line-height:1.32;letter-spacing:1px}.intro p{margin-top:20px;max-width:510px;line-height:1.9;color:#b6cce5}.capabilities{display:flex;gap:40px;margin-top:42px}.capabilities div{display:flex;flex-direction:column;gap:5px}.capabilities b{font-size:25px;color:#74b7ff}.capabilities span{font-size:12px;color:#b7cbe2}.intro-flow{position:absolute;bottom:44px;color:#88add5;font-size:12px;letter-spacing:.6px}.login-panel{display:grid;place-items:center;padding:40px}.login-card{width:min(460px,100%)}.panel-header h2{font-size:28px;color:#1e385b}.panel-header p{margin:10px 0 22px;color:#8898ac;font-size:13px}.login-card :deep(.el-alert){margin-bottom:15px}.login-card :deep(.el-input){margin:7px 0}.login-card :deep(.el-button--primary){width:100%;height:42px;margin-top:12px}.account-title{margin:28px 0 12px;font-size:13px;font-weight:700;color:#48617d}.accounts{display:flex;max-height:210px;overflow:auto;flex-wrap:wrap;gap:8px}.accounts .el-button{display:flex;flex-direction:column;gap:2px;height:48px;margin:0;padding:5px 10px;color:#4c6480}.accounts small{font-size:10px;color:#91a1b4}</style>
