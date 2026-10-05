import { apiClient } from './apiClient'

export async function listVehiclesForBooking() {
  const { data } = await apiClient.get('/renter/bookings/vehicles')
  return data.data ?? []
}

export async function createBooking(body) {
  const { data } = await apiClient.post('/renter/bookings', body)
  return data.data
}

export async function checkoutPreview(bookingCode) {
  const { data } = await apiClient.get(`/renter/bookings/${bookingCode}/checkout`)
  return data.data
}

export async function checkout(bookingCode, method) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/checkout`, { method })
  return data.data
}
