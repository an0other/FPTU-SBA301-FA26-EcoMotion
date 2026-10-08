/**
 * Offset/limit pager. Renders nothing when everything fits on one page, so a small fleet looks
 * exactly like it did before pagination existed.
 *
 * Props are the raw paging envelope plus the navigation callback; the parent owns the offset state.
 */
export default function Pagination({ total, offset, limit, hasMore, onPageChange }) {
  const pageSize = limit > 0 ? limit : 1
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const currentPage = Math.floor(offset / pageSize) + 1

  if (total <= pageSize) return null

  const firstOnPage = total === 0 ? 0 : offset + 1
  const lastOnPage = Math.min(offset + pageSize, total)

  const goTo = (page) => {
    const clamped = Math.min(Math.max(1, page), pageCount)
    onPageChange((clamped - 1) * pageSize)
  }

  // Windowed page numbers: at most 5 chips centred on the current page, so a long fleet does not
  // stretch the control across the row.
  const windowSize = 5
  let start = Math.max(1, currentPage - Math.floor(windowSize / 2))
  const end = Math.min(pageCount, start + windowSize - 1)
  start = Math.max(1, end - windowSize + 1)
  const pages = []
  for (let p = start; p <= end; p += 1) pages.push(p)

  const navButton =
    'inline-flex h-9 min-w-9 items-center justify-center rounded-lg border border-slate-200 bg-white px-3 text-[13px] font-semibold text-slate-600 transition-colors hover:border-brand-300 hover:text-brand-700 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-slate-200 disabled:hover:text-slate-600'

  return (
    <nav
      aria-label="Phân trang"
      className="mt-8 flex flex-col items-center justify-between gap-4 sm:flex-row"
    >
      <p className="text-[13px] text-slate-500">
        Hiển thị <span className="font-semibold text-slate-700">{firstOnPage}</span>–
        <span className="font-semibold text-slate-700">{lastOnPage}</span> trong{' '}
        <span className="font-semibold text-slate-700">{total}</span> xe
      </p>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          className={navButton}
          onClick={() => goTo(currentPage - 1)}
          disabled={currentPage === 1}
          aria-label="Trang trước"
        >
          Trước
        </button>

        {start > 1 && (
          <>
            <button type="button" className={navButton} onClick={() => goTo(1)}>
              1
            </button>
            {start > 2 && <span className="px-1 text-slate-400">…</span>}
          </>
        )}

        {pages.map((page) => {
          const isActive = page === currentPage
          return (
            <button
              key={page}
              type="button"
              aria-current={isActive ? 'page' : undefined}
              onClick={() => goTo(page)}
              className={
                isActive
                  ? 'inline-flex h-9 min-w-9 items-center justify-center rounded-lg bg-brand-600 px-3 text-[13px] font-semibold text-white'
                  : navButton
              }
            >
              {page}
            </button>
          )
        })}

        {end < pageCount && (
          <>
            {end < pageCount - 1 && <span className="px-1 text-slate-400">…</span>}
            <button type="button" className={navButton} onClick={() => goTo(pageCount)}>
              {pageCount}
            </button>
          </>
        )}

        <button
          type="button"
          className={navButton}
          onClick={() => goTo(currentPage + 1)}
          disabled={!hasMore && currentPage === pageCount}
          aria-label="Trang sau"
        >
          Sau
        </button>
      </div>
    </nav>
  )
}
