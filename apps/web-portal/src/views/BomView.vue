<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '../api/http'
import TablePager from '../shared/components/TablePager.vue'
import { zh } from '../shared/display'

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const status = ref('')
const detail = ref<any>(null)
const drawer = ref(false)
const substitutes = ref<Record<number, any[]>>({})

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/mdm/boms', {
      params: { page: page.value, size: size.value, status: status.value || undefined }
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
  const { data } = await http.get(`/mdm/boms/${row.id}`)
  detail.value = data.data
  drawer.value = true
  substitutes.value = {}
  const lines = detail.value.lines || []
  const results = await Promise.all(lines.map((line: any) =>
    http.get(`/mdm/boms/${detail.value.id}/lines/${line.id}/substitutes`)
  ))
  lines.forEach((line: any, index: number) => { substitutes.value[line.id] = results[index].data.data || [] })
}

function filter() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <section class="bom-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>BOM 与替代料</h1><p>查看物料清单版本、组成明细、生效日期与替代料关系。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条 BOM 记录</div>
        <el-select v-model="status" clearable placeholder="全部状态" @change="filter">
          <el-option v-for="item in ['DRAFT','PENDING','PUBLISHED','REJECTED','DISABLED']" :key="item" :label="mdmStatus(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无 BOM 数据">
        <el-table-column prop="bomCode" label="BOM 编码" min-width="135" />
        <el-table-column prop="bomName" label="BOM 名称" min-width="170" />
        <el-table-column prop="productCode" label="产品编码" min-width="130" />
        <el-table-column prop="bomType" label="BOM 类型" width="110"><template #default="{row}">{{ zh(row.bomType) }}</template></el-table-column>
        <el-table-column prop="bomVersion" label="BOM 版本" width="105" />
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column prop="current" label="当前版本" width="105"><template #default="{row}">{{ zh(row.current) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="versionNo" label="记录版本" width="95" />
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看明细</el-button></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>

    <el-drawer v-model="drawer" title="BOM 版本详情" size="760px">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="BOM 编码">{{ detail.bomCode }}</el-descriptions-item>
          <el-descriptions-item label="BOM 名称">{{ detail.bomName }}</el-descriptions-item>
          <el-descriptions-item label="BOM 版本">{{ detail.bomVersion }}</el-descriptions-item>
          <el-descriptions-item label="BOM 类型">{{ zh(detail.bomType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ mdmStatus(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.effectiveDate || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="detail-heading">物料组成</h3>
        <div v-for="line in detail.lines || []" :key="line.id" class="line">
          <div class="line-main"><b>{{ line.lineNo }} · {{ line.childMaterialCode }} {{ line.childMaterialName }}</b><span>用量 {{ line.qtyPer }} {{ zh(line.unitCode) }}</span></div>
          <div class="substitutes">
            <span v-if="!(substitutes[line.id] || []).length" class="empty-sub">暂无替代料</span>
            <el-tag v-for="item in substitutes[line.id] || []" :key="item.id" :type="item.status==='ACTIVE'?'success':'info'">
              {{ item.substituteMaterialCode }} · 优先级 {{ item.priorityNo }} · {{ zh(item.status) }}
            </el-tag>
          </div>
        </div>
        <el-empty v-if="!(detail.lines || []).length" description="此版本暂无物料明细" />
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.bom-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-select{width:170px}.detail-heading{margin:24px 0 10px;color:#2d4665;font-size:15px}.line{padding:15px 0;border-bottom:1px solid #edf1f6}.line-main,.substitutes{display:flex;align-items:center;flex-wrap:wrap;gap:10px}.line-main{justify-content:space-between;color:#334d6c}.line-main span,.empty-sub{color:#8493a6;font-size:12px}.substitutes{margin-top:10px}
</style>
