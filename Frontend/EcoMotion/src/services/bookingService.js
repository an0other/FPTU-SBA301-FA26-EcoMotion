import { apiClient } from './apiClient'

export async function listMyBookings() {
  const { data } = await apiClient.get('/renter/bookings')
  return data.data ?? []
}

export async function listVehiclesForBooking() {
  const { data } = await apiClient.get('/renter/bookings/vehicles')
  return data.data ?? []
}

export async function createBooking(body) {
  const { data } = await apiClient.post('/renter/bookings', body)
  return data.data
}

export async function getMyBooking(bookingCode) {
  const { data } = await apiClient.get(`/renter/bookings/${bookingCode}`)
  return data.data
}

export async function cancelMyBooking(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/cancel`)
  return data.data
}

export async function confirmTripEnd(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/confirm-end`)
  return data.data
}

export async function openDispute(bookingCode, body) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/disputes`, body)
  return data.data
}

export const bookingService = {
  async getProviderBookings(status) {
    const params = {}
    if (status && status !== 'ALL') {
      params.status = status
    }
    const { data } = await apiClient.get('/provider/bookings', { params })
    return data.data || data
  },

  async getProviderBookingDetail(bookingId) {
    const { data } = await apiClient.get(`/provider/bookings/${bookingId}`)
    return data.data || data
  },

  async acceptBooking(bookingId) {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingId}/accept`)
    return data.data || data
  },

  async rejectBooking(bookingId, payload) {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingId}/reject`, payload)
    return data.data || data
  },

  async getAvailableDrivers(bookingId) {
    const { data } = await apiClient.get('/provider/drivers/available', {
      params: { bookingId },
    })
    return data.data || data
  },

  async assignDriver(bookingId, driverId) {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingId}/driver`, {
      driverId,
    })
    return data.data || data
  },

  async addManualCharge(bookingId, payload) {
    const { data } = await apiClient.post(`/provider/bookings/${bookingId}/charges`, payload)
    return data.data || data
  },

  async removeManualCharge(bookingId, chargeId) {
    const { data } = await apiClient.delete(`/provider/bookings/${bookingId}/charges/${chargeId}`)
    return data.data || data
  },
}
