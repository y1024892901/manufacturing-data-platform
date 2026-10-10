<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import WorkflowProgressDrawer from '../../shared/components/WorkflowProgressDrawer.vue'
import { useAuthStore } from '../../stores/auth'
import { zh } from '../../shared/display'
import { loadMdmUnits } from '../../shared/mdmOptions'

type Field = { key:string; label:string; type?:'text'|'number'|'date'|'datetime'|'select'|'textarea'|'boolean'; required?:boolean; readonly?:boolean; options?:{label:string;value:string|number|boolean}[]; min?:number; max?:number; placeholder?:string }
const props=defineProps<{kind:string}>()
const auth=useAuthStore()
const configs:Record<string,{title:string;api:string;columns:{key:string;label:string;width?:number}[]}>={
  leads:{title:'销售线索',api:'/crm/leads',columns:[{key:'lead_no',label:'线索编号'},{key:'customer_code',label:'客户编码'},{key:'customer_name',label:'客户名称'},{key:'source_channel',label:'线索来源'},{key:'intent_level',label:'意向程度'},{key:'expected_budget',label:'预估预算'},{key:'expected_close_date',label:'预计成交日'},{key:'status',label:'状态'},{key:'owner_name',label:'负责人'}]},
  opportunities:{title:'商机管理',api:'/crm/opportunities',columns:[{key:'opportunity_no',label:'商机编号'},{key:'opportunity_name',label:'商机名称'},{key:'customer_code',label:'客户编码'},{key:'customer_name',label:'客户名称'},{key:'source_lead_no',label:'来源线索'},{key:'expect_amount',label:'预计金额'},{key:'probability',label:'赢单概率'},{key:'stage_code',label:'销售阶段'},{key:'expected_close_date',label:'预计成交日'},{key:'forecast_category',label:'预测分类'},{key:'priority',label:'优先级'},{key:'owner_name',label:'负责人'},{key:'next_step',label:'下一步计划'}]},
  quotations:{title:'报价管理',api:'/crm/quotations',columns:[{key:'quotation_no',label:'报价编号'},{key:'version_no',label:'版本'},{key:'opportunity_no',label:'关联商机'},{key:'customer_code',label:'客户编码'},{key:'customer_name',label:'客户名称'},{key:'customer_type',label:'客户类型'},{key:'customer_level',label:'客户等级'},{key:'industry',label:'所属行业'},{key:'region',label:'所在地区'},{key:'contact_person',label:'主要联系人'},{key:'contact_phone',label:'联系电话'},{key:'total_amount',label:'报价总额'},{key:'currency',label:'币种'},{key:'discount_rate',label:'折扣率'},{key:'gross_margin_rate',label:'毛利率'},{key:'valid_until',label:'有效期至'},{key:'payment_terms',label:'付款条件'},{key:'status',label:'状态'}]},
  contracts:{title:'合同管理',api:'/crm/contracts',columns:[{key:'contract_no',label:'合同编号'},{key:'contract_name',label:'合同名称'},{key:'customer_name',label:'客户名称'},{key:'quotation_no',label:'来源报价'},{key:'amount',label:'合同金额'},{key:'currency',label:'币种'},{key:'effective_date',label:'生效日期'},{key:'expire_date',label:'到期日期'},{key:'auto_renew',label:'自动续约'},{key:'status',label:'状态'},{key:'erp_sales_order_no',label:'ERP订单号'}]},
  forecast:{title:'销售预测',api:'/crm/forecast',columns:[{key:'forecast_month',label:'预测月份'},{key:'owner_user',label:'负责人'},{key:'dept_code',label:'所属部门'},{key:'product_code',label:'产品编码'},{key:'forecast_amount',label:'预测金额'},{key:'weighted_amount',label:'加权预测金额'}]},
  complaints:{title:'客诉协同',api:'/crm/complaints',columns:[{key:'complaint_no',label:'客诉编号'},{key:'customer_code',label:'客户编码'},{key:'sales_order_no',label:'销售订单'},{key:'delivery_no',label:'交付单号'},{key:'product_code',label:'产品编码'},{key:'batch_no',label:'批次号'},{key:'severity',label:'严重程度'},{key:'source_channel',label:'问题来源'},{key:'response_due_at',label:'响应期限'},{key:'desired_resolution',label:'客户期望方案'},{key:'status',label:'状态'}]}
}
const c=computed(()=>configs[props.kind]||configs.leads)
const rows=ref<any[]>([]),page=ref(1),size=ref(20),total=ref(0),keyword=ref(''),status=ref(''),loading=ref(false)
const dialog=ref(false),detailDialog=ref(false),editingId=ref<number|null>(null),saving=ref(false),detail=ref<any>(null)
const approvalDialog=ref(false),approvalSaving=ref(false),approvalRow=ref<any>(null),approvalPlan=ref<any>(null),approvalUsers=ref<any[]>([]),approvalApprovers=ref<string[]>([]),approvalExtraCount=ref(0)
const progressVisible=ref(false),progressInstanceId=ref<number|null>(null)
const form=reactive<any>({}), opportunities=ref<any[]>([]),effectiveQuotes=ref<any[]>([]),customerOptions=ref<any[]>([]),leadOptions=ref<any[]>([]),productOptions=ref<any[]>([])
const assignDialog=ref(false),followDialog=ref(false),actionSaving=ref(false),actionLead=ref<any>(null),leadAssignees=ref<any[]>([])
const assignmentForm=reactive({username:'',reason:''}),followForm=reactive({followType:'CALL',content:'',nextFollowAt:''})
const stageDialog=ref(false),stageSaving=ref(false),stageOpportunity=ref<any>(null),stageForm=reactive({code:'',reason:''})
const unitOptions=ref<{label:string;value:string}[]>([])
function defaultUnitCode(){return unitOptions.value.find(unit=>unit.value==='PCS')?.value||unitOptions.value[0]?.value||''}
async function loadSharedUnits(){unitOptions.value=(await loadMdmUnits()).map(unit=>({label:unit.label,value:unit.value}))}
const stageOptions=[{label:'初步线索',value:'LEAD'},{label:'需求确认',value:'QUALIFY'},{label:'方案报价',value:'PROPOSAL'},{label:'商务谈判',value:'NEGOTIATE'},{label:'赢单',value:'WON'},{label:'丢单',value:'LOST'}]
const availableStages=computed(()=>{const current=String(fieldValue(stageOpportunity.value,'stage_code')||'LEAD');const order=stageOptions.filter(x=>x.value!=='LOST').map(x=>x.value);const index=order.indexOf(current);return stageOptions.filter(x=>x.value==='LOST'||index<0||order.indexOf(x.value)>index)})
const commonFields=reactive<Record<string,Field[]>>({
 leads:[{key:'leadNo',label:'线索编号',readonly:true,placeholder:'留空则自动生成'},{key:'customerId',label:'客户档案',type:'select',required:true,options:[]},{key:'sourceChannel',label:'线索来源',type:'select',options:['展会','官网','客户转介绍','电话开发','合作伙伴','其他'].map(x=>({label:x,value:x}))},{key:'intentLevel',label:'意向程度',type:'select',options:[{label:'高',value:'HIGH'},{label:'中',value:'MEDIUM'},{label:'低',value:'LOW'}]},{key:'score',label:'线索评分',type:'number',min:0,max:100},{key:'expectedBudget',label:'预估预算',type:'number',min:0},{key:'expectedCloseDate',label:'预计成交日',type:'date'},{key:'preferredContactMethod',label:'偏好联系渠道'}],
 opportunities:[{key:'opportunityNo',label:'商机编号',readonly:true,placeholder:'留空则自动生成'},{key:'opportunityName',label:'商机名称',required:true},{key:'sourceLeadNo',label:'来源线索（选填）',type:'select',options:[]},{key:'customerId',label:'客户档案',type:'select',required:true,options:[]},{key:'expectAmount',label:'预计金额',type:'number',min:0},{key:'probability',label:'赢单概率（%）',type:'number',min:0,max:100},{key:'expectedCloseDate',label:'预计成交日',type:'date'},{key:'leadSource',label:'商机来源'},{key:'forecastCategory',label:'预测分类',type:'select',options:['PIPELINE','BEST_CASE','COMMIT','OMIT'].map(x=>({label:({PIPELINE:'销售机会',BEST_CASE:'最佳情景',COMMIT:'承诺成交',OMIT:'不纳入预测'} as any)[x],value:x}))},{key:'priority',label:'优先级',type:'select',options:[{label:'高',value:'HIGH'},{label:'中',value:'MEDIUM'},{label:'低',value:'LOW'}]},{key:'nextStep',label:'下一步计划',type:'textarea'},{key:'description',label:'商机描述',type:'textarea'},{key:'remark',label:'备注',type:'textarea'}],
 quotations:[{key:'quotationNo',label:'报价编号',readonly:true,placeholder:'留空则自动生成'},{key:'opportunityNo',label:'关联商机',type:'select',required:true,options:[]},{key:'customerCode',label:'客户编码',required:true,readonly:true,placeholder:'选择商机后自动带入'},{key:'currency',label:'币种',type:'select',options:[{label:'人民币 CNY',value:'CNY'},{label:'美元 USD',value:'USD'},{label:'欧元 EUR',value:'EUR'}]},{key:'taxRate',label:'税率（%）',type:'number',min:0,max:100},{key:'validUntil',label:'报价有效期',type:'date'},{key:'paymentTerms',label:'付款条件'},{key:'deliveryTerms',label:'交付条件'},{key:'shipToAddress',label:'收货地址',type:'textarea'},{key:'notes',label:'报价说明',type:'textarea'}],
 contracts:[{key:'contractNo',label:'合同编号',readonly:true,placeholder:'留空则自动生成'},{key:'contractName',label:'合同名称',required:true},{key:'quotationId',label:'来源报价',type:'select',required:true,readonly:true,options:[]},{key:'effectiveDate',label:'生效日期',type:'date'},{key:'expireDate',label:'到期日期',type:'date'},{key:'signingDate',label:'签署日期',type:'date'},{key:'deliveryTerms',label:'交付条件',type:'textarea'},{key:'paymentTerms',label:'付款条件',type:'textarea'},{key:'penaltyTerms',label:'违约条款',type:'textarea'},{key:'autoRenew',label:'自动续约',type:'boolean'},{key:'renewalNoticeDays',label:'续约提前通知天数',type:'number',min:0},{key:'specialTerms',label:'特殊条款',type:'textarea'},{key:'attachmentUrl',label:'合同附件链接'}],
 complaints:[{key:'complaintNo',label:'客诉编号',placeholder:'留空则自动生成'},{key:'customerCode',label:'客户编码',required:true},{key:'salesOrderNo',label:'销售订单号'},{key:'deliveryNo',label:'交付单号'},{key:'productCode',label:'产品编码'},{key:'batchNo',label:'生产批次'},{key:'severity',label:'严重程度',type:'select',options:[{label:'高',value:'HIGH'},{label:'中',value:'MEDIUM'},{label:'低',value:'LOW'}],required:true},{key:'problemDesc',label:'问题描述',type:'textarea',required:true},{key:'contactEmail',label:'客户联系邮箱'},{key:'sourceChannel',label:'问题来源'},{key:'responseDueAt',label:'响应期限',type:'datetime'},{key:'desiredResolution',label:'客户期望方案',type:'textarea'},{key:'responsibleDept',label:'责任部门'}]
})
const fields=computed(()=>props.kind==='quotations'?commonFields.quotations:props.kind==='contracts'?commonFields.contracts:(commonFields[props.kind]||[]))
const visibleColumns=computed(()=>c.value.columns)
const selectedCustomer=computed(()=>customerOptions.value.find(customer=>String(customer.id)===String(form.customerId)))
const selectedQuoteOpportunity=computed(()=>opportunities.value.find(o=>String(o.opportunityNo)===String(form.opportunityNo)))
const selectedQuoteCustomer=computed(()=>selectedQuoteOpportunity.value?{customerCode:selectedQuoteOpportunity.value.customerCode,customerName:selectedQuoteOpportunity.value.customerName,contactName:selectedQuoteOpportunity.value.contactName}:null)
const statuses=computed(()=>props.kind==='leads'?['NEW','FOLLOWING','CONVERTED','INVALID']:props.kind==='opportunities'?['LEAD','QUALIFY','PROPOSAL','NEGOTIATE','WON','LOST']:props.kind==='quotations'?['DRAFT','PENDING','APPROVED','REJECTED','EFFECTIVE','EXPIRED','VOID']:props.kind==='contracts'?['DRAFT','PENDING','APPROVED','REJECTED','ACTIVE','TERMINATED','CANCELED']:props.kind==='complaints'?['OPEN','IN_PROGRESS','CLOSED']:[])
function clearForm(){for(const k of Object.keys(form))delete form[k]}
function fromDb(row:any){const out:any={};for(const [k,v] of Object.entries(row||{}))out[k.replace(/_([a-z])/g,(_,c)=>c.toUpperCase())]=v;return out}
async function load(){loading.value=true;try{const{data}=await http.get(c.value.api,{params:{page:page.value,size:size.value,keyword:keyword.value||undefined,status:status.value||undefined}});rows.value=data.data?.content||[];total.value=data.data?.totalElements||0}finally{loading.value=false}}
function optionFields(){
  const customerChoices=customerOptions.value.map(customer=>({label:`${customer.customerCode} · ${customer.customerName}`,value:customer.id}))
  commonFields.leads.find(f=>f.key==='customerId')!.options=customerChoices
  commonFields.opportunities.find(f=>f.key==='customerId')!.options=customerChoices
  commonFields.opportunities.find(f=>f.key==='sourceLeadNo')!.options=leadOptions.value.map(lead=>({label:`${lead.lead_no} · ${lead.customer_name}`,value:lead.lead_no}))
  if(props.kind==='quotations')commonFields.quotations.find(f=>f.key==='opportunityNo')!.options=opportunities.value.map(o=>({label:`${o.opportunityNo} · ${o.opportunityName} · ${o.customerName||o.customerCode||'未关联客户'}${o.contactName?` · 联系人：${o.contactName}`:''}`,value:o.opportunityNo}))
  if(props.kind==='contracts')commonFields.contracts.find(f=>f.key==='quotationId')!.options=effectiveQuotes.value.map(q=>({label:`${q.quotation_no} V${q.version_no} · ${q.customer_code} · ${q.total_amount}`,value:q.id}))
}
async function loadCustomerAndLeadOptions(){
  try{
    const requests:[Promise<any>,Promise<any>?]=[
      http.get('/crm/customers',{params:{page:1,size:200}}),
      props.kind==='opportunities'?http.get('/crm/leads',{params:{page:1,size:200}}):undefined
    ]
    const customerResponse=await requests[0]
    customerOptions.value=customerResponse.data.data?.content||[]
    if(requests[1]){
      const leadResponse=await requests[1]
      leadOptions.value=(leadResponse.data.data?.content||[]).filter((lead:any)=>['NEW','FOLLOWING'].includes(lead.status)&&!lead.converted_opportunity_no)
    }
    optionFields()
  }catch{return}
}
async function loadQuotationOptions(){
  const [opportunityResponse,productResponse]=await Promise.all([
    http.get('/crm/opportunities',{params:{page:1,size:200}}),
    http.get('/mdm/options/products')
  ])
  opportunities.value=opportunityResponse.data.data?.content||[]
  productOptions.value=(productResponse.data.data||[]).map((row:any)=>{
    const product=fromDb(row)
    return {...product,productCode:String(product.productCode||''),productName:String(product.productName||''),label:`${product.productCode} · ${product.productName}${product.productModel?` · ${product.productModel}`:''}`,value:String(product.productCode||'')}
  }).filter((product:any)=>product.value)
  if(!unitOptions.value.length)await loadSharedUnits()
  optionFields()
}
async function openCreate(){
  editingId.value=null;clearForm()
  if(props.kind==='leads'||props.kind==='opportunities')await loadCustomerAndLeadOptions()
  if(props.kind==='quotations'){try{await loadQuotationOptions()}catch{return}}
  if(props.kind==='contracts'){try{const{data}=await http.get('/crm/quotations',{params:{page:1,size:200,status:'EFFECTIVE'}});effectiveQuotes.value=data.data.content||[];optionFields();if(!effectiveQuotes.value.length){ElMessage.warning('当前没有已生效的报价，请先完成报价审批并生效');return}}catch{return}}
  if(props.kind==='quotations'){
    Object.assign(form,{currency:'CNY',taxRate:13,lines:[{lineNo:1,productCode:'',productName:'',qty:1,unitCode:defaultUnitCode(),standardPrice:0,salesPrice:0,standardCost:0}]})
  }
  if(props.kind==='contracts')Object.assign(form,{autoRenew:false,paymentPlans:[{periodNo:1,dueDate:'',planRatio:100,planAmount:0}]})
  if(props.kind==='opportunities')Object.assign(form,{probability:10,expectAmount:0,sourceLeadNo:null})
  dialog.value=true
}
async function openEdit(row:any){
  try{
    if(props.kind==='leads'||props.kind==='opportunities')await loadCustomerAndLeadOptions()
    if(props.kind==='quotations')await loadQuotationOptions()
    const{data}=await http.get(`${c.value.api}/${row.id}`)
    editingId.value=row.id;clearForm();Object.assign(form,fromDb(data.data))
    if(props.kind==='opportunities'&&form.sourceLeadNo&&!leadOptions.value.some(lead=>lead.lead_no===form.sourceLeadNo))leadOptions.value.push({lead_no:form.sourceLeadNo,customer_name:form.customerName,customerId:form.customerId})
    if(props.kind==='quotations'&&form.opportunityNo&&!opportunities.value.some(o=>String(o.opportunityNo)===String(form.opportunityNo)))opportunities.value.push({opportunityNo:form.opportunityNo,opportunityName:'历史关联商机',customerCode:form.customerCode,customerName:form.customerName,contactName:form.contactPerson})
    optionFields()
    if(props.kind==='quotations'){
      form.lines=(form.lines||[]).map((line:any)=>fromDb(line))
      form.lines=form.lines.map((line:any)=>({lineNo:line.lineNo,productCode:line.productCode,productName:line.productName,qty:line.qty,unitCode:line.unitCode,standardPrice:line.standardPrice,salesPrice:line.salesPrice,standardCost:line.standardCost}))
      for(const line of form.lines)if(line.productCode&&!productOptions.value.some((product:any)=>product.productCode===line.productCode))productOptions.value.push({productCode:line.productCode,productName:line.productName||'历史产品',productModel:'',unitCode:line.unitCode,label:`${line.productCode} · ${line.productName||'历史产品'}（请改选已发布产品）`,value:line.productCode,unavailable:true})
    }
    if(props.kind==='contracts'){form.paymentPlans=(form.paymentPlans||[]).map((plan:any)=>fromDb(plan));const quoteField=commonFields.contracts.find(f=>f.key==='quotationId')!;quoteField.options=[{label:`${form.quotationNo} · ${form.customerCode} · ${Number(form.amount||0).toLocaleString('zh-CN')}`,value:form.quotationId}]}
    dialog.value=true
  }catch{}
}
async function save(){const missing=fields.value.find(f=>f.required&&(form[f.key]==null||String(form[f.key]).trim()===''));if(missing){ElMessage.warning(`请填写${missing.label}`);return}if(props.kind==='contracts'&&(!form.paymentPlans?.length||!form.paymentPlans[0].dueDate)){ElMessage.warning('请填写至少一期回款计划及到期日');return}if(props.kind==='quotations'&&(!form.lines?.length||form.lines.some((x:any)=>!x.productCode||Number(x.qty)<=0))){ElMessage.warning('请选择报价产品并填写大于零的数量');return}saving.value=true;try{if(editingId.value)await http.put(`${c.value.api}/${editingId.value}`,form);else await http.post(c.value.api,form);ElMessage.success(editingId.value?'修改已保存':'单据已创建');dialog.value=false;page.value=1;await load()}catch{}finally{saving.value=false}}
async function detailRow(row:any){detail.value=null;try{const{data}=await http.get(`${c.value.api}/${row.id}`);detail.value=data.data;detailDialog.value=true}catch{}}
async function deleteRow(row:any){try{await ElMessageBox.confirm(`确认删除单据 ${fieldValue(row,'lead_no')||fieldValue(row,'opportunity_no')||fieldValue(row,'quotation_no')||fieldValue(row,'contract_no')||fieldValue(row,'complaint_no')}？仅未进入审批或下游流程的单据可删除。`,'删除确认',{type:'warning'});await http.delete(`${c.value.api}/${row.id}`);ElMessage.success('单据已删除');await load()}catch(e:any){if(e!=='cancel'&&e!=='close'){/* API 错误已由全局弹窗提示 */}}}
async function promptAction(row:any,action:string,label:string){
  try{
    const defaultName=`${fieldValue(row,'customer_name')||'客户'}商机`
    const{value}=await ElMessageBox.prompt(action==='convert'?'请输入商机名称，客户信息将从该线索自动带入':`请输入${label}所需信息`,label,{inputValue:action==='convert'?defaultName:''})
    let body:any={reason:value}
    if(action==='convert')body={opportunityName:value}
    await http.post(`${c.value.api}/${row.id}/${action}`,body);ElMessage.success(`${label}成功`);await load()
  }catch(e:any){if(e!=='cancel'&&e!=='close'){/* API 错误已由全局弹窗提示 */}}
}
function openStage(row:any){stageOpportunity.value=row;const current=String(fieldValue(row,'stage_code')||'LEAD');const nextStage:Record<string,string>={LEAD:'QUALIFY',QUALIFY:'PROPOSAL',PROPOSAL:'NEGOTIATE',NEGOTIATE:'WON'};Object.assign(stageForm,{code:nextStage[current]||current,reason:''});stageDialog.value=true}
function handleStageChange(value:string){if(value!=='LOST')stageForm.reason=''}
async function submitStage(){if(!stageForm.code){ElMessage.warning('请选择目标阶段');return}if(stageForm.code==='LOST'&&!stageForm.reason.trim()){ElMessage.warning('丢单时必须填写丢单原因');return}stageSaving.value=true;try{await http.post(`/crm/opportunities/${stageOpportunity.value.id}/stage`,{code:stageForm.code,reason:stageForm.reason.trim()});ElMessage.success(`商机阶段已更新为“${stageOptions.find(x=>x.value===stageForm.code)?.label||stageForm.code}”`);stageDialog.value=false;await load()}catch{}finally{stageSaving.value=false}}
async function loadLeadAssignees(){const{data}=await http.get('/crm/lead-assignees');leadAssignees.value=data.data||[]}
async function openAssign(row:any){actionLead.value=row;Object.assign(assignmentForm,{username:'',reason:''});try{await loadLeadAssignees();if(!leadAssignees.value.length){ElMessage.warning('当前没有可分配的 CRM 线索负责人，请检查用户的系统权限和线索查看权限');return}assignDialog.value=true}catch{}}
function openFollow(row:any){actionLead.value=row;Object.assign(followForm,{followType:'CALL',content:'',nextFollowAt:''});followDialog.value=true}
async function submitAssign(){if(!assignmentForm.username){ElMessage.warning('请选择新的线索负责人');return}actionSaving.value=true;try{await http.post(`/crm/leads/${actionLead.value.id}/assign`,assignmentForm);ElMessage.success('线索负责人已调整，分配记录已保存');assignDialog.value=false;await load()}catch{}finally{actionSaving.value=false}}
async function submitFollow(){if(!followForm.content.trim()){ElMessage.warning('请填写本次跟进内容');return}actionSaving.value=true;try{await http.post(`/crm/leads/${actionLead.value.id}/follow`,followForm);ElMessage.success('跟进记录已保存');followDialog.value=false;await load()}catch{}finally{actionSaving.value=false}}
async function direct(row:any,action:string,label:string){try{await http.post(`${c.value.api}/${row.id}/${action}`);ElMessage.success(action==='submit'?`${label}成功，可在统一审批页面跟踪进度`:`${label}成功`);await load()}catch{/* API 错误已由全局弹窗提示 */}}
async function openQuoteApproval(row:any){
  try{
    const[planResponse,userResponse]=await Promise.all([http.get(`/crm/quotations/${row.id}/approval-plan`),http.get('/workflow/users')])
    approvalRow.value=row;approvalPlan.value=planResponse.data.data;approvalUsers.value=(userResponse.data.data||[]).filter((user:any)=>user.canApprove&&user.username!==auth.user?.username)
    approvalApprovers.value=approvalPlan.value.steps.map(()=> '');approvalExtraCount.value=0;approvalDialog.value=true
  }catch{/* API 错误由全局弹框提示 */}
}
function approvalCandidates(step:any){const role=step?.approver_role;return approvalUsers.value.filter((user:any)=>!role||user.roles?.includes(role)||user.roles?.includes('ADMIN'))}
function addApprovalLevel(){if(approvalApprovers.value.length>=8)return;approvalApprovers.value.push('');approvalExtraCount.value++}
function removeApprovalLevel(){if(!approvalExtraCount.value)return;approvalApprovers.value.pop();approvalExtraCount.value--}
async function submitQuoteApproval(){
  if(approvalApprovers.value.some(value=>!value)){ElMessage.warning('请逐级选择审批人');return}
  if(new Set(approvalApprovers.value).size!==approvalApprovers.value.length){ElMessage.warning('各级审批人不能重复');return}
  approvalSaving.value=true
  try{const{data}=await http.post(`/crm/quotations/${approvalRow.value.id}/submit`,{approvers:approvalApprovers.value});approvalDialog.value=false;ElMessage.success('报价已提交审批');await load();progressInstanceId.value=data.data.id;progressVisible.value=true}catch{/* API 错误由全局弹框提示 */}finally{approvalSaving.value=false}
}
function openQuoteProgress(row:any){const id=fieldValue(row,'workflow_instance_id');if(id){progressInstanceId.value=Number(id);progressVisible.value=true}else ElMessage.warning('该报价尚未提交审批')}
async function removeLine(index:number){form.lines.splice(index,1)}
function addLine(){form.lines.push({lineNo:form.lines.length+1,productCode:'',productName:'',qty:1,unitCode:defaultUnitCode(),standardPrice:0,salesPrice:0,standardCost:0})}
function selectProduct(line:any,code:string){if(!code){line.productCode='';line.productName='';return}const product=productOptions.value.find((item:any)=>item.productCode===code);if(!product||product.unavailable)return;line.productCode=product.productCode;line.productName=product.productName;line.unitCode=product.unitCode||line.unitCode||defaultUnitCode()}
function addPlan(){const amount=Number(form.amount||0);form.paymentPlans.push({periodNo:form.paymentPlans.length+1,dueDate:'',planRatio:0,planAmount:0});if(form.paymentPlans.length===1)form.paymentPlans[0].planAmount=amount}
function canEdit(row:any){const s=String(fieldValue(row,'status')||'');if(props.kind==='opportunities')return !['WON','LOST'].includes(fieldValue(row,'stage_code'));if(props.kind==='leads')return ['NEW','FOLLOWING'].includes(s)&&!fieldValue(row,'converted_opportunity_no');if(props.kind==='quotations'||props.kind==='contracts')return ['DRAFT','REJECTED'].includes(s);if(props.kind==='complaints')return s==='OPEN';return false}
function canDelete(row:any){if(props.kind==='leads')return ['NEW','FOLLOWING'].includes(fieldValue(row,'status'))&&!fieldValue(row,'converted_opportunity_no');if(props.kind==='opportunities')return !['WON','LOST'].includes(fieldValue(row,'stage_code'));if(props.kind==='quotations'||props.kind==='contracts')return fieldValue(row,'status')==='DRAFT';if(props.kind==='complaints')return fieldValue(row,'status')==='OPEN';return false}
function canWorkLead(row:any){return props.kind==='leads'&&['NEW','FOLLOWING'].includes(String(fieldValue(row,'status')))&&!fieldValue(row,'converted_opportunity_no')}
function fieldValue(row:any,key:string){if(row?.[key]!==undefined)return row[key];const camel=key.replace(/_([a-z])/g,(_,c)=>c.toUpperCase());return row?.[camel]}
function statusTag(value:any){return ['APPROVED','EFFECTIVE','ACTIVE','WON','CONVERTED','CLOSED'].includes(String(value))?'success':['PENDING','FOLLOWING','QUALIFY','PROPOSAL','NEGOTIATE','OPEN'].includes(String(value))?'warning':['REJECTED','LOST','INVALID','VOID','TERMINATED','CANCELED'].includes(String(value))?'danger':'info'}
function statusLabel(value:any){if(props.kind==='contracts'&&value==='ACTIVE')return '已生效';return zh(value)}
function display(value:any,key:string){if(value==null||value==='')return '—';if(typeof value==='boolean'||key==='auto_renew')return value?'是':'否';if(['total_amount','amount','expect_amount','forecast_amount','weighted_amount','expected_budget'].includes(key)&&value!=null)return Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});if(key==='probability'||key.endsWith('_rate'))return `${value}%`;return zh(value)}
watch(()=>props.kind,()=>{page.value=1;status.value='';keyword.value='';load()})
watch(()=>form.sourceLeadNo,(leadNo)=>{if(!leadNo)return;const match=leadOptions.value.find(lead=>lead.lead_no===leadNo);if(match)form.customerId=match.customer_id??match.customerId})
watch(()=>form.opportunityNo,(no)=>{const match=opportunities.value.find(o=>o.opportunityNo===no);if(match)form.customerCode=match.customerCode||'';else if(props.kind==='quotations')form.customerCode=''})
watch(()=>form.quotationId,(id)=>{const q=effectiveQuotes.value.find(x=>Number(x.id)===Number(id));if(q&&form.paymentPlans?.length===1&&Number(form.paymentPlans[0].planAmount)===0)form.paymentPlans[0].planAmount=Number(q.total_amount)})
onMounted(async()=>{try{await loadSharedUnits()}catch{}await load()})
</script>
<template>
  <section class="page">
    <div class="head"><div><span>客户关系业务</span><h1>{{c.title}}</h1><p>线索和商机引用统一主数据中的客户档案；商机可由线索转换，也可直接创建。</p></div><el-button v-if="kind!=='forecast'" type="primary" @click="openCreate">新增{{c.title}}</el-button></div>
    <el-card shadow="never"><div class="bar"><el-input v-model="keyword" placeholder="输入编号、客户或名称" clearable @keyup.enter="page=1;load()"/><el-select v-if="statuses.length" v-model="status" clearable placeholder="全部状态" @change="page=1;load()"><el-option v-for="s in statuses" :key="s" :label="$zh(s)" :value="s"/></el-select><el-button @click="page=1;load()">查询</el-button><el-button @click="keyword='';status='';page=1;load()">重置</el-button></div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无数据">
        <el-table-column v-for="col in visibleColumns" :key="col.key" :prop="col.key" :label="col.label" :min-width="col.width||130" show-overflow-tooltip><template #default="{row}"><el-tag v-if="['status','stage_code','severity','priority'].includes(col.key)" :type="statusTag(fieldValue(row,col.key))">{{statusLabel(fieldValue(row,col.key))}}</el-tag><span v-else>{{display(fieldValue(row,col.key),col.key)}}</span></template></el-table-column>
        <el-table-column v-if="kind!=='forecast'" label="操作" width="390" fixed="right"><template #default="{row}"><el-button link @click="detailRow(row)">详情</el-button><el-button v-if="kind!=='forecast'&&canEdit(row)" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="kind!=='forecast'&&canDelete(row)" link type="danger" @click="deleteRow(row)">删除</el-button><template v-if="kind==='leads'"><el-button v-if="canWorkLead(row)" link @click="openAssign(row)">分配负责人</el-button><el-button v-if="canWorkLead(row)" link @click="openFollow(row)">登记跟进</el-button><el-button v-if="canWorkLead(row)" link type="primary" @click="promptAction(row,'convert','转商机')">转商机</el-button></template><template v-if="kind==='opportunities'"><el-button v-if="!['WON','LOST'].includes(fieldValue(row,'stage_code'))" link @click="openStage(row)">推进阶段</el-button></template><template v-if="kind==='quotations'"><el-button v-if="fieldValue(row,'workflow_instance_id')" link @click="openQuoteProgress(row)">审批进度</el-button><el-button v-if="['EFFECTIVE','EXPIRED'].includes(fieldValue(row,'status'))" link @click="direct(row,'new-version','新建报价版本')">升版</el-button><el-button v-if="['DRAFT','REJECTED'].includes(fieldValue(row,'status'))" link type="primary" @click="openQuoteApproval(row)">提交审批</el-button><el-button v-if="fieldValue(row,'status')==='APPROVED'" link type="success" @click="direct(row,'activate','报价生效')">生效</el-button></template><template v-if="kind==='contracts'"><el-button v-if="['DRAFT','REJECTED'].includes(fieldValue(row,'status'))" link type="primary" @click="direct(row,'submit','提交审批')">提交审批</el-button><el-button v-if="fieldValue(row,'status')==='APPROVED'" link type="success" @click="direct(row,'activate','合同生效')">生效</el-button><el-button v-if="fieldValue(row,'status')==='ACTIVE'&&!row.erp_sales_order_no" link type="primary" @click="direct(row,'create-order','生成订单')">生成订单</el-button><el-button v-if="['ACTIVE','APPROVED'].includes(fieldValue(row,'status'))" link @click="promptAction(row,'new-version','合同变更升版')">变更升版</el-button></template></template></el-table-column>
      </el-table><TablePager v-if="kind!=='opportunities'||total>size" v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>
    <el-dialog v-model="dialog" :title="`${editingId?'编辑':'新增'}${c.title}`" width="860px" top="5vh" destroy-on-close>
      <el-form label-position="top"><el-row :gutter="16"><el-col v-for="f in fields" :key="f.key" :span="f.type==='textarea'?24:12"><el-form-item :label="f.label" :required="f.required">
        <el-select v-if="f.type==='select'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)||(kind==='opportunities'&&f.key==='customerId'&&!!form.sourceLeadNo)" filterable clearable style="width:100%" :placeholder="f.placeholder||`请选择${f.label}`"><el-option v-for="o in f.options||[]" :key="String(o.value)" :label="o.label" :value="o.value"/></el-select>
        <el-input-number v-else-if="f.type==='number'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)" :min="f.min" :max="f.max" :precision="2" controls-position="right" style="width:100%"/>
        <el-date-picker v-else-if="f.type==='date'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)" type="date" value-format="YYYY-MM-DD" style="width:100%"/>
        <el-date-picker v-else-if="f.type==='datetime'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/>
        <el-switch v-else-if="f.type==='boolean'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)"/>
        <el-input v-else-if="f.type==='textarea'" v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)" type="textarea" :rows="3" :placeholder="f.placeholder"/>
        <el-input v-else v-model="form[f.key]" :disabled="!!(editingId&&f.readonly)" :readonly="kind==='quotations'&&f.key==='customerCode'" :placeholder="f.placeholder"/>
      </el-form-item></el-col></el-row>
        <div v-if="selectedCustomer&&(kind==='leads'||kind==='opportunities')" class="customer-summary"><span>已关联 MDM 客户档案</span><b>{{selectedCustomer.customerCode}} · {{selectedCustomer.customerName}}</b><small v-if="selectedCustomer.contactPerson">主联系人：{{selectedCustomer.contactPerson}}<template v-if="selectedCustomer.contactPhone"> · {{selectedCustomer.contactPhone}}</template></small></div>
        <div v-if="kind==='quotations'&&selectedQuoteCustomer" class="customer-summary"><span>客户档案信息（根据商机自动带入）</span><b>{{selectedQuoteCustomer.customerName}}（{{selectedQuoteCustomer.customerCode}}）</b><small v-if="selectedQuoteCustomer.contactName">联系人：{{selectedQuoteCustomer.contactName}}</small></div>
        <template v-if="kind==='quotations'"><div class="subhead"><b>报价明细</b><el-button size="small" @click="addLine">新增明细</el-button></div><el-table :data="form.lines" border><el-table-column label="选择已发布产品" min-width="280"><template #default="{row}"><el-select v-model="row.productCode" filterable clearable style="width:100%" placeholder="输入产品编码或名称搜索" @change="selectProduct(row,$event)"><el-option v-for="product in productOptions" :key="product.value" :label="product.label" :value="product.value" :disabled="product.unavailable"/></el-select></template></el-table-column><el-table-column prop="productName" label="产品名称" min-width="140"/><el-table-column label="数量" width="125"><template #default="{row}"><el-input-number v-model="row.qty" :min="0.0001" :precision="4"/></template></el-table-column><el-table-column label="单位" width="145"><template #default="{row}"><el-select v-model="row.unitCode" filterable><el-option v-for="unit in unitOptions" :key="unit.value" :label="unit.label" :value="unit.value"/></el-select></template></el-table-column><el-table-column label="标准价" width="125"><template #default="{row}"><el-input-number v-model="row.standardPrice" :min="0" :precision="4"/></template></el-table-column><el-table-column label="销售价" width="125"><template #default="{row}"><el-input-number v-model="row.salesPrice" :min="0" :precision="4"/></template></el-table-column><el-table-column label="标准成本" width="125"><template #default="{row}"><el-input-number v-model="row.standardCost" :min="0" :precision="4"/></template></el-table-column><el-table-column label="操作" width="65"><template #default="{$index}"><el-button link type="danger" @click="removeLine($index)">删除</el-button></template></el-table-column></el-table><p v-if="!productOptions.some(product=>!product.unavailable)" class="form-hint">当前没有已发布产品，请先在 MDM 产品主数据中完成产品发布。</p></template>
        <template v-if="kind==='contracts'"><div class="subhead"><b>回款计划</b><el-button size="small" @click="addPlan">新增期次</el-button></div><el-table :data="form.paymentPlans" border><el-table-column prop="periodNo" label="期次" width="80"/><el-table-column label="到期日" min-width="170"><template #default="{row}"><el-date-picker v-model="row.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></template></el-table-column><el-table-column label="比例（%）" width="150"><template #default="{row}"><el-input-number v-model="row.planRatio" :min="0" :max="100" :precision="2"/></template></el-table-column><el-table-column label="计划金额" width="180"><template #default="{row}"><el-input-number v-model="row.planAmount" :min="0" :precision="2"/></template></el-table-column><el-table-column label="操作" width="65"><template #default="{$index}"><el-button link type="danger" @click="form.paymentPlans.splice($index,1)">删除</el-button></template></el-table-column></el-table><p class="form-hint">所有期次金额之和必须等于来源报价总额。</p></template>
      </el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{editingId?'保存修改':'创建草稿'}}</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailDialog" :title="`${c.title}详情`" width="920px">
      <el-descriptions v-if="detail" :column="2" border><el-descriptions-item v-for="col in visibleColumns" :key="col.key" :label="col.label">{{display(fieldValue(detail,col.key),col.key)}}</el-descriptions-item><el-descriptions-item v-if="detail.problem_desc" label="问题描述" :span="2">{{detail.problem_desc}}</el-descriptions-item><el-descriptions-item v-if="detail.notes" label="报价说明" :span="2">{{detail.notes}}</el-descriptions-item></el-descriptions>
      <template v-if="kind==='leads'&&detail"><h4>跟进记录</h4><el-table :data="detail.follows||[]" border empty-text="暂无跟进记录"><el-table-column prop="follow_type" label="跟进方式" width="110"><template #default="{row}">{{zh(row.follow_type)}}</template></el-table-column><el-table-column prop="content" label="跟进内容" min-width="220"/><el-table-column prop="follow_user_name" label="跟进人" width="120"/><el-table-column prop="follow_at" label="跟进时间" min-width="165"/><el-table-column prop="next_follow_at" label="下次跟进时间" min-width="165"/></el-table><h4>负责人调整记录</h4><el-table :data="detail.assignments||[]" border empty-text="暂无负责人调整记录"><el-table-column prop="from_user_name" label="原负责人" min-width="130"><template #default="{row}">{{row.from_user_name||'未分配'}}</template></el-table-column><el-table-column prop="to_user_name" label="新负责人" min-width="130"/><el-table-column prop="reason" label="分配原因" min-width="180"/><el-table-column prop="assigned_by_name" label="分配人" width="120"/><el-table-column prop="assigned_at" label="分配时间" min-width="165"/></el-table></template>
      <template v-if="detail?.lines"><h4>报价明细</h4><el-table :data="detail.lines" border><el-table-column prop="product_code" label="产品编码"/><el-table-column prop="product_name" label="产品名称"/><el-table-column prop="qty" label="数量"/><el-table-column prop="sales_price" label="销售价"/></el-table></template>
      <template v-if="detail?.payment_plans"><h4>回款计划</h4><el-table :data="detail.payment_plans" border><el-table-column prop="period_no" label="期次"/><el-table-column prop="due_date" label="到期日"/><el-table-column prop="plan_ratio" label="比例（%）"/><el-table-column prop="plan_amount" label="计划金额"/><el-table-column prop="status" label="状态"><template #default="{row}">{{$zh(fieldValue(row,'status'))}}</template></el-table-column></el-table></template>
    </el-dialog>
    <el-dialog v-model="stageDialog" title="推进商机阶段" width="540px" destroy-on-close>
      <p class="action-intro">请选择商机“{{fieldValue(stageOpportunity,'opportunity_name')||fieldValue(stageOpportunity,'opportunityNo')}}”要推进到的阶段。阶段名称使用中文展示。</p>
      <el-alert title="赢单前必须有已生效报价；选择丢单时需要填写原因。" type="info" :closable="false" show-icon class="stage-hint"/>
      <el-form label-position="top"><el-form-item label="目标阶段" required><el-select v-model="stageForm.code" style="width:100%" placeholder="请选择目标阶段" @change="handleStageChange"><el-option v-for="stage in availableStages" :key="stage.value" :label="stage.label" :value="stage.value"/></el-select></el-form-item><el-form-item v-if="stageForm.code==='LOST'" label="丢单原因" required><el-input v-model="stageForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="请说明丢单原因"/></el-form-item></el-form>
      <template #footer><el-button @click="stageDialog=false">取消</el-button><el-button type="primary" :loading="stageSaving" @click="submitStage">保存阶段</el-button></template>
    </el-dialog>
    <el-dialog v-model="assignDialog" title="分配线索负责人" width="540px" destroy-on-close>
      <p class="action-intro">为“{{actionLead?.customer_name||actionLead?.lead_no}}”选择新的负责人。系统会保留原负责人、调整人和调整时间，并将线索状态更新为“跟进中”。</p>
      <el-form label-position="top"><el-form-item label="新负责人" required><el-select v-model="assignmentForm.username" filterable style="width:100%" placeholder="选择有 CRM 线索权限的用户"><el-option v-for="user in leadAssignees" :key="user.username" :value="user.username" :disabled="user.username===actionLead?.owner_user" :label="`${user.real_name}（${user.username}）${user.dept_code?` · ${user.dept_code}`:''}`"/></el-select></el-form-item><el-form-item label="分配原因"><el-input v-model="assignmentForm.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：区域调整、工作交接"/></el-form-item></el-form>
      <template #footer><el-button @click="assignDialog=false">取消</el-button><el-button type="primary" :loading="actionSaving" @click="submitAssign">确认分配</el-button></template>
    </el-dialog>
    <el-dialog v-model="followDialog" title="登记线索跟进" width="620px" destroy-on-close>
      <p class="action-intro">记录与“{{actionLead?.customer_name||actionLead?.lead_no}}”的实际沟通；保存后可在详情的“跟进记录”中查看。</p>
      <el-form label-position="top"><el-form-item label="跟进方式" required><el-select v-model="followForm.followType" style="width:100%"><el-option label="电话" value="CALL"/><el-option label="拜访" value="VISIT"/><el-option label="邮件" value="EMAIL"/><el-option label="会议" value="MEETING"/></el-select></el-form-item><el-form-item label="跟进内容" required><el-input v-model="followForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="记录沟通结果、客户反馈和待办事项"/></el-form-item><el-form-item label="下次跟进时间"><el-date-picker v-model="followForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="可选" style="width:100%"/></el-form-item></el-form>
      <template #footer><el-button @click="followDialog=false">取消</el-button><el-button type="primary" :loading="actionSaving" @click="submitFollow">保存跟进记录</el-button></template>
    </el-dialog>
    <el-dialog v-model="approvalDialog" title="提交报价审批" width="640px" destroy-on-close>
      <template v-if="approvalPlan">
        <el-alert :title="approvalPlan.risk?'该报价满足风险条件，需完成销售与财务两级审批':'该报价需要销售主管审批'" type="info" :closable="false" show-icon />
        <el-form label-position="top">
          <el-form-item v-for="(step,index) in approvalPlan.steps" :key="step.node_seq" :label="`${index+1} 级 · ${step.node_name}`" required>
            <el-select v-model="approvalApprovers[index]" filterable clearable style="width:100%" placeholder="搜索姓名或账号，选择审批人">
              <el-option v-for="user in approvalCandidates(step)" :key="user.username" :label="`${user.realName}（${user.username}） · ${user.positionName||user.deptCode||step.node_name}`" :value="user.username" />
            </el-select>
            <el-text v-if="!approvalCandidates(step).length" type="danger">当前没有符合该节点角色且有审批权限的用户，请先配置人员权限。</el-text>
          </el-form-item>
          <el-form-item v-for="level in approvalExtraCount" :key="`extra-${level}`" :label="`附加审批第 ${level} 级`" required>
            <el-select v-model="approvalApprovers[approvalPlan.steps.length+level-1]" filterable clearable style="width:100%" placeholder="选择附加审批人"><el-option v-for="user in approvalUsers" :key="user.username" :label="`${user.realName}（${user.username}） · ${user.positionName||user.deptCode||'审批人'}`" :value="user.username" /></el-select>
          </el-form-item>
        </el-form>
        <el-space wrap><el-button @click="addApprovalLevel" :disabled="approvalApprovers.length>=8">增加一级审批</el-button><el-button v-if="approvalExtraCount" @click="removeApprovalLevel">移除最后一级</el-button></el-space>
      </template>
      <template #footer><el-button @click="approvalDialog=false">取消</el-button><el-button type="primary" :loading="approvalSaving" :disabled="!approvalPlan||approvalPlan.steps.some((step:any)=>!approvalCandidates(step).length)" @click="submitQuoteApproval">提交审批</el-button></template>
    </el-dialog>
    <WorkflowProgressDrawer v-model="progressVisible" :instance-id="progressInstanceId" @updated="load" />
  </section>
</template>
<style scoped>.page{max-width:1600px;margin:auto}.head{display:flex;align-items:end;justify-content:space-between;margin-bottom:16px}.head span{font-size:11px;color:#3976d5}.head h1{margin:5px 0;color:#263f5c}.head p{font-size:12px;color:#8391a4}.bar{display:flex;gap:8px;margin-bottom:14px}.bar .el-input{width:280px}.bar .el-select{width:180px}.subhead{display:flex;align-items:center;justify-content:space-between;margin:16px 0 8px}.customer-summary{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:2px 0 18px;padding:13px 15px;border:1px solid #dce9f7;border-radius:10px;background:linear-gradient(100deg,#f5f9ff,#f8f7ff);color:#71839b;font-size:12px}.customer-summary b{color:#31577f;font-weight:650}.customer-summary small{color:#8998aa}.form-hint{font-size:12px;color:#7b8ca3}.action-intro{margin:0 0 16px;padding:12px 14px;border-radius:10px;background:#f5f8fd;color:#647790;font-size:13px;line-height:1.6}.stage-hint{margin:0 0 16px}</style>
