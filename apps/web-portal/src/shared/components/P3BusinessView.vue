<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import http from '../../api/http'
import { showErrorDialog } from '../errorDialog'
import { zh, zhKey, zhQms } from '../display'
import { loadMdmMaterials, loadMdmUnits, type MdmMaterialOption, type MdmUnitOption } from '../mdmOptions'
import TablePager from './TablePager.vue'
import { p3Catalog, type P3Field } from '../p3Catalog'

const props = defineProps<{ system: string; kind: string; title: string }>()
const cfg = computed(() => p3Catalog[`${props.system}:${props.kind}`] || { fields: [], columns: [], readonly: true })
const rows = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const dialog = ref(false)
const editingId = ref<number | null>(null)
const drawer = ref(false)
const detailData = ref<any>({})
const form = reactive<Record<string, any>>({})
const actionDialog = ref(false)
const actionKind = ref('')
const actionRecord = ref<any>(null)
const actionForm = reactive<Record<string, any>>({})
const countLines = ref<any[]>([])
const countLineDialog = ref(false)
const countLineEditingId = ref<number | null>(null)
const countLineForm = reactive<Record<string, any>>({})

const isQms = computed(() => props.system === 'qms')
const isWms = computed(() => props.system === 'wms')
const isSrm = computed(() => props.system === 'srm')
const canCreate = computed(() => (isWms.value && props.kind === 'inventory-actions') || (!cfg.value.readonly && cfg.value.fields.length > 0))
const canImport = computed(() => canCreate.value && !(isWms.value && props.kind === 'inventory-actions'))
const formTitle = computed(() => `${editingId.value ? '修改' : '新增'}${props.title}`)
const onboardingOptions = ref<any[]>([])
const rfqOptions = ref<any[]>([])
const purchaseOrderOptions = ref<any[]>([])
const mdmMaterials = ref<MdmMaterialOption[]>([])
const mdmUnits = ref<MdmUnitOption[]>([])
const sharedOptionsLoading = ref(false)

