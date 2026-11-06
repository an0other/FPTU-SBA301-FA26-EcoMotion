import { useEffect, useState } from 'react'
import { useLocation, useSearchParams, Link } from 'react-router-dom'
import { paymentService } from '../services/paymentService'
import { formatPaymentMoney, formatPaymentDate } from '../utils/paymentDisplay'
import { useRealtimeSync } from '../hooks/useRealtimeSync'

const TITLES = {
  SUCCESS: 'Thanh toán thành công!',
  FAILED: 'Thanh toán không thành công',
  PENDING_VERIFICATION: 'Đang xác minh thanh toán',
  RECONCILIATION_REQUIRED: 'Đã nhận tiền, cần đối soát đơn thuê',
}

export default function PaymentSuccessPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const reference = searchParams.get('reference') || location.state?.reference
    || searchParams.get('vnpTxnRef') || searchParams.get('transactionCode')
    || location.state?.vnpTxnRef || location.state?.transactionCode || ''
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ key: null, receipt: null, error: null })
  const key = `${reference}:${attempt}`

  useEffect(() => {
    if (!reference) return undefined
    const controller = new AbortController()
    let cancelled = false
    paymentService.getReceipt(reference, controller.signal).then(receipt => {
      if (cancelled) return
      if (!TITLES[receipt?.status]) throw new Error('Máy chủ chưa trả về kết quả thanh toán hợp lệ.')
      setState({ key, receipt, error: null })
    }).catch(error => {
      if (!cancelled) setState({ key, receipt: null, error: error?.message || 'Không thể tải biên lai thanh toán.' })
    })
    return () => { cancelled = true; controller.abort() }
  }, [reference, key])

  const loading = Boolean(reference) && state.key !== key
  const error = !reference ? 'Thiếu mã tham chiếu để kiểm tra thanh toán.' : state.key === key ? state.error : null
  const receipt = state.key === key && !error ? state.receipt : null
  const success = receipt?.status === 'SUCCESS'
  const canRecheckReceipt = Boolean(reference) && !loading && !success
  const title = loading ? 'Đang kiểm tra thanh toán' : error ? 'Không thể xác minh thanh toán' : TITLES[receipt?.status]
  const bookingPath = receipt?.bookingCode ? `/renter/bookings/${encodeURIComponent(receipt.bookingCode)}` : null

  useRealtimeSync({
    enabled: Boolean(reference) && state.receipt?.status === 'PENDING_VERIFICATION',
    pollIntervalMs: 5000,
    onResync: () => setAttempt(value => value + 1),
  })

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm text-center space-y-6" aria-live="polite">
        <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
        {loading && <p>Đang lấy trạng thái từ máy chủ EcoMotion.</p>}
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        {receipt && !success && (
          <p className="text-sm text-slate-600">{receipt.status === 'FAILED'
            ? 'Máy chủ ghi nhận giao dịch không thành công. Vui lòng kiểm tra đơn thuê.'
            : 'Vui lòng không thanh toán lại trong lúc chờ xác minh hoặc đối soát.'}</p>
        )}
        {success && (
          <>
            <p className="text-sm text-slate-600">{receipt.verificationMethod === 'GATEWAY_VERIFIED'
              ? 'Giao dịch đã được cổng thanh toán xác minh và máy chủ ghi nhận.'
              : 'Khoản chuyển khoản đã được xác nhận thủ công và máy chủ ghi nhận.'}</p>
            <dl className="rounded-xl border border-slate-200 bg-slate-50 p-5 text-left text-sm space-y-4" aria-label="Biên lai thanh toán">
              <div className="flex justify-between items-center"><dt className="text-slate-500">Mã đơn hàng</dt><dd className="font-mono font-bold break-all text-slate-900">{receipt.bookingCode}</dd></div>
              {receipt.transactionCode && <div className="flex justify-between items-center"><dt className="text-slate-500">Mã thanh toán nội bộ</dt><dd className="font-mono font-bold text-brand-700">{receipt.transactionCode}</dd></div>}
              {receipt.vnpTransactionNo && <div className="flex justify-between items-center"><dt className="text-slate-500">Mã giao dịch VNPAY</dt><dd className="font-mono break-all text-slate-900">{receipt.vnpTransactionNo}</dd></div>}
              <div className="flex justify-between items-center"><dt className="text-slate-500">Số tiền đã thanh toán</dt><dd className="text-lg font-bold text-emerald-700">{formatPaymentMoney(receipt.amount)}</dd></div>
              <div className="flex justify-between items-center"><dt className="text-slate-500">Trạng thái</dt><dd className="inline-flex items-center gap-1.5 font-bold text-emerald-700"><span className="h-2 w-2 rounded-full bg-emerald-500"></span>Đã thanh toán</dd></div>
              <div className="flex justify-between items-center"><dt className="text-slate-500">Phương thức</dt><dd className="font-medium text-slate-800">{receipt.paymentMethod === 'VNPAY' ? 'Cổng thanh toán VNPAY' : 'Chuyển khoản ngân hàng'}</dd></div>
              <div className="flex justify-between items-center"><dt className="text-slate-500">Thời gian thanh toán</dt><dd className="font-medium text-slate-800">{formatPaymentDate(receipt.paidAt)}</dd></div>
            </dl>
          </>
        )}
        <div className="flex flex-wrap justify-center gap-3">
          {canRecheckReceipt && (
            <button type="button" onClick={() => setAttempt(value => value + 1)} className="rounded-lg border px-4 py-2">
              Kiểm tra lại
            </button>
          )}
          {bookingPath && <Link to={bookingPath} className="rounded-lg bg-brand-700 px-4 py-2 text-white">Xem chi tiết đơn thuê</Link>}
          <Link to="/renter/bookings" className="rounded-lg border px-4 py-2">Đơn của tôi</Link>
          <Link to="/" className="rounded-lg border px-4 py-2">Về trang chủ</Link>
        </div>
      </div>
    </div>
  )
}
