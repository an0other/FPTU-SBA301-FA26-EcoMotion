import { apiClient } from './apiClient.js'

function bookingPath(bookingCode) {
  return encodeURIComponent(String(bookingCode ?? '').trim().toUpperCase())
}

export async function listMyBookings({ group = 'ALL', keyword = '', page = 0, size = 10 } = {}) {
  const params = { page, size }
  if (group && group !== 'ALL') params.group = group
  if (keyword.trim()) params.keyword = keyword.trim()
  const { data } = await apiClient.get('/renter/bookings', { params })
  return data.data ?? { items: [], total: 0, offset: 0, limit: size, hasMore: false }
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
  const { data } = await apiClient.get(`/renter/bookings/${bookingPath(bookingCode)}`)
  return data.data
}

export async function getMyBookingTracking(bookingCode) {
  const { data } = await apiClient.get(`/renter/bookings/${bookingPath(bookingCode)}/tracking`)
  return data.data
}

export async function getMyBookingOperationalRisk(bookingCode) {
  const { data } = await apiClient.get(`/renter/bookings/${bookingPath(bookingCode)}/operational-risk`)
  return data.data
}

export const getBookingDetail = getMyBooking

export async function updateBookingRequestDetails(bookingCode, body) {
  const { data } = await apiClient.patch(`/renter/bookings/${bookingPath(bookingCode)}/request-details`, body)
  return data.data
}

export async function cancelMyBooking(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingPath(bookingCode)}/cancel`)
  return data.data
}

export async function confirmTripEnd(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingPath(bookingCode)}/confirm-end`)
  return data.data
}

export async function confirmTripStart(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingPath(bookingCode)}/confirm-start`)
  return data.data
}

export async function openDispute(bookingCode, body) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingPath(bookingCode)}/disputes`, body)
  return data.data
}

export async function uploadDisputeEvidence(bookingCode, file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/disputes/upload-evidence`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data?.data?.url
}

export async function createVnpayPaymentUrl(payload) {
  const { data } = await apiClient.post('/v1/payment/vnpay/create-url', payload)
  return data?.data
}

export const bookingService = {
  // Renter methods
  getMyBooking,
  getBookingDetail: getMyBooking,
  listMyBookings,
  listVehiclesForBooking,
  createBooking,
  updateBookingRequestDetails,
  cancelMyBooking,
  confirmTripEnd,
  openDispute,
  uploadDisputeEvidence,
  createVnpayPaymentUrl,

  // Provider methods
  async getProviderBookings(status) {
    const params = {}
    if (status && status !== 'ALL') {
      params.status = status
    }
    const { data } = await apiClient.get('/provider/bookings', { params })
    return data.data || data
  },

  async getProviderBookingDetail(bookingCode) {
    const { data } = await apiClient.get(`/provider/bookings/${bookingPath(bookingCode)}`)
    return data.data || data
  },

  async acceptBooking(bookingCode) {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingPath(bookingCode)}/accept`)
    return data.data || data
  },

  async rejectBooking(bookingCode, payload) {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingPath(bookingCode)}/reject`, payload)
    return data.data || data
  },

  async getAvailableDrivers(bookingCode) {
    const { data } = await apiClient.get('/provider/drivers/available', {
      params: { bookingCode: String(bookingCode ?? '').trim().toUpperCase() },
    })
    return data.data || data
  },

  async assignDriver(bookingCode, driverId, mode = 'MANUAL') {
    const { data } = await apiClient.patch(`/provider/bookings/${bookingPath(bookingCode)}/driver`, {
      driverId,
      mode,
    })
    return data.data || data
  },

  async addManualCharge(bookingCode, payload) {
    const { data } = await apiClient.post(`/provider/bookings/${bookingPath(bookingCode)}/charges`, payload)
    return data.data || data
  },

  async removeManualCharge(bookingCode, chargeId) {
    const { data } = await apiClient.delete(`/provider/bookings/${bookingPath(bookingCode)}/charges/${chargeId}`)
    return data.data || data
  },
}
