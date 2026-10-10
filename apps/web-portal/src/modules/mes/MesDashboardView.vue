<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../api/http'
import { zh } from '../../shared/display'
import { showErrorDialog } from '../../shared/errorDialog'

const router = useRouter()
const loading = ref(false)
const workOrders = ref<any[]>([])
const total = ref(0)
const statusCounts = computed(() => ({
  waiting: workOrders.value.filter(row => ['CREATED', 'RELEASED'].includes(row.status)).length,
  running: workOrders.value.filter(row => row.status === 'STARTED').length,
  paused: workOrders.value.filter(row => row.status === 'PAUSED').length,
  completed: workOrders.value.filter(row => row.status === 'COMPLETED').length
}))
const totalPlanned = computed(() => workOrders.value.reduce((sum, row) => sum + Number(row.planQty || 0), 0))
const totalCompleted = computed(() => workOrders.value.reduce((sum, row) => sum + Number(row.completedQty || 0), 0))
const progress = computed(() => totalPlanned.value ? Math.min(100, Math.round(totalCompleted.value / totalPlanned.value * 100)) : 0)
const load = async () => {
  loading.value = true
  try {
    const { data } = await http.get('/mes/work-orders', { params: { page: 1, size: 200 } })
    const result = data.data
    workOrders.value = Array.isArray(result?.content) ? result.content : []
    total.value = result?.totalElements ?? workOrders.value.length
  } catch (reason: any) {
    showErrorDialog(reason?.message, '加载工单失败')
  } finally {
    loading.value = false
  }
}
const routes = [
  { title: '生产工单', desc: '工单领用 · 工序执行 · 进度追踪', path: '/mes/work-orders', action: '打开工单队列' },
  { title: '派工排产', desc: '班组、设备与计划时段安排', path: '/mes/dispatch', action: '进入派工台' },
  { title: '报工明细', desc: '合格品、报废品与工时登记', path: '/mes/reports', action: '登记生产结果' },
  { title: 'Andon 异常', desc: '现场异常响应与关闭追踪', path: '/mes/andon', action: '进入异常中心' }
]
onMounted(load)
</script>

<template>
  <section class="mes-home">
    <header class="mes-command">
      <div><span class="eyebrow">SHOP FLOOR / LIVE EXECUTION</span><h1>车间生产总览</h1><p>工单执行、资源派工和现场异常，集中在一个生产控制台中处理。</p></div>
      <div class="command-actions"><span class="shift-state"><i></i> 生产现场</span><el-button plain :loading="loading" @click="load">刷新工单</el-button></div>
    </header>

    <div class="production-wall">
      <section class="status-rack" v-loading="loading" aria-label="工单状态">
        <article class="status-panel status-panel--all"><span>工单总数</span><strong>{{ total }}</strong><small>当前可查看的 MES 工单</small></article>
        <article class="status-panel status-panel--waiting"><span>待排产 / 已下达</span><strong>{{ statusCounts.waiting }}</strong><small>最近加载的工单</small></article>
        <article class="status-panel status-panel--running"><span>生产中</span><strong>{{ statusCounts.running }}</strong><small>正在执行的工序</small></article>
        <article class="status-panel status-panel--paused"><span>已暂停</span><strong>{{ statusCounts.paused }}</strong><small>需要现场跟进</small></article>
      </section>
      <aside class="andon-console">
        <span class="eyebrow">ANDON RESPONSE</span>
        <div class="andon-beacon"><b>!</b><span>现场响应</span></div>
        <h2>异常呼叫中心</h2><p>按车间问题登记、分派责任人并追踪处理状态。</p>
        <el-button type="primary" @click="router.push('/mes/andon')">打开 Andon 面板 <span aria-hidden="true">↗</span></el-button>
      </aside>
    </div>

    <section class="execution-panel">
      <div class="panel-heading"><div><span class="eyebrow">WORK ORDER EXECUTION</span><h2>工单报工进度</h2><p>按当前加载工单的计划数量与已报工数量汇总</p></div><strong class="completion-value">{{ progress }}<small>%</small></strong></div>
      <el-progress :percentage="progress" :show-text="false" />
      <div class="quantity-track"><span>已报工 <b>{{ zh(totalCompleted) }}</b></span><span>计划数量 <b>{{ zh(totalPlanned) }}</b></span></div>
      <div class="table-heading"><h2>最近工单</h2><el-button link type="primary" @click="router.push('/mes/work-orders')">查看工单队列 →</el-button></div>
      <el-table :data="workOrders.slice(0, 8)" v-loading="loading" stripe>
        <el-table-column prop="workOrderNo" label="工单号" min-width="150" />
        <el-table-column prop="prodOrderNo" label="生产订单号" min-width="150" />
        <el-table-column prop="productCode" label="产品编码" min-width="120" />
        <el-table-column prop="operationName" label="工序" min-width="120" />
        <el-table-column prop="planQty" label="计划数量" min-width="100" />
        <el-table-column prop="completedQty" label="已报工数量" min-width="110" />
        <el-table-column prop="status" label="状态" min-width="100"><template #default="{ row }">{{ zh(row.status) }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && workOrders.length === 0" description="暂无可见工单" />
    </section>

    <nav class="mes-route-strip" aria-label="生产执行模块">
      <button v-for="(item, index) in routes" :key="item.path" @click="router.push(item.path)"><small>0{{ index + 1 }} / EXECUTION</small><b>{{ item.title }}</b><span>{{ item.desc }}</span><i>{{ item.action }} →</i></button>
    </nav>
  </section>
</template>

