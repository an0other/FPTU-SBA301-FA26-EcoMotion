import apiClient from './apiClient'

export const paymentService = {
  async getHistory({ page = 0, size = 10 } = {}) {
    const { data } = await apiClient.get('/renter/payments', {
      params: { page, size },
    })

    return data.data
  },

  async getBookingCharges(bookingCode) {
    const { data } = await apiClient.get(
      `/renter/bookings/${encodeURIComponent(bookingCode)}/charges`,
    )

    return data.data
  },
}