import apiClient from './apiClient.js'

export const paymentService = {
  async getVietQrCallbacks(page, signal) {
    const { data } = await apiClient.get('/v1/provider/vietqr/callbacks', { params: { page }, signal })
    return data?.data
  },
  async createVietQr(paymentRequestId) {
    const { data } = await apiClient.post('/v1/payments/vietqr/create', { paymentRequestId })
    return data?.data
  },

  async getVietQrStatus(paymentCode, signal) {
    const { data } = await apiClient.get(`/v1/payments/${encodeURIComponent(paymentCode)}/status`, { signal })
    return data?.data
  },

  async getReceipt(reference, signal) {
    const { data } = await apiClient.get('/v1/payments/receipt', { params: { reference }, signal })
    return data?.data
  },

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

  async processVnpayCallback(params, signal) {
    const { data } = await apiClient.get('/v1/payment/vnpay/callback', { params, signal })
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

  async queryVnpayTransaction(bookingCode, vnpTxnRef, signal) {
    const { data } = await apiClient.get('/v1/payment/vnpay/query', {
      params: { bookingCode, vnpTxnRef },
      signal,
    })
    return data?.data
  },
}
