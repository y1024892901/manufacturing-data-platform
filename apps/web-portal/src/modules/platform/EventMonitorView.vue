<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'

const rows = ref<any[]>([]), loading = ref(false), status = ref('')
const mode = ref<'master'|'business'>('master')
const page = ref(1), size = ref(20), total = ref(0)
const detail = ref<any>(null), detailVisible = ref(false)
const successOnPage = computed(() => rows.value.filter(item => item.status === 'SUCCESS').length)
const failedOnPage = computed(() => rows.value.filter(item => item.status === 'FAILED').length)

async function load() {
  loading.value = true
  try {
    const url = mode.value === 'master' ? '/mdm/distributions/events' : '/events/business'
    const { data } = await http.get(url, { params: { status: status.value || undefined, page: page.value, size: size.value } })
    rows.value = data.data.content
    total.value = data.data.totalElements
    if (!rows.value.length && total.value > 0 && page.value > 1) {
      page.value--
      await load()
    }
  } catch (error: any) { ElMessage.error(error.message || '分发记录加载失败') }
  finally { loading.value = false }
}
function filter() { page.value = 1; load() }
function switchMode(value:'master'|'business') { mode.value=value; page.value=1; status.value=''; load() }
async function showDetail(row:any) { try { const base=mode.value==='master'?'/mdm/distributions/events':'/events/business'; const {data}=await http.get(`${base}/${row.event_id}`); detail.value=data.data; detailVisible.value=true } catch(error:any){ ElMessage.error(error.message||'详情加载失败') } }
async function retry(row: any) { try { const base=mode.value==='master'?'/mdm/distributions/events':'/events/business'; await http.post(`${base}/${row.event_id}/retry`); ElMessage.success('事件已进入重试队列'); await load() } catch (error: any) { ElMessage.error(error.message || '重试失败') } }
async function reconcile(row:any) { try { const {data}=await http.post(`/mdm/distributions/events/${row.event_id}/reconcile`); detail.value=data.data; detailVisible.value=true; ElMessage.success('对账完成') } catch(error:any){ ElMessage.error(error.message||'对账失败') } }
onMounted(load)
</script>

<template>
  <section class="event-page">
    <div class="page-head"><div><span>数据集成监控</span><h1>数据流转与分发监控</h1><p>同时展示主数据分发与 CRM、ERP 业务事件的 Outbox/Inbox 状态。</p></div><el-button @click="load">刷新</el-button></div>
    <el-card shadow="never">
      <div class="toolbar"><el-radio-group :model-value="mode" @change="switchMode($event as 'master'|'business')"><el-radio-button value="master">主数据分发</el-radio-button><el-radio-button value="business">业务系统事件</el-radio-button></el-radio-group><el-select v-model="status" clearable placeholder="全部状态" style="width:160px" @change="filter"><el-option v-for="s in ['PENDING','PROCESSING','SUCCESS','FAILED','RETRYING','DEAD']" :key="s" :label="$zh(s)" :value="s"/></el-select><el-tag type="success">本页成功 {{successOnPage}}</el-tag><el-tag type="danger">本页失败 {{failedOnPage}}</el-tag></div>
      <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="occurred_at" label="发生时间" width="175"/><el-table-column prop="event_id" label="事件ID" min-width="190" show-overflow-tooltip/><el-table-column prop="event_type" label="事件类型" width="210"/><el-table-column prop="source_system" label="来源系统" width="100"/><el-table-column prop="target_system" label="目标系统" width="100"/><el-table-column prop="aggregate_type" label="业务对象" width="120"/><el-table-column :prop="mode==='master'?'aggregate_code':'aggregate_id'" label="业务编码" min-width="145"/><el-table-column prop="retry_count" label="重试" width="70"/><el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':row.status==='FAILED'||row.status==='DEAD'?'danger':'warning'">{{$zh(row.status)}}</el-tag></template></el-table-column><el-table-column prop="error_message" label="错误信息" min-width="180" show-overflow-tooltip/><el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button link @click="showDetail(row)">详情</el-button><el-button v-if="mode==='master'" link @click="reconcile(row)">对账</el-button><el-button v-if="row.status==='FAILED'||row.status==='DEAD'" link type="primary" @click="retry(row)">重试</el-button></template></el-table-column></el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>
    <el-drawer v-model="detailVisible" title="事件、接收记录与对账详情" size="620px"><pre class="json-detail">{{JSON.stringify(detail,null,2)}}</pre></el-drawer>
  </section>
</template>

<style scoped>.event-page{max-width:1500px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.toolbar{display:flex;align-items:center;gap:9px;margin-bottom:14px}.json-detail{padding:14px;border-radius:8px;background:#f6f8fb;white-space:pre-wrap;word-break:break-all;color:#3e5570;font-size:12px}</style>
