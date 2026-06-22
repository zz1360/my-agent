import { useContextStore } from '@/stores/context'

export function contextParams(extra: Record<string, string> = {}): URLSearchParams {
  const context = useContextStore()
  const params = new URLSearchParams({
    tenantId: context.tenantId,
    userId: context.userId,
    ...extra,
  })
  context.roles.forEach((role) => params.append('roles', role))
  return params
}

export function contextPayload(extra: Record<string, unknown> = {}): Record<string, unknown> {
  const context = useContextStore()
  return {
    tenantId: context.tenantId,
    userId: context.userId,
    roles: [...context.roles],
    ...extra,
  }
}
