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
  { id: 'PICKUP', label: 'Theo trạng thái' },
  { id: 'NEWEST', label: 'Mới tạo' },
]

const RISK_VISIBLE_STATUSES = new Set([
  'CONFIRMED',
  'DRIVER_ASSIGNED',
  'DRIVER_CONFIRMED',
  'DRIVER_ARRIVED',
  'AWAITING_START_CONFIRMATION',
])

const WAITING_DEPOSIT_STATUSES = new Set(['AWAITING_DEPOSIT', 'ACCEPTED'])

const RENTER_DRIVER_VISIBLE_STATUSES = new Set([
  'DRIVER_CONFIRMED',
  'DRIVER_ARRIVED',
  'AWAITING_START_CONFIRMATION',
  'IN_PROGRESS',
  'AWAITING_END_CONFIRMATION',
  'AWAITING_EXTRA_CHARGE_CONFIRMATION',
  'AWAITING_FINAL_PAYMENT',
  'COMPLETED',
])

export function renterCanSeeDriver(status) {
  return RENTER_DRIVER_VISIBLE_STATUSES.has(status)
}

export function bookingStatusMeta(status) {
  return BOOKING_STATUS_META[status] ?? {
    label: 'Trạng thái chưa xác định',
    tripLabel: 'Trạng thái chưa xác định',
    group: 'ENDED',
    badgeClass: 'text-slate-700 bg-slate-100 border-slate-200',
  }
}

export const PAYMENT_STATUS_LABELS = {
  VERIFYING: 'Đang xác minh thanh toán',
  RECONCILIATION_REQUIRED: 'Đã nhận tiền, cần đối soát',
  UNPAID: 'Chưa thanh toán',
  AWAITING_DEPOSIT: 'Chờ đặt cọc',
  DEPOSIT_PAID: 'Đã thanh toán cọc',
  PARTIALLY_PAID: 'Thanh toán một phần',
  FULLY_PAID: 'Đã tất toán',
  PAID: 'Đã thanh toán',
  REFUND_PENDING: 'Chờ hoàn tiền',
  REFUNDED: 'Đã hoàn tiền',
  FAILED: 'Thanh toán lỗi',
}

export function paymentStatusLabel(paymentStatus) {
  if (!paymentStatus) return null
  return PAYMENT_STATUS_LABELS[paymentStatus] ?? paymentStatus
}

export const CANCEL_REASON_META = {
  RENTER_REQUEST: {
    label: 'Khách chủ động hủy',
    description: 'Bạn hoặc nhà xe đã hủy đơn theo yêu cầu trước khi chuyến diễn ra.',
    icon: 'person_off',
  },
  PROVIDER_NO_VEHICLE: {
    label: 'Không còn xe phù hợp',
    description: 'Nhà xe không thể cung cấp xe cho khung giờ này.',
    icon: 'directions_car',
  },
  PROVIDER_NO_DRIVER: {
    label: 'Không sắp xếp được tài xế',
    description: 'Nhà xe không tìm được tài xế cho chuyến trong thời hạn quy định.',
    icon: 'person_off',
  },
  PREVIOUS_RENTER_OVERDUE: {
    label: 'Chuyến trước kéo dài',
    description: 'Chuyến thuê liền kề chưa kết thúc kịp nên đơn này không thể tiếp tục.',
    icon: 'schedule',
  },
  DEPOSIT_TIMEOUT: {
    label: 'Hết hạn giữ chỗ (chưa cọc kịp)',
    description: 'Đơn bị hủy tự động vì tiền cọc chưa được thanh toán trong thời gian giữ chỗ. Bạn có thể đặt lại xe với khung giờ khác.',
    icon: 'timer_off',
  },
  YEARLY_REQUEST_TIMEOUT: {
    label: 'Hết hạn xử lý gói năm',
    description: 'Yêu cầu thuê năm không được xử lý kịp trong thời hạn cho phép.',
    icon: 'event_busy',
  },
  YEARLY_CONTRACT_TIMEOUT: {
    label: 'Hết hạn ký hợp đồng năm',
    description: 'Hợp đồng thuê năm chưa hoàn tất kịp thời hạn cam kết.',
    icon: 'description',
  },
  FORCE_MAJEURE: {
    label: 'Bất khả kháng',
    description: 'Đơn dừng do sự kiện bất khả kháng theo chính sách nền tảng.',
    icon: 'thunderstorm',
  },
  SYSTEM: {
    label: 'Hệ thống hủy',
    description: 'Đơn được hủy theo quy trình tự động của hệ thống.',
    icon: 'smart_toy',
  },
}

export function cancelReasonPresentation(rawReason) {
  if (!rawReason) {
    return {
      label: null,
      description: 'Đơn thuê không tiếp tục thực hiện.',
      icon: 'info',
      isKnownCode: false,
    }
  }
  const code = String(rawReason).trim()
  const meta = CANCEL_REASON_META[code]
  if (meta) {
    return { ...meta, isKnownCode: true }
  }
  return {
    label: code,
    description: null,
    icon: 'chat',
    isKnownCode: false,
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

export function listPaymentLabel(status, { holdExpired = false, actionKind, depositPaymentStatus } = {}) {
  if (depositPaymentStatus === 'REJECTED') {
    return 'Biên lai cọc bị từ chối'
  }
  if (depositPaymentStatus === 'PROOF_SUBMITTED' || depositPaymentStatus === 'REVIEW_REQUIRED') {
    return 'Chờ nhà xe xác nhận cọc'
  }
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

export function cardAction(actionKind, depositPaymentStatus) {
  switch (actionKind) {
    case 'PAY_DEPOSIT':
      return {
        kind: 'PAY_DEPOSIT',
        label: depositPaymentStatus === 'REJECTED' ? 'Gửi lại cọc / biên lai' : 'Thanh toán cọc',
      }
    case 'PAY_SETTLEMENT':
      return { kind: 'PAY_SETTLEMENT', label: 'Thanh toán quyết toán' }
    default:
      return null
  }
}
