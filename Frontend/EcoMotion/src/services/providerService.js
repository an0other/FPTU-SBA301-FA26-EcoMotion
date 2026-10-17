import apiClient from './apiClient'

export const providerService = {
  async getDashboardSummary() {
    const { data } = await apiClient.get('/provider/dashboard/summary')
    return data?.data
  },
}
