import apiClient from './apiClient'

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

