<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { systemByCode } from '../shared/systemCatalog'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = computed(() => String(route.meta.system || ''))
const system = computed(() => systemByCode(code.value))

onMounted(async () => {
  if (!auth.user) await auth.loadMe()
  if (!auth.canAccess(code.value)) router.replace('/portal')
})

const logout = async () => {
  await auth.logout()
  router.replace('/login')
}
const asideWidth = computed(() => code.value === 'wms' ? '208px' : code.value === 'erp' ? '224px' : '248px')
</script>

<template>
  <el-container v-if="system" :class="['system-shell', `system-${code}`]">
    <el-aside v-if="code !== 'crm'" :width="asideWidth">
      <div class="system-brand"><div class="system-symbol">{{ system.shortName.slice(0, 1) }}</div><div><b>{{ system.shortName }}</b><small>{{ system.name }}</small></div></div>
      <div class="nav-label">{{ code === 'mes' ? 'SHOP FLOOR' : code === 'plm' ? 'PRODUCT EXPLORER' : code === 'wms' ? 'WAREHOUSE' : '业务工作区' }}</div>
      <el-menu router :default-active="$route.path"><el-menu-item v-for="item in system.menus" :key="item.route" :index="item.route">{{ item.label }}</el-menu-item></el-menu>
      <button class="portal-return" @click="router.push('/portal')">← 返回统一门户</button>
    </el-aside>
    <el-container class="workspace-frame">
      <el-header>
        <div class="header-title"><b>{{ system.name }}</b><span>{{ system.description }}</span></div>
        <div class="header-user"><el-tag effect="plain">{{ auth.user?.positionName || '业务用户' }}</el-tag><span>{{ auth.user?.realName }}</span><el-button link type="primary" @click="logout">退出</el-button></div>
        <el-menu v-if="code === 'crm'" class="crm-top-nav" router mode="horizontal" :default-active="$route.path"><el-menu-item v-for="item in system.menus" :key="item.route" :index="item.route">{{ item.label }}</el-menu-item></el-menu>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.system-shell { --workspace-accent: var(--vyre-interactive-primary); --workspace-page: var(--vyre-surface-background); --workspace-panel: var(--vyre-surface-card); --workspace-soft: var(--vyre-surface-subtle); --workspace-ink: var(--vyre-content-primary); --workspace-muted: var(--vyre-content-secondary); --workspace-border: var(--vyre-border-subtle); --workspace-rail: var(--vyre-color-navy-950); --workspace-radius: var(--vyre-radius-md); --workspace-shadow: var(--vyre-shadow-card); --el-color-primary: var(--workspace-accent); --el-text-color-secondary: var(--workspace-muted); --el-text-color-placeholder: var(--workspace-muted); display: flex; height: 100vh; overflow: hidden; background: var(--workspace-page); color: var(--workspace-ink); }
