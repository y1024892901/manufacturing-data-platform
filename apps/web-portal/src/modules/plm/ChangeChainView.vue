<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh, zhKey } from '../../shared/display'
import { showErrorDialog } from '../../shared/errorDialog'

type ChainTab = 'ecrs' | 'ecos' | 'ecns'
const tab = ref<ChainTab>('ecrs')
const rows = ref<Record<string, any>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const detailDialog = ref(false)
const detailRow = ref<Record<string, any> | null>(null)
const editingId = ref<number | null>(null)
const impactDialog = ref(false)
const selectedEcr = ref<Record<string, any> | null>(null)
const impacts = ref<Record<string, any>[]>([])
const form = reactive<Record<string, any>>({})
const impactForm = reactive<Record<string, any>>({})

const title = computed(() => ({ ecrs: '变更申请（ECR）', ecos: '变更命令（ECO）', ecns: '变更通知（ECN）' }[tab.value]))
const serial = (prefix: string) => `${prefix}-${Date.now().toString().slice(-8)}`
function displayValue(key: string, value: unknown) {
  if (key === 'changeType') return value === 'PROCESS' ? '工艺变更' : value === 'DESIGN' ? '设计变更' : zh(value)
  return zh(value)
}
function displayKey(key: string) {
  return tab.value === 'ecns' && key === 'bomCode' ? '目标编码' : zhKey(key)
}

