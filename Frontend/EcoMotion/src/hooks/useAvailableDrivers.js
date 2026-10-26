import { useEffect, useState, useCallback } from 'react'
import { providerBookingService } from '../services/providerBookingService'

export function useAvailableDrivers(bookingCode) {
  const [drivers, setDrivers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let active = true

    async function fetchDrivers() {
      try {
        const data = await providerBookingService.getAvailableDrivers(bookingCode)
        if (active) {
          setDrivers(data)
          setLoading(false)
        }
      } catch (err) {
        if (active) {
          setError(err?.message || 'Không thể tải danh sách tài xế.')
          setLoading(false)
        }
      }
    }

    fetchDrivers()

    return () => {
      active = false
    }
  }, [bookingCode])

  const refetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await providerBookingService.getAvailableDrivers(bookingCode)
      setDrivers(data)
    } catch (err) {
      setError(err?.message || 'Không thể tải danh sách tài xế.')
    } finally {
      setLoading(false)
    }
  }, [bookingCode])

  return {
    drivers,
    loading,
    error,
    refetch,
  }
}
