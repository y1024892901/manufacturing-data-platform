<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { systemByCode } from '../systemCatalog'

const props = defineProps<{ system: string }>()
const router = useRouter()
const info = computed(() => systemByCode(props.system)!)
const menu = (route: string) => info.value.menus.find((item) => item.route === route)
const links = (routes: string[]) => routes.map(menu).filter((item): item is NonNullable<typeof item> => Boolean(item))
const enter = (route: string) => router.push(route)
const registryGroups = [
  { label: '客户与伙伴', route: '/mdm/customers' },
  { label: '物料与产品', route: '/mdm/materials' },
  { label: '制造定义', route: '/mdm/boms' },
]
const headings: Record<string, { eyebrow: string; title: string; description: string }> = {
  mdm: { eyebrow: 'DATA GOVERNANCE / 01', title: '主数据目录', description: '按数据域维护共享对象，保持编码、版本和跨系统引用一致。' },
  crm: { eyebrow: 'REVENUE WORKSPACE', title: '销售机会工作台', description: '从客户触达到签约回款，按销售阶段推进客户关系。' },
  erp: { eyebrow: 'FINANCE & OPERATIONS', title: '经营账簿', description: '把订单、计划、应收和成本放在同一条经营链路中处理。' },
  plm: { eyebrow: 'ENGINEERING CHANGE / ECR → ECO', title: '产品定义工作区', description: '围绕产品结构和工程变更，串联版本、基线与受控文档。' },
  srm: { eyebrow: 'SOURCE TO DELIVER', title: '供应协同台', description: '从供应商准入到采购交付，沿寻源流程处理协作事项。' },
  wms: { eyebrow: 'WAREHOUSE FLOOR / ZONE VIEW', title: '库区作业图', description: '从库区位置进入收货、上架、库存、调拨和盘点任务。' },
  mes: { eyebrow: 'SHOP FLOOR / ANDON CONSOLE', title: '生产现场控制台', description: '围绕工单、派工、报工和异常响应组织车间作业。' },
  qms: { eyebrow: 'QUALITY LOOP / PREVENT → CORRECT', title: '质量闭环工作区', description: '从检验标准开始，将不合格评审、返工与纠正措施连成闭环。' },
  eam: { eyebrow: 'ASSET CARE / PLAN → REPAIR', title: '设备维护计划', description: '用设备台账、点检、故障和维修任务维持资产可靠性。' },
  energy: { eyebrow: 'ENERGY FLOW / METER → AREA', title: '能源流向图', description: '从计量入口追踪到设备与车间，并在同一视图处理用能告警。' },
}
const heading = computed(() => headings[props.system] || headings.mdm)
</script>

