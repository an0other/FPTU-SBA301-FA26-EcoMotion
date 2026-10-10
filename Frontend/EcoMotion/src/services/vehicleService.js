import apiClient from './apiClient'

export const vehicleService = {
  async listVehicles({
    keyword,
    location,
    fuelType,
    planType,
    maxPrice,
    sortBy,
    offset = 0,
    limit = 6,
  } = {}) {
    const params = { offset, limit }
    if (keyword) params.keyword = keyword
    if (location) params.location = location
    if (fuelType) params.fuelType = fuelType
    if (planType) params.planType = planType
    if (maxPrice) params.maxPrice = maxPrice
    if (sortBy && sortBy !== 'default') params.sortBy = sortBy

    const { data } = await apiClient.get('/vehicles', { params })
    const page = data?.data
    return {
      items: page?.items ?? [],
      total: page?.total ?? 0,
      offset: page?.offset ?? offset,
      limit: page?.limit ?? limit,
      hasMore: page?.hasMore ?? false,
    }
  },

  async getVehicle(id) {
    const { data } = await apiClient.get(`/vehicles/${id}`)
    return data?.data ?? null
  },

}
