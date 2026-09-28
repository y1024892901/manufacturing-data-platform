<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh } from '../../shared/display'

const rows = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detail = ref<any>(null)
const detailVisible = ref(false)

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/mdm/routings', {
      params: { keyword: keyword.value || undefined, status: status.value || undefined, page: page.value, size: size.value }
    })
    rows.value = data.data?.content || []
    total.value = data.data?.totalElements || 0
    if (!rows.value.length && total.value && page.value > 1) {
      page.value -= 1
      await load()
    }
  } finally {
    loading.value = false
  }
}

async function open(row: any) {
  const { data } = await http.get(`/mdm/routings/${row.id}`)
  detail.value = data.data
  detailVisible.value = true
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <section class="routing-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>工艺路线与工序</h1><p>查看产品工艺路线、工序顺序、工时与检验要求。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条工艺路线</div>
        <el-input v-model="keyword" clearable placeholder="输入路线编码或名称" @keyup.enter="search" />
        <el-select v-model="status" clearable placeholder="全部状态" @change="search">
          <el-option v-for="item in ['DRAFT','PENDING','PUBLISHED','REJECTED','CHANGING','DISABLED']" :key="item" :label="mdmStatus(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无工艺路线">
        <el-table-column prop="routingCode" label="路线编码" min-width="140" />
        <el-table-column prop="routingName" label="路线名称" min-width="180" />
        <el-table-column prop="productCode" label="产品编码" min-width="130" />
        <el-table-column prop="routingVersion" label="路线版本" width="110" />
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column prop="expireDate" label="失效日期" width="120" />
        <el-table-column prop="current" label="当前版本" width="105"><template #default="{row}">{{ zh(row.current) }}</template></el-table-column>
        <el-table-column prop="versionNo" label="记录版本" width="95" />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看工序</el-button></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
    <el-drawer v-model="detailVisible" title="工艺路线详情" size="880px">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="路线编码">{{ detail.routingCode }}</el-descriptions-item>
          <el-descriptions-item label="路线名称">{{ detail.routingName }}</el-descriptions-item>
          <el-descriptions-item label="产品编码">{{ detail.productCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="路线版本">{{ detail.routingVersion }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.effectiveDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="记录状态">{{ mdmStatus(detail.status) }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="detail-heading">工序明细</h3>
        <el-table :data="detail.operations || []" stripe empty-text="暂无工序">
          <el-table-column prop="opSeq" label="顺序" width="80" />
          <el-table-column prop="operationCode" label="工序编码" min-width="110" />
          <el-table-column prop="operationName" label="工序名称" min-width="140" />
          <el-table-column prop="workCenter" label="工作中心" min-width="120" />
          <el-table-column prop="setupTimeMin" label="准备时间（分钟）" min-width="145" />
          <el-table-column prop="runTimeMin" label="单件工时（分钟）" min-width="145" />
          <el-table-column prop="defaultEquipmentCode" label="默认设备" min-width="120" />
          <el-table-column label="关键工序" width="95"><template #default="{row}">{{ zh(row.keyOperation) }}</template></el-table-column>
          <el-table-column label="检验工序" width="95"><template #default="{row}">{{ zh(row.inspectionOp) }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.routing-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-input{width:270px}.toolbar .el-select{width:165px}.detail-heading{margin:24px 0 10px;color:#2d4665;font-size:15px}
</style>
