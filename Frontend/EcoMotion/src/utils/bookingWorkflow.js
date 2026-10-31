export const WORKFLOW_LABELS = {
  UNPAID: 'Chưa ghi nhận thanh toán', VERIFYING: 'Đang xác minh thanh toán',
  RECONCILIATION_REQUIRED: 'Đã nhận tiền, cần đối soát', REFUND_PENDING: 'Đang xử lý hoàn tiền',
  REFUNDED: 'Đã ghi nhận hoàn tiền', PARTIALLY_PAID: 'Đã thanh toán một phần', COVERED: 'Đã ghi nhận đủ chi phí hiện tại',
  NOT_ASSIGNED: 'Chưa có tài xế', PENDING_RESPONSE: 'Đã gửi yêu cầu, chờ tài xế', ACCEPTED: 'Tài xế đã chấp nhận',
  RESPONSE_EXPIRED: 'Quá hạn phản hồi, cần phân công lại', REPLACEMENT_REQUIRED: 'Cần tài xế thay thế', NOT_REQUIRED: 'Không tiếp tục phân công',
  NOT_STARTED: 'Chưa bắt đầu', IN_PROGRESS: 'Đang diễn ra', ENDED: 'Hai bên đã xác nhận kết thúc',
  AWAITING_START_CONFIRMATION: 'Chờ hai bên xác nhận bắt đầu', AWAITING_END_CONFIRMATION: 'Chờ đủ xác nhận kết thúc', CANCELLED: 'Không thực hiện chuyến',
  DISPUTED: 'Đang giải quyết tranh chấp', SETTLED: 'Đã hoàn tất quyết toán',
  AWAITING_FINAL_PAYMENT: 'Chờ thanh toán phần còn lại', REVIEWING_CHARGES: 'Chờ chốt phụ phí', NOT_READY: 'Chưa đến bước quyết toán',
}

export const REFUND_WORKFLOW_LABELS = {
  REQUESTED: 'Chờ xem xét', UNDER_REVIEW: 'Đang xem xét', APPROVED: 'Đã duyệt, chưa chuyển tiền',
  PROCESSING: 'Đang xử lý chuyển khoản', REFUNDED: 'Đã xác nhận hoàn tiền', REJECTED: 'Đã từ chối',
  FAILED: 'Hoàn tiền chưa thành công, cần xử lý lại', PENDING_RECONCILIATION: 'Đang đối soát khoản hoàn',
}

export function vietnamInputToInstant(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Chọn ngày và giờ hợp lệ theo giờ Việt Nam.')
  const instant = new Date(`${value}:00+07:00`)
  if (Number.isNaN(instant.getTime())) throw new Error('Ngày hoặc giờ không hợp lệ.')
  if (new Date(instant.getTime() + 7 * 60 * 60 * 1000).toISOString().slice(0, 16) !== value) throw new Error('Ngày hoặc giờ không hợp lệ.')
  return instant.toISOString()
}
