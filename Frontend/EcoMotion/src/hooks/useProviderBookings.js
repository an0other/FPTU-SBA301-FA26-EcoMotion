import { useEffect, useState, useCallback } from 'react'
import { providerBookingService } from '../services/providerBookingService'

export function useProviderBookings(status = 'ALL') {
  const [bookings, setBookings] = useState([])
  const [summary, setSummary] = useState({
    total: 0,
    pending: 0,
    accepted: 0,
    confirmed: 0,
    inProgress: 0,
    completed: 0,
    rejected: 0,
    cancelled: 0,
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let active = true

    async function fetchData(silent = false) {
      if (!silent) {
        setLoading(true)
      }
      try {
        const [listResult, summaryResult] = await Promise.all([
          providerBookingService.listBookings(status),
          providerBookingService.getSummary().catch(() => null),
        ])
        if (active) {
          setBookings(listResult)
          if (summaryResult) {
            setSummary(summaryResult)
          }
          setError(null)
          if (!silent) {
            setLoading(false)
          }
        }
      } catch (err) {
        if (active) {
          if (!silent) {
            setError(
              err?.status === 403
                ? 'Bạn không có quyền truy cập dữ liệu của nhà cung cấp.'
                : err?.message || 'Không thể tải danh sách đơn thuê từ máy chủ.',
            )
            setLoading(false)
          }
        }
      }
    }

    fetchData(false)

    // Window focus & visibility change handler for instant state synchronization
    const handleSync = () => {
      if (document.visibilityState === 'visible' && active) {
        fetchData(true)
      }
    }

    window.addEventListener('focus', handleSync)
    document.addEventListener('visibilitychange', handleSync)

    // Periodic polling every 8s when tab is active
    const intervalId = setInterval(() => {
      if (document.visibilityState === 'visible') {
        fetchData(true)
      }
    }, 8000)

    return () => {
      active = false
      window.removeEventListener('focus', handleSync)
      document.removeEventListener('visibilitychange', handleSync)
      clearInterval(intervalId)
    }
  }, [status])

  const refetch = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true)
    }
    setError(null)
    try {
      const [listResult, summaryResult] = await Promise.all([
        providerBookingService.listBookings(status),
        providerBookingService.getSummary().catch(() => null),
      ])
      setBookings(listResult)
      if (summaryResult) {
        setSummary(summaryResult)
      }
    } catch (err) {
      if (!silent) {
        setError(
          err?.status === 403
            ? 'Bạn không có quyền truy cập dữ liệu của nhà cung cấp.'
            : err?.message || 'Không thể tải danh sách đơn thuê từ máy chủ.',
        )
      }
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [status])

  return {
    bookings,
    summary,
    loading,
    error,
    refetch,
  }
}
