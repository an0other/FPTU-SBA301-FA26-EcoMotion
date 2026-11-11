import apiClient from './apiClient'

/** Hoa hồng tháng: nhà xe xem/nộp, admin lập bảng kê và xác nhận đã nhận. */
export const commissionService = {
  async providerOverview() {
    const { data } = await apiClient.get('/provider/finance/commission-statements')
    return data?.data
  },

  async providerStatement(id) {
    const { data } = await apiClient.get(`/provider/finance/commission-statements/${id}`)
    return data?.data
  },

  async reportPayment(id, payload = {}) {
    const { data } = await apiClient.post(`/provider/finance/commission-statements/${id}/report-payment`, payload)
    return data?.data
  },

  async adminList({ month, status } = {}) {
    const params = {}
    if (month) params.month = month
    if (status && status !== 'ALL') params.status = status
    const { data } = await apiClient.get('/admin/commission-statements', { params })
    return data?.data ?? []
  },

  async adminDetail(id) {
    const { data } = await apiClient.get(`/admin/commission-statements/${id}`)
    return data?.data
  },

  async confirm(id, receivedAmount, note) {
    const { data } = await apiClient.post(`/admin/commission-statements/${id}/confirm`, { receivedAmount, note })
    return data?.data
  },

  async rejectReport(id, note) {
    const { data } = await apiClient.post(`/admin/commission-statements/${id}/reject-report`, { note })
    return data?.data
  },

  async generate(month) {
    const { data } = await apiClient.post('/admin/commission-statements/generate', null, { params: month ? { month } : {} })
    return data?.data
  },
}
