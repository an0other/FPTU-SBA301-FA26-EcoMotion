import apiClient from './apiClient'

export const providerBookingService = {
  // status === 'ALL' / empty -> omit the param so the backend returns every booking.
  async listBookings(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status

    const { data } = await apiClient.get('/provider/bookings', { params })
    return data?.data ?? []
  },
}
