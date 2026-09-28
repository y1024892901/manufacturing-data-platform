<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import TablePager from '../shared/components/TablePager.vue'
import { showErrorDialog } from '../shared/errorDialog'
import { zh, zhKey } from '../shared/display'

type SelectOption = { label: string; value: any }
type Field = { key: string; label: string; type?: 'text' | 'number' | 'date' | 'datetime' | 'select' | 'textarea'; options?: (string | SelectOption)[]; required?: boolean; placeholder?: string; lockedOnEdit?: boolean; defaultValue?: unknown }
type Action = { label: string; path: (row: any) => string; tone?: 'primary' | 'success' | 'warning' | 'danger'; prompt?: { label: string; key: string; hint: string }; bodyPrompt?: boolean; visible?: (row: any) => boolean }
type Workspace = { title: string; eyebrow: string; subtitle: string; url: string; cols: string[]; fields: Field[]; createText: string; links: [string, string][]; actions?: Action[]; compose?: (form: Record<string, any>) => any; reference?: { label: string; url: string; note: string }; canCreate?: boolean; canRead?: boolean; canEdit?: boolean; canDelete?: boolean }

const props = defineProps<{ kind: string }>()
const localToday = new Date()
const today = `${localToday.getFullYear()}-${String(localToday.getMonth() + 1).padStart(2, '0')}-${String(localToday.getDate()).padStart(2, '0')}`
const baseFields = (items: Field[]) => items
const mesWorkOrderFields = (): Field[] => baseFields([
  { key: 'workOrderNo', label: '工单号', required: true },
  { key: 'prodOrderNo', label: '生产订单号', required: true },
  { key: 'productCode', label: '产品编码', required: true },
  { key: 'opSeq', label: '工序序号', type: 'number', required: true },
  { key: 'operationCode', label: '工序编码', required: true },
  { key: 'operationName', label: '工序名称', required: true },
  { key: 'planQty', label: '计划数量', type: 'number', required: true },
  { key: 'routingCode', label: '工艺路线编码（选填）' },
  { key: 'routingVersion', label: '工艺路线版本（选填）' },
  { key: 'workCenter', label: '工作中心（选填）' },
  { key: 'workshopCode', label: '车间编码（选填）' },
  { key: 'equipmentCode', label: '设备编码（选填）' },
  { key: 'workshopUser', label: '班组负责人账号（选填）' },
  { key: 'planStartTime', label: '计划开工时间（选填）', type: 'datetime' },
  { key: 'planEndTime', label: '计划完工时间（选填）', type: 'datetime' },
  { key: 'planHours', label: '计划工时（选填）', type: 'number' },
  { key: 'priorityLevel', label: '优先级（选填）', type: 'select', options: [
    { label: '普通', value: 'NORMAL' }, { label: '低', value: 'LOW' }, { label: '高', value: 'HIGH' }, { label: '紧急', value: 'URGENT' }
  ], defaultValue: 'NORMAL' },
  { key: 'batchNo', label: '生产批次（选填）' },
  { key: 'shiftCode', label: '计划班次（选填）', type: 'select', options: [
    { label: '白班', value: 'DAY' }, { label: '夜班', value: 'NIGHT' }
  ] },
  { key: 'remark', label: '工单补充说明（选填）', type: 'textarea' }
])
const configs: Record<string, Workspace> = {
  mdmCustomers: {
    title: '客户主数据', eyebrow: 'MDM · CUSTOMER GOVERNANCE', subtitle: '客户档案、信用额度和结算条件由主数据平台统一管控，CRM 与 ERP 只能消费已发布记录。', url: '/mdm/customers', cols: ['customerCode', 'customerName', 'customerLevel', 'region', 'creditLimit', 'status'], createText: '新建客户', links: [['物料主数据', '/mdm/materials'], ['供应商主数据', '/ops/mdmSuppliers']],
    fields: baseFields([{ key: 'customerCode', label: '客户编码', required: true }, { key: 'customerName', label: '客户名称', required: true }, { key: 'customerLevel', label: '客户等级', type: 'select', options: ['A', 'B', 'C'] }, { key: 'region', label: '销售区域' }, { key: 'creditLimit', label: '信用额度', type: 'number' }, { key: 'paymentTerms', label: '结算条件', placeholder: 'NET30' }]), actions: [{ label: '提交审批', path: r => `/mdm/customers/${r.id}/submit`, tone: 'primary' }]
  },
  mdmSuppliers: {
    title: '供应商主数据', eyebrow: 'MDM · SUPPLIER GOVERNANCE', subtitle: '供应商资质、交期能力与质量绩效由统一主数据管理，采购单据只可选择发布供应商。', url: '/mdm/suppliers', cols: ['supplierCode', 'supplierName', 'supplierLevel', 'leadTimeDays', 'qualStatus', 'status'], createText: '新建供应商', links: [['客户主数据', '/ops/mdmCustomers'], ['采购订单', '/ops/srmPo']],
    fields: baseFields([{ key: 'supplierCode', label: '供应商编码', required: true }, { key: 'supplierName', label: '供应商名称', required: true }, { key: 'supplierLevel', label: '供应商等级', type: 'select', options: ['A', 'B', 'C'] }, { key: 'leadTimeDays', label: '标准提前期（天）', type: 'number' }, { key: 'qualStatus', label: '资质状态', type: 'select', options: ['VALID', 'EXPIRING', 'EXPIRED'] }, { key: 'paymentTerms', label: '结算条件', placeholder: 'NET30' }]), actions: [{ label: '提交审批', path: r => `/mdm/suppliers/${r.id}/submit`, tone: 'primary' }]
  },
  crm: {
    title: 'CRM 商机经营', eyebrow: 'CRM · OPPORTUNITY PIPELINE', subtitle: '从线索、资格审查、方案、谈判到赢单；只有赢单的需求才应进入 ERP 订单。', url: '/crm/opportunities', cols: ['opportunityNo', 'opportunityName', 'customerName', 'expectAmount', 'stageCode', 'ownerName'], createText: '登记商机', links: [['客户主数据', '/ops/mdmCustomers'], ['销售订单', '/ops/erp']],
    fields: baseFields([{ key: 'opportunityNo', label: '商机编号', required: true }, { key: 'opportunityName', label: '商机名称', required: true }, { key: 'customerCode', label: '客户编码', required: true }, { key: 'expectAmount', label: '预计金额', type: 'number' }, { key: 'expectSignDate', label: '预计签约日', type: 'date' }, { key: 'winRate', label: '赢单概率（0-1）', type: 'number' }]), actions: [{ label: '推进阶段', path: r => `/crm/opportunities/${r.id}/stage`, tone: 'primary', prompt: { label: '目标阶段', key: 'code', hint: '输入 LEAD / QUALIFY / PROPOSAL / NEGOTIATE / WON / LOST' } }, { label: '登记跟进', path: r => `/crm/opportunities/${r.id}/follows`, tone: 'success', prompt: { label: '跟进方式', key: 'followType', hint: '输入 CALL / VISIT / EMAIL / MEETING' }, bodyPrompt: true }]
  },
  erp: {
    title: 'ERP 销售订单', eyebrow: 'ERP · ORDER TO CASH', subtitle: '已确认订单代表正式交付承诺，订单行引用的是 MDM 已发布物料而非自行维护的物料。', url: '/erp/sales-orders', cols: ['salesOrderNo', 'customerName', 'deliveryDate', 'totalAmount', 'status'], createText: '新建销售订单', links: [['CRM 商机', '/ops/crm'], ['生产订单', '/ops/production']],
    fields: baseFields([{ key: 'salesOrderNo', label: '销售订单号', required: true }, { key: 'customerCode', label: '客户编码', required: true }, { key: 'deliveryDate', label: '交付日期', type: 'date', required: true }, { key: 'materialCode', label: '物料编码', required: true }, { key: 'orderQty', label: '订单数量', type: 'number', required: true }, { key: 'unitPrice', label: '含税单价', type: 'number', required: true }, { key: 'unitCode', label: '单位', placeholder: 'PCS', required: true }]),
    compose: f => ({ salesOrderNo: f.salesOrderNo, customerCode: f.customerCode, deliveryDate: f.deliveryDate, remark: f.remark, lines: [{ lineNo: 1, materialCode: f.materialCode, orderQty: f.orderQty, unitPrice: f.unitPrice, unitCode: f.unitCode }] }), actions: [{ label: '确认订单', path: r => `/erp/sales-orders/${r.id}/confirm`, tone: 'success' }]
  },
  erpFinance: {
    title: 'ERP 财务凭证', eyebrow: 'ERP · FINANCE CONTROL', subtitle: '业务单据的资金影响由借贷平衡的财务凭证记录，可关联订单、采购和生产等来源单号。', url: '/erp/financial-vouchers', cols: ['voucherNo', 'companyCode', 'fiscalPeriod', 'voucherDate', 'debitAmount', 'creditAmount', 'sourceNo'], createText: '录入会计凭证', links: [['销售订单', '/ops/erp'], ['生产订单', '/ops/production']],
    fields: baseFields([{ key: 'voucherNo', label: '凭证号', required: true }, { key: 'companyCode', label: '公司编码', required: true }, { key: 'fiscalPeriod', label: '会计期间', placeholder: '2026-09', required: true }, { key: 'voucherDate', label: '凭证日期', type: 'date' }, { key: 'debitAmount', label: '借方金额', type: 'number', required: true }, { key: 'creditAmount', label: '贷方金额', type: 'number', required: true }, { key: 'subjectCode', label: '科目编码' }, { key: 'costCenterCode', label: '成本中心' }, { key: 'sourceType', label: '来源类型', placeholder: 'SALES_ORDER' }, { key: 'sourceNo', label: '来源单号' }, { key: 'summary', label: '摘要' }])
  },
  production: {
    title: 'ERP 生产订单', eyebrow: 'ERP · PLAN TO PRODUCE', subtitle: '生产计划锁定产品、数量与 BOM 版本；下达后由 MES 拆分为车间可执行工单。', url: '/erp/production-orders', cols: ['prodOrderNo', 'productName', 'planQty', 'bomVersion', 'planStartDate', 'status'], createText: '创建生产订单', links: [['销售订单', '/ops/erp'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'prodOrderNo', label: '生产订单号', required: true }, { key: 'productCode', label: '产品物料编码', required: true }, { key: 'planQty', label: '计划数量', type: 'number', required: true }, { key: 'unitCode', label: '单位', placeholder: 'PCS', required: true }, { key: 'planStartDate', label: '计划开工日', type: 'date', required: true }, { key: 'planFinishDate', label: '计划完工日', type: 'date', required: true }, { key: 'bomCode', label: 'BOM 编码（可选）' }]), actions: [{ label: '下达 MES', path: r => `/erp/production-orders/${r.id}/release`, tone: 'success' }]
  },
  mes: {
    title: '生产工单', eyebrow: '制造执行 · 工单管理', subtitle: '维护工序、计划数量、工艺路线、设备与班次；开工后由报工明细记录产量并回写工单进度。', url: '/mes/work-orders', cols: ['workOrderNo', 'prodOrderNo', 'productCode', 'operationName', 'planQty', 'completedQty', 'qualifiedQty', 'status'], createText: '创建工序工单', links: [['ERP 生产订单', '/ops/production'], ['质量检验', '/ops/qms'], ['设备故障', '/ops/eamFault']], fields: mesWorkOrderFields(), canRead: true, canEdit: true, canDelete: true,
    reference: { label: '制造工单与工序字段说明', url: 'https://www.odoo.com/documentation/master/applications/inventory_and_mrp/manufacturing/basic_setup/manufacturing_work_orders.html', note: '产品、生产订单、工序、工作中心与数量按制造工单参考设计；批次和班次保留为可选现场字段。' },
    actions: [
      { label: '开工', path: r => `/mes/work-orders/${r.id}/start`, tone: 'primary', visible: r => ['CREATED', 'RELEASED'].includes(r.status) },
      { label: '快速报工', path: r => `/mes/work-orders/${r.id}/report`, tone: 'success', prompt: { label: '合格数量', key: 'qualifiedQty', hint: '请输入本次合格数量；报废数量按 0 记入' }, visible: r => r.status === 'STARTED' }
    ]
  },
  mesDispatch: {
    title: '派工排产', eyebrow: '制造执行 · 资源派工', subtitle: '为未开工工单安排车间、班组负责人、工作中心、设备、班次与计划时间。排产信息保存后可从工单页开工。', url: '/mes/work-orders', cols: ['workOrderNo', 'prodOrderNo', 'operationName', 'workshopCode', 'workCenter', 'equipmentCode', 'workshopUser', 'planStartTime', 'planEndTime', 'priorityLevel', 'status'], createText: '派工', links: [['生产工单', '/mes/work-orders'], ['报工明细', '/mes/reports'], ['Andon 异常', '/mes/andon']], fields: mesWorkOrderFields(), canCreate: false, canRead: true, canEdit: true,
    reference: { label: '工作中心与产能字段说明', url: 'https://www.odoo.com/documentation/16.0/applications/inventory_and_mrp/manufacturing/management/using_work_centers.html', note: '工作中心、设备、计划时段、工时与优先级用于派工和产能安排；排产备注、批次、班次为可选字段。' },
    actions: [{ label: '开工', path: r => `/mes/work-orders/${r.id}/start`, tone: 'primary', visible: r => ['CREATED', 'RELEASED'].includes(r.status) }]
  },
  mesReports: {
    title: '报工明细', eyebrow: '制造执行 · 生产确认', subtitle: '记录工单每次产出、合格品、报废品、班次、操作人、设备、作业时段与停机信息；报工变更会重新计算工单汇总。', url: '/mes/reports', cols: ['reportNo', 'workOrderNo', 'prodOrderNo', 'reportQty', 'qualifiedQty', 'scrapQty', 'reportDate', 'shiftCode', 'operatorCode'], createText: '登记报工明细', links: [['生产工单', '/ops/mes'], ['派工排产', '/mes/dispatch'], ['质量检验', '/ops/qms']], canRead: true, canEdit: true, canDelete: true,
    reference: { label: '生产确认字段说明', url: 'https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/25a41481f62e469ba0e61015a0d39d20/2d6b6b54cd410e4ee10000000a423f68.html', note: '确认单关联生产订单、工序与工作中心，并登记产量、日期和人员；作业时间、班次、设备和停机原因作为可选明细。' },
    fields: baseFields([
      { key: 'reportNo', label: '报工单号', required: true },
      { key: 'workOrderNo', label: 'MES 工单号', required: true, lockedOnEdit: true },
      { key: 'qualifiedQty', label: '合格数量', type: 'number', required: true },
      { key: 'scrapQty', label: '报废数量（选填）', type: 'number', defaultValue: 0 },
      { key: 'reportDate', label: '报工日期', type: 'date', defaultValue: today },
      { key: 'shiftCode', label: '班次（选填）', type: 'select', options: [{ label: '白班', value: 'DAY' }, { label: '夜班', value: 'NIGHT' }] },
      { key: 'startTime', label: '开始时间（选填）', type: 'datetime' },
      { key: 'endTime', label: '结束时间（选填）', type: 'datetime' },
      { key: 'workHours', label: '作业工时（选填）', type: 'number' },
      { key: 'equipmentCode', label: '设备编码（选填）' },
      { key: 'paused', label: '因异常暂停（选填）', type: 'select', options: [{ label: '否', value: 'false' }, { label: '是', value: 'true' }], defaultValue: 'false' },
      { key: 'pauseReason', label: '停机原因（选填）', type: 'textarea' },
      { key: 'pauseMinutes', label: '停机分钟数（选填）', type: 'number', defaultValue: 0 },
      { key: 'remark', label: '报工补充说明（选填）', type: 'textarea' }
    ])
  },
  mesAndon: {
    title: 'Andon 异常', eyebrow: '制造执行 · 现场异常闭环', subtitle: '登记缺料、设备、质量、工艺与安全异常，记录影响数量、停线时长和响应处理过程。异常按待响应、已响应、已处理、已关闭闭环。', url: '/mes/andon-events', cols: ['eventNo', 'eventType', 'severity', 'workOrderNo', 'equipmentCode', 'description', 'affectedQty', 'downtimeMinutes', 'reportedAt', 'status'], createText: '提报异常', links: [['生产工单', '/ops/mes'], ['派工排产', '/mes/dispatch'], ['报工明细', '/mes/reports']], canRead: true, canEdit: true, canDelete: true,
    reference: { label: '生产现场质量与工作中心参考', url: 'https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/manufacturing/shop_floor/shop_floor_overview.html', note: '现场卡片关联工单、产品、数量、工作中心与执行状态；异常影响数量、设备、响应时限与处理方案补充闭环信息。' },
    fields: baseFields([
      { key: 'eventNo', label: '异常单号', required: true },
      { key: 'eventType', label: '异常类型', type: 'select', required: true, options: [
        { label: '缺料', value: 'MATERIAL' }, { label: '设备异常', value: 'EQUIPMENT' }, { label: '质量异常', value: 'QUALITY' }, { label: '工艺异常', value: 'PROCESS' }, { label: '安全异常', value: 'SAFETY' }, { label: '其他异常', value: 'OTHER' }
      ] },
      { key: 'severity', label: '异常等级', type: 'select', required: true, defaultValue: 'MEDIUM', options: [
        { label: '一般', value: 'NORMAL' }, { label: '中', value: 'MEDIUM' }, { label: '高', value: 'HIGH' }, { label: '紧急', value: 'URGENT' }
      ] },
      { key: 'description', label: '异常描述', type: 'textarea', required: true },
      { key: 'workOrderNo', label: '关联 MES 工单（选填）' },
      { key: 'prodOrderNo', label: '关联生产订单（选填）' },
      { key: 'equipmentCode', label: '设备编码（选填）' },
      { key: 'workshopCode', label: '车间编码（选填）' },
      { key: 'affectedQty', label: '影响数量（选填）', type: 'number' },
      { key: 'downtimeMinutes', label: '停线分钟数（选填）', type: 'number' },
      { key: 'responseDueAt', label: '期望响应时间（选填）', type: 'datetime' }
    ]),
    actions: [
      { label: '响应', path: r => `/mes/andon-events/${r.id}/respond`, tone: 'primary', prompt: { label: '响应说明', key: 'responseNote', hint: '请输入响应说明' }, bodyPrompt: true, visible: r => r.status === 'OPEN' },
      { label: '完成处理', path: r => `/mes/andon-events/${r.id}/resolve`, tone: 'success', prompt: { label: '处理方案与结果', key: 'resolution', hint: '请说明处理措施和结果' }, bodyPrompt: true, visible: r => r.status === 'RESPONDED' },
      { label: '关闭', path: r => `/mes/andon-events/${r.id}/close`, tone: 'success', visible: r => r.status === 'RESOLVED' }
    ]
  },
  qms: {
    title: 'QMS 质量检验', eyebrow: 'QMS · QUALITY CONTROL', subtitle: '来料、过程、成品检验的结论会驱动不合格品隔离、返工、报废或让步接收。', url: '/qms/inspections', cols: ['inspectionNo', 'inspectionType', 'materialCode', 'inspectedQty', 'defectRate', 'result'], createText: '创建检验单', links: [['不合格品处置', '/ops/qmsDefect'], ['供应商到货', '/ops/srmDelivery'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'inspectionNo', label: '检验单号', required: true }, { key: 'inspectionType', label: '检验类型', type: 'select', options: ['IQC', 'IPQC', 'FQC', 'OQC'], required: true }, { key: 'materialCode', label: '物料编码', required: true }, { key: 'materialName', label: '物料名称' }, { key: 'batchNo', label: '批次号' }, { key: 'inspectedQty', label: '送检数量', type: 'number', required: true }, { key: 'workOrderNo', label: 'MES 工单号' }]), actions: [{ label: '录入判定', path: r => `/qms/inspections/${r.id}/judge`, tone: 'success', prompt: { label: '合格数量', key: 'qualifiedQty', hint: '输入合格数；不合格数由送检数自动补齐，结论为 PASSED' } }]
  },
  qmsDefect: {
    title: 'QMS 不合格品处置', eyebrow: 'QMS · NONCONFORMANCE', subtitle: '记录缺陷原因与责任归属，进行返工、报废、让步接收或退货，形成质量闭环。', url: '/qms/defects', cols: ['defectNo', 'inspectionNo', 'materialCode', 'defectQty', 'defectType', 'disposition', 'dispositionQty'], createText: '登记不合格品', links: [['质量检验', '/ops/qms'], ['WMS 库存流水', '/ops/wmsTxn']],
    fields: baseFields([{ key: 'defectNo', label: '不合格品单号', required: true }, { key: 'inspectionNo', label: '来源检验单号', required: true }, { key: 'defectQty', label: '不合格数量', type: 'number', required: true }, { key: 'defectType', label: '缺陷类型', required: true }, { key: 'defectLevel', label: '严重等级', type: 'select', options: ['MINOR', 'MAJOR', 'CRITICAL'] }, { key: 'responsibleDept', label: '责任部门' }, { key: 'defectDesc', label: '缺陷描述' }]), actions: [{ label: '处置', path: r => `/qms/defects/${r.id}/disposition`, tone: 'warning', prompt: { label: '处置方式与数量', key: 'action', hint: '输入 REWORK / SCRAP / CONCESSION / RETURN；数量默认全部处置' } }]
  },
  qmsRework: {
    title: 'QMS 返工闭环', eyebrow: 'QMS · REWORK MANAGEMENT', subtitle: '返工只能从已判定“返工”的不合格品生成，显式记录质量问题对生产订单、工单与交期的影响。', url: '/qms/reworks', cols: ['reworkNo', 'defectNo', 'prodOrderNo', 'workOrderNo', 'materialCode', 'reworkQty', 'delayDays', 'status'], createText: '创建返工单', links: [['不合格品处置', '/ops/qmsDefect'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'reworkNo', label: '返工单号', required: true }, { key: 'defectNo', label: '不合格品单号', required: true }, { key: 'prodOrderNo', label: '生产订单号', required: true }, { key: 'workOrderNo', label: 'MES 工单号' }, { key: 'reworkQty', label: '返工数量', type: 'number', required: true }, { key: 'reworkHours', label: '预计返工工时', type: 'number' }, { key: 'delayDays', label: '预计延期天数', type: 'number' }]), actions: [{ label: '开始返工', path: r => `/qms/reworks/${r.id}/start`, tone: 'primary' }, { label: '完成返工', path: r => `/qms/reworks/${r.id}/complete`, tone: 'success', prompt: { label: '返工结论', key: 'status', hint: '输入 DONE 或 SCRAPPED' } }]
  },
  wmsInventory: {
    title: 'WMS 库存余额', eyebrow: 'WMS · INVENTORY CONTROL', subtitle: '库存余额按物料、仓库、库位与批次维度可追溯；库存变化必须通过出入库流水形成。', url: '/wms/inventories', cols: ['materialCode', 'materialName', 'warehouseCode', 'locationCode', 'batchNo', 'onHandQty', 'availableQty'], createText: '查看流水入账', links: [['库位维护', '/ops/wmsLocations'], ['出入库流水', '/ops/wmsTxn']], fields: [],
  },
  wmsLocations: {
    title: 'WMS 仓库与库位', eyebrow: 'WMS · LOCATION MASTER', subtitle: '仓库库位独立维护，出入库时按可用库位落账，保证账实可追溯。', url: '/wms/locations', cols: ['locationCode', 'locationName', 'warehouseCode', 'warehouseName', 'locationType', 'available'], createText: '新增库位', links: [['库存余额', '/ops/wmsInventory'], ['出入库流水', '/ops/wmsTxn']],
    fields: baseFields([{ key: 'locationCode', label: '库位编码', required: true }, { key: 'locationName', label: '库位名称', required: true }, { key: 'warehouseCode', label: '仓库编码', required: true }, { key: 'warehouseName', label: '仓库名称', required: true }, { key: 'locationType', label: '库位类型', type: 'select', options: ['RAW', 'WIP', 'FINISHED', 'QUARANTINE'] }]), actions: [{ label: '停用', path: r => `/wms/locations/${r.id}/availability`, tone: 'warning', prompt: { label: '是否可用', key: 'enabled', hint: '输入 true 启用，输入 false 停用' } }]
  },
  wmsTxn: {
    title: 'WMS 出入库流水', eyebrow: 'WMS · STOCK MOVEMENT', subtitle: '采购入库、生产领料、销售出库和调整都在这里形成唯一流水，并实时更新可用库存。', url: '/wms/transactions', cols: ['txnNo', 'materialCode', 'txnType', 'direction', 'txnQty', 'warehouseCode', 'locationCode', 'sourceNo'], createText: '登记出入库', links: [['仓库库位', '/ops/wmsLocations'], ['库存余额', '/ops/wmsInventory'], ['供应商到货', '/ops/srmDelivery']],
    fields: baseFields([{ key: 'txnNo', label: '流水号', required: true }, { key: 'materialCode', label: '物料编码', required: true }, { key: 'warehouseCode', label: '仓库编码', required: true }, { key: 'locationCode', label: '库位编码' }, { key: 'batchNo', label: '批次号' }, { key: 'txnType', label: '业务类型', type: 'select', options: ['IN', 'OUT', 'ADJUST'], required: true }, { key: 'txnQty', label: '数量', type: 'number', required: true }, { key: 'unitCode', label: '单位', placeholder: 'PCS', required: true }, { key: 'unitCost', label: '单位成本', type: 'number', required: true }, { key: 'sourceNo', label: '来源单号' }])
  },
  srmPo: {
    title: 'SRM 采购订单', eyebrow: 'SRM · PROCURE TO PAY', subtitle: '采购订单承接物料需求和供应商交期承诺，下达后供应商才可登记送货。', url: '/srm/purchase-orders', cols: ['purchaseOrderNo', 'supplierName', 'materialName', 'orderQty', 'expectedDate', 'status'], createText: '创建采购订单', links: [['供应商主数据', '/ops/mdmSuppliers'], ['供应商到货', '/ops/srmDelivery']],
    fields: baseFields([{ key: 'purchaseOrderNo', label: '采购订单号', required: true }, { key: 'supplierCode', label: '供应商编码', required: true }, { key: 'supplierName', label: '供应商名称' }, { key: 'materialCode', label: '物料编码', required: true }, { key: 'materialName', label: '物料名称' }, { key: 'orderQty', label: '采购数量', type: 'number', required: true }, { key: 'unitCode', label: '单位', placeholder: 'PCS', required: true }, { key: 'unitPrice', label: '单价', type: 'number', required: true }, { key: 'expectedDate', label: '需求到货日', type: 'date', required: true }, { key: 'promisedDate', label: '承诺到货日', type: 'date' }]), actions: [{ label: '下达供应商', path: r => `/srm/purchase-orders/${r.id}/send`, tone: 'success' }]
  },
  srmDelivery: {
    title: 'SRM 供应商到货', eyebrow: 'SRM · INBOUND COLLABORATION', subtitle: '到货数量、批次、延误天数与来料检验结果连接 SRM、QMS 和 WMS。', url: '/srm/deliveries', cols: ['deliveryNo', 'purchaseOrderNo', 'materialCode', 'deliveryQty', 'delayDays', 'inspectionStatus', 'stocked'], createText: '登记供应商到货', links: [['采购订单', '/ops/srmPo'], ['质量检验', '/ops/qms'], ['WMS 流水', '/ops/wmsTxn']],
    fields: baseFields([{ key: 'deliveryNo', label: '到货单号', required: true }, { key: 'purchaseOrderNo', label: '采购订单号', required: true }, { key: 'deliveryQty', label: '到货数量', type: 'number', required: true }, { key: 'batchNo', label: '批次号' }]), actions: [{ label: '录入检验', path: r => `/srm/deliveries/${r.id}/inspect`, tone: 'success', prompt: { label: '合格数量', key: 'qualifiedQty', hint: '输入本次来料合格数量' } }]
  },
  eamEquipment: {
    title: 'EAM 设备台账', eyebrow: 'EAM · 设备资产台账', subtitle: '维护设备档案、位置、产能、采购质保与保养周期，为生产排程和维修追溯提供依据。', url: '/eam/equipments', cols: ['equipmentCode', 'equipmentName', 'equipmentType', 'workshopCode', 'workCenter', 'locationDesc', 'status', 'healthLevel', 'purchaseDate', 'warrantyEndDate'], createText: '新增设备', links: [['设备故障', '/ops/eamFault'], ['设备点检', '/ops/eamInspection'], ['设备能耗', '/ops/energy']],
    fields: baseFields([{ key: 'equipmentCode', label: '设备编码', required: true, lockedOnEdit: true }, { key: 'equipmentName', label: '设备名称', required: true }, { key: 'equipmentType', label: '设备类型', type: 'select', required: true, options: [{ label: '机械', value: 'MECHANICAL' }, { label: '电气', value: 'ELECTRICAL' }, { label: '液压', value: 'HYDRAULIC' }, { label: '程序控制', value: 'PROGRAM' }, { label: '检测', value: 'INSPECTION' }] }, { key: 'equipmentModel', label: '设备型号' }, { key: 'workshopCode', label: '所属车间编码', required: true }, { key: 'workCenter', label: '工作中心' }, { key: 'locationDesc', label: '安装位置' }, { key: 'capacityPerHour', label: '每小时产能', type: 'number' }, { key: 'healthLevel', label: '健康等级', type: 'select', options: [{ label: '优秀', value: 'A' }, { label: '良好', value: 'B' }, { label: '关注', value: 'C' }, { label: '重点', value: 'D' }] }, { key: 'purchaseDate', label: '购置日期', type: 'date' }, { key: 'purchasePrice', label: '购置金额（元）', type: 'number' }, { key: 'warrantyEndDate', label: '质保到期日', type: 'date' }, { key: 'maintenanceCycleDays', label: '保养周期（天）', type: 'number' }, { key: 'lastMaintenanceDate', label: '上次保养日', type: 'date' }]),
    actions: [{ label: '切换状态', path: r => `/eam/equipments/${r.id}/status`, tone: 'primary', prompt: { label: '设备状态', key: 'status', hint: '选择状态：运行、闲置、故障、维修中或已报废' }, visible: r => r.status !== 'SCRAPPED' }],
    reference: { label: 'IBM Maximo 设备资料与导入规范', url: 'https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=assets-asset-import-guidelines', note: '型号、位置、优先级、质保和维护周期等扩展字段均为非必填。' }
  },
  eamFault: {
    title: 'EAM 故障工单', eyebrow: 'EAM · 故障响应', subtitle: '登记设备异常及其生产影响，关联维修单后追踪处理结果；关闭后设备恢复可用状态。', url: '/eam/faults', cols: ['faultNo', 'equipmentName', 'faultType', 'faultLevel', 'faultCause', 'workOrderNo', 'prodOrderNo', 'operationCode', 'status', 'faultTime'], createText: '申报设备故障', links: [['设备台账', '/ops/eamEquipment'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'faultNo', label: '故障单号', required: true, lockedOnEdit: true }, { key: 'equipmentCode', label: '设备编码', required: true, lockedOnEdit: true }, { key: 'faultType', label: '故障类型', required: true, placeholder: '如机械、电气、液压、程序' }, { key: 'faultLevel', label: '故障等级', type: 'select', options: [{ label: '低', value: 'LOW' }, { label: '中', value: 'MEDIUM' }, { label: '高', value: 'HIGH' }, { label: '严重', value: 'CRITICAL' }] }, { key: 'faultDesc', label: '故障描述', type: 'textarea' }, { key: 'faultCause', label: '故障原因' }, { key: 'workOrderNo', label: '关联 MES 工单号' }, { key: 'prodOrderNo', label: '受影响生产订单号' }, { key: 'operationCode', label: '受影响工序编码' }]), actions: [{ label: '关闭故障', path: r => `/eam/faults/${r.id}/close`, tone: 'success', visible: r => r.status === 'OPEN' }],
    reference: { label: 'IBM Maximo 工单管理', url: 'https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=module-work-orders-application', note: '故障原因、工单、生产订单和工序为便于追溯的选填信息。' }
  },
  eamRepair: {
    title: 'EAM 维修执行', eyebrow: 'EAM · 维修执行', subtitle: '维修单关联故障与设备，记录维修内容、备件、成本和工时；完工后自动同步故障与设备状态。', url: '/eam/repairs', cols: ['repairNo', 'faultNo', 'equipmentCode', 'repairType', 'repairContent', 'replacedParts', 'repairmanCode', 'downtimeMinutes', 'maintenanceHours', 'repairCost', 'repairResult'], createText: '创建维修单', links: [['设备故障', '/ops/eamFault'], ['设备台账', '/ops/eamEquipment'], ['WMS 流水', '/ops/wmsTxn']],
    fields: baseFields([{ key: 'repairNo', label: '维修单号', required: true, lockedOnEdit: true }, { key: 'faultNo', label: '关联故障单号', required: true, lockedOnEdit: true }, { key: 'equipmentCode', label: '设备编码', required: true, lockedOnEdit: true }, { key: 'repairType', label: '维修类型', type: 'select', options: [{ label: '抢修', value: 'EMERGENCY' }, { label: '计划维修', value: 'PLANNED' }, { label: '大修', value: 'OVERHAUL' }] }, { key: 'repairContent', label: '维修内容', required: true, type: 'textarea' }, { key: 'replacedParts', label: '更换备件（选填）' }, { key: 'repairCost', label: '维修成本（元）', type: 'number' }, { key: 'maintenanceHours', label: '维修工时（小时）', type: 'number' }, { key: 'remark', label: '备注' }]), actions: [{ label: '完成维修', path: r => `/eam/repairs/${r.id}/complete`, tone: 'success', prompt: { label: '维修结论', key: 'result', hint: '选择结论：已修复、待备件或转报废' }, visible: r => ['REPAIRING', 'PENDING_PARTS'].includes(r.repairResult) }],
    reference: { label: 'IBM Maximo 工单与物料管理', url: 'https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=module-inventory', note: '备件、维修成本、维修工时和备注为选填项，便于维修成本与物料追踪。' }
  },
  eamInspection: {
    title: 'EAM 设备点检', eyebrow: 'EAM · 预防点检', subtitle: '建立设备点检安排，记录周期、计划日期和检查项目；执行后登记检查结论与异常情况。', url: '/eam/inspections', cols: ['inspectionNo', 'equipmentCode', 'inspectionType', 'planDate', 'actualDate', 'checkItems', 'result', 'abnormalDesc', 'inspectorCode', 'missed'], createText: '创建点检计划', links: [['设备台账', '/ops/eamEquipment'], ['设备故障', '/ops/eamFault']],
    fields: baseFields([{ key: 'inspectionNo', label: '点检单号', required: true, lockedOnEdit: true }, { key: 'equipmentCode', label: '设备编码', required: true, lockedOnEdit: true }, { key: 'inspectionType', label: '点检周期', type: 'select', options: [{ label: '日检', value: 'DAILY' }, { label: '周检', value: 'WEEKLY' }, { label: '月检', value: 'MONTHLY' }], required: true }, { key: 'planDate', label: '计划日期', type: 'date', required: true }, { key: 'checkItems', label: '点检项目（JSON，可选）', type: 'textarea', placeholder: '例如：{"润滑":"正常","异响":"无"}' }]), actions: [{ label: '完成点检', path: r => `/eam/inspections/${r.id}/complete`, tone: 'success', prompt: { label: '点检结果', key: 'result', hint: '选择结果：正常或异常' }, visible: r => !r.result }],
    reference: { label: 'IBM Maximo 预防性维护与周期计划', url: 'https://www.ibm.com/docs/en/mfo-and-g/cd?topic=module-preventive-maintenance-application', note: '点检项目、实际执行日期、结果和检查人为选填或执行时自动记录。' }
  },
  energy: {
    title: 'EMS 设备能耗', eyebrow: 'EMS · ENERGY PERFORMANCE', subtitle: '按设备、时段和能源介质采集能耗，结合产出计算单位能耗，为节能诊断提供事实依据。', url: '/energy/usages', cols: ['equipmentCode', 'statDate', 'energyType', 'energyValue', 'outputQty', 'unitConsumption'], createText: '登记能耗读数', links: [['设备台账', '/ops/eamEquipment'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'equipmentCode', label: '设备编码', required: true }, { key: 'statDate', label: '统计日期', type: 'date' }, { key: 'statHour', label: '统计小时', type: 'number' }, { key: 'energyType', label: '能源类型', type: 'select', options: ['ELECTRIC', 'WATER', 'GAS', 'STEAM'], required: true }, { key: 'energyValue', label: '能耗量', type: 'number', required: true }, { key: 'unitCode', label: '计量单位', placeholder: 'KWH' }, { key: 'runHours', label: '运行小时', type: 'number' }, { key: 'outputQty', label: '产出数量', type: 'number' }])
  },
  energyWorkshop: {
    title: 'EMS 车间能耗', eyebrow: 'EMS · WORKSHOP ENERGY', subtitle: '以车间、日期与能源介质汇总能耗，并自动计算单位产出能耗，形成班组与车间节能改善的管理口径。', url: '/energy/workshop-usages', cols: ['workshopCode', 'statDate', 'energyType', 'energyValue', 'totalOutput', 'unitConsumption'], createText: '登记车间能耗', links: [['设备能耗', '/ops/energy'], ['设备台账', '/ops/eamEquipment']],
    fields: baseFields([{ key: 'workshopCode', label: '车间编码', required: true }, { key: 'statDate', label: '统计日期', type: 'date' }, { key: 'energyType', label: '能源类型', type: 'select', options: ['ELECTRIC', 'WATER', 'GAS', 'STEAM'], required: true }, { key: 'energyValue', label: '能耗量', type: 'number', required: true }, { key: 'unitCode', label: '计量单位', placeholder: 'KWH' }, { key: 'totalOutput', label: '车间总产出', type: 'number' }])
  }
}

