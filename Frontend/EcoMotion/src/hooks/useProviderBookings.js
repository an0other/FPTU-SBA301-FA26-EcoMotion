import { useEffect, useState, useCallback, useRef } from 'react'
import { providerBookingService } from '../services/providerBookingService'
export function useProviderBookings(status = 'ALL', filters = {}) {
  const [bookings, setBookings] = useState([])
  const [summary, setSummary] = useState({})
  const [total, setTotal] = useState(0)
  const filterKey = JSON.stringify(filters)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const sequence = useRef(0)
  const refetch = useCallback(async () => {
    const id = ++sequence.current
    try {
      const [rows, counts] = await Promise.all([providerBookingService.listBookingsPaged(status, JSON.parse(filterKey).offset || 0, JSON.parse(filterKey).limit || 10, JSON.parse(filterKey)), providerBookingService.getSummary()])
      if (id !== sequence.current) return
      setBookings(Array.isArray(rows?.items) ? rows.items : [])
      setTotal(Number(rows?.total) || 0)
      setSummary(counts || {})
      setError(null)
    } catch (err) {
      if (id === sequence.current) setError(err.message || 'Không thể tải dữ liệu')
    } finally {
      if (id === sequence.current) setLoading(false)
    }
  }, [status, filterKey])
  useEffect(() => {
    const timer = setTimeout(refetch, 0)
    return () => { clearTimeout(timer); ++sequence.current }
  }, [refetch])
  return { bookings, total, summary, loading, error, refetch }
}
