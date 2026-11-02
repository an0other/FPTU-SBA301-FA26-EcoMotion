import { useEffect, useRef } from 'react'
import { useBookingCharges } from '../../hooks/useBookingCharges'
import BookingPaymentSummary from './BookingPaymentSummary'
import PaymentHistoryTable from './PaymentHistoryTable'
import {
  BOOKING_STATUS_LABELS,
  CHARGE_TYPE_LABELS,
  formatAppliedRate,
  formatChargeDescription,
  formatChargeQuantity,
  formatPaymentMoney,
} from '../../utils/paymentDisplay'

const PLAN_LABELS = {
  HOURLY: 'Theo giờ',
  DAILY: 'Theo ngày',
  MONTHLY: 'Theo tháng',
  YEARLY: 'Theo năm',
}

export default function BookingChargeBreakdown({ bookingCode, onClose }) {
  const { data, loading, error, retry } = useBookingCharges(bookingCode)
  const closeButtonRef = useRef(null)
  const dialogRef = useRef(null)

  useEffect(() => {
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose()
        return
      }

      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = dialogRef.current.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      )
      if (focusable.length === 0) return

      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    closeButtonRef.current?.focus()

    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus()
      }
    }
  }, [onClose])

  const costs = data?.charges?.filter(
    (charge) => charge.chargeType !== 'DEPOSIT',
  ) ?? []

  const deposits = data?.charges?.filter(
    (charge) => charge.chargeType === 'DEPOSIT',
  ) ?? []
  const yearly = data?.planType === 'YEARLY'

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-950/50 p-3 backdrop-blur-[2px] sm:items-center sm:p-6"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="booking-charge-heading"
        aria-busy={loading}
        className="my-auto w-full max-w-6xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
      >
      <div className="flex items-start justify-between gap-4 border-b border-slate-100 p-5 sm:p-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-700">
            Chi tiết thanh toán đơn thuê
          </p>
          <h2
            id="booking-charge-heading"
            className="mt-1 text-lg font-bold text-slate-900"
          >
            {bookingCode}
          </h2>

          {data && (
            <>
              <p className="mt-2 text-sm font-medium text-slate-800">
                {data.vehicleName}
                <span className="mx-2 text-slate-300">·</span>
                {PLAN_LABELS[data.planType] ?? data.planType}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {BOOKING_STATUS_LABELS[data.bookingStatus] ?? data.bookingStatus}
              </p>
            </>
          )}
        </div>

        <button
          ref={closeButtonRef}
          type="button"
          aria-label="Đóng chi tiết khoản phí"
          onClick={onClose}
          className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          Đóng
        </button>
      </div>

      {loading && (
        <p role="status" className="p-8 text-center text-sm text-slate-500">
          Đang tải thông tin thanh toán…
        </p>
      )}

      {error && (
        <div role="alert" className="p-6">
          <p className="text-sm text-on-error-container">{error}</p>
          <button
            type="button"
            onClick={retry}
            className="mt-3 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold"
          >
            Thử lại
          </button>
        </div>
      )}

      {!loading && !error && data && (
        <div>
          <div className="p-5 sm:p-6">
            <BookingPaymentSummary
              summary={data.summary}
              planType={data.planType}
            />
          </div>

          <div className="border-t border-slate-100">
            <div className="px-5 py-4 sm:px-6">
              <h3 className="font-bold text-slate-900">
                {yearly ? 'Tiền thuê và các khoản phụ phí' : 'Chi tiết tiền thuê và phụ phí'}
              </h3>
              <p className="mt-1 text-xs text-slate-500">
                {yearly
                  ? 'Hiển thị tổng tiền thuê theo hợp đồng và các khoản phụ phí đã ghi nhận; khoản trả trước là một phần của tiền thuê. Mức mỗi kỳ là tham chiếu, kỳ cuối có thể được điều chỉnh để khớp tổng tiền.'
                  : 'Các khoản dưới đây đã ghi nhận cho đơn thuê. Khoản cọc được trình bày riêng và không cộng thêm vào tổng chi phí.'}
              </p>
            </div>

            {costs.length === 0 ? (
              <p className="px-6 pb-6 text-sm text-slate-500">
                Chưa có khoản chi phí được ghi nhận.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[750px] text-left text-sm">
                  <caption className="sr-only">
                    Chi phí của đơn {bookingCode}
                  </caption>

                  <thead className="bg-slate-50 text-xs text-slate-500">
                    <tr>
                      <th scope="col" className="px-5 py-4">Khoản phí</th>
                      <th scope="col" className="px-5 py-4">{yearly ? 'Số kỳ' : 'Số lượng'}</th>
                      <th scope="col" className="px-5 py-4 text-right">
                        {yearly ? 'Mức tham chiếu mỗi kỳ' : 'Đơn giá / cơ sở tính'}
                      </th>
                      <th scope="col" className="px-5 py-4 text-right">Tỷ lệ phụ thu</th>
                      <th scope="col" className="px-5 py-4 text-right">Tổng khoản phí</th>
                      <th scope="col" className="px-5 py-4">Trạng thái</th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-100">
                    {costs.map((charge, index) => (
                      <tr key={`${charge.chargeType}-${charge.createdAt}-${index}`}>
                        <td className="max-w-xs px-5 py-5">
                          <p className="font-semibold text-slate-800">
                            {CHARGE_TYPE_LABELS[charge.chargeType] ?? charge.chargeType}
                          </p>
                          <p className="mt-1 break-words text-xs leading-relaxed text-slate-500">
                            {formatChargeDescription(charge, data.planType)}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-5 py-5 text-slate-600">
                          {formatChargeQuantity({ ...charge, planType: data.planType })}
                        </td>

                        <td className="whitespace-nowrap px-5 py-5 text-right tabular-nums">
                          {formatPaymentMoney(charge.unitPrice)}
                        </td>

                        <td className="px-5 py-5 text-right tabular-nums">
                          {formatAppliedRate(charge.appliedRate)}
                        </td>

                        <td
                          className={`whitespace-nowrap px-5 py-5 text-right font-bold tabular-nums ${
                            charge.waived
                              ? 'text-slate-400 line-through'
                              : 'text-slate-900'
                          }`}
                        >
                          {formatPaymentMoney(charge.amount)}
                        </td>

                        <td className="whitespace-nowrap px-5 py-5 text-xs">
                          {charge.waived ? 'Đã miễn' : 'Đã ghi nhận'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {deposits.length > 0 && (
            <div className="border-t border-slate-100 p-5 sm:p-6">
              <h3 className="font-bold text-slate-900">
                {yearly ? 'Khoản trả trước theo hợp đồng' : 'Tiền đặt cọc'}
              </h3>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">
                {yearly
                  ? 'Khoản trả trước theo hợp đồng là một phần của tổng tiền thuê gói năm, không phải phụ phí.'
                  : 'Tiền cọc là khoản bảo đảm, có thể được hoàn trả hoặc đối trừ theo điều kiện hợp đồng; đây không phải phụ phí.'}
                {' '}Trạng thái thanh toán được thể hiện trong lịch sử giao dịch.
              </p>

              <div className="mt-4 space-y-3">
                {deposits.map((charge, index) => (
                  <div
                    key={`${charge.createdAt}-${index}`}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-100 bg-brand-50/60 p-4"
                  >
                    <div>
                      <p className="text-sm font-semibold text-brand-900">
                        {yearly
                          ? charge.waived ? 'Khoản trả trước được miễn' : 'Khoản trả trước theo hợp đồng'
                          : charge.waived ? 'Tiền cọc được miễn' : 'Tiền cọc theo đơn thuê'}
                      </p>
                      <p className="mt-1 text-xs text-slate-600">
                        {charge.appliedRate != null ? (
                          <>
                            Cách tính: {formatPaymentMoney(charge.unitPrice)}
                            {' × '}{formatAppliedRate(charge.appliedRate)}
                          </>
                        ) : (
                          <>Cơ sở tính: {formatPaymentMoney(charge.unitPrice)}</>
                        )}
                      </p>
                    </div>

                    <div className="text-right">
                      <p
                        className={`font-bold tabular-nums ${
                          charge.waived
                            ? 'text-slate-400 line-through'
                            : 'text-brand-800'
                        }`}
                      >
                        {formatPaymentMoney(charge.amount)}
                      </p>
                      {charge.waived && (
                        <p className="mt-1 text-xs text-brand-800">
                          Không yêu cầu thanh toán
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="border-t border-slate-100">
            <div className="px-5 py-4 sm:px-6">
              <h3 className="font-bold text-slate-900">
                Giao dịch của đơn này
              </h3>
              <p className="mt-1 text-xs text-slate-500">
                Chỉ giao dịch thành công được tính vào tổng tiền đã thanh toán hoặc đã hoàn.
              </p>
            </div>

            {data.transactions?.length > 0 ? (
              <PaymentHistoryTable
                items={data.transactions}
                planType={data.planType}
              />
            ) : (
              <p className="px-6 pb-6 text-sm text-slate-500">
                Chưa có giao dịch được ghi nhận.
              </p>
            )}
          </div>
        </div>
      )}
      </section>
    </div>
  )
}