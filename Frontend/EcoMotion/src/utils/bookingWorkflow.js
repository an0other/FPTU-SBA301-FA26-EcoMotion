export const WORKFLOW_LABELS = {
  UNPAID: 'Chưa ghi nhận thanh toán', VERIFYING: 'Đang xác minh thanh toán',
  RECONCILIATION_REQUIRED: 'Đã nhận tiền, cần đối soát', REFUND_PENDING: 'Đang xử lý hoàn tiền',
  REFUNDED: 'Đã ghi nhận hoàn tiền', PARTIALLY_PAID: 'Đã thanh toán một phần', COVERED: 'Đã ghi nhận đủ chi phí hiện tại',
  DEPOSIT_PAID: 'Đã đặt cọc', FULLY_PAID: 'Đã tất toán', FAILED: 'Thanh toán chưa thành công',
  NOT_ASSIGNED: 'Chưa có tài xế', PENDING_RESPONSE: 'Đã gửi yêu cầu, chờ tài xế', ACCEPTED: 'Tài xế đã chấp nhận',
  RESPONSE_EXPIRED: 'Quá hạn phản hồi, cần phân công lại', REPLACEMENT_REQUIRED: 'Cần tài xế thay thế', NOT_REQUIRED: 'Không tiếp tục phân công',
  NOT_STARTED: 'Chưa bắt đầu', IN_PROGRESS: 'Đang diễn ra', ENDED: 'Hai bên đã xác nhận kết thúc',
  AWAITING_START_CONFIRMATION: 'Chờ hai bên xác nhận bắt đầu', AWAITING_END_CONFIRMATION: 'Chờ đủ xác nhận kết thúc', CANCELLED: 'Không thực hiện chuyến',
  DISPUTED: 'Đang giải quyết tranh chấp', SETTLED: 'Đã hoàn tất quyết toán',
  AWAITING_FINAL_PAYMENT: 'Chờ thanh toán phần còn lại', REVIEWING_CHARGES: 'Chờ chốt phụ phí', NOT_READY: 'Chưa đến bước quyết toán',
}

export const REFUND_WORKFLOW_LABELS = {
  REQUESTED: 'Chờ xem xét', UNDER_REVIEW: 'Đang xem xét', APPROVED: 'Đã duyệt, chưa chuyển tiền',
  PROCESSING: 'Đang chờ cổng thanh toán hoặc ngân hàng', REFUNDED: 'Đã xác nhận tiền hoàn', REJECTED: 'Đã từ chối',
  FAILED: 'Hoàn tiền chưa thành công, cần xử lý lại', PENDING_RECONCILIATION: 'Đang đối soát khoản hoàn',
}

/** Hide redundant refund list when stepper + badges already describe a single in-flight refund. */
export function shouldShowRefundDetailsList(refunds = [], { provider = false } = {}) {
  if (!refunds?.length) return false
  if (refunds.length > 1) return true
  const refund = refunds[0]
  if (refund.rejectionReason) return true
  if (refund.proofAvailable) return true
  if (refund.receiptStatus) return true
  if (provider && refund.status === 'PROCESSING') return true
  if (['FAILED', 'REJECTED', 'PENDING_RECONCILIATION', 'UNDER_REVIEW', 'REQUESTED', 'APPROVED'].includes(refund.status)) {
    return true
  }
  return false
}

// B4: khách không thấy đề xuất đã bị nhà xe rút hoặc đã hết hiệu lực; nhà xe vẫn thấy lịch sử xử lý.
export function visibleProposal(proposal, { provider = false } = {}) {
  if (!proposal) return null
  if (!provider && (proposal.status === 'WITHDRAWN' || proposal.status === 'EXPIRED')) return null
  return proposal
}

// B6: backend (BookingWorkflowServiceImpl) chỉ nhận giờ nhận mới SAU hiện tại hơn 1 giờ (đúng 1 giờ bị từ chối).
export const MIN_RESCHEDULE_LEAD_MS = 60 * 60 * 1000
export const RESCHEDULE_LEAD_MESSAGE = 'Giờ nhận mới phải sau hiện tại ít nhất 1 giờ.'
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000

/** Giá trị `min` cho input datetime-local (giờ Việt Nam): phút nguyên đầu tiên sau hiện tại + 1 giờ. */
export function minRescheduleInput(nowMs = Date.now()) {
  const earliest = Math.floor((nowMs + MIN_RESCHEDULE_LEAD_MS) / 60000) * 60000 + 60000
  return new Date(earliest + VIETNAM_OFFSET_MS).toISOString().slice(0, 16)
}

/** Kiểm tra giờ nhận mới (giờ Việt Nam, định dạng datetime-local) theo cùng quy tắc với backend. */
export function validateRescheduleInput(value, nowMs = Date.now()) {
  if (!value) return { valid: false, instant: null, message: '' }
  let instant
  try { instant = vietnamInputToInstant(value) } catch { return { valid: false, instant: null, message: 'Giờ chưa hợp lệ.' } }
  if (new Date(instant).getTime() <= nowMs + MIN_RESCHEDULE_LEAD_MS) {
    return { valid: false, instant: null, message: RESCHEDULE_LEAD_MESSAGE }
  }
  return { valid: true, instant, message: '' }
}

export function vietnamInputToInstant(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Chọn ngày và giờ hợp lệ theo giờ Việt Nam.')
  const instant = new Date(`${value}:00+07:00`)
  if (Number.isNaN(instant.getTime())) throw new Error('Ngày hoặc giờ không hợp lệ.')
  if (new Date(instant.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 16) !== value) throw new Error('Ngày hoặc giờ không hợp lệ.')
  return instant.toISOString()
}
