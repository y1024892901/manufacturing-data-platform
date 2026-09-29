<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh } from '../../shared/display'
import { loadMdmUnits } from '../../shared/mdmOptions'

type Option = { label: string; value: string }
type Field = { key: string; label: string; type?: 'text' | 'number' | 'date' | 'textarea' | 'select'; required?: boolean; options?: Option[]; min?: number; max?: number; placeholder?: string }
type Column = { key: string; label: string; unit?: string }
type PageConfig = { title: string; intro: string; endpoint: string; createText: string; fields: Field[]; columns: Column[] }

const energyTypes: Option[] = [
  { label: '电力', value: 'ELECTRIC' }, { label: '水', value: 'WATER' }, { label: '天然气', value: 'GAS' },
  { label: '蒸汽', value: 'STEAM' }, { label: '压缩空气', value: 'COMPRESSED_AIR' }, { label: '其他能源', value: 'OTHER' }
]
const units = ref<Option[]>([])
const configurations: Record<string, PageConfig> = {
  meters: {
    title: '能源仪表与采集点', intro: '维护计量仪表、采集周期、归属对象和校准信息。', endpoint: '/energy/meters', createText: '新增仪表',
    fields: [
      { key: 'meterCode', label: '仪表编码', required: true }, { key: 'meterName', label: '仪表名称', required: true },
      { key: 'energyType', label: '能源类型', type: 'select', required: true, options: energyTypes },
      { key: 'unitCode', label: '计量单位', type: 'select', required: true },
      { key: 'meterKind', label: '计量层级', type: 'select', required: true, options: [{ label: '总表', value: 'MAIN' }, { label: '分表', value: 'SUBMETER' }] },
      { key: 'status', label: '使用状态', type: 'select', required: true, options: [{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }, { label: '校准维护', value: 'MAINTENANCE' }] },
      { key: 'workshopCode', label: '所属车间编码' }, { key: 'equipmentCode', label: '绑定设备编码' },
      { key: 'parentMeterCode', label: '上级仪表编码' }, { key: 'installLocation', label: '安装位置' },
      { key: 'multiplier', label: '仪表倍率', type: 'number', min: 0 }, { key: 'readIntervalMinutes', label: '采集间隔（分钟）', type: 'number', min: 1 },
      { key: 'calibrationDate', label: '最近校准日期', type: 'date' }, { key: 'warningThresholdPercent', label: '能耗预警偏差（%）', type: 'number', min: 0 },
      { key: 'dataSource', label: '数据来源' }, { key: 'responsiblePerson', label: '仪表负责人' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ],
    columns: [{ key: 'meterCode', label: '仪表编码' }, { key: 'meterName', label: '仪表名称' }, { key: 'energyType', label: '能源类型' }, { key: 'unitCode', label: '计量单位' }, { key: 'meterKind', label: '计量层级' }, { key: 'workshopCode', label: '所属车间' }, { key: 'status', label: '使用状态' }]
  },
  equipment: {
    title: '设备能耗', intro: '按设备、日期和能源介质维护读数；录入基线与预警偏差后自动计算单耗并生成超限告警。', endpoint: '/energy/usages', createText: '登记设备能耗',
    fields: [
      { key: 'equipmentCode', label: '设备编码', required: true }, { key: 'meterCode', label: '仪表编码' },
      { key: 'statDate', label: '统计日期', type: 'date', required: true }, { key: 'statHour', label: '统计小时（0 至 23）', type: 'number', min: 0, max: 23 },
      { key: 'energyType', label: '能源类型', type: 'select', required: true, options: energyTypes },
      { key: 'energyValue', label: '能耗量', type: 'number', required: true, min: 0 }, { key: 'unitCode', label: '计量单位', type: 'select', required: true },
      { key: 'runHours', label: '设备运行小时', type: 'number', min: 0 }, { key: 'outputQty', label: '同期产出数量', type: 'number', min: 0 },
      { key: 'baselineValue', label: '同期能耗基线', type: 'number', min: 0 },
      { key: 'baselinePeriodStartDate', label: '基线期间起始日期', type: 'date' }, { key: 'baselinePeriodEndDate', label: '基线期间结束日期', type: 'date' },
      { key: 'warningThresholdPercent', label: '预警偏差（%）', type: 'number', min: 0 },
      { key: 'shiftCode', label: '班次编码' }, { key: 'productionOrderNo', label: '生产订单号' }, { key: 'remark', label: '备注', type: 'textarea' }
    ],
    columns: [{ key: 'equipmentCode', label: '设备编码' }, { key: 'meterCode', label: '仪表编码' }, { key: 'statDate', label: '统计日期' }, { key: 'energyType', label: '能源类型' }, { key: 'energyValue', label: '能耗量', unit: 'unitCode' }, { key: 'outputQty', label: '同期产出' }, { key: 'baselinePeriodStartDate', label: '基线起始日期' }, { key: 'baselinePeriodEndDate', label: '基线结束日期' }, { key: 'unitConsumption', label: '单位能耗', unit: 'unitCode' }, { key: 'abnormal', label: '是否超限' }]
  },
  workshops: {
    title: '车间能耗', intro: '按车间、日期和能源介质维护汇总，结合产出与基线计算单位能耗和偏差。', endpoint: '/energy/workshop-usages', createText: '登记车间能耗',
    fields: [
      { key: 'workshopCode', label: '车间编码', required: true }, { key: 'statDate', label: '统计日期', type: 'date', required: true },
      { key: 'energyType', label: '能源类型', type: 'select', required: true, options: energyTypes },
      { key: 'energyValue', label: '能耗量', type: 'number', required: true, min: 0 }, { key: 'unitCode', label: '计量单位', type: 'select', required: true },
      { key: 'totalOutput', label: '同期总产出', type: 'number', min: 0 }, { key: 'baselineValue', label: '同期能耗基线', type: 'number', min: 0 },
      { key: 'baselinePeriodStartDate', label: '基线期间起始日期', type: 'date' }, { key: 'baselinePeriodEndDate', label: '基线期间结束日期', type: 'date' },
      { key: 'warningThresholdPercent', label: '预警偏差（%）', type: 'number', min: 0 }, { key: 'shiftCode', label: '班次编码' },
      { key: 'costCenterCode', label: '成本中心编码' }, { key: 'remark', label: '备注', type: 'textarea' }
    ],
    columns: [{ key: 'workshopCode', label: '车间编码' }, { key: 'statDate', label: '统计日期' }, { key: 'energyType', label: '能源类型' }, { key: 'energyValue', label: '能耗量', unit: 'unitCode' }, { key: 'totalOutput', label: '同期总产出' }, { key: 'baselinePeriodStartDate', label: '基线起始日期' }, { key: 'baselinePeriodEndDate', label: '基线结束日期' }, { key: 'unitConsumption', label: '单位能耗', unit: 'unitCode' }, { key: 'deviationRate', label: '偏离基线（%）' }, { key: 'abnormal', label: '是否超限' }]
  },
  alerts: {
    title: '能耗异常告警', intro: '查看自动生成的基线超限告警，也可登记外部发现的能耗异常并跟踪处理结果。', endpoint: '/energy/alerts', createText: '登记告警',
    fields: [
      { key: 'alertNo', label: '告警编号', required: true },
      { key: 'sourceType', label: '告警来源', type: 'select', required: true, options: [{ label: '能源仪表', value: 'METER' }, { label: '设备能耗', value: 'EQUIPMENT' }, { label: '车间能耗', value: 'WORKSHOP' }] },
      { key: 'sourceId', label: '关联记录编号', type: 'number', min: 1 }, { key: 'objectCode', label: '对象编码', required: true },
      { key: 'energyType', label: '能源类型', type: 'select', required: true, options: energyTypes }, { key: 'statDate', label: '发生日期', type: 'date', required: true },
      { key: 'actualValue', label: '实际能耗', type: 'number', required: true, min: 0 }, { key: 'thresholdValue', label: '预警阈值', type: 'number', required: true, min: 0 },
      { key: 'deviationRate', label: '偏离率（%）', type: 'number' },
      { key: 'severity', label: '告警等级', type: 'select', required: true, options: [{ label: '低', value: 'LOW' }, { label: '中', value: 'MEDIUM' }, { label: '高', value: 'HIGH' }, { label: '严重', value: 'CRITICAL' }] },
      { key: 'status', label: '处置状态', type: 'select', required: true, options: [{ label: '待处理', value: 'OPEN' }, { label: '处理中', value: 'PROCESSING' }, { label: '已关闭', value: 'CLOSED' }, { label: '已忽略', value: 'IGNORED' }] },
      { key: 'assignedTo', label: '处置负责人' }, { key: 'dueDate', label: '要求完成日期', type: 'date' },
      { key: 'cause', label: '异常原因', type: 'textarea' }, { key: 'correctiveAction', label: '处置措施', type: 'textarea' }
    ],
    columns: [{ key: 'alertNo', label: '告警编号' }, { key: 'sourceType', label: '告警来源' }, { key: 'objectCode', label: '对象编码' }, { key: 'energyType', label: '能源类型' }, { key: 'statDate', label: '发生日期' }, { key: 'actualValue', label: '实际能耗' }, { key: 'thresholdValue', label: '预警阈值' }, { key: 'severity', label: '告警等级' }, { key: 'status', label: '处置状态' }, { key: 'assignedTo', label: '处置负责人' }]
  }
}

const props = defineProps<{ kind: string }>()
const config = computed(() => configurations[props.kind] || configurations.equipment)
const rows = ref<Record<string, any>[]>([])
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const dialog = ref(false)
const detailDialog = ref(false)
const editingId = ref<number | null>(null)
const detailRecord = ref<Record<string, any>>({})
const saving = ref(false)
const form = reactive<Record<string, any>>({})

function todayLocal() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const filteredRows = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  if (!query) return rows.value
  return rows.value.filter(row => config.value.fields.some(field => {
    const raw = row[field.key]
    return String(raw ?? '').toLocaleLowerCase().includes(query) || zh(raw).toLocaleLowerCase().includes(query)
  }))
})
const visibleRows = computed(() => filteredRows.value.slice((page.value - 1) * size.value, page.value * size.value))

