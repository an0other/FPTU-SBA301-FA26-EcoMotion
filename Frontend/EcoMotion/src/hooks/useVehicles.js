import { useEffect, useState } from 'react'
import { vehicleService } from '../services/vehicleService'

export const PAGE_SIZE = 6

const INITIAL_STATE = {
  vehicles: [],
  total: 0,
  offset: 0,
  limit: PAGE_SIZE,
  hasMore: false,
  loading: true,
  error: null,
}

export function useVehicles(filters = {}) {
  const { keyword, location, fuelType, planType, maxPrice, sortBy, offset = 0 } = filters
  const [state, setState] = useState(INITIAL_STATE)

  useEffect(() => {
    let active = true

    async function load() {
      setState((prev) => ({ ...prev, loading: true, error: null }))
      try {
        const page = await vehicleService.listVehicles({
          keyword,
          location,
          fuelType,
          planType,
          maxPrice,
          sortBy,
          offset,
          limit: PAGE_SIZE,
        })
        if (!active) return
        setState({
          vehicles: page.items,
          total: page.total,
          offset: page.offset,
          limit: page.limit,
          hasMore: page.hasMore,
          loading: false,
          error: null,
        })
      } catch (err) {
        if (!active) return
        setState({
          ...INITIAL_STATE,
          offset,
          loading: false,
          error: err?.message || 'Không tải được danh sách xe.',
        })
      }
    }

    load()

    return () => {
      active = false
    }
  }, [keyword, location, fuelType, planType, maxPrice, sortBy, offset])

  return state
}
