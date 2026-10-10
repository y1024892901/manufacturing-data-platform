<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import { zh } from '../display'

type Step = { nodeSeq:number; nodeName:string; approverRole:string; assignedUser?:string; assignedName?:string; status:string; opinion?:string; finishedAt?:string }
type Progress = { instanceNo:string; bizNo:string; bizTitle:string; status:string; submitter:string; submittedAt:string; steps:Step[]; events:any[]; myTaskId?:number }
const props=defineProps<{modelValue:boolean;instanceId:number|null}>()
const emit=defineEmits<{ 'update:modelValue':[value:boolean];updated:[] }>()
const progress=ref<Progress|null>(null),loading=ref(false),saving=ref(false),opinion=ref('')
const visible=computed({get:()=>props.modelValue,set:(value:boolean)=>emit('update:modelValue',value)})
const activeStep=computed(()=>progress.value?.steps.findIndex(step=>step.status==='PENDING')??-1)
function stepStatus(step:Step){return step.status==='APPROVED'?'success':step.status==='REJECTED'?'error':step.status==='PENDING'?'process':'wait'}
async function load(){if(!props.instanceId)return;loading.value=true;try{const{data}=await http.get(`/workflow/instances/${props.instanceId}/progress`);progress.value=data.data;opinion.value=''}finally{loading.value=false}}
watch(()=>[props.modelValue,props.instanceId],([open,id])=>{if(open&&id)load();else if(!open)progress.value=null})
async function act(action:'approve'|'reject'){
  if(!progress.value?.myTaskId)return
  if(action==='reject'&&opinion.value.trim().length<2){await ElMessageBox.alert('请填写至少两个字的驳回原因，方便发起人修改报价。','请填写审批意见');return}
  saving.value=true
  try{
    await http.post(`/workflow/tasks/${progress.value.myTaskId}/${action}`,{opinion:opinion.value.trim()||'同意'})
    ElMessage.success(action==='approve'?'审批已流转':'报价已驳回')
    await load();emit('updated')
  }catch{/* 错误由全局弹框展示 */}finally{saving.value=false}
}
</script>

<template>
  <el-drawer v-model="visible" title="审批进度" size="50%" :destroy-on-close="true">
    <div v-loading="loading">
      <template v-if="progress">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="流程编号">{{progress.instanceNo}}</el-descriptions-item>
          <el-descriptions-item label="当前状态"><el-tag>{{zh(progress.status)}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="业务单号">{{progress.bizNo}}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{progress.submitter}}</el-descriptions-item>
          <el-descriptions-item label="业务摘要" :span="2">{{progress.bizTitle}}</el-descriptions-item>
        </el-descriptions>
        <h3>审批流程</h3>
        <el-steps :active="activeStep<0?progress.steps.length:activeStep" align-center finish-status="success">
          <el-step v-for="step in progress.steps" :key="step.nodeSeq" :title="step.nodeName" :description="step.assignedName||step.assignedUser||step.approverRole" :status="stepStatus(step)" />
        </el-steps>
        <el-table :data="progress.steps" stripe>
          <el-table-column prop="nodeName" label="审批级次" min-width="130" />
          <el-table-column label="审批人" min-width="140"><template #default="{row}">{{row.assignedName||row.assignedUser||row.approverRole}}</template></el-table-column>
          <el-table-column label="进度" width="120"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':row.status==='REJECTED'?'danger':row.status==='PENDING'?'warning':'info'">{{row.status==='WAITING'?'待前序审批':zh(row.status)}}</el-tag></template></el-table-column>
          <el-table-column prop="opinion" label="审批意见" min-width="170"><template #default="{row}">{{row.opinion||'—'}}</template></el-table-column>
        </el-table>
        <template v-if="progress.myTaskId">
          <h3>处理当前审批</h3>
          <el-input v-model="opinion" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写审批意见；驳回时必须说明原因" />
          <el-space wrap><el-button type="primary" :loading="saving" @click="act('approve')">同意并流转</el-button><el-button type="danger" plain :loading="saving" @click="act('reject')">驳回</el-button></el-space>
        </template>
        <h3>处理记录</h3>
        <el-timeline v-if="progress.events.length">
          <el-timeline-item v-for="(event,index) in progress.events" :key="index" :timestamp="event.operatedAt" :type="event.action==='APPROVE'?'success':event.action==='REJECT'?'danger':'primary'">
            {{event.actionLabel}} · {{event.nodeName}} · {{event.operatorName||event.operator}}<span v-if="event.opinion">：{{event.opinion}}</span>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无处理记录" />
      </template>
    </div>
  </el-drawer>
</template>
