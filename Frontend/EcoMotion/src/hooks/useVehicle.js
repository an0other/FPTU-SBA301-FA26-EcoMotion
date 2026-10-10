import { useEffect, useState } from 'react'
import { vehicleService } from '../services/vehicleService'

const INITIAL_STATE = {
  vehicle: null,
  loading: true,
  error: null,
  notFound: false,
}

export function useVehicle(id) {
  const [state, setState] = useState(INITIAL_STATE)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true

    async function load() {
      if (!id) {
        setState({ ...INITIAL_STATE, loading: false, notFound: true })
        return
      }

      setState(INITIAL_STATE)
      try {
        const vehicle = await vehicleService.getVehicle(id)
        if (!active) return
        setState({ vehicle, loading: false, error: null, notFound: false })
      } catch (err) {
        if (!active) return
        const notFound = err?.status === 404
        setState({
          vehicle: null,
          loading: false,
          notFound,
          error: notFound ? null : err?.message || 'Không tải được thông tin xe.',
        })
      }
    }

    load()

    return () => {
      active = false
    }
  }, [id, attempt])

  return {
    ...state,
    reload: () => setAttempt((value) => value + 1),
  }
}