const c = computed(() => configs[props.kind] || configs.crm)
const isEam = computed(() => props.kind.startsWith('eam'))
const isMes = computed(() => ['mes', 'mesDispatch', 'mesReports', 'mesAndon'].includes(props.kind))
const rows = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const dialog = ref(false)
const detailDialog = ref(false)
const detailRecord = ref<any>(null)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<Record<string, any>>({})
const eamColumnLabels: Record<string, string> = {
  id: '记录编号', createdAt: '创建时间', updatedAt: '更新时间',
  equipmentCode: '设备编码', equipmentName: '设备名称', equipmentType: '设备类型', equipmentModel: '设备型号',
  workshopCode: '车间编码', workCenter: '工作中心', locationDesc: '安装位置', capacityPerHour: '每小时产能',
  status: '设备状态', healthLevel: '健康等级', healthScore: '健康评分', purchaseDate: '购置日期', purchasePrice: '购置金额',
  warrantyEndDate: '质保到期日', maintenanceCycleDays: '保养周期（天）', lastMaintenanceDate: '上次保养日',
  faultNo: '故障单号', faultType: '故障类型', faultLevel: '故障等级', faultDesc: '故障描述', faultCause: '故障原因',
  faultTime: '故障时间', reportedBy: '上报人', workOrderNo: 'MES 工单号', prodOrderNo: '生产订单号', operationCode: '工序编码',
  repairNo: '维修单号', repairType: '维修类型', repairContent: '维修内容', replacedParts: '更换备件',
  repairmanCode: '维修人', repairStartTime: '维修开始时间', repairEndTime: '维修结束时间', downtimeMinutes: '停机时长（分钟）', maintenanceHours: '维修工时（小时）',
  repairCost: '维修成本', repairResult: '维修结论', remark: '备注', inspectionNo: '点检单号',
  inspectionType: '点检周期', planDate: '计划日期', actualDate: '实际日期', checkItems: '点检项目',
  result: '检查结果', abnormalDesc: '异常描述', inspectorCode: '检查人', missed: '是否漏检'
}
const eamValueLabels: Record<string, string> = {
  RUNNING: '运行中', IDLE: '闲置', FAULT: '故障', MAINTENANCE: '维修中', SCRAPPED: '已报废',
  OPEN: '待处理', PROCESSING: '处理中', REPAIRING: '维修中', REPAIRED: '已修复', CLOSED: '已关闭',
  PENDING_PARTS: '待备件', NORMAL: '正常', ABNORMAL: '异常', NA: '未点检',
  DAILY: '日检', WEEKLY: '周检', MONTHLY: '月检', EMERGENCY: '抢修', PLANNED: '计划维修', OVERHAUL: '大修',
  MECHANICAL: '机械', MACHINERY: '机械', MACHINE: '机械设备', MACHINING: '机加工', ELECTRICAL: '电气', ELECTRIC: '电气设备',
  HYDRAULIC: '液压', PROGRAM: '程序控制', SOFTWARE: '程序控制', INSPECTION: '检测', ASSEMBLY: '装配设备', WELDING: '焊接设备',
  DRILLING: '钻孔设备', TESTING: '检测设备', CONVEYOR: '输送设备', ROBOT: '工业机器人', CNC: '数控设备', CNC_LATHE: '数控车床',
  PUMP: '泵类设备', MOTOR: '电机', COMPRESSOR: '压缩机', PRESS: '压力机', PACKAGING: '包装设备', WEAR: '磨损', SAFETY: '安全故障', OTHER: '其他',
  A: '优秀', B: '良好', C: '关注', D: '重点', TRUE: '是', FALSE: '否'
}
const columnLabel = (key: string) => isMes.value ? zhKey(key) : isEam.value ? (eamColumnLabels[key] || key) : key.replaceAll('Code', '编码').replaceAll('No', '编号')
const localizedValue = (value: unknown) => {
  if (value == null || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  const raw = String(value)
  return eamValueLabels[raw.toUpperCase()] || zh(value)
}
const cellText = (key: string, value: unknown) => {
  if (isMes.value) return zh(value)
  if (!isEam.value) return value == null || value === '' ? '—' : String(value)
  if (key === 'missed') return value ? '是' : '否'
  if (key === 'checkItems' && typeof value === 'string') {
    try {
      const parseAndTranslate = (node: any): any => Array.isArray(node)
        ? node.map(parseAndTranslate)
        : node && typeof node === 'object'
          ? Object.fromEntries(Object.entries(node).map(([k, v]) => [k, parseAndTranslate(v)]))
          : typeof node === 'string' ? (eamValueLabels[node.toUpperCase()] || node) : node
      return JSON.stringify(parseAndTranslate(JSON.parse(value)))
    } catch { return String(value) }
  }
  return localizedValue(value)
}
const reportError = (message: string) => {
  if (isEam.value || isMes.value) showErrorDialog(message)
  else ElMessage.error(message)
}
const errorText = (error: any, fallback: string) => error?.message || error?.response?.data?.message || fallback
const optionValue = (option: string | SelectOption) => typeof option === 'string' ? option : option.value
const optionLabel = (option: string | SelectOption) => typeof option === 'string' ? (isEam.value ? localizedValue(option) : zh(option)) : option.label
const visibleActions = (row: any) => c.value.actions?.filter(action => !action.visible || action.visible(row)) || []
const canEditEamRow = (row: any) => {
  if (props.kind === 'eamEquipment') return true
  if (props.kind === 'eamFault') return row.status === 'OPEN'
  if (props.kind === 'eamRepair') return ['REPAIRING', 'PENDING_PARTS'].includes(row.repairResult)
  if (props.kind === 'eamInspection') return !row.result && !row.missed
  return false
}
const canDeleteEamRow = (row: any) => canEditEamRow(row)
const resetForm = () => {
  Object.keys(form).forEach(k => delete form[k])
  c.value.fields.forEach(field => {
    if (field.defaultValue !== undefined) form[field.key] = field.defaultValue
    else if (field.type === 'date') form[field.key] = isMes.value ? '' : isEam.value && !field.required ? '' : today
    else if (field.type === 'datetime') form[field.key] = ''
    else if (field.key.endsWith('No')) form[field.key] = ''
    else form[field.key] = ''
  })
}
const openCreate = () => { editingId.value = null; resetForm(); dialog.value = true }
const openEdit = async (row: any) => {
  let record = row
  if (isMes.value) {
    try {
      const { data } = await http.get(`${c.value.url}/${row.id}`)
      record = data.data
    } catch (error: any) {
      reportError(errorText(error, '读取单据详情失败'))
      return
    }
  }
  editingId.value = row.id
  resetForm()
  c.value.fields.forEach(field => { form[field.key] = record[field.key] ?? '' })
  dialog.value = true
}
const openDetail = async (row: any) => {
  try {
    const { data } = await http.get(`${c.value.url}/${row.id}`)
    detailRecord.value = data.data
    detailDialog.value = true
  } catch (error: any) {
    reportError(errorText(error, '读取单据详情失败'))
  }
}
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await http.get(c.value.url, { params: { page: page.value, size: size.value } })
    // 多数端点返回分页对象 { content, totalElements }，少数返回纯数组
    const payload = data.data
    if (payload && Array.isArray(payload.content)) {
      rows.value = payload.content
      total.value = payload.totalElements ?? payload.content.length
    } else {
      rows.value = Array.isArray(payload) ? payload : []
      total.value = rows.value.length
    }
  } catch (error: any) {
    loadError.value = error.message || '数据加载失败'
    if (isEam.value || isMes.value) reportError(errorText(error, '业务数据加载失败'))
  } finally { loading.value = false }
}
// 切换业务域时回到第一页，避免停留在上一个域的不存在页码上
watch(() => props.kind, () => { page.value = 1; load() })
const save = async () => {
  const missing = c.value.fields.find(f => f.required && (form[f.key] == null || String(form[f.key]).trim() === ''))
  if (missing) return reportError(`请填写：${missing.label}`)
  saving.value = true
  try {
    const formPayload = isMes.value || isEam.value
      ? Object.fromEntries(c.value.fields.map(field => {
        const value = form[field.key]
        return [field.key, value === '' && !field.required ? null : value]
      }))
      : form
    const payload = c.value.compose && editingId.value == null ? c.value.compose(form) : formPayload
    if (editingId.value != null) await http.put(`${c.value.url}/${editingId.value}`, payload)
    else await http.post(c.value.url, payload)
    ElMessage.success(editingId.value != null ? '业务单据已更新' : '业务单据已创建')
    dialog.value = false
    await load()
  } catch (error: any) {
    reportError(errorText(error, '保存失败，请检查必填字段与主数据状态'))
  } finally { saving.value = false }
}
const remove = async (row: any) => {
  const key = row.workOrderNo || row.reportNo || row.eventNo || row.faultNo || row.repairNo || row.inspectionNo || row.equipmentCode || `#${row.id}`
  try {
    await ElMessageBox.confirm(`确定删除单据“${key}”吗？如该单据已有后续业务记录，系统会阻止删除。`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await http.delete(`${c.value.url}/${row.id}`)
    ElMessage.success('业务单据已删除')
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close' || error?.message === 'cancel' || error?.message === 'close') return
    reportError(errorText(error, '删除失败，请检查单据状态及关联记录'))
  }
}
const run = async (action: Action, row: any) => {
  try {
    let params: Record<string, any> | undefined
    let body: Record<string, any> | null = null
    if (action.prompt) {
      const { value } = await ElMessageBox.prompt(action.prompt.hint, action.label, { inputPlaceholder: action.prompt.hint, inputType: action.prompt.key === 'responseNote' || action.prompt.key === 'resolution' ? 'textarea' : 'text', confirmButtonText: '确认', cancelButtonText: '取消' })
      if (!value) return
      let promptValue = value.trim()
      if (isEam.value) {
        const choices: Record<string, Record<string, string>> = {
          status: { '运行': 'RUNNING', '运行中': 'RUNNING', '闲置': 'IDLE', '故障': 'FAULT', '维修中': 'MAINTENANCE', '已报废': 'SCRAPPED' },
          result: { '正常': 'NORMAL', '异常': 'ABNORMAL', '已修复': 'REPAIRED', '待备件': 'PENDING_PARTS', '转报废': 'SCRAPPED' }
        }
        promptValue = choices[action.prompt.key]?.[promptValue] || promptValue.toUpperCase()
      }
      params = { [action.prompt.key]: promptValue }
      if (isEam.value && props.kind === 'eamInspection' && promptValue === 'ABNORMAL') {
        const { value: description } = await ElMessageBox.prompt('请补充异常说明（可留空）', '点检异常说明', { inputPlaceholder: '例如：设备运行时出现明显异响', inputValue: '', confirmButtonText: '确认', cancelButtonText: '取消' })
        params.abnormalDesc = description.trim()
      }
      if (props.kind === 'mes' && action.prompt.key === 'qualifiedQty') params.scrapQty = 0
      if (props.kind === 'qms' && action.prompt.key === 'qualifiedQty') {
        const qualified = Number(value); params.defectQty = Number(row.inspectedQty) - qualified; params.result = params.defectQty === 0 ? 'PASSED' : 'FAILED'
      }
      if (props.kind === 'qmsDefect' && action.prompt.key === 'action') { params.qty = row.defectQty }
      if (action.bodyPrompt) { body = params; params = undefined }
    }
    await http.post(action.path(row), body, { params })
    ElMessage.success(`${action.label}已完成`)
    await load()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close' && error?.message !== 'cancel' && error?.message !== 'close') reportError(errorText(error, `${action.label}未完成，请检查当前单据状态`))
  }
}
onMounted(load)
</script>

<template>
  <section class="workspace">
    <div class="hero">
      <div><div class="eyebrow">{{ c.eyebrow }}</div><h1>{{ c.title }}</h1><p>{{ c.subtitle }}</p></div>
      <div class="hero-actions"><el-button plain @click="load">刷新数据</el-button><el-button v-if="c.fields.length && c.canCreate !== false" type="primary" @click="openCreate">{{ c.createText }}</el-button></div>
    </div>
    <div class="flow-nav"><span>相关工作区</span><el-button v-for="link in c.links" :key="link[1]" text @click="$router.push(link[1])">{{ link[0] }} →</el-button></div>
    <el-alert v-if="(isEam || isMes) && c.reference" class="reference-note" type="info" :closable="false" show-icon>
      <template #title><a :href="c.reference.url" target="_blank" rel="noreferrer">字段参考：{{ c.reference.label }}</a></template>
      <span>{{ c.reference.note }}</span>
    </el-alert>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="load">重新加载</el-button></template></el-alert>
    <el-card class="data-card" shadow="never">
      <template #header><div class="card-head"><span>业务记录</span><small>共 {{ total }} 条 · 最近刷新</small></div></template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column v-for="key in c.cols" :key="key" :prop="key" :label="columnLabel(key)" min-width="128" show-overflow-tooltip>
          <template #default="{ row }">{{ cellText(key, row[key]) }}</template>
        </el-table-column>
        <el-table-column v-if="c.actions?.length || isEam || (isMes && (c.canRead || c.canEdit || c.canDelete))" label="业务操作" :width="isMes ? 370 : isEam ? 300 : 190" fixed="right">
          <template #default="{ row }">
            <el-button v-for="action in visibleActions(row)" :key="action.label" link :type="action.tone" @click="run(action,row)">{{ action.label }}</el-button>
            <template v-if="isMes || isEam">
              <el-button v-if="(isMes && c.canRead) || isEam" link @click="openDetail(row)">详情</el-button>
              <el-button v-if="(isMes && c.canEdit) || (isEam && canEditEamRow(row))" link type="primary" @click="openEdit(row)">修改</el-button>
              <el-button v-if="c.canDelete || (isEam && canDeleteEamRow(row))" link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无业务记录，可通过右上角按钮创建第一张业务单据。" />
      <TablePager v-if="rows.length" v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
    <el-dialog v-model="dialog" :title="editingId != null ? `编辑${c.title}` : c.createText" width="720px" destroy-on-close @closed="editingId = null">
      <div class="form-note">带 <b>*</b> 的字段为业务必填项。编码应引用已发布的统一主数据。<span v-if="isEam || isMes">未标星字段均可不填。</span></div>
      <el-alert v-if="(isEam || isMes) && c.reference" class="dialog-reference" type="info" :closable="false"><a :href="c.reference.url" target="_blank" rel="noreferrer">查看{{ c.reference.label }}</a> · {{ c.reference.note }}</el-alert>
      <el-form label-position="top" class="work-form"><el-row :gutter="16"><el-col v-for="field in c.fields" :key="field.key" :span="field.type==='textarea' ? 24 : 12"><el-form-item :label="field.label" :required="field.required"><el-select v-if="field.type==='select'" v-model="form[field.key]" filterable :allow-create="!isEam" default-first-option placeholder="请选择" :disabled="editingId != null && field.lockedOnEdit"><el-option v-for="option in field.options" :key="optionValue(option)" :label="optionLabel(option)" :value="optionValue(option)" /></el-select><el-date-picker v-else-if="field.type==='date'" v-model="form[field.key]" value-format="YYYY-MM-DD" type="date" style="width:100%" :disabled="editingId != null && field.lockedOnEdit" /><el-date-picker v-else-if="field.type==='datetime'" v-model="form[field.key]" value-format="YYYY-MM-DDTHH:mm:ss" type="datetime" style="width:100%" :disabled="editingId != null && field.lockedOnEdit" /><el-input-number v-else-if="field.type==='number'" v-model="form[field.key]" :min="0" controls-position="right" style="width:100%" :disabled="editingId != null && field.lockedOnEdit" /><el-input v-else v-model="form[field.key]" :type="field.type==='textarea' ? 'textarea' : 'text'" :rows="field.type==='textarea' ? 3 : undefined" :placeholder="field.placeholder || `请输入${field.label}`" :disabled="editingId != null && field.lockedOnEdit" /></el-form-item></el-col></el-row></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ editingId != null ? '保存修改' : '保存业务单据' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailDialog" title="单据详情" width="760px" destroy-on-close>
      <el-descriptions v-if="detailRecord" :column="2" border>
        <el-descriptions-item v-for="[key, value] in Object.entries(detailRecord)" :key="key" :label="isEam ? (eamColumnLabels[key] || key) : zhKey(key)">{{ isEam ? cellText(key, value) : zh(value) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="detailDialog=false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workspace{max-width:1480px;margin:0 auto}.hero{display:flex;justify-content:space-between;align-items:flex-start;padding:4px 2px 20px}.eyebrow{color:#3770ef;font-size:11px;font-weight:800;letter-spacing:1.3px}.hero h1{margin:7px 0 7px;color:#1b3150;font-size:25px;letter-spacing:-.4px}.hero p{max-width:720px;margin:0;color:#708096;line-height:1.7;font-size:13px}.hero-actions{display:flex;gap:9px;padding-top:16px}.flow-nav{display:flex;align-items:center;gap:3px;margin-bottom:15px;padding:9px 14px;border:1px solid #e3eaf3;border-radius:10px;background:#fbfdff;color:#718198;font-size:12px}.flow-nav span{margin-right:6px;font-weight:700;color:#65758b}.reference-note{margin:-5px 0 15px}.reference-note a,.dialog-reference a{color:var(--el-color-primary);text-decoration:none}.dialog-reference{margin-bottom:14px}.data-card{border:1px solid #e5ebf3;border-radius:12px}.card-head{display:flex;align-items:center;justify-content:space-between;color:#304563;font-size:14px;font-weight:700}.card-head small{font-size:11px;color:#9ba9b9;font-weight:400}.form-note{margin:-4px 0 12px;padding:9px 11px;border-radius:7px;background:#f2f7ff;color:#6c7d91;font-size:12px}.form-note b{color:#f56c6c}.work-form :deep(.el-form-item){margin-bottom:14px}.work-form :deep(.el-form-item__label){padding-bottom:5px;color:#53657e;font-weight:600}@media(max-width:760px){.hero{display:block}.hero-actions{padding-top:12px}.flow-nav{overflow:auto}.work-form :deep(.el-col){width:100%;max-width:100%;flex:0 0 100%}}
</style>
