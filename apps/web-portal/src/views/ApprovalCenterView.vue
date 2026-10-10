<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import TablePager from '../shared/components/TablePager.vue'
import WorkflowProgressDrawer from '../shared/components/WorkflowProgressDrawer.vue'

const tab = ref('pending')
const pending = ref<any[]>([]), handled = ref<any[]>([]), started = ref<any[]>([]), copied = ref<any[]>([])
const pendingLoading = ref(false), handledLoading = ref(false), startedLoading = ref(false), copiedLoading = ref(false)
const pendingPage = ref(1), pendingSize = ref(20), pendingTotal = ref(0)
const handledPage = ref(1), handledSize = ref(20), handledTotal = ref(0)
const startedPage = ref(1), startedSize = ref(20), startedTotal = ref(0)
const copiedPage = ref(1), copiedSize = ref(20), copiedTotal = ref(0)
const progressVisible=ref(false),progressInstanceId=ref<number|null>(null)
const users=ref<any[]>([]),assignDialog=ref(false),copyDialog=ref(false),working=ref(false)
const assignTask=ref<any>(null),assignAction=ref<'transfer'|'add-sign'>('transfer'),assignUsername=ref(''),assignReason=ref('')
const copyInstance=ref<any>(null),copyUsernames=ref<string[]>([]),copyReason=ref('')

async function loadUsers(){if(users.value.length)return;const{data}=await http.get('/workflow/users');users.value=data.data||[]}

async function loadPending() {
  pendingLoading.value = true
  try {
    const { data } = await http.get('/workflow/tasks/pending', { params: { page: pendingPage.value, size: pendingSize.value } })
    pending.value = data.data.content
    pendingTotal.value = data.data.totalElements
    if (!pending.value.length && pendingTotal.value > 0 && pendingPage.value > 1) {
      pendingPage.value--
      await loadPending()
    }
  } finally { pendingLoading.value = false }
}

async function loadHandled() {
  handledLoading.value = true
  try {
    const { data } = await http.get('/workflow/tasks/handled', { params: { page: handledPage.value, size: handledSize.value } })
    handled.value = data.data.content
    handledTotal.value = data.data.totalElements
    if (!handled.value.length && handledTotal.value > 0 && handledPage.value > 1) {
      handledPage.value--
      await loadHandled()
    }
  } finally { handledLoading.value = false }
}

async function loadStarted() {
  startedLoading.value = true
  try {
    const { data } = await http.get('/workflow/instances/started', { params: { page: startedPage.value, size: startedSize.value } })
    started.value = data.data.content
    startedTotal.value = data.data.totalElements
    if (!started.value.length && startedTotal.value > 0 && startedPage.value > 1) {
      startedPage.value--
      await loadStarted()
    }
  } finally { startedLoading.value = false }
}

async function loadCopied() {
  copiedLoading.value = true
  try {
    const { data } = await http.get('/workflow/tasks/copied', { params: { page: copiedPage.value, size: copiedSize.value } })
    copied.value = data.data.content; copiedTotal.value = data.data.totalElements
    if (!copied.value.length && copiedTotal.value > 0 && copiedPage.value > 1) { copiedPage.value--; await loadCopied() }
  } finally { copiedLoading.value = false }
}
async function loadAll() { await Promise.all([loadPending(), loadHandled(), loadStarted(), loadCopied()]) }

async function assign(row:any, action:'transfer'|'add-sign') {try{await loadUsers();assignTask.value=row;assignAction.value=action;assignUsername.value='';assignReason.value='';assignDialog.value=true}catch{/* 全局弹框 */}}
async function submitAssignment(){if(!assignUsername.value||assignReason.value.trim().length<2){ElMessage.warning('请选择审批人并填写操作原因');return}working.value=true;try{await http.post(`/workflow/tasks/${assignTask.value.taskId}/${assignAction.value}`,{username:assignUsername.value,mode:'BEFORE',reason:assignReason.value.trim()});ElMessage.success(assignAction.value==='transfer'?'已转交':'已完成前加签');assignDialog.value=false;await loadPending()}catch{/* 全局弹框 */}finally{working.value=false}}
async function copyTo(row:any){try{await loadUsers();copyInstance.value=row;copyUsernames.value=[];copyReason.value='';copyDialog.value=true}catch{/* 全局弹框 */}}
async function submitCopy(){if(!copyUsernames.value.length){ElMessage.warning('请选择抄送人');return}working.value=true;try{await http.post(`/workflow/instances/${copyInstance.value.id}/cc`,{usernames:copyUsernames.value,reason:copyReason.value.trim()||'发起人抄送'});ElMessage.success('已抄送');copyDialog.value=false;await loadCopied()}catch{/* 全局弹框 */}finally{working.value=false}}