function resetForm() {
  Object.keys(form).forEach(key => delete form[key])
  for (const field of config.value.fields) {
    if (field.type === 'number') form[field.key] = null
    else if (field.type === 'date') form[field.key] = field.required ? todayLocal() : ''
    else if (field.key === 'energyType') form[field.key] = 'ELECTRIC'
    else if (field.key === 'unitCode') form[field.key] = units.value.find(unit => unit.value === 'KWH')?.value || units.value[0]?.value || ''
    else if (field.key === 'meterKind') form[field.key] = 'SUBMETER'
    else if (field.key === 'status') form[field.key] = props.kind === 'alerts' ? 'OPEN' : 'ACTIVE'
    else if (field.key === 'severity') form[field.key] = 'MEDIUM'
    else if (field.key === 'sourceType') form[field.key] = 'EQUIPMENT'
    else form[field.key] = ''
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(config.value.endpoint)
    rows.value = Array.isArray(data.data) ? data.data : []
    if (page.value > Math.max(1, Math.ceil(filteredRows.value.length / size.value))) page.value = 1
  } catch {
    // HTTP 拦截器会直接显示中文错误弹框。
  } finally { loading.value = false }
}

async function openCreate() {
  if (!units.value.length) {
    try { units.value = (await loadMdmUnits()).map(unit => ({ label: unit.label, value: unit.value })) }
    catch { return }
  }
  editingId.value = null
  resetForm()
  dialog.value = true
}

