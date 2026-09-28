<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh, zhKey } from '../../shared/display'

const tabs = [
  { label: '合并记录', key: 'merges' },
  { label: '编码映射', key: 'mappings' },
  { label: '变更历史', key: 'history' }
]
const tab = ref('merges')
const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

function display(value: unknown, key: string): string {
  if (key === 'status') return mdmStatus(value)
  if ((key === 'before_json' || key === 'after_json') && typeof value === 'string') {
    try { return display(JSON.parse(value), key) } catch { return value }
  }
  if (Array.isArray(value)) return value.map(item => display(item, key)).join('、')
  if (value && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).map(([name, item]) => `${zhKey(name)}：${display(item, name)}`).join('；')
  }
  return String(zh(value))
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/mdm/governance/${tab.value}`, { params: { page: page.value, size: size.value } })
    rows.value = data.data?.content || []
    total.value = data.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

watch(tab, () => { page.value = 1; load() })
onMounted(load)
</script>

<template>
  <section class="governance-page">
    <header class="page-head">
      <div><span>数据治理 · 只读追溯</span><h1>主数据治理记录</h1><p>查询历史合并、旧编码映射与主数据变更痕迹。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane v-for="item in tabs" :key="item.key" :label="item.label" :name="item.key" />
      </el-tabs>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无治理记录">
        <el-table-column v-for="key in Object.keys(rows[0] || {})" :key="key" :label="zhKey(key)" min-width="140" show-overflow-tooltip>
          <template #default="{row}">{{ display(row[key], key) }}</template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>
  </section>
</template>

<style scoped>
.governance-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}
</style>
