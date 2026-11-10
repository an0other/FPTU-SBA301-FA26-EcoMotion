import apiClient from './apiClient.js'
import { serializeTripFilters } from '../utils/driverTripUtils.js'

export function createDriverTripService(client = apiClient) {
  return {
    async listTrips(options = {}) {
      const params = serializeTripFilters(options)
      const { data } = await client.get('/driver/trips', { params })
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

    // Banner chỉ hiện chuyến đang thực sự diễn ra (IN_PROGRESS). Không lấy item đầu tiên của view ACTIVE
    // vì nó có thể là chuyến DRIVER_ARRIVED, khiến chuyến IN_PROGRESS bị bỏ sót.
    async getActiveTrip() {
      const { data } = await client.get('/driver/trips', { params: { view: 'ACTIVE', size: 20 } })
      const items = data?.data?.items || []
      return items.find((item) => item?.status === 'IN_PROGRESS') || null
    },

    // Số chuyến theo từng tab: { TODAY, REQUESTS, UPCOMING, ACTIVE, HISTORY }
    async getTripCounts() {
      const { data } = await client.get('/driver/trips/counts')
      return data?.data || {}
    },

    // Chuyến tài xế đang làm dở (đã đến điểm đón / đang chạy, chưa kết thúc) — dùng để khoá thao tác chuyến khác.
    async getBusyTrips() {
      const { data } = await client.get('/driver/trips', { params: { view: 'ACTIVE', size: 20 } })
      const items = data?.data?.items || []
      return items.filter((item) => ['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS'].includes(item?.status))
    },

    async getTripDetail(tripIdOrCode) {
      if (!tripIdOrCode) {
        throw new Error('Mã chuyến đi không hợp lệ')
      }
      const { data } = await client.get(
        `/driver/trips/${encodeURIComponent(tripIdOrCode)}`
      )
      return data?.data
    },

    async confirmTrip(tripId, assignedAt) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/confirm`, assignedAt ? { assignedAt } : undefined)
      return data?.data
    },

    async arriveTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/arrive`)
      return data?.data
    },

    async startTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/start`)
      return data?.data
    },

    async finishTrip(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/finish`)
      return data?.data
    },

    async reportUnavailable(tripId, reason) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/report-unavailable`, { reason })
      return data?.data
    },

    async rejectTrip(tripId, rejectPayload) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(
        `/driver/trips/${encodeURIComponent(tripId)}/reject`,
        rejectPayload
      )
      return data?.data
    },

    async reportIncident(tripId, incidentPayload) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(
        `/driver/trips/${encodeURIComponent(tripId)}/incidents`,
        incidentPayload
      )
      return data?.data
    },

    async listIncidents(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.get(
        `/driver/trips/${encodeURIComponent(tripId)}/incidents`
      )
      return data?.data ?? []
    },

    async uploadEvidence(tripId, evidencePayload, options = {}) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const isFormData = typeof FormData !== 'undefined' && evidencePayload instanceof FormData
      const endpoint = isFormData
        ? `/driver/trips/${encodeURIComponent(tripId)}/evidence/upload`
        : `/driver/trips/${encodeURIComponent(tripId)}/evidence`
      // options có thể chứa onUploadProgress (tiến trình upload); các lời gọi cũ không truyền options.
      const config = options.onUploadProgress ? { onUploadProgress: options.onUploadProgress } : undefined
      const { data } = await client.post(endpoint, evidencePayload, config)
      return data?.data
    },

    async listEvidence(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.get(
        `/driver/trips/${encodeURIComponent(tripId)}/evidence`
      )
      return data?.data ?? []
    },

    async deleteEvidence(tripId, evidenceId) {
      if (!tripId || !evidenceId) throw new Error('Mã chuyến đi hoặc ID minh chứng không hợp lệ')
      const { data } = await client.delete(
        `/driver/trips/${encodeURIComponent(tripId)}/evidence/${encodeURIComponent(evidenceId)}`
      )
      return data?.data
    },

    async sendLocation(tripId, locationPayload) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.post(`/driver/trips/${encodeURIComponent(tripId)}/locations`, locationPayload)
      return data?.data
    },

    async getTripTracking(tripId) {
      if (!tripId) throw new Error('Mã chuyến đi không hợp lệ')
      const { data } = await client.get(`/driver/trips/${encodeURIComponent(tripId)}/tracking`)
      return data?.data
    },
  }
}

export const driverTripService = createDriverTripService()
