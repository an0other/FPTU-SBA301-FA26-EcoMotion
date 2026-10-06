import apiClient from './apiClient'

export const authService = {
  async login(email, password, rememberMe = false, captchaToken = '') {
    const { data } = await apiClient.post('/auth/login', { email, password, rememberMe, captchaToken })
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

  // ---------------------------------------------------------------- registration

  async sendRegisterOtp(email) {
    const { data } = await apiClient.post('/auth/register/send-otp', { email })
    return data
  },

  async verifyRegisterOtp(email, otp) {
    const { data } = await apiClient.post('/auth/register/verify-otp', { email, otp })
    return data
  },

  async register(payload) {
    const { data } = await apiClient.post('/auth/register', payload)
    return data
  },

  // ---------------------------------------------------------------- activation

  async activateStatus(activationToken) {
    const { data } = await apiClient.post('/auth/activate/status', { activationToken })
    return data
  },

  async activateSendPhoneOtp(activationToken, phone) {
    const { data } = await apiClient.post('/auth/activate/send-phone-otp', { activationToken, phone })
    return data
  },

  async activateVerifyPhoneOtp(activationToken, otp) {
    const { data } = await apiClient.post('/auth/activate/verify-phone-otp', { activationToken, otp })
    return data
  },

  async activateScanCccd(activationToken, { cccdFront, cccdBack }) {
    const form = new FormData()
    form.append('activationToken', activationToken)
    form.append('cccdFront', cccdFront)
    form.append('cccdBack', cccdBack)
    // Content-Type is set by the browser (with the multipart boundary) — see apiClient.
    const { data } = await apiClient.post('/auth/activate/scan-cccd', form)
    return data
  },

  async activateFaceMatch(activationToken, portrait) {
    const form = new FormData()
    form.append('activationToken', activationToken)
    form.append('portrait', portrait)
    const { data } = await apiClient.post('/auth/activate/face-match', form)
    return data
  },

  // ---------------------------------------------------------------- resume activation

  async activateResumeOtp(email) {
    const { data } = await apiClient.post('/auth/activate/resume-otp', { email })
    return data
  },

  async activateResume(email, otp) {
    const { data } = await apiClient.post('/auth/activate/resume', { email, otp })
    return data
  },
}
