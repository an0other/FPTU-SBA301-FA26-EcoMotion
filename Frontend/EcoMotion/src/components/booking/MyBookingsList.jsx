import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  BOOKING_FILTERS,
  BOOKING_SORTS,
  bookingStatusMeta,
  canShowOperationalRisk,
  cardAction,
  isWaitingDeposit,
  listPaymentLabel,
} from '../../utils/bookingStatus'
import { formatCurrency } from '../../utils/formatCurrency'
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

function planModeLabel(planType) {
  return planType === 'YEARLY' ? 'Thuê xe tự lái theo hợp đồng' : 'Thuê xe có tài xế'
}

function BookingSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      {[0, 1, 2].map((key) => (
        <div
          key={key}
          className="grid animate-pulse gap-4 rounded-xl border border-[#E6EBE8] bg-white p-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(16rem,0.9fr)_11rem]"
        >
          <div className="flex gap-3">
            <div className="h-20 w-28 rounded-lg bg-slate-200" />
            <div className="min-w-0 flex-1 space-y-2">
              <div className="h-3 w-28 rounded bg-slate-200" />
              <div className="h-4 w-48 rounded bg-slate-100" />
              <div className="h-3 w-40 rounded bg-slate-100" />
            </div>
          </div>
          <div className="space-y-2">
            <div className="h-5 w-28 rounded-full bg-slate-100" />
            <div className="h-4 w-36 rounded bg-slate-100" />
          </div>
          <div className="h-9 w-32 rounded-lg bg-slate-100 lg:justify-self-end" />
        </div>
      ))}
    </div>
  )
}

function EmptyState({ kind, onBookCar }) {
  const copy = {
    none: {
      title: 'Chưa có đơn thuê',
      body: 'Khi bạn đặt xe, đơn sẽ hiện tại đây để theo dõi cọc, nhận xe và quyết toán.',
      action: true,
    },
    search: {
      title: 'Không khớp tìm kiếm',
      body: 'Thử mã đơn khác hoặc tên xe. Bộ lọc hiện tại vẫn đang áp dụng.',
      action: false,
    },
    group: {
      title: 'Chưa có đơn ở nhóm này',
      body: 'Không có đơn trong nhóm đang chọn. Đổi tab hoặc xem tất cả đơn.',
      action: false,
    },
  }[kind]

  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-[#E6EBE8] bg-white px-4 py-14 text-center shadow-card">
      <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-brand-50 text-brand-700">
        <span className="material-symbols-outlined text-[28px]" aria-hidden="true">
          directions_car
        </span>
      </div>
      <h3 className="mb-1 text-lg font-bold text-[#14221A]">{copy.title}</h3>
      <p className="mb-5 max-w-sm text-sm text-[#14221A]/70">{copy.body}</p>
      {copy.action ? (
        <button
          onClick={onBookCar}
          className={`cursor-pointer rounded-lg bg-brand-700 px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-brand-800 ${FOCUS}`}
          type="button"
        >
          Khám phá &amp; Đặt xe
        </button>
      ) : null}
    </div>
  )
}

function cardAccent(booking) {
  if (booking.status === 'CANCELLED' || booking.status === 'REJECTED') {
    return 'border-[#E6EBE8] border-l-[3px] border-l-red-500'
  }
  if (booking.actionKind && booking.actionKind !== 'NONE') {
    return 'border-[#E6EBE8] border-l-[3px] border-l-amber-500'
  }
  return 'border-[#E6EBE8]'
}

