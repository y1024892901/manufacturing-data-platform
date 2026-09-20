<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
const online=ref(navigator.onLine)
const update=()=>online.value=navigator.onLine
onMounted(()=>{window.addEventListener('online',update);window.addEventListener('offline',update)})
onBeforeUnmount(()=>{window.removeEventListener('online',update);window.removeEventListener('offline',update)})
</script>
<template><el-alert v-if="!online" class="offline" title="网络已断开，当前页面数据可能不是最新状态" type="warning" show-icon :closable="false"/><router-view/></template>
<style scoped>.offline{position:fixed;z-index:9999;top:8px;left:50%;width:460px;transform:translateX(-50%);box-shadow:0 8px 26px rgba(45,57,72,.18)}</style>
