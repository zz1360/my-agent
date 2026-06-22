import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'logistics-agent-context'

interface StoredContext {
  tenantId?: string
  userId?: string
  roles?: string[]
}

function loadContext(): StoredContext {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') as StoredContext
  } catch {
    return {}
  }
}

export const useContextStore = defineStore('context', () => {
  const stored = loadContext()
  const tenantId = ref(stored.tenantId || 'T001')
  const userId = ref(stored.userId || 'u-cs-001')
  const roles = ref<string[]>(stored.roles?.length ? stored.roles : ['CUSTOMER_SERVICE'])

  const primaryRole = computed(() => roles.value[0] || 'CUSTOMER_SERVICE')
  const roleHeader = computed(() => roles.value.join(','))

  function update(next: StoredContext) {
    tenantId.value = next.tenantId?.trim() || 'T001'
    userId.value = next.userId?.trim() || 'u-cs-001'
    roles.value = next.roles?.length ? [...next.roles] : ['CUSTOMER_SERVICE']
  }

  watch(
    [tenantId, userId, roles],
    () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ tenantId: tenantId.value, userId: userId.value, roles: roles.value }),
      )
    },
    { deep: true },
  )

  return { tenantId, userId, roles, primaryRole, roleHeader, update }
})
