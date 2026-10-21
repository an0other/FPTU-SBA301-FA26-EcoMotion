import apiClient from './apiClient'

// Fallback bank list matching Backend SupportedBank enum
export const SUPPORTED_BANKS = [
  { code: 'VCB', shortName: 'Vietcombank', fullName: 'Ngân hàng TMCP Ngoại thương Việt Nam', minLength: 10, maxLength: 13 },
  { code: 'TCB', shortName: 'Techcombank', fullName: 'Ngân hàng TMCP Kỹ Thương Việt Nam', minLength: 10, maxLength: 14 },
  { code: 'MB', shortName: 'MBBank', fullName: 'Ngân hàng TMCP Quân đội', minLength: 9, maxLength: 14 },
  { code: 'BIDV', shortName: 'BIDV', fullName: 'Ngân hàng TMCP Đầu tư và Phát triển Việt Nam', minLength: 14, maxLength: 14 },
  { code: 'CTG', shortName: 'VietinBank', fullName: 'Ngân hàng TMCP Công thương Việt Nam', minLength: 12, maxLength: 12 },
  { code: 'VPB', shortName: 'VPBank', fullName: 'Ngân hàng TMCP Việt Nam Thịnh Vượng', minLength: 8, maxLength: 16 },
  { code: 'ACB', shortName: 'ACB', fullName: 'Ngân hàng TMCP Á Châu', minLength: 8, maxLength: 12 },
  { code: 'TPB', shortName: 'TPBank', fullName: 'Ngân hàng TMCP Tiên Phong', minLength: 10, maxLength: 12 },
  { code: 'STB', shortName: 'Sacombank', fullName: 'Ngân hàng TMCP Sài Gòn Thương Tín', minLength: 10, maxLength: 12 },
  { code: 'VIB', shortName: 'VIB', fullName: 'Ngân hàng TMCP Quốc tế Việt Nam', minLength: 10, maxLength: 16 },
  { code: 'SHB', shortName: 'SHB', fullName: 'Ngân hàng TMCP Sài Gòn - Hà Nội', minLength: 10, maxLength: 12 },
  { code: 'HDB', shortName: 'HDBank', fullName: 'Ngân hàng TMCP Phát triển TP.HCM', minLength: 10, maxLength: 15 },
  { code: 'MSB', shortName: 'MSB', fullName: 'Ngân hàng TMCP Hàng Hải Việt Nam', minLength: 10, maxLength: 14 },
  { code: 'OCB', shortName: 'OCB', fullName: 'Ngân hàng TMCP Phương Đông', minLength: 10, maxLength: 14 },
  { code: 'AGR', shortName: 'Agribank', fullName: 'Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam', minLength: 13, maxLength: 13 },
]

