import { useState, useEffect, useRef, useCallback } from 'react'
import { providerBookingService } from '../services/providerBookingService'

export function useProviderTripTracking(bookingCode, { enabled = true, intervalMs = 10000 } = {}) {
  const [trackingData, setTrackingData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const lastFetchTimestampRef = useRef(0)
  const isMountedRef = useRef(true)

  const fetchTracking = useCallback(async () => {
    if (!bookingCode) return

    const reqTimestamp = Date.now()
    try {
      const data = await providerBookingService.getTripTracking(bookingCode)

      // Guard against out-of-order network responses
      if (isMountedRef.current && reqTimestamp >= lastFetchTimestampRef.current) {
        lastFetchTimestampRef.current = reqTimestamp
        setTrackingData(data)
        setError(null)
      }
    } catch (err) {
      if (isMountedRef.current && reqTimestamp >= lastFetchTimestampRef.current) {
        setError(err.response?.data?.message || err.message || 'Không thể tải dữ liệu định vị')
      }
    } finally {
      if (isMountedRef.current) {
        setLoading(false)
      }
    }
  }, [bookingCode])

  useEffect(() => {
    isMountedRef.current = true

    if (!enabled || !bookingCode) {
      setTrackingData(null)
      return
    }

    setLoading(true)
    fetchTracking()

    let timerId = null

    const startPolling = () => {
      if (timerId !== null) clearInterval(timerId)
      timerId = setInterval(() => {
        if (document.visibilityState === 'visible') {
          fetchTracking()
        }
      }, intervalMs)
    }

    const stopPolling = () => {
      if (timerId !== null) {
        clearInterval(timerId)
        timerId = null
      }
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchTracking()
        startPolling()
      } else {
        stopPolling()
      }
    }

    startPolling()
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      isMountedRef.current = false
      stopPolling()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [enabled, bookingCode, intervalMs, fetchTracking])

  return {
    trackingData,
    loading,
    error,
    refetchTracking: fetchTracking,
  }
}