export const MyBookingsList = ({
  bookings,
  loading = false,
  error = '',
  activeFilter,
  keyword,
  sort = 'PICKUP',
  page,
  pageSize,
  total,
  hasMore,
  pendingActionId = '',
  onFilterChange,
  onKeywordChange,
  onSortChange,
  onPageChange,
  onCardAction,
  onBookCar,
}) => {
  const watchingHold = (bookings ?? []).some(
    (booking) => isWaitingDeposit(booking.status) && booking.holdExpiresAt,
  )
  const now = useNow(watchingHold)
  const activeFilterLabel = BOOKING_FILTERS.find((tab) => tab.id === activeFilter)?.label ?? 'Tất cả'
  const emptyKind = keyword.trim()
    ? 'search'
    : activeFilter === 'ALL'
      ? 'none'
      : 'group'

  return (
    <div className="mx-auto w-full max-w-7xl space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-pretty text-2xl font-bold tracking-tight text-[#14221A] sm:text-3xl">
            Đơn của tôi
          </h1>
          <p className="mt-1 text-sm text-[#14221A]/70">
            Nhìn một thẻ là biết chuyến đang ở đâu và việc cần làm ngay.
          </p>
        </div>
        {total > 0 ? (
          <p role="status" aria-atomic="true" className="text-sm font-medium tabular-nums text-[#14221A]/70">
            {total} đơn
          </p>
        ) : null}
      </div>

      <div className="rounded-xl border border-[#E6EBE8] bg-white p-3 shadow-xs sm:p-3.5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <label className="block w-full max-w-md">
            <span className="sr-only">Tìm đơn thuê</span>
            <div className="flex h-9 items-center gap-2 rounded-lg border border-[#DDE5E0] bg-slate-50/80 px-3 focus-within:border-brand-600 focus-within:bg-white focus-within:ring-2 focus-within:ring-brand-500/30">
              <span className="material-symbols-outlined text-[18px] text-[#14221A]/45" aria-hidden="true">
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

          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xs font-medium text-[#14221A]/65">
              Bộ lọc: {activeFilterLabel}
            </p>
            <label className="inline-flex items-center gap-2 text-xs font-medium text-[#14221A]">
              <span className="whitespace-nowrap">Sắp xếp</span>
              <select
                value={sort}
                onChange={(event) => onSortChange(event.target.value)}
                aria-label="Sắp xếp đơn thuê"
                className={`h-9 rounded-lg border border-[#DDE5E0] bg-white px-2.5 text-sm text-[#14221A] ${FOCUS}`}
              >
                {BOOKING_SORTS.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <div
          role="group"
          aria-label="Lọc theo trạng thái"
          className="mt-3 flex flex-wrap gap-1.5"
        >
          {BOOKING_FILTERS.map((tab) => {
            const isActive = activeFilter === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => onFilterChange(tab.id)}
                type="button"
                aria-pressed={isActive}
                className={`cursor-pointer whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium transition-colors ${FOCUS} ${
                  isActive
                    ? 'border border-transparent bg-brand-700 text-white shadow-xs'
                    : 'border border-[#E6EBE8] bg-white text-[#14221A] hover:border-brand-200 hover:bg-brand-50 hover:text-brand-800'
                }`}
              >
                {tab.label}
              </button>
            )
          })}
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
        <div className="flex flex-col gap-3">
          {bookings.map((booking) => {
            const meta = bookingStatusMeta(booking.status)
            const holdMs = remainingMs(booking.holdExpiresAt, now)
            const holdExpired =
              isWaitingDeposit(booking.status) && holdMs != null && holdMs <= 0
            const paymentLabel = listPaymentLabel(booking.status, {
              holdExpired,
              actionKind: booking.actionKind,
            })
            const action = cardAction(booking.actionKind)
            const badgeLabel = holdExpired ? 'Hết hạn giữ chỗ' : meta.label
            const extraPayment = paymentLabel && paymentLabel !== badgeLabel ? paymentLabel : null
            const showCountdown =
              isWaitingDeposit(booking.status)
              && Boolean(booking.holdExpiresAt)
              && !holdExpired
            const showDepositAmount =
              (booking.actionKind === 'PAY_DEPOSIT' || isWaitingDeposit(booking.status))
              && booking.depositAmount != null
            const showRisk = canShowOperationalRisk(
              booking.status,
              booking.atRisk,
              booking.riskMessage,
            )
            const busy = pendingActionId === booking.id

            return (
              <article
                key={booking.id}
                className={`rounded-xl border bg-white p-4 shadow-card transition-colors hover:border-brand-200 ${cardAccent(booking)}`}
              >
                <div className="grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(16rem,0.9fr)_11rem] lg:items-start lg:gap-5">
                  <div className="flex min-w-0 items-start gap-3 sm:gap-4">
                    <img
                      alt=""
                      width={112}
                      height={80}
                      className="h-20 w-28 shrink-0 rounded-lg object-cover"
                      src={booking.vehicle.image}
                    />
                    <div className="min-w-0 flex-1 space-y-1">
                      <p
                        className="font-mono text-[11px] font-semibold tracking-wide text-[#14221A]"
                        translate="no"
                      >
                        {booking.id}
                      </p>
                      <h2 className="truncate text-base font-bold text-[#14221A]">
                        {booking.vehicle.name}
                        {booking.vehicle.year ? ` (${booking.vehicle.year})` : ''}
                      </h2>
                      <p className="text-xs font-medium text-[#14221A]/70">
                        {planModeLabel(booking.planType)}
                      </p>
                      <p className="text-sm text-[#14221A]/80">
                        <span className="font-medium text-[#14221A]">Nhận</span>
                        {' '}
                        {booking.formattedStart || '—'}
                        <span className="px-1.5 text-[#14221A]/40" aria-hidden="true">→</span>
                        <span className="font-medium text-[#14221A]">Trả</span>
                        {' '}
                        {booking.formattedEnd || '—'}
                      </p>
                      <p className="truncate text-sm text-[#14221A]/65" title={booking.pickupAddress}>
                        {booking.pickupAddress}
                      </p>
                      {booking.driverName ? (
                        <p className="truncate text-sm text-[#14221A]/80">
                          Tài xế: {booking.driverName}
                        </p>
                      ) : null}
                    </div>
                  </div>

                  <div className="min-w-0 space-y-2">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span
                        className={`inline-flex max-w-full items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${
                          holdExpired
                            ? 'text-slate-800 bg-slate-100 border-slate-300'
                            : meta.badgeClass
                        }`}
                      >
                        {badgeLabel}
                      </span>
                      {extraPayment ? (
                        <span className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-900">
                          {extraPayment}
                        </span>
                      ) : null}
                    </div>
                    <p className="text-sm text-[#14221A]">
                      <span className="text-[#14221A]/65">Tổng tiền thuê</span>
                      <span className="ml-2 font-bold tabular-nums">
                        {formatCurrency(booking.totalAmount)}
                      </span>
                    </p>
                    {showDepositAmount ? (
                      <p className="text-sm text-[#14221A]">
                        <span className="text-[#14221A]/65">Cọc cần thanh toán</span>
                        <span className="ml-2 font-bold tabular-nums">
                          {formatCurrency(booking.depositAmount)}
                        </span>
                      </p>
                    ) : null}
                    {showCountdown ? <HoldCountdown expiresAt={booking.holdExpiresAt} compact /> : null}
                    {showRisk ? (
                      <aside className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-950">
                        <p className="font-semibold">Xe có thể chưa sẵn sàng đúng giờ</p>
                        <p className="mt-0.5">{booking.riskMessage}</p>
                        <p className="mt-0.5 text-amber-900/80">Nhà cung cấp đang kiểm tra.</p>
                      </aside>
                    ) : null}
                  </div>

                  <div className="flex flex-col gap-2 lg:items-stretch">
                    {action ? (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onCardAction(booking, action.kind)}
                        className={`cursor-pointer w-full whitespace-nowrap rounded-lg bg-brand-700 px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-brand-800 disabled:cursor-wait disabled:opacity-70 ${FOCUS}`}
                      >
                        {action.label}
                      </button>
                    ) : null}
                    <Link
                      to={`/renter/bookings/${booking.id}`}
                      className={`inline-flex w-full items-center justify-center rounded-lg border border-[#DDE5E0] px-4 py-2 text-sm font-semibold text-[#14221A] hover:bg-slate-50 ${FOCUS}`}
                    >
                      Xem chi tiết
                    </Link>
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      ) : (
        <EmptyState kind={emptyKind} onBookCar={onBookCar} />
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