async function openEdit(row: Record<string, any>) {
  try {
    const { data } = await http.get(`${config.value.endpoint}/${row.id}`)
    editingId.value = data.data.id
    resetForm()
    for (const field of config.value.fields) form[field.key] = data.data[field.key] ?? (field.type === 'number' ? null : '')
    dialog.value = true
  } catch {
    // HTTP 拦截器会直接显示中文错误弹框。
  }
}

async function openDetail(row: Record<string, any>) {
  try {
    const { data } = await http.get(`${config.value.endpoint}/${row.id}`)
    detailRecord.value = data.data
    detailDialog.value = true
  } catch {
    // HTTP 拦截器会直接显示中文错误弹框。
  }
}

async function save() {
  const missing = config.value.fields.find(field => field.required && (form[field.key] == null || String(form[field.key]).trim() === ''))
  if (missing) { ElMessage.warning(`请填写${missing.label}`); return }
  saving.value = true
  const payload: Record<string, any> = {}
  for (const field of config.value.fields) {
    const value = form[field.key]
    payload[field.key] = value === '' ? null : value
  }
  try {
    if (editingId.value == null) await http.post(config.value.endpoint, payload)
    else await http.put(`${config.value.endpoint}/${editingId.value}`, payload)
    ElMessage.success(editingId.value == null ? '新增成功' : '修改成功')
    dialog.value = false
    await load()
  } catch {
    // HTTP 拦截器会直接显示服务端返回的中文错误弹框。
  } finally { saving.value = false }
}