<style scoped>
.mes-home { max-width: var(--vyre-layout-content-max); margin: 0 auto; color: var(--workspace-ink); }
.mes-command { display: flex; align-items: center; justify-content: space-between; gap: var(--vyre-space-4); padding-bottom: var(--vyre-space-4); border-bottom: 1px solid var(--workspace-border); }
.eyebrow { color: var(--workspace-accent); font-size: var(--vyre-font-size-xs); font-weight: 750; letter-spacing: .11em; }
.mes-command h1 { margin-top: var(--vyre-space-2); color: var(--workspace-ink); font-size: var(--vyre-font-size-display); font-weight: 760; }
.mes-command p, .andon-console p, .panel-heading p { margin-top: var(--vyre-space-2); color: var(--workspace-muted); font-size: var(--vyre-font-size-sm); line-height: 1.6; }
.command-actions { display: flex; align-items: center; gap: var(--vyre-space-3); }
.shift-state { display: inline-flex; align-items: center; gap: var(--vyre-space-2); color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .08em; }
.shift-state i { width: var(--vyre-space-2); height: var(--vyre-space-2); border-radius: var(--vyre-radius-full); background: var(--workspace-accent); }
.production-wall { display: grid; grid-template-columns: minmax(0,1.7fr) minmax(240px,.7fr); margin-top: var(--vyre-space-4); border: 1px solid var(--workspace-border); background: var(--workspace-panel); }
.status-rack { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); align-content: stretch; }
.status-panel { display: grid; align-content: center; gap: var(--vyre-space-2); min-height: 148px; padding: var(--vyre-space-4); border-right: 1px solid var(--workspace-border); border-bottom: 1px solid var(--workspace-border); border-top: 2px solid var(--workspace-accent); background: var(--workspace-panel); }
.status-panel:nth-child(2n) { border-right: 0; }
.status-panel span, .status-panel small { color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); }
.status-panel strong { color: var(--workspace-ink); font-size: var(--vyre-font-size-display); line-height: 1; }
.status-panel--waiting { border-top-color: var(--vyre-status-warning-solid, var(--workspace-accent)); }
.status-panel--running { border-top-color: var(--vyre-status-success-solid, var(--workspace-accent)); }
.status-panel--paused { border-top-color: var(--vyre-status-danger-solid, var(--workspace-accent)); }
.andon-console { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; padding: var(--vyre-space-5); border-left: 1px solid var(--workspace-border); background: var(--workspace-rail); }
.andon-beacon { display: grid; place-items: center; width: calc(var(--vyre-space-8) * 2); height: calc(var(--vyre-space-8) * 2); margin-top: var(--vyre-space-4); border: 1px solid var(--workspace-accent); border-radius: var(--vyre-radius-full); color: var(--workspace-accent); }
.andon-beacon b { font-size: var(--vyre-font-size-display); }
.andon-beacon span { font-size: var(--vyre-font-size-xs); }
.andon-console h2 { margin-top: var(--vyre-space-3); color: var(--vyre-content-inverse); font-size: var(--vyre-font-size-lg); }
.andon-console p { color: color-mix(in srgb,var(--vyre-content-inverse) 72%,transparent); }
.andon-console :deep(.el-button) { margin-top: var(--vyre-space-4); }
.andon-console :deep(.el-button span span) { margin-left: var(--vyre-space-2); }
.execution-panel { margin-top: var(--vyre-space-4); padding: var(--vyre-space-4); border: 1px solid var(--workspace-border); border-top: 3px solid var(--workspace-accent); background: var(--workspace-panel); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--vyre-space-4); margin-bottom: var(--vyre-space-3); }
.panel-heading h2, .table-heading h2 { margin-top: var(--vyre-space-1); color: var(--workspace-ink); font-size: var(--vyre-font-size-lg); }
.completion-value { color: var(--workspace-accent); font-size: var(--vyre-font-size-display); }
.completion-value small { font-size: var(--vyre-font-size-sm); }
.execution-panel :deep(.el-progress-bar__outer) { background: var(--workspace-soft); }
.execution-panel :deep(.el-progress-bar__inner) { background: var(--workspace-accent); }
.quantity-track { display: flex; justify-content: space-between; gap: var(--vyre-space-3); margin-top: var(--vyre-space-2); color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); }
.quantity-track b { margin-left: var(--vyre-space-1); color: var(--workspace-ink); }
.table-heading { display: flex; align-items: center; justify-content: space-between; margin: var(--vyre-space-5) 0 var(--vyre-space-2); padding-top: var(--vyre-space-3); border-top: 1px solid var(--workspace-border); }
.mes-route-strip { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: var(--vyre-space-2); margin-top: var(--vyre-space-4); }
.mes-route-strip button { display: grid; gap: var(--vyre-space-2); min-height: 140px; padding: var(--vyre-space-3); border: 1px solid var(--workspace-border); border-top: 2px solid var(--workspace-accent); background: var(--workspace-panel); color: var(--workspace-ink); text-align: left; cursor: pointer; }
.mes-route-strip button:hover { border-color: var(--workspace-accent); }
.mes-route-strip small { color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); letter-spacing: .08em; }
.mes-route-strip span { color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); }
.mes-route-strip i { align-self: end; color: var(--workspace-accent); font-size: var(--vyre-font-size-xs); font-style: normal; }
@media (max-width: 900px) { .production-wall { grid-template-columns: 1fr; } .andon-console { border-top: 1px solid var(--workspace-border); border-left: 0; } }
@media (max-width: 680px) { .mes-command { align-items: flex-start; flex-direction: column; } .mes-command h1 { font-size: var(--vyre-font-size-lg); } .mes-route-strip { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
