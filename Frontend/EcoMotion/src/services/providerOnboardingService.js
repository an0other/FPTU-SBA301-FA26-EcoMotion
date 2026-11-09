import apiClient from './apiClient'

export const providerOnboardingService = {
  async get() {
    return (await apiClient.get('/provider/onboarding')).data
  },
  async options() {
    return (await apiClient.get('/provider/onboarding/options')).data
  },
  async save(profile) {
    const payload = { ...profile }
    for (const [field, limit] of [['latitude', 90], ['longitude', 180]]) {
      const value = profile[field]
      const coordinate = Number(value)
      if (value == null || value === '' || !Number.isFinite(coordinate) || Math.abs(coordinate) > limit) {
        throw new Error(field === 'latitude' ? 'Vĩ độ không hợp lệ, phải nằm từ -90 đến 90.' : 'Kinh độ không hợp lệ, phải nằm từ -180 đến 180.')
      }
      payload[field] = Number(coordinate.toFixed(6))
    }
    return (await apiClient.put('/provider/onboarding', payload)).data
  },
  async submit(version) {
    return (await apiClient.post('/provider/onboarding/submit', { version })).data
  },
  async sendPhoneOtp(phone) {
    return (await apiClient.post('/provider/onboarding/phone/send-otp', { phone })).data
  },
  async verifyPhoneOtp(otp) {
    return (await apiClient.post('/provider/onboarding/phone/verify-otp', { otp })).data
  },
  async pending(page = 0, status = 'SUBMITTED') {
    return (await apiClient.get('/admin/provider-applications', { params: { page, status } })).data
  },
  async detail(userId) {
    return (await apiClient.get(`/admin/provider-applications/${userId}`)).data
  },
  async review(userId, version, decision, reasonCode) {
    return (await apiClient.post(`/admin/provider-applications/${userId}/review`, { version, decision, reasonCode })).data
  },
  async history(userId) {
    return (await apiClient.get(`/admin/provider-applications/${userId}/history`)).data
  },
}

export default providerOnboardingService