.system-mdm { --workspace-accent: var(--vyre-system-mdm-accent); --workspace-page: var(--vyre-system-mdm-page); --workspace-panel: var(--vyre-system-mdm-panel); --workspace-soft: var(--vyre-system-mdm-soft); --workspace-ink: var(--vyre-system-mdm-ink); --workspace-muted: var(--vyre-system-mdm-muted); --workspace-border: var(--vyre-system-mdm-border); --workspace-rail: var(--vyre-system-mdm-rail); }
.system-crm { --workspace-accent: var(--vyre-system-crm-accent); --workspace-page: var(--vyre-system-crm-page); --workspace-panel: var(--vyre-system-crm-panel); --workspace-soft: var(--vyre-system-crm-soft); --workspace-ink: var(--vyre-system-crm-ink); --workspace-muted: var(--vyre-system-crm-muted); --workspace-border: var(--vyre-system-crm-border); --workspace-rail: var(--vyre-system-crm-rail); --workspace-radius: var(--vyre-radius-xl); }
.system-erp { --workspace-accent: var(--vyre-system-erp-accent); --workspace-page: var(--vyre-system-erp-page); --workspace-panel: var(--vyre-system-erp-panel); --workspace-soft: var(--vyre-system-erp-soft); --workspace-ink: var(--vyre-system-erp-ink); --workspace-muted: var(--vyre-system-erp-muted); --workspace-border: var(--vyre-system-erp-border); --workspace-rail: var(--vyre-system-erp-rail); --workspace-radius: var(--vyre-radius-xs); }
.system-plm { --workspace-accent: var(--vyre-system-plm-accent); --workspace-page: var(--vyre-system-plm-page); --workspace-panel: var(--vyre-system-plm-panel); --workspace-soft: var(--vyre-system-plm-soft); --workspace-ink: var(--vyre-system-plm-ink); --workspace-muted: var(--vyre-system-plm-muted); --workspace-border: var(--vyre-system-plm-border); --workspace-rail: var(--vyre-system-plm-rail); }
.system-srm { --workspace-accent: var(--vyre-system-srm-accent); --workspace-page: var(--vyre-system-srm-page); --workspace-panel: var(--vyre-system-srm-panel); --workspace-soft: var(--vyre-system-srm-soft); --workspace-ink: var(--vyre-system-srm-ink); --workspace-muted: var(--vyre-system-srm-muted); --workspace-border: var(--vyre-system-srm-border); --workspace-rail: var(--vyre-system-srm-rail); }
.system-wms { --workspace-accent: var(--vyre-system-wms-accent); --workspace-page: var(--vyre-system-wms-page); --workspace-panel: var(--vyre-system-wms-panel); --workspace-soft: var(--vyre-system-wms-soft); --workspace-ink: var(--vyre-system-wms-ink); --workspace-muted: var(--vyre-system-wms-muted); --workspace-border: var(--vyre-system-wms-border); --workspace-rail: var(--vyre-system-wms-rail); --workspace-radius: var(--vyre-radius-xs); }
.system-mes { --workspace-accent: var(--vyre-system-mes-accent); --workspace-page: var(--vyre-system-mes-page); --workspace-panel: var(--vyre-system-mes-panel); --workspace-soft: var(--vyre-system-mes-soft); --workspace-ink: var(--vyre-system-mes-ink); --workspace-muted: var(--vyre-system-mes-muted); --workspace-border: var(--vyre-system-mes-border); --workspace-rail: var(--vyre-system-mes-rail); --workspace-radius: var(--vyre-radius-xs); --el-text-color-primary: var(--vyre-system-mes-ink); --el-text-color-regular: var(--vyre-system-mes-muted); --el-bg-color: var(--vyre-system-mes-panel); --el-fill-color-blank: var(--vyre-system-mes-panel); --el-border-color-lighter: var(--vyre-system-mes-border); --el-fill-color-light: var(--vyre-system-mes-soft); }
.system-qms { --workspace-accent: var(--vyre-system-qms-accent); --workspace-page: var(--vyre-system-qms-page); --workspace-panel: var(--vyre-system-qms-panel); --workspace-soft: var(--vyre-system-qms-soft); --workspace-ink: var(--vyre-system-qms-ink); --workspace-muted: var(--vyre-system-qms-muted); --workspace-border: var(--vyre-system-qms-border); --workspace-rail: var(--vyre-system-qms-rail); }
.system-eam { --workspace-accent: var(--vyre-system-eam-accent); --workspace-page: var(--vyre-system-eam-page); --workspace-panel: var(--vyre-system-eam-panel); --workspace-soft: var(--vyre-system-eam-soft); --workspace-ink: var(--vyre-system-eam-ink); --workspace-muted: var(--vyre-system-eam-muted); --workspace-border: var(--vyre-system-eam-border); --workspace-rail: var(--vyre-system-eam-rail); }
.system-energy { --workspace-accent: var(--vyre-system-energy-accent); --workspace-page: var(--vyre-system-energy-page); --workspace-panel: var(--vyre-system-energy-panel); --workspace-soft: var(--vyre-system-energy-soft); --workspace-ink: var(--vyre-system-energy-ink); --workspace-muted: var(--vyre-system-energy-muted); --workspace-border: var(--vyre-system-energy-border); --workspace-rail: var(--vyre-system-energy-rail); --workspace-radius: var(--vyre-radius-lg); --el-text-color-primary: var(--vyre-system-energy-ink); --el-text-color-regular: var(--vyre-system-energy-muted); --el-bg-color: var(--vyre-system-energy-panel); --el-fill-color-blank: var(--vyre-system-energy-panel); --el-border-color-lighter: var(--vyre-system-energy-border); --el-fill-color-light: var(--vyre-system-energy-soft); }
.system-shell > .el-aside { position: relative; display: flex; flex: 0 0 auto; flex-direction: column; height: 100vh; overflow: auto; padding: var(--vyre-space-4) var(--vyre-space-3) var(--vyre-space-3); border-right: 1px solid color-mix(in srgb,var(--vyre-content-inverse) 12%,transparent); background: var(--workspace-rail); color: var(--vyre-content-inverse); scrollbar-gutter: stable; }
.system-brand { display: flex; align-items: center; gap: var(--vyre-space-3); padding: var(--vyre-space-1) var(--vyre-space-2) var(--vyre-space-5); }
.system-symbol { display: grid; place-items: center; width: var(--vyre-space-8); height: var(--vyre-space-8); flex: 0 0 var(--vyre-space-8); border-radius: var(--workspace-radius); background: var(--workspace-accent); color: var(--vyre-content-inverse); font-weight: 800; }
.system-brand b { display: block; color: var(--vyre-content-inverse); font-size: var(--vyre-font-size-md); }
.system-brand small { display: block; margin-top: var(--vyre-space-1); color: color-mix(in srgb,var(--vyre-content-inverse) 64%,transparent); font-size: var(--vyre-font-size-xs); }
.nav-label { margin: var(--vyre-space-2) var(--vyre-space-2); color: color-mix(in srgb,var(--vyre-content-inverse) 54%,transparent); font-size: var(--vyre-font-size-xs); letter-spacing: .1em; }
.system-shell .el-menu { flex: 1 1 auto; min-width: 0; border: 0; background: transparent; }
.system-shell .el-menu :deep(.el-menu-item) { height: var(--vyre-space-10); margin: var(--vyre-space-1) 0; padding: 0 var(--vyre-space-3); border-radius: var(--workspace-radius); color: color-mix(in srgb,var(--vyre-content-inverse) 78%,transparent); font-size: var(--vyre-font-size-sm); }
.system-shell .el-menu :deep(.el-menu-item:hover) { background: color-mix(in srgb,var(--workspace-accent) 20%,transparent); color: var(--vyre-content-inverse); }
.system-shell .el-menu :deep(.el-menu-item.is-active) { background: var(--workspace-accent); color: var(--vyre-content-inverse); font-weight: 700; }
.portal-return { width: 100%; margin-top: auto; padding: var(--vyre-space-3); border: 1px solid color-mix(in srgb,var(--vyre-content-inverse) 22%,transparent); border-radius: var(--workspace-radius); background: color-mix(in srgb,var(--workspace-rail) 78%,var(--vyre-content-inverse)); color: var(--vyre-content-inverse); text-align: left; font-size: var(--vyre-font-size-sm); font-weight: 650; cursor: pointer; }
.workspace-frame { min-width: 0; height: 100vh; overflow: hidden; }
.workspace-frame > .el-header { display: flex; flex: 0 0 var(--vyre-space-16); align-items: center; justify-content: space-between; gap: var(--vyre-space-4); padding: 0 var(--vyre-space-6); border-bottom: 1px solid var(--workspace-border); background: var(--workspace-panel); }
.header-title { min-width: 0; overflow: hidden; }
.header-title b { color: var(--workspace-ink); font-size: var(--vyre-font-size-sm); }
.header-title span { display: block; overflow: hidden; margin-top: var(--vyre-space-1); color: var(--workspace-muted); font-size: var(--vyre-font-size-xs); text-overflow: ellipsis; white-space: nowrap; }
.header-user { display: flex; flex: 0 0 auto; align-items: center; gap: var(--vyre-space-3); color: var(--workspace-muted); font-size: var(--vyre-font-size-sm); }
.header-user :deep(.el-tag) { border-color: var(--workspace-border); background: var(--workspace-soft); color: var(--workspace-ink); }
.workspace-frame > .el-main { height: calc(100vh - var(--vyre-space-16)); overflow: auto; padding: var(--vyre-space-6); background: var(--workspace-page); scrollbar-gutter: stable; }
.system-crm > .workspace-frame > .el-header { display: grid; grid-template-columns: minmax(0,1fr) auto; grid-template-rows: auto auto; height: auto; min-height: var(--vyre-space-16); flex: 0 0 auto; gap: var(--vyre-space-2) var(--vyre-space-4); padding-top: var(--vyre-space-2); background: var(--workspace-rail); }
.system-crm .header-title b, .system-crm .header-title span { color: var(--vyre-content-inverse); }
.system-crm .header-user { color: var(--vyre-content-inverse); }
.system-crm .header-user :deep(.el-tag) { border-color: color-mix(in srgb,var(--vyre-content-inverse) 28%,transparent); background: transparent; color: var(--vyre-content-inverse); }
.crm-top-nav { grid-column: 1 / -1; width: 100%; overflow-x: auto; border: 0; background: transparent; }
.crm-top-nav :deep(.el-menu-item) { height: var(--vyre-space-10); padding: 0 var(--vyre-space-3); border: 0 !important; background: transparent; color: color-mix(in srgb,var(--vyre-content-inverse) 72%,transparent); font-size: var(--vyre-font-size-xs); }
.crm-top-nav :deep(.el-menu-item:hover), .crm-top-nav :deep(.el-menu-item.is-active) { background: color-mix(in srgb,var(--workspace-accent) 28%,transparent); color: var(--vyre-content-inverse); }
.system-plm > .el-aside { border-right-color: var(--workspace-border); background: var(--workspace-soft); color: var(--workspace-ink); }
.system-plm .system-brand b, .system-plm .portal-return { color: var(--workspace-ink); }
.system-plm .system-brand small, .system-plm .nav-label { color: var(--workspace-muted); }
.system-plm .portal-return { border-color: var(--workspace-border); background: var(--workspace-panel); }
.system-plm .el-menu :deep(.el-menu-item) { color: var(--workspace-ink); }
.system-plm .el-menu :deep(.el-menu-item.is-active) { color: var(--vyre-content-inverse); }
.system-erp > .el-aside { border-right: 2px solid var(--workspace-accent); }
.system-erp .el-menu :deep(.el-menu-item) { border-radius: 0; border-bottom: 1px solid color-mix(in srgb,var(--vyre-content-inverse) 14%,transparent); }
.system-wms .system-brand { gap: var(--vyre-space-2); }
.system-wms .system-brand small { max-width: var(--vyre-space-16); }
.system-wms .el-menu :deep(.el-menu-item) { min-height: var(--vyre-space-10); }
.system-mes > .el-aside { border-right: 1px solid var(--workspace-border); }
.system-mes .el-menu :deep(.el-menu-item.is-active) { border-left: 3px solid var(--workspace-accent); border-radius: var(--vyre-radius-xs); }
.system-qms > .el-aside { border-top: 4px solid var(--workspace-accent); }
.system-eam > .el-aside { border-right: 3px solid var(--workspace-accent); }
.system-energy > .el-aside { border-right-color: color-mix(in srgb,var(--workspace-accent) 30%,transparent); }
.system-energy .el-menu :deep(.el-menu-item.is-active) { color: var(--workspace-rail); }
.system-shell :deep(.el-card) { border-color: var(--workspace-border); border-radius: var(--workspace-radius); background: var(--workspace-panel); box-shadow: var(--workspace-shadow); }
.system-shell :deep(.el-card__header) { border-bottom-color: var(--workspace-border); background: color-mix(in srgb,var(--workspace-accent) 5%,var(--workspace-panel)); }
.system-shell :deep(.el-table) { --el-table-header-bg-color: color-mix(in srgb,var(--workspace-accent) 7%,var(--workspace-panel)); --el-table-border-color: var(--workspace-border); --el-table-row-hover-bg-color: color-mix(in srgb,var(--workspace-accent) 6%,var(--workspace-panel)); --el-table-text-color: var(--workspace-ink); --el-table-header-text-color: var(--workspace-muted); --el-table-bg-color: var(--workspace-panel); --el-table-tr-bg-color: var(--workspace-panel); }
.system-shell :deep(.el-button--primary) { --el-button-bg-color: var(--workspace-accent); --el-button-border-color: var(--workspace-accent); --el-button-hover-bg-color: color-mix(in srgb,var(--workspace-accent) 84%,var(--workspace-rail)); --el-button-hover-border-color: color-mix(in srgb,var(--workspace-accent) 84%,var(--workspace-rail)); }
.system-shell :deep(.el-alert__title), .system-shell :deep(.el-alert__description), .system-shell :deep(.el-empty__description), .system-shell :deep(.el-pagination__total), .system-shell :deep(.el-pagination__jump) { color: var(--workspace-muted); }
.system-shell :deep(.el-alert__title a) { color: var(--workspace-ink); text-decoration: underline; text-underline-offset: 2px; }
.system-erp :deep(.el-button--primary), .system-srm :deep(.el-button--primary), .system-qms :deep(.el-button--primary), .system-eam :deep(.el-button--primary), .system-energy :deep(.el-button--primary) { --el-button-text-color: var(--workspace-rail); }
.system-erp .el-menu :deep(.el-menu-item.is-active), .system-srm .el-menu :deep(.el-menu-item.is-active), .system-qms .el-menu :deep(.el-menu-item.is-active), .system-eam .el-menu :deep(.el-menu-item.is-active), .system-energy .el-menu :deep(.el-menu-item.is-active) { color: var(--workspace-rail); }
.system-mes :deep(.el-loading-mask), .system-energy :deep(.el-loading-mask) { background: color-mix(in srgb,var(--workspace-panel) 92%,transparent); }
.system-shell :deep(.el-input__wrapper), .system-shell :deep(.el-select__wrapper) { border-radius: var(--workspace-radius); box-shadow: 0 0 0 1px var(--workspace-border) inset; }
.system-erp :deep(.el-card) { border-top: 2px solid var(--workspace-accent); box-shadow: none; }
.system-plm :deep(.el-card) { border-left: 3px solid var(--workspace-accent); }
.system-mes :deep(.el-card) { border-top: 3px solid var(--workspace-accent); }
.system-qms :deep(.el-card__header) { border-left: 3px solid var(--workspace-accent); }
.system-eam :deep(.el-card__header) { border-bottom: 2px solid var(--workspace-accent); }
@media (max-width: 900px) { .workspace-frame > .el-header { padding: 0 var(--vyre-space-4); } .workspace-frame > .el-main { padding: var(--vyre-space-4); } }
@media (max-width: 680px) {
  .system-shell { height: auto; min-height: 100vh; flex-direction: column; overflow: visible; }
  .system-shell > .el-aside { width: 100% !important; height: auto; overflow: visible; padding: var(--vyre-space-3); }
  .system-brand { padding: 0 var(--vyre-space-2) var(--vyre-space-2); }
  .nav-label { margin: var(--vyre-space-1) var(--vyre-space-2); }
  .system-shell .el-menu { display: flex; max-width: 100%; overflow-x: auto; overflow-y: hidden; scrollbar-width: thin; scrollbar-color: color-mix(in srgb,var(--workspace-accent) 58%,transparent) transparent; }
  .system-shell .el-menu::-webkit-scrollbar { height: var(--vyre-space-1); }
  .system-shell .el-menu::-webkit-scrollbar-track { background: transparent; }
  .system-shell .el-menu::-webkit-scrollbar-thumb { border-radius: var(--vyre-radius-full); background: color-mix(in srgb,var(--workspace-accent) 58%,transparent); }
  .system-shell .el-menu :deep(.el-menu-item) { flex: 0 0 auto; margin-right: var(--vyre-space-1); }
  .portal-return { margin-top: var(--vyre-space-2); }
  .workspace-frame { width: 100%; height: auto; overflow: visible; }
  .workspace-frame > .el-header { min-height: var(--vyre-space-16); flex-wrap: wrap; padding: var(--vyre-space-3); }
  .header-title span { display: none; }
  .header-user { gap: var(--vyre-space-2); font-size: var(--vyre-font-size-xs); }
  .workspace-frame > .el-main { height: auto; min-height: calc(100vh - var(--vyre-space-16)); overflow: visible; padding: var(--vyre-space-4); }
  .system-crm > .workspace-frame > .el-header { grid-template-columns: 1fr; }
  .crm-top-nav { grid-column: 1; }
}
</style>
