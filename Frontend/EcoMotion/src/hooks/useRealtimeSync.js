import { useEffect, useRef, useState, useCallback } from 'react'
import { getAccessToken, subscribeAccessToken } from '../utils/tokenStore.js'

export const TV5_REALTIME_EVENTS = Object.fromEntries([
  'BOOKING_CONFIRMED', 'BOOKING_CHANGED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED',
  'DRIVER_ACCEPTED', 'DRIVER_REJECTED', 'DRIVER_REASSIGNED', 'DRIVER_TIMEOUT', 'DRIVER_UNAVAILABLE',
  'DRIVER_ARRIVED', 'DRIVER_STATUS_CHANGED', 'TRIP_STARTED', 'TRIP_COMPLETED',
  'DRIVER_INCIDENT', 'INCIDENT_RESOLVED', 'CHARGES_CHANGED', 'PAYMENT_VERIFIED', 'PAYMENT_RECONCILIATION_REQUIRED', 'REFUND_UPDATED', 'OPERATIONAL_PROPOSAL_CHANGED',
].map(name => [name, name]))

export function useRealtimeSync({ handlers = {}, onResync, pollIntervalMs = 15000, enabled = true } = {}) {
  const [isConnected, setIsConnected] = useState(false)
  const [sseAvailable, setSseAvailable] = useState(true)
  const [lastSyncedAt, setLastSyncedAt] = useState(null)
  const handlersRef = useRef(handlers)
  const onResyncRef = useRef(onResync)
  const pending = useRef(false)
  const queued = useRef(false)
  const active = useRef(enabled)
  const [token, setToken] = useState(getAccessToken)
  useEffect(() => subscribeAccessToken(setToken), [])
  useEffect(() => {
    active.current = enabled
    return () => { active.current = false; queued.current = false }
  }, [enabled])
  useEffect(() => { handlersRef.current = handlers; onResyncRef.current = onResync }, [handlers, onResync])
  const triggerSync = useCallback(async () => {
    if (!active.current) return
    queued.current = true
    if (pending.current) return
    pending.current = true
    try {
      do {
        queued.current = false
        await onResyncRef.current?.()
        if (active.current) setLastSyncedAt(new Date())
      } while (queued.current && active.current)
    } finally {
      pending.current = false
    }
  }, [])
  useEffect(() => {
    if (!enabled) return
    const sync = () => { if (document.visibilityState === 'visible') triggerSync().catch(() => {}) }
    window.addEventListener('focus', sync)
    window.addEventListener('online', sync)
    document.addEventListener('visibilitychange', sync)
    const timer = pollIntervalMs ? setInterval(sync, pollIntervalMs) : null
    return () => {
      if (timer) clearInterval(timer)
      window.removeEventListener('focus', sync)
      window.removeEventListener('online', sync)
      document.removeEventListener('visibilitychange', sync)
    }
  }, [enabled, pollIntervalMs, triggerSync])
  useEffect(() => {
    if (!enabled || !token || typeof EventSource === 'undefined') return
    const baseUrl = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
    const source = new EventSource(`${baseUrl}/v1/notifications/stream?token=${encodeURIComponent(token)}`)
    source.onopen = () => { setIsConnected(true); setSseAvailable(true); triggerSync().catch(() => {}) }
    source.onerror = () => { setIsConnected(false); setSseAvailable(false) }
    const receive = event => {
      let data
      try { data = JSON.parse(event.data) } catch { return }
      handlersRef.current[event.type]?.(data)
      triggerSync().catch(() => {})
    }
    Object.values(TV5_REALTIME_EVENTS).forEach(name => source.addEventListener(name, receive))
    source.onmessage = receive
    return () => { source.close() }
  }, [enabled, triggerSync, token])
  return { isConnected, sseAvailable, lastSyncedAt, triggerSync }
}
