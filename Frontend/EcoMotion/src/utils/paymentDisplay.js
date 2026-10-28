export const PAYMENT_TYPE_LABELS = {
  DEPOSIT_PAY: 'Thanh toán cọc',
  UPFRONT_PAY: 'Trả trước gói năm',
  SETTLEMENT: 'Thanh toán còn lại',
  REFUND: 'Hoàn tiền',
}

export const PAYMENT_METHOD_LABELS = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  E_WALLET: 'Ví điện tử',
  CARD: 'Thẻ',
}

export const CHARGE_TYPE_LABELS = {
  BASE_RENTAL: 'Tiền thuê',
  DEPOSIT: 'Cọc giữ chỗ',
  OVERTIME: 'Phụ phí quá giờ',
  WAITING: 'Phụ phí chờ',
  WEEKEND: 'Phụ phí cuối tuần / lễ',
  OUT_OF_RADIUS: 'Phụ phí đón ngoài bán kính',
}

export const BOOKING_STATUS_LABELS = {
  PENDING: 'Chờ chấp nhận',
  AWAITING_DEPOSIT: 'Chờ thanh toán cọc',
  REJECTED: 'Đã từ chối',
  ACCEPTED: 'Chờ thanh toán cọc',
  CONFIRMED: 'Đã xác nhận',
  DRIVER_ASSIGNED: 'Đã phân công tài xế',
  DRIVER_CONFIRMED: 'Tài xế đã xác nhận',
  DRIVER_ARRIVED: 'Tài xế đã đến',
  AWAITING_START_CONFIRMATION: 'Chờ hai bên xác nhận bắt đầu',
  IN_PROGRESS: 'Đang diễn ra',
  AWAITING_END_CONFIRMATION: 'Chờ hai bên xác nhận kết thúc',
  AWAITING_EXTRA_CHARGE_CONFIRMATION: 'Chờ chốt phụ phí',
  AWAITING_FINAL_PAYMENT: 'Chờ thanh toán quyết toán',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
  CANCELLATION_REVIEW: 'Đang đối soát huỷ',
}

const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
})

const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Asia/Ho_Chi_Minh',
})

export function formatPaymentMoney(value) {
  if (value == null || value === '') return '—'

  const amount = Number(value)
  return Number.isFinite(amount) ? currencyFormatter.format(amount) : '—'
}

export function formatPaymentDate(value) {
  if (!value) return '—'

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date)
}

export function formatAppliedRate(value) {
  if (value == null) return '—'

  return new Intl.NumberFormat('vi-VN', {
    style: 'percent',
    maximumFractionDigits: 2,
  }).format(Number(value))
}

export function formatChargeQuantity(charge) {
  if (charge.quantity == null) return '—'

  const units = {
    BASE_RENTAL: 'slot',
    OVERTIME: 'slot',
    WAITING: 'phút',
    OUT_OF_RADIUS: 'km',
  }

  return `${charge.quantity} ${units[charge.chargeType] ?? ''}`.trim()
}

export function isPaymentRequestExpired(request, now = Date.now()) {
  if (!request?.expiresAt) return false
  const expiresAt = new Date(request.expiresAt).getTime()
  return Number.isFinite(expiresAt) && expiresAt <= now
}

export const HOLD_DURATION_MS = 15 * 60 * 1000
export const HOLD_URGENT_MS = 2 * 60 * 1000

export function remainingMs(expiresAt, now = Date.now()) {
  if (!expiresAt) return null
  const deadline = new Date(expiresAt).getTime()
  if (!Number.isFinite(deadline)) return null
  return deadline - now
}

export function formatCountdown(ms) {
  if (ms == null) return '—'
  if (ms <= 0) return '00:00'
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function countdownUrgency(ms) {
  if (ms == null) return 'none'
  if (ms <= 0) return 'expired'
  if (ms <= HOLD_URGENT_MS) return 'urgent'
  return 'ok'
}

export function holdProgress(ms, durationMs = HOLD_DURATION_MS) {
  if (ms == null) return 0
  if (ms <= 0) return 0
  return Math.min(100, Math.max(0, (ms / durationMs) * 100))
}
