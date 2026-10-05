import { apiClient } from './apiClient'

export async function listMyBookings() {
  const { data } = await apiClient.get('/renter/bookings')
  return data.data ?? []
}

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

export async function getMyBooking(bookingCode) {
  const { data } = await apiClient.get(`/renter/bookings/${bookingCode}`)
  return data.data
}

export async function cancelMyBooking(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/cancel`)
  return data.data
}

export async function confirmTripEnd(bookingCode) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/confirm-end`)
  return data.data
}

export async function openDispute(bookingCode, body) {
  const { data } = await apiClient.post(`/renter/bookings/${bookingCode}/disputes`, body)
  return data.data
}
