<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../api/http'
import { zh } from '../../shared/display'

const router = useRouter()
const loading = ref(false)
const error = ref('')
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
  error.value = ''
  try {
    const { data } = await http.get('/mes/work-orders', { params: { page: 1, size: 200 } })
    const result = data.data
    workOrders.value = Array.isArray(result?.content) ? result.content : []
    total.value = result?.totalElements ?? workOrders.value.length
  } catch (reason: any) {
    error.value = reason?.message || '工单数据加载失败'
  } finally {
    loading.value = false
  }
}
const routes = [
  { title: '生产工单', desc: '查看工序工单、开工、报工和进度', path: '/mes/work-orders', action: '进入工单' },
  { title: '派工排产', desc: '安排车间、班组、设备、班次和计划时段', path: '/mes/dispatch', action: '进入派工' },
  { title: '报工明细', desc: '登记合格品、报废品、工时与停机原因', path: '/mes/reports', action: '进入报工' },
  { title: 'Andon 异常', desc: '跟进现场异常响应、处理和关闭', path: '/mes/andon', action: '进入异常' }
]
onMounted(load)
</script>

<template>
  <section class="mes-home">
    <div class="hero">
      <div>
        <div class="eyebrow">制造执行系统</div>
        <h1>车间生产总览</h1>
        <p>从生产工单、资源派工到报工和现场异常，集中查看执行进度。</p>
      </div>
      <el-button plain :loading="loading" @click="load">刷新数据</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="load-error" />

    <div class="summary-grid" v-loading="loading">
      <el-card shadow="never"><span>工单总数</span><strong>{{ total }}</strong><small>当前可查看的 MES 工单</small></el-card>
      <el-card shadow="never"><span>待排产 / 已下达</span><strong>{{ statusCounts.waiting }}</strong><small>最近 200 张工单</small></el-card>
      <el-card shadow="never"><span>生产中</span><strong>{{ statusCounts.running }}</strong><small>最近 200 张工单</small></el-card>
      <el-card shadow="never"><span>已完工</span><strong>{{ statusCounts.completed }}</strong><small>最近 200 张工单</small></el-card>
    </div>

    <el-card shadow="never" class="progress-card">
      <div class="progress-head"><div><b>最近工单报工进度</b><span>按当前加载工单的计划数量与已报工数量汇总</span></div><strong>{{ progress }}%</strong></div>
      <el-progress :percentage="progress" :stroke-width="12" :show-text="false" />
      <div class="progress-numbers"><span>已报工 {{ zh(totalCompleted) }}</span><span>计划 {{ zh(totalPlanned) }}</span></div>
    </el-card>

    <div class="section-title"><div><h2>MES 工作区</h2><p>选择单据页面继续处理生产现场业务。</p></div></div>
    <div class="route-grid">
      <el-card v-for="item in routes" :key="item.path" shadow="hover" class="route-card" @click="router.push(item.path)">
        <div class="route-icon">{{ item.title === 'Andon 异常' ? '!' : item.title.slice(0, 1) }}</div>
        <h3>{{ item.title }}</h3><p>{{ item.desc }}</p>
        <el-button link type="primary">{{ item.action }} →</el-button>
      </el-card>
    </div>

    <el-card shadow="never" class="recent-card">
      <template #header><div class="table-head"><b>最近工单</b><el-button link type="primary" @click="router.push('/mes/work-orders')">查看全部</el-button></div></template>
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
    </el-card>
  </section>
</template>

<style scoped>
.mes-home{max-width:1480px;margin:0 auto}.hero{display:flex;justify-content:space-between;align-items:flex-start;padding:4px 2px 18px}.eyebrow{color:#be123c;font-size:12px;font-weight:700}.hero h1{margin:8px 0;color:#1b3150;font-size:26px}.hero p,.section-title p{margin:0;color:#708096;font-size:13px}.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.summary-grid :deep(.el-card__body){display:grid;gap:8px}.summary-grid span,.summary-grid small{color:#718198;font-size:12px}.summary-grid strong{color:#1c3556;font-size:28px}.progress-card,.recent-card{margin-top:16px;border-radius:12px}.progress-head,.table-head{display:flex;justify-content:space-between;align-items:center}.progress-head div{display:grid;gap:5px}.progress-head span{font-size:12px;color:#8492a6}.progress-head strong{font-size:24px;color:#be123c}.progress-numbers{display:flex;justify-content:space-between;margin-top:8px;color:#718198;font-size:12px}.section-title{margin:24px 2px 12px}.section-title h2{margin:0 0 6px;color:#1b3150;font-size:18px}.route-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.route-card{cursor:pointer;border-radius:12px}.route-icon{display:grid;place-items:center;width:36px;height:36px;border-radius:10px;background:#fff1f2;color:#be123c;font-weight:800}.route-card h3{margin:13px 0 6px;color:#29405d;font-size:15px}.route-card p{min-height:38px;margin:0 0 12px;color:#718198;font-size:12px;line-height:1.6}.load-error{margin-bottom:14px}.table-head b{color:#304563}@media(max-width:900px){.summary-grid,.route-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:560px){.summary-grid,.route-grid{grid-template-columns:1fr}.hero{display:block}.hero>.el-button{margin-top:12px}}
</style>