async function remove(row: Record<string, any>) {
  try {
    await ElMessageBox.confirm(`确定删除“${row.alertNo || row.meterName || row.equipmentCode || row.workshopCode || row.id}”吗？`, '删除确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    await http.delete(`${config.value.endpoint}/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // HTTP 拦截器会直接显示服务端返回的中文错误弹框。
  }
}

function formatDate(value: unknown) {
  const match = String(value ?? '').match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/)
  return match ? `${match[1]}年${Number(match[2])}月${Number(match[3])}日${match[4] ? ` ${match[4]}:${match[5]}` : ''}` : '—'
}

function displayValue(value: unknown, field?: Field) {
  if (value == null || value === '') return '—'
  const options = field ? fieldOptions(field) : []
  if (options.length) return options.find(option => option.value === value)?.label || zh(value)
  if (field?.type === 'date' || /^\d{4}-\d{2}-\d{2}/.test(String(value))) return formatDate(value)
  if (field?.type === 'number' || typeof value === 'number') {
    const formatted = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(Number(value))
    return field?.key === 'deviationRate' || field?.key === 'warningThresholdPercent' ? `${formatted}%` : formatted
  }
  return zh(value)
}

function fieldByKey(key: string) { return config.value.fields.find(field => field.key === key) }
function fieldOptions(field: Field) { return field.key === 'unitCode' ? units.value : field.options || [] }
function optionLabel(field: Field, value: unknown) { return fieldOptions(field).find(option => option.value === value)?.label || displayValue(value, field) }

watch(() => props.kind, () => { page.value = 1; keyword.value = ''; load() })
watch(keyword, () => { page.value = 1 })
onMounted(async () => {
  try { units.value = (await loadMdmUnits()).map(unit => ({ label: unit.label, value: unit.value })) }
  catch { /* HTTP 拦截器会直接显示中文错误弹框。 */ }
  await load()
})
</script>

