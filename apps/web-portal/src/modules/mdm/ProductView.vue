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
    const { data } = await http.get('/mdm/products', {
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
  const { data } = await http.get(`/mdm/products/${row.id}`)
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
  <section class="product-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>产品主数据</h1><p>查询产品档案、生命周期、单位与版本信息。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条产品记录</div>
        <el-input v-model="keyword" clearable placeholder="输入产品编码或名称" @keyup.enter="search" />
        <el-select v-model="status" clearable placeholder="全部状态" @change="search">
          <el-option v-for="item in ['DRAFT','PENDING','PUBLISHED','REJECTED','CHANGING','DISABLED']" :key="item" :label="mdmStatus(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无产品数据">
        <el-table-column prop="productCode" label="产品编码" min-width="140" />
        <el-table-column prop="productName" label="产品名称" min-width="180" />
        <el-table-column prop="productModel" label="产品型号" min-width="145" />
        <el-table-column prop="lifecycleStatus" label="生命周期" min-width="115"><template #default="{row}">{{ zh(row.lifecycleStatus) }}</template></el-table-column>
        <el-table-column prop="unitCode" label="计量单位" width="100"><template #default="{row}">{{ zh(row.unitCode) }}</template></el-table-column>
        <el-table-column prop="weightKg" label="单重（千克）" width="125" />
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看详情</el-button></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
    <el-drawer v-model="detailVisible" title="产品档案详情" size="620px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="产品型号">{{ detail.productModel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生命周期">{{ zh(detail.lifecycleStatus) }}</el-descriptions-item>
        <el-descriptions-item label="计量单位">{{ zh(detail.unitCode) }}</el-descriptions-item>
        <el-descriptions-item label="单重（千克）">{{ detail.weightKg ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="上市日期">{{ detail.launchDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="停产日期">{{ detail.eolDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="记录状态">{{ mdmStatus(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="变更原因" :span="2">{{ detail.changeReason || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.product-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-input{width:280px}.toolbar .el-select{width:165px}
</style>
