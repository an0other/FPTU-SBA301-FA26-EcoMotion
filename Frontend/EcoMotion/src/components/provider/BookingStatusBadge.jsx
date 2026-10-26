const BOOKING_STATUS_META = {
  PENDING: {
    label: 'Chờ duyệt',
    className: 'bg-amber-100 text-amber-900',
    dot: 'bg-amber-600 animate-pulse',
  },
  ACCEPTED: {
    label: 'Chờ đặt cọc',
    className: 'bg-tertiary-fixed text-tertiary',
    dot: 'bg-tertiary',
  },
  AWAITING_DEPOSIT: {
    label: 'Chờ khách đặt cọc',
    className: 'bg-amber-100 text-amber-900',
    dot: 'bg-amber-600 animate-pulse',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    className: 'bg-secondary-fixed text-secondary',
    dot: 'bg-secondary',
  },
  DRIVER_ASSIGNED: {
    label: 'Đã gán tài xế',
    className: 'bg-blue-100 text-blue-800',
    dot: 'bg-blue-600',
  },
  DRIVER_CONFIRMED: {
    label: 'Tài xế đã nhận',
    className: 'bg-indigo-100 text-indigo-800',
    dot: 'bg-indigo-600',
  },
  DRIVER_ARRIVED: {
    label: 'Tài xế đã đến',
    className: 'bg-cyan-100 text-cyan-800',
    dot: 'bg-cyan-600 animate-pulse',
  },
  AWAITING_START_CONFIRMATION: {
    label: 'Chờ xác nhận bắt đầu',
    className: 'bg-cyan-100 text-cyan-800',
    dot: 'bg-cyan-600 animate-pulse',
  },
  IN_PROGRESS: {
    label: 'Đang chạy',
    className: 'bg-primary-fixed text-primary',
    dot: 'bg-primary animate-ping',
  },
  AWAITING_END_CONFIRMATION: {
    label: 'Chờ xác nhận kết thúc',
    className: 'bg-orange-100 text-orange-800',
    dot: 'bg-orange-600 animate-pulse',
  },
  CANCELLATION_REVIEW: {
    label: 'Đang đối soát huỷ',
    className: 'bg-orange-100 text-orange-800',
    dot: 'bg-orange-600 animate-pulse',
  },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: {
    label: 'Chờ duyệt phụ phí',
    className: 'bg-orange-100 text-orange-800',
    dot: 'bg-orange-600 animate-pulse',
  },
  AWAITING_FINAL_PAYMENT: {
    label: 'Chờ quyết toán',
    className: 'bg-purple-100 text-purple-800',
    dot: 'bg-purple-600 animate-pulse',
  },
  COMPLETED: {
    label: 'Hoàn tất',
    className: 'bg-surface-container text-on-surface-variant',
    dot: 'bg-outline',
  },
  REJECTED: {
    label: 'Từ chối',
    className: 'bg-error-container text-on-error-container',
    dot: 'bg-error',
  },
  CANCELLED: {
    label: 'Đã huỷ',
    className: 'bg-surface-container text-on-surface-variant',
    dot: 'bg-outline',
  },
}

export default function BookingStatusBadge({ status }) {
  const meta = BOOKING_STATUS_META[status] ?? {
    label: 'Trạng thái chưa xác định',
    className: 'bg-surface-container text-on-surface-variant',
    dot: 'bg-outline',
  }
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-[11px] font-semibold ${meta.className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
      {meta.label}
    </span>
  )
}
