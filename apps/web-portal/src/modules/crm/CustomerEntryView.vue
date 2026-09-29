<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import { zh } from '../../shared/display'

const rows = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)
const dialog = ref(false)
const detailDialog = ref(false)
const selected = ref<any>(null)
const editingId = ref<number | null>(null)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const form = reactive<any>({})

const fields = [
  { key: 'customerCode', label: '客户编码', placeholder: '留空后由系统自动生成' },
  { key: 'customerName', label: '客户名称', required: true },
  { key: 'shortName', label: '客户简称' },
  { key: 'unifiedSocialCode', label: '统一社会信用代码' },
  { key: 'customerLevel', label: '客户等级', type: 'select', options: [{ label: 'A级', value: 'A' }, { label: 'B级', value: 'B' }, { label: 'C级', value: 'C' }, { label: 'D级', value: 'D' }] },
  { key: 'customerType', label: '客户类型', type: 'select', options: [{ label: '直客', value: 'DIRECT' }, { label: '经销商', value: 'DEALER' }, { label: '代理商', value: 'AGENT' }] },
  { key: 'industry', label: '所属行业' },
  { key: 'region', label: '所属地区' },
  { key: 'creditLimit', label: '信用额度', type: 'number' },
  { key: 'paymentTerms', label: '付款条件', type: 'select', options: [{ label: '预付款', value: 'PREPAY' }, { label: '月结30天', value: 'NET30' }, { label: '月结60天', value: 'NET60' }] },
  { key: 'taxNo', label: '纳税人识别号' },
  { key: 'contactPerson', label: '联系人' },
  { key: 'contactPhone', label: '联系电话' },
  { key: 'contactEmail', label: '联系邮箱' },
  { key: 'address', label: '联系地址', type: 'textarea', span: 2 }
]

function resetForm() {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, { customerLevel: 'C', customerType: 'DIRECT', creditLimit: 0 })
}

async function load() {
  loading.value = true
  try {
    const response = await http.get('/crm/customers', { params: { keyword: keyword.value || undefined, page: page.value, size: size.value } })
    const data = response.data.data
    rows.value = data?.content || []
    total.value = Number(data?.totalElements || 0)
  } catch { /* API 错误由全局弹窗提示 */ }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialog.value = true
}

function openEdit(row: any) {
  editingId.value = Number(row.id)
  resetForm()
  Object.assign(form, Object.fromEntries(fields.map(field => [field.key, row[field.key] ?? null])))
  dialog.value = true
}

async function submit() {
  if (!String(form.customerName || '').trim()) {
    ElMessage.warning('请填写客户名称')
    return
  }
  submitting.value = true
  try {
    const response = editingId.value
      ? await http.put(`/crm/customers/${editingId.value}`, form)
      : await http.post('/crm/customers', form)
    const result = response.data.data
    ElMessage.success(`${editingId.value ? '客户信息已更新' : '客户已写入主数据'}，分发任务已排队（${result.distributionEventId}）`)
    dialog.value = false
    editingId.value = null
    page.value = 1
    await load()
  } catch { /* API 错误由全局弹窗提示 */ }
  finally { submitting.value = false }
}

function viewDetail(row: any) {
  selected.value = row
  detailDialog.value = true
}

function valueOf(row: any, key: string) {
  const value = row?.[key]
  if (key === 'customerLevel') return value ? `${value}级` : '—'
  if (key === 'customerType') return ({ DIRECT: '直客', DEALER: '经销商', AGENT: '代理商' } as Record<string, string>)[value] || zh(value)
  if (key === 'paymentTerms') return ({ PREPAY: '预付款', NET30: '月结30天', NET60: '月结60天' } as Record<string, string>)[value] || zh(value)
  return value ?? '—'
}

onMounted(load)
</script>

