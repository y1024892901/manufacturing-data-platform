<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../../api/http'
import { zh } from '../../shared/display'
import TablePager from '../../shared/components/TablePager.vue'
import WorkflowProgressDrawer from '../../shared/components/WorkflowProgressDrawer.vue'

const rows=ref<any[]>([]),loading=ref(false),page=ref(1),size=ref(20),total=ref(0),status=ref('PENDING'),keyword=ref('')
const progressVisible=ref(false),progressInstanceId=ref<number|null>(null)
async function load(){loading.value=true;try{const{data}=await http.get('/crm/quotations',{params:{page:page.value,size:size.value,status:status.value||undefined,keyword:keyword.value||undefined}});rows.value=data.data?.content||[];total.value=data.data?.totalElements||0}finally{loading.value=false}}
function search(){page.value=1;load()}
function showProgress(row:any){progressInstanceId.value=Number(row.workflow_instance_id);progressVisible.value=true}
onMounted(load)
</script>

<template>
  <section>
    <header><h1>报价审批</h1><p>查看报价的逐级审批进度；轮到你审批时，可在进度中直接处理。</p></header>
    <el-card shadow="never">
      <el-space wrap>
        <el-input v-model="keyword" clearable placeholder="报价编号、客户名称或联系人" @keyup.enter="search" />
        <el-select v-model="status" clearable placeholder="全部状态" @change="search"><el-option label="待审批" value="PENDING"/><el-option label="已通过" value="APPROVED"/><el-option label="已驳回" value="REJECTED"/><el-option label="已生效" value="EFFECTIVE"/></el-select>
        <el-button @click="search">查询</el-button><el-button @click="load">刷新</el-button>
      </el-space>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无报价审批记录">
        <el-table-column prop="quotation_no" label="报价编号" min-width="150" />
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="customer_name" label="客户名称" min-width="180" />
        <el-table-column prop="contact_person" label="主要联系人" min-width="120" />
        <el-table-column prop="total_amount" label="报价金额" min-width="140" />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag>{{zh(row.status)}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button v-if="row.workflow_instance_id" link type="primary" @click="showProgress(row)">审批进度 / 处理</el-button><span v-else>尚未提交审批</span></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
    <WorkflowProgressDrawer v-model="progressVisible" :instance-id="progressInstanceId" @updated="load" />
  </section>
</template>
