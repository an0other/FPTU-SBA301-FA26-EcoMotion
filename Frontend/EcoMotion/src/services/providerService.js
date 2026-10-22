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

  async uploadDocumentFile(file) {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post('/provider/vehicles/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return data?.data
  },

  async getProviderScheduleStats() {
    const { data } = await apiClient.get('/provider/schedules/stats')
    return data?.data
  },

  async getProviderSchedules(params = {}) {
    const { data } = await apiClient.get('/provider/schedules', { params })
    return data?.data
  },

  async createBlockSchedule(payload) {
    const { data } = await apiClient.post('/provider/schedules', payload)
    return data?.data
  },

  async cancelBlockSchedule(id) {
    const { data } = await apiClient.patch(`/provider/schedules/${id}/cancel`)
    return data?.data
  },

  async getDrivers(params = {}) {
    const { data } = await apiClient.get('/provider/drivers', { params })
    return data?.data
  },

  async getDriverStats() {
    const { data } = await apiClient.get('/provider/drivers/stats')
    return data?.data
  },

  async getDriverById(id) {
    const { data } = await apiClient.get(`/provider/drivers/${id}`)
    return data?.data
  },

  async createDriver(payload) {
    const { data } = await apiClient.post('/provider/drivers', payload)
    return data?.data
  },

  async updateDriver(id, payload) {
    const { data } = await apiClient.put(`/provider/drivers/${id}`, payload)
    return data?.data
  },

  async toggleDriverStatus(id, status) {
    const { data } = await apiClient.patch(`/provider/drivers/${id}/status`, { status })
    return data?.data
  },

  async uploadDriverImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post('/provider/drivers/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return data?.data
  },
}