async function act(row: any, action: 'approve' | 'reject') {
  let opinion = '同意'
  if (action === 'reject') {
    try {
      const result = await ElMessageBox.prompt('请输入明确的驳回与修订意见', '驳回审批', { inputPattern: /.{2,}/, inputErrorMessage: '至少输入2个字符' })
      opinion = result.value
    } catch { return }
  }
  try {
    await http.post(`/workflow/tasks/${row.taskId}/${action}`, { opinion })
    ElMessage.success(action === 'approve' ? '审批已流转' : '已驳回')
    await loadAll()
  } catch (error: any) { ElMessage.error(error.message || '审批失败') }
}

function showProgress(instanceId:number){progressInstanceId.value=instanceId;progressVisible.value=true}

async function cancel(row: any) {
  try {
    await ElMessageBox.confirm('确认撤回该审批申请吗？', '撤回确认', { type: 'warning' })
    await http.post(`/workflow/instances/${row.id}/cancel`, { opinion: '发起人撤回' })
    ElMessage.success('申请已撤回')
    await Promise.all([loadPending(), loadStarted()])
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error(error.message || '撤回失败')
  }
}

onMounted(loadAll)
</script>

<template>
  <section class="approval-page">
    <div class="page-head"><div><span>统一审批流程</span><h1>统一审批中心</h1><p>集中处理主数据、工程变更、订单例外和跨部门业务事项。</p></div><el-button @click="loadAll">刷新</el-button></div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`待办（${pendingTotal}）`" name="pending">
          <el-table :data="pending" v-loading="pendingLoading" stripe><el-table-column prop="instanceNo" label="流程编号" min-width="155"/><el-table-column prop="bizType" label="业务类型" width="120"><template #default="{row}"><el-tag effect="plain">{{$zh(row.bizType)}}</el-tag></template></el-table-column><el-table-column prop="bizNo" label="业务单号" min-width="135"/><el-table-column prop="bizTitle" label="业务摘要" min-width="220"/><el-table-column prop="submitter" label="发起人" width="105"/><el-table-column prop="nodeName" label="当前节点" width="135"/><el-table-column prop="waitingHours" label="等待(h)" width="90"/><el-table-column label="操作" width="350" fixed="right"><template #default="{row}"><el-button link type="primary" @click="act(row,'approve')">同意</el-button><el-button link type="danger" @click="act(row,'reject')">驳回</el-button><el-button link @click="assign(row,'transfer')">转交</el-button><el-button link @click="assign(row,'add-sign')">加签</el-button><el-button link @click="showProgress(row.instanceId)">审批进度</el-button></template></el-table-column></el-table>
          <el-empty v-if="!pendingLoading&&!pending.length" description="当前身份没有待处理审批"/>
          <TablePager v-model:page="pendingPage" v-model:size="pendingSize" :total="pendingTotal" :disabled="pendingLoading" @change="loadPending"/>
        </el-tab-pane>
        <el-tab-pane :label="`已办（${handledTotal}）`" name="handled">
          <el-table :data="handled" v-loading="handledLoading" stripe><el-table-column prop="instanceNo" label="流程编号"/><el-table-column prop="bizType" label="业务类型"/><el-table-column prop="bizNo" label="业务单号"/><el-table-column prop="nodeName" label="处理节点"/><el-table-column prop="result" label="处理结果"><template #default="{row}"><el-tag :type="row.result==='APPROVED'?'success':'danger'">{{$zh(row.result)}}</el-tag></template></el-table-column><el-table-column prop="opinion" label="意见" min-width="180"/><el-table-column prop="finishedAt" label="处理时间" width="175"/><el-table-column label="操作" width="85"><template #default="{row}"><el-button link @click="showProgress(row.instanceId)">审批进度</el-button></template></el-table-column></el-table>
          <TablePager v-model:page="handledPage" v-model:size="handledSize" :total="handledTotal" :disabled="handledLoading" @change="loadHandled"/>
        </el-tab-pane>
        <el-tab-pane :label="`我发起的（${startedTotal}）`" name="started">
          <el-table :data="started" v-loading="startedLoading" stripe><el-table-column prop="instanceNo" label="流程编号"/><el-table-column prop="bizType" label="业务类型"/><el-table-column prop="bizNo" label="业务单号"/><el-table-column prop="bizTitle" label="业务摘要" min-width="220"/><el-table-column prop="status" label="状态"><template #default="{row}"><el-tag>{{$zh(row.status)}}</el-tag></template></el-table-column><el-table-column prop="submittedAt" label="发起时间" width="175"/><el-table-column label="操作" width="200"><template #default="{row}"><el-button link @click="showProgress(row.id)">审批进度</el-button><el-button link @click="copyTo(row)">抄送</el-button><el-button v-if="row.status==='RUNNING'" link type="warning" @click="cancel(row)">撤回</el-button></template></el-table-column></el-table>
          <TablePager v-model:page="startedPage" v-model:size="startedSize" :total="startedTotal" :disabled="startedLoading" @change="loadStarted"/>
        </el-tab-pane>
        <el-tab-pane :label="`抄送我的（${copiedTotal}）`" name="copied">
          <el-table :data="copied" v-loading="copiedLoading" stripe><el-table-column prop="instanceId" label="流程实例"/><el-table-column prop="username" label="抄送人"/><el-table-column prop="reason" label="抄送原因" min-width="220"/><el-table-column prop="readFlag" label="已读"><template #default="{row}"><el-tag :type="row.readFlag?'success':'warning'">{{row.readFlag?'已读':'未读'}}</el-tag></template></el-table-column><el-table-column prop="createdAt" label="抄送时间" width="175"/><el-table-column label="操作" width="85"><template #default="{row}"><el-button link @click="showProgress(row.instanceId)">审批进度</el-button></template></el-table-column></el-table>
          <el-empty v-if="!copiedLoading&&!copied.length" description="暂无抄送记录"/><TablePager v-model:page="copiedPage" v-model:size="copiedSize" :total="copiedTotal" :disabled="copiedLoading" @change="loadCopied"/>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <WorkflowProgressDrawer v-model="progressVisible" :instance-id="progressInstanceId" @updated="loadAll" />
    <el-dialog v-model="assignDialog" :title="assignAction==='transfer'?'转交审批':'加签审批'" width="480px">
      <el-form label-position="top"><el-form-item label="选择审批人" required><el-select v-model="assignUsername" filterable clearable style="width:100%" placeholder="搜索姓名或账号"><el-option v-for="user in users.filter(item=>item.canApprove)" :key="user.username" :label="`${user.realName}（${user.username}） · ${user.positionName||user.deptCode||'审批人'}`" :value="user.username" /></el-select></el-form-item><el-form-item label="操作原因" required><el-input v-model="assignReason" type="textarea" :rows="3" /></el-form-item></el-form>
      <template #footer><el-button @click="assignDialog=false">取消</el-button><el-button type="primary" :loading="working" @click="submitAssignment">确定</el-button></template>
    </el-dialog>
    <el-dialog v-model="copyDialog" title="选择抄送人" width="520px">
      <el-form label-position="top"><el-form-item label="抄送人员" required><el-select v-model="copyUsernames" multiple filterable clearable collapse-tags style="width:100%" placeholder="搜索姓名或账号，可多选"><el-option v-for="user in users" :key="user.username" :label="`${user.realName}（${user.username}） · ${user.deptCode||'未分配部门'}`" :value="user.username" /></el-select></el-form-item><el-form-item label="抄送说明（选填）"><el-input v-model="copyReason" type="textarea" :rows="2" /></el-form-item></el-form>
      <template #footer><el-button @click="copyDialog=false">取消</el-button><el-button type="primary" :loading="working" @click="submitCopy">确定抄送</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>.approval-page{max-width:1500px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.el-timeline p{color:#708198;font-size:12px}</style>
