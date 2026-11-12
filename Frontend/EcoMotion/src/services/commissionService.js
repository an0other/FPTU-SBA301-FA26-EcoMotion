import apiClient from './apiClient'

export const commissionService = {
  async providerOverview() {
    const { data } = await apiClient.get('/provider/finance/commission-statements')
    return data?.data
  },

  async providerStatement(id) {
    const { data } = await apiClient.get(`/provider/finance/commission-statements/${id}`)
    return data?.data
  },

  async reportPayment(id, { amount, paymentReference, note, file } = {}) {
    const form = new FormData()
    if (amount) form.append('amount', amount)
    if (paymentReference) form.append('paymentReference', paymentReference)
    if (note) form.append('note', note)
    if (file) form.append('file', file)
    const { data } = await apiClient.post(`/provider/finance/commission-statements/${id}/report-payment`, form)
    return data?.data
  },

  async providerProof(id, paymentId) {
    const { data } = await apiClient.get(`/provider/finance/commission-statements/${id}/payments/${paymentId}/proof`, { responseType: 'blob' })
    return data
  },

  async adminProof(id, paymentId) {
    const { data } = await apiClient.get(`/admin/commission-statements/${id}/payments/${paymentId}/proof`, { responseType: 'blob' })
    return data
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
