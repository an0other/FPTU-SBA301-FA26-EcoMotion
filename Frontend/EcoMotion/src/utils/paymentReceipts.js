const REQUEST_LABELS = {
  DEPOSIT: 'Tiền cọc',
  FINAL_BALANCE: 'Quyết toán',
  MONTHLY_INSTALLMENT: 'Kỳ thuê tháng',
  ADJUSTMENT: 'Điều chỉnh',
}

const OFFSET_BLOCKED = new Set(['AWAITING_FINAL_PAYMENT', 'COMPLETED', 'CANCELLED', 'CANCELLATION_REVIEW'])

export function requestLabel(type) {
  return REQUEST_LABELS[type] || 'Thanh toán'
}

export function buildPaymentReceipts(transactions = []) {
  const groups = new Map()
  transactions
    .filter((tx) => tx.direction === 'IN' && tx.status === 'PAID' && tx.paymentRequestId != null)
    .forEach((tx) => {
      const key = tx.paymentRequestId
      const current = groups.get(key) || {
        paymentRequestId: key,
        label: requestLabel(tx.paymentRequestType),
        amount: 0,
        lastPaidAt: null,
        gateway: false,
        adminAdjusted: false,
      }
      current.amount += Number(tx.amount) || 0
      if (!current.lastPaidAt || (tx.paidAt && tx.paidAt > current.lastPaidAt)) current.lastPaidAt = tx.paidAt
      if (tx.method === 'VNPAY') current.gateway = true
      if (tx.verificationMethod === 'ADMIN_RECONCILED') current.adminAdjusted = true
      groups.set(key, current)
    })
  return [...groups.values()].sort((a, b) => String(a.lastPaidAt).localeCompare(String(b.lastPaidAt)))
}

export function excessRefundViews(refunds = [], bookingStatus) {
  return refunds
    .filter((refund) => refund.excess)
    .map((refund) => {
      const offsetDone = refund.status === 'REJECTED' && /cấn trừ/i.test(refund.rejectionReason || '')
      const refunded = refund.status === 'REFUNDED'
      const pending = refund.status === 'PROCESSING' || refund.status === 'UNDER_REVIEW' || refund.status === 'REQUESTED'
      return {
        id: refund.id,
        amount: Number(refund.amount) || 0,
        pending,
        refunded,
        offsetDone,
        canOffset: refund.status === 'PROCESSING' && !OFFSET_BLOCKED.has(bookingStatus),
        statusText: offsetDone
          ? 'Đã cấn trừ vào quyết toán'
          : refunded
            ? 'Nhà xe đã chuyển hoàn — kiểm tra tài khoản của bạn'
            : pending ? 'Chờ nhà xe chuyển hoàn' : 'Đã đóng',
      }
    })
    .filter((view) => view.pending || view.refunded || view.offsetDone)
}

export function providerPaymentState(request) {
  switch (request.status) {
    case 'PROOF_SUBMITTED':
      return { text: 'Khách đã báo chuyển khoản — cần bạn đối chiếu sao kê và xác nhận', tone: 'amber', action: true }
    case 'PARTIALLY_PAID':
      return { text: 'Đã nhận một phần — chờ khách chuyển thêm', tone: 'amber', action: false }
    case 'CONFIRMED':
      return { text: 'Đã nhận đủ', tone: 'emerald', action: false }
    case 'REVIEW_REQUIRED':
      return { text: 'Cần đối soát — đã báo sai lệch / chờ quản trị viên', tone: 'rose', action: false }
    case 'PENDING':
      return { text: 'Chờ khách chuyển khoản', tone: 'slate', action: false }
    case 'EXPIRED':
      return { text: 'Hết hạn — khách chưa thanh toán', tone: 'slate', action: false }
    case 'REJECTED':
      return { text: 'Bạn đã từ chối biên lai — chờ khách gửi lại', tone: 'slate', action: false }
    case 'VOIDED':
      return { text: 'Đã huỷ', tone: 'slate', action: false }
    default:
      return { text: request.status, tone: 'slate', action: false }
  }
}

export function displayRefundReason(reason) {
  return String(reason || '').replace(/^EXCESS:\s*/, '')
}
