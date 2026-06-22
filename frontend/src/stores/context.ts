import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchSecurityContext } from '@/api/agent'
import { errorMessage } from '@/api/http'
import type { AgentPermission, SecurityContext } from '@/types/api'

export const useContextStore = defineStore('context', () => {
  const tenantId = ref('T001')
  const userId = ref('api-client')
  const roles = ref<string[]>([])
  const permissions = ref<AgentPermission[]>([])
  const authenticated = ref(false)
  const initialized = ref(false)
  const loading = ref(false)
  const authError = ref('')
  const apiKeyRequired = ref(false)
  const authenticationType = ref('none')

  const primaryRole = computed(() => roles.value[0] || 'UNASSIGNED')
  const roleHeader = computed(() => roles.value.join(','))

  async function bootstrap(force = false): Promise<boolean> {
    if (initialized.value && !force) return authenticated.value
    loading.value = true
    authError.value = ''
    try {
      apply(await fetchSecurityContext())
      return authenticated.value
    } catch (error) {
      authenticated.value = false
      permissions.value = []
      authError.value = errorMessage(error)
      initialized.value = true
      return false
    } finally {
      loading.value = false
    }
  }

  function apply(context: SecurityContext) {
    tenantId.value = context.tenantId
    userId.value = context.userId
    roles.value = [...context.roles]
    permissions.value = [...context.permissions]
    authenticated.value = context.authenticated
    apiKeyRequired.value = context.apiKeyRequired
    authenticationType.value = context.authenticationType
    initialized.value = true
  }

  function hasPermission(permission?: AgentPermission): boolean {
    return !permission || permissions.value.includes(permission)
  }

  function reauthenticate() {
    const entryUrl = String(import.meta.env.VITE_AUTH_ENTRY_URL || '').trim()
    if (entryUrl) {
      window.location.assign(entryUrl)
      return
    }
    initialized.value = false
    window.location.reload()
  }

  function logout() {
    const logoutUrl = String(import.meta.env.VITE_LOGOUT_URL || '').trim()
    initialized.value = false
    authenticated.value = false
    permissions.value = []
    if (logoutUrl) window.location.assign(logoutUrl)
    else window.location.assign('/access')
  }

  return {
    tenantId,
    userId,
    roles,
    permissions,
    primaryRole,
    roleHeader,
    authenticated,
    initialized,
    loading,
    authError,
    apiKeyRequired,
    authenticationType,
    bootstrap,
    hasPermission,
    reauthenticate,
    logout,
  }
})