async function load() {
  loading.value = true
  try {
    const endpoint = tab.value === 'ecns' ? '/plm/ecns' : `/plm/${tab.value}`
    const { data } = await http.get(endpoint, { params: { keyword: keyword.value || undefined, page: page.value, size: size.value } })
    rows.value = data.data.content
    total.value = data.data.totalElements
    if (!rows.value.length && total.value > 0 && page.value > 1) {
      page.value -= 1
      await load()
    }
  } catch (error: any) {
    showErrorDialog(error?.message, '加载工程变更单失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; void load() }

function clearForm() { Object.keys(form).forEach(key => delete form[key]) }

function createEcr() {
  editingId.value = null
  clearForm()
  Object.assign(form, { ecrNo: serial('ECR'), ecrTitle: '', productCode: '', problemDesc: '', changeReason: '', urgency: 'NORMAL', proposal: '', plannedEffectiveDate: '' })
  dialog.value = true
}

function createEcn() {
  editingId.value = null
  clearForm()
  Object.assign(form, { ecnNo: serial('ECN'), ecnTitle: '', productCode: '', bomCode: '', changeType: 'DESIGN', targetType: 'BOM', targetVersion: '', changeContent: '', changeReason: '', effectiveDate: '' })
  dialog.value = true
}

function editEcr(row: Record<string, any>) {
  editingId.value = Number(row.id)
  clearForm()
  Object.assign(form, {
    ecrNo: row.ecr_no, ecrTitle: row.ecr_title, productCode: row.product_code, problemDesc: row.problem_desc,
    changeReason: row.change_reason, urgency: row.urgency || 'NORMAL', proposal: row.proposal || '', plannedEffectiveDate: row.planned_effective_date || ''
  })
  dialog.value = true
}

function editEco(row: Record<string, any>) {
  editingId.value = Number(row.id)
  clearForm()
  Object.assign(form, { ecoNo: row.eco_no, ecoTitle: row.eco_title, changeScope: row.change_scope || '', targetType: row.target_type, targetCode: row.target_code || '', newVersion: row.new_version || '' })
  dialog.value = true
}

function editEcn(row: Record<string, any>) {
  editingId.value = Number(row.id)
  clearForm()
  Object.assign(form, {
    ecnNo: row.ecnNo, ecnTitle: row.ecnTitle, productCode: row.productCode, bomCode: row.bomCode || '',
    changeType: row.changeType || 'DESIGN', targetType: row.targetType || 'BOM', targetVersion: row.targetVersion || '',
    changeContent: row.changeContent || '', changeReason: row.changeReason || '', effectiveDate: row.effectiveDate || ''
  })
  dialog.value = true
}

function createEco(row: Record<string, any>) {
  tab.value = 'ecos'
  editingId.value = null
  clearForm()
  Object.assign(form, {
    ecrId: row.id, ecoNo: serial('ECO'), ecoTitle: row.ecr_title, changeScope: row.proposal || '',
    targetType: 'BOM', targetCode: '', newVersion: ''
  })
  dialog.value = true
}

function validateForm() {
  const commonRequired = tab.value === 'ecrs'
    ? [['ecrNo', '变更申请编号'], ['ecrTitle', '标题'], ['productCode', '产品编码'], ['problemDesc', '问题描述'], ['changeReason', '变更原因']]
    : tab.value === 'ecos'
      ? [['ecoNo', '变更命令编号'], ['ecoTitle', '标题'], ['targetType', '目标类型'], ['newVersion', '目标版本']]
      : [['ecnNo', '变更通知编号'], ['ecnTitle', '标题'], ['productCode', '产品编码'], ['targetType', '目标类型'], ['targetVersion', '目标版本']]
  const missing = commonRequired.find(([key]) => !String(form[key] ?? '').trim())
  if (missing) {
    showErrorDialog(`请填写${missing[1]}`, '表单校验失败')
    return false
  }
  return true
}

async function save() {
  if (!validateForm()) return
  saving.value = true
  try {
    if (tab.value === 'ecrs') {
      const payload = { ...form }
      if (editingId.value) await http.put(`/plm/ecrs/${editingId.value}`, payload)
      else await http.post('/plm/ecrs', payload)
    } else if (tab.value === 'ecos') {
      const payload = { ecoNo: form.ecoNo, ecoTitle: form.ecoTitle, changeScope: form.changeScope, targetType: form.targetType, targetCode: form.targetCode, newVersion: form.newVersion }
      if (editingId.value) await http.put(`/plm/ecos/${editingId.value}`, payload)
      else await http.post(`/plm/ecrs/${form.ecrId}/create-eco`, payload)
    } else {
      const payload = { ...form, bomCode: form.bomCode || null, changeContent: form.changeContent || null, changeReason: form.changeReason || null, effectiveDate: form.effectiveDate || null }
      if (editingId.value) await http.put(`/plm/ecns/${editingId.value}`, payload)
      else await http.post('/plm/ecns', payload)
    }
    dialog.value = false
    ElMessage.success(editingId.value ? '修改成功' : '创建成功')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '保存失败')
  } finally {
    saving.value = false
  }
}

async function view(row: Record<string, any>) {
  try {
    const endpoint = tab.value === 'ecns' ? `/plm/ecns/${row.id}` : `/plm/${tab.value}/${row.id}`
    const { data } = await http.get(endpoint)
    detailRow.value = data.data
    if (tab.value === 'ecrs') {
      const result = await http.get(`/plm/ecrs/${row.id}/impacts`)
      impacts.value = result.data.data || []
    } else impacts.value = []
    detailDialog.value = true
  } catch (error: any) {
    showErrorDialog(error?.message, '读取单据详情失败')
  }
}

function openImpact(row: Record<string, any>) {
  selectedEcr.value = row
  Object.assign(impactForm, { impactType: 'BOM', objectCode: '', impactDesc: '', riskLevel: 'MEDIUM', costImpact: '', scheduleImpactDays: '', ownerUser: '' })
  impactDialog.value = true
}

async function saveImpact() {
  if (!selectedEcr.value || !String(impactForm.objectCode || '').trim()) return showErrorDialog('请填写受影响对象编码', '表单校验失败')
  try {
    const payload = [{ ...impactForm, costImpact: impactForm.costImpact === '' ? null : Number(impactForm.costImpact), scheduleImpactDays: impactForm.scheduleImpactDays === '' ? null : Number(impactForm.scheduleImpactDays) }]
    await http.put(`/plm/ecrs/${selectedEcr.value.id}/impact-analysis`, payload)
    impactDialog.value = false
    ElMessage.success('影响分析已保存')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '影响分析失败')
  }
}

async function remove(row: Record<string, any>) {
  const number = tab.value === 'ecrs' ? row.ecr_no : tab.value === 'ecos' ? row.eco_no : row.ecnNo
  try {
    await ElMessageBox.confirm(`确认删除单据“${number}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  try {
    await http.delete(`/plm/${tab.value}/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '删除失败')
  }
}

async function submitEco(row: Record<string, any>) {
  try {
    await http.post(`/plm/ecos/${row.id}/submit`)
    ElMessage.success('已提交审批')
    await load()
  } catch (error: any) { showErrorDialog(error?.message, '提交审批失败') }
}

async function ecnAction(row: Record<string, any>) {
  try {
    if (row.status === 'DRAFT') {
      await http.post(`/plm/ecns/${row.id}/submit`)
      ElMessage.success('已提交审批')
    } else if (row.status === 'APPROVED') {
      await http.post(`/plm/ecns/${row.id}/implement`)
      ElMessage.success('已生成MDM草稿版本')
    }
    await load()
  } catch (error: any) { showErrorDialog(error?.message, '执行变更动作失败') }
}

watch(tab, () => { page.value = 1; keyword.value = ''; void load() })
onMounted(() => { void load() })
</script>

<template>
  <section class="page">
    <div class="head">
      <div><span>工程变更管理</span><h1>工程变更闭环</h1><p>维护申请、审批变更命令、发布变更通知并生成 MDM 受控草稿。</p></div>
      <el-button v-if="tab === 'ecrs'" type="primary" @click="createEcr">新增变更申请</el-button>
      <el-button v-else-if="tab === 'ecns'" type="primary" @click="createEcn">新增变更通知</el-button>
    </div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="变更申请（ECR）" name="ecrs"/><el-tab-pane label="变更命令（ECO）" name="ecos"/><el-tab-pane label="变更通知（ECN）" name="ecns"/>
      </el-tabs>
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="按单号或标题查询" @keyup.enter="search"/><el-button @click="search">查询</el-button></div>
      <el-table :data="rows" v-loading="loading" stripe>
        <template v-if="tab === 'ecrs'">
          <el-table-column prop="ecr_no" label="变更申请编号" min-width="150"/><el-table-column prop="ecr_title" label="标题" min-width="170" show-overflow-tooltip/>
          <el-table-column prop="product_code" label="产品编码" min-width="120"/><el-table-column prop="urgency" label="紧急程度" width="100"><template #default="{ row }">{{ zh(row.urgency) }}</template></el-table-column>
          <el-table-column prop="planned_effective_date" label="计划生效日期" min-width="140"><template #default="{ row }">{{ zh(row.planned_effective_date) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag>{{ zh(row.status) }}</el-tag></template></el-table-column>
        </template>
        <template v-else-if="tab === 'ecos'">
          <el-table-column prop="eco_no" label="变更命令编号" min-width="150"/><el-table-column prop="eco_title" label="标题" min-width="170" show-overflow-tooltip/>
          <el-table-column prop="product_code" label="产品编码" min-width="120"/><el-table-column prop="target_type" label="目标类型" width="110"><template #default="{ row }">{{ zh(row.target_type) }}</template></el-table-column>
          <el-table-column prop="target_code" label="目标编码" min-width="120"/><el-table-column prop="new_version" label="目标版本" width="110"/>
          <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag>{{ zh(row.status) }}</el-tag></template></el-table-column>
        </template>
        <template v-else>
          <el-table-column prop="ecnNo" label="变更通知编号" min-width="150"/><el-table-column prop="ecnTitle" label="标题" min-width="170" show-overflow-tooltip/>
          <el-table-column prop="productCode" label="产品编码" min-width="120"/><el-table-column prop="targetType" label="目标类型" width="110"><template #default="{ row }">{{ zh(row.targetType) }}</template></el-table-column>
          <el-table-column prop="bomCode" label="目标编码" min-width="120"/><el-table-column prop="targetVersion" label="目标版本" width="110"/>
          <el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag>{{ zh(row.status) }}</el-tag></template></el-table-column>
        </template>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row)">详情</el-button>
            <template v-if="tab === 'ecrs'">
              <el-button link :disabled="!['DRAFT', 'ANALYZED'].includes(row.status)" @click="openImpact(row)">影响分析</el-button>
              <el-button link type="primary" :disabled="row.status !== 'ANALYZED'" @click="createEco(row)">生成变更命令</el-button>
              <el-button link type="primary" :disabled="row.status !== 'DRAFT'" @click="editEcr(row)">编辑</el-button>
              <el-button link type="danger" :disabled="!['DRAFT', 'ANALYZED'].includes(row.status)" @click="remove(row)">删除</el-button>
            </template>
            <template v-else-if="tab === 'ecos'">
              <el-button link type="primary" :disabled="!['DRAFT', 'REJECTED'].includes(row.status)" @click="editEco(row)">编辑</el-button>
              <el-button link type="primary" :disabled="!['DRAFT', 'REJECTED'].includes(row.status)" @click="submitEco(row)">提交审批</el-button>
              <el-button link type="danger" :disabled="!['DRAFT', 'REJECTED'].includes(row.status)" @click="remove(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" :disabled="row.status !== 'DRAFT'" @click="editEcn(row)">编辑</el-button>
              <el-button link type="primary" :disabled="!['DRAFT', 'APPROVED'].includes(row.status)" @click="ecnAction(row)">{{ row.status === 'DRAFT' ? '提交审批' : '实施' }}</el-button>
              <el-button link type="danger" :disabled="row.status !== 'DRAFT'" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId ? '编辑' : '新增'}${title}`" width="820px" destroy-on-close>
      <el-form label-position="top"><el-row :gutter="16">
        <template v-if="tab === 'ecrs'">
          <el-col :span="12"><el-form-item label="变更申请编号"><el-input v-model="form.ecrNo" :disabled="Boolean(editingId)"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标题"><el-input v-model="form.ecrTitle"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品编码"><el-input v-model="form.productCode"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="紧急程度（选填）"><el-select v-model="form.urgency" style="width:100%"><el-option v-for="o in ['LOW', 'NORMAL', 'HIGH', 'URGENT']" :key="o" :label="zh(o)" :value="o"/></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="问题描述"><el-input v-model="form.problemDesc" type="textarea" :rows="3"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="变更原因"><el-input v-model="form.changeReason" type="textarea" :rows="2"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="建议方案（选填）"><el-input v-model="form.proposal" type="textarea" :rows="2"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划生效日期（选填）"><el-date-picker v-model="form.plannedEffectiveDate" value-format="YYYY-MM-DD" clearable style="width:100%"/></el-form-item></el-col>
        </template>
        <template v-else-if="tab === 'ecos'">
          <el-col :span="12"><el-form-item label="变更命令编号"><el-input v-model="form.ecoNo" :disabled="Boolean(editingId)"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标题"><el-input v-model="form.ecoTitle"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标类型"><el-select v-model="form.targetType" style="width:100%"><el-option label="物料清单" value="BOM"/><el-option label="工艺路线" value="ROUTING"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标编码（选填）"><el-input v-model="form.targetCode"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="新版本号"><el-input v-model="form.newVersion" placeholder="例如 V1.1"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="变更范围（选填）"><el-input v-model="form.changeScope" type="textarea" :rows="3"/></el-form-item></el-col>
        </template>
        <template v-else>
          <el-col :span="12"><el-form-item label="变更通知编号"><el-input v-model="form.ecnNo" :disabled="Boolean(editingId)"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标题"><el-input v-model="form.ecnTitle"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品编码"><el-input v-model="form.productCode"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标类型"><el-select v-model="form.targetType" style="width:100%"><el-option label="物料清单" value="BOM"/><el-option label="工艺路线" value="ROUTING"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标编码（选填）"><el-input v-model="form.bomCode"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标版本"><el-input v-model="form.targetVersion" placeholder="例如 V1.1"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="变更类型（选填）"><el-select v-model="form.changeType" style="width:100%"><el-option label="设计变更" value="DESIGN"/><el-option label="工艺变更" value="PROCESS"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="生效日期（选填）"><el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" clearable style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="变更内容（选填）"><el-input v-model="form.changeContent" type="textarea" :rows="2"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="变更原因（选填）"><el-input v-model="form.changeReason" type="textarea" :rows="2"/></el-form-item></el-col>
        </template>
      </el-row></el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="impactDialog" title="变更影响分析" width="700px">
      <el-form label-position="top"><el-row :gutter="16">
        <el-col :span="12"><el-form-item label="影响类型"><el-select v-model="impactForm.impactType" style="width:100%"><el-option label="物料清单" value="BOM"/><el-option label="工艺路线" value="ROUTING"/><el-option label="文档" value="DOCUMENT"/><el-option label="产品" value="PRODUCT"/></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="受影响对象编码"><el-input v-model="impactForm.objectCode"/></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="影响说明（选填）"><el-input v-model="impactForm.impactDesc" type="textarea"/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="风险等级（选填）"><el-select v-model="impactForm.riskLevel" style="width:100%"><el-option v-for="o in ['LOW', 'MEDIUM', 'HIGH']" :key="o" :label="zh(o)" :value="o"/></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="成本影响估算（选填）"><el-input-number v-model="impactForm.costImpact" :min="0" :precision="2"/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="进度影响天数（选填）"><el-input-number v-model="impactForm.scheduleImpactDays" :min="0" :precision="0"/></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="责任人账号（选填）"><el-input v-model="impactForm.ownerUser"/></el-form-item></el-col>
      </el-row></el-form>
      <template #footer><el-button @click="impactDialog = false">取消</el-button><el-button type="primary" @click="saveImpact">保存分析</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDialog" :title="`${title}详情`" size="720px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item v-for="(value, key) in detailRow" :key="key" :label="displayKey(String(key))">{{ displayValue(String(key), value) }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="tab === 'ecrs' && impacts.length"><h3>影响分析</h3><el-table :data="impacts" border><el-table-column prop="impact_type" label="影响类型"><template #default="{ row }">{{ zh(row.impact_type) }}</template></el-table-column><el-table-column prop="object_code" label="受影响对象编码"/><el-table-column prop="impact_desc" label="影响说明"/><el-table-column prop="risk_level" label="风险等级"><template #default="{ row }">{{ zh(row.risk_level) }}</template></el-table-column><el-table-column prop="cost_impact" label="成本影响估算"/><el-table-column prop="schedule_impact_days" label="进度影响天数"/></el-table></template>
    </el-drawer>
  </section>
</template>

<style scoped>
.page{max-width:1480px;margin:auto}.head{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:17px}.head h1{margin:6px 0}.head span{color:#3775d6;font-size:10px}.head p{color:#8291a4;font-size:12px}.toolbar{display:flex;gap:8px;margin-bottom:14px}.toolbar .el-input{width:280px}
</style>
