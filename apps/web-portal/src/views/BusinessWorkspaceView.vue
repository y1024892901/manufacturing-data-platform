<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'

type Field = { key: string; label: string; type?: 'text' | 'number' | 'date' | 'select'; options?: string[]; required?: boolean; placeholder?: string }
type Action = { label: string; path: (row: any) => string; tone?: 'primary' | 'success' | 'warning' | 'danger'; prompt?: { label: string; key: string; hint: string }; bodyPrompt?: boolean }
type Workspace = { title: string; eyebrow: string; subtitle: string; url: string; cols: string[]; fields: Field[]; createText: string; links: [string, string][]; actions?: Action[]; compose?: (form: Record<string, any>) => any }

const props = defineProps<{ kind: string }>()
const today = new Date().toISOString().slice(0, 10)
const baseFields = (items: Field[]) => items
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
    title: 'MES 制造执行', eyebrow: 'MES · SHOP FLOOR EXECUTION', subtitle: '工序工单记录开工、合格报工、报废与完工，是计划与现场执行差异的真实来源。', url: '/mes/work-orders', cols: ['workOrderNo', 'prodOrderNo', 'operationName', 'planQty', 'completedQty', 'qualifiedQty', 'status'], createText: '创建工序工单', links: [['生产订单', '/ops/production'], ['质量检验', '/ops/qms'], ['设备故障', '/ops/eamFault']],
    fields: baseFields([{ key: 'workOrderNo', label: '工单号', required: true }, { key: 'prodOrderNo', label: '生产订单号' }, { key: 'productCode', label: '产品编码', required: true }, { key: 'opSeq', label: '工序序号', type: 'number' }, { key: 'operationCode', label: '工序编码', required: true }, { key: 'operationName', label: '工序名称', required: true }, { key: 'equipmentCode', label: '设备编码' }, { key: 'planQty', label: '计划数量', type: 'number', required: true }]), actions: [{ label: '开工', path: r => `/mes/work-orders/${r.id}/start`, tone: 'primary' }, { label: '报工', path: r => `/mes/work-orders/${r.id}/report`, tone: 'success', prompt: { label: '合格数量', key: 'qualifiedQty', hint: '输入本次合格数量；报废数量默认为 0' } }]
  },
  mesReports: {
    title: 'MES 报工明细', eyebrow: 'MES · PRODUCTION REPORTING', subtitle: '每一次报工记录班次、操作人、设备与停机原因，并同步汇总到工单进度，支撑 OEE 和产能分析。', url: '/mes/reports', cols: ['reportNo', 'workOrderNo', 'prodOrderNo', 'qualifiedQty', 'scrapQty', 'reportDate', 'operatorCode'], createText: '登记报工明细', links: [['MES 工单', '/ops/mes'], ['设备故障', '/ops/eamFault'], ['质量检验', '/ops/qms']],
    fields: baseFields([{ key: 'reportNo', label: '报工单号', required: true }, { key: 'workOrderNo', label: 'MES 工单号', required: true }, { key: 'qualifiedQty', label: '合格数量', type: 'number', required: true }, { key: 'scrapQty', label: '报废数量', type: 'number' }, { key: 'reportDate', label: '报工日期', type: 'date' }, { key: 'shiftCode', label: '班次', type: 'select', options: ['DAY', 'NIGHT'] }, { key: 'workHours', label: '工时', type: 'number' }, { key: 'pauseMinutes', label: '停机分钟', type: 'number' }, { key: 'pauseReason', label: '停机原因' }])
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
    title: 'EAM 设备台账', eyebrow: 'EAM · ASSET MASTER', subtitle: '设备、产能、工作中心与健康状态独立维护，为 MES 排产和能源分析提供可信对象。', url: '/eam/equipments', cols: ['equipmentCode', 'equipmentName', 'equipmentType', 'workshopCode', 'workCenter', 'status', 'healthLevel'], createText: '新增设备', links: [['设备故障', '/ops/eamFault'], ['设备点检', '/ops/eamInspection'], ['设备能耗', '/ops/energy']],
    fields: baseFields([{ key: 'equipmentCode', label: '设备编码', required: true }, { key: 'equipmentName', label: '设备名称', required: true }, { key: 'equipmentType', label: '设备类型', required: true }, { key: 'equipmentModel', label: '设备型号' }, { key: 'workshopCode', label: '车间编码', required: true }, { key: 'workCenter', label: '工作中心' }, { key: 'capacityPerHour', label: '每小时产能', type: 'number' }, { key: 'healthLevel', label: '健康等级', type: 'select', options: ['A', 'B', 'C', 'D'] }]), actions: [{ label: '切换状态', path: r => `/eam/equipments/${r.id}/status`, tone: 'primary', prompt: { label: '设备状态', key: 'status', hint: '输入 RUNNING / IDLE / FAULT / MAINTENANCE / SCRAPPED' } }]
  },
  eamFault: {
    title: 'EAM 故障工单', eyebrow: 'EAM · FAILURE RESPONSE', subtitle: '故障申报会把设备置为故障状态，关闭后恢复待机，形成生产影响与维修闭环。', url: '/eam/faults', cols: ['faultNo', 'equipmentName', 'faultType', 'faultLevel', 'prodOrderNo', 'status'], createText: '申报设备故障', links: [['设备台账', '/ops/eamEquipment'], ['MES 工单', '/ops/mes']],
    fields: baseFields([{ key: 'faultNo', label: '故障单号', required: true }, { key: 'equipmentCode', label: '设备编码', required: true }, { key: 'faultType', label: '故障类型', required: true }, { key: 'faultLevel', label: '故障等级', type: 'select', options: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] }, { key: 'faultDesc', label: '故障描述' }, { key: 'workOrderNo', label: '关联 MES 工单' }, { key: 'prodOrderNo', label: '关联生产订单' }]), actions: [{ label: '关闭故障', path: r => `/eam/faults/${r.id}/close`, tone: 'success' }]
  },
  eamRepair: {
    title: 'EAM 维修执行', eyebrow: 'EAM · MAINTENANCE EXECUTION', subtitle: '维修单关联故障、设备、停机时长、更换备件和维修成本；已修复后自动恢复设备可用状态。', url: '/eam/repairs', cols: ['repairNo', 'faultNo', 'equipmentCode', 'repairType', 'repairmanCode', 'downtimeMinutes', 'repairCost', 'repairResult'], createText: '创建维修单', links: [['设备故障', '/ops/eamFault'], ['设备台账', '/ops/eamEquipment'], ['WMS 流水', '/ops/wmsTxn']],
    fields: baseFields([{ key: 'repairNo', label: '维修单号', required: true }, { key: 'faultNo', label: '关联故障单号', required: true }, { key: 'equipmentCode', label: '设备编码', required: true }, { key: 'repairType', label: '维修类型', type: 'select', options: ['EMERGENCY', 'PLANNED', 'OVERHAUL'] }, { key: 'repairContent', label: '维修内容', required: true }, { key: 'replacedParts', label: '更换备件' }, { key: 'repairCost', label: '维修成本', type: 'number' }, { key: 'remark', label: '备注' }]), actions: [{ label: '完成维修', path: r => `/eam/repairs/${r.id}/complete`, tone: 'success', prompt: { label: '维修结论', key: 'result', hint: '输入 REPAIRED / PENDING_PARTS / SCRAPPED' } }]
  },
  eamInspection: {
    title: 'EAM 设备点检', eyebrow: 'EAM · PREVENTIVE INSPECTION', subtitle: '日、周、月点检计划和异常记录，让设备管理从故障响应转为预防维护。', url: '/eam/inspections', cols: ['inspectionNo', 'equipmentCode', 'inspectionType', 'planDate', 'actualDate', 'result'], createText: '创建点检计划', links: [['设备台账', '/ops/eamEquipment'], ['设备故障', '/ops/eamFault']],
    fields: baseFields([{ key: 'inspectionNo', label: '点检单号', required: true }, { key: 'equipmentCode', label: '设备编码', required: true }, { key: 'inspectionType', label: '点检类型', type: 'select', options: ['DAILY', 'WEEKLY', 'MONTHLY'], required: true }, { key: 'planDate', label: '计划日期', type: 'date', required: true }]), actions: [{ label: '完成点检', path: r => `/eam/inspections/${r.id}/complete`, tone: 'success', prompt: { label: '点检结果', key: 'result', hint: '输入 NORMAL 或 ABNORMAL' } }]
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
const rows = ref<any[]>([])
const loading = ref(false)
const loadError = ref('')
const dialog = ref(false)
const saving = ref(false)
const form = reactive<Record<string, any>>({})
const resetForm = () => {
  Object.keys(form).forEach(k => delete form[k])
  c.value.fields.forEach(field => {
    if (field.type === 'date') form[field.key] = field.key.includes('Date') || field.key.includes('date') ? today : ''
    else if (field.key.endsWith('No')) form[field.key] = ''
    else form[field.key] = ''
  })
}
const openCreate = () => { resetForm(); dialog.value = true }
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await http.get(c.value.url, { params: { page: 1, size: 50 } })
    rows.value = data.data?.content || data.data || []
  } catch (error: any) {
    loadError.value = error.message || '数据加载失败'
  } finally { loading.value = false }
}
const create = async () => {
  const missing = c.value.fields.find(f => f.required && !form[f.key])
  if (missing) return ElMessage.warning(`请填写：${missing.label}`)
  saving.value = true
  try {
    await http.post(c.value.url, c.value.compose ? c.value.compose(form) : form)
    ElMessage.success('业务单据已创建')
    dialog.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存失败，请检查必填字段与主数据状态')
  } finally { saving.value = false }
}
const run = async (action: Action, row: any) => {
  try {
    let params: Record<string, any> | undefined
    let body: Record<string, any> | null = null
    if (action.prompt) {
      const { value } = await ElMessageBox.prompt(action.prompt.hint, action.label, { inputPlaceholder: action.prompt.hint, confirmButtonText: '确认', cancelButtonText: '取消' })
      if (!value) return
      params = { [action.prompt.key]: value }
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
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.message || `${action.label}未完成，请检查当前单据状态`)
  }
}
onMounted(load)
</script>

