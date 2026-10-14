import { useEffect, useState } from 'react'
import { vehicleService } from '../services/vehicleService'

export function useMapVehicles(filters, origin, radiusKm, enabled) {
  const { keyword, location, fuelType, planType, maxPrice, sortBy } = filters
  const [state, setState] = useState({ vehicles: [], loading: false, error: null })

  useEffect(() => {
    if (!enabled || !origin) return undefined
    let active = true

    async function load() {
      setState((previous) => ({ ...previous, loading: true, error: null }))
      try {
        const vehicles = await vehicleService.findNearbyVehicles({
          latitude: origin.latitude,
          longitude: origin.longitude,
          radiusKm,
          keyword,
          location,
          fuelType,
          planType,
          maxPrice,
          sortBy,
        })
        if (active) setState({ vehicles, loading: false, error: null })
      } catch (error) {
        if (active) setState({ vehicles: [], loading: false,
          error: error?.message || 'Không tải được xe trên bản đồ.' })
      }
    }

    load()
    return () => { active = false }
  }, [enabled, origin, radiusKm, keyword, location, fuelType, planType, maxPrice, sortBy])

  return state
}
