import { useEffect, useState } from 'react'
import { providerBookingService } from '../services/providerBookingService'

export function useProviderBookings(status) {
  const [state, setState] = useState({ bookings: [], loading: true, error: null })

  useEffect(() => {
    let active = true

    async function load() {
      setState((prev) => ({ ...prev, loading: true, error: null }))
      try {
        const bookings = await providerBookingService.listBookings(status)
        if (!active) return
        setState({ bookings, loading: false, error: null })
      } catch (err) {
        if (!active) return
        setState({
          bookings: [],
          loading: false,
          error: err?.status === 403
            ? 'Bạn không có quyền xem danh sách booking của nhà cung cấp.'
            : err?.message || 'Không tải được danh sách booking.',
        })
      }
    }

    load()

    return () => {
      active = false
    }
  }, [status])

  return state
}
