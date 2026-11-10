import apiClient from './apiClient'

export const bookingWorkflowService = {
  async get(code, actor = 'renter') {
    const { data } = await apiClient.get(`/${actor}/bookings/${encodeURIComponent(code)}/workflow`)
    return data?.data
  },
  // B1: gửi acknowledgeLowerQuality khi khách đồng ý đổi sang xe chất lượng thấp hơn.
  async respond(code, proposalId, action, reason, acknowledgeLowerQuality = false) {
    const { data } = await apiClient.put(`/renter/bookings/${encodeURIComponent(code)}/operational-proposal/${encodeURIComponent(proposalId)}`, { action, reason, acknowledgeLowerQuality })
    return data?.data
  },

  async withdraw(code, proposalId) {
    const { data } = await apiClient.delete(`/provider/bookings/${encodeURIComponent(code)}/operational-proposal/${encodeURIComponent(proposalId)}`)
    return data?.data
  },
}
