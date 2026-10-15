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
}
