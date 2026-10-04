import apiClient from './apiClient'

export const authService = {
  async login(email, password, rememberMe = false) {
    const { data } = await apiClient.post('/auth/login', { email, password, rememberMe })
    return data
  },

  async refresh() {
    const { data } = await apiClient.post('/auth/refresh')
    return data
  },

  async fetchMe() {
    const { data } = await apiClient.get('/auth/me')
    return data
  },

  async logout() {
    await apiClient.post('/auth/logout')
  },
}
