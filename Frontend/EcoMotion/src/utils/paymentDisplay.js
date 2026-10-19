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
  REJECTED: 'Đã từ chối',
  ACCEPTED: 'Chờ thanh toán giữ chỗ',
  CONFIRMED: 'Đã xác nhận',
  IN_PROGRESS: 'Đang diễn ra',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
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