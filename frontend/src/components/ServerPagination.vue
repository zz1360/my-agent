<script setup lang="ts">
defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ change: [page: number, size: number] }>()
</script>

<template>
  <div v-if="total > 0" class="server-pagination">
    <el-pagination
      background
      :current-page="page"
      :page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @update:current-page="emit('change', $event, size)"
      @update:page-size="emit('change', 1, $event)"
    />
  </div>
</template>

<style scoped>
.server-pagination {
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: flex-end;
  overflow-x: auto;
  border-top: 1px solid var(--line);
}
</style>
