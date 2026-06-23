import type { App } from 'vue'
import type { Router } from 'vue-router'

export type FrontendEventType =
  | 'WINDOW_ERROR'
  | 'UNHANDLED_REJECTION'
  | 'VUE_ERROR'
  | 'API_FAILURE'
  | 'API_TIMING'
  | 'ROUTE_TIMING'
  | 'WEB_VITAL_LCP'
  | 'WEB_VITAL_CLS'

export interface FrontendEvent {
  type: FrontendEventType
  route?: string
  message?: string
  status?: number
  durationMs?: number
  traceId?: string
}

export function reportFrontendEvent(event: FrontendEvent) {
  const payload = JSON.stringify({
    ...event,
    route: event.route || window.location.pathname,
    timestamp: new Date().toISOString(),
  })
  const blob = new Blob([payload], { type: 'application/json' })
  navigator.sendBeacon?.('/api/ops/frontend-events', blob)
}

export function installFrontendTelemetry(app: App, router: Router) {
  window.addEventListener('error', (event) =>
    reportFrontendEvent({ type: 'WINDOW_ERROR', message: event.message }),
  )
  window.addEventListener('unhandledrejection', (event) =>
    reportFrontendEvent({ type: 'UNHANDLED_REJECTION', message: String(event.reason || '') }),
  )
  app.config.errorHandler = (error) =>
    reportFrontendEvent({
      type: 'VUE_ERROR',
      message: error instanceof Error ? error.message : String(error),
    })

  let navigationStarted = performance.now()
  router.beforeEach(() => {
    navigationStarted = performance.now()
  })
  router.afterEach((to) =>
    reportFrontendEvent({
      type: 'ROUTE_TIMING',
      route: to.path,
      durationMs: Math.round(performance.now() - navigationStarted),
    }),
  )

  observeWebVital('largest-contentful-paint', 'WEB_VITAL_LCP', (entries) =>
    Math.round(entries.at(-1)?.startTime || 0),
  )
  observeWebVital('layout-shift', 'WEB_VITAL_CLS', (entries) =>
    Math.round(
      entries.reduce(
        (sum, entry) => sum + Number((entry as PerformanceEntry & { value?: number }).value || 0),
        0,
      ) * 1000,
    ),
  )
}

function observeWebVital(
  entryType: string,
  type: 'WEB_VITAL_LCP' | 'WEB_VITAL_CLS',
  value: (entries: PerformanceEntry[]) => number,
) {
  if (!('PerformanceObserver' in window)) return
  try {
    const observer = new PerformanceObserver((list) =>
      reportFrontendEvent({ type, durationMs: value(list.getEntries()) }),
    )
    observer.observe({ type: entryType, buffered: true })
  } catch {
    // Older browsers may not expose every entry type.
  }
}
