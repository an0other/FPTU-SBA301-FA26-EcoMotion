export const BOOKING_STATUS_META = {
  PENDING: { label: 'Chờ chủ xe duyệt', group: 'PROCESSING', badgeClass: 'text-slate-700 bg-slate-100 border-slate-200' },
  AWAITING_DEPOSIT: { label: 'Chờ thanh toán cọc', group: 'PROCESSING', badgeClass: 'text-amber-800 bg-amber-50 border-amber-200', action: 'Thanh toán cọc' },
  ACCEPTED: { label: 'Chờ thanh toán cọc', group: 'PROCESSING', badgeClass: 'text-amber-800 bg-amber-50 border-amber-200', action: 'Thanh toán cọc' },
  CONFIRMED: { label: 'Đã xác nhận', group: 'UPCOMING', badgeClass: 'text-emerald-800 bg-emerald-50 border-emerald-200' },
  DRIVER_ASSIGNED: { label: 'Đã phân công tài xế', group: 'UPCOMING', badgeClass: 'text-blue-800 bg-blue-50 border-blue-200' },
  DRIVER_CONFIRMED: { label: 'Tài xế đã xác nhận', group: 'UPCOMING', badgeClass: 'text-blue-800 bg-blue-50 border-blue-200' },
  DRIVER_ARRIVED: { label: 'Tài xế đã đến', group: 'ACTIVE', badgeClass: 'text-cyan-800 bg-cyan-50 border-cyan-200' },
  AWAITING_START_CONFIRMATION: { label: 'Chờ hai bên xác nhận bắt đầu', group: 'ACTIVE', badgeClass: 'text-cyan-800 bg-cyan-50 border-cyan-200' },
  IN_PROGRESS: { label: 'Đang thực hiện', group: 'ACTIVE', badgeClass: 'text-[#006948] bg-[#E8F6EE] border-[#006948]/30' },
  AWAITING_END_CONFIRMATION: { label: 'Chờ hai bên xác nhận kết thúc', group: 'ACTIVE', badgeClass: 'text-orange-800 bg-orange-50 border-orange-200' },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: { label: 'Chờ chốt phụ phí', group: 'SETTLEMENT', badgeClass: 'text-orange-800 bg-orange-50 border-orange-200' },
  AWAITING_FINAL_PAYMENT: { label: 'Chờ thanh toán quyết toán', group: 'SETTLEMENT', badgeClass: 'text-purple-800 bg-purple-50 border-purple-200', action: 'Thanh toán quyết toán' },
  COMPLETED: { label: 'Hoàn thành', group: 'COMPLETED', badgeClass: 'text-teal-700 bg-teal-50 border-teal-200' },
  CANCELLED: { label: 'Đã huỷ', group: 'ENDED', badgeClass: 'text-red-700 bg-red-50 border-red-200' },
  CANCELLATION_REVIEW: { label: 'Đang đối soát huỷ', group: 'PROCESSING', badgeClass: 'text-orange-800 bg-orange-50 border-orange-200' },
  REJECTED: { label: 'Đã từ chối', group: 'ENDED', badgeClass: 'text-red-700 bg-red-50 border-red-200' },
}

export const BOOKING_FILTERS = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'PROCESSING', label: 'Chờ xử lý' },
  { id: 'UPCOMING', label: 'Sắp tới' },
  { id: 'ACTIVE', label: 'Đang thực hiện' },
  { id: 'SETTLEMENT', label: 'Chờ quyết toán' },
  { id: 'COMPLETED', label: 'Hoàn thành' },
  { id: 'ENDED', label: 'Đã kết thúc' },
]

export function bookingStatusMeta(status) {
  return BOOKING_STATUS_META[status] ?? {
    label: 'Trạng thái chưa xác định',
    group: 'ENDED',
    badgeClass: 'text-slate-700 bg-slate-100 border-slate-200',
  }
}

export function bookingMatchesFilter(status, filterId) {
  return filterId === 'ALL' || bookingStatusMeta(status).group === filterId
}
