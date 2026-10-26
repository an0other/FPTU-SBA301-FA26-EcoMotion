import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BOOKING_FILTERS, bookingStatusMeta } from '../../utils/bookingStatus'
import { remainingMs } from '../../utils/paymentDisplay'
import { HoldCountdown } from '../payments/HoldCountdown'

const FOCUS =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2'

function useNow(enabled) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return undefined
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [enabled])
  return now
}

function BookingSkeleton() {
  return (
    <div className="flex flex-col gap-3.5" aria-hidden="true">
      {[0, 1, 2].map((key) => (
        <div
          key={key}
          className="flex animate-pulse flex-col gap-4 rounded-xl border border-[#E6EBE8] bg-white p-4 md:flex-row md:items-center"
        >
          <div className="h-20 w-28 shrink-0 rounded-xl bg-slate-200" />
          <div className="min-w-0 flex-1 space-y-2">
            <div className="h-3 w-32 rounded bg-slate-200" />
            <div className="h-4 w-48 rounded bg-slate-100" />
            <div className="h-3 w-40 rounded bg-slate-100" />
          </div>
          <div className="h-8 w-28 rounded-full bg-slate-100" />
          <div className="h-10 w-32 rounded-xl bg-slate-100" />
        </div>
      ))}
    </div>
  )
}

function payAction(booking, holdExpired) {
  if (!booking.canPay) return null
  if (holdExpired) return { label: 'Cấp lại', tone: 'renew' }
  return {
    label: bookingStatusMeta(booking.status).action || 'Thanh toán cọc',
    tone: 'pay',
  }
}

