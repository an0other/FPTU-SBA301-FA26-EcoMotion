import apiClient from './apiClient'

export const vehicleService = {
  /**
   * GET /api/vehicles — one offset/limit page of the approved fleet, optionally filtered.
   *
   * The backend wraps the payload in ApiResponse<T>: { code, message, data }, and `data` is the
   * paging envelope { items, total, offset, limit, hasMore }. We unwrap to that envelope so the
   * caller gets both the page items and the counters needed to render pagination.
   */
  async listVehicles({ keyword, location, fuelType, planType, maxPrice, offset = 0, limit = 6 } = {}) {
    const params = { offset, limit }
    if (keyword) params.keyword = keyword
    if (location) params.location = location
    if (fuelType) params.fuelType = fuelType
    if (planType) params.planType = planType
    if (maxPrice) params.maxPrice = maxPrice

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
}
