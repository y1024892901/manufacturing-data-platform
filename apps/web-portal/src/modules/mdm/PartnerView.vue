<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '../../api/http'
import TablePager from '../../shared/components/TablePager.vue'
import { zh, zhKey } from '../../shared/display'

const props = defineProps<{ kind: 'customers' | 'suppliers' }>()
const isCustomer = computed(() => props.kind === 'customers')
const title = computed(() => isCustomer.value ? '客户主数据' : '供应商主数据')
const rows = ref<any[]>([])
const contacts = ref<any[]>([])
const loading = ref(false)
const contactsLoading = ref(false)
const contactsVisible = ref(false)
const selected = ref<any>(null)
const detailVisible = ref(false)
const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

function mdmStatus(value: unknown) {
  return String(value || '').toUpperCase() === 'PENDING' ? '待处理' : zh(value)
}

function display(value: unknown, key: string) {
  return key === 'status' ? mdmStatus(value) : zh(value)
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/mdm/${props.kind}`, {
      params: { keyword: keyword.value || undefined, status: status.value || undefined, page: page.value, size: size.value }
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

async function showDetails(row: any) {
  selected.value = row
  detailVisible.value = true
}

async function showContacts(row: any) {
  selected.value = row
  contactsVisible.value = true
  contactsLoading.value = true
  try {
    const { data } = await http.get(`/mdm/${props.kind}/${row.id}/contacts`)
    contacts.value = data.data || []
  } finally {
    contactsLoading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

watch(() => props.kind, () => { page.value = 1; status.value = ''; keyword.value = ''; load() })
onMounted(load)
</script>

<template>
  <section class="partner-page">
    <header class="page-head">
      <div><span>权威主数据 · 只读查询</span><h1>{{ title }}</h1><p>查看伙伴档案、资质、信用与联系人信息。</p></div>
      <el-tag type="info" effect="light" round>只读模式</el-tag>
    </header>
    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-note">共 {{ total }} 条档案</div>
        <el-input v-model="keyword" clearable placeholder="输入编码或名称" @keyup.enter="search" />
        <el-select v-model="status" clearable placeholder="全部状态" @change="search">
          <el-option v-for="item in ['DRAFT','PENDING','PUBLISHED','REJECTED','DISABLED']" :key="item" :label="mdmStatus(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无伙伴档案">
        <el-table-column :prop="isCustomer?'customerCode':'supplierCode'" label="档案编码" min-width="140" />
        <el-table-column :prop="isCustomer?'customerName':'supplierName'" label="名称" min-width="180" />
        <el-table-column :prop="isCustomer?'shortName':'shortName'" label="简称" min-width="130" />
        <el-table-column :prop="isCustomer?'customerLevel':'supplierLevel'" label="等级" width="90"><template #default="{row}">{{ zh(row[isCustomer?'customerLevel':'supplierLevel']) }}</template></el-table-column>
        <el-table-column :prop="isCustomer?'customerType':'supplierType'" :label="isCustomer?'客户类型':'供应商类型'" min-width="120"><template #default="{row}">{{ zh(row[isCustomer?'customerType':'supplierType']) }}</template></el-table-column>
        <el-table-column v-if="isCustomer" prop="region" label="所属地区" min-width="120" />
        <el-table-column v-else prop="qualStatus" label="资质状态" min-width="110"><template #default="{row}">{{ zh(row.qualStatus) }}</template></el-table-column>
        <el-table-column v-if="isCustomer" prop="creditLimit" label="信用额度" min-width="115" />
        <el-table-column v-else prop="leadTimeDays" label="交货提前期（天）" min-width="130" />
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='PUBLISHED'?'success':'info'">{{ mdmStatus(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column label="操作" width="180" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="showDetails(row)">档案详情</el-button>
          <el-button link @click="showContacts(row)">联系人</el-button>
        </template></el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" :disabled="loading" @change="load" />
    </el-card>

    <el-drawer v-model="detailVisible" :title="`${title}详情`" size="620px">
      <el-descriptions v-if="selected" :column="2" border>
        <el-descriptions-item v-for="key in Object.keys(selected)" :key="key" :label="zhKey(key)">{{ display(selected[key], key) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
    <el-drawer v-model="contactsVisible" title="联系人档案" size="650px">
      <div class="contact-title">{{ selected?.[isCustomer?'customerName':'supplierName'] || '' }}</div>
      <el-table :data="contacts" v-loading="contactsLoading" stripe empty-text="暂无联系人">
        <el-table-column prop="contact_name" label="姓名" min-width="115" />
        <el-table-column prop="position_name" label="职务" min-width="110" />
        <el-table-column prop="mobile" label="手机" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="is_primary" label="主要联系人" width="115"><template #default="{row}">{{ zh(row.is_primary) }}</template></el-table-column>
      </el-table>
    </el-drawer>
  </section>
</template>

<style scoped>
.partner-page{max-width:1480px;margin:0 auto}.page-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-head span{color:#3472d2;font-size:11px;font-weight:750;letter-spacing:1px}.page-head h1{margin:7px 0;color:#203b5b;font-size:27px}.page-head p{margin:0;color:#8291a4;font-size:13px}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:15px}.toolbar-note{margin-right:auto;color:#8392a6;font-size:12px}.toolbar .el-input{width:270px}.toolbar .el-select{width:165px}.contact-title{margin-bottom:15px;color:#526982;font-weight:650}
</style>
