import { useState, useEffect } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { paymentService } from '../services/paymentService'

const POLL_INTERVAL_MS = 3000
const MAX_POLLS = 40

export default function VnpayCallbackPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.toString()
  const [state, setState] = useState({ result: null, error: null, checking: true })

  useEffect(() => {
    let cancelled = false
    let timer
    let polls = 0
    const controller = new AbortController()
    const publish = (value) => { if (!cancelled) setState(value) }

    async function poll(result) {
      try {
        const next = await paymentService.queryVnpayTransaction(result.bookingCode, result.vnpTxnRef, controller.signal)
        if (cancelled) return
        publish({ result: next, error: null, checking: false })
        polls += 1
        if (next.status === 'PENDING_VERIFICATION' && polls < MAX_POLLS) {
          timer = window.setTimeout(() => poll(next), POLL_INTERVAL_MS)
        }
      } catch (err) {
        if (cancelled) return
        polls += 1
        publish({ result, error: err?.message || 'Chưa thể kết nối để xác minh thanh toán.', checking: false })
        if (polls < MAX_POLLS) timer = window.setTimeout(() => poll(result), POLL_INTERVAL_MS)
      }
    }

    async function verify() {
      publish({ result: null, error: null, checking: true })
      const params = Object.fromEntries(new URLSearchParams(query))
      if (!params.vnp_TxnRef) {
        publish({ result: null, error: 'Không nhận được thông tin giao dịch từ VNPay.', checking: false })
        return
      }
      try {
        const result = await paymentService.processVnpayCallback(params, controller.signal)
        if (cancelled) return
        publish({ result, error: null, checking: false })
        if (result.status === 'PENDING_VERIFICATION' && result.bookingCode && result.vnpTxnRef) {
          timer = window.setTimeout(() => poll(result), POLL_INTERVAL_MS)
        }
      } catch (err) {
        publish({ result: null, error: err?.message || 'Chưa thể xác minh thanh toán. Vui lòng kiểm tra lại sau.', checking: false })
      }
    }

    void verify()
    return () => {
      cancelled = true
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [query])

  const { result, error, checking } = state
  const success = result?.status === 'SUCCESS'
  const failed = result?.status === 'FAILED'
  const reconciliation = result?.status === 'RECONCILIATION_REQUIRED'
  const title = reconciliation ? 'Đã nhận tiền, cần đối soát đơn thuê' : success ? 'Thanh toán thành công!' : failed ? 'Thanh toán không thành công' : 'Đang xác minh thanh toán'
  const color = success ? 'text-emerald-700 bg-emerald-100' : failed ? 'text-red-700 bg-red-100' : 'text-amber-700 bg-amber-100'
  const bookingPath = result?.bookingCode ? `/renter/bookings/${encodeURIComponent(result.bookingCode)}` : null

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 sm:p-7 shadow-xs text-center space-y-5" aria-live="polite">
        <span className={`material-symbols-rounded inline-flex h-16 w-16 items-center justify-center rounded-full text-4xl ${color}`} aria-hidden="true">
          {success ? 'check_circle' : failed ? 'cancel' : 'hourglass_top'}
        </span>
        <div>
          <h1 className="text-xl font-bold text-slate-900 sm:text-2xl">{title}</h1>
          <p className="mt-2 text-sm text-slate-600">
            {checking ? 'Hệ thống đang kiểm tra kết quả với máy chủ EcoMotion.' : error || result?.message}
          </p>
          {!success && !failed && !checking && (
            <p className="mt-2 text-xs text-slate-500">Vui lòng không thanh toán lại. Nếu chưa có kết quả, hãy kiểm tra trong Đơn của tôi hoặc liên hệ hỗ trợ.</p>
          )}
        </div>
        {result?.bookingCode && (
          <dl className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-left space-y-3">
            <div className="flex justify-between gap-3"><dt>Mã đơn đặt xe</dt><dd className="font-mono font-bold break-all">{result.bookingCode}</dd></div>
            <div className="flex justify-between gap-3"><dt>Số tiền thanh toán</dt><dd className="font-bold">{Number(result.amount ?? 0).toLocaleString('vi-VN')} ₫</dd></div>
            <div className="flex justify-between gap-3"><dt>Mã giao dịch VNPay</dt><dd className="font-mono break-all">{result.vnpTransactionNo || '—'}</dd></div>
            {result.bankCode && <div className="flex justify-between gap-3"><dt>Ngân hàng</dt><dd>{result.bankCode}</dd></div>}
            <div className="flex justify-between gap-3"><dt>Trạng thái xác thực</dt><dd className="font-semibold">{success ? 'Đã xác minh bởi máy chủ EcoMotion' : failed ? 'Đã xác minh thất bại' : 'Chờ đối soát'}</dd></div>
          </dl>
        )}
        <div className="flex flex-wrap justify-center gap-3 pt-2">
          {success && bookingPath && <Link to={bookingPath} className="rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-brand-800">Xem chi tiết đơn thuê</Link>}
          {failed && result?.retryAllowed === true && bookingPath && <Link to={`${bookingPath}/payment`} className="rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-brand-800">Thử lại thanh toán</Link>}
          <Link to="/renter/bookings" className="rounded-lg border border-brand-200 bg-brand-50 px-5 py-2.5 text-xs font-semibold text-brand-800 hover:bg-brand-100">Đơn của tôi</Link>
          <Link to="/" className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">Về trang chủ</Link>
        </div>
      </div>
    </div>
  )
}