<template>
  <section class="customer-entry">
    <div class="page-heading">
      <div>
        <span class="eyebrow">客户主数据 · 统一档案</span>
        <h1>客户信息录入</h1>
        <p>在 CRM 登记客户后，信息直接写入 MDM 客户主表，并自动进入分发队列。</p>
      </div>
      <el-button type="primary" size="large" @click="openCreate">＋ 新增客户</el-button>
    </div>

    <div class="info-strip">
      <span class="status-dot"></span>
        <span>主数据单一来源：CRM 与 MDM 读取同一客户档案</span>
        <span class="divider">·</span>
        <span>新增或修改后自动更新版本并排入分发队列</span>
        <span class="divider">·</span>
        <b>修改通过客户主键回写 MDM</b>
    </div>

    <el-card shadow="never" class="list-card">
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="按客户编码或客户名称搜索" @keyup.enter="page = 1; load()" />
        <el-button type="primary" plain @click="page = 1; load()">查询</el-button>
        <el-button @click="keyword = ''; page = 1; load()">重置</el-button>
        <span class="record-count">共 {{ total }} 条已发布档案</span>
      </div>
      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无客户档案">
        <el-table-column prop="customerCode" label="客户编码" min-width="160" />
        <el-table-column prop="customerName" label="客户名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="shortName" label="客户简称" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="110"><template #default="{ row }">{{ valueOf(row, 'customerType') }}</template></el-table-column>
        <el-table-column label="等级" width="90"><template #default="{ row }">{{ valueOf(row, 'customerLevel') }}</template></el-table-column>
        <el-table-column prop="region" label="地区" min-width="120" />
        <el-table-column label="主数据状态" width="120"><template #default="{ row }"><el-tag type="success" effect="light">{{ zh(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link @click="viewDetail(row)">查看</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="page = 1; load()" /></div>
    </el-card>

    <el-dialog v-model="dialog" :title="editingId ? '编辑客户档案' : '新增客户档案'" width="min(820px, 94vw)" destroy-on-close>
      <div class="dialog-intro">{{ editingId ? '保存后按当前客户主键更新 MDM 主档，版本递增并生成可追踪的分发任务。' : '录入后立即发布到统一主数据，并生成可追踪的分发任务。' }}</div>
      <el-form :model="form" label-position="top" class="entry-form">
        <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :required="Boolean(field.required)" :class="{ wide: field.span === 2 }">
          <el-select v-if="field.type === 'select'" v-model="form[field.key]" :placeholder="`请选择${field.label}`" clearable>
            <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-input-number v-else-if="field.type === 'number'" v-model="form[field.key]" :min="0" :precision="2" :step="1000" controls-position="right" />
          <el-input v-else-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="2" :placeholder="`请输入${field.label}`" />
          <el-input v-else v-model="form[field.key]" :disabled="field.key === 'customerCode' && Boolean(editingId)" :placeholder="field.key === 'customerCode' ? '留空由系统自动生成' : `请输入${field.label}`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存修改并下发' : '保存并同步主数据' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="客户档案详情" width="min(760px, 94vw)">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">{{ valueOf(selected, field.key) }}</el-descriptions-item>
        <el-descriptions-item label="主数据状态">{{ zh(selected.status) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ selected.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="录入人">{{ selected.createdBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ selected.createdAt || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>

<style scoped>
.customer-entry{max-width:1500px;margin:0 auto}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:2px 0 20px}.eyebrow{font-size:11px;font-weight:700;letter-spacing:1px;color:#7455c4}.page-heading h1{margin:7px 0 5px;color:#253a56;font-size:27px;letter-spacing:-.5px}.page-heading p{margin:0;color:#7d8ca1;font-size:13px}.page-heading :deep(.el-button){height:44px;padding:0 20px;border:0;border-radius:11px;background:linear-gradient(135deg,#7658d1,#9a73e8);box-shadow:0 8px 18px rgba(119,87,203,.22)}.info-strip{display:flex;align-items:center;flex-wrap:wrap;gap:9px;margin-bottom:18px;padding:13px 16px;border:1px solid #e8e1f6;border-radius:12px;background:linear-gradient(100deg,rgba(255,255,255,.88),rgba(248,244,255,.85));color:#687890;font-size:12px}.info-strip b{color:#6246a9;font-weight:650}.status-dot{width:8px;height:8px;border-radius:50%;background:#34b27b;box-shadow:0 0 0 4px rgba(52,178,123,.12)}.divider{color:#c1b5d8}.list-card{border-radius:16px}.toolbar{display:flex;align-items:center;gap:9px;margin-bottom:16px}.toolbar .el-input{width:min(360px,50vw)}.record-count{margin-left:auto;color:#8794a7;font-size:12px}.pagination{display:flex;justify-content:flex-end;padding-top:16px}.dialog-intro{margin:-2px 0 18px;padding:12px 14px;border-radius:10px;background:#f6f3fc;color:#685393;font-size:12px}.entry-form{display:grid;grid-template-columns:1fr 1fr;column-gap:20px}.entry-form .wide{grid-column:1 / -1}.entry-form :deep(.el-select),.entry-form :deep(.el-input-number){width:100%}@media(max-width:720px){.page-heading{align-items:flex-start;flex-direction:column}.toolbar{flex-wrap:wrap}.record-count{width:100%;margin-left:0}.entry-form{grid-template-columns:1fr}.entry-form .wide{grid-column:auto}}
</style>
