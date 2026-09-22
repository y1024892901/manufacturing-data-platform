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
    { path: 'approval', component: () => import('../views/ApprovalCenterView.vue') },
    { path: 'events', component: () => import('../modules/platform/EventMonitorView.vue') },
    { path: 'audit', component: () => import('../modules/platform/AuditLogView.vue'), meta: { admin: true } },
    { path: 'operations', component: () => import('../views/OperationsView.vue') }
  ] },
  systemRoute('mdm', () => import('../modules/mdm/HomeView.vue'), [
    { path: 'materials', component: () => import('../views/MaterialView.vue') },
    { path: 'products', component: () => import('../modules/mdm/ProductView.vue') },
    { path: 'boms', component: () => import('../views/BomView.vue') },
    { path: 'routings', component: () => import('../modules/mdm/RoutingView.vue') },
    { path: 'customers', component: () => import('../modules/mdm/PartnerView.vue'), props: { kind: 'customers' } },
    { path: 'suppliers', component: () => import('../modules/mdm/PartnerView.vue'), props: { kind: 'suppliers' } },
    { path: 'warehouses', component: () => import('../modules/mdm/MdmResourceView.vue'), props: { kind: 'warehouses' } },
    { path: 'work-centers', component: () => import('../modules/mdm/MdmResourceView.vue'), props: { kind: 'work-centers' } },
    { path: 'production-versions', component: () => import('../modules/mdm/MdmResourceView.vue'), props: { kind: 'production-versions' } },
    { path: 'governance', component: () => import('../modules/mdm/GovernanceView.vue') },
    { path: 'categories', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'categories' } },
    { path: 'units', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'units' } },
    { path: 'organizations', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'organizations' } },
    { path: 'cost-centers', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'cost-centers' } },
    { path: 'subjects', component: () => import('../modules/mdm/ReferenceDataView.vue'), props: { kind: 'subjects' } }
  ]),
  systemRoute('crm', () => import('../modules/crm/HomeView.vue'), [
    { path:'customers',component:()=>import('../modules/crm/Customer360View.vue') },
    { path:'leads',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'leads'} },
    { path:'opportunities',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'opportunities'} },
    { path:'quotations',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'quotations'} },
    { path:'contracts',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'contracts'} },
    { path:'forecast',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'forecast'} },
    { path:'complaints',component:()=>import('../modules/crm/CrmP2View.vue'),props:{kind:'complaints'} }
  ]),
  systemRoute('erp', () => import('../modules/erp/HomeView.vue'), [
    { path:'sales-orders',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'sales'} },
    { path:'credits',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'credits'} },
    { path:'atp',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'sales'} },
    { path:'mrp',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'mrp'} },
    { path:'suggestions',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'suggestions'} },
    { path:'production-orders',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'production'} },
    { path:'receivables',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'receivables'} },
    { path:'invoices',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'invoices'} },
    { path:'receipts',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'receipts'} },
    { path:'payables',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'payables'} },
    { path:'vouchers',component:()=>import('../modules/erp/ErpP2View.vue'),props:{kind:'vouchers'} },
    { path:'margins',component:()=>import('../modules/erp/MarginView.vue') }
  ]),
  systemRoute('plm', () => import('../modules/plm/HomeView.vue'), [
    { path: 'families', component: () => import('../modules/plm/PlmCatalogView.vue'), props: { kind: 'families' } },
    { path: 'products', component: () => import('../modules/plm/PlmCatalogView.vue'), props: { kind: 'versions' } },
    { path: 'baselines', component: () => import('../modules/plm/PlmCatalogView.vue'), props: { kind: 'baselines' } },
    { path: 'boms', component: () => import('../modules/plm/PlmBomView.vue') },
    { path: 'ecns', component: () => import('../modules/plm/ChangeChainView.vue') },
    { path: 'documents', component: () => import('../modules/plm/PlmCatalogView.vue'), props: { kind: 'documents' } }
  ]),
  systemRoute('srm', () => import('../modules/srm/HomeView.vue'), [
    {path:'onboarding',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'onboarding',title:'供应商准入'}},
    {path:'rfq',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'rfqs',title:'询报价与定标'}},
    {path:'purchase-orders',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'purchase-orders',title:'采购订单'}},
    {path:'deliveries',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'asns',title:'ASN到货协同'}},
    {path:'quality',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'supplier-quality',title:'供应商质量'}},
    {path:'performance',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'srm',kind:'performance',title:'供应商绩效'}}
  ]),
  systemRoute('wms', () => import('../modules/wms/HomeView.vue'), [
    {path:'locations',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'putaway',title:'库位与上架'}},{path:'receipts',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'receipts',title:'收货待检'}},{path:'inventory',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'inventories',title:'库存余额'}},{path:'transactions',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'inventory-actions',title:'库存状态操作'}},{path:'transfers',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'transfers',title:'库存调拨'}},{path:'counting',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'wms',kind:'counts',title:'盘点作业'}}
  ]),
  systemRoute('mes', () => import('../modules/mes/HomeView.vue'), [
    workspace('work-orders', 'mes'),
    placeholder('dispatch', '排产与派工', '按产能、班次、人员和设备执行有限排产与派工。', ['排产看板', '班次', '人员', '设备资源']),
    workspace('reports', 'mesReports'),
    placeholder('andon', 'Andon 异常', '登记缺料、质量、设备和安全异常并跟踪闭环。', ['异常呼叫', '响应', '停线影响', '闭环'])
  ]),
  systemRoute('qms', () => import('../modules/qms/HomeView.vue'), [
    {path:'standards',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'standards',title:'检验标准'}},{path:'sampling',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'sampling-plans',title:'抽样方案'}},{path:'inspections',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'inspections',title:'质量检验'}},{path:'defects',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'ncrs',title:'不合格评审'}},{path:'reworks',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'reworks',title:'返工作业'}},{path:'capas',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'capas',title:'CAPA'}},{path:'8d',component:()=>import('../shared/components/P3BusinessView.vue'),props:{system:'qms',kind:'8d',title:'供应商8D'}}
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
