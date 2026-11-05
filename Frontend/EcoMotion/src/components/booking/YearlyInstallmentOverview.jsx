import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { providerFinanceService } from '../../services/providerFinanceService'
import { useBookingCharges } from '../../hooks/useBookingCharges'
import {
  formatPaymentDate,
  formatPaymentMoney,
  isPaymentRequestExpired,
  PAYMENT_TYPE_LABELS,
} from '../../utils/paymentDisplay'

export default function YearlyInstallmentOverview({ bookingCode, onPay }) {
  const { data: charges, loading: chargesLoading, error: chargesError, retry } =
    useBookingCharges(bookingCode)
  const [requests, setRequests] = useState([])
  const [requestError, setRequestError] = useState('')
  const [requestsLoading, setRequestsLoading] = useState(true)
  const [requestRevision, setRequestRevision] = useState(0)

  useEffect(() => {
    let active = true
    providerFinanceService.getPaymentRequestsByBooking(bookingCode)
      .then((result) => {
        if (active) {
          setRequests(result)
          setRequestError('')
        }
      })
      .catch((error) => {
        if (active) setRequestError(error.message || 'Không thể tải kỳ thanh toán tiếp theo.')
      })
      .finally(() => {
        if (active) setRequestsLoading(false)
      })

    return () => { active = false }
  }, [bookingCode, requestRevision])

  const monthlyRequest = requests.find((request) =>
    request.paymentType === 'MONTHLY_INSTALLMENT'
    && ['PENDING', 'REJECTED', 'PARTIALLY_PAID'].includes(request.status))
  const rentTransactions = (charges?.transactions ?? [])
    .filter((transaction) =>
      transaction.type === 'SETTLEMENT'
      && transaction.direction === 'IN'
      && transaction.status === 'PAID')
  const totalRent = Number(charges?.summary?.recordedCost ?? 0)
  const totalPaid = Number(charges?.summary?.netPaidAmount ?? 0)
  const remainingRent = Math.max(totalRent - totalPaid, 0)
  const requestExpired = isPaymentRequestExpired(monthlyRequest)
  const canPay = monthlyRequest
    && ['PENDING', 'REJECTED', 'PARTIALLY_PAID'].includes(monthlyRequest.status)
    && !requestExpired

  return (
    <section className="rounded-2xl border border-emerald-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">
            Gói thuê năm
          </p>
          <h2 className="mt-1 text-lg font-bold text-slate-900">
            Thanh toán và lịch sử kỳ thuê
          </h2>
        </div>
        <Link
          to="/renter/payments"
          className="text-sm font-semibold text-emerald-800 underline underline-offset-4"
        >
          Lịch sử giao dịch
        </Link>
      </div>

      {(chargesLoading || requestsLoading) && (
        <p role="status" className="mt-4 text-sm text-slate-500">
          Đang tải thông tin thanh toán…
        </p>
      )}
      {(chargesError || requestError) && (
        <div role="alert" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-red-700">
          <span>{chargesError || requestError}</span>
          <button
            type="button"
            onClick={() => {
              retry()
              setRequestsLoading(true)
              setRequestRevision((revision) => revision + 1)
            }}
            className="font-semibold underline"
          >
            Tải lại
          </button>
        </div>
      )}

      {charges && (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-slate-50 p-4">
              <p className="text-xs text-slate-500">Tổng tiền thuê theo gói</p>
              <p className="mt-1 font-bold text-slate-900">{formatPaymentMoney(totalRent)}</p>
            </div>
            <div className="rounded-xl bg-emerald-50 p-4">
              <p className="text-xs text-emerald-800">Đã thanh toán</p>
              <p className="mt-1 font-bold text-emerald-900">{formatPaymentMoney(totalPaid)}</p>
            </div>
            <div className="rounded-xl bg-amber-50 p-4">
              <p className="text-xs text-amber-800">Còn lại theo tiền thuê</p>
              <p className="mt-1 font-bold text-amber-950">{formatPaymentMoney(remainingRent)}</p>
            </div>
          </div>

          <div className="mt-5 rounded-xl border border-slate-200 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-sm font-bold text-slate-900">Kỳ thanh toán kế tiếp</p>
                {monthlyRequest ? (
                  <>
                    <p className="mt-1 text-sm text-slate-700">
                      {monthlyRequest.issueNote || 'Khoản thanh toán tiền thuê theo tháng'}
                    </p>
                    <p className="mt-1 text-xs text-slate-500">
                      Số tiền: {formatPaymentMoney(monthlyRequest.remainingAmount ?? monthlyRequest.amount)}
                      {monthlyRequest.expiresAt
                        ? ` · Hạn thanh toán: ${formatPaymentDate(monthlyRequest.expiresAt)}`
                        : ''}
                    </p>
                  </>
                ) : (
                  <p className="mt-1 text-sm text-slate-600">
                    Chưa có yêu cầu thanh toán tháng tiếp theo.
                  </p>
                )}
              </div>
              {canPay && (
                <button
                  type="button"
                  onClick={onPay}
                  className="rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-800"
                >
                  Thanh toán kỳ này
                </button>
              )}
              {monthlyRequest && requestExpired && (
                <span className="text-sm font-semibold text-red-700">Yêu cầu đã hết hạn</span>
              )}
            </div>
          </div>

          <div className="mt-5">
            <h3 className="text-sm font-bold text-slate-900">Các khoản đã ghi nhận</h3>
            {rentTransactions.length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">Chưa có kỳ thuê nào được ghi nhận thanh toán.</p>
            ) : (
              <ul className="mt-2 divide-y divide-slate-100">
                {rentTransactions.map((transaction) => (
                  <li
                    key={transaction.transactionCode || `${transaction.createdAt}-${transaction.amount}`}
                    className="flex flex-wrap justify-between gap-2 py-2.5 text-sm"
                  >
                    <span className="text-slate-600">
                      {PAYMENT_TYPE_LABELS[transaction.type] ?? transaction.type}
                      {' · '}{formatPaymentDate(transaction.paidAt || transaction.createdAt)}
                    </span>
                    <strong className="text-slate-900">{formatPaymentMoney(transaction.amount)}</strong>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </section>
  )
}
