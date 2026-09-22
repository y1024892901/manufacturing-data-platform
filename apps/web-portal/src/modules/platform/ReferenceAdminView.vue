<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'

const route = useRoute()
const rows = ref<any[]>([])
const permissions = ref<any[]>([])
const departmentOptions = ref<any[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<any>({})
const page = ref(1)
const size = ref(20)
const total = ref(0)
const kind = computed(() => String(route.meta.kind || route.path.split('/').pop()) === 'roles' ? 'roles' : 'organization')
const isRole = computed(() => kind.value === 'roles')
const title = computed(() => isRole.value ? '角色与权限' : '组织与岗位')

async function load() {
  loading.value = true
  try {
    if (isRole.value) {
      const [r, p] = await Promise.all([http.get('/admin/roles', { params: { page: page.value, size: size.value } }), http.get('/admin/permissions')])
      rows.value = r.data.data.content
      total.value = r.data.data.totalElements
      permissions.value = p.data.data
    } else {
      const [result, options] = await Promise.all([http.get('/admin/departments', { params: { page: page.value, size: size.value } }), http.get('/admin/department-options')])
      rows.value = result.data.data.content
      total.value = result.data.data.totalElements
      departmentOptions.value = options.data.data
    }
    if (!rows.value.length && total.value > 0 && page.value > 1) {
      page.value--
      await load()
    }
  } finally { loading.value = false }
}

function create() {
  editingId.value = null
  if (isRole.value) Object.assign(form, { roleCode: '', roleName: '', description: '', sortNo: 0, permissionIds: [] })
  else Object.assign(form, { deptCode: '', deptName: '', parentCode: '', deptLevel: 2, managerUser: '', sortNo: 0, enabled: true })
  dialog.value = true
}

function edit(row: any) {
  editingId.value = row.id
  if (isRole.value) Object.assign(form, {
    roleCode: row.roleCode, roleName: row.roleName, description: row.description || '',
    sortNo: row.sortNo, permissionIds: (row.permissions || []).map((p: any) => p.id)
  })
  else Object.assign(form, {
    deptCode: row.deptCode, deptName: row.deptName, parentCode: row.parentCode || '',
    deptLevel: row.deptLevel, managerUser: row.managerUser || '', sortNo: row.sortNo, enabled: row.enabled
  })
  dialog.value = true
}

async function save() {
  const creating = editingId.value === null
  saving.value = true
  try {
    const base = isRole.value ? '/admin/roles' : '/admin/departments'
    if (editingId.value) await http.put(`${base}/${editingId.value}`, form)
    else await http.post(base, form)
    ElMessage.success(editingId.value ? '修改已保存' : '记录已创建')
    dialog.value = false
    if (creating) page.value = 1
    await load()
  } catch (error: any) { ElMessage.error(error.message || '保存失败') }
  finally { saving.value = false }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认${isRole.value ? '删除角色' : '停用部门'}“${isRole.value ? row.roleName : row.deptName}”吗？`, '操作确认', { type: 'warning' })
  try {
    await http.delete(`/admin/${isRole.value ? 'roles' : 'departments'}/${row.id}`)
    ElMessage.success(isRole.value ? '角色已删除' : '部门已停用')
    await load()
  } catch (error: any) { ElMessage.error(error.message || '操作失败，请检查是否仍被用户引用') }
}

function permissionSummary(row: any) {
  const list = row.permissions || []
  return list.length ? `${list.length} 个权限点` : '未分配'
}

watch(() => route.path, () => { page.value = 1; load() })
onMounted(load)
</script>

<template>
  <section class="admin-page">
    <div class="page-head">
      <div><span>PLATFORM ADMINISTRATION</span><h1>{{ title }}</h1><p>{{ isRole ? '定义岗位职责、系统菜单、按钮/API 权限和数据范围。' : '维护集团、公司、工厂、车间、部门与负责人层级。' }}</p></div>
      <el-button type="primary" @click="create">{{ isRole ? '新增角色' : '新增组织' }}</el-button>
    </div>
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <template v-if="isRole">
          <el-table-column prop="roleCode" label="角色编码" min-width="140"/>
          <el-table-column prop="roleName" label="角色名称" min-width="130"/>
          <el-table-column prop="description" label="职责说明" min-width="250"/>
          <el-table-column label="权限" width="120"><template #default="{row}"><el-tag effect="plain">{{permissionSummary(row)}}</el-tag></template></el-table-column>
          <el-table-column label="类型" width="100"><template #default="{row}">{{row.system ? '系统内置' : '自定义'}}</template></el-table-column>
        </template>
        <template v-else>
          <el-table-column prop="deptCode" label="组织编码" min-width="120"/>
          <el-table-column prop="deptName" label="组织名称" min-width="180"/>
          <el-table-column prop="parentCode" label="上级组织" min-width="120"/>
          <el-table-column prop="managerUser" label="负责人账号" min-width="130"/>
          <el-table-column prop="deptLevel" label="层级" width="80"/>
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template></el-table-column>
        </template>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" :disabled="isRole&&row.system" @click="remove(row)">{{isRole?'删除':'停用'}}</el-button></template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load"/>
    </el-card>

    <el-dialog v-model="dialog" :title="`${editingId?'编辑':'新增'}${isRole?'角色':'组织'}`" width="720px" destroy-on-close>
      <el-form label-position="top">
        <template v-if="isRole">
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="角色编码" required><el-input v-model="form.roleCode" :disabled="Boolean(editingId)"/></el-form-item></el-col><el-col :span="12"><el-form-item label="角色名称" required><el-input v-model="form.roleName"/></el-form-item></el-col></el-row>
          <el-form-item label="职责说明"><el-input v-model="form.description"/></el-form-item>
          <el-form-item label="权限点"><el-select v-model="form.permissionIds" multiple filterable collapse-tags collapse-tags-tooltip style="width:100%"><el-option v-for="p in permissions" :key="p.id" :label="`${p.systemCode.toUpperCase()} · ${p.permName}（${p.permCode}）`" :value="p.id"/></el-select></el-form-item>
        </template>
        <template v-else>
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="组织编码" required><el-input v-model="form.deptCode" :disabled="Boolean(editingId)"/></el-form-item></el-col><el-col :span="12"><el-form-item label="组织名称" required><el-input v-model="form.deptName"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="上级组织"><el-select v-model="form.parentCode" clearable filterable style="width:100%"><el-option v-for="d in departmentOptions.filter(x=>x.id!==editingId)" :key="d.id" :label="d.deptName" :value="d.deptCode"/></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="负责人账号"><el-input v-model="form.managerUser"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="组织层级"><el-input-number v-model="form.deptLevel" :min="1" :max="9"/></el-form-item></el-col><el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0"/></el-form-item></el-col></el-row>
        </template>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:17px}.page-head span{color:#3775d6;font-size:10px;font-weight:800;letter-spacing:1.4px}.page-head h1{margin:6px 0;color:#243d5d;font-size:25px}.page-head p{color:#8291a4;font-size:12px}
</style>
