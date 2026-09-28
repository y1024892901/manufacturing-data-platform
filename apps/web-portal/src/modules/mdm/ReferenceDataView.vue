<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '../../api/http'
import { zh, zhKey } from '../../shared/display'

type Field = { key: string; db: string; label: string }
type Config = { title: string; subtitle: string; fields: Field[] }
const props = defineProps<{ kind: string }>()
const configs: Record<string, Config> = {
  categories: { title: '物料分类', subtitle: '查看物料分类层级、路径和末级挂载规则。', fields: [
    { key: 'categoryCode', db: 'category_code', label: '分类编码' }, { key: 'categoryName', db: 'category_name', label: '分类名称' },
    { key: 'parentId', db: 'parent_id', label: '上级记录编号' }, { key: 'categoryLevel', db: 'category_level', label: '层级' },
    { key: 'leaf', db: 'is_leaf', label: '末级分类' }, { key: 'categoryPath', db: 'category_path', label: '分类路径' }
  ] },
  units: { title: '单位与换算', subtitle: '查看数量、重量、长度、面积和时间单位及其换算关系。', fields: [
    { key: 'unitCode', db: 'unit_code', label: '单位编码' }, { key: 'unitName', db: 'unit_name', label: '单位名称' },
    { key: 'unitType', db: 'unit_type', label: '单位类型' }, { key: 'baseUnitCode', db: 'base_unit_code', label: '基本单位' },
    { key: 'convertRate', db: 'convert_rate', label: '换算率' }
  ] },
  organizations: { title: '组织架构', subtitle: '查看公司、工厂、车间和产线组织层级。', fields: [
    { key: 'orgCode', db: 'org_code', label: '组织编码' }, { key: 'orgName', db: 'org_name', label: '组织名称' },
    { key: 'orgType', db: 'org_type', label: '组织类型' }, { key: 'parentId', db: 'parent_id', label: '上级记录编号' },
    { key: 'orgLevel', db: 'org_level', label: '组织层级' }, { key: 'managerUserId', db: 'manager_user_id', label: '负责人编号' }
  ] },
  'cost-centers': { title: '成本中心', subtitle: '查看组织、生产订单、维修与能源成本归集信息。', fields: [
    { key: 'ccCode', db: 'cc_code', label: '成本中心编码' }, { key: 'ccName', db: 'cc_name', label: '成本中心名称' },
    { key: 'orgId', db: 'org_id', label: '所属组织编号' }, { key: 'ccType', db: 'cc_type', label: '成本中心类型' },
    { key: 'managerEmpId', db: 'manager_emp_id', label: '负责人员工编号' }
  ] },
  subjects: { title: '会计科目', subtitle: '查看资产、负债、权益、成本和收入科目层级。', fields: [
    { key: 'subjectCode', db: 'subject_code', label: '科目编码' }, { key: 'subjectName', db: 'subject_name', label: '科目名称' },
    { key: 'subjectType', db: 'subject_type', label: '科目类型' }, { key: 'parentId', db: 'parent_id', label: '上级记录编号' },
    { key: 'subjectLevel', db: 'subject_level', label: '科目层级' }, { key: 'leaf', db: 'is_leaf', label: '明细科目' }
  ] }
}

const config = computed(() => configs[props.kind])
const rows = ref<any[]>([])
const loading = ref(false)
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
    const { data } = await http.get(`/mdm/reference/${props.kind}`)
    rows.value = data.data || []
  } finally {
    loading.value = false
  }
}

function open(row: any) {
  selected.value = row
  detailVisible.value = true
}

watch(() => props.kind, load)
onMounted(load)
</script>

<template>
  <section class="reference-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>{{ config.title }}</h1><p>{{ config.subtitle }}</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="table-caption">{{ config.title }}档案 <span>{{ rows.length }} 条记录</span></div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无主数据">
        <el-table-column v-for="field in config.fields" :key="field.db" :prop="field.db" :label="field.label" min-width="135">
          <template #default="{row}">{{ display(row[field.db], field.key) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="version_no" label="版本" width="85" />
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看详情</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无可查询的主数据" />
    </el-card>
    <el-drawer v-model="detailVisible" :title="`${config.title}详情`" size="560px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item v-for="field in Object.keys(selected)" :key="field" :label="zhKey(field)">{{ display(selected[field], field) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.reference-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.table-caption{display:flex;justify-content:space-between;margin:0 0 14px;color:#334d6b;font-size:14px;font-weight:700}.table-caption span{color:#8795a8;font-size:12px;font-weight:400}
</style>
