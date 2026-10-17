import apiClient from './apiClient'

export const providerService = {
  async getDashboardSummary() {
    const { data } = await apiClient.get('/provider/dashboard/summary')
    return data?.data
  },

  async getVehicles(params = {}) {
    const { data } = await apiClient.get('/provider/vehicles', { params })
    return data?.data
  },

  async toggleVehicleVisibility(id, visible) {
    const { data } = await apiClient.patch(`/provider/vehicles/${id}/visibility`, { visible })
    return data?.data
  },
}