<template>
  <section class="workspace-home" :class="`workspace-home--${system}`">
    <template v-if="system === 'mdm'">
      <header class="home-heading home-heading--registry">
        <div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div>
        <div class="registry-stamp"><b>MDM</b><span>ONE SOURCE<br />OF TRUTH</span></div>
      </header>
      <div class="registry-layout">
        <aside class="registry-index"><span class="eyebrow">DATA DOMAINS</span><button v-for="(group, i) in registryGroups" :key="group.route" @click="enter(group.route)"><b>0{{ i + 1 }}</b><span>{{ group.label }}</span><i>↗</i></button><button class="index-governance" @click="enter('/mdm/governance')"><b>+</b><span>治理规则与质量</span><i>→</i></button></aside>
        <div class="registry-main"><div class="section-heading"><div><span class="eyebrow">AUTHORITATIVE RECORDS</span><h2>共享数据对象</h2></div><span class="read-only">受控维护 · 版本可追溯</span></div><div class="domain-grid"><button v-for="item in links(['/mdm/customers','/mdm/suppliers','/mdm/materials','/mdm/products','/mdm/boms','/mdm/routings'])" :key="item.route" @click="enter(item.route)"><span class="domain-glyph">{{ item.label.slice(0, 1) }}</span><b>{{ item.label }}</b><small>查看对象与版本记录</small><i>打开目录 →</i></button></div><div class="registry-foot"><span>REFERENCE DATA</span><button @click="enter('/mdm/categories')">物料分类 <i>→</i></button><button @click="enter('/mdm/units')">计量单位 <i>→</i></button><button @click="enter('/mdm/organizations')">组织架构 <i>→</i></button></div></div>
      </div>
    </template>

    <template v-else-if="system === 'crm'">
      <header class="home-heading home-heading--crm"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><button class="crm-primary" @click="enter('/crm/customer-entry')">＋ 新建客户</button></header>
      <div class="crm-stagebar"><span>销售流程</span><b>需求识别</b><i>→</i><b>机会评估</b><i>→</i><b>方案与报价</b><i>→</i><b>合同签约</b><span class="stage-note">工作台 · 按阶段推进</span></div>
      <div class="crm-board"><section v-for="stage in [{title:'发现客户',sub:'CUSTOMER / LEAD',routes:['/crm/customers','/crm/leads']},{title:'推进机会',sub:'OPPORTUNITY',routes:['/crm/opportunities','/crm/forecast']},{title:'方案报价',sub:'QUOTE / APPROVAL',routes:['/crm/quotations','/crm/approvals']},{title:'签约服务',sub:'CONTRACT / SERVICE',routes:['/crm/contracts','/crm/complaints']}]" :key="stage.title" class="crm-lane"><div class="lane-head"><span>{{ stage.sub }}</span><b>{{ stage.title }}</b><i>···</i></div><button v-for="item in links(stage.routes)" :key="item.route" class="crm-opportunity" @click="enter(item.route)"><span class="opportunity-dot"></span><b>{{ item.label }}</b><small>{{ item.route.endsWith('forecast') ? '销售计划与阶段预测' : '进入业务队列与客户记录' }}</small><i>查看工作区 →</i></button><button v-if="stage.title === '发现客户'" class="crm-add" @click="enter('/crm/customer-entry')">＋ 新建客户档案</button></section></div>
      <div class="crm-footer"><span>客户洞察</span><button @click="enter('/crm/customers')">打开客户 360 <i>↗</i></button><span class="crm-foot-note">关系记录 · 跟进历史 · 当前商机</span></div>
    </template>

    <template v-else-if="system === 'erp'">
      <header class="home-heading home-heading--ledger"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><div class="ledger-period"><span>FINANCIAL WORKSPACE</span><b>经营链路 / 总览</b></div></header>
      <div class="ledger-flow"><span class="ledger-label">ORDER TO CASH</span><div class="ledger-steps"><button v-for="(item, i) in links(['/erp/sales-orders','/erp/mrp','/erp/production-orders','/erp/receivables','/erp/receipts'])" :key="item.route" @click="enter(item.route)"><small>0{{ i + 1 }} / POSTING</small><b>{{ item.label }}</b><span>{{ ['需求录入','供需平衡','生产执行','应收核对','回款核销'][i] }}</span><i>打开分录 →</i></button></div></div>
      <div class="ledger-bottom"><section class="ledger-register"><div class="ledger-section-title"><span>FINANCIAL REGISTER</span><b>账务与核算</b></div><button v-for="item in links(['/erp/invoices','/erp/payables','/erp/vouchers','/erp/margins'])" :key="item.route" @click="enter(item.route)"><span>{{ item.label }}</span><i>打开台账　→</i></button></section><section class="ledger-control"><span>PLANNING CONTROL</span><b>需求计划与承诺</b><p>先校验可承诺量，再生成计划建议并下达生产订单。</p><div><button @click="enter('/erp/atp')">交期承诺</button><button @click="enter('/erp/suggestions')">计划建议</button><button @click="enter('/erp/credits')">信用管理</button></div></section></div>
    </template>

    <template v-else-if="system === 'plm'">
      <header class="home-heading home-heading--plm"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><div class="revision-mark"><span>REVISION CONTROL</span><b>Engineering<br />Workspace</b></div></header>
      <div class="plm-workbench"><aside class="plm-tree"><div class="tree-title"><span>PRODUCT EXPLORER</span><b>产品结构</b></div><button class="tree-root" @click="enter('/plm/families')">⌄ 产品族与系列</button><button v-for="item in links(['/plm/products','/plm/boms'])" :key="item.route" class="tree-child" @click="enter(item.route)"><span>◇</span>{{ item.label }}<i>›</i></button><div class="tree-separator"></div><button v-for="item in links(['/plm/baselines','/plm/documents'])" :key="item.route" class="tree-child" @click="enter(item.route)"><span>▤</span>{{ item.label }}<i>›</i></button></aside><section class="plm-canvas"><div class="canvas-toolbar"><span>ENGINEERING BASELINE / PRODUCT STRUCTURE</span><button @click="enter('/plm/boms')">打开 BOM 编辑器 ↗</button></div><div class="bom-branch"><button @click="enter('/plm/products')"><small>PRODUCT FAMILY</small><b>产品版本</b><span>受控定义 · 版本快照</span></button><i>┬</i><div class="branch-children"><button @click="enter('/plm/boms')"><small>ENGINEERING BOM</small><b>设计结构</b><span>组件与用量关系</span></button><button @click="enter('/plm/boms')"><small>MANUFACTURING BOM</small><b>制造结构</b><span>装配层级与工序引用</span></button></div></div><div class="change-callout"><span>ENGINEERING CHANGE</span><b>变更从提案进入评估、批准与基线发布</b><button @click="enter('/plm/ecns')">打开工程变更链 →</button></div></section></div>
    </template>

    <template v-else-if="system === 'srm'">
      <header class="home-heading home-heading--srm"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><div class="srm-seal"><span>SUPPLY</span><b>协同采购</b><span>PARTNER NETWORK</span></div></header>
      <div class="srm-journey"><span class="eyebrow">SUPPLIER LIFECYCLE</span><div class="journey-steps"><button v-for="(item, i) in links(['/srm/onboarding','/srm/qualifications','/srm/rfq','/srm/quotes','/srm/purchase-orders','/srm/deliveries'])" :key="item.route" @click="enter(item.route)"><small>PHASE 0{{ i + 1 }}</small><b>{{ item.label }}</b><i>进入 →</i></button></div></div>
      <div class="srm-lower"><section class="srm-vendor"><span class="eyebrow">SUPPLIER RELATIONSHIP</span><h2>伙伴档案</h2><p>从准入资料、资质审核到合作表现，沉淀完整供应商关系。</p><button @click="enter('/srm/onboarding')">供应商准入 <i>↗</i></button><button @click="enter('/srm/performance')">绩效评估 <i>↗</i></button></section><section class="srm-collab"><span class="eyebrow">COLLABORATION DESK</span><h2>订单与质量协同</h2><div><button v-for="item in links(['/srm/quality','/srm/deliveries'])" :key="item.route" @click="enter(item.route)">{{ item.label }} <i>→</i></button></div></section></div>
    </template>

    <template v-else-if="system === 'wms'">
      <header class="home-heading home-heading--wms"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><button class="warehouse-action" @click="enter('/wms/locations')">查看库位明细 <i>↗</i></button></header>
      <div class="warehouse-layout"><section class="warehouse-map"><div class="map-head"><div><span class="eyebrow">WAREHOUSE GRID / LOCATION INDEX</span><h2>作业区域</h2></div><span class="map-key"><i></i>库位索引</span></div><div class="map-body"><div class="map-aisle-labels"><span v-for="aisle in ['A','B','C','D','E','F']" :key="aisle">{{ aisle }}</span></div><div class="location-grid"><button v-for="slot in 36" :key="slot" :class="{ 'is-crossing': slot % 7 === 0 }" :aria-label="`库位索引 ${slot}`" @click="enter('/wms/locations')"><span>{{ String.fromCharCode(65 + Math.floor((slot - 1) / 6)) }}-{{ String((slot - 1) % 6 + 1).padStart(2, '0') }}</span></button></div></div><div class="map-legend"><span>收货区</span><span>存储巷道</span><span>出库月台</span></div></section><aside class="warehouse-ops"><span class="eyebrow">EXECUTION QUEUE</span><h2>仓内作业</h2><button v-for="item in links(['/wms/receipts','/wms/putaway','/wms/inventory','/wms/transfers','/wms/counting','/wms/transactions'])" :key="item.route" @click="enter(item.route)"><b>{{ item.label.slice(0, 1) }}</b><span>{{ item.label }}</span><i>→</i></button></aside></div>
    </template>

    <template v-else-if="system === 'mes'">
      <header class="home-heading home-heading--mes"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><span class="andon-indicator"><i></i> SHOP FLOOR</span></header>
      <div class="mes-console"><section class="mes-primary"><div class="console-top"><span>WORK ORDER EXECUTION</span><b>生产单元</b></div><div class="mes-workorder"><span class="eyebrow">DISPATCH / REPORT / TRACE</span><h2>车间执行</h2><p>从排产派工进入工序执行，再将完工报工回写到工单。</p><div class="mes-sequence"><button v-for="(item, i) in links(['/mes/work-orders','/mes/dispatch','/mes/reports'])" :key="item.route" @click="enter(item.route)"><small>STEP 0{{ i + 1 }}</small><b>{{ item.label }}</b><span>{{ ['工单领用','工序派发','产量与工时'][i] }}</span><i>进入模块 →</i></button></div></div></section><aside class="mes-andon"><span class="eyebrow">ANDON RESPONSE</span><div class="andon-ring"><b>!</b><span>异常响应</span></div><h2>异常呼叫中心</h2><p>按现场问题登记、分派并追踪关闭过程。</p><button @click="enter('/mes/andon')">打开 Andon 面板 <i>↗</i></button></aside></div>
      <div class="mes-bottom"><span>生产闭环</span><b>计划下达</b><i>→</i><b>派工执行</b><i>→</i><b>工序报工</b><i>→</i><b>追溯归档</b></div>
    </template>

    <template v-else-if="system === 'qms'">
      <header class="home-heading home-heading--qms"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><button class="quality-cta" @click="enter('/qms/inspections')">进入检验工作区 <i>↗</i></button></header>
      <div class="quality-loop"><div class="loop-heading"><span>CONTROL PLAN / QUALITY LOOP</span><b>预防 → 检测 → 纠正 → 改进</b></div><div class="quality-steps"><button v-for="(item, i) in links(['/qms/standards','/qms/inspections','/qms/ncrs','/qms/capas'])" :key="item.route" @click="enter(item.route)"><span class="quality-index">0{{ i + 1 }}</span><b>{{ item.label }}</b><small>{{ ['标准与抽样规则','来料 / 过程 / 成品','评审与处置','纠正预防措施'][i] }}</small><i>{{ i < 3 ? '向下游传递 ↓' : '回到标准更新 ↺' }}</i></button></div></div>
      <div class="quality-support"><section><span class="eyebrow">NONCONFORMANCE HANDLING</span><h2>不合格处置</h2><p>记录缺陷，评审风险，再决定返工、让步或供应商纠正。</p><button v-for="item in links(['/qms/defects','/qms/reworks'])" :key="item.route" @click="enter(item.route)">{{ item.label }} <i>→</i></button></section><section class="quality-supplier"><span class="eyebrow">SUPPLIER CORRECTIVE ACTION</span><h2>供应商质量协同</h2><p>将外部问题纳入同一质量闭环。</p><button @click="enter('/qms/8d')">供应商 8D 协同 <i>↗</i></button></section></div>
    </template>

    <template v-else-if="system === 'eam'">
      <header class="home-heading home-heading--eam"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><button class="eam-action" @click="enter('/eam/equipments')">浏览资产台账 <i>↗</i></button></header>
      <div class="maintenance-layout"><section class="asset-rail"><span class="eyebrow">ASSET REGISTER</span><h2>资产域</h2><button @click="enter('/eam/equipments')"><b>01</b><span>设备台账</span><i>→</i></button><div class="asset-separator"></div><span class="eyebrow">CARE CYCLE</span><button v-for="(item, i) in links(['/eam/inspections','/eam/faults','/eam/repairs'])" :key="item.route" @click="enter(item.route)"><b>0{{ i + 2 }}</b><span>{{ item.label }}</span><i>→</i></button></section><section class="maintenance-plan"><div class="plan-title"><div><span class="eyebrow">MAINTENANCE ROUTINE</span><h2>设备可靠性计划</h2></div><span>维护路径</span></div><div class="maintenance-timeline"><div v-for="(item, i) in links(['/eam/inspections','/eam/faults','/eam/repairs'])" :key="item.route" class="timeline-item"><span class="timeline-node">0{{ i + 1 }}</span><div><b>{{ item.label }}</b><p>{{ ['周期点检与结果记录','故障分级与维修申请','维修执行与完工归档'][i] }}</p><button @click="enter(item.route)">打开任务簿 →</button></div></div></div><div class="maintenance-note"><span>备件关联</span><button @click="enter('/eam/repairs')">在维修任务中查看备件需求 ↗</button></div></section></div>
    </template>

    <template v-else>
      <header class="home-heading home-heading--energy"><div><span class="eyebrow">{{ heading.eyebrow }}</span><h1>{{ heading.title }}</h1><p>{{ heading.description }}</p></div><button class="energy-action" @click="enter('/energy/alerts')">查看用能告警 <i>↗</i></button></header>
      <div class="energy-flow"><section class="energy-input"><span class="eyebrow">METERING</span><h2>计量入口</h2><button @click="enter('/energy/meters')"><span class="flow-icon">⌁</span><b>能源仪表</b><small>采集与计量点</small><i>打开仪表域 →</i></button><div class="flow-lines"><i></i><i></i><i></i></div></section><section class="energy-hub"><span class="eyebrow">ENERGY MANAGEMENT</span><div class="hub-orbit"><span>ENERGY</span><b>用能<br />中心</b></div><small>计量 · 分摊 · 告警</small></section><section class="energy-uses"><span class="eyebrow">CONSUMPTION AREAS</span><h2>用能去向</h2><button v-for="item in links(['/energy/equipment','/energy/workshops'])" :key="item.route" @click="enter(item.route)"><span class="use-mark">∿</span><b>{{ item.label }}</b><small>按区域查看用能明细</small><i>查看 →</i></button></section></div>
      <div class="energy-footer"><span>异常治理</span><b>告警发现</b><i>→</i><b>定位用能区域</b><i>→</i><b>跟进节能措施</b><button @click="enter('/energy/alerts')">打开告警中心 ↗</button></div>
    </template>
  </section>
