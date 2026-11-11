import { formatCurrency } from './formatCurrency.js'

/** Phán quyết admin chọn cho từng loại khiếu nại (thay vì chọn trạng thái kỹ thuật). */
export const TRIP_END_VERDICTS = [
  { value: 'ACCEPT_RENTER_TIME', label: 'Chấp nhận khiếu nại — chốt giờ kết thúc theo khách', hint: 'Nhập giờ kết thúc đúng; hệ thống tự tính lại phí quá giờ (chỉ giảm, phần đã thu dư sẽ được hoàn).' },
  { value: 'KEEP_DRIVER_TIME', label: 'Bác khiếu nại — giữ giờ tài xế báo', hint: 'Giờ kết thúc được chốt ngay, đơn tiếp tục quyết toán.' },
  { value: 'OTHER', label: 'Khác (ghi phương án xử lý)', hint: 'Ghi rõ phương án; giờ kết thúc vẫn tạm dừng cho đến khi bạn chọn một trong hai phán quyết trên.' },
]

export const TRIP_END_ADVANCED = [
  { value: 'RESUME_TRIP', label: 'Tiếp tục chuyến (tài xế báo kết thúc nhầm)' },
  { value: 'REOPEN_CONFIRMATION', label: 'Mở lại thời hạn xác nhận 12 giờ' },
]

export const CHARGE_VERDICTS = [
  { value: 'WAIVE_CHARGE', label: 'Miễn toàn bộ phụ phí', hint: 'Gạch khoản phụ phí khỏi quyết toán; phần đã thu sẽ được hoàn.' },
  { value: 'PARTIAL_WAIVE', label: 'Giảm một phần (nhập số tiền giảm)', hint: 'Khách trả phần còn lại; phần đã thu dư sẽ được hoàn.' },
  { value: 'KEEP_CHARGE', label: 'Giữ nguyên phụ phí', hint: 'Khách thanh toán đủ khoản phí theo chính sách.' },
  { value: 'OTHER', label: 'Khác (ghi phương án xử lý)', hint: 'Ghi rõ phương án; khoản phí vẫn tạm giữ cho đến khi bạn chốt miễn / giảm / giữ.' },
]

export const GENERIC_VERDICTS = [
  { value: 'RESOLVED', label: 'Chấp thuận khiếu nại' },
  { value: 'REJECTED', label: 'Bác bỏ khiếu nại' },
  { value: 'OTHER', label: 'Khác / cần điều tra thêm (giữ khiếu nại mở)' },
]

export function verdictOptions(category) {
  if (category === 'TRIP_NOT_CONFIRMED') return TRIP_END_VERDICTS
  if (category === 'CHARGE') return CHARGE_VERDICTS
  return GENERIC_VERDICTS
}

export function defaultVerdict(category) {
  return verdictOptions(category)[0].value
}

/**
 * Chuyển phán quyết trên form thành tham số API. Trả { error } nếu thiếu dữ liệu bắt buộc.
 * "Khác" luôn là IN_REVIEW kèm ghi chú: khiếu nại chưa đóng, chưa đổi tiền hay giờ.
 */
