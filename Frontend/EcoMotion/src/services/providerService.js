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

  async createVehicle(payload) {
    const { data } = await apiClient.post('/provider/vehicles', payload)
    return data?.data
  },

  async getVehicleById(id) {
    const { data } = await apiClient.get(`/provider/vehicles/${id}`)
    return data?.data
  },

  async updateVehicle(id, payload) {
    const { data } = await apiClient.put(`/provider/vehicles/${id}`, payload)
    return data?.data
  },

  async getVehicleRentalPlans(id) {
    const { data } = await apiClient.get(`/provider/vehicles/${id}/plans`)
    return data?.data
  },

  async updateVehicleRentalPlans(id, payload) {
    const { data } = await apiClient.put(`/provider/vehicles/${id}/plans`, payload)
    return data?.data
  },

  async getVehicleDocuments(id) {
    const { data } = await apiClient.get(`/provider/vehicles/${id}/documents`)
    return data?.data
  },

  async updateVehicleDocument(id, payload) {
    const { data } = await apiClient.put(`/provider/vehicles/${id}/documents`, payload)
    return data?.data
  },
}
