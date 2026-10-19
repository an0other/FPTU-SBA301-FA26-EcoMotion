import { useEffect, useState } from 'react'
import { paymentService } from '../services/paymentService'

export function useBookingCharges(bookingCode) {
  const [state, setState] = useState({
    data: null,
    loading: true,
    error: '',
  })
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true

    async function load() {
      setState({ data: null, loading: true, error: '' })

      try {
        const data = await paymentService.getBookingCharges(bookingCode)

        if (active) {
          setState({ data, loading: false, error: '' })
        }
      } catch (error) {
        if (active) {
          setState({
            data: null,
            loading: false,
            error: error.message || 'Không tải được các khoản phí.',
          })
        }
      }
    }

    load()

    return () => {
      active = false
    }
  }, [bookingCode, revision])

  return {
    ...state,
    retry: () => setRevision((value) => value + 1),
  }
}