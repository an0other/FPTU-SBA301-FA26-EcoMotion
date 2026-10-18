import { useEffect, useState, useCallback } from 'react'
import { bookingService } from '../services/bookingService'

const INITIAL_STATE = {
  booking: null,
  loading: true,
  error: null,
}

export function useProviderBookingDetail(bookingId) {
  const [state, setState] = useState(INITIAL_STATE)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true

    async function load() {
      if (!bookingId) {
        setState({ booking: null, loading: false, error: 'Thiếu mã đơn đặt xe.' })
        return
      }

      setState((prev) => ({ ...prev, loading: true, error: null }))
      try {
        const booking = await bookingService.getProviderBookingDetail(bookingId)
        if (!active) return
        setState({ booking, loading: false, error: null })
      } catch (err) {
        if (!active) return
        setState({
          booking: null,
          loading: false,
          error: err?.message || 'Không thể tải thông tin đơn đặt xe.',
        })
      }
    }

    load()

    return () => {
      active = false
    }
  }, [bookingId, attempt])

  const reload = useCallback(() => {
    setAttempt((v) => v + 1)
  }, [])

  return {
    ...state,
    reload,
  }
}
