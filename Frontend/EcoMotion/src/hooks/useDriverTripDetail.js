import { useCallback, useEffect, useState } from 'react'
import { driverTripService } from '../services/driverTripService'
import { useAuth } from './useAuth'

export function useDriverTripDetail(tripIdOrCode) {
  const { user } = useAuth()
  const [trip, setTrip] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchDetail = useCallback(async () => {
    if (!tripIdOrCode) return
    setLoading(true)
    setError(null)

    try {
      const data = await driverTripService.getTripDetail(tripIdOrCode)
      setTrip(data)
    } catch (err) {
      let message = 'Không thể tải thông tin chi tiết chuyến đi.'
      if (err?.status === 401) {
        message = 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
      } else if (err?.status === 403) {
        message = 'Bạn không có quyền truy cập thông tin chuyến này.'
      } else if (err?.status === 404) {
        message = 'Chuyến đi không tồn tại hoặc bạn chưa được phân công.'
      } else if (err?.message) {
        message = err.message
      }
      setError(message)
    } finally {
      setLoading(false)
    }
  }, [tripIdOrCode])

  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active) fetchDetail()
    })
    return () => {
      active = false
    }
  }, [fetchDetail, user?.id])

  return {
    trip,
    loading,
    error,
    refetch: fetchDetail,
  }
}
