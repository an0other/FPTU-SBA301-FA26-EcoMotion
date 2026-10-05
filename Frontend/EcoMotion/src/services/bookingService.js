import { apiClient } from './apiClient'

export async function listVehiclesForBooking() {
  const { data } = await apiClient.get('/renter/bookings/vehicles')
  return data.data ?? []
}

export async function createBooking(body) {
  const { data } = await apiClient.post('/renter/bookings', body)
  return data.data
}