function toSnake(value: string) { return value.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`) }
function toCamel(value: string) { return value.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase()) }

function resetForm() {
  Object.keys(form).forEach(key => delete form[key])
  cfg.value.fields.forEach(field => { form[field.key] = null })
}

async function load() {
  loading.value = true
  try {
    const response = await http.get(`/${props.system}/${props.kind}`, {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, status: status.value || undefined }
    })
    rows.value = response.data.data.content || []
    total.value = response.data.data.totalElements || 0
    if (!rows.value.length && page.value > 1) { page.value--; return await load() }
  } catch (error: any) {
    showErrorDialog(error?.message || '数据加载失败')
  } finally {
    loading.value = false
  }
}

function normalizePayload() {
  const result: Record<string, any> = {}
  for (const field of cfg.value.fields) {
    const value = form[field.key]
    result[field.key] = typeof value === 'string' && value.trim() === '' ? null
      : field.type === 'number' && value !== null && value !== undefined ? Number(value) : value
  }
  return result
}

function validateForm() {
  for (const field of cfg.value.fields) {
    const value = form[field.key]
    const empty = value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
    if (field.required && empty) { ElMessage.warning(`请填写${field.label}`); return false }
    if (!empty && field.type === 'number' && Number.isNaN(Number(value))) { ElMessage.warning(`${field.label}必须是数字`); return false }
    if (!empty && field.type === 'number' && field.min !== undefined && Number(value) < field.min) {
      ElMessage.warning(`${field.label}不能小于${field.min}`); return false
    }
  }
  return true
}

async function save() {
  if (!validateForm()) return
  try {
    const data = normalizePayload()
    if (isWms.value && props.kind === 'inventory-actions') {
      const actionRoutes: Record<string, string> = { FREEZE: 'freeze', UNFREEZE: 'unfreeze', QUARANTINE: 'quarantine', RELEASE: 'release', SCRAP: 'scrap', SUPPLIER_RETURN: 'supplier-return' }
      const route = actionRoutes[String(data.actionType)]
      if (!route) { ElMessage.warning('请选择库存动作'); return }
      await http.post(`/wms/inventory-actions/${route}`, {
        idempotencyKey: data.idempotencyKey, materialCode: data.materialCode, warehouseCode: data.warehouseCode,
        locationCode: data.locationCode, batchNo: data.batchNo, quantity: data.quantity,
        sourceSystem: data.sourceSystem || 'WMS', sourceNo: data.sourceNo, reason: data.reason
      })
    } else if (editingId.value === null) await http.post(`/${props.system}/${props.kind}`, data)
    else await http.put(`/${props.system}/${props.kind}/${editingId.value}`, data)
    dialog.value = false
    ElMessage.success(isWms.value && props.kind === 'inventory-actions' ? '库存动作已执行，库存余额与流水已同步' : editingId.value === null ? '新增成功' : '修改成功')
    page.value = 1
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message || '保存失败，请检查填写内容')
  }
}

async function detail(row: any) {
  try {
    const response = await http.get(`/${props.system}/${props.kind}/${row.id}`)
    detailData.value = response.data.data
    if (isWms.value && props.kind === 'counts') await loadCountLines(Number(row.id))
    drawer.value = true
  } catch (error: any) {
    showErrorDialog(error?.message || '详情加载失败')
  }
}

async function loadCountLines(countId: number) {
  try {
    const response = await http.get(`/wms/counts/${countId}/lines`)
    countLines.value = response.data.data || []
  } catch (error: any) {
    showErrorDialog(error?.message || '盘点明细加载失败')
  }
}

function openNewCountLine() {
  if (!mdmMaterials.value.length) { showErrorDialog('主数据中暂无已发布物料，请先维护并发布物料主数据'); return }
  countLineEditingId.value = null
  Object.keys(countLineForm).forEach(key => delete countLineForm[key])
  Object.assign(countLineForm, { materialCode: '', locationCode: '', batchNo: '', actualQty: null })
  countLineDialog.value = true
}

function openEditCountLine(line: any) {
  countLineEditingId.value = Number(line.id)
  Object.keys(countLineForm).forEach(key => delete countLineForm[key])
  Object.assign(countLineForm, { actualQty: line.actual_qty === null ? null : Number(line.actual_qty) })
  countLineDialog.value = true
}

async function saveCountLine() {
  try {
    if (countLineEditingId.value !== null) {
      if (countLineForm.actualQty === null || countLineForm.actualQty === '' || Number(countLineForm.actualQty) < 0) { ElMessage.warning('实盘数量不能小于零'); return }
      await http.put(`/wms/counts/${detailData.value.id}/lines/${countLineEditingId.value}`, { actualQty: Number(countLineForm.actualQty) })
    } else {
      if (!mdmMaterials.value.some(item => item.value === String(countLineForm.materialCode || ''))) { ElMessage.warning('请选择有效的已发布物料'); return }
      await http.post(`/wms/counts/${detailData.value.id}/lines`, {
        materialCode: String(countLineForm.materialCode).trim(),
        locationCode: countLineForm.locationCode || null,
        batchNo: countLineForm.batchNo || null
      })
    }
    countLineDialog.value = false
    await loadCountLines(Number(detailData.value.id))
    ElMessage.success(countLineEditingId.value === null ? '盘点明细已新增' : '实盘数量已保存')
  } catch (error: any) {
    showErrorDialog(error?.message || '盘点明细保存失败')
  }
}

async function approveCountLine(line: any) {
  try {
    await http.post(`/wms/counts/${detailData.value.id}/lines/${line.id}/approve`)
    await loadCountLines(Number(detailData.value.id))
    ElMessage.success('盘点明细复核通过')
  } catch (error: any) {
    showErrorDialog(error?.message || '盘点明细复核失败')
  }
}

async function removeCountLine(line: any) {
  try {
    await ElMessageBox.confirm(`确认删除物料 ${line.material_code} 的盘点明细？`, '删除盘点明细', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await http.delete(`/wms/counts/${detailData.value.id}/lines/${line.id}`)
    await loadCountLines(Number(detailData.value.id))
    ElMessage.success('盘点明细已删除')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    showErrorDialog(error?.message || '盘点明细删除失败')
  }
}

async function openEdit(row: any) {
  try {
    const response = await http.get(`/${props.system}/${props.kind}/${row.id}`)
    detailData.value = response.data.data
    resetForm()
    for (const field of cfg.value.fields) {
      const raw = detailData.value[field.key] ?? detailData.value[toSnake(field.key)]
      form[field.key] = field.type === 'number' && raw !== null && raw !== undefined ? Number(raw) : field.type === 'select' && raw !== null && raw !== undefined ? String(raw) : (raw ?? null)
    }
    editingId.value = row.id
    dialog.value = true
  } catch (error: any) {
    showErrorDialog(error?.message || '读取单据失败，无法编辑')
  }
}

function openCreate() { editingId.value = null; resetForm(); dialog.value = true }

async function loadSrmReferences() {
  if (!isSrm.value) return
  try {
    if (props.kind === 'qualifications') {
      const response = await http.get('/srm/onboarding', { params: { page: 1, size: 200 } })
      onboardingOptions.value = response.data.data.content || []
    } else if (props.kind === 'quotes') {
      const response = await http.get('/srm/rfqs', { params: { page: 1, size: 200 } })
      rfqOptions.value = (response.data.data.content || []).filter((row: any) => ['PUBLISHED', 'QUOTING'].includes(String(row.status)))
    } else if (props.kind === 'asns') {
      const response = await http.get('/srm/purchase-orders', { params: { page: 1, size: 200 } })
      purchaseOrderOptions.value = (response.data.data.content || []).filter((row: any) => ['SENT', 'PARTIAL'].includes(String(row.status || row.orderStatus)))
    }
  } catch (error: any) {
    showErrorDialog(error?.message || '关联业务数据加载失败')
  }
}

async function loadSharedMdmOptions() {
  const fieldKeys = cfg.value.fields.map(field => field.key)
  const needMaterials = fieldKeys.includes('materialCode') || (isWms.value && props.kind === 'counts')
  const needUnits = fieldKeys.includes('unitCode')
  sharedOptionsLoading.value = true
  try {
    const [materials, units] = await Promise.all([
      needMaterials ? loadMdmMaterials() : Promise.resolve(mdmMaterials.value),
      needUnits ? loadMdmUnits() : Promise.resolve(mdmUnits.value),
    ])
    if (needMaterials) mdmMaterials.value = materials
    if (needUnits) mdmUnits.value = units
  } catch (error: any) {
    showErrorDialog(error?.message || '物料或单位主数据加载失败，请刷新后重试')
  } finally { sharedOptionsLoading.value = false }
}

function fieldOptions(field: P3Field) {
  if (field.key === 'materialCode') return mdmMaterials.value.map(item => ({ label: item.label, value: item.value }))
  if (field.key === 'unitCode') return mdmUnits.value.map(item => ({ label: item.label, value: item.value }))
  if (props.kind === 'qualifications' && field.key === 'onboardingId')
    return onboardingOptions.value.map(row => ({ label: `${row.application_no} · ${row.supplier_name}`, value: String(row.id) }))
  if (props.kind === 'quotes' && field.key === 'rfqId')
    return rfqOptions.value.map(row => ({ label: `${row.rfq_no} · ${row.title}`, value: String(row.id) }))
  if (props.kind === 'asns' && field.key === 'purchaseOrderNo')
    return purchaseOrderOptions.value.map(row => ({ label: `${row.purchaseOrderNo} · ${row.supplierName || row.supplierCode}`, value: row.purchaseOrderNo }))
  return field.options || []
}

function fieldDisabled(field: P3Field) {
  if (editingId.value === null) return isSrm.value && props.kind === 'asns' && ['supplierCode', 'materialCode'].includes(field.key)
  if (isQms.value) {
    const immutable: Record<string, string[]> = {
      defects: ['inspectionNo'], reworks: ['defectNo'],
      ncrs: ['ncrNo', 'inspectionNo', 'materialCode', 'defectQty', 'sourceNo', 'supplierCode'],
      capas: ['capaNo', 'ncrNo'], '8d': ['eightDNo', 'ncrNo']
    }
    if (immutable[props.kind]?.includes(field.key)) return true
  }
  const immutable: Record<string, string[]> = {
    onboarding: ['applicationNo'], qualifications: ['onboardingId', 'qualificationType', 'certificateNo'],
    rfqs: ['rfqNo'], quotes: ['rfqId', 'supplierCode'], 'purchase-orders': ['purchaseOrderNo'],
    asns: ['asnNo', 'purchaseOrderNo', 'supplierCode', 'materialCode'], 'supplier-quality': ['issueNo', 'supplierCode']
  }
  return Boolean(immutable[props.kind]?.includes(field.key))
    || (isSrm.value && props.kind === 'asns' && ['supplierCode', 'materialCode'].includes(field.key))
}

function srmFieldChanged(field: P3Field) {
  if (props.kind !== 'asns' || field.key !== 'purchaseOrderNo') return
  const order = purchaseOrderOptions.value.find(row => row.purchaseOrderNo === form.purchaseOrderNo)
  if (order) {
    form.supplierCode = order.supplierCode
    form.materialCode = order.materialCode
  }
}

async function remove(row: any) {
  const srmNumberKeys: Record<string, string> = { onboarding: 'application_no', qualifications: 'certificate_no', rfqs: 'rfq_no', quotes: 'supplier_code', 'purchase-orders': 'purchase_order_no', asns: 'asn_no', 'supplier-quality': 'issue_no' }
  const srmKey = srmNumberKeys[props.kind]
  const documentNumber = isSrm.value && srmKey ? row[srmKey] ?? row[toCamel(srmKey)] ?? row.id : null
  try {
    const label = isSrm.value ? documentNumber : row[props.kind === 'inspections' ? 'inspection_no' : props.kind === 'defects' ? 'defect_no' : props.kind === 'ncrs' ? 'ncr_no' : props.kind === 'reworks' ? 'rework_no' : props.kind === 'capas' ? 'capa_no' : props.kind === '8d' ? 'eight_d_no' : props.kind === 'standards' ? 'standard_code' : 'plan_code'] || row.id
    await ElMessageBox.confirm(`确认删除单据“${label}”吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await http.delete(`/${props.system}/${props.kind}/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    showErrorDialog(error?.message || '删除失败')
  }
}

function rowStatus(row: any) {
  if (props.kind === 'inspections') return row.inspect_result ?? row.result
  if (props.kind === 'defects') return row.disposition || 'OPEN'
  if (props.kind === 'reworks') return row.rework_status ?? row.status
  if (props.kind === 'qualifications') return row.audit_result ?? row.auditResult
  if (props.kind === 'supplier-quality') return row.eight_d_status ?? row.eightDStatus
  return row.status || row.order_status || row.orderStatus || row.inspect_result
}

function cellValue(row: any, column: string) {
  if (column === 'inspect_result') return row.inspect_result ?? row.result
  if (column === 'rework_status') return row.rework_status ?? row.status
  if (column === 'disposition') return row.disposition ?? 'OPEN'
  if (column === 'order_status') return row.order_status ?? row.orderStatus ?? row.status
  return row[column] ?? row[toCamel(column)]
}

function displayValue(value: unknown) { return isQms.value ? zhQms(value) : zh(value) }
function unitLabel(value: unknown) { return mdmUnits.value.find(item => item.value === String(value ?? ''))?.label || displayValue(value) }

function displayCell(row: any, column: string) {
  const value = cellValue(row, column)
  if (column === 'unit_code' || column === 'unitCode') return unitLabel(value)
  const state = cfg.value.statuses?.find(item => item.value === String(value))
  if (state) return state.label
  if (props.kind === 'purchase-orders') {
    const poStates: Record<string, string> = { CREATED: '新建', SENT: '已下达', PARTIAL: '部分收货', CLOSED: '已完成', RECEIVED: '已收货', CANCELLED: '已取消', CANCELED: '已取消' }
    if (poStates[String(value)]) return poStates[String(value)]
  }
  return displayValue(value)
}

function displayDetail(key: string, value: unknown) {
  if (key === 'unit_code' || key === 'unitCode') return unitLabel(value)
  if (['status', 'order_status', 'orderStatus', 'audit_result', 'auditResult', 'eight_d_status', 'eightDStatus'].includes(key)) {
    const state = cfg.value.statuses?.find(item => item.value === String(value))
    if (state) return state.label
    if (props.kind === 'purchase-orders') {
      const orderStates: Record<string, string> = { CREATED: '新建', SENT: '已下达', PARTIAL: '部分收货', RECEIVED: '已收货', CLOSED: '已完成', CANCELLED: '已取消', CANCELED: '已取消' }
      return orderStates[String(value)] || displayValue(value)
    }
  }
  return displayValue(value)
}

function transitionOptions(row: any) {
  if (isSrm.value) {
    const current = String(rowStatus(row) || '')
    const allowed: Record<string, Record<string, string[]>> = {
      onboarding: { DRAFT: ['APPROVING'], APPROVING: ['APPROVED', 'REJECTED'], APPROVED: ['CERTIFIED', 'BLACKLISTED'], CERTIFIED: ['BLACKLISTED'] },
      qualifications: { PENDING: ['PASSED', 'FAILED'], FAILED: ['PENDING'] },
      rfqs: { DRAFT: ['PUBLISHED', 'CANCELLED'], PUBLISHED: ['QUOTING', 'CLOSED', 'CANCELLED'], QUOTING: ['CLOSED', 'CANCELLED'], CLOSED: ['CANCELLED'] },
      asns: { DRAFT: ['SHIPPED', 'CANCELLED'], SHIPPED: ['CANCELLED'], ARRIVED: ['RECEIVED', 'RETURNED'] },
      'supplier-quality': { OPEN: ['EIGHT_D_REQUIRED'], EIGHT_D_REQUIRED: ['VERIFYING'], VERIFYING: ['CLOSED', 'EIGHT_D_REQUIRED'] }
    }
    const next = new Set(allowed[props.kind]?.[current] || [])
    return (cfg.value.statuses || []).filter(option => next.has(option.value))
  }
  if (isWms.value) {
    if (props.kind !== 'putaway') return []
    const allowed: Record<string, string[]> = { CREATED: ['PROCESSING', 'CANCELLED'], PROCESSING: ['CANCELLED'] }
    const next = new Set(allowed[String(rowStatus(row))] || [])
    return (cfg.value.statuses || []).filter(option => next.has(option.value))
  }
  if (!isQms.value) return cfg.value.transitions || cfg.value.statuses || []
  const current = rowStatus(row)
  if (props.kind === 'standards') {
    if (current === 'DRAFT') return [{ label: '发布', value: 'PUBLISHED' }]
    if (current === 'PUBLISHED') return [{ label: '停用', value: 'DISABLED' }]
    if (current === 'DISABLED') return [{ label: '重新发布', value: 'PUBLISHED' }]
  }
  if (props.kind === 'sampling-plans') {
    if (current === 'ACTIVE' || current === 'ENABLED') return [{ label: '停用', value: 'DISABLED' }]
    if (current === 'DISABLED') return [{ label: '启用', value: 'ACTIVE' }]
  }
  if (props.kind === 'capas' && current === 'DRAFT') return [{ label: '开始整改', value: 'IMPLEMENTING' }]
  if (props.kind === '8d' && current === 'OPEN') return [{ label: '提交8D', value: 'SUBMITTED' }]
  if (props.kind === 'ncrs' || props.kind === 'defects' || props.kind === 'inspections' || props.kind === 'reworks') return []
  return []
}

async function changeStatus(row: any, value: string) {
  try {
    await http.post(`/${props.system}/${props.kind}/${row.id}/status`, { status: value })
    ElMessage.success('状态已更新')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message || '状态变更失败')
  }
}

function canEdit(row: any) {
  if (isSrm.value) {
    const state = String(rowStatus(row) || '')
    const editable: Record<string, string[]> = {
      onboarding: ['DRAFT', 'REJECTED'], qualifications: ['PENDING', 'FAILED'], rfqs: ['DRAFT'],
      quotes: ['SUBMITTED', 'DRAFT'], 'purchase-orders': ['CREATED'], asns: ['DRAFT'],
      'supplier-quality': ['OPEN', 'EIGHT_D_REQUIRED', 'VERIFYING']
    }
    return !cfg.value.readonly && cfg.value.fields.length > 0 && (editable[props.kind] || []).includes(state)
  }
  if (isWms.value) {
    const state = rowStatus(row)
    if (props.kind === 'receipts') return state === 'PENDING_INSPECTION'
    if (props.kind === 'putaway') return state === 'CREATED'
    if (props.kind === 'transfers' || props.kind === 'counts') return state === 'DRAFT'
    return false
  }
  if (!isQms.value || cfg.value.readonly) return false
  const state = rowStatus(row)
  if (props.kind === 'standards') return state === 'DRAFT' || state === 'DISABLED'
  if (props.kind === 'sampling-plans') return true
  if (props.kind === 'inspections') return state === 'PENDING'
  if (props.kind === 'defects') return !row.disposition
  if (props.kind === 'ncrs') return state === 'OPEN' || state === 'REVIEWING'
  if (props.kind === 'reworks') return state === 'PENDING'
  if (props.kind === 'capas') return state === 'DRAFT' || state === 'IMPLEMENTING'
  if (props.kind === '8d') return state === 'OPEN' || state === 'SUBMITTED'
  return false
}

function canDelete(row: any) {
  const state = rowStatus(row)
  if (isSrm.value) {
    const removable: Record<string, string[]> = {
      onboarding: ['DRAFT', 'REJECTED'], qualifications: ['PENDING', 'FAILED'], rfqs: ['DRAFT', 'CANCELLED'],
      quotes: ['SUBMITTED', 'DRAFT', 'LOST'], 'purchase-orders': ['CREATED'], asns: ['DRAFT', 'CANCELLED'],
      'supplier-quality': ['OPEN', 'EIGHT_D_REQUIRED']
    }
    return !cfg.value.readonly && cfg.value.fields.length > 0 && (removable[props.kind] || []).includes(String(state || ''))
  }
  if (isWms.value) {
    if (props.kind === 'putaway') return state === 'CREATED'
    if (props.kind === 'transfers' || props.kind === 'counts') return state === 'DRAFT'
    return false
  }
  if (!isQms.value || cfg.value.readonly) return false
  if (props.kind === 'standards') return state === 'DRAFT' || state === 'DISABLED'
  if (props.kind === 'sampling-plans') return state === 'DISABLED' || state === 'DRAFT'
  if (props.kind === 'inspections') return state === 'PENDING'
  if (props.kind === 'defects') return !row.disposition
  if (props.kind === 'ncrs') return state === 'OPEN'
  if (props.kind === 'reworks') return state === 'PENDING'
  if (props.kind === 'capas') return state === 'DRAFT'
  if (props.kind === '8d') return state === 'OPEN'
  return false
}

function qmsActionName(row: any) {
  const state = rowStatus(row)
  if (props.kind === 'inspections' && state === 'PENDING') return '检验判定'
  if (props.kind === 'defects' && !row.disposition) return '不合格处置'
  if (props.kind === 'ncrs' && (state === 'OPEN' || state === 'REVIEWING')) return 'NCR处置'
  if (props.kind === 'reworks' && state === 'PENDING') return '返工开工'
  if (props.kind === 'reworks' && state === 'DOING') return '返工完工'
  if (props.kind === 'capas' && state === 'IMPLEMENTING') return '提交验证'
  if (props.kind === 'capas' && state === 'VERIFYING') return '关闭CAPA'
  if (props.kind === '8d' && state === 'SUBMITTED') return '提交验证'
  if (props.kind === '8d' && state === 'VERIFYING') return '关闭8D'
  return ''
}

function dispositionOptions(kind: string) {
  const values = kind === 'ncr-dispose' ? p3Catalog['qms:ncrs']?.statuses : p3Catalog['qms:defects']?.statuses
  return (values || []).filter(option => !['OPEN', 'REVIEWING', 'CLOSED'].includes(option.value))
}

function openAction(row: any) {
  actionRecord.value = row
  Object.keys(actionForm).forEach(key => delete actionForm[key])
  if (props.kind === 'inspections') {
    actionKind.value = 'judge'; actionForm.qualifiedQty = null; actionForm.defectQty = null; actionForm.result = ''
  } else if (props.kind === 'defects') {
    actionKind.value = 'defect-dispose'; actionForm.disposition = 'REWORK'; actionForm.qty = Number(row.defect_qty); actionForm.reason = ''
  } else if (props.kind === 'ncrs') {
    actionKind.value = 'ncr-dispose'; actionForm.disposition = 'REWORK'; actionForm.reason = ''
  } else if (props.kind === 'reworks') {
    actionKind.value = row.rework_status === 'PENDING' ? 'rework-start' : 'rework-complete'; actionForm.outcome = 'DONE'
  }
  actionDialog.value = true
}

async function runAction() {
  const row = actionRecord.value
  try {
    if (actionKind.value === 'judge') {
      if (actionForm.qualifiedQty === null || actionForm.defectQty === null || !actionForm.result) { ElMessage.warning('请填写合格数、不合格数和检验结论'); return }
      await http.post(`/qms/inspections/${row.id}/judge`, null, { params: { qualifiedQty: actionForm.qualifiedQty, defectQty: actionForm.defectQty, result: actionForm.result } })
    } else if (actionKind.value === 'defect-dispose') {
      if (!actionForm.qty || Number(actionForm.qty) <= 0) { ElMessage.warning('请输入有效的处置数量'); return }
      await http.post(`/qms/defects/${row.id}/disposition`, null, { params: { action: actionForm.disposition, qty: actionForm.qty, reason: actionForm.reason || undefined } })
    } else if (actionKind.value === 'ncr-dispose') {
      await http.post(`/qms/ncrs/${row.id}/dispose`, { disposition: actionForm.disposition, reason: actionForm.reason || '' })
    } else if (actionKind.value === 'rework-start') {
      await http.post(`/qms/reworks/${row.id}/start`)
    } else if (actionKind.value === 'rework-complete') {
      await http.post(`/qms/reworks/${row.id}/complete`, null, { params: { status: actionForm.outcome } })
    }
    actionDialog.value = false
    ElMessage.success('质量流程已更新')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message || '质量流程操作失败')
  }
}

async function qmsLifecycleAction(row: any) {
  const state = rowStatus(row)
  const close = state === 'VERIFYING'
  const endpoint = props.kind === 'capas' ? `/qms/capas/${row.id}/${close ? 'close' : 'verify'}` : `/qms/8d/${row.id}/${close ? 'close' : 'verify'}`
  try {
    await http.post(endpoint)
    ElMessage.success(close ? '单据已关闭' : '单据已提交验证')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message || '质量闭环操作失败')
  }
}

async function special(row: any) {
  try {
    if (props.system === 'srm' && props.kind === 'rfqs') {
      if (['PUBLISHED', 'QUOTING'].includes(String(rowStatus(row)))) {
        const input = await ElMessageBox.prompt('按“供应商编码,单价,交付日期”填写，例如 SUP001,12.50,2026-12-20', '登记供应商报价')
        const [supplierCode, price, deliveryDate] = input.value.split(',').map((value: string) => value.trim())
        const unitPrice = Number(price)
        if (!supplierCode || !Number.isFinite(unitPrice) || unitPrice < 0 || !/^\d{4}-\d{2}-\d{2}$/.test(deliveryDate || '')) throw new Error('报价格式不正确，请按提示填写')
        await http.post('/srm/quotes', { rfqId: row.id, supplierCode, unitPrice, deliveryDate })
      } else {
        const input = await ElMessageBox.prompt('请输入中标报价记录编号', '询价定标')
        const quoteId = Number(input.value)
        if (!Number.isInteger(quoteId) || quoteId < 1) throw new Error('报价记录编号必须是正整数')
        await http.post(`/srm/rfqs/${row.id}/award`, null, { params: { quoteId } })
      }
    } else if (props.system === 'srm' && props.kind === 'asns') {
      const input = await ElMessageBox.prompt('请输入收货仓库编码', '确认到货', { inputValue: 'WH01' })
      if (!input.value.trim()) throw new Error('请填写收货仓库编码')
      await http.post(`/srm/asns/${row.id}/arrive`, null, { params: { warehouseCode: input.value } })
    } else if (props.system === 'srm' && props.kind === 'purchase-orders') {
      await ElMessageBox.confirm(`确认下达采购订单 ${row.purchase_order_no || row.purchaseOrderNo}？`, '下达采购订单', { type: 'warning', confirmButtonText: '确认下达', cancelButtonText: '取消' })
      await http.post(`/srm/purchase-orders/${row.id}/send`)
    } else if (isWms.value && props.kind === 'putaway') {
      await http.post(`/wms/putaway/${row.id}/complete`)
    } else if (isWms.value && props.kind === 'transfers') {
      if (rowStatus(row) === 'DRAFT') {
        await ElMessageBox.confirm(`确认调拨单 ${row.transfer_no}？确认后将锁定该单据。`, '确认库存调拨', { type: 'warning', confirmButtonText: '确认调拨', cancelButtonText: '取消' })
        await http.post(`/wms/transfers/${row.id}/confirm`)
      } else if (rowStatus(row) === 'CONFIRMED') {
        const input = await ElMessageBox.prompt('请输入本次执行的业务幂等号，重试时请使用相同编号。', '执行库存调拨', { inputValue: `${row.transfer_no}-MOVE` })
        if (!input.value.trim()) { ElMessage.warning('业务幂等号不能为空'); return }
        await http.post(`/wms/transfers/${row.id}/execute`, null, { params: { idempotencyKey: input.value.trim() } })
      }
    } else if (isWms.value && props.kind === 'counts') {
      if (rowStatus(row) === 'DRAFT') await http.post(`/wms/counts/${row.id}/release`)
      else if (rowStatus(row) === 'RELEASED' || rowStatus(row) === 'COUNTING') await http.post(`/wms/counts/${row.id}/review`)
      else if (rowStatus(row) === 'REVIEWING') {
        const input = await ElMessageBox.prompt('请输入本次盘点过账的业务幂等号。', '盘点差异过账', { inputValue: `${row.count_no}-POST` })
        if (!input.value.trim()) { ElMessage.warning('业务幂等号不能为空'); return }
        await http.post(`/wms/counts/${row.id}/post`, null, { params: { idempotencyKey: input.value.trim() } })
      }
    }
    ElMessage.success(isWms.value ? '仓储业务操作已完成' : '业务操作已完成')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    showErrorDialog(error?.message || '业务操作失败')
  }
}

function specialName(row?: any) {
  if (isWms.value && row) {
    const state = rowStatus(row)
    if (props.kind === 'putaway' && state === 'PROCESSING') return '完成上架'
    if (props.kind === 'transfers' && state === 'DRAFT') return '确认调拨'
    if (props.kind === 'transfers' && state === 'CONFIRMED') return '执行调拨'
    if (props.kind === 'counts') return ({ DRAFT: '下达盘点', RELEASED: '开始盘点', COUNTING: '提交复核', REVIEWING: '盘点过账' } as Record<string, string>)[state] || ''
    return ''
  }
  if (props.system === 'srm' && props.kind === 'rfqs') return ['PUBLISHED', 'QUOTING'].includes(String(rowStatus(row))) ? '登记报价' : rowStatus(row) === 'CLOSED' ? '询价定标' : ''
  if (props.system === 'srm' && props.kind === 'asns') return rowStatus(row) === 'SHIPPED' ? '确认到货' : ''
  if (props.system === 'srm' && props.kind === 'purchase-orders') return rowStatus(row) === 'CREATED' ? '下达订单' : ''
  return ''
}

function exportCsv() {
  const columns = cfg.value.columns
  const escape = (value: any) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const header = columns.map(column => escape(isQms.value || isWms.value || isSrm.value ? zhKey(column) : column))
  const data = rows.value.map(row => columns.map(column => escape(isSrm.value ? displayCell(row, column) : displayValue(cellValue(row, column)))))
  const csv = '\ufeff' + [header, ...data].map(line => line.join(',')).join('\r\n')
  const anchor = document.createElement('a')
  anchor.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  anchor.download = `${props.title}.csv`; anchor.click(); URL.revokeObjectURL(anchor.href)
}

async function importCsv(file: UploadFile) {
  try {
    if (!file.raw) throw new Error('无法读取所选文件')
    const text = await file.raw.text()
    const lines = text.replace(/^\ufeff/, '').split(/\r?\n/).filter(line => line.trim())
    if (lines.length < 2) throw new Error('CSV文件中没有可导入的数据')
    const parseRow = (line: string) => {
      const values: string[] = []; let value = ''; let quoted = false
      for (let index = 0; index < line.length; index += 1) {
        const char = line[index]
        if (char === '"' && quoted && line[index + 1] === '"') { value += '"'; index += 1 }
        else if (char === '"') quoted = !quoted
        else if (char === ',' && !quoted) { values.push(value); value = '' }
        else value += char
      }
      values.push(value); return values
    }
    const heads = parseRow(lines.shift()!).map(value => value.trim())
    const fieldByHeader = new Map<string, P3Field>()
    for (const field of cfg.value.fields) {
      for (const name of [field.key, toSnake(field.key), field.label, zhKey(field.key), zhKey(toSnake(field.key))]) fieldByHeader.set(name, field)
    }
    for (const line of lines) {
      const values = parseRow(line)
      const body: Record<string, any> = {}
      heads.forEach((head, index) => {
        const field = fieldByHeader.get(head)
        if (!field) return
        const raw = (values[index] || '').trim()
        const option = fieldOptions(field).find(item => item.label === raw)
        body[field.key] = raw === '' ? null : option?.value ?? (field.type === 'number' ? Number(raw) : raw)
      })
      if (!Object.keys(body).length) throw new Error('CSV表头与当前页面字段不匹配')
      await http.post(`/${props.system}/${props.kind}`, body)
    }
    ElMessage.success(`已导入${lines.length}条`); page.value = 1; await load()
  } catch (error: any) {
    showErrorDialog(error?.message || '导入失败，请检查字段表头和数据格式')
  }
}

watch(() => [props.kind, props.system], () => { page.value = 1; status.value = ''; keyword.value = ''; void load(); void loadSrmReferences(); void loadSharedMdmOptions() })
onMounted(() => { void load(); void loadSrmReferences(); void loadSharedMdmOptions() })
</script>

<template>
  <section>
    <div class="head">
      <div><span>{{ isQms ? '质量管理业务中心' : isWms ? '仓储管理业务中心' : `${system.toUpperCase()}业务中心` }}</span><h1>{{ title }}</h1><p>{{ isWms ? '管理仓储单据、库存状态、批次追溯与库内作业；已过账的库存流水不可直接修改或删除。' : '质量单据、检验结论、整改闭环与来源追溯。' }}</p></div>
      <div class="actions">
        <el-upload v-if="canImport && !isQms" :show-file-list="false" :auto-upload="false" accept=".csv" @change="importCsv"><el-button>导入CSV</el-button></el-upload>
        <el-button @click="exportCsv">导出当前页</el-button>
        <el-button v-if="canCreate" type="primary" @click="openCreate">{{ isWms && kind === 'inventory-actions' ? '执行库存动作' : '新增' }}</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <div class="bar">
        <el-input v-model="keyword" :placeholder="isQms ? '输入单号、物料或来源' : '输入编号、物料或供应商'" clearable @keyup.enter="page=1;load()" />
        <el-select v-if="cfg.statuses?.length" v-model="status" placeholder="全部状态" clearable @change="page=1;load()" style="width:220px">
          <el-option v-for="item in cfg.statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-else v-model="status" placeholder="输入状态编码" clearable @keyup.enter="page=1;load()" />
        <el-button type="primary" @click="page=1;load()">查询</el-button>
        <el-button @click="keyword='';status='';page=1;load()">重置</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无业务数据">
        <el-table-column v-for="column in cfg.columns" :key="column" :prop="column" :label="$zhKey(column)" min-width="125" show-overflow-tooltip>
          <template #default="{ row }">{{ displayCell(row, column) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="isQms ? 420 : isWms ? 420 : 300" fixed="right">
          <template #default="{ row }">
            <el-button link @click="detail(row)">详情</el-button>
            <el-button v-if="canEdit(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete(row)" link type="danger" @click="remove(row)">删除</el-button>
            <el-button v-if="qmsActionName(row) && ['capas','8d'].includes(kind)" link type="success" @click="qmsLifecycleAction(row)">{{ qmsActionName(row) }}</el-button>
            <el-button v-else-if="qmsActionName(row)" link type="success" @click="openAction(row)">{{ qmsActionName(row) }}</el-button>
            <el-button v-if="specialName(row)" link @click="special(row)">{{ specialName(row) }}</el-button>
            <el-dropdown v-if="transitionOptions(row).length" @command="(value:string)=>changeStatus(row,value)">
              <el-button link type="primary">状态操作</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in transitionOptions(row)" :key="item.value" :command="item.value">{{ item.label }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" @change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="formTitle" width="760">
      <el-form label-width="132px" class="form">
        <el-form-item v-for="field in cfg.fields" :key="field.key" :label="field.required ? field.label : `${field.label}（选填）`" :required="field.required">
          <el-select v-if="field.type === 'select' || field.key === 'materialCode' || field.key === 'unitCode'" v-model="form[field.key]" clearable :placeholder="field.key === 'materialCode' ? '从统一物料主数据中选择' : field.key === 'unitCode' ? '从统一计量单位字典中选择' : `请选择${field.label}`" :disabled="fieldDisabled(field) || sharedOptionsLoading" style="width:100%" @change="srmFieldChanged(field)">
            <el-option v-for="option in fieldOptions(field)" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" type="date" value-format="YYYY-MM-DD" :placeholder="`请选择${field.label}`" :disabled="fieldDisabled(field)" style="width:100%" />
          <el-input v-else v-model="form[field.key]" :type="field.type === 'textarea' ? 'textarea' : field.type === 'number' ? 'number' : 'text'" :min="field.type === 'number' ? field.min : undefined" :rows="3" :disabled="fieldDisabled(field)" :placeholder="`请输入${field.label}`" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="actionDialog" :title="qmsActionName(actionRecord || {})" width="520">
      <el-form label-width="130px">
        <template v-if="actionKind === 'judge'">
          <el-form-item label="合格数量" required><el-input v-model="actionForm.qualifiedQty" type="number" min="0" /></el-form-item>
          <el-form-item label="不合格数量" required><el-input v-model="actionForm.defectQty" type="number" min="0" /></el-form-item>
          <el-form-item label="检验结论" required><el-select v-model="actionForm.result" style="width:100%"><el-option v-for="item in (p3Catalog['qms:inspections']?.statuses || [])" :key="item.value" :label="item.label" :value="item.value" :disabled="item.value === 'PENDING'" /></el-select></el-form-item>
        </template>
        <template v-if="actionKind === 'defect-dispose' || actionKind === 'ncr-dispose'">
          <el-form-item label="处置方式" required><el-select v-model="actionForm.disposition" style="width:100%"><el-option v-for="item in dispositionOptions(actionKind)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item v-if="actionKind === 'defect-dispose'" label="处置数量" required><el-input v-model="actionForm.qty" type="number" min="0" /></el-form-item>
          <el-form-item label="处置说明"><el-input v-model="actionForm.reason" type="textarea" :rows="3" /></el-form-item>
        </template>
        <el-form-item v-if="actionKind === 'rework-complete'" label="完工结论" required><el-select v-model="actionForm.outcome" style="width:100%"><el-option label="返工完成" value="DONE"/><el-option label="转为报废" value="SCRAPPED"/></el-select></el-form-item>
        <el-alert v-if="actionKind === 'rework-start'" title="确认开始执行该返工单？" type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="actionDialog=false">取消</el-button><el-button type="primary" @click="runAction">确认</el-button></template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="`${title}详情`" size="52%">
      <el-descriptions :column="1" border><el-descriptions-item v-for="(value,key) in detailData" :key="key" :label="$zhKey(String(key))">{{ displayDetail(String(key), value) }}</el-descriptions-item></el-descriptions>
      <template v-if="isWms && kind === 'counts'">
        <el-divider>盘点明细</el-divider>
        <div class="count-lines-head"><span>逐项登记实盘数量；所有差异复核通过后才能过账。</span><el-button v-if="['DRAFT','RELEASED','COUNTING'].includes(String(detailData.status))" size="small" type="primary" @click="openNewCountLine">新增明细</el-button></div>
        <el-table :data="countLines" size="small" stripe empty-text="暂无盘点明细">
          <el-table-column prop="material_code" label="物料编码" min-width="110" />
          <el-table-column prop="location_code" label="库位" min-width="90"><template #default="{ row }">{{ displayValue(row.location_code) }}</template></el-table-column>
          <el-table-column prop="batch_no" label="批次号" min-width="90"><template #default="{ row }">{{ displayValue(row.batch_no) }}</template></el-table-column>
          <el-table-column prop="book_qty" label="账面数量" min-width="95" />
          <el-table-column prop="actual_qty" label="实盘数量" min-width="95"><template #default="{ row }">{{ displayValue(row.actual_qty) }}</template></el-table-column>
          <el-table-column prop="difference_qty" label="盘点差异" min-width="95"><template #default="{ row }">{{ displayValue(row.difference_qty) }}</template></el-table-column>
          <el-table-column prop="review_status" label="复核状态" min-width="90"><template #default="{ row }">{{ row.review_status === 'APPROVED' ? '已复核' : '待复核' }}</template></el-table-column>
          <el-table-column label="操作" min-width="190" fixed="right">
            <template #default="{ row }">
              <el-button v-if="detailData.status === 'COUNTING'" link type="primary" @click="openEditCountLine(row)">录入实盘数</el-button>
              <el-button v-if="detailData.status === 'REVIEWING' && row.actual_qty !== null && row.review_status !== 'APPROVED'" link type="success" @click="approveCountLine(row)">复核通过</el-button>
              <el-button v-if="['DRAFT','RELEASED','COUNTING'].includes(String(detailData.status))" link type="danger" @click="removeCountLine(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-divider>审计时间轴</el-divider>
      <el-timeline>
        <el-timeline-item :timestamp="displayValue(detailData.updated_at || detailData.updatedAt || detailData.created_at || detailData.createdAt)">当前状态：{{ displayValue(rowStatus(detailData) || '已记录') }}</el-timeline-item>
        <el-timeline-item>业务记录编号：{{ detailData.id }}</el-timeline-item>
      </el-timeline>
    </el-drawer>

    <el-dialog v-model="countLineDialog" :title="countLineEditingId === null ? '新增盘点明细' : '登记实盘数量'" width="520" append-to-body>
      <el-form label-width="130px">
        <template v-if="countLineEditingId === null">
          <el-form-item label="物料编码" required><el-select v-model="countLineForm.materialCode" filterable placeholder="从统一物料主数据中选择"><el-option v-for="item in mdmMaterials" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="库位编码（选填）"><el-input v-model="countLineForm.locationCode" placeholder="请输入库位编码" /></el-form-item>
          <el-form-item label="批次号（选填）"><el-input v-model="countLineForm.batchNo" placeholder="请输入批次号" /></el-form-item>
        </template>
        <el-form-item v-else label="实盘数量" required><el-input v-model="countLineForm.actualQty" type="number" min="0" placeholder="请输入实盘数量" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="countLineDialog = false">取消</el-button><el-button type="primary" @click="saveCountLine">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.head{display:flex;justify-content:space-between;align-items:end;margin-bottom:16px}.head span{color:#3976d5;font-size:11px}.head h1{margin:5px 0;color:#263f5c}.head p{color:#8391a4}.actions,.bar{display:flex;gap:8px}.bar{margin-bottom:14px}.bar .el-input{width:250px}.form{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.form :deep(.el-form-item:has(textarea)){grid-column:1/-1}
</style>
