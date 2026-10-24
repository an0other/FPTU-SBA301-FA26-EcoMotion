const BOOKING_STATUS_META = {
  PENDING: {
    label: 'Chờ duyệt',
    className: 'bg-amber-50 text-amber-800 border border-amber-200',
    dot: 'bg-amber-500',
  },
  ACCEPTED: {
    label: 'Chờ đặt cọc',
    className: 'bg-blue-50 text-blue-800 border border-blue-200',
    dot: 'bg-blue-500',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    className: 'bg-emerald-50 text-emerald-800 border border-emerald-200',
    dot: 'bg-emerald-500',
  },
  DRIVER_ASSIGNED: {
    label: 'Đã gán tài xế',
    className: 'bg-indigo-50 text-indigo-800 border border-indigo-200',
    dot: 'bg-indigo-500',
  },
  DRIVER_CONFIRMED: {
    label: 'Tài xế đã nhận',
    className: 'bg-teal-50 text-teal-800 border border-teal-200',
    dot: 'bg-teal-500',
  },
  DRIVER_ARRIVED: {
    label: 'Tài xế đã đến',
    className: 'bg-cyan-50 text-cyan-800 border border-cyan-200',
    dot: 'bg-cyan-500',
  },
  IN_PROGRESS: {
    label: 'Đang chạy',
    className: 'bg-emerald-50 text-emerald-800 border border-emerald-200',
    dot: 'bg-emerald-600',
  },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: {
    label: 'Chờ duyệt phụ phí',
    className: 'bg-orange-50 text-orange-800 border border-orange-200',
    dot: 'bg-orange-500',
  },
  AWAITING_FINAL_PAYMENT: {
    label: 'Chờ quyết toán',
    className: 'bg-purple-50 text-purple-800 border border-purple-200',
    dot: 'bg-purple-500',
  },
  COMPLETED: {
    label: 'Hoàn tất',
    className: 'bg-slate-100 text-slate-700 border border-slate-200',
    dot: 'bg-slate-400',
  },
  REJECTED: {
    label: 'Từ chối',
    className: 'bg-rose-50 text-rose-800 border border-rose-200',
    dot: 'bg-rose-500',
  },
  CANCELLED: {
    label: 'Đã huỷ',
    className: 'bg-slate-100 text-slate-700 border border-slate-200',
    dot: 'bg-slate-400',
  },
}

export default function BookingStatusBadge({ status }) {
  const meta = BOOKING_STATUS_META[status] ?? {
    label: status ?? '—',
    className: 'bg-slate-100 text-slate-700 border border-slate-200',
    dot: 'bg-slate-400',
  }
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${meta.className}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
      {meta.label}
    </span>
  )
}
