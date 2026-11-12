import apiClient from './apiClient'

function assessmentForm({ cccdFront, cccdBack, video, activationToken }) {
  const form = new FormData()
  form.append('cccdFront', cccdFront)
  form.append('cccdBack', cccdBack)
  form.append('video', video)
  if (activationToken) form.append('activationToken', activationToken)
  return form
}

export const identityVerificationService = {
  async constraints() {
    return (await apiClient.get('/identity/constraints')).data
  },

  async status() {
    return (await apiClient.get('/identity/status')).data
  },

  async activationStatus(activationToken) {
    return (await apiClient.post('/auth/activate/identity-status', { activationToken })).data
  },

  async requestLink(targetEmail, password) {
    return (await apiClient.post('/identity/links', { targetEmail, password })).data
  },

  async links(page = 0) {
    return (await apiClient.get('/identity/links', { params: { page } })).data
  },

  async confirmLink(id, version, password) {
    return (await apiClient.post(`/identity/links/${id}/confirm`, { version, password })).data
  },

  async cancelLink(id, version) {
    return (await apiClient.post(`/identity/links/${id}/cancel`, { version })).data
  },

  async activationLinks(activationToken, page = 0) {
    return (await apiClient.post('/auth/activate/identity-links/current', { activationToken, page })).data
  },

  async confirmActivationLink(activationToken, id, version, password) {
    return (await apiClient.post(`/auth/activate/identity-links/${id}/confirm`, { activationToken, version, password })).data
  },

  async cancelActivationLink(activationToken, id, version) {
    return (await apiClient.post(`/auth/activate/identity-links/${id}/cancel`, { activationToken, version })).data
  },
  async submitAttempt(id, version, confirmed) {
    return (await apiClient.post(`/identity/attempts/${id}/submit`, { version, confirmed })).data
  },

  async submitActivationAttempt(activationToken, id, version, confirmed) {
    return (await apiClient.post(`/auth/activate/identity-attempts/${id}/submit`,
      { activationToken, version, confirmed })).data
  },

  async pendingRenterIdentities(page = 0, status = 'SUBMITTED') {
    return (await apiClient.get('/admin/identity-attempts', { params: { page, status } })).data
  },

  async providerIdentityDetail(id) {
    return (await apiClient.get(`/admin/identity-attempts/${id}`)).data
  },

  async reviewRenterIdentity(id, version, decision, reasonCode, note) {
    return (await apiClient.post(`/admin/identity-attempts/${id}/review`,
      { version, decision, reasonCode, note })).data
  },

  async downloadHistoryEvidence(id, eventId, kind) {
    return (await apiClient.get(`/admin/identity-attempts/${id}/history/${eventId}/evidence/${kind}`, { responseType: 'blob' })).data
  },
  async downloadProviderIdentityEvidence(id, kind) {
    return (await apiClient.get(`/admin/identity-attempts/${id}/evidence/${kind}`, { responseType: 'blob' })).data
  },
  async processing(id, activationToken, signal) {
    const options = { timeout: 10000, signal }
    return activationToken
      ? (await apiClient.post(`/auth/activate/identity-attempts/${id}/progress`, { activationToken }, options)).data
      : (await apiClient.get(`/identity/attempts/${id}/progress`, options)).data
  },
  async attemptHistory(page = 0, activationToken) {
    return activationToken
      ? (await apiClient.post('/auth/activate/identity-attempts/history', { activationToken }, { params: { page } })).data
      : (await apiClient.get('/identity/attempts/history', { params: { page } })).data
  },
  async ownHistoryEvidence(id, eventId, kind, activationToken, signal) {
    const options = { responseType: 'blob', signal }
    return activationToken
      ? (await apiClient.post(`/auth/activate/identity-attempts/${id}/history/${eventId}/evidence/${kind}`, { activationToken }, options)).data
      : (await apiClient.get(`/identity/attempts/${id}/history/${eventId}/evidence/${kind}`, options)).data
  },
  async createAttempt() {
    return (await apiClient.post('/identity/attempts')).data
  },

  async currentAttempt() {
    return (await apiClient.get('/identity/attempts/current')).data
  },

  async getAttempt(id) {
    return (await apiClient.get(`/identity/attempts/${id}`)).data
  },

  async uploadDocuments(id, version, { cccdFront, cccdBack }, onUploadProgress) {
    const form = new FormData()
    form.append('version', version)
    form.append('cccdFront', cccdFront)
    form.append('cccdBack', cccdBack)
    return (await apiClient.post(`/identity/attempts/${id}/documents`, form, { timeout: 420000, onUploadProgress })).data
  },

  async uploadVideo(id, version, video, onUploadProgress) {
    const form = new FormData()
    form.append('version', version)
    form.append('video', video)
    return (await apiClient.post(`/identity/attempts/${id}/video`, form, { timeout: 420000, onUploadProgress })).data
  },

  async cancelAttempt(id, version) {
    return (await apiClient.post(`/identity/attempts/${id}/cancel`, { version })).data
  },

  async downloadEvidence(id, kind, signal) {
    return (await apiClient.get(`/identity/attempts/${id}/evidence/${kind}`, { responseType: 'blob', signal })).data
  },

  async activationAttempt(activationToken, action = '', id) {
    const suffix = id ? `/${id}/status` : action === 'current' ? '/current' : ''
    return (await apiClient.post(`/auth/activate/identity-attempts${suffix}`, { activationToken })).data
  },

  async uploadActivationDocuments(activationToken, id, version, { cccdFront, cccdBack }, onUploadProgress) {
    const form = new FormData()
    form.append('activationToken', activationToken)
    form.append('version', version)
    form.append('cccdFront', cccdFront)
    form.append('cccdBack', cccdBack)
    return (await apiClient.post(`/auth/activate/identity-attempts/${id}/documents`, form, { timeout: 420000, onUploadProgress })).data
  },

  async uploadActivationVideo(activationToken, id, version, video, onUploadProgress) {
    const form = new FormData()
    form.append('activationToken', activationToken)
    form.append('version', version)
    form.append('video', video)
    return (await apiClient.post(`/auth/activate/identity-attempts/${id}/video`, form, { timeout: 420000, onUploadProgress })).data
  },

  async cancelActivationAttempt(activationToken, id, version) {
    return (await apiClient.post(`/auth/activate/identity-attempts/${id}/cancel`, { activationToken, version })).data
  },

  async downloadActivationEvidence(activationToken, id, kind, signal) {
    return (await apiClient.post(`/auth/activate/identity-attempts/${id}/evidence/${kind}`,
      { activationToken }, { responseType: 'blob', signal })).data
  },

  async assess(files) {
    const { data } = await apiClient.post('/identity/assessments', assessmentForm(files), {
      timeout: 420000,
    })
    return data
  },

  async assessActivation(activationToken, files) {
    const { data } = await apiClient.post(
      '/auth/activate/identity-assessment',
      assessmentForm({ ...files, activationToken }),
      { timeout: 420000 },
    )
    return data
  },
}