<template>
  <section class="energy-page">
    <div class="page-head">
      <div><span class="eyebrow">能源管理</span><h1>{{ config.title }}</h1><p>{{ config.intro }}</p></div>
      <div class="head-actions"><el-button @click="load">刷新</el-button><el-button type="primary" @click="openCreate">{{ config.createText }}</el-button></div>
    </div>
    <el-card shadow="never" class="records-card">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="搜索编号、设备、车间或能源类型"/><span>共 {{ filteredRows.length }} 条记录</span></div>
      <el-table :data="visibleRows" v-loading="loading" stripe empty-text="暂无记录">
        <el-table-column v-for="column in config.columns" :key="column.key" :label="column.label" :min-width="column.key.includes('Code') || column.key.endsWith('No') ? 135 : 115" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="column.key === 'status' || column.key === 'severity'" size="small" :type="column.key === 'status' ? (row.status === 'OPEN' ? 'warning' : row.status === 'CLOSED' || row.status === 'ACTIVE' ? 'success' : 'info') : (row.severity === 'CRITICAL' || row.severity === 'HIGH' ? 'danger' : 'warning')">{{ displayValue(row[column.key], fieldByKey(column.key)) }}</el-tag>
            <el-tag v-else-if="column.key === 'abnormal'" size="small" :type="row.abnormal ? 'danger' : 'success'">{{ row.abnormal ? '是' : '否' }}</el-tag>
            <span v-else>{{ displayValue(row[column.key], fieldByKey(column.key)) }}<template v-if="column.unit && row[column.key] != null"> {{ displayValue(row[column.unit], fieldByKey(column.unit)) }}</template></span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-if="filteredRows.length" v-model:page="page" v-model:size="size" :total="filteredRows.length" :disabled="loading" @change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="editingId == null ? config.createText : `编辑${config.title}`" width="820px" destroy-on-close>
      <el-form label-position="top" class="energy-form">
        <el-row :gutter="16">
          <el-col v-for="field in config.fields" :key="field.key" :span="field.type === 'textarea' ? 24 : 12">
            <el-form-item :label="field.required ? `${field.label}（必填）` : `${field.label}（非必填）`" :required="field.required">
              <el-select v-if="field.type === 'select'" v-model="form[field.key]" :clearable="!field.required" placeholder="请选择">
                <el-option v-for="option in fieldOptions(field)" :key="option.value" :label="option.label" :value="option.value"/>
              </el-select>
              <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" type="date" value-format="YYYY-MM-DD" format="YYYY年MM月DD日" :placeholder="`请选择${field.label}`"/>
              <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="field.min ?? 0" :max="field.max" :controls="false" :placeholder="`请输入${field.label}`"/>
              <el-input v-else-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="3" :placeholder="`请输入${field.label}`"/>
              <el-input v-else v-model="form[field.key]" :placeholder="field.placeholder || `请输入${field.label}`"/>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDialog" :title="`${config.title}详情`" size="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="field in config.fields" :key="field.key" :label="field.label">{{ optionLabel(field, detailRecord[field.key]) }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRecord.createdAt" label="创建时间">{{ formatDate(detailRecord.createdAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRecord.updatedAt" label="更新时间">{{ formatDate(detailRecord.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.energy-page{max-width:1480px;margin:0 auto}.page-head{display:flex;justify-content:space-between;align-items:flex-end;gap:18px;margin-bottom:16px}.eyebrow{color:#28764f;font-size:11px;font-weight:800;letter-spacing:1px}.page-head h1{margin:6px 0;color:#263f5c;font-size:25px}.page-head p{margin:0;color:#8291a4;font-size:13px}.head-actions{display:flex;gap:8px}.records-card{border:1px solid #e3eaf2;border-radius:12px}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px}.toolbar .el-input{max-width:360px}.toolbar span{color:#8291a4;font-size:12px;white-space:nowrap}.energy-form :deep(.el-select),.energy-form :deep(.el-date-editor),.energy-form :deep(.el-input-number),.energy-form :deep(.el-input){width:100%}.energy-form :deep(.el-form-item){margin-bottom:12px}.energy-form :deep(.el-form-item__label){color:#53657e;font-weight:600}@media(max-width:720px){.page-head{display:block}.head-actions{margin-top:12px}.energy-form :deep(.el-col){width:100%;max-width:100%;flex:0 0 100%}}
</style>
