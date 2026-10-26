export const BOOKING_STATUS_META = {
  PENDING: {
    label: 'Chờ chủ xe xác nhận',
    tripLabel: 'Chờ chủ xe xác nhận',
    group: null,
    badgeClass: 'text-slate-800 bg-slate-100 border-slate-200',
  },
  AWAITING_DEPOSIT: {
    label: 'Chờ thanh toán cọc',
    tripLabel: 'Chờ thanh toán cọc',
    paymentLabel: 'Chờ thanh toán cọc',
    group: null,
    badgeClass: 'text-amber-800 bg-amber-50 border-amber-200',
  },
  ACCEPTED: {
    label: 'Chờ thanh toán cọc',
    tripLabel: 'Chờ thanh toán cọc',
    paymentLabel: 'Chờ thanh toán cọc',
    group: null,
    badgeClass: 'text-amber-800 bg-amber-50 border-amber-200',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    tripLabel: 'Sắp khởi hành',
    group: 'UPCOMING',
    badgeClass: 'text-emerald-800 bg-emerald-50 border-emerald-200',
  },
  DRIVER_ASSIGNED: {
    label: 'Đã phân công tài xế',
    tripLabel: 'Sắp khởi hành',
    group: 'UPCOMING',
    badgeClass: 'text-blue-800 bg-blue-50 border-blue-200',
  },
  DRIVER_CONFIRMED: {
    label: 'Tài xế đã xác nhận',
    tripLabel: 'Sắp khởi hành',
    group: 'UPCOMING',
    badgeClass: 'text-blue-800 bg-blue-50 border-blue-200',
  },
  DRIVER_ARRIVED: {
    label: 'Tài xế đã đến',
    tripLabel: 'Đang thuê',
    group: 'ACTIVE',
    badgeClass: 'text-cyan-800 bg-cyan-50 border-cyan-200',
  },
  AWAITING_START_CONFIRMATION: {
    label: 'Chờ hai bên xác nhận bắt đầu',
    tripLabel: 'Đang thuê',
    group: 'ACTIVE',
    badgeClass: 'text-cyan-800 bg-cyan-50 border-cyan-200',
  },
  IN_PROGRESS: {
    label: 'Đang thực hiện',
    tripLabel: 'Đang thực hiện',
    group: 'ACTIVE',
    badgeClass: 'text-[#006948] bg-[#E8F6EE] border-[#006948]/30',
  },
  AWAITING_END_CONFIRMATION: {
    label: 'Chờ hai bên xác nhận kết thúc',
    tripLabel: 'Đang thuê',
    group: 'ACTIVE',
    badgeClass: 'text-orange-800 bg-orange-50 border-orange-200',
  },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: {
    label: 'Chờ chốt phụ phí',
    tripLabel: 'Đang thuê',
    group: null,
    badgeClass: 'text-orange-800 bg-orange-50 border-orange-200',
  },
  AWAITING_FINAL_PAYMENT: {
    label: 'Chờ thanh toán quyết toán',
    tripLabel: 'Chờ thanh toán quyết toán',
    paymentLabel: 'Chờ thanh toán quyết toán',
    group: null,
    badgeClass: 'text-purple-800 bg-purple-50 border-purple-200',
  },
  COMPLETED: {
    label: 'Hoàn thành',
    tripLabel: 'Hoàn thành',
    group: 'ENDED',
    badgeClass: 'text-teal-700 bg-teal-50 border-teal-200',
  },
  CANCELLED: {
    label: 'Đã huỷ',
    tripLabel: 'Đã huỷ',
    group: 'ENDED',
    badgeClass: 'text-red-700 bg-red-50 border-red-200',
  },
  CANCELLATION_REVIEW: {
    label: 'Đang đối soát huỷ',
    tripLabel: 'Đang đối soát huỷ',
    group: null,
    badgeClass: 'text-orange-800 bg-orange-50 border-orange-200',
  },
  REJECTED: {
    label: 'Đã từ chối',
    tripLabel: 'Đã từ chối',
    group: 'ENDED',
    badgeClass: 'text-red-700 bg-red-50 border-red-200',
  },
}

export const BOOKING_FILTERS = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'ACTION_NEEDED', label: 'Cần thao tác' },
  { id: 'UPCOMING', label: 'Sắp tới' },
  { id: 'ACTIVE', label: 'Đang thuê' },
  { id: 'ENDED', label: 'Đã kết thúc' },
]

export const BOOKING_SORTS = [
  { id: 'PICKUP', label: 'Gần ngày nhận' },
  { id: 'NEWEST', label: 'Mới tạo' },
]

const RISK_VISIBLE_STATUSES = new Set([
  'CONFIRMED',
  'DRIVER_ASSIGNED',
  'DRIVER_CONFIRMED',
  'DRIVER_ARRIVED',
  'AWAITING_START_CONFIRMATION',
  'IN_PROGRESS',
  'AWAITING_END_CONFIRMATION',
  'AWAITING_EXTRA_CHARGE_CONFIRMATION',
  'AWAITING_FINAL_PAYMENT',
])

const WAITING_DEPOSIT_STATUSES = new Set(['AWAITING_DEPOSIT', 'ACCEPTED'])

export function bookingStatusMeta(status) {
  return BOOKING_STATUS_META[status] ?? {
    label: 'Trạng thái chưa xác định',
    tripLabel: 'Trạng thái chưa xác định',
    group: 'ENDED',
    badgeClass: 'text-slate-700 bg-slate-100 border-slate-200',
  }
}

export function bookingMatchesFilter(status, filterId) {
  if (filterId === 'ALL') return true
  if (filterId === 'ACTION_NEEDED') return false
  return bookingStatusMeta(status).group === filterId
}

export function canShowDepositCTA(actionKind) {
  return actionKind === 'PAY_DEPOSIT'
}

export function canShowFinalPaymentCTA(actionKind) {
  return actionKind === 'PAY_SETTLEMENT'
}

export function listPaymentLabel(status, { holdExpired = false, actionKind } = {}) {
  if (WAITING_DEPOSIT_STATUSES.has(status) && holdExpired) {
    return 'Hết hạn giữ chỗ'
  }
  if (WAITING_DEPOSIT_STATUSES.has(status) || actionKind === 'PAY_DEPOSIT') {
    return 'Chờ thanh toán cọc'
  }
  if (status === 'AWAITING_FINAL_PAYMENT' || actionKind === 'PAY_SETTLEMENT') {
    return 'Chờ thanh toán quyết toán'
  }
  return null
}

export function canShowOperationalRisk(status, atRisk, riskMessage) {
  return Boolean(atRisk)
    && Boolean(String(riskMessage ?? '').trim())
    && RISK_VISIBLE_STATUSES.has(status)
}

export function isWaitingDeposit(status) {
  return WAITING_DEPOSIT_STATUSES.has(status)
}

export function cardAction(actionKind) {
  switch (actionKind) {
    case 'PAY_DEPOSIT':
      return { kind: 'PAY_DEPOSIT', label: 'Thanh toán cọc' }
    case 'PAY_SETTLEMENT':
      return { kind: 'PAY_SETTLEMENT', label: 'Thanh toán quyết toán' }
    case 'CONFIRM_START':
      return { kind: 'CONFIRM_START', label: 'Xác nhận bắt đầu' }
    case 'CONFIRM_END':
      return { kind: 'CONFIRM_END', label: 'Xác nhận kết thúc' }
    default:
      return null
  }
}
