<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'
import TablePager from '../shared/components/TablePager.vue'

const health = ref<any>({}), databases = ref<any[]>([]), loading = ref(false)
const page = ref(1), size = ref(20), total = ref(0)

async function load() {
  loading.value = true
  try {
    const [healthResult, databaseResult] = await Promise.all([http.get('/health'), http.get('/health/databases', { params: { page: page.value, size: size.value } })])
    health.value = healthResult.data.data
    databases.value = databaseResult.data.data.content
    total.value = databaseResult.data.data.totalElements
    if (!databases.value.length && total.value > 0 && page.value > 1) {
      page.value--
      await load()
    }
  } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="ops-page" v-loading="loading">
    <div class="page-head"><div><span>RUNTIME OPERATIONS</span><h1>平台运行监控</h1><p>检查 Java 单进程、MySQL 连接以及十系统数据库表结构。</p></div><el-button @click="load">重新检查</el-button></div>
    <div class="metrics"><el-card shadow="never"><span>应用状态</span><b class="up">{{health.status||'UNKNOWN'}}</b></el-card><el-card shadow="never"><span>后端服务</span><b>{{health.service||'-'}}</b></el-card><el-card shadow="never"><span>连接数据库</span><b>{{health.database||'-'}}</b></el-card><el-card shadow="never"><span>MySQL 版本</span><b>{{health.mysqlVersion||'-'}}</b></el-card></div>
    <el-card shadow="never"><template #header><b>数据库与表结构</b></template><el-table :data="databases" stripe><el-table-column prop="layer" label="架构层" min-width="180"/><el-table-column prop="dbName" label="Database" min-width="190"/><el-table-column prop="tableCount" label="表数量" width="120"/><el-table-column label="状态" width="120"><template #default><el-tag type="success">可访问</el-tag></template></el-table-column></el-table><TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/></el-card>
  </section>
</template>

<style scoped>.ops-page{max-width:1450px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:15px}.metrics span,.metrics b{display:block}.metrics span{color:#8797aa;font-size:11px}.metrics b{margin-top:9px;color:#294361;font-size:18px}.metrics .up{color:#16a16f}</style>
