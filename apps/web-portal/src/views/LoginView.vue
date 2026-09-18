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
    router.push('/')
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请检查网络、账号和密码')
  } finally {
    loading.value = false
  }
}
</script>
<template><main class="login"><section><h1>制造业运营数据平台</h1><p>十系统协同 · 统一主数据 · 自研审批</p><el-alert title="先选择演示身份，再输入演示密码后登录。" type="info" :closable="false"/><el-form @submit.prevent="login"><el-input v-model="username" placeholder="账号"/><el-input v-model="password" type="password" show-password placeholder="密码" @keyup.enter="login"/><el-button type="primary" :loading="loading" @click="login">登录</el-button></el-form><h3>选择演示身份</h3><div class="accounts"><el-button v-for="a in accounts" :key="a.username" plain @click="username=a.username">{{a.realName}} · {{a.username}}</el-button></div></section></main></template>
<style scoped>.login{min-height:100vh;display:grid;place-items:center;background:linear-gradient(135deg,#071b35,#1a5593);color:#fff}.login section{width:480px}.login p{opacity:.8}.login :deep(.el-input){margin:8px 0}.login :deep(.el-button--primary){width:100%;margin-top:10px}.accounts{display:flex;flex-wrap:wrap;gap:8px}.accounts .el-button{margin:0}</style>
