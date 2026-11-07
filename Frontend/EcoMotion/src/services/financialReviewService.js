import apiClient from './apiClient'

const value = response => response.data?.data
const annual = code => '/admin/yearly-bookings/' + encodeURIComponent(code)

export const financialReviewService = {
  refunds: () => apiClient.get('/admin/refunds').then(value),
  reconcile: (id, body) => apiClient.post('/admin/refunds/' + id + '/reconcile', body).then(value),
  refundHistory: id => apiClient.get('/admin/refunds/' + id + '/history').then(value),
  yearly: code => apiClient.get(annual(code) + '/review').then(value),
  settlement: (code, body) => apiClient.post(annual(code) + '/reviews/settlement', body).then(value),
  cancellation: (code, body) => apiClient.post(annual(code) + '/reviews/cancellation', body).then(value),
}
