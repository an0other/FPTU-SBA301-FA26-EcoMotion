import { useCallback, useEffect, useRef, useState } from 'react'
import { getMyBookingTracking } from '../services/bookingService'
import { readErrorMessage } from '../services/apiClient'

export function useRenterTripTracking(bookingCode, enabled, intervalMs = 10000) {
  const [trackingData, setTrackingData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const mountedRef = useRef(true)
  const requestSequenceRef = useRef(0)

  const refetch = useCallback(async () => {
    if (!enabled || !bookingCode) return
    const sequence = ++requestSequenceRef.current
    setLoading(true)
    try {
      const data = await getMyBookingTracking(bookingCode)
      if (mountedRef.current && sequence === requestSequenceRef.current) {
        setTrackingData(data)
        setError('')
      }
    } catch (err) {
      if (mountedRef.current && sequence === requestSequenceRef.current) {
        setError(readErrorMessage(err))
      }
    } finally {
      if (mountedRef.current && sequence === requestSequenceRef.current) setLoading(false)
    }
  }, [bookingCode, enabled])

  useEffect(() => {
    mountedRef.current = true
    if (!enabled || !bookingCode) return () => { mountedRef.current = false }
    const initialTimer = window.setTimeout(refetch, 0)
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === 'visible') refetch()
    }, intervalMs)
    const handleFocus = () => refetch()
    window.addEventListener('focus', handleFocus)
    return () => {
      mountedRef.current = false
      window.clearTimeout(initialTimer)
      window.clearInterval(intervalId)
      window.removeEventListener('focus', handleFocus)
    }
  }, [bookingCode, enabled, intervalMs, refetch])

  return { trackingData, loading, error, refetch }
}
