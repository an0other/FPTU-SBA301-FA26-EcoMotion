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
  CONFIRMED: {
    label: 'Đã xác nhận',
    className: 'bg-secondary-fixed text-secondary',
    dot: 'bg-secondary',
  },
  IN_PROGRESS: {
    label: 'Đang chạy',
    className: 'bg-primary-fixed text-primary',
    dot: 'bg-primary animate-ping',
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
    label: status ?? '—',
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
