import apiClient from './apiClient'

export const locationService = {
  async route({ fromLatitude, fromLongitude, toLatitude, toLongitude }) {
    const { data } = await apiClient.get('/location/route', { params: { fromLatitude, fromLongitude, toLatitude, toLongitude } })
    if (!data?.data?.coordinates?.length) throw new Error('Chưa có tuyến ô tô hợp lệ')
    return data.data
  },
  async findNearbyVehicles({ latitude, longitude, radiusKm, keyword, location,
    fuelType, planType, maxPrice, sortBy }) {
    const params = { latitude, longitude, radiusKm }
    if (keyword) params.keyword = keyword
    if (location) params.location = location
    if (fuelType) params.fuelType = fuelType
    if (planType) params.planType = planType
    if (maxPrice) params.maxPrice = maxPrice
    if (sortBy && sortBy !== 'default') params.sortBy = sortBy
    const { data } = await apiClient.get('/location/map', { params })
    return data?.data ?? []
  },

  async reverseGeocode({ latitude, longitude }) {
    const { data } = await apiClient.get('/location/map/reverse', {
      params: { latitude, longitude },
    })
    return data?.data?.approximateAddress ?? null
  },

  async searchAddress(query) {
    const { data } = await apiClient.get('/location/map/search', { params: { query } })
    return data?.data ?? []
  },
}
