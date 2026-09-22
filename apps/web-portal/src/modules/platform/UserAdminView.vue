<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
const users=ref<any[]>([]),roles=ref<any[]>([]),departments=ref<any[]>([])
const loading=ref(false),dialog=ref(false),saving=ref(false),keyword=ref(''),editingId=ref<number|null>(null),fileInput=ref<HTMLInputElement|null>(null)
const page=ref(1),size=ref(20),total=ref(0)
const systemOptions=[['mdm','MDM'],['crm','CRM'],['erp','ERP'],['plm','PLM'],['srm','SRM'],['wms','WMS'],['mes','MES'],['qms','QMS'],['eam','EAM'],['energy','能源']]
const form=reactive<any>({})
async function load(){loading.value=true;try{const[u,r,d]=await Promise.all([http.get('/admin/users',{params:{keyword:keyword.value||undefined,page:page.value,size:size.value}}),http.get('/admin/role-options'),http.get('/admin/department-options')]);users.value=u.data.data.content;total.value=u.data.data.totalElements;roles.value=r.data.data;departments.value=d.data.data;if(!users.value.length&&total.value>0&&page.value>1){page.value--;await load()}}finally{loading.value=false}}
function search(){page.value=1;load()}
function create(){editingId.value=null;Object.assign(form,{username:'',initialPassword:'Test@123456',realName:'',empCode:'',deptCode:'',positionName:'',email:'',mobile:'',demoAccount:true,roleIds:[],systems:[],dataScopeType:'ROLE'});dialog.value=true}
function edit(row:any){editingId.value=row.id;Object.assign(form,{username:row.username,initialPassword:'',realName:row.realName,empCode:row.empCode||'',deptCode:row.deptCode||'',positionName:row.positionName||'',email:row.email||'',mobile:row.mobile||'',demoAccount:row.demoAccount,roleIds:row.roles.map((r:any)=>r.id),systems:[...row.systems],dataScopeType:row.dataScopeType||'ROLE'});dialog.value=true}
async function save(){if(!form.username||!form.realName)return ElMessage.warning('请填写用户名和姓名');const creating=editingId.value===null;saving.value=true;try{const response=editingId.value?await http.put(`/admin/users/${editingId.value}`,form):await http.post('/admin/users',form);const id=editingId.value||response.data.data.id;await http.put(`/admin/users/${id}/data-scope`,{scopeType:form.dataScopeType,values:[]});ElMessage.success(editingId.value?'用户已更新':'用户已创建');dialog.value=false;if(creating)page.value=1;await load()}catch(e:any){ElMessage.error(e.message||'保存失败')}finally{saving.value=false}}
async function toggle(row:any){await http.post(`/admin/users/${row.id}/${row.enabled?'disable':'enable'}`);ElMessage.success(row.enabled?'账号已停用':'账号已启用');load()}
async function lock(row:any){await http.post(`/admin/users/${row.id}/${row.locked?'unlock':'lock'}`);ElMessage.success(row.locked?'账号已解锁':'账号已锁定');load()}
async function remove(row:any){await ElMessageBox.confirm(`确认删除用户“${row.realName}”吗？历史业务记录仍会保留。`,'删除用户',{type:'warning'});await http.delete(`/admin/users/${row.id}`);ElMessage.success('用户已逻辑删除');load()}
async function resetPassword(row:any){const{value}=await ElMessageBox.prompt('输入至少8位的新密码','重置密码',{inputType:'password',inputPattern:/.{8,}/,inputErrorMessage:'密码至少8位'});await http.post(`/admin/users/${row.id}/reset-password`,{password:value});ElMessage.success('密码已重置，账号同时解除锁定')}
async function exportUsers(){const response=await http.get('/admin/users/export',{responseType:'blob'});const url=URL.createObjectURL(response.data);const a=document.createElement('a');a.href=url;a.download='users.csv';a.click();URL.revokeObjectURL(url)}
async function importUsers(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;const data=new FormData();data.append('file',file);try{const response=await http.post('/admin/users/import',data);ElMessage.success(`导入完成：新增 ${response.data.data.created}，跳过 ${response.data.data.skipped}`);page.value=1;await load()}catch(e:any){ElMessage.error(e.message||'导入失败')}finally{input.value=''}}
onMounted(load)
</script>
<template>
  <section class="admin-page">
    <div class="page-head"><div><span>IDENTITY & ACCESS</span><h1>用户与系统授权</h1><p>统一维护账号、岗位、角色、业务系统入口和账号状态。</p></div><div class="head-actions"><input ref="fileInput" type="file" accept=".csv,text/csv" hidden @change="importUsers"/><el-button @click="fileInput?.click()">导入 CSV</el-button><el-button @click="exportUsers">导出 CSV</el-button><el-button type="primary" @click="create">新增用户</el-button></div></div>
    <el-card shadow="never">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="搜索用户名、姓名或工号" style="width:320px" @keyup.enter="search"/><el-button @click="search">查询</el-button></div>
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" min-width="110"/><el-table-column prop="realName" label="姓名" min-width="100"/><el-table-column prop="deptCode" label="部门" min-width="100"/><el-table-column prop="positionName" label="岗位" min-width="130"/>
        <el-table-column label="角色" min-width="190"><template #default="{row}"><el-tag v-for="role in row.roles.slice(0,2)" :key="role.id" size="small" effect="plain">{{role.name}}</el-tag><span v-if="row.roles.length>2" class="more">+{{row.roles.length-2}}</span></template></el-table-column>
        <el-table-column label="系统" min-width="120"><template #default="{row}">{{row.systems.length}} 个系统</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.locked?'danger':row.enabled?'success':'info'">{{row.locked?'已锁定':row.enabled?'已启用':'已停用'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" fixed="right" width="270"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link @click="toggle(row)">{{row.enabled?'停用':'启用'}}</el-button><el-button link type="warning" @click="lock(row)">{{row.locked?'解锁':'锁定'}}</el-button><el-dropdown><el-button link>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="resetPassword(row)">重置密码</el-dropdown-item><el-dropdown-item divided @click="remove(row)">删除用户</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>
    <el-dialog v-model="dialog" :title="editingId?'编辑用户':'新增用户'" width="760px" destroy-on-close>
      <el-form label-position="top"><el-row :gutter="16">
        <el-col :span="12"><el-form-item label="用户名" required><el-input v-model="form.username" :disabled="Boolean(editingId)"/></el-form-item></el-col>
        <el-col v-if="!editingId" :span="12"><el-form-item label="初始密码" required><el-input v-model="form.initialPassword" type="password" show-password/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="姓名" required><el-input v-model="form.realName"/></el-form-item></el-col><el-col :span="12"><el-form-item label="工号"><el-input v-model="form.empCode"/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="部门"><el-select v-model="form.deptCode" clearable filterable style="width:100%"><el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.deptCode"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="岗位"><el-input v-model="form.positionName"/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="form.email"/></el-form-item></el-col><el-col :span="12"><el-form-item label="手机"><el-input v-model="form.mobile"/></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="角色"><el-select v-model="form.roleIds" multiple filterable style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="`${r.roleName}（${r.roleCode}）`" :value="r.id"/></el-select></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="可访问系统"><el-checkbox-group v-model="form.systems"><el-checkbox v-for="s in systemOptions" :key="s[0]" :value="s[0]">{{s[1]}}</el-checkbox></el-checkbox-group></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="数据范围"><el-select v-model="form.dataScopeType" style="width:100%"><el-option label="沿用角色规则" value="ROLE"/><el-option label="全部数据" value="ALL"/><el-option label="本部门" value="DEPT"/><el-option label="仅本人" value="SELF"/><el-option label="无业务数据" value="NONE"/></el-select></el-form-item></el-col>
      </el-row></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存用户</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>.admin-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.head-actions,.toolbar{display:flex;gap:8px}.toolbar{margin-bottom:14px}.el-tag+.el-tag{margin-left:4px}.more{margin-left:5px;color:#8a99aa;font-size:11px}</style>