export const providerFinanceService = {
  // ==========================================
  // Provider Finance Overview & Bank Accounts
  // ==========================================

  async getFinanceSummary() {
    const { data } = await apiClient.get('/v1/provider/finance/summary')
    return data?.data ?? null
  },

  async getBankAccount() {
    const { data } = await apiClient.get('/v1/provider/finance/bank-account')
    return data?.data ?? null
  },

  async listBankAccounts() {
    const { data } = await apiClient.get('/v1/provider/finance/bank-accounts')
    return data?.data ?? []
  },

  async createBankAccount(payload) {
    const { data } = await apiClient.post('/v1/provider/finance/bank-accounts', payload)
    return data?.data
  },

  async patchBankAccount(id, payload) {
    const { data } = await apiClient.patch(`/v1/provider/finance/bank-accounts/${id}`, payload)
    return data?.data
  },

  async updateBankAccount(payload) {
    const { data } = await apiClient.put('/v1/provider/finance/bank-account', payload)
    return data?.data
  },

  // ==========================================
  // Transactions & Financial Breakdown
  // ==========================================

  async listTransactions(params = {}) {
    const query = {
      offset: params.offset ?? 0,
      limit: params.limit ?? 10,
    }
    if (params.type && params.type !== 'ALL') query.type = params.type
    if (params.page != null) query.page = params.page
    if (params.size != null) query.size = params.size

    const { data } = await apiClient.get('/v1/provider/finance/transactions', { params: query })
    return data?.data ?? { items: [], total: 0, offset: 0, limit: 10, hasMore: false }
  },

  async getBookingBreakdown(bookingId) {
    const { data } = await apiClient.get(`/v1/provider/finance/bookings/${bookingId}/breakdown`)
    return data?.data
  },

  async getCommissions() {
    const { data } = await apiClient.get('/v1/provider/finance/commissions')
    return data?.data
  },

  // ==========================================
  // Provider Payment Requests & Confirmation
  // ==========================================

  async listProviderPaymentRequests(params = {}) {
    const query = {
      offset: params.offset ?? 0,
      limit: params.limit ?? 10,
    }
    if (params.status && params.status !== 'ALL') query.status = params.status
    if (params.page != null) query.page = params.page
    if (params.size != null) query.size = params.size

    const { data } = await apiClient.get('/v1/provider/payment-requests', { params: query })
    return data?.data ?? { items: [], total: 0, offset: 0, limit: 10, hasMore: false }
  },

  async confirmPayment(paymentRequestId, payload = {}) {
    const { data } = await apiClient.post(`/v1/provider/payment-requests/${paymentRequestId}/confirm`, payload)
    return data?.data
  },

  async rejectPayment(paymentRequestId, payload) {
    const { data } = await apiClient.post(`/v1/provider/payment-requests/${paymentRequestId}/reject`, payload)
    return data?.data
  },

  async reportIssue(paymentRequestId, payload) {
    const { data } = await apiClient.post(`/v1/provider/payment-requests/${paymentRequestId}/report-issue`, payload)
    return data?.data
  },

  // ==========================================
  // Customer Payment & Proofs
  // ==========================================

  async getPaymentRequestsByBooking(bookingCode) {
    const { data } = await apiClient.get(`/v1/renter/bookings/${encodeURIComponent(bookingCode)}/payment-requests`)
    return data?.data ?? []
  },

  async getPaymentRequestDetail(id) {
    const { data } = await apiClient.get(`/v1/payment-requests/${id}`)
    return data?.data
  },

  async submitPaymentProof(id, payload) {
    const form = new FormData()
    form.append('file', payload.file)
    form.append('transferredAmount', payload.transferredAmount)
    if (payload.transactionReference) form.append('transactionReference', payload.transactionReference)
    if (payload.transferredAt) form.append('transferredAt', payload.transferredAt)
    if (payload.note) form.append('note', payload.note)
    const { data } = await apiClient.post(`/v1/payment-requests/${id}/proofs/multipart`, form)
    return data?.data
  },

  async getPaymentEvidenceImage(id) {
    const { data } = await apiClient.get(`/v1/payment-evidences/${id}/file`, { responseType: 'blob' })
    return data
  },

  async getBookingPaymentHistory(bookingId) {
    const { data } = await apiClient.get(`/v1/bookings/${bookingId}/payment-history`)
    return data?.data ?? []
  },

  // ==========================================
  // Admin Mock Sandbox
  // ==========================================

  async simulatePayment(paymentRequestId, payload) {
    const { data } = await apiClient.post(`/v1/admin/sandbox/payment-requests/${paymentRequestId}/simulate`, payload)
    return data?.data
  },

  async seedSandbox() {
    const { data } = await apiClient.post('/v1/admin/sandbox/seed')
    return data?.data
  },

  // ==========================================
  // Legacy Payouts (backward compatibility)
  // ==========================================

  async listPayouts(params = {}) {
    return this.listTransactions(params)
  },

  async getPayoutDetail(payoutId) {
    const { data } = await apiClient.get(`/v1/provider/finance/payouts/${payoutId}`)
    return data?.data
  },
}
