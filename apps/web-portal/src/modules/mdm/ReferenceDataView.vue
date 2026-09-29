<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'
import { showErrorDialog } from '../../shared/errorDialog'
import { zh, zhKey } from '../../shared/display'

type Field = { key: string; db: string; label: string }
type Config = { title: string; subtitle: string; fields: Field[] }
const props = defineProps<{ kind: string }>()
const configs: Record<string, Config> = {
  categories: { title: '物料分类', subtitle: '维护统一的物料分类层级；物料档案只能挂接已发布的末级分类。', fields: [
    { key: 'categoryCode', db: 'category_code', label: '分类编码' }, { key: 'categoryName', db: 'category_name', label: '分类名称' },
    { key: 'parentId', db: 'parent_id', label: '上级分类' }, { key: 'categoryLevel', db: 'category_level', label: '层级' },
    { key: 'leaf', db: 'is_leaf', label: '末级分类' }, { key: 'categoryPath', db: 'category_path', label: '分类路径' }
  ] },
  units: { title: '计量单位', subtitle: '维护制造、采购、仓储、质检和能源业务共用的单位及换算关系。', fields: [
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

const auth = useAuthStore()
const config = computed(() => configs[props.kind] || configs.categories)
const editable = computed(() => ['categories', 'units'].includes(props.kind))
const can = (permission: string) => Boolean(auth.isAdmin || auth.user?.permissions?.includes(permission))
const rows = ref<any[]>([])
const loading = ref(false)
const selected = ref<any>(null)
const detailVisible = ref(false)
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<Record<string, any>>({})
const unitTypes = [
  { label: '数量单位', value: 'QUANTITY' }, { label: '重量单位', value: 'WEIGHT' },
  { label: '长度单位', value: 'LENGTH' }, { label: '面积单位', value: 'AREA' },
  { label: '体积单位', value: 'VOLUME' }, { label: '时间单位', value: 'TIME' }, { label: '能源单位', value: 'ENERGY' },
]
const categoryParents = computed(() => {
  const current = rows.value.find(row => Number(row.id) === editingId.value)
  const ownPath = String(current?.category_path || '')
  return rows.value.filter(row => row.status === 'PUBLISHED'
    && Number(row.id) !== editingId.value
    && !(ownPath && String(row.category_path || '').startsWith(`${ownPath}/`)))
})
const categoryPathPreview = computed(() => {
  const name = String(form.categoryName || '').trim()
  if (!name) return '填写分类名称后自动生成'
  const parent = rows.value.find(row => Number(row.id) === Number(form.parentId))
  return parent?.category_path ? `${parent.category_path}/${name}` : name
})

function errorMessage(error: any, fallback: string) { return error?.response?.data?.message || error?.message || fallback }
function mdmStatus(value: unknown) { return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value) }
function fieldDisplay(row: any, field: Field) {
  if (field.key === 'leaf') return row[field.db] ? '是' : '否'
  if (field.key === 'parentId' && props.kind === 'categories') {
    const parent = rows.value.find(item => Number(item.id) === Number(row[field.db]))
    return parent ? `${parent.category_name}（${parent.category_code}）` : '—'
  }
  if (field.key === 'unitType') return unitTypes.find(item => item.value === row[field.db])?.label || zh(row[field.db])
  if (field.key === 'baseUnitCode') {
    const unit = rows.value.find(item => item.unit_code === row[field.db])
    return unit ? `${unit.unit_name}（${unit.unit_code}）` : row[field.db] || '—'
  }
  return row[field.db] == null || row[field.db] === '' ? '—' : zh(row[field.db])
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/mdm/reference/${props.kind}`)
    rows.value = data.data || []
  } catch (error) {
    showErrorDialog(errorMessage(error, `${config.value.title}加载失败`))
  } finally { loading.value = false }
}

function clearForm() { Object.keys(form).forEach(key => delete form[key]) }
function openCreate() {
  editingId.value = null
  clearForm()
  if (props.kind === 'categories') Object.assign(form, { categoryCode: '', categoryName: '', parentId: null, leaf: true })
  else Object.assign(form, { unitCode: '', unitName: '', unitType: 'QUANTITY', baseUnitCode: null, convertRate: 1 })
  dialog.value = true
}

function openEdit(row: any) {
  editingId.value = Number(row.id)
  clearForm()
  if (props.kind === 'categories') Object.assign(form, {
    categoryCode: row.category_code, categoryName: row.category_name, parentId: row.parent_id,
    leaf: Boolean(row.is_leaf),
  })
  else Object.assign(form, {
    unitCode: row.unit_code, unitName: row.unit_name, unitType: row.unit_type,
    baseUnitCode: row.base_unit_code, convertRate: Number(row.convert_rate ?? 1),
  })
  dialog.value = true
}

function payload() {
  if (props.kind === 'categories') {
    const name = String(form.categoryName || '').trim()
    const parent = rows.value.find(row => Number(row.id) === Number(form.parentId))
    return {
      categoryCode: String(form.categoryCode || '').trim(), categoryName: name,
      parentId: form.parentId ? Number(form.parentId) : null,
      categoryLevel: parent ? Number(parent.category_level) + 1 : 1,
      leaf: Boolean(form.leaf), categoryPath: parent?.category_path ? `${parent.category_path}/${name}` : name,
    }
  }
  return {
    unitCode: String(form.unitCode || '').trim(), unitName: String(form.unitName || '').trim(),
    unitType: form.unitType, baseUnitCode: form.baseUnitCode || null, convertRate: Number(form.convertRate || 1),
  }
}

async function save() {
  const body = payload()
  if (props.kind === 'categories' && (!body.categoryCode || !body.categoryName)) { showErrorDialog('分类编码和分类名称不能为空'); return }
  if (props.kind === 'units' && (!body.unitCode || !body.unitName || !body.unitType)) { showErrorDialog('单位编码、名称和类型不能为空'); return }
  if (props.kind === 'units' && (!Number.isFinite(Number(body.convertRate)) || Number(body.convertRate) <= 0)) { showErrorDialog('换算率必须大于0'); return }
  saving.value = true
  try {
    const url = `/mdm/reference/${props.kind}${editingId.value === null ? '' : `/${editingId.value}`}`
    if (editingId.value === null) await http.post(url, body)
    else await http.put(url, body)
    ElMessage.success(editingId.value === null ? `${config.value.title}已新增` : `${config.value.title}已更新`)
    dialog.value = false
    await load()
  } catch (error) { showErrorDialog(errorMessage(error, `${config.value.title}保存失败`)) }
  finally { saving.value = false }
}

async function disable(row: any) {
  const code = props.kind === 'categories' ? row.category_code : row.unit_code
  try {
    await ElMessageBox.confirm(`停用“${code} · ${props.kind === 'categories' ? row.category_name : row.unit_name}”？停用后新的业务单据不能再选择。`, '停用确认', { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' })
    await http.delete(`/mdm/reference/${props.kind}/${row.id}`)
    ElMessage.success(`${config.value.title}已停用`)
    await load()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    showErrorDialog(errorMessage(error, `${config.value.title}停用失败`))
  }
}

function open(row: any) { selected.value = row; detailVisible.value = true }
watch(() => props.kind, () => { dialog.value = false; void load() })
onMounted(load)
</script>

<template>
  <section class="reference-page">
    <header class="page-head">
      <div><span>权威主数据 · 统一维护</span><h1>{{ config.title }}</h1><p>{{ config.subtitle }}</p></div>
      <div class="head-actions">
        <el-tag v-if="!editable" type="info" effect="light" round>只读查询</el-tag>
        <el-button v-if="editable && can('MDM:BASE_DATA:CREATE')" type="primary" @click="openCreate">新增{{ props.kind==='categories'?'分类':'单位' }}</el-button>
      </div>
    </header>
    <el-card shadow="never">
      <div class="table-caption">{{ config.title }}档案 <span>{{ rows.length }} 条记录</span><el-button text :loading="loading" @click="load">刷新</el-button></div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无主数据">
        <el-table-column v-for="field in config.fields" :key="field.db" :prop="field.db" :label="field.label" min-width="135">
          <template #default="{row}">{{ fieldDisplay(row, field) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="version_no" label="版本" width="85" />
        <el-table-column label="操作" :width="editable ? 230 : 105" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" @click="open(row)">查看详情</el-button>
            <template v-if="editable && row.status==='PUBLISHED'">
              <el-button v-if="can('MDM:BASE_DATA:UPDATE')" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="can('MDM:BASE_DATA:DELETE')" link type="danger" @click="disable(row)">停用</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无可查询的主数据" />
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId===null?'新增':'编辑'}${props.kind==='categories'?'物料分类':'计量单位'}`" width="660px" destroy-on-close>
      <el-form label-position="top" class="reference-form">
        <template v-if="props.kind==='categories'">
          <el-form-item label="分类编码" required><el-input v-model="form.categoryCode" :disabled="editingId!==null" placeholder="请输入唯一分类编码" /></el-form-item>
          <el-form-item label="分类名称" required><el-input v-model="form.categoryName" placeholder="例如：电子元件、紧固件" /></el-form-item>
          <el-form-item label="上级分类"><el-select v-model="form.parentId" clearable filterable style="width:100%" placeholder="顶级分类可留空"><el-option v-for="row in categoryParents" :key="row.id" :label="`${row.category_path || row.category_name}（${row.category_code}）`" :value="row.id" /></el-select></el-form-item>
          <el-form-item label="末级分类"><el-switch v-model="form.leaf" active-text="可挂物料" inactive-text="作为上级" /></el-form-item>
          <el-form-item label="分类路径预览"><el-input :model-value="categoryPathPreview" disabled /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="单位编码" required><el-input v-model="form.unitCode" :disabled="editingId!==null" placeholder="例如：PCS、KG、M2" /></el-form-item>
          <el-form-item label="单位名称" required><el-input v-model="form.unitName" placeholder="例如：件、千克、平方米" /></el-form-item>
          <el-form-item label="单位类型" required><el-select v-model="form.unitType" style="width:100%"><el-option v-for="item in unitTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="换算基准单位"><el-select v-model="form.baseUnitCode" clearable filterable style="width:100%" placeholder="基准单位可留空"><el-option v-for="row in rows.filter(item=>item.status==='PUBLISHED')" :key="row.unit_code" :label="`${row.unit_name}（${row.unit_code}）`" :value="row.unit_code" /></el-select></el-form-item>
          <el-form-item label="换算率" required><el-input-number v-model="form.convertRate" :min="0.000001" :precision="6" controls-position="right" style="width:100%" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="`${config.title}详情`" size="560px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item v-for="field in config.fields" :key="field.db" :label="field.label">{{ fieldDisplay(selected, field) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ mdmStatus(selected.status) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ selected.version_no }}</el-descriptions-item>
        <el-descriptions-item v-if="selected.created_by" label="创建人">{{ selected.created_by }}</el-descriptions-item>
        <el-descriptions-item v-if="selected.updated_by" label="更新人">{{ selected.updated_by }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.reference-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.head-actions{display:flex;align-items:center;gap:10px}.table-caption{display:flex;align-items:center;gap:12px;margin:0 0 14px;color:#334d6b;font-size:14px;font-weight:700}.table-caption span{margin-left:auto;color:#8795a8;font-size:12px;font-weight:400}.reference-form :deep(.el-form-item){margin-bottom:18px}
</style>
