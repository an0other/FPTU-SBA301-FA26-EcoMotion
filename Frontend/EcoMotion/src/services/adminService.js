import apiClient from './apiClient'

export const adminService = {
  // A01 — Dashboard Stats
  async getDashboardStats() {
    const { data } = await apiClient.get('/admin/dashboard/stats')
    return data?.data ?? null
  },

  // A02 — User Management & Approvals
  async getUsers({ role, status, keyword } = {}) {
    const params = {}
    if (role && role !== 'ALL') params.role = role
    if (status && status !== 'ALL') params.status = status
    if (keyword) params.keyword = keyword
    const { data } = await apiClient.get('/admin/users', { params })
    return data?.data ?? []
  },

  async updateUserStatus(id, status) {
    const { data } = await apiClient.patch(`/admin/users/${id}/status`, { status })
    return data?.data ?? null
  },

  async getProviders(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/admin/providers', { params })
    return data?.data ?? []
  },

  async updateProviderStatus(id, status) {
    const { data } = await apiClient.patch(`/admin/providers/${id}/status`, { status })
    return data?.data ?? null
  },

  async getDrivers(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/admin/drivers', { params })
    return data?.data ?? []
  },

  async updateDriverStatus(id, status, rejectReason = '') {
    const { data } = await apiClient.patch(`/admin/drivers/${id}/status`, { status, rejectReason })
    return data?.data ?? null
  },

  async getEkyc(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/admin/ekyc', { params })
    return data?.data ?? []
  },

  async updateEkycStatus(userId, status) {
    const { data } = await apiClient.patch(`/admin/ekyc/${userId}/status`, { status })
    return data?.data ?? null
  },

  // A03 — Vehicle Approval
  async getVehicles({ status, keyword } = {}) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    if (keyword) params.keyword = keyword
    const { data } = await apiClient.get('/admin/vehicles', { params })
    return data?.data ?? []
  },

  async getVehicleDetail(id) {
    const { data } = await apiClient.get(`/admin/vehicles/${id}`)
    return data?.data ?? null
  },

  async updateVehicleApproval(id, status, reason = '') {
    const { data } = await apiClient.patch(`/admin/vehicles/${id}/approval`, { status, reason })
    return data?.data ?? null
  },

  // A04 — Operations: Bookings, Disputes, Payments
  async getBookings({ status, keyword } = {}) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    if (keyword) params.keyword = keyword
    const { data } = await apiClient.get('/admin/operations/bookings', { params })
    return data?.data ?? []
  },

  async getBookingDetail(id) {
    const { data } = await apiClient.get(`/admin/operations/bookings/${id}`)
    return data?.data ?? null
  },

  async getDisputes(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/admin/operations/disputes', { params })
    return data?.data ?? []
  },

  async resolveDispute(id, status, resolution) {
    const { data } = await apiClient.patch(`/admin/operations/disputes/${id}`, { status, resolution })
    return data?.data ?? null
  },

  async getPayments() {
    const { data } = await apiClient.get('/admin/operations/payments')
    return data?.data ?? []
  },

  async refundBooking(bookingId, amount, reason) {
    const { data } = await apiClient.post('/admin/operations/refund', { bookingId, amount, reason })
    return data?.data ?? null
  },
}
