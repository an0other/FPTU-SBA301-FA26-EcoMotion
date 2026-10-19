import { useEffect, useState, useCallback } from 'react'
import { providerBookingService } from '../services/providerBookingService'

export function useProviderBookingDetail(idOrCode) {
  const [booking, setBooking] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!idOrCode) {
      return
    }

    let active = true

    async function fetchDetail(silent = false) {
      if (!silent) {
        setLoading(true)
      }
      try {
        const data = await providerBookingService.getBookingDetail(idOrCode)
        if (active) {
          setBooking(data)
          setError(null)
          if (!silent) {
            setLoading(false)
          }
        }
      } catch (err) {
        if (active) {
          if (!silent) {
            setError(
              err?.status === 404
                ? 'Không tìm thấy thông tin đơn thuê này.'
                : err?.status === 403
                ? 'Bạn không có quyền xem chi tiết đơn thuê này.'
                : err?.message || 'Không thể tải thông tin chi tiết đơn thuê.',
            )
            setLoading(false)
          }
        }
      }
    }

    fetchDetail(false)

    // Sync on window focus or tab visibility
    const handleSync = () => {
      if (document.visibilityState === 'visible' && active) {
        fetchDetail(true)
      }
    }

    window.addEventListener('focus', handleSync)
    document.addEventListener('visibilitychange', handleSync)

    // Auto-polling every 5 seconds for real-time driver/trip synchronization
    const intervalId = setInterval(() => {
      if (document.visibilityState === 'visible' && active) {
        fetchDetail(true)
      }
    }, 5000)

    return () => {
      active = false
      window.removeEventListener('focus', handleSync)
      document.removeEventListener('visibilitychange', handleSync)
      clearInterval(intervalId)
    }
  }, [idOrCode])

  const refetch = useCallback(async (silent = false) => {
    if (!idOrCode) return
    if (!silent) {
      setLoading(true)
    }
    setError(null)
    try {
      const data = await providerBookingService.getBookingDetail(idOrCode)
      setBooking(data)
    } catch (err) {
      if (!silent) {
        setError(
          err?.status === 404
            ? 'Không tìm thấy thông tin đơn thuê này.'
            : err?.status === 403
            ? 'Bạn không có quyền xem chi tiết đơn thuê này.'
            : err?.message || 'Không thể tải thông tin chi tiết đơn thuê.',
        )
      }
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [idOrCode])

  return {
    booking,
    loading,
    error,
    refetch,
  }
}
