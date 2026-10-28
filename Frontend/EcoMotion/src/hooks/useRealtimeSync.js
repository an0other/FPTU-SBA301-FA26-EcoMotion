import { useEffect, useRef, useState, useCallback } from 'react'
import { getAccessToken } from '../utils/tokenStore.js'

/**
 * TV5 Realtime Event Contract definitions.
 * Use these constants to subscribe to dispatch, lifecycle, and finance events.
 */
export const TV5_REALTIME_EVENTS = {
  BOOKING_CONFIRMED: 'BOOKING_CONFIRMED',
  DRIVER_ASSIGNED: 'DRIVER_ASSIGNED',
  DRIVER_STATUS_CHANGED: 'DRIVER_STATUS_CHANGED',
  DRIVER_ACCEPTED: 'DRIVER_ACCEPTED',
  DRIVER_REJECTED: 'DRIVER_REJECTED',
  DRIVER_REASSIGNED: 'DRIVER_REASSIGNED',
  TRIP_STARTED: 'TRIP_STARTED',
  TRIP_COMPLETED: 'TRIP_COMPLETED',
  DRIVER_INCIDENT: 'DRIVER_INCIDENT',
  PAYMENT_VERIFIED: 'PAYMENT_VERIFIED',
  REFUND_UPDATED: 'REFUND_UPDATED',
}

/**
 * useRealtimeSync Hook
 * Connects to the existing SSE Notification stream when available.
 * If SSE is not yet running or disconnected, falls back seamlessly to
 * window-focus revalidation and periodic quiet polling to maintain
 * absolute state parity between Provider and Driver dashboards.
 *
 * @param {Object} options
 * @param {Object} options.handlers - Map of { [EVENT_NAME]: (payload) => void }
 * @param {Function} options.onResync - Callback to refetch data on sync triggers
 * @param {number} [options.pollIntervalMs=15000] - Fallback poll interval when SSE offline
 * @param {boolean} [options.enabled=true] - Toggle sync active
 */
export function useRealtimeSync({
  handlers = {},
  onResync,
  pollIntervalMs = 15000,
  enabled = true,
} = {}) {
  const [isConnected, setIsConnected] = useState(false)
  const [sseAvailable, setSseAvailable] = useState(true)
  const [lastSyncedAt, setLastSyncedAt] = useState(new Date())

  const handlersRef = useRef(handlers)
  const onResyncRef = useRef(onResync)
  handlersRef.current = handlers
  onResyncRef.current = onResync

  const triggerSync = useCallback(() => {
    setLastSyncedAt(new Date())
    if (typeof onResyncRef.current === 'function') {
      try {
        onResyncRef.current()
      } catch (err) {
        console.warn('RealtimeSync onResync callback error:', err)
      }
    }
  }, [])

  // Listen to Window Focus and Online events to resync state immediately
  useEffect(() => {
    if (!enabled) return

    const handleFocusOrOnline = () => {
      triggerSync()
    }

    window.addEventListener('focus', handleFocusOrOnline)
    window.addEventListener('online', handleFocusOrOnline)

    return () => {
      window.removeEventListener('focus', handleFocusOrOnline)
      window.removeEventListener('online', handleFocusOrOnline)
    }
  }, [enabled, triggerSync])

  // Fallback Polling when SSE is unavailable or down
  useEffect(() => {
    if (!enabled || isConnected || !pollIntervalMs) return

    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') triggerSync()
    }, pollIntervalMs)

    return () => clearInterval(timer)
  }, [enabled, isConnected, pollIntervalMs, triggerSync])

  // SSE Connection attempt
  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') {
      setSseAvailable(false)
      return
    }

    const token = getAccessToken()
    if (!token) {
      setSseAvailable(false)
      return
    }

    // Attempt standard SSE notification stream endpoint
    const streamUrl = `/api/v1/notifications/stream?token=${encodeURIComponent(token)}`

    let eventSource = null
    let reconnectTimeout = null

    try {
      eventSource = new EventSource(streamUrl)

      eventSource.onopen = () => {
        setIsConnected(true)
        setSseAvailable(true)
        triggerSync()
      }

      eventSource.onerror = () => {
        setIsConnected(false)
        // If SSE endpoint fails (e.g. 404 or backend SSE not yet mounted), fallback quietly to polling
        setSseAvailable(false)
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
      }

      // Register listener for generic message
      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          const eventType = data?.type || data?.event
          if (eventType && handlersRef.current[eventType]) {
            handlersRef.current[eventType](data.payload || data)
          }
          if (handlersRef.current['*']) {
            handlersRef.current['*'](eventType, data)
          }
          triggerSync()
        } catch {
          // Ignore non-json heartbeats
        }
      }

      // Register listeners for all specific TV5 events
      Object.values(TV5_REALTIME_EVENTS).forEach((evName) => {
        eventSource.addEventListener(evName, (event) => {
          try {
            const data = JSON.parse(event.data)
            if (handlersRef.current[evName]) {
              handlersRef.current[evName](data)
            }
          } catch {
            if (handlersRef.current[evName]) {
              handlersRef.current[evName](event.data)
            }
          }
          triggerSync()
        })
      })
    } catch {
      setIsConnected(false)
      setSseAvailable(false)
    }

    return () => {
      if (reconnectTimeout) clearTimeout(reconnectTimeout)
      if (eventSource) {
        eventSource.close()
      }
      setIsConnected(false)
    }
  }, [enabled, triggerSync])

  return {
    isConnected,
    sseAvailable,
    lastSyncedAt,
    triggerSync,
  }
}
