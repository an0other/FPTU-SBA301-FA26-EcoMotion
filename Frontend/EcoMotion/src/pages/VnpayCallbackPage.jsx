import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { paymentService } from '../services/paymentService'

/**
 * Landing page after VNPAY redirects the browser back. The backend verifies the signed return and
 * records the result; this page shows that recorded result: success, failure, or an error when the
 * return cannot be verified. It never shows a "waiting" state for a normal return.
 */
export default function VnpayCallbackPage() {
  const [searchParams] = useSearchParams()
  const query = searchParams.toString()
  const hasReference = Boolean(searchParams.get('vnp_TxnRef'))
  const [state, setState] = useState(() => (hasReference
    ? { loading: true, result: null, error: null }
    : { loading: false, result: null, error: 'Không nhận được thông tin giao dịch từ VNPay.' }))

  useEffect(() => {
    if (!hasReference) return undefined
    let cancelled = false
    const controller = new AbortController()
    const params = Object.fromEntries(new URLSearchParams(query))

    paymentService.processVnpayCallback(params, controller.signal)
      .then((result) => {
        if (!cancelled) setState({ loading: false, result, error: null })
      })
      .catch((err) => {
        if (!cancelled) setState({ loading: false, result: null, error: err?.response?.data?.message || err?.message || 'Không thể xác nhận kết quả thanh toán.' })
      })

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [query, hasReference])

  const { loading, result, error } = state
  const success = result?.status === 'SUCCESS'
  const failed = result?.status === 'FAILED'
  const reconciliation = result?.status === 'RECONCILIATION_REQUIRED'
  const bookingPath = result?.bookingCode ? `/renter/bookings/${encodeURIComponent(result.bookingCode)}` : null

  if (loading) {
    return (
      <Shell tone="neutral" icon="progress_activity" title="Đang tải kết quả thanh toán">
        <p className="text-sm text-slate-600">Vui lòng chờ trong giây lát.</p>
      </Shell>
    )
  }

  if (success) {
    return (
      <Shell tone="success" icon="check_circle" title="Thanh toán thành công">
        <p className="text-sm text-slate-600">Đơn thuê đã ghi nhận khoản thanh toán của bạn.</p>
        <p className="text-3xl font-extrabold tabular-nums text-emerald-700">{formatVnd(result.amount)}</p>
        <Details rows={[
          ['Mã đơn thuê', result.bookingCode],
          ['Mã giao dịch', result.transactionCode],
          ['Mã giao dịch VNPay', result.vnpTransactionNo || null],
        ]} />
        <Actions>
          <Link to="/renter/bookings" className={SECONDARY}>Đơn của tôi</Link>
          {bookingPath
            ? <Link to={bookingPath} className={PRIMARY}>Xem chi tiết đơn</Link>
            : <Link to="/" className={PRIMARY}>Về trang chủ</Link>}
        </Actions>
      </Shell>
    )
  }

  if (failed || reconciliation) {
    return (
      <Shell
        tone={failed ? 'failed' : 'reconciliation'}
        icon={failed ? 'cancel' : 'receipt_long'}
        title={failed ? 'Thanh toán thất bại' : 'Đã nhận tiền, cần đối soát đơn thuê'}
      >
        <p className="text-sm text-slate-600">
          {failed ? 'Giao dịch chưa được thanh toán thành công.' : 'Khoản tiền đã được ghi nhận và sẽ được đối soát với đơn thuê.'}
        </p>
        <Details rows={[
          ['Mã đơn thuê', result.bookingCode],
          ['Số tiền', formatVnd(result.amount)],
          ['Lý do', failed ? result.message : null],
        ]} />
        <Actions>
          {bookingPath && <Link to={bookingPath} className={PRIMARY}>Quay lại đơn thuê</Link>}
          <Link to="/" className={SECONDARY}>Về trang chủ</Link>
        </Actions>
      </Shell>
    )
  }

  // Invalid or unverifiable return: nothing is confirmed, so the page says so instead of claiming success.
  return (
    <Shell tone="error" icon="error" title="Không xác nhận được kết quả thanh toán">
      <p className="text-sm text-slate-600">{error || result?.message || 'Kết quả trả về từ VNPay không hợp lệ.'}</p>
      <Actions>
        <Link to="/renter/bookings" className={PRIMARY}>Đơn của tôi</Link>
        <Link to="/" className={SECONDARY}>Về trang chủ</Link>
      </Actions>
    </Shell>
  )
}

const PRIMARY = 'rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-brand-800'
const SECONDARY = 'rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50'

const TONE = {
  success: 'text-emerald-700 bg-emerald-100',
  failed: 'text-red-700 bg-red-100',
  error: 'text-red-700 bg-red-100',
  reconciliation: 'text-purple-700 bg-purple-100',
  neutral: 'text-slate-700 bg-slate-100',
}

function Shell({ tone, icon, title, children }) {
  return (
    // Nằm trong BookingShell (đã có header): không dùng min-h-screen để tránh khoảng trắng và thanh cuộn thừa.
    <div className="flex justify-center px-4 py-10 sm:py-16">
      <div className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white text-center shadow-sm" aria-live="polite">
        <div className={`h-1.5 w-full ${BAR[tone]}`} aria-hidden="true" />
        <div className="space-y-5 p-6 sm:p-8">
        <span className={`material-symbols-rounded mx-auto flex h-16 w-16 items-center justify-center rounded-full leading-none ${TONE[tone]}`}
          // Stylesheet Google Fonts đặt font-size 24px ngoài layer, lấn class Tailwind — dùng inline style.
          style={{ fontSize: 40, fontVariationSettings: "'FILL' 1" }} aria-hidden="true">
          {icon}
        </span>
        <div className="space-y-2">
          <h1 className="text-xl font-bold text-slate-900 sm:text-2xl">{title}</h1>
          {children}
        </div>
        </div>
      </div>
    </div>
  )
}

const BAR = {
  success: 'bg-emerald-500',
  failed: 'bg-red-500',
  error: 'bg-red-500',
  reconciliation: 'bg-purple-500',
  neutral: 'bg-slate-300',
}

function Details({ rows }) {
  const visible = rows.filter(([, value]) => value != null && value !== '')
  if (visible.length === 0) return null
  return (
    <dl className="mt-4 divide-y divide-slate-200 rounded-xl border border-slate-200 bg-slate-50 px-4 text-left text-sm">
      {visible.map(([label, value]) => (
        <div key={label} className="flex items-center justify-between gap-3 py-2.5">
          <dt className="text-slate-600">{label}</dt>
          <dd className="font-mono font-bold text-slate-900 break-all text-right">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Actions({ children }) {
  return <div className="flex flex-col-reverse justify-center gap-3 pt-2 sm:flex-row">{children}</div>
}

function formatVnd(value) {
  return `${Number(value ?? 0).toLocaleString('vi-VN')} ₫`
}
