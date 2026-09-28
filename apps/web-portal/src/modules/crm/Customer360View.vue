<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import { zh } from '../../shared/display'
const code=ref(''),data=ref<any>(null),loading=ref(false)
const sections=computed<[string,any][]>(()=>Object.entries(data.value||{}).filter(([name])=>name!=='customer') as [string,any][])
function display(value:any,key:string){if(['auto_renew','autoRenew','is_primary','isPrimary'].includes(key))return value===true||value===1||value==='1'?'是':'否';return zh(value)}
async function load(){if(!code.value.trim()){ElMessage.warning('请输入客户编码');return}loading.value=true;data.value=null;try{const r=await http.get(`/crm/customer-360/${encodeURIComponent(code.value.trim())}`);data.value=r.data.data}catch{/* API 错误已由全局弹窗提示 */}finally{loading.value=false}}
</script>
<template><section><div class="head"><div><span>客户全景视图</span><h1>客户360</h1><p>从客户主数据追溯联系人、商机、报价、合同、应收账款和客诉。</p></div></div><el-card shadow="never"><div class="search"><el-input v-model="code" placeholder="输入客户编码" @keyup.enter="load"/><el-button type="primary" :loading="loading" @click="load">查询</el-button></div><el-skeleton v-if="loading" :rows="6" animated/><template v-else-if="data"><el-descriptions title="客户档案" :column="3" border><el-descriptions-item v-for="(v,k) in data.customer" :key="k" :label="$zhKey(String(k))">{{display(v,String(k))}}</el-descriptions-item></el-descriptions><el-tabs class="tabs"><el-tab-pane v-for="[name,list] in sections" :key="name" :label="$zhKey(name)"><el-table :data="list" stripe empty-text="暂无相关记录"><el-table-column v-for="field in Object.keys(list?.[0]||{})" :key="field" :label="$zhKey(field)" min-width="130" show-overflow-tooltip><template #default="{row}">{{display(row[field],field)}}</template></el-table-column></el-table></el-tab-pane></el-tabs></template><el-empty v-else description="请输入客户编码查看客户完整业务信息"/></el-card></section></template>
<style scoped>.head{margin-bottom:16px}.head span{font-size:11px;color:#3976d5}.head h1{margin:5px 0}.head p{color:#8391a4}.search{display:flex;gap:8px;margin-bottom:18px}.search .el-input{width:320px}.tabs{margin-top:16px}</style>
