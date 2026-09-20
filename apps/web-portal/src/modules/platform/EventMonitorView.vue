<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'

const rows = ref<any[]>([]), loading = ref(false), status = ref('')
const page = ref(1), size = ref(20), total = ref(0)
const successOnPage = computed(() => rows.value.filter(item => item.status === 'SUCCESS').length)
const failedOnPage = computed(() => rows.value.filter(item => item.status === 'FAILED').length)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/mdm/distributions', { params: { status: status.value || undefined, page: page.value, size: size.value } })
    rows.value = data.data.content
    total.value = data.data.totalElements
  } catch (error: any) { ElMessage.error(error.message || '分发记录加载失败') }
  finally { loading.value = false }
}
function filter() { page.value = 1; load() }
async function retry(row: any) { try { await http.post(`/mdm/distributions/${row.id}/retry`); ElMessage.success('已重新分发'); await load() } catch (error: any) { ElMessage.error(error.message || '重试失败') } }
onMounted(load)
</script>

<template>
  <section class="event-page">
    <div class="page-head"><div><span>INTEGRATION MONITOR</span><h1>数据流转与分发监控</h1><p>展示 MDM 发布后向 ERP、MES、PLM 等系统写入只读副本的结果，可对失败记录手工重试。</p></div><el-button @click="load">刷新</el-button></div>
    <el-card shadow="never">
      <div class="toolbar"><el-select v-model="status" clearable placeholder="全部状态" style="width:160px" @change="filter"><el-option label="成功" value="SUCCESS"/><el-option label="失败" value="FAILED"/><el-option label="待处理" value="PENDING"/></el-select><el-tag type="success">本页成功 {{successOnPage}}</el-tag><el-tag type="danger">本页失败 {{failedOnPage}}</el-tag></div>
      <el-table :data="rows" v-loading="loading" stripe><el-table-column prop="distributed_at" label="流转时间" width="175"/><el-table-column prop="entity_type" label="主数据类型" width="120"/><el-table-column prop="entity_code" label="主数据编码" min-width="150"/><el-table-column prop="entity_version" label="版本" width="75"/><el-table-column prop="target_system" label="目标系统" width="110"/><el-table-column prop="distribute_mode" label="方式" width="90"/><el-table-column prop="row_count" label="写入行数" width="95"/><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'danger'">{{row.status}}</el-tag></template></el-table-column><el-table-column prop="error_message" label="失败原因" min-width="220" show-overflow-tooltip/><el-table-column label="操作" width="90"><template #default="{row}"><el-button v-if="row.status==='FAILED'" link type="primary" @click="retry(row)">重试</el-button></template></el-table-column></el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>
  </section>
</template>

<style scoped>.event-page{max-width:1500px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.toolbar{display:flex;align-items:center;gap:9px;margin-bottom:14px}</style>
