import apiClient from './apiClient'

function reviewsPath(bookingCode) {
  return `/renter/bookings/${encodeURIComponent(bookingCode)}/reviews`
}

export const reviewService = {
  async getVehicleReviews(vehicleId) {
    const { data } = await apiClient.get(`/vehicles/${encodeURIComponent(vehicleId)}/reviews`)
    return data.data
  },

  async getDriverReviews(driverId) {
    const { data } = await apiClient.get(`/reviews/drivers/${encodeURIComponent(driverId)}`)
    return data.data
  },

  async getBookingReviews(bookingCode) {
    const { data } = await apiClient.get(reviewsPath(bookingCode))
    return data.data
  },

  async createReview(bookingCode, request) {
    const { data } = await apiClient.post(
      reviewsPath(bookingCode),
      request,
    )
    return data.data
  },

  async updateReview(bookingCode, target, request) {
    const { data } = await apiClient.put(
      `${reviewsPath(bookingCode)}/${target}`,
      request,
    )
    return data.data
  },

  async uploadImage(bookingCode, file) {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post(`${reviewsPath(bookingCode)}/images`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data?.data?.url
  },

  async deleteReview(bookingCode, target) {
    await apiClient.delete(`${reviewsPath(bookingCode)}/${target}`)
  },
}