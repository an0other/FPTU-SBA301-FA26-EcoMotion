import apiClient from './apiClient.js'
import { serializeTripFilters } from '../utils/driverTripUtils.js'

export function createDriverTripService(client = apiClient) {
  return {
    async listTrips(options = {}) {
      const params = serializeTripFilters(options)
      const { data } = await client.get('/v1/driver/trips', { params })
      return (
        data?.data ?? {
          items: [],
          total: 0,
          offset: 0,
          limit: params.size || 20,
          hasMore: false,
        }
      )
    },

    async getActiveTrip() {
      try {
        const { data } = await client.get('/v1/driver/trips', {
          params: { view: 'ACTIVE', size: 1 },
        })
        const items = data?.data?.items || []
        return items.length > 0 ? items[0] : null
      } catch {
        return null
      }
    },

    async getTripDetail(tripIdOrCode) {
      if (!tripIdOrCode) {
        throw new Error('Mã chuyến đi không hợp lệ')
      }
      const { data } = await client.get(
        `/v1/driver/trips/${encodeURIComponent(tripIdOrCode)}`
      )
      return data?.data
    },

    async confirmTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/v1/driver/trips/${encodeURIComponent(tripId)}/confirm`)
      return data?.data
    },

    async arriveTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/v1/driver/trips/${encodeURIComponent(tripId)}/arrive`)
      return data?.data
    },

    async startTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/v1/driver/trips/${encodeURIComponent(tripId)}/start`)
      return data?.data
    },

    async finishTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/v1/driver/trips/${encodeURIComponent(tripId)}/finish`)
      return data?.data
    },

    async sendLocation(tripId, locationPayload) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/v1/driver/trips/${encodeURIComponent(tripId)}/locations`, locationPayload)
      return data?.data
    },

    async getTripTracking(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.get(`/v1/driver/trips/${encodeURIComponent(tripId)}/tracking`)
      return data?.data
    },
  }
}

export const driverTripService = createDriverTripService()
