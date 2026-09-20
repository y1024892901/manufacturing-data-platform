import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const LoginView = () => import('../views/LoginView.vue')
const PortalLayout = () => import('../layouts/PortalLayout.vue')
const SystemLayout = () => import('../layouts/SystemLayout.vue')
const Placeholder = () => import('../shared/components/ModulePlaceholder.vue')
const Workspace = () => import('../views/BusinessWorkspaceView.vue')

const placeholder = (path: string, title: string, description: string, items: string[] = []): RouteRecordRaw => ({ path, component: Placeholder, props: { title, description, items } })
const workspace = (path: string, kind: string): RouteRecordRaw => ({ path, component: Workspace, props: { kind } })
const systemRoute = (code: string, home: () => Promise<unknown>, children: RouteRecordRaw[]): RouteRecordRaw => ({
  path: `/${code}`, component: SystemLayout, meta: { system: code },
  children: [{ path: '', redirect: `/${code}/dashboard` }, { path: 'dashboard', component: home }, ...children]
})

const routes: RouteRecordRaw[] = [
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/', redirect: '/portal' },
  { path: '/portal', component: PortalLayout, children: [
    { path: '', component: () => import('../modules/platform/PortalHome.vue') },
    { path: 'users', component: () => import('../modules/platform/UserAdminView.vue'), meta: { admin: true } },
    { path: 'roles', component: () => import('../modules/platform/ReferenceAdminView.vue'), props: { kind: 'roles' }, meta: { admin: true } },
    { path: 'organization', component: () => import('../modules/platform/ReferenceAdminView.vue'), props: { kind: 'organization' }, meta: { admin: true } },
    // { path: 'approval', component: () => import('../views/ApprovalCenterView.vue') },
    { path: 'events', component: () => import('../modules/platform/EventMonitorView.vue') },
    { path: 'audit', component: () => import('../modules/platform/AuditLogView.vue'), meta: { admin: true } },
    { path: 'operations', component: () => import('../views/OperationsView.vue') }
  ] },
  systemRoute('mdm', () => import('../modules/mdm/HomeView.vue'), [
    { path: 'materials', component: () => import('../views/MaterialView.vue') },
    { path: 'boms', component: () => import('../views/BomView.vue') },
    workspace('customers', 'mdmCustomers'), workspace('suppliers', 'mdmSuppliers'),
    { path: 'categories', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'categories' } },
    { path: 'units', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'units' } },
    { path: 'organizations', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'organizations' } },
    { path: 'cost-centers', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'cost-centers' } },
    { path: 'subjects', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'subjects' } }
  ]),
  systemRoute('crm', () => import('../modules/crm/HomeView.vue'), [
    placeholder('customers', '客户 360', '汇总客户画像、联系人、交易、回款、服务和风险。', ['客户画像', '联系人', '交易历史', '信用风险']),
    placeholder('leads', '销售线索', '完成线索登记、分配、跟进、评分和转商机。', ['线索池', '分配', '跟进', '转商机']),
    workspace('opportunities', 'crm'),
    placeholder('quotations', '报价与合同', '管理价格版本、折扣审批、合同、回款计划和变更。', ['报价版本', '折扣审批', '销售合同', '回款计划'])
  ]),
  systemRoute('erp', () => import('../modules/erp/HomeView.vue'), [
    workspace('sales-orders', 'erp'), workspace('production-orders', 'production'),
    placeholder('mrp', 'MRP 与齐套计划', '根据销售需求、库存、BOM 和在途计算净需求。', ['净需求', '采购建议', '生产建议', '齐套检查']),
    workspace('vouchers', 'erpFinance')
  ]),
  systemRoute('plm', () => import('../modules/plm/HomeView.vue'), [
    placeholder('products', '产品族与产品版本', '管理产品族、产品版本、生命周期和工程基线。', ['产品族', '产品版本', '生命周期', '工程基线']),
    placeholder('boms', 'EBOM / MBOM', '维护设计 BOM、制造 BOM、差异转换和版本比较。', ['EBOM', 'MBOM', '版本比较', '差异转换']),
    { path: 'ecns', component: () => import('../views/EcnWorkspaceView.vue') },
    placeholder('documents', '图纸与受控文档', '管理图纸、工艺卡、作业指导书和受控发布。', ['图纸', '工艺卡', '作业指导书', '版本受控'])
  ]),
  systemRoute('srm', () => import('../modules/srm/HomeView.vue'), [
    placeholder('onboarding', '供应商准入', '管理资质、认证、现场审核、黑名单和准入审批。', ['资质档案', '认证', '现场审核', '黑名单']),
    placeholder('rfq', '询报价与定标', '完成 RFQ、供应商报价、比价、澄清和定标。', ['RFQ', '报价', '比价', '定标']),
    workspace('purchase-orders', 'srmPo'), workspace('deliveries', 'srmDelivery')
  ]),
  systemRoute('wms', () => import('../modules/wms/HomeView.vue'), [
    workspace('locations', 'wmsLocations'), workspace('inventory', 'wmsInventory'), workspace('transactions', 'wmsTxn'),
    placeholder('counting', '盘点与库存调整', '生成盘点任务，复核差异并形成盘盈盘亏流水。', ['盘点任务', '扫码实盘', '差异复核', '库存调整'])
  ]),
  systemRoute('mes', () => import('../modules/mes/HomeView.vue'), [
    workspace('work-orders', 'mes'),
    placeholder('dispatch', '排产与派工', '按产能、班次、人员和设备执行有限排产与派工。', ['排产看板', '班次', '人员', '设备资源']),
    workspace('reports', 'mesReports'),
    placeholder('andon', 'Andon 异常', '登记缺料、质量、设备和安全异常并跟踪闭环。', ['异常呼叫', '响应', '停线影响', '闭环'])
  ]),
  systemRoute('qms', () => import('../modules/qms/HomeView.vue'), [
    placeholder('standards', '检验标准', '维护检验项目、AQL、抽样方案和判定规则。', ['检验项目', 'AQL', '抽样方案', '判定规则']),
    workspace('inspections', 'qms'), workspace('defects', 'qmsDefect'), workspace('reworks', 'qmsRework')
  ]),
  systemRoute('eam', () => import('../modules/eam/HomeView.vue'), [
    workspace('equipments', 'eamEquipment'), workspace('inspections', 'eamInspection'), workspace('faults', 'eamFault'), workspace('repairs', 'eamRepair')
  ]),
  systemRoute('energy', () => import('../modules/energy/HomeView.vue'), [
    placeholder('meters', '能源仪表与采集点', '管理能源品类、仪表、采集点、计量层级和校验规则。', ['仪表台账', '采集点', '计量层级', '补录校验']),
    workspace('equipment', 'energy'), workspace('workshops', 'energyWorkshop'),
    placeholder('alerts', '能耗异常告警', '识别偏离定额、基线与峰谷策略的异常并跟踪处置。', ['阈值规则', '告警', '责任人', '处置闭环'])
  ]),
  { path: '/ops/:kind', redirect: to => ({ path: ({
    mdmCustomers:'/mdm/customers', mdmSuppliers:'/mdm/suppliers', crm:'/crm/opportunities',
    erp:'/erp/sales-orders', erpFinance:'/erp/vouchers', production:'/erp/production-orders',
    mes:'/mes/work-orders', mesReports:'/mes/reports', qms:'/qms/inspections',
    qmsDefect:'/qms/defects', qmsRework:'/qms/reworks', wmsInventory:'/wms/inventory',
    wmsLocations:'/wms/locations', wmsTxn:'/wms/transactions', srmPo:'/srm/purchase-orders',
    srmDelivery:'/srm/deliveries', eamEquipment:'/eam/equipments', eamFault:'/eam/faults',
    eamRepair:'/eam/repairs', eamInspection:'/eam/inspections', energy:'/energy/equipment',
    energyWorkshop:'/energy/workshops'
  } as Record<string,string>)[String(to.params.kind)] || '/portal' }) },
  { path: '/:pathMatch(.*)*', redirect: '/portal' }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(async to => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.token ? '/portal' : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try { await auth.loadMe() } catch { auth.clearLocal(); return '/login' }
  }
  if (to.meta.admin && !auth.isAdmin) return { path: '/portal', query: { denied: 'admin' } }
  const system = String(to.meta.system || '')
  if (system && !auth.canAccess(system)) return { path: '/portal', query: { denied: system } }
  return true
})
export default router
