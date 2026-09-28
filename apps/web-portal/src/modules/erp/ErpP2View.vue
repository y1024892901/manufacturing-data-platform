<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh } from '../../shared/display'

type Option = { label: string; value: string | number }
type Field = {
  key: string
  label: string
  type?: 'text' | 'number' | 'date' | 'select' | 'textarea'
  required?: boolean
  min?: number
  max?: number
  options?: Option[]
}
type Column = { key: string; label: string; width?: number }
type PageConfig = {
  title: string
  api: string
  saveApi?: string
  docKind?: string
  columns: Column[]
  fields?: Field[]
  statuses?: string[]
  canCreate?: boolean
}

const props = defineProps<{ kind: string }>()
const today = new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10)
const defaultDueDate = new Date(Date.now() + 8 * 60 * 60 * 1000 + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
const erpPageValueLabels: Record<string, Record<string, string>> = {
  sales: { DRAFT: '草稿', CREDIT_PASSED: '信用通过', CREDIT_EXCEPTION: '信用例外', ATP_COMMITTED: '交期已承诺', CONFIRMED: '已确认', IN_PROD: '生产中', DELIVERED: '已交付', CLOSED: '已关闭', CANCELED: '已取消' },
  atp: { DRAFT: '草稿', CREDIT_PASSED: '信用通过', CREDIT_EXCEPTION: '信用例外', ATP_COMMITTED: '交期已承诺', CONFIRMED: '已确认', IN_PROD: '生产中', DELIVERED: '已交付' },
  production: { CREATED: '已创建', RELEASED: '已下达', IN_PROGRESS: '生产中', FINISHED: '已完工', CLOSED: '已关闭', CANCELED: '已取消' },
  mrp: { CREATED: '待运行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' },
  suggestions: { PROPOSED: '待确认', CONFIRMED: '已确认' },
  invoices: { DRAFT: '草稿', ISSUED: '已开具', APPLIED: '已核销', CANCELED: '已取消' },
  receipts: { DRAFT: '草稿', UNAPPLIED: '未核销', PARTIAL: '部分核销', APPLIED: '已核销' },
  receivables: { OPEN: '待收款', PARTIAL: '部分回款', SETTLED: '已结清', OVERDUE: '已逾期', BAD_DEBT: '坏账' },
  payables: { OPEN: '待付款', PARTIAL: '部分付款', PAID: '已付款', OVERDUE: '已逾期' },
  vouchers: { DRAFT: '草稿', POSTED: '已过账', CANCELED: '已取消' },
  credits: { ACTIVE: '启用', INACTIVE: '停用' },
}

const configs: Record<string, PageConfig> = {
  sales: {
    title: '销售订单', api: '/erp/sales-orders', saveApi: '/erp/sales-orders', canCreate: true,
    statuses: ['DRAFT', 'CREDIT_PASSED', 'CREDIT_EXCEPTION', 'ATP_COMMITTED', 'CONFIRMED', 'IN_PROD', 'DELIVERED', 'CLOSED', 'CANCELED'],
    columns: [
      { key: 'sales_order_no', label: '销售订单号' }, { key: 'customer_code', label: '客户编码' },
      { key: 'customer_name', label: '客户名称' }, { key: 'order_date', label: '下单日期' },
      { key: 'delivery_date', label: '要求交期' }, { key: 'total_amount', label: '订单金额' },
      { key: 'customer_reference', label: '客户采购参考号' }, { key: 'payment_terms', label: '付款条件' },
      { key: 'order_status', label: '订单状态' },
    ],
    fields: [
      { key: 'salesOrderNo', label: '销售订单号', required: true }, { key: 'customerCode', label: '客户编码', required: true },
      { key: 'deliveryDate', label: '要求交期', type: 'date', required: true },
      { key: 'customerReference', label: '客户采购参考号' }, { key: 'paymentTerms', label: '付款条件' },
      { key: 'shippingTerms', label: '交付条件' }, { key: 'shipToAddress', label: '收货地址', type: 'textarea' },
      { key: 'factoryCode', label: '供货工厂' }, { key: 'sourceOpportunityNo', label: '来源商机号' },
      { key: 'sourceContractNo', label: '来源合同号' },
      { key: 'currency', label: '币种', type: 'select', options: [{ label: '人民币', value: 'CNY' }, { label: '美元', value: 'USD' }, { label: '欧元', value: 'EUR' }] },
      { key: 'taxRate', label: '税率（%）', type: 'number', min: 0, max: 100 }, { key: 'remark', label: '订单备注', type: 'textarea' },
    ],
  },
  atp: {
    title: '交期承诺（ATP）', api: '/erp/sales-orders',
    statuses: ['DRAFT', 'CREDIT_PASSED', 'CREDIT_EXCEPTION', 'ATP_COMMITTED', 'CONFIRMED', 'IN_PROD', 'DELIVERED'],
    columns: [
      { key: 'sales_order_no', label: '销售订单号' }, { key: 'customer_name', label: '客户名称' },
      { key: 'delivery_date', label: '要求交期' }, { key: 'factory_code', label: '供货工厂' },
      { key: 'credit_status', label: '信用检查' }, { key: 'atp_status', label: '交期承诺' }, { key: 'order_status', label: '订单状态' },
    ],
  },
  credits: {
    title: '信用管理', api: '/erp/credits', docKind: 'credits', statuses: ['ACTIVE', 'INACTIVE'],
    columns: [
      { key: 'customer_code', label: '客户编码' }, { key: 'credit_limit', label: '信用额度' },
      { key: 'used_amount', label: '已用额度' }, { key: 'available_credit', label: '可用额度' },
      { key: 'overdue_amount', label: '逾期金额' }, { key: 'payment_days', label: '付款天数' },
      { key: 'risk_level', label: '风险等级' }, { key: 'status', label: '状态' },
    ],
  },
  mrp: {
    title: '物料需求计划', api: '/erp/mrp/runs', saveApi: '/erp/mrp/runs', docKind: 'mrp', canCreate: true,
    statuses: ['CREATED', 'RUNNING', 'COMPLETED', 'FAILED'],
    columns: [
      { key: 'run_no', label: '计划运行编号' }, { key: 'run_type', label: '计划类型' },
      { key: 'sales_order_no', label: '销售订单号' }, { key: 'factory_code', label: '工厂' },
      { key: 'plan_date', label: '计划日期' }, { key: 'planner', label: '计划员' },
      { key: 'status', label: '运行状态' }, { key: 'remark', label: '运行备注' },
    ],
    fields: [
      { key: 'runNo', label: '计划运行编号', required: true }, { key: 'salesOrderNo', label: '销售订单号', required: true },
      { key: 'factoryCode', label: '工厂', required: true }, { key: 'planDate', label: '计划日期', type: 'date', required: true },
      { key: 'planner', label: '计划员' }, { key: 'remark', label: '运行备注', type: 'textarea' },
    ],
  },
  suggestions: {
    title: '计划建议', api: '/erp/mrp/suggestions', docKind: 'suggestions', statuses: ['PROPOSED', 'CONFIRMED'],
    columns: [
      { key: 'suggestion_no', label: '建议编号' }, { key: 'suggestion_type', label: '建议类型' },
      { key: 'material_code', label: '物料编码' }, { key: 'quantity', label: '建议数量' },
      { key: 'unit_code', label: '计量单位' }, { key: 'required_date', label: '需求日期' },
      { key: 'priority_level', label: '优先级' }, { key: 'status', label: '建议状态' },
      { key: 'converted_order_no', label: '转出单号' }, { key: 'planner_note', label: '计划备注' },
    ],
    fields: [
      { key: 'quantity', label: '建议数量', type: 'number', min: 0.0001, required: true },
      { key: 'unitCode', label: '计量单位', required: true }, { key: 'requiredDate', label: '需求日期', type: 'date', required: true },
      { key: 'priorityLevel', label: '优先级', type: 'select', options: [{ label: '紧急', value: 'URGENT' }, { label: '高', value: 'HIGH' }, { label: '普通', value: 'NORMAL' }, { label: '低', value: 'LOW' }] },
      { key: 'plannerNote', label: '计划备注', type: 'textarea' },
    ],
  },
  production: {
    title: '生产订单', api: '/erp/production-orders/p2', saveApi: '/erp/production-orders', canCreate: true,
    statuses: ['CREATED', 'RELEASED', 'IN_PROGRESS', 'FINISHED', 'CLOSED', 'CANCELED'],
    columns: [
      { key: 'prod_order_no', label: '生产订单号' }, { key: 'sales_order_no', label: '来源销售订单' },
      { key: 'product_code', label: '产品编码' }, { key: 'product_name', label: '产品名称' },
      { key: 'plan_qty', label: '计划数量' }, { key: 'plan_start_date', label: '计划开工日期' },
      { key: 'plan_finish_date', label: '计划完工日期' }, { key: 'priority_level', label: '优先级' },
      { key: 'factory_code', label: '工厂' }, { key: 'order_status', label: '订单状态' }, { key: 'kit_status', label: '齐套状态' },
    ],
    fields: [
      { key: 'prodOrderNo', label: '生产订单号', required: true }, { key: 'salesOrderNo', label: '来源销售订单号' },
      { key: 'productCode', label: '产品物料编码', required: true }, { key: 'planQty', label: '计划数量', type: 'number', min: 0.0001, required: true },
      { key: 'unitCode', label: '计量单位', required: true }, { key: 'planStartDate', label: '计划开工日期', type: 'date', required: true },
      { key: 'planFinishDate', label: '计划完工日期', type: 'date', required: true },
      { key: 'productionVersionCode', label: '生产版本编码' },
      { key: 'priorityLevel', label: '生产优先级', type: 'select', options: [{ label: '紧急', value: 'URGENT' }, { label: '高', value: 'HIGH' }, { label: '普通', value: 'NORMAL' }, { label: '低', value: 'LOW' }] },
      { key: 'factoryCode', label: '工厂' }, { key: 'workshopCode', label: '车间' }, { key: 'costCenterCode', label: '成本中心' },
      { key: 'routingCode', label: '工艺路线编码' }, { key: 'routingVersion', label: '工艺路线版本' },
      { key: 'plannerRemark', label: '计划备注', type: 'textarea' },
    ],
  },
  receivables: {
    title: '应收账款', api: '/erp/receivables', docKind: 'receivables', canCreate: true,
    statuses: ['OPEN', 'PARTIAL', 'SETTLED', 'OVERDUE', 'BAD_DEBT'],
    columns: [
      { key: 'receivable_no', label: '应收单号' }, { key: 'customer_code', label: '客户编码' },
      { key: 'customer_name', label: '客户名称' }, { key: 'sales_order_no', label: '销售订单号' },
      { key: 'invoice_no', label: '发票号' }, { key: 'invoice_date', label: '开票日期' },
      { key: 'due_date', label: '到期日' }, { key: 'invoice_amount', label: '应收金额' },
      { key: 'received_amount', label: '已回款金额' }, { key: 'outstanding_amount', label: '未回款金额' },
      { key: 'settle_status', label: '核销状态' }, { key: 'source_type', label: '来源类型' },
    ],
    fields: [
      { key: 'receivableNo', label: '应收单号', required: true }, { key: 'customerCode', label: '客户编码', required: true },
      { key: 'salesOrderNo', label: '销售订单号' }, { key: 'amount', label: '应收金额', type: 'number', min: 0.01, required: true },
      { key: 'invoiceDate', label: '业务日期', type: 'date' }, { key: 'dueDate', label: '到期日', type: 'date' },
      { key: 'paymentTerms', label: '付款条件' }, { key: 'customerReference', label: '客户采购参考号' },
      { key: 'remark', label: '应收备注', type: 'textarea' },
    ],
  },
  invoices: {
    title: '销售发票', api: '/erp/invoices', saveApi: '/erp/invoices', docKind: 'invoices', canCreate: true,
    statuses: ['DRAFT', 'ISSUED', 'APPLIED', 'CANCELED'],
    columns: [
      { key: 'invoice_no', label: '发票号' }, { key: 'sales_order_no', label: '销售订单号' },
      { key: 'customer_code', label: '客户编码' }, { key: 'invoice_date', label: '开票日期' },
      { key: 'due_date', label: '应收到期日' }, { key: 'amount', label: '开票金额' },
      { key: 'tax_amount', label: '税额' }, { key: 'payment_terms', label: '付款条件' }, { key: 'status', label: '发票状态' },
    ],
    fields: [
      { key: 'invoiceNo', label: '发票号', required: true }, { key: 'salesOrderNo', label: '销售订单号', required: true },
      { key: 'amount', label: '开票金额', type: 'number', min: 0.01, required: true },
      { key: 'taxAmount', label: '税额', type: 'number', min: 0 },
      { key: 'invoiceDate', label: '开票日期', type: 'date', required: true }, { key: 'dueDate', label: '应收到期日', type: 'date' },
      { key: 'customerReference', label: '客户采购参考号' }, { key: 'paymentTerms', label: '付款条件' },
      { key: 'externalReference', label: '外部参考号' }, { key: 'remark', label: '发票备注', type: 'textarea' },
    ],
  },
  receipts: {
    title: '回款与核销', api: '/erp/receipts', saveApi: '/erp/receipts', docKind: 'receipts', canCreate: true,
    statuses: ['DRAFT', 'UNAPPLIED', 'PARTIAL', 'APPLIED'],
    columns: [
      { key: 'receipt_no', label: '回款单号' }, { key: 'customer_code', label: '客户编码' },
      { key: 'bank_reference', label: '银行流水' }, { key: 'receipt_date', label: '回款日期' },
      { key: 'amount', label: '回款金额' }, { key: 'unapplied_amount', label: '未核销金额' },
      { key: 'payment_method', label: '付款方式' }, { key: 'deposit_account', label: '入账账户' }, { key: 'status', label: '回款状态' },
    ],
    fields: [
      { key: 'receiptNo', label: '回款单号', required: true }, { key: 'customerCode', label: '客户编码', required: true },
      { key: 'amount', label: '回款金额', type: 'number', min: 0.01, required: true },
      { key: 'receiptDate', label: '回款日期', type: 'date', required: true }, { key: 'bankReference', label: '银行流水号' },
      { key: 'paymentMethod', label: '付款方式', type: 'select', options: [{ label: '银行转账', value: 'BANK_TRANSFER' }, { label: '现金', value: 'CASH' }, { label: '支票', value: 'CHECK' }, { label: '其他', value: 'OTHER' }] },
      { key: 'depositAccount', label: '入账账户' }, { key: 'remittanceReference', label: '汇款参考号' },
      { key: 'remark', label: '回款备注', type: 'textarea' },
    ],
  },
  payables: {
    title: '应付账款', api: '/erp/payables', saveApi: '/erp/payables', docKind: 'payables', canCreate: true,
    statuses: ['OPEN', 'PARTIAL', 'PAID', 'OVERDUE'],
    columns: [
      { key: 'payable_no', label: '应付单号' }, { key: 'supplier_code', label: '供应商编码' },
      { key: 'supplier_invoice_no', label: '供应商发票号' }, { key: 'source_order_no', label: '采购来源单号' },
      { key: 'invoice_date', label: '发票日期' }, { key: 'invoice_received_date', label: '收票日期' },
      { key: 'amount', label: '应付金额' }, { key: 'paid_amount', label: '已付款金额' },
      { key: 'due_date', label: '到期日' }, { key: 'status', label: '应付状态' },
    ],
    fields: [
      { key: 'payableNo', label: '应付单号', required: true }, { key: 'supplierCode', label: '供应商编码', required: true },
      { key: 'sourceOrderNo', label: '采购来源单号' }, { key: 'amount', label: '应付金额', type: 'number', min: 0.01, required: true },
      { key: 'supplierInvoiceNo', label: '供应商发票号' }, { key: 'invoiceDate', label: '发票日期', type: 'date' },
      { key: 'invoiceReceivedDate', label: '收票日期', type: 'date' }, { key: 'dueDate', label: '付款到期日', type: 'date' },
      { key: 'paymentTerms', label: '付款条件' },
      { key: 'paymentMethod', label: '付款方式', type: 'select', options: [{ label: '银行转账', value: 'BANK_TRANSFER' }, { label: '支票', value: 'CHECK' }, { label: '其他', value: 'OTHER' }] },
      { key: 'costCenterCode', label: '成本中心' }, { key: 'remark', label: '应付备注', type: 'textarea' },
    ],
  },
  vouchers: {
    title: '业务凭证', api: '/erp/financial-vouchers', saveApi: '/erp/financial-vouchers', canCreate: true,
    statuses: ['DRAFT', 'POSTED', 'CANCELED'],
    columns: [
      { key: 'voucher_no', label: '凭证号' }, { key: 'company_code', label: '公司代码' },
      { key: 'fiscal_period', label: '会计期间' }, { key: 'voucher_date', label: '凭证日期' },
      { key: 'debit_amount', label: '借方金额' }, { key: 'credit_amount', label: '贷方金额' },
      { key: 'subject_code', label: '会计科目' }, { key: 'source_no', label: '来源单号' },
      { key: 'summary', label: '凭证摘要' }, { key: 'voucher_status', label: '凭证状态' },
    ],
    fields: [
      { key: 'voucherNo', label: '凭证号', required: true }, { key: 'companyCode', label: '公司代码', required: true },
      { key: 'fiscalPeriod', label: '会计期间（YYYY-MM）', required: true },
      { key: 'voucherDate', label: '凭证日期', type: 'date', required: true },
      { key: 'debitAmount', label: '借方金额', type: 'number', min: 0, required: true },
      { key: 'creditAmount', label: '贷方金额', type: 'number', min: 0, required: true },
      { key: 'subjectCode', label: '会计科目' }, { key: 'costCenterCode', label: '成本中心' },
      { key: 'sourceType', label: '来源类型', type: 'select', options: [{ label: '销售', value: 'SALES' }, { label: '采购', value: 'PURCHASE' }, { label: '生产', value: 'PROD' }, { label: '其他', value: 'OTHER' }] },
      { key: 'sourceNo', label: '来源单号' }, { key: 'salesOrderNo', label: '销售订单号' }, { key: 'prodOrderNo', label: '生产订单号' },
      { key: 'summary', label: '凭证摘要', type: 'textarea' }, { key: 'attachmentUrl', label: '附件链接' },
    ],
  },
}

const config = computed(() => configs[props.kind] || configs.sales)
const rows = ref<Record<string, any>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const detailDialog = ref(false)
const editingId = ref<number | null>(null)
const detail = ref<Record<string, any> | null>(null)
const form = reactive<Record<string, any>>({})
const pageError = ref('')
const formError = ref('')

const message = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback
const rowValue = (row: Record<string, any>, key: string) => {
  if (row[key] !== undefined) return row[key]
  const camel = key.replace(/_([a-z])/g, (_match, letter: string) => letter.toUpperCase())
  return row[camel]
}
const camelize = (source: Record<string, any> = {}) => Object.fromEntries(
  Object.entries(source).map(([key, value]) => [key.replace(/_([a-z])/g, (_match, letter: string) => letter.toUpperCase()), value]),
)
const recordStatus = (row: Record<string, any>) => rowValue(row, 'voucher_status') || rowValue(row, 'order_status') || rowValue(row, 'settle_status') || rowValue(row, 'status')
function displayValue(value: unknown, key = '') {
  if (value == null || value === '') return '—'
  const code = String(value).toUpperCase()
  if (key === 'source_type' || key === 'sourceType') {
    if (code === 'INVOICE') return '销售发票'
    if (code === 'MANUAL') return '手工录入'
  }
  if (key === 'payment_method' || key === 'paymentMethod') {
    if (code === 'CHECK') return '支票'
    if (code === 'OTHER') return '其他'
  }
  return erpPageValueLabels[props.kind]?.[code] || zh(value)
}
const formatValue = (value: unknown, key: string) => {
  if (value == null || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (key.endsWith('_date') || key.endsWith('Date')) return String(value).slice(0, 10)
  if (['total_amount', 'invoice_amount', 'received_amount', 'outstanding_amount', 'amount', 'tax_amount', 'taxAmount', 'debit_amount', 'credit_amount', 'credit_limit', 'used_amount', 'available_credit', 'overdue_amount', 'paid_amount', 'unapplied_amount'].includes(key)) {
    const number = Number(value)
    return Number.isFinite(number) ? number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : zh(value)
  }
  return displayValue(value, key)
}

async function load() {
  loading.value = true
  pageError.value = ''
  try {
    const { data } = await http.get(config.value.api, {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, status: status.value || undefined },
    })
    const result = data.data || {}
    rows.value = result.content || []
    total.value = Number(result.totalElements || 0)
  } catch (error) {
    pageError.value = message(error, `${config.value.title}加载失败`)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  status.value = ''
  page.value = 1
  void load()
}

function clearForm() {
  for (const key of Object.keys(form)) delete form[key]
}

function initialValues() {
  const defaults = Object.fromEntries((config.value.fields || []).map(field => [field.key, null]))
  const generated = {
    sales: { salesOrderNo: `SO-${Date.now()}`, deliveryDate: today, currency: 'CNY', taxRate: 13, sourceType: 'MANUAL', lines: [{ lineNo: 1, materialCode: '', orderQty: 1, unitPrice: 0, unitCode: 'PCS' }] },
    mrp: { runNo: `MRP-${Date.now()}`, runType: 'ORDER', factoryCode: 'F001', planDate: today },
    production: { prodOrderNo: `MO-${Date.now()}`, planQty: 1, unitCode: 'PCS', planStartDate: today, planFinishDate: today, priorityLevel: 'NORMAL' },
    invoices: { invoiceNo: `INV-${Date.now()}`, amount: 0, taxAmount: 0, invoiceDate: today, dueDate: defaultDueDate, draft: true },
    receipts: { receiptNo: `RC-${Date.now()}`, amount: 0, receiptDate: today, draft: true },
    receivables: { receivableNo: `AR-${Date.now()}`, invoiceDate: today, dueDate: defaultDueDate },
    payables: { payableNo: `AP-${Date.now()}`, amount: 0, invoiceDate: today, invoiceReceivedDate: today, dueDate: defaultDueDate },
    vouchers: { voucherNo: `V-${Date.now()}`, companyCode: 'C001', fiscalPeriod: today.slice(0, 7), voucherDate: today, debitAmount: 0, creditAmount: 0 },
    suggestions: { priorityLevel: 'NORMAL' },
  } as Record<string, Record<string, any>>
  return { ...defaults, ...(generated[props.kind] || {}) }
}

function openCreate() {
  editingId.value = null
  formError.value = ''
  clearForm()
  Object.assign(form, initialValues())
  dialog.value = true
}

function detailApi(row: Record<string, any>) {
  const id = rowValue(row, 'id')
  if (props.kind === 'sales' || props.kind === 'atp') return `/erp/sales-orders/${id}`
  if (props.kind === 'production') return `/erp/production-orders/${id}`
  if (props.kind === 'vouchers') return `/erp/financial-vouchers/${id}`
  return `/erp/documents/${config.value.docKind}/${id}`
}

async function openEdit(row: Record<string, any>) {
  editingId.value = Number(rowValue(row, 'id'))
  formError.value = ''
  pageError.value = ''
  try {
    const { data } = await http.get(detailApi(row))
    const value = data.data || {}
    clearForm()
    if (props.kind === 'sales') {
      Object.assign(form, camelize(value.header || {}))
      form.lines = (value.lines || []).map((line: Record<string, any>) => camelize(line))
    } else {
      Object.assign(form, camelize(value))
      if (props.kind === 'receivables') form.amount = form.invoiceAmount
    }
    if (props.kind === 'invoices' || props.kind === 'receipts') form.draft = true
    dialog.value = true
  } catch (error) {
    pageError.value = message(error, `${config.value.title}详情读取失败`)
  }
}

async function openDetail(row: Record<string, any>) {
  pageError.value = ''
  try {
    const { data } = await http.get(detailApi(row))
    const value = data.data || {}
    detail.value = props.kind === 'sales'
      ? { ...camelize(value.header || {}), lines: (value.lines || []).map((line: Record<string, any>) => camelize(line)) }
      : camelize(value)
    detailDialog.value = true
  } catch (error) {
    pageError.value = message(error, `${config.value.title}详情读取失败`)
  }
}

const detailEntries = computed(() => {
  if (!detail.value) return []
  return config.value.columns.map(column => ({
    label: column.label,
    value: formatValue(rowValue(detail.value as Record<string, any>, column.key), column.key),
  }))
})

function payload() {
  const result: Record<string, any> = {}
  for (const field of config.value.fields || []) result[field.key] = form[field.key]
  if (props.kind === 'sales') {
    result.lines = (form.lines || []).map((line: Record<string, any>, index: number) => ({
      lineNo: Number(line.lineNo || index + 1), materialCode: line.materialCode, orderQty: Number(line.orderQty),
      unitPrice: Number(line.unitPrice), unitCode: line.unitCode || 'PCS',
      customerMaterialCode: line.customerMaterialCode || null, remark: line.remark || null,
    }))
  }
  if (props.kind === 'invoices' || props.kind === 'receipts') result.draft = true
  return result
}

function validate() {
  const missing = (config.value.fields || []).find(field => field.required && (form[field.key] == null || String(form[field.key]).trim() === ''))
  if (missing) return `请填写${missing.label}`
  if (props.kind === 'sales') {
    if (!form.lines?.length || form.lines.some((line: Record<string, any>) => !line.materialCode || Number(line.orderQty) <= 0 || Number(line.unitPrice) < 0)) return '请填写有效的销售订单明细'
  }
  if (props.kind === 'vouchers') {
    if (Number(form.debitAmount) <= 0 || Number(form.creditAmount) <= 0) return '凭证借贷金额必须大于0'
    if (Number(form.debitAmount) !== Number(form.creditAmount)) return '借方金额必须等于贷方金额'
  }
  if (props.kind === 'production' && String(form.planFinishDate) < String(form.planStartDate)) return '计划完工日期不能早于计划开工日期'
  return ''
}

async function save() {
  formError.value = validate()
  if (formError.value) {
    ElMessage.error(formError.value)
    return
  }
  saving.value = true
  try {
    const body = payload()
    if (editingId.value) {
      if (props.kind === 'sales' || props.kind === 'production' || props.kind === 'vouchers') {
        await http.put(`${config.value.saveApi}/${editingId.value}`, body)
      } else {
        await http.put(`/erp/documents/${config.value.docKind}/${editingId.value}`, body)
      }
    } else {
      await http.post(config.value.saveApi || config.value.api, body)
    }
    ElMessage.success(editingId.value ? '修改已保存' : '单据已创建')
    dialog.value = false
    page.value = 1
    await load()
  } catch (error) {
    formError.value = message(error, '保存失败，请检查填写内容')
  } finally {
    saving.value = false
  }
}

function editable(row: Record<string, any>) {
  const state = String(recordStatus(row) || '')
  if (props.kind === 'sales') return state === 'DRAFT'
  if (props.kind === 'production') return state === 'CREATED'
  if (props.kind === 'mrp') return state === 'CREATED'
  if (props.kind === 'suggestions') return state === 'PROPOSED' && !rowValue(row, 'converted_order_no')
  if (props.kind === 'invoices' || props.kind === 'receipts') return state === 'DRAFT'
  if (props.kind === 'receivables') return state === 'OPEN' && rowValue(row, 'source_type') === 'MANUAL' && Number(rowValue(row, 'received_amount') || 0) === 0
  if (props.kind === 'payables') return state === 'OPEN' && Number(rowValue(row, 'paid_amount') || 0) === 0
  if (props.kind === 'vouchers') return state === 'DRAFT'
  return false
}

function detailOnlyDeleteApi(row: Record<string, any>) {
  const id = rowValue(row, 'id')
  if (props.kind === 'sales') return `/erp/sales-orders/${id}`
  if (props.kind === 'production') return `/erp/production-orders/${id}`
  if (props.kind === 'vouchers') return `/erp/financial-vouchers/${id}`
  return `/erp/documents/${config.value.docKind}/${id}`
}

async function deleteRow(row: Record<string, any>) {
  const number = rowValue(row, 'sales_order_no') || rowValue(row, 'prod_order_no') || rowValue(row, 'run_no') || rowValue(row, 'suggestion_no') || rowValue(row, 'invoice_no') || rowValue(row, 'receipt_no') || rowValue(row, 'receivable_no') || rowValue(row, 'payable_no') || rowValue(row, 'voucher_no')
  try {
    await ElMessageBox.confirm(`确认删除草稿单据 ${number || ''}？`, '删除确认', { type: 'warning' })
    await http.delete(detailOnlyDeleteApi(row))
    ElMessage.success('单据已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') pageError.value = message(error, '删除失败')
  }
}

async function perform(path: string, label: string, body: Record<string, any> = {}) {
  pageError.value = ''
  try {
    await http.post(`/erp/${path}`, body)
    ElMessage.success(`${label}成功`)
    await load()
  } catch (error) {
    pageError.value = message(error, `${label}失败`)
  }
}

async function promptAction(row: Record<string, any>, path: string, label: string, key = 'reason') {
  try {
    const result = await ElMessageBox.prompt(`请输入${label}说明`, label, { inputType: 'textarea', inputPlaceholder: '可填写原因或补充说明' })
    await perform(path.replace('{id}', String(rowValue(row, 'id'))), label, { [key]: result.value })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') pageError.value = message(error, `${label}失败`)
  }
}

async function postDocument(row: Record<string, any>, kind: 'invoices' | 'receipts' | 'vouchers') {
  const id = rowValue(row, 'id')
  const paths = {
    invoices: `/erp/documents/invoices/${id}/issue`,
    receipts: `/erp/documents/receipts/${id}/post`,
    vouchers: `/erp/financial-vouchers/${id}/post`,
  }
  const labels = { invoices: '确认开票', receipts: '登记回款', vouchers: '凭证过账' }
  try {
    await ElMessageBox.confirm(`确认${labels[kind]}？该操作会锁定当前单据。`, `${labels[kind]}确认`, { type: 'warning' })
    pageError.value = ''
    await http.post(paths[kind])
    ElMessage.success(`${labels[kind]}成功`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') pageError.value = message(error, `${labels[kind]}失败`)
  }
}

async function settle(row: Record<string, any>) {
  try {
    const receivable = await ElMessageBox.prompt('请输入应收单记录编号', '回款核销', { inputType: 'number', inputPattern: /^\d+$/, inputErrorMessage: '请输入有效的应收单记录编号' })
    const amount = await ElMessageBox.prompt('请输入本次核销金额', '回款核销', { inputType: 'number', inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入有效金额' })
    await perform('settlements', '回款核销', { receiptId: rowValue(row, 'id'), receivableId: Number(receivable.value), amount: Number(amount.value) })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') pageError.value = message(error, '回款核销失败')
  }
}

function addLine() {
  form.lines.push({ lineNo: form.lines.length + 1, materialCode: '', orderQty: 1, unitPrice: 0, unitCode: 'PCS' })
}

function removeLine(index: number) {
  form.lines.splice(index, 1)
  form.lines.forEach((line: Record<string, any>, lineIndex: number) => { line.lineNo = lineIndex + 1 })
}

watch(() => props.kind, () => {
  page.value = 1
  keyword.value = ''
  status.value = ''
  pageError.value = ''
  void load()
})
onMounted(load)
</script>

<template>
  <section class="page">
    <div class="head">
      <div>
        <span>企业资源计划业务</span>
        <h1>{{ config.title }}</h1>
        <p>中文单据字段与状态展示；可维护记录按业务规则编辑或删除，过账或进入下游流程后会锁定。</p>
      </div>
      <el-button v-if="config.canCreate" type="primary" @click="openCreate">新增{{ config.title }}</el-button>
    </div>

    <el-alert v-if="pageError" class="page-error" type="error" :closable="true" show-icon :title="pageError" @close="pageError = ''" />

    <el-card shadow="never">
      <div class="bar">
        <el-input v-model="keyword" clearable placeholder="输入单号、客户、物料或供应商" @keyup.enter="page = 1; load()" />
        <el-select v-if="config.statuses?.length" v-model="status" clearable placeholder="全部状态" @change="page = 1; load()">
          <el-option v-for="item in config.statuses" :key="item" :label="displayValue(item, 'status')" :value="item" />
        </el-select>
        <el-button @click="page = 1; load()">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无数据">
        <el-table-column v-for="column in config.columns" :key="column.key" :label="column.label" :min-width="column.width || 130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="['order_status', 'status', 'settle_status', 'voucher_status', 'credit_status', 'atp_status', 'kit_status'].includes(column.key)" :type="['CANCELED', 'CLOSED', 'FAILED', 'BAD_DEBT'].includes(String(rowValue(row, column.key))) ? 'danger' : ['DRAFT', 'PROPOSED', 'OPEN', 'CREATED', 'PARTIAL', 'RUNNING', 'CREDIT_EXCEPTION'].includes(String(rowValue(row, column.key))) ? 'warning' : 'success'">
              {{ displayValue(rowValue(row, column.key), column.key) }}
            </el-tag>
            <span v-else>{{ formatValue(rowValue(row, column.key), column.key) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="350" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="config.fields?.length && editable(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="config.fields?.length && editable(row) && ['sales', 'production', 'mrp', 'suggestions', 'invoices', 'receipts', 'receivables', 'payables', 'vouchers'].includes(kind)" link type="danger" @click="deleteRow(row)">{{ kind === 'receivables' || kind === 'payables' ? '删除' : '删除草稿' }}</el-button>

            <template v-if="kind === 'sales'">
              <el-button link @click="perform(`sales-orders/${row.id}/credit-check`, '信用检查')">信用检查</el-button>
              <el-button link @click="perform(`sales-orders/${row.id}/atp`, '交期计算')">交期计算</el-button>
              <el-button link type="primary" @click="promptAction(row, `sales-orders/{id}/atp/accept`, '确认交期')">确认交期</el-button>
              <el-button link type="warning" @click="promptAction(row, `sales-orders/{id}/credit-exception`, '信用例外审批')">信用例外</el-button>
              <el-button link type="primary" @click="perform(`sales-orders/${row.id}/confirm`, '确认订单')">确认</el-button>
              <el-button v-if="recordStatus(row) !== 'CANCELED' && recordStatus(row) !== 'CLOSED'" link type="danger" @click="promptAction(row, `sales-orders/{id}/cancel`, '取消订单')">取消</el-button>
            </template>
            <template v-else-if="kind === 'atp'">
              <el-button link @click="perform(`sales-orders/${row.id}/atp`, '交期计算')">交期计算</el-button>
              <el-button link type="primary" @click="promptAction(row, `sales-orders/{id}/atp/accept`, '确认交期')">确认交期</el-button>
            </template>
            <el-button v-else-if="kind === 'mrp' && recordStatus(row) === 'CREATED'" link type="primary" @click="perform(`mrp/runs/${row.id}/execute`, '运行计划')">运行计划</el-button>
            <el-button v-else-if="kind === 'suggestions' && recordStatus(row) === 'PROPOSED'" link type="primary" @click="perform(`mrp/suggestions/${row.id}/confirm`, '确认建议')">确认建议</el-button>
            <el-button v-else-if="kind === 'production' && recordStatus(row) === 'CREATED'" link @click="perform(`production-orders/${row.id}/kit-check`, '齐套检查')">齐套检查</el-button>
            <el-button v-if="kind === 'production' && recordStatus(row) === 'CREATED'" link type="primary" @click="perform(`production-orders/${row.id}/release`, '下达生产订单')">下达</el-button>
            <el-button v-if="kind === 'receipts' && ['UNAPPLIED', 'PARTIAL'].includes(String(recordStatus(row)))" link type="primary" @click="settle(row)">核销应收</el-button>
            <el-button v-if="kind === 'invoices' && recordStatus(row) === 'DRAFT'" link type="primary" @click="postDocument(row, 'invoices')">确认开票</el-button>
            <el-button v-if="kind === 'receipts' && recordStatus(row) === 'DRAFT'" link type="primary" @click="postDocument(row, 'receipts')">登记回款</el-button>
            <el-button v-if="kind === 'vouchers' && recordStatus(row) === 'DRAFT'" link type="primary" @click="postDocument(row, 'vouchers')">过账</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" @change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId ? '编辑' : '新增'}${config.title}`" :width="kind === 'sales' ? '850px' : '680px'" destroy-on-close>
      <el-alert v-if="formError" class="form-error" type="error" :closable="true" show-icon :title="formError" @close="formError = ''" />
      <el-form label-position="top" class="document-form">
        <el-form-item v-for="field in config.fields || []" :key="field.key" :label="`${field.label}${field.required ? '' : '（选填）'}`" :required="field.required">
          <el-select v-if="field.type === 'select'" v-model="form[field.key]" clearable :placeholder="`请选择${field.label}`">
            <el-option v-for="option in field.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
          </el-select>
          <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" type="date" value-format="YYYY-MM-DD" :placeholder="`请选择${field.label}`" />
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="field.min ?? 0" :max="field.max" :precision="4" :controls="true" />
          <el-input v-else-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="3" :placeholder="`请输入${field.label}`" />
          <el-input v-else v-model="form[field.key]" :disabled="editingId !== null && ['salesOrderNo', 'prodOrderNo'].includes(field.key)" :placeholder="`请输入${field.label}`" />
        </el-form-item>
      </el-form>

      <div v-if="kind === 'sales'" class="lines-head">
        <strong>销售订单明细</strong>
        <el-button link type="primary" @click="addLine">新增明细</el-button>
      </div>
      <el-table v-if="kind === 'sales'" :data="form.lines || []" border size="small" class="lines-table">
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column label="物料编码" min-width="155"><template #default="{ row }"><el-input v-model="row.materialCode" placeholder="物料编码" /></template></el-table-column>
        <el-table-column label="数量" width="130"><template #default="{ row }"><el-input-number v-model="row.orderQty" :min="0.0001" :precision="4" /></template></el-table-column>
        <el-table-column label="单价" width="130"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="4" /></template></el-table-column>
        <el-table-column label="单位" width="100"><template #default="{ row }"><el-input v-model="row.unitCode" /></template></el-table-column>
        <el-table-column label="客户物料号（选填）" min-width="150"><template #default="{ row }"><el-input v-model="row.customerMaterialCode" /></template></el-table-column>
        <el-table-column label="行备注（选填）" min-width="150"><template #default="{ row }"><el-input v-model="row.remark" /></template></el-table-column>
        <el-table-column label="操作" width="75"><template #default="{ $index }"><el-button link type="danger" @click="removeLine($index)">移除</el-button></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ kind === 'invoices' || kind === 'receipts' ? '保存草稿' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" :title="`${config.title}详情`" width="780px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item v-for="entry in detailEntries" :key="entry.label" :label="entry.label">{{ entry.value }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="kind === 'sales' && detail?.lines?.length">
        <h3 class="detail-lines-title">订单明细</h3>
        <el-table :data="detail.lines" border size="small">
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="materialCode" label="物料编码" />
          <el-table-column prop="materialName" label="物料名称" />
          <el-table-column prop="orderQty" label="订购数量" />
          <el-table-column prop="unitCode" label="计量单位" />
          <el-table-column prop="unitPrice" label="单价" />
          <el-table-column prop="customerMaterialCode" label="客户物料号" />
          <el-table-column prop="remark" label="行备注" />
        </el-table>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page{max-width:1500px;margin:auto}.head{display:flex;align-items:end;justify-content:space-between;margin-bottom:16px}.head span{font-size:11px;color:#3976d5}.head h1{margin:5px 0;color:#263f5c}.head p{font-size:12px;color:#8391a4}.bar{display:flex;gap:8px;margin-bottom:14px}.bar .el-input{width:300px}.bar .el-select{width:180px}.page-error,.form-error{margin-bottom:14px}.document-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.document-form :deep(.el-select),.document-form :deep(.el-date-editor){width:100%}.document-form :deep(.el-input-number){width:100%}.lines-head{display:flex;align-items:center;justify-content:space-between;margin:12px 0 8px}.lines-table{margin-bottom:12px}.detail-lines-title{margin:18px 0 8px;color:#263f5c}
</style>
