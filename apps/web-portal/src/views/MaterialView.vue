<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import TablePager from '../shared/components/TablePager.vue'
import { showErrorDialog } from '../shared/errorDialog'
import { zh } from '../shared/display'
import { loadMdmCategories, loadMdmUnits, type MdmOption, type MdmUnitOption } from '../shared/mdmOptions'

type MaterialRow = Record<string, any>
const auth = useAuthStore()
const can = (permission: string) => Boolean(auth.isAdmin || auth.user?.permissions?.includes(permission))
const rows = ref<MaterialRow[]>([])
const categories = ref<MdmOption[]>([])
const units = ref<MdmUnitOption[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const dialog = ref(false)
const detailDialog = ref(false)
const editingId = ref<number | null>(null)
const selected = ref<MaterialRow | null>(null)
const formError = ref('')
const form = reactive<MaterialRow>({})

const statuses = ['DRAFT', 'PENDING', 'PUBLISHED', 'CHANGING', 'REJECTED', 'DISABLED']
const materialTypes = [
  { label: '原材料', value: 'RAW' }, { label: '半成品', value: 'SEMI' },
  { label: '成品', value: 'FINISHED' }, { label: '备品备件', value: 'SPARE' },
  { label: '包装材料', value: 'PACKAGING' }, { label: '采购件（旧编码）', value: 'PURCHASE' },
  { label: '成品（旧编码）', value: 'FG' },
]
const editable = (row: MaterialRow) => ['DRAFT', 'REJECTED'].includes(String(row.status))
const categoryLabels = computed(() => new Map(categories.value.map(option => [Number(option.value), option.label])) )
const unitLabels = computed(() => new Map(units.value.map(option => [String(option.value), option.label])) )
const details = computed(() => selected.value ? [
  ['物料编码', selected.value.materialCode], ['物料名称', selected.value.materialName],
  ['规格型号', selected.value.materialSpec], ['规格说明', selected.value.specDesc],
  ['物料类型', displayType(selected.value.materialType)], ['物料分类', categoryLabels.value.get(Number(selected.value.categoryId)) || selected.value.categoryId],
  ['基本单位', unitLabels.value.get(String(selected.value.baseUnitCode)) || selected.value.baseUnitCode],
  ['采购单位', unitLabels.value.get(String(selected.value.purchaseUnitCode)) || selected.value.purchaseUnitCode],
  ['采购换算率', selected.value.conversionRate], ['安全库存', selected.value.safetyStock],
  ['保质期（天）', selected.value.shelfLifeDays], ['批次管理', selected.value.batchManaged ? '是' : '否'],
  ['标准成本', selected.value.standardPrice], ['是否需要检验', selected.value.inspectionRequired ? '是' : '否'],
  ['检验标准', selected.value.inspectionStandard], ['状态', statusLabel(selected.value.status)],
  ['版本', selected.value.versionNo], ['变更原因', selected.value.changeReason],
  ['创建人', selected.value.createdBy], ['更新时间', selected.value.updatedAt],
] : [])

function statusLabel(value: unknown) {
  const labels: Record<string, string> = { DRAFT: '草稿', PENDING: '审批中', PUBLISHED: '已发布', CHANGING: '变更中', REJECTED: '已驳回', DISABLED: '已停用' }
  return labels[String(value || '')] || zh(value)
}
function displayType(value: unknown) { return materialTypes.find(item => item.value === value)?.label || zh(value) }
function errorMessage(error: any, fallback: string) { return error?.response?.data?.message || error?.message || fallback }

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/mdm/materials', {
      params: { page: page.value, size: size.value, status: status.value || undefined, keyword: keyword.value.trim() || undefined },
    })
    rows.value = data.data?.content || []
    total.value = data.data?.totalElements || 0
  } catch (error) {
    showErrorDialog(errorMessage(error, '物料列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [unitOptions, categoryOptions] = await Promise.all([loadMdmUnits(), loadMdmCategories()])
    units.value = unitOptions
    categories.value = categoryOptions
  } catch (error) {
    showErrorDialog(errorMessage(error, '物料分类或单位加载失败'))
  }
}

function clearForm() { Object.keys(form).forEach(key => delete form[key]) }
function openCreate() {
  editingId.value = null
  formError.value = ''
  clearForm()
  Object.assign(form, {
    materialCode: '', materialName: '', materialSpec: '', specDesc: '', materialType: 'RAW', categoryId: null,
    baseUnitCode: units.value.find(unit => unit.value === 'PCS')?.value || '', purchaseUnitCode: null,
    conversionRate: 1, safetyStock: 0, shelfLifeDays: null, batchManaged: false, standardPrice: 0,
    inspectionRequired: true, inspectionStandard: '',
  })
  dialog.value = true
}

async function openEdit(row: MaterialRow) {
  formError.value = ''
  try {
    const { data } = await http.get(`/mdm/materials/${row.id}`)
    const material = data.data || row
    clearForm()
    Object.assign(form, {
      materialCode: material.materialCode, materialName: material.materialName, materialSpec: material.materialSpec,
      specDesc: material.specDesc, materialType: material.materialType, categoryId: material.categoryId,
      baseUnitCode: material.baseUnitCode, purchaseUnitCode: material.purchaseUnitCode,
      conversionRate: Number(material.conversionRate ?? 1), safetyStock: Number(material.safetyStock ?? 0),
      shelfLifeDays: material.shelfLifeDays, batchManaged: Boolean(material.batchManaged),
      standardPrice: Number(material.standardPrice ?? 0), inspectionRequired: Boolean(material.inspectionRequired),
      inspectionStandard: material.inspectionStandard,
    })
    editingId.value = Number(row.id)
    dialog.value = true
  } catch (error) {
    showErrorDialog(errorMessage(error, '物料详情加载失败'))
  }
}

async function openDetail(row: MaterialRow) {
  try {
    const { data } = await http.get(`/mdm/materials/${row.id}`)
    selected.value = data.data || row
    detailDialog.value = true
  } catch (error) {
    showErrorDialog(errorMessage(error, '物料详情加载失败'))
  }
}

function validate() {
  if (!String(form.materialCode || '').trim()) return '请填写物料编码'
  if (!String(form.materialName || '').trim()) return '请填写物料名称'
  if (!form.materialType) return '请选择物料类型'
  if (!form.baseUnitCode) return '请选择基本单位'
  if (form.conversionRate == null || Number(form.conversionRate) <= 0) return '采购单位换算率必须大于0'
  return ''
}

async function save() {
  formError.value = validate()
  if (formError.value) return
  saving.value = true
  const payload = {
    ...form,
    materialCode: String(form.materialCode).trim(),
    materialName: String(form.materialName).trim(),
    categoryId: form.categoryId ? Number(form.categoryId) : null,
    purchaseUnitCode: form.purchaseUnitCode || null,
    conversionRate: Number(form.conversionRate),
    safetyStock: Number(form.safetyStock || 0),
    shelfLifeDays: form.shelfLifeDays === '' || form.shelfLifeDays == null ? null : Number(form.shelfLifeDays),
    standardPrice: Number(form.standardPrice || 0),
    inspectionStandard: String(form.inspectionStandard || '').trim() || null,
  }
  try {
    if (editingId.value === null) await http.post('/mdm/materials', payload)
    else await http.put(`/mdm/materials/${editingId.value}`, payload)
    ElMessage.success(editingId.value === null ? '物料草稿已创建' : '物料修改已保存')
    dialog.value = false
    page.value = 1
    await load()
  } catch (error) {
    formError.value = errorMessage(error, '保存失败，请检查物料资料')
    showErrorDialog(formError.value)
  } finally {
    saving.value = false
  }
}

async function submit(row: MaterialRow) {
  try {
    await http.post(`/mdm/materials/${row.id}/submit`)
    ElMessage.success('物料已提交审批')
    await load()
  } catch (error) { showErrorDialog(errorMessage(error, '提交审批失败')) }
}

async function disable(row: MaterialRow) {
  try {
    await ElMessageBox.confirm(`停用物料“${row.materialCode} · ${row.materialName}”？停用后业务单据不能再选用。`, '停用确认', { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' })
    await http.post(`/mdm/materials/${row.id}/disable`)
    ElMessage.success('物料已停用')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    showErrorDialog(errorMessage(error, '停用物料失败'))
  }
}

function filter() { page.value = 1; void load() }
function unitChanged() {
  if (!form.purchaseUnitCode) form.conversionRate = 1
}
function onPurchaseUnitChanged(value: string | null) {
  if (!value || value === form.baseUnitCode) form.conversionRate = 1
}

onMounted(() => { void loadOptions(); void load() })
</script>

<template>
  <section class="material-page">
    <header class="page-head">
      <div>
        <span>统一主数据 · 多系统共享</span>
        <h1>物料维护</h1>
        <p>维护统一物料编码、规格、分类、计量换算、计划库存及质量属性；已发布物料经审批后供业务系统共同选用。</p>
      </div>
      <el-button v-if="can('MDM:MATERIAL:CREATE')" type="primary" @click="openCreate">新增物料</el-button>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条物料记录</div>
        <el-input v-model="keyword" clearable placeholder="搜索物料编码或名称" @keyup.enter="filter" />
        <el-select v-model="status" clearable placeholder="全部状态" @change="filter">
          <el-option v-for="item in statuses" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
        <el-button type="primary" @click="filter">查询</el-button>
        <el-button :loading="loading" @click="loadOptions(); load()">刷新</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无物料数据">
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" />
        <el-table-column prop="materialSpec" label="规格型号" min-width="145" show-overflow-tooltip />
        <el-table-column prop="materialType" label="物料类型" width="125"><template #default="{row}">{{ displayType(row.materialType) }}</template></el-table-column>
        <el-table-column prop="categoryId" label="物料分类" min-width="160"><template #default="{row}">{{ categoryLabels.get(Number(row.categoryId)) || '—' }}</template></el-table-column>
        <el-table-column prop="baseUnitCode" label="基本单位" width="120"><template #default="{row}">{{ unitLabels.get(String(row.baseUnitCode)) || row.baseUnitCode }}</template></el-table-column>
        <el-table-column prop="purchaseUnitCode" label="采购单位" width="120"><template #default="{row}">{{ unitLabels.get(String(row.purchaseUnitCode)) || row.purchaseUnitCode || '—' }}</template></el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="105" />
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':row.status==='REJECTED'?'danger':row.status==='DISABLED'?'info':'warning'">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="versionNo" label="版本" width="75" />
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="can('MDM:MATERIAL:UPDATE') && editable(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="can('MDM:MATERIAL:CREATE') && ['DRAFT','REJECTED'].includes(row.status)" link type="success" @click="submit(row)">提交审批</el-button>
            <el-button v-if="can('MDM:MATERIAL:DISABLE') && row.status==='PUBLISHED'" link type="danger" @click="disable(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-if="rows.length || total" v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="editingId === null ? '新增物料' : '编辑物料'" width="900px" top="5vh" destroy-on-close>
      <el-alert v-if="formError" class="form-error" type="error" show-icon :title="formError" :closable="true" @close="formError=''" />
      <el-form label-position="top" class="material-form">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="物料编码" required><el-input v-model="form.materialCode" :disabled="editingId!==null" placeholder="请输入唯一物料编码" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称" required><el-input v-model="form.materialName" placeholder="请输入物料名称" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料类型" required><el-select v-model="form.materialType" style="width:100%" placeholder="请选择物料类型"><el-option v-for="item in materialTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料分类"><el-select v-model="form.categoryId" clearable filterable style="width:100%" placeholder="请选择末级物料分类"><el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格型号"><el-input v-model="form.materialSpec" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格说明"><el-input v-model="form.specDesc" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="基本单位" required><el-select v-model="form.baseUnitCode" filterable style="width:100%" placeholder="从统一单位字典中选择" @change="unitChanged"><el-option v-for="item in units" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="采购单位"><el-select v-model="form.purchaseUnitCode" clearable filterable style="width:100%" placeholder="可与基本单位不同" @change="onPurchaseUnitChanged"><el-option v-for="item in units" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="采购单位换算率"><el-input-number v-model="form.conversionRate" :min="0.000001" :precision="6" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="安全库存"><el-input-number v-model="form.safetyStock" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保质期（天）"><el-input-number v-model="form.shelfLifeDays" :min="0" :precision="0" controls-position="right" style="width:100%" placeholder="选填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标准成本"><el-input-number v-model="form.standardPrice" :min="0" :precision="4" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="是否批次管理"><el-switch v-model="form.batchManaged" active-text="是" inactive-text="否" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="是否需要检验"><el-switch v-model="form.inspectionRequired" active-text="是" inactive-text="否" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="检验标准"><el-input v-model="form.inspectionStandard" type="textarea" :rows="3" placeholder="选填，可填写检验规范或外部标准号" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ editingId===null ? '创建草稿' : '保存修改' }}</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDialog" title="物料主数据详情" size="620px">
      <el-descriptions v-if="selected" :column="1" border><el-descriptions-item v-for="[label,value] in details" :key="label" :label="label">{{ value ?? '—' }}</el-descriptions-item></el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.material-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{max-width:960px;margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-input{width:260px}.toolbar .el-select{width:170px}.form-error{margin-bottom:14px}.material-form :deep(.el-form-item){margin-bottom:17px}
</style>
