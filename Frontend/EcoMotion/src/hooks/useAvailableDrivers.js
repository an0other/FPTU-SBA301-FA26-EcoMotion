import { useEffect, useState, useCallback, useRef } from 'react'
import { useRealtimeSync } from './useRealtimeSync'
import { providerBookingService } from '../services/providerBookingService'
export function useAvailableDrivers(bookingCode, enabled = true) {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const sequence = useRef(0)
  const refetch = useCallback(async () => {
    if (!enabled || !bookingCode) return
    const id = ++sequence.current
    setLoading(true)
    try {
      const data = await providerBookingService.getAvailableDrivers(bookingCode)
      if (id === sequence.current) { setDrivers(data); setError(null) }
    } catch (err) {
      if (id === sequence.current) setError(err?.message || 'Không thể tải danh sách tài xế.')
    } finally {
      if (id === sequence.current) setLoading(false)
    }
  }, [bookingCode, enabled])
  useEffect(() => {
    const timer = setTimeout(refetch, 0)
    return () => {
      clearTimeout(timer)
      // This ref is a numeric request generation, not a rendered DOM ref.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      ++sequence.current
    }
  }, [refetch])
  useRealtimeSync({ enabled: enabled && !!bookingCode, onResync: refetch })
  return { drivers, loading, error, refetch }
}
