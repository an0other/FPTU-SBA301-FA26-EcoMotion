import { useEffect, useRef, useState, useCallback } from 'react'
import { subscribeNotificationStream } from '../services/notificationStream.js'

export const TV5_REALTIME_EVENTS = Object.fromEntries([
  'BOOKING_CONFIRMED', 'BOOKING_CHANGED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED',
  'DRIVER_ACCEPTED', 'DRIVER_REJECTED', 'DRIVER_REASSIGNED', 'DRIVER_TIMEOUT', 'DRIVER_UNAVAILABLE',
  'DRIVER_ARRIVED', 'DRIVER_STATUS_CHANGED', 'TRIP_STARTED', 'TRIP_COMPLETED',
  'DRIVER_INCIDENT', 'INCIDENT_RESOLVED', 'TRIP_END_CONFIRMATION_CHANGED', 'DISPUTE_OPENED', 'DISPUTE_UPDATED', 'CHARGES_CHANGED', 'PAYMENT_VERIFIED', 'PAYMENT_RECONCILIATION_REQUIRED', 'PAYMENT_PROOF_SUBMITTED', 'PAYMENT_OVERPAID', 'PAYMENT_ISSUE_REPORTED', 'REFUND_UPDATED', 'OPERATIONAL_PROPOSAL_CHANGED',
].map(name => [name, name]))

const REALTIME_EVENT_NAMES = new Set(Object.values(TV5_REALTIME_EVENTS))

// Khi SSE kết nối lại liên tục (stream đóng sớm, proxy lỗi) mỗi lần 'open' không được kéo dữ liệu lại;
// chỉ đồng bộ nếu lần đồng bộ gần nhất đã cũ hơn ngưỡng này.
export const RECONNECT_RESYNC_MIN_GAP_MS = 30000

export function useRealtimeSync({ handlers = {}, onResync, pollIntervalMs = 15000, enabled = true } = {}) {
  const [isConnected, setIsConnected] = useState(false)
  const [sseAvailable, setSseAvailable] = useState(true)
  const [lastSyncedAt, setLastSyncedAt] = useState(null)
  const handlersRef = useRef(handlers)
  const onResyncRef = useRef(onResync)
  const pending = useRef(false)
  const lastSyncAt = useRef(0)
  const queued = useRef(false)
  const active = useRef(enabled)
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
        lastSyncAt.current = Date.now()
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
    if (!enabled) return undefined
    // Một kết nối SSE dùng chung cho cả tab; hook chỉ đăng ký listener và hủy khi unmount.
    return subscribeNotificationStream((event) => {
      if (event.type === 'status') {
        if (event.status === 'open') {
          setIsConnected(true)
          setSseAvailable(true)
        } else if (event.status === 'reconnecting' || event.status === 'auth-failed') {
          setIsConnected(false)
          setSseAvailable(false)
        } else {
          setIsConnected(false)
        }
        return
      }
      if (event.type === 'open') {
        if (Date.now() - lastSyncAt.current >= RECONNECT_RESYNC_MIN_GAP_MS) triggerSync().catch(() => {})
        return
      }
      if (event.type !== 'message' && !REALTIME_EVENT_NAMES.has(event.type)) return
      let data
      try { data = JSON.parse(event.data) } catch { return }
      handlersRef.current[event.type]?.(data)
      triggerSync().catch(() => {})
    })
  }, [enabled, triggerSync])
  return { isConnected, sseAvailable, lastSyncedAt, triggerSync }
}
