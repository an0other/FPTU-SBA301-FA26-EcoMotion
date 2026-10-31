import apiClient from './apiClient'

export const bookingWorkflowService = {
  async get(code, actor = 'renter') {
    const { data } = await apiClient.get(`/${actor}/bookings/${encodeURIComponent(code)}/workflow`)
    return data?.data
  },
  async respond(code, proposalId, action, reason) {
    const { data } = await apiClient.put(`/renter/bookings/${encodeURIComponent(code)}/operational-proposal/${encodeURIComponent(proposalId)}`, { action, reason })
    return data?.data
  },
  async requestRefund(code, amount, reason) {
    const { data } = await apiClient.post('/v1/refunds', { bookingCode: code, amount, reason })
    return data?.data
  },
  async withdraw(code, proposalId) {
    const { data } = await apiClient.delete(`/provider/bookings/${encodeURIComponent(code)}/operational-proposal/${encodeURIComponent(proposalId)}`)
    return data?.data
  },
}
