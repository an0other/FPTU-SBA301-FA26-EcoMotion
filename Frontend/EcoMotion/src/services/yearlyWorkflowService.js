import apiClient from './apiClient'
import { yearlyRentalService } from './yearlyRentalService'

const path = (role, code) => '/' + role + '/yearly-bookings/' + encodeURIComponent(code)
const unwrap = (response) => response.data?.data

export const yearlyWorkflowService = {
  async load(role, code) {
    const base = path(role, code)
    const [workflow, handover, payments, evidence, contract] = await Promise.all([
      apiClient.get(base + '/workflow').then(unwrap),
      apiClient.get(base + '/handover').then(unwrap),
      apiClient.get(base + '/payments').then(unwrap).catch((error) => {
        if (error.status === 404 || error.response?.status === 404) return []
        throw error
      }),
      apiClient.get('/bookings/' + encodeURIComponent(code) + '/evidences').then(unwrap),
      role === 'renter' ? yearlyRentalService.getContractStatus(code) : Promise.resolve(null),
    ])
    return { workflow, handover, payments: payments || [], evidence: evidence || [], contract }
  },
  async action(role, code, action, body = {}) {
    return unwrap(await apiClient.post(path(role, code) + '/' + action, body))
  },
  async upload(code, file) {
    if (!file || !['image/jpeg', 'image/png'].includes(file.type)) throw new Error('Chọn ảnh JPG hoặc PNG.')
    if (file.size > 8 * 1024 * 1024) throw new Error('Ảnh không được vượt quá 8 MB.')
    const form = new FormData()
    form.append('file', file)
    form.append('type', 'OTHER')
    form.append('note', 'Minh chứng hợp đồng thuê năm')
    return unwrap(await apiClient.post('/bookings/' + encodeURIComponent(code) + '/evidences/upload', form))
  },
}
