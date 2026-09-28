<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'
import TablePager from '../shared/components/TablePager.vue'
import { zh } from '../shared/display'

const rows = ref<any[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/mdm/materials', {
      params: { page: page.value, size: size.value, status: status.value || undefined }
    })
    rows.value = data.data?.content || []
    total.value = data.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

function filter() {
  page.value = 1
  load()
}

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

onMounted(load)
</script>

<template>
  <section class="material-page">
    <header class="page-head">
      <div>
        <span>权威主数据 · 只读查询</span>
        <h1>物料主数据</h1>
        <p>查看物料编码、规格、计量与质量属性，以及当前版本状态。</p>
      </div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条物料记录</div>
        <el-select v-model="status" clearable placeholder="全部状态" @change="filter">
          <el-option v-for="item in ['DRAFT','PENDING','PUBLISHED','REJECTED','DISABLED']" :key="item" :label="mdmStatus(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无物料数据">
        <el-table-column prop="materialCode" label="物料编码" min-width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="materialSpec" label="规格型号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="materialType" label="物料类别" min-width="110"><template #default="{row}">{{ zh(row.materialType) }}</template></el-table-column>
        <el-table-column prop="baseUnitCode" label="基本单位" width="100"><template #default="{row}">{{ zh(row.unitLabel || row.baseUnitCode) }}</template></el-table-column>
        <el-table-column prop="purchaseUnitCode" label="采购单位" width="100"><template #default="{row}">{{ zh(row.purchaseUnitCode) }}</template></el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="110" />
        <el-table-column prop="inspectionRequired" label="是否需检验" width="120"><template #default="{row}">{{ zh(row.inspectionRequired) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
      </el-table>
      <TablePager v-if="rows.length || total" v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
  </section>
</template>

<style scoped>
.material-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-select{width:170px}
</style>
