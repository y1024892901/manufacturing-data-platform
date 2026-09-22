<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'

const tab = ref('operations')
const operations = ref<any[]>([])
const logins = ref<any[]>([])
const loading = ref(false)
const operationPage = ref(1), operationSize = ref(20), operationTotal = ref(0)
const loginPage = ref(1), loginSize = ref(20), loginTotal = ref(0)

async function loadOperations() {
  loading.value = true
  try {
    const { data } = await http.get('/admin/audit-logs', { params: { page: operationPage.value, size: operationSize.value } })
    operations.value = data.data.content
    operationTotal.value = data.data.totalElements
    if (!operations.value.length && operationTotal.value > 0 && operationPage.value > 1) {
      operationPage.value--
      await loadOperations()
    }
  } finally { loading.value = false }
}

async function loadLogins() {
  loading.value = true
  try {
    const { data } = await http.get('/admin/login-logs', { params: { page: loginPage.value, size: loginSize.value } })
    logins.value = data.data.content
    loginTotal.value = data.data.totalElements
    if (!logins.value.length && loginTotal.value > 0 && loginPage.value > 1) {
      loginPage.value--
      await loadLogins()
    }
  } finally { loading.value = false }
}

function refresh() { return tab.value === 'operations' ? loadOperations() : loadLogins() }
onMounted(() => Promise.all([loadOperations(), loadLogins()]))
</script>

<template>
  <section class="audit-page">
    <div class="page-head"><div><span>审计与合规</span><h1>统一审计日志</h1><p>业务变更不记录密码和请求正文，只保留操作者、对象、动作、来源地址与结果。</p></div><el-button @click="refresh">刷新日志</el-button></div>
    <el-card shadow="never" v-loading="loading">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`操作日志（${operationTotal}）`" name="operations">
          <el-table :data="operations" stripe><el-table-column prop="operatedAt" label="操作时间" width="175"/><el-table-column prop="operatorName" label="操作者" width="110"/><el-table-column prop="operator" label="账号" width="110"/><el-table-column prop="systemCode" label="系统" width="95"/><el-table-column prop="action" label="动作" width="110"><template #default="{row}">{{$zh(row.action)}}</template></el-table-column><el-table-column prop="objectType" label="业务对象" width="150"/><el-table-column prop="objectId" label="对象编号" width="100"/><el-table-column prop="objectName" label="请求路径" min-width="260"/><el-table-column prop="ipAddress" label="网络地址" width="130"/></el-table>
          <TablePager v-model:page="operationPage" v-model:size="operationSize" :total="operationTotal" :disabled="loading" @change="loadOperations"/>
        </el-tab-pane>
        <el-tab-pane :label="`登录日志（${loginTotal}）`" name="logins">
          <el-table :data="logins" stripe><el-table-column prop="loginAt" label="登录时间" width="175"/><el-table-column prop="realName" label="姓名" width="110"/><el-table-column prop="username" label="账号" width="120"/><el-table-column label="结果" width="100"><template #default="{row}"><el-tag :type="row.loginStatus==='SUCCESS'?'success':'danger'">{{$zh(row.loginStatus)}}</el-tag></template></el-table-column><el-table-column prop="failReason" label="失败原因" min-width="180"/><el-table-column prop="ipAddress" label="网络地址" width="130"/><el-table-column prop="userAgent" label="客户端" min-width="300" show-overflow-tooltip/></el-table>
          <TablePager v-model:page="loginPage" v-model:size="loginSize" :total="loginTotal" :disabled="loading" @change="loadLogins"/>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<style scoped>.audit-page{max-width:1500px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}</style>
