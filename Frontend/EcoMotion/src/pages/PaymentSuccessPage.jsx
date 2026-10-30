import { useLocation, useSearchParams, Link, useNavigate } from 'react-router-dom'
import { formatPaymentMoney, formatPaymentDate } from '../utils/paymentDisplay'

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">
      {name}
    </span>
  )
}

export default function PaymentSuccessPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const state = location.state || {}

  const bookingCode = state.bookingCode || searchParams.get('bookingCode') || ''
  const transactionCode = state.transactionCode || searchParams.get('transactionCode') || ''
  const vnpTransactionNo = state.vnpTransactionNo || searchParams.get('vnpTransactionNo') || searchParams.get('vnp_TransactionNo') || ''
  const rawAmount = state.amount || searchParams.get('amount') || 0
  const amount = Number(rawAmount)
  const paymentMethod = (state.paymentMethod || searchParams.get('paymentMethod') || searchParams.get('method') || 'VNPAY').toUpperCase()
  const paidAt = state.paidAt || searchParams.get('paidAt') || new Date().toISOString()

  const methodLabel = paymentMethod === 'VNPAY' ? 'Cổng thanh toán VNPay' : 'Chuyển khoản VietQR'
  const bookingPath = bookingCode ? `/renter/bookings/${encodeURIComponent(bookingCode)}` : '/renter/bookings'

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm text-center space-y-6 animate-fade-in">
        {/* Success Icon */}
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 ring-8 ring-emerald-50">
          <Icon name="check_circle" className="text-5xl" />
        </div>

        {/* Title */}
        <div>
          <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">Thanh toán thành công!</h1>
          <p className="mt-2 text-sm text-slate-600">
            Giao dịch thanh toán của bạn đã được máy chủ EcoMotion xác minh và ghi nhận thành công.
          </p>
        </div>

        {/* Receipt Card */}
        <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-5 text-left text-sm space-y-3.5">
          {bookingCode && (
            <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
              <span className="text-slate-500 text-xs">Mã đơn thuê</span>
              <span className="font-mono font-bold text-slate-900 break-all">{bookingCode}</span>
            </div>
          )}

          {transactionCode && (
            <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
              <span className="text-slate-500 text-xs">Mã thanh toán nội bộ</span>
              <span className="font-mono font-bold text-brand-700 bg-brand-50 px-2 py-0.5 rounded border border-brand-200">
                {transactionCode}
              </span>
            </div>
          )}

          {vnpTransactionNo && (
            <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
              <span className="text-slate-500 text-xs">Mã giao dịch cổng (VNPay)</span>
              <span className="font-mono text-slate-700 break-all">{vnpTransactionNo}</span>
            </div>
          )}

          <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
            <span className="text-slate-500 text-xs">Số tiền thanh toán</span>
            <span className="text-lg font-black text-slate-900">
              {formatPaymentMoney(amount)}
            </span>
          </div>

          <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
            <span className="text-slate-500 text-xs">Phương thức</span>
            <span className="font-medium text-slate-800">{methodLabel}</span>
          </div>

          <div className="flex items-center justify-between gap-3 border-b border-slate-200/70 pb-3">
            <span className="text-slate-500 text-xs">Thời gian thanh toán</span>
            <span className="text-xs text-slate-700">{formatPaymentDate(paidAt)}</span>
          </div>

          <div className="flex items-center justify-between gap-3 pt-0.5">
            <span className="text-slate-500 text-xs">Trạng thái</span>
            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-bold text-emerald-800">
              <Icon name="check" className="text-xs" />
              Đã thanh toán
            </span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row justify-center gap-3 pt-2">
          {bookingCode && (
            <Link
              to={bookingPath}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-brand-700 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-brand-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
            >
              <Icon name="receipt" className="text-base" />
              Xem chi tiết đơn thuê
            </Link>
          )}

          <Link
            to="/renter/bookings"
            className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-brand-200 bg-brand-50 px-5 py-2.5 text-xs font-bold text-brand-800 hover:bg-brand-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
          >
            <Icon name="list_alt" className="text-base" />
            Về danh sách đơn
          </Link>

          <Link
            to="/"
            className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-300"
          >
            Về trang chủ
          </Link>
        </div>
      </div>
    </div>
  )
}
