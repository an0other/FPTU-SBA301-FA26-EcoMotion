import api from './apiClient'
const own = '/renter/driving-license/submissions'
const admin = '/admin/driving-license-submissions'
export const drivingLicenseReviewService = {
  async history(page = 0) { return (await api.get(own, { params: { page } })).data },
  async current() { return (await api.get(`${own}/current`)).data || null },
  async prepare(front, back, noQrDeclared, onUploadProgress, processingId) {
    const form = new FormData(); form.append('front', front); form.append('back', back); form.append('noQrDeclared', noQrDeclared)
    return (await api.post(own, form, { timeout: 420000, onUploadProgress, headers: processingId ? { 'X-Processing-ID': processingId } : {} })).data
  },
  async processing(id, signal) { return (await api.get(`${own}/processing/${id}`, { signal, timeout: 10000 })).data },
  async currentProcessing(signal) { return (await api.get(`${own}/processing/current`, { signal, timeout: 10000 })).data || null },
  async submit(row) { return (await api.post(`${own}/${row.id}/submit`, { version: row.version, confirmed: true })).data },
  async cancel(row) { return (await api.post(`${own}/${row.id}/cancel`, { version: row.version })).data },
  async queue(status, page) { return (await api.get(admin, { params: { status, page } })).data },
  async pipelineHistory(page = 0) { return (await api.get(`${own}/processing/history`, { params: { page } })).data },
  async identityImage(id, side) { return (await api.get(`${admin}/${id}/identity-images/${side}`, { responseType: 'blob' })).data },
  async applicant(id) { return (await api.get(`${admin}/${id}/applicant`)).data },
  async detail(id) { return (await api.get(`${admin}/${id}`)).data },
  async review(row, action, reason, note, document) { return (await api.post(`${admin}/${row.id}/review`, { version: row.version, action, reason, note, document })).data },
  async image(id, side, isAdmin = false) { return (await api.get(`${isAdmin ? admin : own}/${id}/images/${side}`, { responseType: 'blob' })).data },
}
