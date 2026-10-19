import apiClient from './apiClient'

function reviewsPath(bookingCode) {
  return `/renter/bookings/${encodeURIComponent(bookingCode)}/reviews`
}

export const reviewService = {
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

  async deleteReview(bookingCode, target) {
    await apiClient.delete(`${reviewsPath(bookingCode)}/${target}`)
  },
}