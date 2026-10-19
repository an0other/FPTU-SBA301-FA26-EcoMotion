import { useCallback, useEffect, useRef, useState } from 'react'
import { driverTripService } from '../services/driverTripService'
import { useAuth } from './useAuth'

export function useDriverSchedule(initialOptions = {}) {
  const { user } = useAuth()
  const [view, setViewState] = useState(initialOptions.view || 'TODAY')
  const [status, setStatusState] = useState(initialOptions.status || 'ALL')
  const [date, setDateState] = useState(initialOptions.date || null)
  const [page, setPageState] = useState(initialOptions.page || 0)
  const [size, setSizeState] = useState(initialOptions.size || 10)

  const [trips, setTrips] = useState([])
  const [activeTrip, setActiveTrip] = useState(null)
  const [total, setTotal] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const requestIdRef = useRef(0)

  const setView = useCallback((newView) => {
    setViewState(newView)
    setPageState(0)
  }, [])

  const setStatus = useCallback((newStatus) => {
    setStatusState(newStatus)
    setPageState(0)
  }, [])

  const setDate = useCallback((newDate) => {
    setDateState(newDate)
    setPageState(0)
  }, [])

  const setPage = useCallback((newPage) => {
    setPageState(newPage)
  }, [])

  const setSize = useCallback((newSize) => {
    setSizeState(newSize)
    setPageState(0)
  }, [])

  const fetchData = useCallback(async () => {
    const currentRequestId = ++requestIdRef.current
    setLoading(true)
    setError(null)

    try {
      const [listResult, activeResult] = await Promise.all([
        driverTripService.listTrips({ view, status, date, page, size }),
        driverTripService.getActiveTrip().catch(() => null),
      ])

      if (currentRequestId === requestIdRef.current) {
        setTrips(listResult.items || [])
        setTotal(listResult.total || 0)
        setHasMore(Boolean(listResult.hasMore))
        setActiveTrip(activeResult)
        setLoading(false)
      }
    } catch (err) {
      if (currentRequestId === requestIdRef.current) {
        let message = 'Không thể tải lịch trình chuyến đi.'
        if (err?.status === 401) {
          message = 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
        } else if (err?.status === 403) {
          message = 'Bạn không có quyền truy cập vào danh sách chuyến của tài xế.'
        } else if (err?.message) {
          message = err.message
        }
        setError(message)
        setLoading(false)
      }
    }
  }, [view, status, date, page, size])

  useEffect(() => {
    fetchData()
  }, [fetchData, user?.id])

  return {
    view,
    status,
    date,
    page,
    size,
    trips,
    activeTrip,
    total,
    hasMore,
    loading,
    error,
    setView,
    setStatus,
    setDate,
    setPage,
    setSize,
    refetch: fetchData,
  }
}
