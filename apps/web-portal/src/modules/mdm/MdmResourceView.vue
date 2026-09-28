<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh, zhKey } from '../../shared/display'

type Field = { key: string; db: string; label: string }
const props = defineProps<{ kind: string }>()
const configs: Record<string, { title: string; fields: Field[] }> = {
  warehouses: { title: '仓库主数据', fields: [
    { key: 'warehouseCode', db: 'warehouse_code', label: '仓库编码' }, { key: 'warehouseName', db: 'warehouse_name', label: '仓库名称' },
    { key: 'factoryCode', db: 'factory_code', label: '工厂编码' }, { key: 'warehouseType', db: 'warehouse_type', label: '仓库类型' }
  ] },
  'work-centers': { title: '工作中心', fields: [
    { key: 'workCenterCode', db: 'work_center_code', label: '中心编码' }, { key: 'workCenterName', db: 'work_center_name', label: '中心名称' },
    { key: 'workshopCode', db: 'workshop_code', label: '车间编码' }, { key: 'capacityPerDay', db: 'capacity_per_day', label: '日产能' }
  ] },
  'production-versions': { title: '生产版本', fields: [
    { key: 'productionVersionCode', db: 'production_version_code', label: '版本编码' }, { key: 'productCode', db: 'product_code', label: '产品编码' },
    { key: 'bomCode', db: 'bom_code', label: 'BOM编码' }, { key: 'bomVersion', db: 'bom_version', label: 'BOM版本' },
    { key: 'routingCode', db: 'routing_code', label: '工艺路线' }, { key: 'routingVersion', db: 'routing_version', label: '路线版本' },
    { key: 'factoryCode', db: 'factory_code', label: '工厂编码' }, { key: 'effectiveDate', db: 'effective_date', label: '生效日期' }
  ] }
}

const config = computed(() => configs[props.kind])
const rows = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selected = ref<any>(null)
const detailVisible = ref(false)

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

function display(value: unknown, key: string) {
  if (key === 'status') return mdmStatus(value)
  return zh(value)
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/mdm/${props.kind}`, {
      params: { keyword: keyword.value || undefined, page: page.value, size: size.value }
    })
    rows.value = data.data?.content || []
    total.value = data.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function open(row: any) {
  selected.value = row
  detailVisible.value = true
}

watch(() => props.kind, () => { page.value = 1; keyword.value = ''; load() })
onMounted(load)
</script>

<template>
  <section class="resource-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>{{ config.title }}</h1><p>查询 {{ config.title }}档案与版本信息，下游业务通过统一主数据引用。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条记录</div>
        <el-input v-model="keyword" clearable placeholder="输入编码或名称" @keyup.enter="search" />
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无主数据">
        <el-table-column v-for="field in config.fields" :key="field.db" :prop="field.db" :label="field.label" min-width="135">
          <template #default="{row}">{{ display(row[field.db], field.key) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看详情</el-button></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
    <el-drawer v-model="detailVisible" :title="`${config.title}详情`" size="560px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item v-for="field in Object.keys(selected)" :key="field" :label="zhKey(field)">{{ display(selected[field], field) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.resource-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-input{width:280px}
</style>
