import { useEffect, useState } from 'react'
import { reviewService } from '../services/reviewService'

export function useBookingReviews(bookingCode) {
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
        const data = await reviewService.getBookingReviews(bookingCode)

        if (active) {
          setState({ data, loading: false, error: '' })
        }
      } catch (error) {
        if (active) {
          setState({
            data: null,
            loading: false,
            error: error.message || 'Không tải được thông tin đánh giá.',
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
    reload: () => setRevision((value) => value + 1),
  }
}