export const MyBookingsList = ({
  bookings,
  loading = false,
  error = '',
  activeFilter,
  keyword,
  page,
  pageSize,
  total,
  hasMore,
  onFilterChange,
  onKeywordChange,
  onPageChange,
  onPayDeposit,
  onBookCar,
}) => {
  const watchingHold = (bookings ?? []).some(
    (booking) => booking.status === 'AWAITING_DEPOSIT' && booking.holdExpiresAt,
  )
  const now = useNow(watchingHold)

  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <div className="sticky top-32 z-10 space-y-4 bg-surface pb-3 pt-1">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#14221A] sm:text-3xl">
            Đơn của tôi
          </h1>
          <p className="mt-1 text-sm text-[#14221A]/70 sm:text-base">
            Mã đơn, hạn cọc và thao tác kế tiếp trên một hàng.
          </p>
        </div>

        <label className="block max-w-xl">
          <span className="sr-only">Tìm đơn thuê</span>
          <div className="flex items-center gap-2 rounded-xl border border-[#DDE5E0] bg-white px-4 py-3 shadow-sm focus-within:border-brand-600 focus-within:ring-2 focus-within:ring-brand-500/30">
            <span className="material-symbols-outlined text-[20px] text-[#14221A]/50" aria-hidden="true">
              search
            </span>
            <input
              type="search"
              name="booking-search"
              autoComplete="off"
              spellCheck={false}
              value={keyword}
              onChange={(event) => onKeywordChange(event.target.value)}
              placeholder="Tìm theo mã đơn hoặc tên xe…"
              className="min-w-0 flex-1 bg-transparent text-sm text-[#14221A] outline-none placeholder:text-[#14221A]/45"
            />
          </div>
        </label>

        <div className="w-full overflow-x-auto pb-1">
          <div className="inline-flex items-center gap-2">
            {BOOKING_FILTERS.map((tab) => {
              const isActive = activeFilter === tab.id
              return (
                <button
                  key={tab.id}
                  onClick={() => onFilterChange(tab.id)}
                  type="button"
                  aria-pressed={isActive}
                  className={`cursor-pointer whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors ${FOCUS} ${
                    isActive
                      ? 'border border-transparent bg-brand-700 text-white shadow-xs'
                      : 'border border-[#E6EBE8] bg-white text-[#14221A] hover:bg-slate-50'
                  }`}
                >
                  {tab.label}
                </button>
              )
            })}
          </div>
        </div>
      </div>

      {error ? (
        <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {error}
        </p>
      ) : null}

      {loading || !bookings ? (
        <BookingSkeleton />
      ) : bookings.length > 0 ? (
        <div className="flex flex-col gap-3.5">
          {bookings.map((booking) => {
            const meta = bookingStatusMeta(booking.status)
            const holdMs = remainingMs(booking.holdExpiresAt, now)
            const holdExpired =
              booking.status === 'AWAITING_DEPOSIT' && holdMs != null && holdMs <= 0
            const action = payAction(booking, holdExpired)
            const showCountdown =
              booking.status === 'AWAITING_DEPOSIT' &&
              Boolean(booking.holdExpiresAt) &&
              !holdExpired

            return (
              <article
                key={booking.id}
                className="rounded-xl border border-[#E6EBE8] bg-white p-4 shadow-card"
              >
                <div className="flex flex-col gap-4 min-[390px]:gap-4 lg:flex-row lg:items-center lg:gap-6">
                  <Link
                    to={`/renter/bookings/${booking.id}`}
                    className={`flex min-w-0 flex-1 flex-col gap-3 rounded-lg sm:flex-row sm:items-center sm:gap-4 ${FOCUS}`}
                  >
                    <img
                      alt=""
                      width={112}
                      height={80}
                      className="h-20 w-28 shrink-0 rounded-xl object-cover"
                      src={booking.vehicle.image}
                    />
                    <div className="min-w-0 flex-1 space-y-1">
                      <p
                        className="font-mono text-xs font-bold tracking-wide text-[#14221A]"
                        translate="no"
                      >
                        {booking.id}
                      </p>
                      <h2 className="truncate text-base font-bold text-[#14221A]">
                        {booking.vehicle.name}
                        {booking.vehicle.year ? ` (${booking.vehicle.year})` : ''}
                      </h2>
                      <p className="text-sm text-[#14221A]/80">{booking.formattedStart}</p>
                      <p className="truncate text-sm text-[#14221A]/70">{booking.pickupAddress}</p>
                      {booking.atRisk ? (
                        <p className="text-xs font-semibold text-amber-800">Xe có nguy cơ về trễ</p>
                      ) : null}
                    </div>
                    <p className="shrink-0 text-base font-bold tabular-nums text-[#14221A] sm:text-right">
                      {booking.totalAmount.toLocaleString('vi-VN')}&nbsp;đ
                    </p>
                  </Link>

                  <div className="flex min-w-0 shrink-0 flex-col items-stretch gap-2 sm:flex-row sm:items-center sm:justify-end">
                    <div className="flex min-w-0 flex-col items-start gap-1.5 sm:items-end">
                      <span
                        className={`inline-flex max-w-full items-center rounded-full border px-3 py-1 text-xs font-semibold whitespace-nowrap ${meta.badgeClass}`}
                      >
                        {holdExpired ? 'Hết hạn giữ chỗ' : meta.label}
                      </span>
                      {showCountdown ? <HoldCountdown expiresAt={booking.holdExpiresAt} compact /> : null}
                    </div>
                    {action ? (
                      <button
                        type="button"
                        onClick={() => onPayDeposit(booking)}
                        className={`cursor-pointer whitespace-nowrap rounded-xl px-4 py-2.5 text-sm font-medium shadow-xs transition-colors ${FOCUS} ${
                          action.tone === 'renew'
                            ? 'border border-brand-700 bg-white text-brand-800 hover:bg-brand-50'
                            : 'bg-brand-700 text-white hover:bg-brand-800'
                        }`}
                      >
                        {action.label}
                      </button>
                    ) : null}
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      ) : (
        <div className="flex flex-col items-center justify-center rounded-xl border border-[#E6EBE8] bg-white px-4 py-16 text-center shadow-card">
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-brand-50 text-brand-700">
            <span className="material-symbols-outlined text-[32px]" aria-hidden="true">
              directions_car
            </span>
          </div>
          <h3 className="mb-1 text-lg font-bold text-[#14221A]">Chưa có đơn ở trạng thái này</h3>
          <p className="mb-6 max-w-sm text-sm text-[#14221A]/70">
            Bạn chưa có đơn thuê xe nào trong danh mục này.
          </p>
          <button
            onClick={onBookCar}
            className={`cursor-pointer rounded-xl bg-brand-700 px-6 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-brand-800 ${FOCUS}`}
            type="button"
          >
            Khám phá &amp; Đặt xe
          </button>
        </div>
      )}

      {total > 0 && !loading ? (
        <div className="flex flex-col gap-3 rounded-xl border border-[#E6EBE8] bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm tabular-nums text-[#14221A]/70">
            Hiển thị {page * pageSize + 1}–{Math.min((page + 1) * pageSize, total)} trong {total} đơn
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => onPageChange(Math.max(0, page - 1))}
              className={`rounded-lg border border-[#DDE5E0] px-3 py-2 text-sm font-medium text-[#14221A] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 ${FOCUS}`}
            >
              Trang trước
            </button>
            <span className="min-w-20 text-center text-sm font-semibold tabular-nums text-[#14221A]">
              Trang {page + 1}/{Math.max(1, Math.ceil(total / pageSize))}
            </span>
            <button
              type="button"
              disabled={!hasMore}
              onClick={() => onPageChange(page + 1)}
              className={`rounded-lg border border-[#DDE5E0] px-3 py-2 text-sm font-medium text-[#14221A] hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 ${FOCUS}`}
            >
              Trang sau
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
