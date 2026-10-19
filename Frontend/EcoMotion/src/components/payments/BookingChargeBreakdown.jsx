import { useBookingCharges } from '../../hooks/useBookingCharges'
import BookingPaymentSummary from './BookingPaymentSummary'
import PaymentHistoryTable from './PaymentHistoryTable'
import {
  BOOKING_STATUS_LABELS,
  CHARGE_TYPE_LABELS,
  formatAppliedRate,
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

  const costs = data?.charges?.filter(
    (charge) => charge.chargeType !== 'DEPOSIT',
  ) ?? []

  const deposits = data?.charges?.filter(
    (charge) => charge.chargeType === 'DEPOSIT',
  ) ?? []

  return (
    <section
      aria-labelledby="booking-charge-heading"
      aria-busy={loading}
      className="overflow-hidden rounded-xl border border-brand-200 bg-white shadow-card"
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
          type="button"
          onClick={onClose}
          className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
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
                Chi phí thuê và phụ phí
              </h3>
              <p className="mt-1 text-xs text-slate-500">
                Tổng chi phí không bao gồm dòng cọc và các khoản đã miễn.
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
                      <th scope="col" className="px-5 py-4">Số lượng</th>
                      <th scope="col" className="px-5 py-4 text-right">
                        Đơn giá / cơ sở tính
                      </th>
                      <th scope="col" className="px-5 py-4 text-right">Tỷ lệ</th>
                      <th scope="col" className="px-5 py-4 text-right">Thành tiền</th>
                      <th scope="col" className="px-5 py-4">Áp dụng</th>
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
                            {charge.description}
                          </p>
                        </td>

                        <td className="whitespace-nowrap px-5 py-5 text-slate-600">
                          {formatChargeQuantity(charge)}
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
                          {charge.waived ? 'Đã miễn' : 'Đang áp dụng'}
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
                Cọc giữ chỗ
              </h3>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">
                Cọc là phần trả trước của tiền thuê, không phải chi phí cộng thêm.
                Việc đã trả cọc hay chưa được đối chiếu trong giao dịch bên dưới.
              </p>

              <div className="mt-4 space-y-3">
                {deposits.map((charge, index) => (
                  <div
                    key={`${charge.createdAt}-${index}`}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-100 bg-brand-50/60 p-4"
                  >
                    <div>
                      <p className="text-sm font-semibold text-brand-900">
                        {charge.waived ? 'Cọc được miễn' : 'Cọc yêu cầu'}
                      </p>
                      <p className="mt-1 text-xs text-slate-600">
                        Cơ sở tính: {formatPaymentMoney(charge.unitPrice)}
                        {charge.appliedRate != null
                          ? ` × ${formatAppliedRate(charge.appliedRate)}`
                          : ''}
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
                Chỉ giao dịch thành công được tính vào số tiền đã thanh toán hoặc đã hoàn.
              </p>
            </div>

            {data.transactions?.length > 0 ? (
              <PaymentHistoryTable
                items={data.transactions}
                selectedBookingCode={bookingCode}
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
  )
}