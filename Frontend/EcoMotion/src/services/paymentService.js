import apiClient from './apiClient.js'

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

  async createVnpayPaymentUrl(payload) {
    const { data } = await apiClient.post('/v1/payment/vnpay/create-url', payload)
    return data?.data
  },

  async processVnpayCallback(params) {
    const { data } = await apiClient.get('/v1/payment/vnpay/callback', { params })
    return data?.data
  },

  async getSettlement(bookingCode) {
    const { data } = await apiClient.get(
      `/renter/bookings/${encodeURIComponent(bookingCode)}/settlement`,
    )
    return data?.data
  },

  async createFinalPaymentUrl(bookingCode) {
    const { data } = await apiClient.post(
      `/renter/bookings/${encodeURIComponent(bookingCode)}/payments/final/create`,
    )
    return data?.data
  },

  async queryVnpayTransaction(bookingCode, vnpTxnRef) {
    const { data } = await apiClient.get('/v1/payment/vnpay/query', {
      params: { bookingCode, vnpTxnRef },
    })
    return data?.data
  },
}