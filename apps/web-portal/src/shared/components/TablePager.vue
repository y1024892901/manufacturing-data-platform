<script setup lang="ts">
defineProps<{
  page: number
  size: number
  total: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:page', value: number): void
  (event: 'update:size', value: number): void
  (event: 'change'): void
}>()

function changePage(value: number) {
  emit('update:page', value)
  emit('change')
}

function changeSize(value: number) {
  emit('update:size', value)
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <div class="table-pager">
    <span class="total">共 {{ total }} 条</span>
    <el-pagination
      background
      :disabled="disabled"
      :current-page="page"
      :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      layout="sizes, prev, pager, next, jumper"
      :total="total"
      @current-change="changePage"
      @size-change="changeSize"
    />
  </div>
</template>

<style scoped>
.table-pager{display:flex;align-items:center;justify-content:flex-end;gap:14px;padding-top:16px}.total{color:#7d8da1;font-size:12px}
</style>
