import { useEffect, useState, useCallback, useRef } from 'react'
import { useRealtimeSync } from './useRealtimeSync'
import { providerBookingService } from '../services/providerBookingService'
export function useAvailableDrivers(bookingId, enabled = true) {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const sequence = useRef(0)
  const refetch = useCallback(async () => {
    if (!enabled || !bookingId) return
    const id = ++sequence.current
    setLoading(true)
    try {
      const data = await providerBookingService.getAvailableDrivers(bookingId)
      if (id === sequence.current) { setDrivers(data); setError(null) }
    } catch (err) {
      if (id === sequence.current) setError(err?.message || 'Không thể tải danh sách tài xế.')
    } finally {
      if (id === sequence.current) setLoading(false)
    }
  }, [bookingId, enabled])
  useEffect(() => {
    const timer = setTimeout(refetch, 0)
    return () => { clearTimeout(timer); ++sequence.current }
  }, [refetch])
  useRealtimeSync({ enabled: enabled && !!bookingId, onResync: refetch })
  return { drivers, loading, error, refetch }
}
