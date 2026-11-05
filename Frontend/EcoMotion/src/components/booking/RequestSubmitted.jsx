import { useEffect, useRef, useState } from 'react'
import { HoldCountdown } from '../payments/HoldCountdown'
import { DEPOSIT_HOLD_COPY } from '../../utils/bookingCopy'
import {
  isDepositAwaitingProvider,
  RECONCILIATION_DURATION_MS,
  remainingMs,
} from '../../utils/paymentDisplay'
import { bookingStatusMeta } from '../../utils/bookingStatus'
import { formatCurrency } from '../../utils/formatCurrency'

const FUEL_LABELS = {
  EV: 'Thuần điện (EV)',
  GASOLINE: 'Xăng',
  DIESEL: 'Dầu',
  HYBRID: 'Hybrid',
}

function InfoRow({ icon, label, children }) {
  return (
    <div className="flex items-start gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#E6EBE8] bg-white text-brand-700 shadow-xs">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <span className="block text-[11px] font-semibold uppercase tracking-wider text-[#14221A]/45">
          {label}
        </span>
        {children}
      </div>
    </div>
  )
}

export const RequestSubmitted = ({
  booking,
  depositPaymentStatus = null,
  onPayDeposit,
  onReissueDeposit,
  onGoToMyBookings,
  onGoToCarPage,
  onRefreshBooking,
  reissuing = false,
}) => {
  const [now, setNow] = useState(() => Date.now())
  const refreshedAfterExpiry = useRef(false)

  useEffect(() => {
    if (!booking.holdExpiresAt) return undefined
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [booking.holdExpiresAt])

  const awaitingProvider = isDepositAwaitingProvider(depositPaymentStatus)
  const holdMs = remainingMs(booking.holdExpiresAt, now)
  const holdExpired = !awaitingProvider && holdMs != null && holdMs <= 0
  const cancelled = booking.status === 'CANCELLED' && !awaitingProvider
  const canPayDeposit = Boolean(booking.canPay) && !holdExpired && !cancelled && !awaitingProvider

  useEffect(() => {
    if (!holdExpired || refreshedAfterExpiry.current || !onRefreshBooking) return
    refreshedAfterExpiry.current = true
    onRefreshBooking()
  }, [holdExpired, onRefreshBooking])

  useEffect(() => {
    if (!holdExpired) refreshedAfterExpiry.current = false
  }, [booking.holdExpiresAt, booking.canPay, booking.status])

  const showReissue = holdExpired && !cancelled && !awaitingProvider && typeof onReissueDeposit === 'function'
  const orderStatusMeta = bookingStatusMeta(booking.status)

  const title = awaitingProvider
    ? 'Đã gửi biên lai — chờ nhà xe xác nhận cọc'
    : cancelled
      ? 'Đơn đã hủy do hết thời gian cọc'
      : holdExpired
        ? 'Đã hết thời gian giữ chỗ'
        : 'Lịch xe đã được giữ thành công!'

  const statusBadge = awaitingProvider
    ? { label: 'Đang đối soát', className: 'bg-blue-100 text-blue-900' }
    : cancelled
      ? { label: 'Đã hủy', className: 'bg-red-100 text-red-900' }
      : holdExpired
        ? { label: 'Hết hạn cọc', className: 'bg-red-100 text-red-900' }
        : { label: 'Chờ thanh toán cọc', className: 'bg-amber-100 text-amber-900' }

  const hint = awaitingProvider
    ? 'Lịch xe vẫn được giữ trong thời gian đối soát. Nhà xe sẽ xác nhận chuyển khoản; bạn không cần gửi lại biên lai trừ khi bị từ chối.'
    : cancelled
      ? 'Hệ thống đã huỷ đơn và nhả lịch xe. Bạn có thể đặt lại xe khác hoặc xem chi tiết trong đơn của tôi.'
      : holdExpired
        ? 'Thời hạn 15 phút đã qua. Thanh toán cọc tạm thời bị khoá; cấp lại yêu cầu thanh toán nếu xe vẫn còn trống.'
        : `${DEPOSIT_HOLD_COPY.submitHint} ${DEPOSIT_HOLD_COPY.confirmedWhen}`

  const headerTone = awaitingProvider
    ? 'bg-blue-50 text-blue-700'
    : cancelled || holdExpired
      ? 'bg-red-50 text-red-700'
      : 'bg-brand-50 text-brand-700'

  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <nav className="flex items-center gap-2 text-xs text-[#64748B] sm:text-sm">
        <button type="button" onClick={onGoToCarPage} className="transition-colors hover:text-brand-700">
          Trang chủ
        </button>
        <svg className="h-3 w-3 text-[#64748B]/50" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clipRule="evenodd" />
        </svg>
        <button type="button" onClick={onGoToMyBookings} className="transition-colors hover:text-brand-700">
          Đơn của tôi
        </button>
        <svg className="h-3 w-3 text-[#64748B]/50" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clipRule="evenodd" />
        </svg>
        <span className="font-medium text-[#14221A]">Đơn {booking.id}</span>
      </nav>

      <div className="relative mx-auto w-full max-w-2xl overflow-hidden rounded-2xl border border-[#E6EBE8] bg-white p-6 shadow-sm sm:p-10">
        <div className={`absolute inset-x-0 top-0 h-1 ${awaitingProvider ? 'bg-blue-500' : cancelled || holdExpired ? 'bg-red-500' : 'bg-brand-700'}`} />

        <div className="flex justify-center">
          <div className={`flex h-16 w-16 items-center justify-center rounded-full shadow-xs ${headerTone}`}>
            {awaitingProvider ? (
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            ) : cancelled || holdExpired ? (
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12V12.75z" />
              </svg>
            ) : (
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" strokeWidth="2.5" stroke="currentColor" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
              </svg>
            )}
          </div>
        </div>

        <div className="mt-5 space-y-3 text-center">
          <h1 className="text-xl font-bold tracking-tight text-[#14221A] sm:text-2xl">
            {title}
          </h1>

          <div className="flex flex-wrap items-center justify-center gap-2">
            <span className="rounded-lg border border-[#E6EBE8] bg-[#F7F8F6] px-3 py-1 font-mono text-2xl font-bold tracking-wider text-[#14221A]">
              {booking.id}
            </span>
            <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${statusBadge.className}`}>
              {!cancelled && !holdExpired && !awaitingProvider ? <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-amber-600" /> : null}
              {statusBadge.label}
            </span>
            {orderStatusMeta.label !== statusBadge.label ? (
              <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${orderStatusMeta.badgeClass}`}>
                Đơn: {orderStatusMeta.label}
              </span>
            ) : null}
          </div>

          <p className="mx-auto max-w-md text-sm leading-relaxed text-[#14221A]/65">
            {hint}
          </p>

          {booking.holdExpiresAt && !cancelled ? (
            <div className="flex justify-center pt-1">
              <HoldCountdown
                expiresAt={booking.holdExpiresAt}
                variant={awaitingProvider ? 'reconciliation' : 'hold'}
                durationMs={awaitingProvider ? RECONCILIATION_DURATION_MS : undefined}
              />
            </div>
          ) : null}
        </div>

        <div className="my-6 space-y-3 rounded-xl border border-[#E6EBE8] bg-[#F7F8F6] p-5">
          <InfoRow
            label="Phương tiện"
            icon={
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 18.75a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m3 0H21M3.375 14.25h-.375m0 0V10.5m0 3.75h.375m0 0h4.875m4.875 0h4.875m-4.875 0V10.5m4.875 3.75h.375m0 0V10.5m0 0l-1.5-5.25h-9l-1.5 5.25" />
              </svg>
            }
          >
            <span className="block truncate text-sm font-bold text-[#14221A]">
              {booking.vehicle.name} ({booking.vehicle.year})
            </span>
            <span className="block text-xs text-[#14221A]/55">
              {booking.vehicle.plateNumber}
              {booking.vehicle.fuelType ? ` · ${FUEL_LABELS[booking.vehicle.fuelType] ?? booking.vehicle.fuelType}` : ''}
            </span>
          </InfoRow>

          <div className="h-px bg-[#E6EBE8]" />

          <InfoRow
            label="Bắt đầu dự kiến"
            icon={
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
              </svg>
            }
          >
            <span className="block text-sm font-bold text-[#14221A]">
              {booking.formattedStart || '—'}
            </span>
          </InfoRow>

          <InfoRow
            label="Kết thúc dự kiến"
            icon={
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            }
          >
            <span className="block text-sm font-bold text-[#14221A]">
              {booking.formattedEnd || '—'}
            </span>
            <span className="block text-xs text-[#14221A]/55">
              {booking.durationLabel || (booking.slots + ' slot liên tiếp')}
              {booking.pricingDescription ? ` · ${booking.pricingDescription}` : ''}
              {booking.planType === 'YEARLY' ? ' · theo hợp đồng' : ' · kèm tài xế'}
            </span>
          </InfoRow>

          {booking.totalAmount != null && booking.totalAmount > 0 ? (
            <>
              <div className="h-px bg-[#E6EBE8]" />
              <InfoRow
                label="Tổng tiền thuê"
                icon={
                  <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 013 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 00-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 01-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 003 15h-.75M15 10.5a3 3 0 11-6 0 3 3 0 016 0zm3 0h.008v.008H18V10.5zm-12 0h.008v.008H6V10.5z" />
                  </svg>
                }
              >
                <span className="block text-lg font-bold tabular-nums text-[#14221A]">
                  {formatCurrency(booking.totalAmount)}
                </span>
              </InfoRow>
            </>
          ) : null}

          <div className="h-px bg-[#E6EBE8]" />

          <InfoRow
            label="Điểm đón"
            icon={
              <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
              </svg>
            }
          >
            <span className="block text-sm font-medium text-[#14221A]">
              {booking.pickupAddress}
            </span>
          </InfoRow>

          <div className="h-px bg-[#E6EBE8]" />

          <div className="rounded-xl border border-brand-100 bg-white p-4 shadow-xs">
            <div className="flex items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18.75a60.07 60.07 0 0115.797 2.101c.727.198 1.453-.342 1.453-1.096V18.75M3.75 4.5v.75A.75.75 0 013 6h-.75m0 0v-.375c0-.621.504-1.125 1.125-1.125H20.25M2.25 6v9m18-10.5v.75c0 .414.336.75.75.75h.75m-1.5-1.5h.375c.621 0 1.125.504 1.125 1.125v9.75c0 .621-.504 1.125-1.125 1.125h-.375m1.5-1.5H21a.75.75 0 00-.75.75v.75m0 0H3.75m0 0h-.375a1.125 1.125 0 01-1.125-1.125V15m1.5 1.5v-.75A.75.75 0 003 15h-.75M15 10.5a3 3 0 11-6 0 3 3 0 016 0zm3 0h.008v.008H18V10.5zm-12 0h.008v.008H6V10.5z" />
                </svg>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline justify-between gap-1">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-[#14221A]/45">
                    Tiền cọc cần thanh toán
                  </span>
                  <span className="text-lg font-bold tabular-nums text-brand-700">
                    {booking.depositAmount.toLocaleString('vi-VN')} đ
                  </span>
                </div>
                <p className="mt-0.5 text-xs leading-relaxed text-[#14221A]/55">
                  Cọc giữ chỗ — trả trước và được <strong className="font-semibold text-[#14221A]/70">trừ vào tổng chi phí</strong>, không phải khoản cộng thêm ngoài tổng tiền thuê.
                  {booking.totalAmount > 0 ? (
                    <span className="mt-1 block">
                      Tổng thuê ước tính {formatCurrency(booking.totalAmount)}
                      {booking.totalAmount > booking.depositAmount
                        ? ` · còn lại dự kiến sau cọc ${formatCurrency(booking.totalAmount - booking.depositAmount)}`
                        : ''}
                      .
                    </span>
                  ) : null}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="flex flex-col items-center justify-center gap-3 sm:flex-row sm:flex-wrap">
          {canPayDeposit ? (
            <button
              onClick={onPayDeposit}
              className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-brand-700 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-800 focus-visible:ring-2 focus-visible:ring-brand-500 sm:w-auto"
              type="button"
            >
              Thanh toán cọc
            </button>
          ) : null}
          {showReissue ? (
            <button
              type="button"
              disabled={reissuing}
              onClick={onReissueDeposit}
              className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-brand-600 bg-white px-6 py-3 text-sm font-semibold text-brand-800 transition-colors hover:bg-brand-50 disabled:opacity-50 sm:w-auto"
            >
              {reissuing ? 'Đang cấp lại...' : 'Cấp lại yêu cầu thanh toán'}
            </button>
          ) : null}
          <button
            onClick={onGoToMyBookings}
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-brand-600 bg-white px-6 py-3 text-sm font-semibold text-brand-700 transition-colors hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500 sm:w-auto"
            type="button"
          >
            Xem đơn của tôi
          </button>
          <button
            onClick={onGoToCarPage}
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-[#E6EBE8] bg-white px-6 py-3 text-sm font-semibold text-[#14221A] transition-colors hover:bg-[#F7F8F6] focus-visible:ring-2 focus-visible:ring-brand-500 sm:w-auto"
            type="button"
          >
            Về trang xe
          </button>
        </div>

        <div className="mt-6 text-center">
          <p className="text-xs text-[#14221A]/50">
            Cần hỗ trợ?{' '}
            <a href="tel:19008899" className="font-semibold text-brand-700 hover:underline">
              1900 8899
            </a>
          </p>
        </div>
      </div>
    </div>
  )
}
