<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { showErrorDialog } from '../../shared/errorDialog'
import { zh } from '../../shared/display'

type LocationRow = Record<string, any>
const rows = ref<LocationRow[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const availability = ref('')
const loading = ref(false)
const dialog = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<Record<string, any>>({})
const visibleRows = computed(() => rows.value.filter(row => {
  const matchesText = !keyword.value || ['locationCode', 'locationName', 'warehouseCode', 'warehouseName', 'zoneCode', 'aisleCode', 'rackCode', 'binCode']
    .some(key => String(row[key] ?? '').toLowerCase().includes(keyword.value.toLowerCase()))
  const matchesAvailability = !availability.value || String(row.available) === availability.value
  return matchesText && matchesAvailability
}))
const locationTypes = [
  { label: '普通货位', value: 'GENERAL' }, { label: '收货区', value: 'RECEIVING' },
  { label: '拣选位', value: 'PICKING' }, { label: '存储区', value: 'BULK' },
  { label: '发货区', value: 'SHIPPING' }, { label: '退货区', value: 'RETURN' }
]

function message(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function reset() {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, {
    locationCode: '', locationName: '', warehouseCode: '', warehouseName: '', locationType: 'GENERAL',
    zoneCode: '', aisleCode: '', rackCode: '', binCode: '', maxWeightKg: null, note: ''
  })
  editingId.value = null
}

async function load() {
  loading.value = true
  try {
    const response = await http.get('/wms/locations', { params: { page: page.value, size: size.value } })
    rows.value = response.data.data.content || []
    total.value = response.data.data.totalElements || 0
    if (!rows.value.length && page.value > 1) { page.value -= 1; await load() }
  } catch (error) {
    showErrorDialog(message(error, '库位数据加载失败'))
  } finally {
    loading.value = false
  }
}

async function openEdit(row: LocationRow) {
  try {
    const response = await http.get(`/wms/locations/${row.id}`)
    reset()
    Object.assign(form, response.data.data || {})
    editingId.value = Number(row.id)
    dialog.value = true
  } catch (error) {
    showErrorDialog(message(error, '库位详情加载失败'))
  }
}

async function save() {
  if (!String(form.locationCode || '').trim() || !String(form.locationName || '').trim() || !String(form.warehouseCode || '').trim()) {
    ElMessage.warning('请填写库位编码、库位名称和仓库编码')
    return
  }
  const payload = { ...form }
  for (const key of ['warehouseName', 'zoneCode', 'aisleCode', 'rackCode', 'binCode', 'note']) if (!payload[key]) payload[key] = null
  if (payload.maxWeightKg === '' || payload.maxWeightKg === undefined) payload.maxWeightKg = null
  try {
    if (editingId.value) {
      await http.put(`/wms/locations/${editingId.value}`, payload)
      ElMessage.success('库位修改成功')
    } else {
      await http.post('/wms/locations', payload)
      ElMessage.success('库位新增成功')
    }
    dialog.value = false
    await load()
  } catch (error) {
    showErrorDialog(message(error, '库位保存失败，请检查填写内容'))
  }
}

function isCancelled(error: unknown) {
  return error === 'cancel' || error === 'close' || (error instanceof Error && error.message === 'cancel')
}

async function remove(row: LocationRow) {
  try {
    await ElMessageBox.confirm(`确认删除库位 ${row.locationCode}？仅未承载库存且未被单据引用的库位可以删除。`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await http.delete(`/wms/locations/${row.id}`)
    ElMessage.success('库位已删除')
    await load()
  } catch (error) {
    if (!isCancelled(error)) showErrorDialog(message(error, '库位删除失败'))
  }
}

async function toggleAvailability(row: LocationRow) {
  const enabled = !row.available
  try {
    await ElMessageBox.confirm(`确认${enabled ? '启用' : '停用'}库位 ${row.locationCode}？`, `${enabled ? '启用' : '停用'}确认`, {
      type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消'
    })
    await http.patch(`/wms/locations/${row.id}/availability`, null, { params: { enabled } })
    ElMessage.success(`库位已${enabled ? '启用' : '停用'}`)
    await load()
  } catch (error) {
    if (!isCancelled(error)) showErrorDialog(message(error, '库位状态更新失败'))
  }
}

onMounted(() => { reset(); load() })
</script>

<template>
  <section>
    <div class="head">
      <div><span>仓储管理 · 基础设置</span><h1>仓库与库位</h1><p>维护仓库库位编码、库区位置和额定承重，已承载库存或被单据引用的库位不能删除。</p></div>
      <el-button type="primary" @click="reset(); dialog = true">新增库位</el-button>
    </div>
    <el-card shadow="never">
      <div class="bar">
        <el-input v-model="keyword" clearable placeholder="搜索库位、仓库或库区编码" />
        <el-select v-model="availability" clearable placeholder="全部状态">
          <el-option label="启用" value="true" /><el-option label="停用" value="false" />
        </el-select>
        <el-button @click="keyword = ''; availability = ''; page = 1; load()">重置</el-button>
      </div>
      <el-table :data="visibleRows" v-loading="loading" stripe empty-text="暂无库位数据">
        <el-table-column prop="locationCode" label="库位编码" min-width="130" />
        <el-table-column prop="locationName" label="库位名称" min-width="130" />
        <el-table-column prop="warehouseCode" label="仓库编码" min-width="120" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="120"><template #default="{ row }">{{ zh(row.warehouseName) }}</template></el-table-column>
        <el-table-column prop="locationType" label="库位类型" min-width="110"><template #default="{ row }">{{ zh(row.locationType) }}</template></el-table-column>
        <el-table-column label="库区 / 巷道 / 货架 / 货格" min-width="200"><template #default="{ row }">{{ [row.zoneCode, row.aisleCode, row.rackCode, row.binCode].filter(Boolean).join(' / ') || '—' }}</template></el-table-column>
        <el-table-column prop="maxWeightKg" label="额定承重（千克）" min-width="145"><template #default="{ row }">{{ zh(row.maxWeightKg) }}</template></el-table-column>
        <el-table-column prop="available" label="状态" min-width="90"><template #default="{ row }">{{ row.available ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="toggleAvailability(row)">{{ row.available ? '停用' : '启用' }}</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" @change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId ? '修改' : '新增'}库位`" width="760">
      <el-form label-width="145px" class="form">
        <el-form-item label="库位编码" required><el-input v-model="form.locationCode" placeholder="请输入库位编码" /></el-form-item>
        <el-form-item label="库位名称" required><el-input v-model="form.locationName" placeholder="请输入库位名称" /></el-form-item>
        <el-form-item label="仓库编码" required><el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" /></el-form-item>
        <el-form-item label="仓库名称（选填）"><el-input v-model="form.warehouseName" placeholder="请输入仓库名称" /></el-form-item>
        <el-form-item label="库位类型（选填）"><el-select v-model="form.locationType" style="width:100%"><el-option v-for="option in locationTypes" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="库区编码（选填）"><el-input v-model="form.zoneCode" placeholder="例如 Z01" /></el-form-item>
        <el-form-item label="巷道编码（选填）"><el-input v-model="form.aisleCode" placeholder="例如 A03" /></el-form-item>
        <el-form-item label="货架编码（选填）"><el-input v-model="form.rackCode" placeholder="例如 R08" /></el-form-item>
        <el-form-item label="货格编码（选填）"><el-input v-model="form.binCode" placeholder="例如 B02" /></el-form-item>
        <el-form-item label="额定承重（千克，选填）"><el-input-number v-model="form.maxWeightKg" :min="0.001" :precision="3" style="width:100%" /></el-form-item>
        <el-form-item label="备注（选填）" class="wide"><el-input v-model="form.note" type="textarea" :rows="3" placeholder="填写库位使用说明" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.head{display:flex;justify-content:space-between;align-items:end;margin-bottom:16px}.head span{color:#3976d5;font-size:12px}.head h1{margin:5px 0;color:#263f5c}.head p{color:#8391a4}.bar{display:flex;gap:8px;margin-bottom:14px}.bar .el-input{width:340px}.bar .el-select{width:160px}.form{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.wide{grid-column:1/-1}
</style>
