export default function PaymentPagination({
  page,
  size,
  total,
  hasMore,
  loading,
  onPageChange,
}) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  const start = total === 0 ? 0 : page * size + 1
  const end = Math.min((page + 1) * size, total)

  return (
    <nav
      aria-label="Phân trang giao dịch"
      className="flex flex-wrap items-center justify-between gap-4 border-t border-slate-100 px-5 py-4"
    >
      <p className="text-xs text-slate-500">
        Hiển thị {start}–{end} / {total} giao dịch
      </p>

      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={loading || page === 0}
          onClick={() => onPageChange(page - 1)}
          className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Trước
        </button>

        <span className="text-xs font-medium text-slate-600">
          Trang {page + 1} / {totalPages}
        </span>

        <button
          type="button"
          disabled={loading || !hasMore}
          onClick={() => onPageChange(page + 1)}
          className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Sau
        </button>
      </div>
    </nav>
  )
}