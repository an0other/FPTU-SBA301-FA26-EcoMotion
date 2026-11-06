// Tổng hợp thanh toán theo từng loại. Chỉ dùng trường do backend trả về:
// paymentType (khóa phân loại), amount/paidAmount/remainingAmount (đã tính ở server), status, thời điểm và evidences.
// Không suy loại hay số tiền từ mô tả/ghi chú. Backend có unique (booking_id, payment_type) nên mỗi loại tối đa một request.

export const PAYMENT_CATEGORIES = [
  { type: 'DEPOSIT', label: 'Tiền đặt cọc' },
  { type: 'FINAL_BALANCE', label: 'Thanh toán cuối chuyến' },
  { type: 'MONTHLY_INSTALLMENT', label: 'Trả góp theo tháng (gói năm)' },
]

// Dữ liệu backend hiện CHƯA có (xem báo cáo audit): số kỳ, hạn của từng kỳ, thời điểm từ chối/hủy,
// và liên kết giao dịch -> loại request trong lịch sử. UI phải hiển thị "chưa có dữ liệu" cho các mục này.
export const PAYMENT_CATEGORY_MISSING_FIELDS = Object.freeze([
  'installmentPeriodNo',
  'installmentDueDate',
  'rejectedAt',
  'voidedAt',
  'transactionPaymentType',
])

const toNumberOrNull = (value) => (value == null || value === '' ? null : Number(value))

function summarizeEvidence(evidence) {
  return {
    id: evidence.id,
    proofImageUrl: evidence.proofImageUrl ?? null,
    transferredAmount: toNumberOrNull(evidence.transferredAmount),
    transactionReference: evidence.transactionReference ?? null,
    transferredAt: evidence.transferredAt ?? null,
    submittedByName: evidence.submittedByName ?? null,
    status: evidence.status ?? null,
    createdAt: evidence.createdAt ?? null,
    reviewedAt: evidence.reviewedAt ?? null,
    note: evidence.note ?? null,
  }
}

export function buildPaymentCategorySummaries(requests = []) {
  const list = Array.isArray(requests) ? requests.filter(Boolean) : []
  return PAYMENT_CATEGORIES.map(({ type, label }) => {
    const matching = list.filter((request) => request.paymentType === type)
    if (matching.length === 0) {
      return { type, label, hasRequest: false, anomaly: false, evidences: [], missingFields: PAYMENT_CATEGORY_MISSING_FIELDS }
    }
    if (matching.length > 1) {
      // Không đoán request nào đúng: đánh dấu bất thường để người vận hành xem.
      return { type, label, hasRequest: false, anomaly: true, evidences: [], missingFields: PAYMENT_CATEGORY_MISSING_FIELDS }
    }
    const request = matching[0]
    return {
      type,
      label,
      hasRequest: true,
      anomaly: false,
      requestId: request.id ?? null,
      paymentCode: request.paymentCode ?? null,
      amount: toNumberOrNull(request.amount),
      paidAmount: toNumberOrNull(request.paidAmount),
      remainingAmount: toNumberOrNull(request.remainingAmount),
      status: request.status ?? null,
      createdAt: request.createdAt ?? null,
      expiresAt: request.expiresAt ?? null,
      firstProofAt: request.firstProofAt ?? null,
      confirmedAt: request.confirmedAt ?? null,
      rejectionReason: request.rejectionReason ?? null,
      issueNote: request.issueNote ?? null,
      evidences: (request.evidences || []).map(summarizeEvidence),
      missingFields: PAYMENT_CATEGORY_MISSING_FIELDS,
    }
  })
}