</template>

<style scoped>
.workspace-home { --work-accent: var(--vyre-interactive-primary); --work-page: var(--vyre-surface-background); --work-panel: var(--vyre-surface-card); --work-soft: var(--vyre-surface-subtle); --work-ink: var(--vyre-content-primary); --work-muted: var(--vyre-content-secondary); --work-border: var(--vyre-border-subtle); --work-rail: var(--vyre-color-navy-950); max-width: var(--vyre-layout-content-max); margin: 0 auto; color: var(--work-ink); }
.workspace-home--mdm { --work-accent: var(--vyre-system-mdm-accent); --work-page: var(--vyre-system-mdm-page); --work-panel: var(--vyre-system-mdm-panel); --work-soft: var(--vyre-system-mdm-soft); --work-ink: var(--vyre-system-mdm-ink); --work-muted: var(--vyre-system-mdm-muted); --work-border: var(--vyre-system-mdm-border); --work-rail: var(--vyre-system-mdm-rail); }
.workspace-home--crm { --work-accent: var(--vyre-system-crm-accent); --work-page: var(--vyre-system-crm-page); --work-panel: var(--vyre-system-crm-panel); --work-soft: var(--vyre-system-crm-soft); --work-ink: var(--vyre-system-crm-ink); --work-muted: var(--vyre-system-crm-muted); --work-border: var(--vyre-system-crm-border); --work-rail: var(--vyre-system-crm-rail); }
.workspace-home--erp { --work-accent: var(--vyre-system-erp-accent); --work-page: var(--vyre-system-erp-page); --work-panel: var(--vyre-system-erp-panel); --work-soft: var(--vyre-system-erp-soft); --work-ink: var(--vyre-system-erp-ink); --work-muted: var(--vyre-system-erp-muted); --work-border: var(--vyre-system-erp-border); --work-rail: var(--vyre-system-erp-rail); }
.workspace-home--plm { --work-accent: var(--vyre-system-plm-accent); --work-page: var(--vyre-system-plm-page); --work-panel: var(--vyre-system-plm-panel); --work-soft: var(--vyre-system-plm-soft); --work-ink: var(--vyre-system-plm-ink); --work-muted: var(--vyre-system-plm-muted); --work-border: var(--vyre-system-plm-border); --work-rail: var(--vyre-system-plm-rail); }
.workspace-home--srm { --work-accent: var(--vyre-system-srm-accent); --work-page: var(--vyre-system-srm-page); --work-panel: var(--vyre-system-srm-panel); --work-soft: var(--vyre-system-srm-soft); --work-ink: var(--vyre-system-srm-ink); --work-muted: var(--vyre-system-srm-muted); --work-border: var(--vyre-system-srm-border); --work-rail: var(--vyre-system-srm-rail); }
.workspace-home--wms { --work-accent: var(--vyre-system-wms-accent); --work-page: var(--vyre-system-wms-page); --work-panel: var(--vyre-system-wms-panel); --work-soft: var(--vyre-system-wms-soft); --work-ink: var(--vyre-system-wms-ink); --work-muted: var(--vyre-system-wms-muted); --work-border: var(--vyre-system-wms-border); --work-rail: var(--vyre-system-wms-rail); }
.workspace-home--mes { --work-accent: var(--vyre-system-mes-accent); --work-page: var(--vyre-system-mes-page); --work-panel: var(--vyre-system-mes-panel); --work-soft: var(--vyre-system-mes-soft); --work-ink: var(--vyre-system-mes-ink); --work-muted: var(--vyre-system-mes-muted); --work-border: var(--vyre-system-mes-border); --work-rail: var(--vyre-system-mes-rail); }
.workspace-home--qms { --work-accent: var(--vyre-system-qms-accent); --work-page: var(--vyre-system-qms-page); --work-panel: var(--vyre-system-qms-panel); --work-soft: var(--vyre-system-qms-soft); --work-ink: var(--vyre-system-qms-ink); --work-muted: var(--vyre-system-qms-muted); --work-border: var(--vyre-system-qms-border); --work-rail: var(--vyre-system-qms-rail); }
.workspace-home--eam { --work-accent: var(--vyre-system-eam-accent); --work-page: var(--vyre-system-eam-page); --work-panel: var(--vyre-system-eam-panel); --work-soft: var(--vyre-system-eam-soft); --work-ink: var(--vyre-system-eam-ink); --work-muted: var(--vyre-system-eam-muted); --work-border: var(--vyre-system-eam-border); --work-rail: var(--vyre-system-eam-rail); }
.workspace-home--energy { --work-accent: var(--vyre-system-energy-accent); --work-page: var(--vyre-system-energy-page); --work-panel: var(--vyre-system-energy-panel); --work-soft: var(--vyre-system-energy-soft); --work-ink: var(--vyre-system-energy-ink); --work-muted: var(--vyre-system-energy-muted); --work-border: var(--vyre-system-energy-border); --work-rail: var(--vyre-system-energy-rail); }
.workspace-home button { min-width: 0; border: 1px solid var(--work-border); background: var(--work-panel); color: var(--work-ink); font: inherit; text-align: left; cursor: pointer; transition: border-color var(--vyre-motion-fast) ease, transform var(--vyre-motion-fast) ease, background-color var(--vyre-motion-fast) ease; }
.workspace-home button:hover { border-color: var(--work-accent); transform: translateY(-2px); }
.workspace-home button:focus-visible { outline: 3px solid var(--vyre-ring-default); outline-offset: 2px; }
.eyebrow { display: block; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-weight: 750; letter-spacing: .11em; }
.home-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--vyre-space-6); margin-bottom: var(--vyre-space-6); }
.home-heading h1 { margin-top: var(--vyre-space-2); color: var(--work-ink); font-size: var(--vyre-font-size-display); font-weight: 760; letter-spacing: -.035em; }
.home-heading p { max-width: 760px; margin-top: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-sm); line-height: 1.7; }
.home-heading button, .crm-primary, .warehouse-action, .quality-cta, .eam-action, .energy-action { padding: var(--vyre-space-3) var(--vyre-space-4); border-radius: var(--vyre-radius-sm); background: var(--work-accent); color: var(--vyre-interactive-primary-foreground); font-size: var(--vyre-font-size-sm); font-weight: 700; white-space: nowrap; }
.home-heading--registry { padding: var(--vyre-space-5) var(--vyre-space-6); border-left: 4px solid var(--work-accent); background: var(--work-panel); }
.registry-stamp { display: flex; align-items: center; gap: var(--vyre-space-3); color: var(--work-accent); }
.registry-stamp b { font-size: var(--vyre-font-size-display); letter-spacing: -.06em; }
.registry-stamp span { padding-left: var(--vyre-space-3); border-left: 1px solid var(--work-border); font-size: var(--vyre-font-size-xs); line-height: 1.5; letter-spacing: .08em; }
.registry-layout { display: grid; grid-template-columns: minmax(220px, .72fr) minmax(0, 2fr); gap: var(--vyre-space-4); }
.registry-index, .registry-main { padding: var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.registry-index button { display: grid; grid-template-columns: var(--vyre-space-6) 1fr auto; align-items: center; width: 100%; margin-top: var(--vyre-space-3); padding: var(--vyre-space-3); border: 0; border-bottom: 1px solid var(--work-border); background: transparent; }
.registry-index button b { color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.registry-index button span { font-weight: 700; }
.registry-index button i, .registry-foot i { color: var(--work-accent); font-style: normal; }
.registry-index .index-governance { margin-top: var(--vyre-space-6); border: 1px solid var(--work-border); background: var(--work-soft); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--vyre-space-4); margin-bottom: var(--vyre-space-4); }
.section-heading h2, .warehouse-map h2, .warehouse-ops h2, .mes-andon h2, .quality-support h2, .maintenance-plan h2 { margin-top: var(--vyre-space-1); color: var(--work-ink); font-size: var(--vyre-font-size-lg); }
.read-only { padding: var(--vyre-space-2) var(--vyre-space-3); background: var(--work-soft); color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.domain-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: var(--vyre-space-3); }
.domain-grid button { display: grid; grid-template-columns: var(--vyre-space-8) 1fr; align-items: center; gap: var(--vyre-space-1) var(--vyre-space-3); padding: var(--vyre-space-4); text-align: left; }
.domain-glyph { display: grid; grid-row: span 3; place-items: center; width: var(--vyre-space-8); height: var(--vyre-space-8); background: var(--work-soft); color: var(--work-accent); font-weight: 800; }
.domain-grid b { font-size: var(--vyre-font-size-sm); }
.domain-grid small { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.domain-grid i { color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.registry-foot { display: flex; flex-wrap: wrap; align-items: center; gap: var(--vyre-space-3); margin-top: var(--vyre-space-4); padding-top: var(--vyre-space-3); border-top: 1px solid var(--work-border); }
.registry-foot > span { margin-right: auto; color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.registry-foot button { padding: var(--vyre-space-2) var(--vyre-space-3); font-size: var(--vyre-font-size-xs); }
.home-heading--crm { align-items: center; }
.crm-primary { border: 0 !important; }
.crm-stagebar { display: flex; align-items: center; gap: var(--vyre-space-3); margin-bottom: var(--vyre-space-4); padding: var(--vyre-space-3) var(--vyre-space-4); border: 1px solid var(--work-border); border-radius: var(--vyre-radius-full); background: var(--work-panel); }
.crm-stagebar > span:first-child, .stage-note { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.crm-stagebar b { color: var(--work-ink); font-size: var(--vyre-font-size-xs); }
.crm-stagebar i { color: var(--work-accent); font-style: normal; }
.stage-note { margin-left: auto; }
.crm-board { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: var(--vyre-space-3); min-height: 420px; }
.crm-lane { padding: var(--vyre-space-3); border: 1px solid var(--work-border); border-radius: var(--vyre-radius-lg); background: color-mix(in srgb, var(--work-panel) 74%, var(--work-page)); }
.lane-head { display: grid; grid-template-columns: 1fr auto; gap: var(--vyre-space-1); margin-bottom: var(--vyre-space-3); padding: 0 var(--vyre-space-1) var(--vyre-space-3); border-bottom: 1px solid var(--work-border); }
.lane-head span { grid-column: 1 / -1; color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .08em; }
.lane-head b { color: var(--work-ink); font-size: var(--vyre-font-size-md); }
.lane-head i { color: var(--work-muted); font-style: normal; }
.crm-opportunity { display: block; width: 100%; margin-bottom: var(--vyre-space-2); padding: var(--vyre-space-4); border-radius: var(--vyre-radius-md); box-shadow: var(--vyre-shadow-card); }
.opportunity-dot { display: inline-block; width: var(--vyre-space-2); height: var(--vyre-space-2); margin-right: var(--vyre-space-2); border-radius: var(--vyre-radius-full); background: var(--work-accent); }
.crm-opportunity b, .crm-opportunity small, .crm-opportunity i { display: block; }
.crm-opportunity small { margin-top: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.crm-opportunity i { margin-top: var(--vyre-space-3); color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.crm-add { width: 100%; padding: var(--vyre-space-3); border-style: dashed !important; border-radius: var(--vyre-radius-md); color: var(--work-accent) !important; }
.crm-footer { display: flex; align-items: center; gap: var(--vyre-space-3); margin-top: var(--vyre-space-4); padding: var(--vyre-space-4); border-top: 2px solid var(--work-accent); background: var(--work-panel); }
.crm-footer > span:first-child { font-weight: 750; }
.crm-footer button { padding: var(--vyre-space-2) var(--vyre-space-3); border-radius: var(--vyre-radius-full); color: var(--work-accent); }
.crm-footer i { margin-left: var(--vyre-space-2); font-style: normal; }
.crm-foot-note { margin-left: auto; color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.home-heading--ledger { padding-bottom: var(--vyre-space-4); border-bottom: 2px solid var(--work-ink); }
.ledger-period { display: grid; gap: var(--vyre-space-2); min-width: 220px; padding: var(--vyre-space-3); border: 1px solid var(--work-border); background: var(--work-panel); }
.ledger-period span, .ledger-label, .ledger-section-title span, .ledger-control > span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.ledger-flow { padding: var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.ledger-label { display: block; margin-bottom: var(--vyre-space-3); }
.ledger-steps { display: grid; grid-template-columns: repeat(5,minmax(0,1fr)); border-top: 1px solid var(--work-border); border-bottom: 1px solid var(--work-border); }
.ledger-steps button { display: grid; gap: var(--vyre-space-3); min-height: 172px; padding: var(--vyre-space-4); border: 0; border-right: 1px solid var(--work-border); background: transparent; }
.ledger-steps button:last-child { border-right: 0; }
.ledger-steps small { color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.ledger-steps b { font-size: var(--vyre-font-size-md); }
.ledger-steps span { color: var(--work-muted); font-size: var(--vyre-font-size-sm); }
.ledger-steps i { align-self: end; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.ledger-bottom { display: grid; grid-template-columns: 1.1fr .9fr; gap: var(--vyre-space-4); margin-top: var(--vyre-space-4); }
.ledger-register, .ledger-control { padding: var(--vyre-space-4); border: 1px solid var(--work-border); background: var(--work-panel); }
.ledger-section-title { display: flex; justify-content: space-between; margin-bottom: var(--vyre-space-2); }
.ledger-register button { display: flex; justify-content: space-between; width: 100%; padding: var(--vyre-space-3) 0; border: 0; border-bottom: 1px solid var(--work-border); background: transparent; }
.ledger-register button i { color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.ledger-control > b { display: block; margin-top: var(--vyre-space-2); }
.ledger-control p { margin-top: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-sm); line-height: 1.6; }
.ledger-control > div { display: flex; flex-wrap: wrap; gap: var(--vyre-space-2); margin-top: var(--vyre-space-4); }
.ledger-control button { padding: var(--vyre-space-2) var(--vyre-space-3); border-color: var(--work-border); background: var(--work-soft); font-size: var(--vyre-font-size-xs); }
.home-heading--plm { padding: var(--vyre-space-4) var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.revision-mark { padding-left: var(--vyre-space-4); border-left: 1px solid var(--work-border); color: var(--work-accent); }
.revision-mark span, .revision-mark b { display: block; }
.revision-mark span { font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.revision-mark b { margin-top: var(--vyre-space-2); font-size: var(--vyre-font-size-md); line-height: 1.3; }
.plm-workbench { display: grid; grid-template-columns: minmax(220px,.72fr) minmax(0,2.2fr); min-height: 480px; border: 1px solid var(--work-border); background: var(--work-panel); }
.plm-tree { padding: var(--vyre-space-4); border-right: 1px solid var(--work-border); background: var(--work-soft); }
.tree-title { display: grid; gap: var(--vyre-space-2); margin-bottom: var(--vyre-space-4); }
.tree-title span, .canvas-toolbar span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .08em; }
.tree-root, .tree-child { display: flex; align-items: center; width: 100%; gap: var(--vyre-space-2); padding: var(--vyre-space-3); border: 0 !important; background: transparent !important; }
.tree-root { color: var(--work-accent) !important; font-weight: 750 !important; }
.tree-child { padding-left: var(--vyre-space-5); }
.tree-child i { margin-left: auto; color: var(--work-accent); font-style: normal; }
.tree-separator { margin: var(--vyre-space-3) 0; border-top: 1px solid var(--work-border); }
.plm-canvas { display: flex; flex-direction: column; justify-content: space-between; padding: var(--vyre-space-5); background-image: linear-gradient(color-mix(in srgb,var(--work-accent) 7%,transparent) 1px,transparent 1px),linear-gradient(90deg,color-mix(in srgb,var(--work-accent) 7%,transparent) 1px,transparent 1px); background-size: var(--vyre-space-5) var(--vyre-space-5); }
.canvas-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--vyre-space-3); padding-bottom: var(--vyre-space-3); border-bottom: 1px solid var(--work-border); }
.canvas-toolbar button, .change-callout button { padding: var(--vyre-space-2) var(--vyre-space-3); border-color: var(--work-border); color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.bom-branch { display: grid; justify-items: center; padding: var(--vyre-space-5) 0; }
.bom-branch > button, .branch-children button { display: grid; gap: var(--vyre-space-2); min-width: 220px; padding: var(--vyre-space-4); box-shadow: var(--vyre-shadow-card); }
.bom-branch small, .branch-children small { color: var(--work-accent); font-size: var(--vyre-font-size-xs); letter-spacing: .06em; }
.bom-branch span, .branch-children span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.bom-branch > i { color: var(--work-accent); font-style: normal; }
.branch-children { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--vyre-space-5); }
.change-callout { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: var(--vyre-space-3); padding: var(--vyre-space-3); border: 1px solid var(--work-border); background: var(--work-panel); }
.change-callout > span { color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.home-heading--srm { padding: var(--vyre-space-5); background: var(--work-rail); color: var(--vyre-content-inverse); }
.home-heading--srm h1, .home-heading--srm p { color: var(--vyre-content-inverse); }
.home-heading--srm p { opacity: .76; }
.srm-seal { display: grid; justify-items: center; gap: var(--vyre-space-1); padding: var(--vyre-space-3); border: 1px solid color-mix(in srgb,var(--vyre-content-inverse) 35%,transparent); color: var(--vyre-content-inverse); }
.srm-seal span { font-size: var(--vyre-font-size-xs); letter-spacing: .12em; }
.srm-seal b { font-size: var(--vyre-font-size-lg); }
.srm-journey { padding: var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.journey-steps { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: var(--vyre-space-2); margin-top: var(--vyre-space-4); }
.journey-steps button { display: grid; gap: var(--vyre-space-3); min-height: 140px; padding: var(--vyre-space-3); border-top: 3px solid var(--work-accent); }
.journey-steps small { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.journey-steps i { align-self: end; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.srm-lower { display: grid; grid-template-columns: 1fr 1fr; gap: var(--vyre-space-4); margin-top: var(--vyre-space-4); }
.srm-vendor, .srm-collab { padding: var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.srm-lower h2 { margin-top: var(--vyre-space-2); font-size: var(--vyre-font-size-lg); }
.srm-lower p { max-width: 580px; margin: var(--vyre-space-2) 0 var(--vyre-space-4); color: var(--work-muted); font-size: var(--vyre-font-size-sm); line-height: 1.6; }
.srm-lower button { margin-right: var(--vyre-space-2); padding: var(--vyre-space-2) var(--vyre-space-3); }
.srm-lower button i { margin-left: var(--vyre-space-2); color: var(--work-accent); font-style: normal; }
.home-heading--wms { align-items: center; }
.warehouse-layout { display: grid; grid-template-columns: minmax(0,1.8fr) minmax(260px,.8fr); gap: var(--vyre-space-4); }
.warehouse-map, .warehouse-ops { border: 1px solid var(--work-border); background: var(--work-panel); }
.warehouse-map { padding: var(--vyre-space-5); }
.map-head { display: flex; align-items: center; justify-content: space-between; }
.map-key { display: flex; align-items: center; gap: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.map-key i { width: var(--vyre-space-2); height: var(--vyre-space-2); border-radius: var(--vyre-radius-full); background: var(--work-accent); }
.map-body { display: grid; grid-template-columns: var(--vyre-space-6) 1fr; gap: var(--vyre-space-2); margin-top: var(--vyre-space-4); }
.map-aisle-labels { display: grid; grid-template-rows: repeat(6,1fr); color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-weight: 750; text-align: center; }
.location-grid { display: grid; grid-template-columns: repeat(6,minmax(0,1fr)); gap: var(--vyre-space-2); padding: var(--vyre-space-3); background: var(--work-page); }
.location-grid button { display: grid; place-items: center; min-height: 60px; padding: var(--vyre-space-2); border: 0; background: color-mix(in srgb,var(--work-accent) 13%,var(--work-panel)); color: var(--work-ink); font-size: var(--vyre-font-size-xs); font-weight: 700; }
.location-grid button.is-crossing { background: var(--work-soft); }
.map-legend { display: flex; justify-content: space-between; margin-top: var(--vyre-space-3); color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.warehouse-ops { padding: var(--vyre-space-4); }
.warehouse-ops button { display: flex; align-items: center; width: 100%; gap: var(--vyre-space-3); margin-top: var(--vyre-space-2); padding: var(--vyre-space-3); }
.warehouse-ops button b { display: grid; place-items: center; width: var(--vyre-space-6); height: var(--vyre-space-6); background: var(--work-soft); color: var(--work-accent); }
.warehouse-ops button i { margin-left: auto; color: var(--work-accent); font-style: normal; }
.home-heading--mes { padding: var(--vyre-space-4) 0; border-bottom: 1px solid var(--work-border); }
.home-heading--mes h1 { color: var(--work-ink); }
.andon-indicator { display: flex; align-items: center; gap: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.andon-indicator i { width: var(--vyre-space-2); height: var(--vyre-space-2); border-radius: var(--vyre-radius-full); background: var(--work-accent); box-shadow: 0 0 0 var(--vyre-space-2) var(--work-soft); }
.mes-console { display: grid; grid-template-columns: minmax(0,1.8fr) minmax(260px,.8fr); min-height: 480px; border: 1px solid var(--work-border); background: var(--work-panel); }
.mes-primary { padding: var(--vyre-space-5); }
.console-top { display: flex; justify-content: space-between; padding-bottom: var(--vyre-space-3); border-bottom: 1px solid var(--work-border); color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.mes-workorder { padding: var(--vyre-space-5) 0; }
.mes-workorder h2 { margin-top: var(--vyre-space-2); font-size: var(--vyre-font-size-display); }
.mes-workorder p, .mes-andon p { margin-top: var(--vyre-space-2); color: var(--work-muted); font-size: var(--vyre-font-size-sm); line-height: 1.6; }
.mes-sequence { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: var(--vyre-space-3); margin-top: var(--vyre-space-5); }
.mes-sequence button { display: grid; gap: var(--vyre-space-3); min-height: 168px; padding: var(--vyre-space-4); border-top: 3px solid var(--work-accent); background: var(--work-soft); }
.mes-sequence small { color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.mes-sequence span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.mes-sequence i { align-self: end; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.mes-andon { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; padding: var(--vyre-space-5); border-left: 1px solid var(--work-border); background: var(--work-rail); color: var(--vyre-content-inverse); }
.mes-andon h2 { margin-top: var(--vyre-space-4); color: var(--vyre-content-inverse); }
.mes-andon p { color: color-mix(in srgb,var(--vyre-content-inverse) 70%,transparent); }
.andon-ring { display: grid; place-items: center; width: calc(var(--vyre-space-8) * 3); height: calc(var(--vyre-space-8) * 3); border: 1px solid var(--work-accent); border-radius: var(--vyre-radius-full); color: var(--work-accent); }
.andon-ring b { font-size: var(--vyre-font-size-display); }
.andon-ring span { font-size: var(--vyre-font-size-xs); }
.mes-andon button { margin-top: var(--vyre-space-4); padding: var(--vyre-space-3) var(--vyre-space-4); border-color: var(--work-accent); background: var(--work-accent); color: var(--vyre-content-inverse); }
.mes-andon button i { margin-left: var(--vyre-space-2); font-style: normal; }
.mes-bottom { display: flex; align-items: center; gap: var(--vyre-space-3); margin-top: var(--vyre-space-4); padding: var(--vyre-space-3) var(--vyre-space-4); border-top: 1px solid var(--work-border); color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.mes-bottom span { margin-right: auto; color: var(--work-accent); font-weight: 750; }
.mes-bottom i { color: var(--work-accent); font-style: normal; }
.home-heading--qms { padding-bottom: var(--vyre-space-4); border-bottom: 1px solid var(--work-border); }
.quality-loop { margin-top: var(--vyre-space-4); padding: var(--vyre-space-5); border: 1px solid var(--work-border); background: var(--work-panel); }
.loop-heading { display: flex; justify-content: space-between; padding-bottom: var(--vyre-space-3); border-bottom: 1px solid var(--work-border); }
.loop-heading span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.loop-heading b { color: var(--work-accent); font-size: var(--vyre-font-size-sm); }
.quality-steps { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: var(--vyre-space-3); padding-top: var(--vyre-space-4); }
.quality-steps button { display: grid; gap: var(--vyre-space-3); min-height: 190px; padding: var(--vyre-space-4); border-radius: var(--vyre-radius-lg); }
.quality-index { display: grid; place-items: center; width: var(--vyre-space-8); height: var(--vyre-space-8); border: 1px solid var(--work-border); border-radius: var(--vyre-radius-full); color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.quality-steps small { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.quality-steps i { align-self: end; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.quality-support { display: grid; grid-template-columns: 1.2fr .8fr; gap: var(--vyre-space-4); margin-top: var(--vyre-space-4); }
.quality-support section { padding: var(--vyre-space-4); border: 1px solid var(--work-border); background: var(--work-panel); }
.quality-support p { margin: var(--vyre-space-2) 0 var(--vyre-space-3); color: var(--work-muted); font-size: var(--vyre-font-size-sm); line-height: 1.6; }
.quality-support button { margin-right: var(--vyre-space-2); padding: var(--vyre-space-2) var(--vyre-space-3); }
.quality-support button i { color: var(--work-accent); font-style: normal; }
.quality-supplier { border-top: 3px solid var(--work-accent) !important; }
.home-heading--eam { padding: var(--vyre-space-4); border: 1px solid var(--work-border); background: var(--work-panel); }
.maintenance-layout { display: grid; grid-template-columns: minmax(240px,.82fr) minmax(0,1.8fr); border: 1px solid var(--work-border); background: var(--work-panel); }
.asset-rail { padding: var(--vyre-space-4); border-right: 1px solid var(--work-border); background: var(--work-soft); }
.asset-rail h2 { margin: var(--vyre-space-2) 0; font-size: var(--vyre-font-size-lg); }
.asset-rail button { display: flex; align-items: center; width: 100%; gap: var(--vyre-space-3); padding: var(--vyre-space-3); border: 0; border-bottom: 1px solid var(--work-border); background: transparent; }
.asset-rail button b { color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.asset-rail button i { margin-left: auto; color: var(--work-accent); font-style: normal; }
.asset-separator { margin: var(--vyre-space-4) 0; border-top: 1px solid var(--work-border); }
.maintenance-plan { padding: var(--vyre-space-5); }
.plan-title { display: flex; justify-content: space-between; align-items: center; }
.plan-title > span { padding: var(--vyre-space-2) var(--vyre-space-3); background: var(--work-soft); color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.maintenance-timeline { display: grid; gap: 0; margin-top: var(--vyre-space-4); }
.timeline-item { display: grid; grid-template-columns: var(--vyre-space-8) 1fr; gap: var(--vyre-space-3); min-height: 108px; }
.timeline-node { display: grid; place-items: center; align-self: start; width: var(--vyre-space-8); height: var(--vyre-space-8); border: 1px solid var(--work-accent); border-radius: var(--vyre-radius-full); color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.timeline-item > div { padding-bottom: var(--vyre-space-3); border-bottom: 1px solid var(--work-border); }
.timeline-item p { margin-top: var(--vyre-space-1); color: var(--work-muted); font-size: var(--vyre-font-size-sm); }
.timeline-item button { margin-top: var(--vyre-space-2); padding: 0; border: 0; background: transparent; color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.maintenance-note { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--vyre-space-2); padding: var(--vyre-space-3); background: var(--work-soft); }
.maintenance-note span { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.maintenance-note button { border: 0; background: transparent; color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
.home-heading--energy { padding: var(--vyre-space-4) 0; border-bottom: 1px solid var(--work-border); }
.home-heading--energy .eyebrow, .home-heading--energy h1 { color: var(--work-ink); }
.energy-flow { display: grid; grid-template-columns: minmax(0,1fr) minmax(200px,.72fr) minmax(0,1fr); align-items: stretch; gap: var(--vyre-space-4); margin-top: var(--vyre-space-4); }
.energy-input, .energy-hub, .energy-uses { position: relative; padding: var(--vyre-space-4); border: 1px solid var(--work-border); background: var(--work-panel); }
.energy-input h2, .energy-uses h2 { margin: var(--vyre-space-2) 0 var(--vyre-space-3); font-size: var(--vyre-font-size-lg); }
.energy-input > button, .energy-uses > button { display: grid; grid-template-columns: var(--vyre-space-8) 1fr; gap: var(--vyre-space-1) var(--vyre-space-3); width: 100%; margin-top: var(--vyre-space-2); padding: var(--vyre-space-3); }
.flow-icon, .use-mark { display: grid; grid-row: span 3; place-items: center; width: var(--vyre-space-8); height: var(--vyre-space-8); border-radius: var(--vyre-radius-full); background: var(--work-soft); color: var(--work-accent); font-size: var(--vyre-font-size-lg); }
.energy-input small, .energy-uses small { color: var(--work-muted); font-size: var(--vyre-font-size-xs); }
.energy-input i, .energy-uses i { color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
.flow-lines { display: grid; gap: var(--vyre-space-3); margin: var(--vyre-space-4) calc(var(--vyre-space-5) * -1) 0 0; }
.flow-lines i { display: block; height: 1px; background: var(--work-accent); opacity: .45; }
.flow-lines i:nth-child(2) { width: 76%; }
.flow-lines i:nth-child(3) { width: 52%; }
.energy-hub { display: grid; justify-items: center; align-content: center; gap: var(--vyre-space-4); background: var(--work-rail); color: var(--vyre-content-inverse); text-align: center; }
.energy-hub > .eyebrow { color: var(--work-accent); }
.hub-orbit { display: grid; place-items: center; width: calc(var(--vyre-space-8) * 3); height: calc(var(--vyre-space-8) * 3); border: 1px solid var(--work-accent); border-radius: var(--vyre-radius-full); box-shadow: 0 0 0 var(--vyre-space-3) color-mix(in srgb,var(--work-accent) 13%,transparent); }
.hub-orbit span { color: var(--work-accent); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.hub-orbit b { font-size: var(--vyre-font-size-lg); line-height: 1.25; }
.energy-hub small { color: color-mix(in srgb,var(--vyre-content-inverse) 68%,transparent); font-size: var(--vyre-font-size-xs); }
.energy-footer { display: flex; align-items: center; gap: var(--vyre-space-3); margin-top: var(--vyre-space-4); padding: var(--vyre-space-4); border-left: 3px solid var(--work-accent); background: var(--work-panel); }
.energy-footer span { margin-right: auto; color: var(--work-accent); font-size: var(--vyre-font-size-xs); font-weight: 750; }
.energy-footer i { color: var(--work-accent); font-style: normal; }
.energy-footer button { padding: var(--vyre-space-2) var(--vyre-space-3); border-color: var(--work-border); color: var(--work-accent); font-size: var(--vyre-font-size-xs); }
@media (max-width: 980px) {
  .crm-board { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .journey-steps { grid-template-columns: repeat(3,minmax(0,1fr)); }
  .ledger-steps { grid-template-columns: repeat(3,minmax(0,1fr)); }
  .plm-workbench { grid-template-columns: 1fr; }
  .plm-tree { display: flex; flex-wrap: wrap; align-items: center; gap: var(--vyre-space-1); border-right: 0; border-bottom: 1px solid var(--work-border); }
  .tree-title { flex-basis: 100%; }
  .plm-tree button { flex: 1 1 auto; width: auto; padding-left: var(--vyre-space-2); }
  .plm-tree .tree-separator { display: none; }
  .plm-workbench, .plm-tree, .plm-canvas { min-width: 0; max-width: 100%; }
  .canvas-toolbar { flex-wrap: wrap; }
  .branch-children { grid-template-columns: 1fr; }
  .bom-branch > button, .branch-children button { min-width: 0; width: 100%; }
  .change-callout { grid-template-columns: 1fr; }
  .warehouse-layout, .energy-flow { grid-template-columns: 1fr; }
  .energy-hub { grid-row: auto; grid-column: auto; }
}
@media (max-width: 680px) {
  .home-heading { align-items: flex-start; flex-direction: column; }
  .home-heading h1 { font-size: var(--vyre-font-size-lg); }
  .registry-layout, .domain-grid, .ledger-bottom, .plm-workbench, .srm-lower, .warehouse-layout, .mes-console, .quality-support, .maintenance-layout, .energy-flow { grid-template-columns: 1fr; }
  .crm-stagebar { flex-wrap: wrap; border-radius: var(--vyre-radius-md); }
  .stage-note, .crm-foot-note { margin-left: 0; }
  .crm-board, .quality-steps, .mes-sequence, .branch-children { grid-template-columns: 1fr; }
  .ledger-steps { grid-template-columns: 1fr; }
  .ledger-steps button { min-height: auto; border-right: 0; border-bottom: 1px solid var(--work-border); }
  .journey-steps { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .location-grid { grid-template-columns: repeat(3,minmax(0,1fr)); }
  .plm-tree { padding: var(--vyre-space-3); }
  .plm-tree button { font-size: var(--vyre-font-size-xs); }
  .plm-canvas { min-width: 0; padding: var(--vyre-space-3); }
  .bom-branch > button, .branch-children button { min-width: 0; width: 100%; }
  .energy-hub { grid-row: auto; grid-column: auto; }
  .plm-tree, .asset-rail, .mes-andon { border-right: 0; border-left: 0; }
  .change-callout { grid-template-columns: 1fr; }
  .crm-footer, .mes-bottom, .energy-footer { flex-wrap: wrap; }
}
</style>