export function buildDisputeResolution(category, form) {
  const resolution = (form.resolution || '').trim()
  if (!resolution) return { error: 'Vui lòng nhập nội dung phán quyết / phương án xử lý.' }
  const verdict = form.verdict
  const amount = form.refundAwardTotal === '' || form.refundAwardTotal == null ? undefined : Number(form.refundAwardTotal)

  if (category === 'TRIP_NOT_CONFIRMED') {
    if (verdict === 'ACCEPT_RENTER_TIME') {
      if (!form.correctedEndAt) return { error: 'Nhập giờ kết thúc đúng theo xác minh.' }
      return { status: 'RESOLVED', resolution, tripEndAction: verdict, correctedEndAt: new Date(form.correctedEndAt).toISOString() }
    }
    if (verdict === 'KEEP_DRIVER_TIME') return { status: 'REJECTED', resolution, tripEndAction: verdict }
    if (verdict === 'RESUME_TRIP' || verdict === 'REOPEN_CONFIRMATION') return { status: 'RESOLVED', resolution, tripEndAction: verdict }
    return { status: 'IN_REVIEW', resolution }
  }
  if (category === 'CHARGE') {
    if (verdict === 'WAIVE_CHARGE') return { status: 'RESOLVED', decision: 'WAIVE_CHARGE', resolution }
    if (verdict === 'PARTIAL_WAIVE') {
      if (!amount || amount <= 0) return { error: 'Nhập số tiền giảm lớn hơn 0.' }
      return { status: 'RESOLVED', decision: 'PARTIAL_WAIVE', resolution, refundAwardTotal: amount }
    }
    if (verdict === 'KEEP_CHARGE') return { status: 'REJECTED', decision: 'KEEP_CHARGE', resolution }
    return { status: 'IN_REVIEW', resolution }
  }
  if (verdict === 'OTHER') return { status: 'IN_REVIEW', resolution }
  const status = verdict === 'REJECTED' ? 'REJECTED' : 'RESOLVED'
  const compensable = category === 'SERVICE_QUALITY' || category === 'OTHER'
  const withAmount = status === 'RESOLVED' && (category === 'DEPOSIT_REFUND' || category === 'PAYMENT_MISMATCH' || compensable) && amount != null
  if (status === 'RESOLVED' && category === 'DEPOSIT_REFUND' && amount == null) return { error: 'Nhập tổng tiền cọc khách được hoàn.' }
  // Chấp thuận "ghi sai tiền" phải có số khách thực chuyển, nếu không phán quyết không có tác dụng.
  if (status === 'RESOLVED' && category === 'PAYMENT_MISMATCH' && (amount == null || amount <= 0))
    return { error: 'Nhập tổng số tiền khách thực chuyển theo sao kê. Nếu chỉ ghi kết luận, hãy chọn Bác bỏ hoặc Khác.' }
  if (withAmount && compensable && amount <= 0) return { error: 'Khoản bồi hoàn phải lớn hơn 0 (để trống nếu không bồi hoàn).' }
  return { status, resolution, ...(withAmount ? { refundAwardTotal: amount } : {}) }
}

/** Nhãn kết luận cuối cùng hiển thị ở danh sách và chi tiết khiếu nại. */
export function disputeVerdictLabel(dispute) {
  if (!dispute || !['RESOLVED', 'REJECTED'].includes(dispute.status)) return null
  if (dispute.category === 'TRIP_NOT_CONFIRMED') {
    switch (dispute.tripEndAction) {
      case 'ACCEPT_RENTER_TIME': return 'Đã chốt: theo giờ khách'
      case 'KEEP_DRIVER_TIME': return 'Đã chốt: giữ giờ tài xế báo'
      case 'RESUME_TRIP': return 'Đã chốt: tiếp tục chuyến'
      case 'REOPEN_CONFIRMATION': return 'Đã chốt: mở lại xác nhận 12 giờ'
      default: return dispute.status === 'RESOLVED' ? 'Đã chốt: chấp thuận' : 'Đã chốt: bác khiếu nại'
    }
  }
  if (dispute.category === 'CHARGE' || dispute.decision) {
    if (dispute.decision === 'PARTIAL_WAIVE') return `Đã chốt: giảm ${formatCurrency(dispute.refundAwardTotal)}`
    if (dispute.decision === 'WAIVE_CHARGE' || (!dispute.decision && dispute.status === 'RESOLVED')) return 'Đã chốt: miễn phụ phí'
    return 'Đã chốt: giữ phụ phí'
  }
  if ((dispute.category === 'SERVICE_QUALITY' || dispute.category === 'OTHER') && dispute.status === 'RESOLVED' && dispute.refundAwardTotal != null)
    return `Đã chốt: bồi hoàn ${formatCurrency(dispute.refundAwardTotal)}`
  if (dispute.category === 'PAYMENT_MISMATCH' && dispute.status === 'RESOLVED' && dispute.refundAwardTotal != null)
    return `Đã chốt: khách thực chuyển ${formatCurrency(dispute.refundAwardTotal)}`
  return dispute.status === 'RESOLVED' ? 'Đã chốt: chấp thuận' : 'Đã chốt: bác khiếu nại'
}
