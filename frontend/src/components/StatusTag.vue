<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value?: string }>()

const tagType = computed(() => {
  const value = String(props.value || '').toUpperCase()
  if (
    [
      'PASSED',
      'COMPLETED',
      'SUCCESS',
      'SUCCEEDED',
      'APPROVED',
      'PUBLISHED',
      'ACTIVE',
      'APPLIED',
      'RESOLVED',
    ].includes(value)
  )
    return 'success'
  if (['BLOCKED', 'FAILED', 'REJECTED', 'DISABLED', 'EXPIRED', 'ERROR'].includes(value))
    return 'danger'
  if (['OPEN', 'PROCESSING', 'PENDING_REVIEW', 'RUNNING'].includes(value)) return 'warning'
  return 'info'
})
</script>

<template>
  <el-tag :type="tagType" effect="plain">{{ value || '-' }}</el-tag>
</template>