<template>
  <section class="workspace">
    <div class="hero">
      <div><div class="eyebrow">{{ c.eyebrow }}</div><h1>{{ c.title }}</h1><p>{{ c.subtitle }}</p></div>
      <div class="hero-actions"><el-button plain @click="load">刷新数据</el-button><el-button v-if="c.fields.length" type="primary" @click="openCreate">{{ c.createText }}</el-button></div>
    </div>
    <div class="flow-nav"><span>相关工作区</span><el-button v-for="link in c.links" :key="link[1]" text @click="$router.push(link[1])">{{ link[0] }} →</el-button></div>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="load">重新加载</el-button></template></el-alert>
    <el-card class="data-card" shadow="never">
      <template #header><div class="card-head"><span>业务记录</span><small>共 {{ rows.length }} 条 · 最近刷新</small></div></template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column v-for="key in c.cols" :key="key" :prop="key" :label="key.replaceAll('Code','编码').replaceAll('No','编号')" min-width="128" show-overflow-tooltip />
        <el-table-column v-if="c.actions?.length" label="业务动作" width="190" fixed="right"><template #default="{ row }"><el-button v-for="action in c.actions" :key="action.label" link :type="action.tone" @click="run(action,row)">{{ action.label }}</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无业务记录，可通过右上角按钮创建第一张业务单据。" />
    </el-card>
    <el-dialog v-model="dialog" :title="c.createText" width="680px" destroy-on-close>
      <div class="form-note">带 <b>*</b> 的字段为业务必填项。编码应引用已发布的统一主数据。</div>
      <el-form label-position="top" class="work-form"><el-row :gutter="16"><el-col v-for="field in c.fields" :key="field.key" :span="12"><el-form-item :label="field.label" :required="field.required"><el-select v-if="field.type==='select'" v-model="form[field.key]" filterable allow-create default-first-option placeholder="请选择"><el-option v-for="option in field.options" :key="option" :label="option" :value="option" /></el-select><el-date-picker v-else-if="field.type==='date'" v-model="form[field.key]" value-format="YYYY-MM-DD" type="date" style="width:100%" /><el-input-number v-else-if="field.type==='number'" v-model="form[field.key]" :min="0" controls-position="right" style="width:100%" /><el-input v-else v-model="form[field.key]" :placeholder="field.placeholder || `请输入${field.label}`" /></el-form-item></el-col></el-row></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="create">保存业务单据</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workspace{max-width:1480px;margin:0 auto}.hero{display:flex;justify-content:space-between;align-items:flex-start;padding:4px 2px 20px}.eyebrow{color:#3770ef;font-size:11px;font-weight:800;letter-spacing:1.3px}.hero h1{margin:7px 0 7px;color:#1b3150;font-size:25px;letter-spacing:-.4px}.hero p{max-width:720px;margin:0;color:#708096;line-height:1.7;font-size:13px}.hero-actions{display:flex;gap:9px;padding-top:16px}.flow-nav{display:flex;align-items:center;gap:3px;margin-bottom:15px;padding:9px 14px;border:1px solid #e3eaf3;border-radius:10px;background:#fbfdff;color:#718198;font-size:12px}.flow-nav span{margin-right:6px;font-weight:700;color:#65758b}.data-card{border:1px solid #e5ebf3;border-radius:12px}.card-head{display:flex;align-items:center;justify-content:space-between;color:#304563;font-size:14px;font-weight:700}.card-head small{font-size:11px;color:#9ba9b9;font-weight:400}.form-note{margin:-4px 0 12px;padding:9px 11px;border-radius:7px;background:#f2f7ff;color:#6c7d91;font-size:12px}.form-note b{color:#f56c6c}.work-form :deep(.el-form-item){margin-bottom:14px}.work-form :deep(.el-form-item__label){padding-bottom:5px;color:#53657e;font-weight:600}@media(max-width:760px){.hero{display:block}.hero-actions{padding-top:12px}.flow-nav{overflow:auto}.work-form :deep(.el-col){width:100%;max-width:100%;flex:0 0 100%}}
</style>
