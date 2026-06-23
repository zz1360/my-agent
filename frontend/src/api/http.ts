import axios, { AxiosError } from 'axios'
import type { ApiErrorResponse } from '@/types/api'
import { reportFrontendEvent } from '@/telemetry'

const requestStartedAt = new Map<string, number>()

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const traceId = `web-${crypto.randomUUID()}`
  config.headers.set('X-Trace-Id', traceId)
  requestStartedAt.set(traceId, performance.now())
  return config
})

http.interceptors.response.use(
  (response) => {
    reportRequestTiming(response.config.headers.get('X-Trace-Id') as string, response.config.url)
    return response
  },
  (error: AxiosError) => {
    const requestUrl = String(error.config?.url || '')
    const requestTraceId = error.config?.headers.get('X-Trace-Id') as string | undefined
    const traceId = String(error.response?.headers['x-trace-id'] || requestTraceId || '')
    if (!requestUrl.includes('/frontend-events')) {
      reportFrontendEvent({
        type: 'API_FAILURE',
        route: requestUrl,
        message: error.message,
        status: error.response?.status,
        durationMs: requestDuration(requestTraceId),
        traceId,
      })
    }
    ;(error as AxiosError & { traceId?: string }).traceId = traceId
    if (error.response?.status === 401 && !requestUrl.includes('/security/context')) {
      window.dispatchEvent(new CustomEvent('agent-auth-invalid'))
    }
    return Promise.reject(error)
  },
)

function reportRequestTiming(traceId?: string, route?: string) {
  if (!route?.includes('/frontend-events')) {
    reportFrontendEvent({
      type: 'API_TIMING',
      route,
      durationMs: requestDuration(traceId),
      traceId,
    })
  }
}

function requestDuration(traceId?: string): number | undefined {
  if (!traceId) return undefined
  const startedAt = requestStartedAt.get(traceId)
  requestStartedAt.delete(traceId)
  return startedAt == null ? undefined : Math.round(performance.now() - startedAt)
}

export function apiUrl(path: string): string {
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return `${baseUrl}${path}`
}

export function contextHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
  }
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = (error as AxiosError<ApiErrorResponse>).response?.data
    const message = response?.message || response?.detail || error.message
    const traceId = response?.traceId || (error as AxiosError & { traceId?: string }).traceId
    return traceId ? `${message}（traceId: ${traceId}）` : message
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}
