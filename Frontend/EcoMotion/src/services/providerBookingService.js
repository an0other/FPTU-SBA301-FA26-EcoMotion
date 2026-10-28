import apiClient from './apiClient.js'

export const providerBookingService = {
  async listBookings(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/provider/bookings', { params })
    return data?.data ?? []
  },

  async listBookingsPaged(status, offset = 0, limit = 10) {
    const params = { offset, limit }
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/provider/bookings/paged', { params })
    return data?.data ?? { items: [], total: 0, offset: 0, limit: 10, hasMore: false }
  },

  async getSummary() {
    const { data } = await apiClient.get('/provider/bookings/summary')
    return data?.data ?? {
      total: 0,
      pending: 0,
      accepted: 0,
      confirmed: 0,
      inProgress: 0,
      completed: 0,
      rejected: 0,
      cancelled: 0,
    }
  },

  async getBookingDetail(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}`)
    return data?.data
  },

  async acceptBooking(idOrCode) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/accept`)
    return data?.data
  },

  async denyBooking(idOrCode, reason) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/deny`, { reason })
    return data?.data
  },

  async assignDriver(idOrCode, driverId) {
    const { data } = await apiClient.put(`/provider/bookings/${encodeURIComponent(idOrCode)}/driver`, { driverId })
    return data?.data
  },

  async getCharges(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/charges`)
    return data?.data ?? []
  },

  async addCharge(idOrCode, chargeData) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/charges`, chargeData)
    return data?.data
  },

  async finalizeSettlement(idOrCode) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/settlement/finalize`)
    return data?.data
  },

  async getAvailableDrivers(bookingId) {
    if (bookingId) {
      const candidates = await this.getAssignmentCandidates(bookingId)
      return candidates.map(driver => ({ ...driver, id: driver.driverId }))
    }
    const { data } = await apiClient.get('/provider/drivers/available')
    return data?.data ?? []
  },

  async getAssignmentCandidates(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/assign-candidates`)
    return data?.data ?? []
  },

  async suggestDriverAssignment(idOrCode) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/assign-suggest`)
    return data?.data
  },

  async autoAssignDriver(idOrCode) {
    const { data } = await apiClient.post(`/provider/bookings/${encodeURIComponent(idOrCode)}/auto-assign`)
    return data?.data
  },

  async getBookingIncidents(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/incidents`)
    return data?.data ?? []
  },

  async resolveIncident(idOrCode, incidentId, payload) {
    const { data } = await apiClient.post(
      `/provider/bookings/${encodeURIComponent(idOrCode)}/incidents/${encodeURIComponent(incidentId)}/resolve`,
      payload
    )
    return data?.data
  },

  async getBookingEvidence(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/evidence`)
    return data?.data ?? []
  },

  async uploadBookingEvidence(idOrCode, formData) {
    const { data } = await apiClient.post(
      `/provider/bookings/${encodeURIComponent(idOrCode)}/evidence/upload`,
      formData
    )
    return data?.data
  },

  async deleteBookingEvidence(idOrCode, evidenceId) {
    const { data } = await apiClient.delete(
      `/provider/bookings/${encodeURIComponent(idOrCode)}/evidence/${encodeURIComponent(evidenceId)}`
    )
    return data?.data
  },

  async exportBookings(status) {
    const params = {}
    if (status && status !== 'ALL') params.status = status
    const response = await apiClient.get('/provider/bookings/export', {
      params,
      responseType: 'blob',
    })

    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.setAttribute('href', url)
    link.setAttribute('download', `danh-sach-don-thue-${new Date().toISOString().slice(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  },

  async getTripTracking(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/tracking`)
    return data?.data
  },

  async getTripLocation(idOrCode) {
    const { data } = await apiClient.get(`/provider/bookings/${encodeURIComponent(idOrCode)}/location`)
    return data?.data
  },
}
