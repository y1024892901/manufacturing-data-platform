<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh, zhKey } from '../../shared/display'
import { showErrorDialog } from '../../shared/errorDialog'

type Field = { key: string; db: string; label: string; required?: boolean; type?: 'select' | 'date' | 'textarea'; options?: string[]; placeholder?: string }
type Config = { title: string; subtitle: string; releasable: boolean; fields: Field[] }

const props = defineProps<{ kind: string }>()
const configs: Record<string, Config> = {
  families: {
    title: '产品族', subtitle: '维护产品族、市场细分和产品负责人。', releasable: false,
    fields: [
      { key: 'familyCode', db: 'family_code', label: '产品族编码', required: true },
      { key: 'familyName', db: 'family_name', label: '产品族名称', required: true },
      { key: 'marketSegment', db: 'market_segment', label: '市场细分' },
      { key: 'ownerUser', db: 'owner_user', label: '负责人账号' },
      { key: 'familyDescription', db: 'family_description', label: '产品族说明', type: 'textarea' },
      { key: 'status', db: 'status', label: '状态', type: 'select', options: ['ACTIVE', 'DISABLED'] }
    ]
  },
  versions: {
    title: '产品版本', subtitle: '维护产品工程版本、生命周期和适用范围。', releasable: true,
    fields: [
      { key: 'productCode', db: 'product_code', label: '产品编码', required: true },
      { key: 'versionNo', db: 'version_no', label: '版本号', required: true },
      { key: 'versionName', db: 'version_name', label: '版本名称' },
      { key: 'lifecycleStatus', db: 'lifecycle_status', label: '生命周期', type: 'select', options: ['DESIGN', 'TRIAL', 'MASS', 'PROD', 'EOL'] },
      { key: 'baselineNo', db: 'baseline_no', label: '基线编号' },
      { key: 'effectiveDate', db: 'effective_date', label: '生效日期', type: 'date' },
      { key: 'changeSummary', db: 'change_summary', label: '版本变更摘要', type: 'textarea' },
      { key: 'targetMarket', db: 'target_market', label: '适用市场' }
    ]
  },
  documents: {
    title: '受控文档', subtitle: '管理图纸、工艺卡、作业指导书和技术规范。', releasable: true,
    fields: [
      { key: 'docNo', db: 'doc_no', label: '文档编号', required: true },
      { key: 'docName', db: 'doc_name', label: '文档名称', required: true },
      { key: 'docType', db: 'doc_type', label: '文档类型', required: true, type: 'select', options: ['DRAWING', 'PROCESS_CARD', 'WORK_INSTRUCTION', 'SPECIFICATION'] },
      { key: 'versionNo', db: 'version_no', label: '版本号' },
      { key: 'productCode', db: 'product_code', label: '产品编码' },
      { key: 'fileName', db: 'file_name', label: '文件名' },
      { key: 'confidentiality', db: 'confidentiality', label: '密级', type: 'select', options: ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL'] },
      { key: 'languageCode', db: 'language_code', label: '文档语言', type: 'select', options: ['zh-CN', 'EN-US'] },
      { key: 'effectiveDate', db: 'effective_date', label: '生效日期', type: 'date' },
      { key: 'description', db: 'description', label: '文档说明', type: 'textarea' }
    ]
  },
  baselines: {
    title: '工程基线', subtitle: '冻结产品、BOM、工艺路线的可追溯版本组合。', releasable: true,
    fields: [
      { key: 'baselineNo', db: 'baseline_no', label: '基线编号', required: true },
      { key: 'baselineName', db: 'baseline_name', label: '基线名称', required: true },
      { key: 'productCode', db: 'product_code', label: '产品编码', required: true },
      { key: 'productVersion', db: 'product_version', label: '产品版本', required: true },
      { key: 'bomCode', db: 'bom_code', label: 'BOM编码' },
      { key: 'bomVersion', db: 'bom_version', label: 'BOM版本' },
      { key: 'routingCode', db: 'routing_code', label: '工艺路线编码' },
      { key: 'routingVersion', db: 'routing_version', label: '工艺路线版本' },
      { key: 'baselinePurpose', db: 'baseline_purpose', label: '基线用途', type: 'textarea' },
      { key: 'remark', db: 'remark', label: '备注', type: 'textarea' }
    ]
  }
}

const cfg = computed(() => configs[props.kind] || configs.families)
const tableFields = computed(() => cfg.value.fields.filter(field => field.key !== 'status'))
const detailFields = computed(() => cfg.value.fields.filter(field => field.key !== 'status'))
const rows = ref<Record<string, any>[]>([])
const loading = ref(false)
const dialog = ref(false)
const detailDialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const selectedRow = ref<Record<string, any> | null>(null)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const form = reactive<Record<string, any>>({})

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/plm/catalog/${props.kind}`, { params: { keyword: keyword.value || undefined, page: page.value, size: size.value } })
    rows.value = data.data.content
    total.value = data.data.totalElements
    if (!rows.value.length && total.value > 0 && page.value > 1) {
      page.value -= 1
      await load()
    }
  } catch (error: any) {
    showErrorDialog(error?.message, '加载目录失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; void load() }

function resetForm() {
  Object.keys(form).forEach(key => delete form[key])
  for (const field of cfg.value.fields) form[field.key] = field.type === 'select' ? (field.options?.[0] || '') : ''
}

function create() {
  editingId.value = null
  resetForm()
  dialog.value = true
}

function edit(row: Record<string, any>) {
  editingId.value = Number(row.id)
  resetForm()
  for (const field of cfg.value.fields) form[field.key] = row[field.db] ?? ''
  dialog.value = true
}

async function view(row: Record<string, any>) {
  try {
    const { data } = await http.get(`/plm/catalog/${props.kind}/${row.id}`)
    selectedRow.value = data.data
    detailDialog.value = true
  } catch (error: any) {
    showErrorDialog(error?.message, '读取目录详情失败')
  }
}

function editable(row: Record<string, any>) {
  return ['DRAFT', 'ACTIVE'].includes(String(row.status))
}

async function save() {
  const missing = cfg.value.fields.find(field => field.required && !String(form[field.key] ?? '').trim())
  if (missing) return showErrorDialog(`请填写${missing.label}`, '表单校验失败')
  saving.value = true
  try {
    if (editingId.value) await http.put(`/plm/catalog/${props.kind}/${editingId.value}`, form)
    else {
      await http.post(`/plm/catalog/${props.kind}`, form)
      page.value = 1
    }
    ElMessage.success('保存成功')
    dialog.value = false
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: Record<string, any>) {
  try {
    await ElMessageBox.confirm(`确认删除“${row[cfg.value.fields[0]?.db] || row.id}”吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await http.delete(`/plm/catalog/${props.kind}/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '删除失败')
  }
}

async function release(row: Record<string, any>) {
  try {
    await http.post(`/plm/catalog/${props.kind}/${row.id}/release`)
    ElMessage.success('版本已受控发布')
    await load()
  } catch (error: any) {
    showErrorDialog(error?.message, '发布失败')
  }
}

watch(() => props.kind, () => { page.value = 1; keyword.value = ''; void load() })
onMounted(() => { void load() })
</script>

<template>
  <section class="catalog-page">
    <div class="page-head">
      <div><span>产品全生命周期受控数据</span><h1>{{ cfg.title }}</h1><p>{{ cfg.subtitle }}</p></div>
      <el-button type="primary" @click="create">新增{{ cfg.title }}</el-button>
    </div>
    <el-card shadow="never">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="输入编码或名称" @keyup.enter="search"/><el-button @click="search">查询</el-button></div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column v-for="field in tableFields" :key="field.db" :prop="field.db" :label="field.label" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ zh(row[field.db]) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="105"><template #default="{ row }"><el-tag :type="row.status === 'RELEASED' || row.status === 'ACTIVE' ? 'success' : 'info'">{{ zh(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row)">详情</el-button>
            <el-button link type="primary" :disabled="!editable(row)" @click="edit(row)">编辑</el-button>
            <el-button v-if="cfg.releasable" link type="success" :disabled="!editable(row)" @click="release(row)">发布</el-button>
            <el-button link type="danger" :disabled="!editable(row)" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId ? '编辑' : '新增'}${cfg.title}`" width="800px" destroy-on-close>
      <el-form label-position="top"><el-row :gutter="16">
        <el-col v-for="field in cfg.fields" :key="field.key" :span="field.type === 'textarea' ? 24 : 12">
          <el-form-item :label="field.label + (field.required ? '' : '（选填）')" :required="field.required">
            <el-select v-if="field.type === 'select'" v-model="form[field.key]" clearable style="width:100%">
              <el-option v-for="option in field.options" :key="option" :label="zh(option)" :value="option"/>
            </el-select>
            <el-date-picker v-else-if="field.type === 'date'" v-model="form[field.key]" value-format="YYYY-MM-DD" clearable style="width:100%"/>
            <el-input v-else-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="3" maxlength="1000" show-word-limit/>
            <el-input v-else v-model="form[field.key]" :disabled="Boolean(editingId) && (field.key.endsWith('Code') || field.key.endsWith('No'))"/>
          </el-form-item>
        </el-col>
      </el-row></el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailDialog" :title="`${cfg.title}详情`" size="620px">
      <el-descriptions v-if="selectedRow" :column="1" border>
        <el-descriptions-item v-for="field in detailFields" :key="field.db" :label="field.label">{{ zh(selectedRow[field.db]) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ zh(selectedRow.status) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ zh(selectedRow.created_at) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ zh(selectedRow.updated_at) }}</el-descriptions-item>
        <el-descriptions-item v-for="key in ['created_by', 'released_by', 'released_at']" :key="key" :label="zhKey(key)">{{ zh(selectedRow[key]) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.catalog-page{max-width:1480px;margin:0 auto}.page-head{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}.toolbar{display:flex;gap:8px;margin-bottom:14px}.toolbar .el-input{width:280px}
</style>
