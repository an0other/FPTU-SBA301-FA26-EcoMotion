import { apiClient, getAccessToken, setAccessToken } from './apiClient'

export async function ensureRenterSession() {
  if (getAccessToken()) {
    return
  }
  const { data } = await apiClient.post('/auth/login', {
    email: 'renter01@ecomotion.vn',
    password: 'EcoMotion@123',
  })
  setAccessToken(data.accessToken)
}
