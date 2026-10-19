const STATUS_CONFIG = {
  PAID: {
    label: "Thành công",
    className: "bg-brand-50 text-brand-800 ring-brand-200",
  },
  PENDING: {
    label: "Đang xử lý",
    className: "bg-amber-50 text-amber-800 ring-amber-200",
  },
  FAILED: {
    label: "Thất bại",
    className: "bg-red-50 text-red-800 ring-red-200",
  },
};

export default function PaymentStatusBadge({ status }) {
  const config = STATUS_CONFIG[status] ?? {
    label: status || "Chưa xác định",
    className: "bg-slate-100 text-slate-700 ring-slate-200",
  };

  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${config.className}`}
    >
      {config.label}
    </span>
  );
}
