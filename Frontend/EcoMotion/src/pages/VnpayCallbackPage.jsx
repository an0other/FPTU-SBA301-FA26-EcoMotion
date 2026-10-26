import { useState, useEffect } from 'react'
import { useSearchParams, Link, useNavigate } from 'react-router-dom'
import { paymentService } from '../services/paymentService'

function formatVnd(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

export default function VnpayCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    async function verifyCallback() {
      // Gather all query parameters returned by VNPAY
      const params = {}
      for (const [key, value] of searchParams.entries()) {
        params[key] = value
      }

      if (Object.keys(params).length === 0) {
        setError('Không nhận được thông tin phản hồi từ cổng thanh toán VNPAY.')
        setLoading(false)
        return
      }

      try {
        setLoading(true)
        setError(null)
        // Strictly verify with Backend HMAC validation & booking state update
        const data = await paymentService.processVnpayCallback(params)
        setResult(data)
      } catch (err) {
        console.error('Failed to verify VNPAY callback:', err)
        setError(
          err?.response?.data?.message ||
          err?.message ||
          'Chữ ký bảo mật không hợp lệ hoặc không thể xác minh giao dịch với ngân hàng.'
        )
      } finally {
        setLoading(false)
      }
    }

    verifyCallback()
  }, [searchParams])

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-4">
        <div className="w-full max-w-md rounded-lg bg-white border border-slate-200 p-7 text-center shadow-xs space-y-4">
          <Icon name="sync" className="animate-spin text-4xl text-brand-700 mx-auto" />
          <h2 className="text-base font-bold text-slate-900">
            Đang xác minh giao dịch với VNPAY...
          </h2>
          <p className="text-xs text-slate-500">
            Hệ thống đang kiểm tra chữ ký điện tử HMAC-SHA512 và xác nhận thanh toán với ngân hàng. Vui lòng không đóng trình duyệt.
          </p>
        </div>
      </div>
    )
  }

  const isSuccess = result?.success === true

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 sm:p-7 shadow-xs text-center space-y-5">
        {/* Status Icon */}
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full">
          {isSuccess ? (
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
              <Icon name="check_circle" className="text-4xl" />
            </span>
          ) : (
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-red-100 text-red-600">
              <Icon name="cancel" className="text-4xl" />
            </span>
          )}
        </div>

        {/* Title & Subtitle */}
        <div>
          <span className="rounded-full bg-slate-100 px-3 py-1 font-mono text-[11px] font-bold text-slate-600 border border-slate-200 inline-block mb-2">
            VNPAY SANDBOX TEST ENVIRONMENT
          </span>
          <h1 className="text-xl font-bold text-slate-900 sm:text-2xl">
            {isSuccess ? 'Thanh toán thành công!' : 'Thanh toán không thành công'}
          </h1>
          <p className="mt-1 text-xs text-slate-500">
            {isSuccess
              ? 'Giao dịch thanh toán của bạn đã được xác minh và ghi nhận an toàn vào hệ thống.'
              : error || result?.message || 'Giao dịch bị từ chối hoặc đã bị hủy bởi người dùng.'}
          </p>
        </div>

        {/* Receipt Card */}
        {result && (
          <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4 text-xs space-y-2.5 text-left">
            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Mã đơn đặt xe:</span>
              <span className="font-mono font-bold text-brand-800 text-sm">
                #{result.bookingCode || searchParams.get('vnp_TxnRef')?.split('-')[0] || '—'}
              </span>
            </div>

            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Số tiền thanh toán:</span>
              <span className="font-mono font-bold text-slate-900 text-sm">
                {formatVnd(result.amount || Number(searchParams.get('vnp_Amount')) / 100)}
              </span>
            </div>

            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Mã giao dịch VNPAY:</span>
              <span className="font-mono font-semibold text-slate-800">
                {result.vnpTxnRef || searchParams.get('vnp_TransactionNo') || '—'}
              </span>
            </div>

            {result.bankCode && (
              <div className="flex justify-between items-center border-b border-slate-200 pb-2">
                <span className="text-slate-500">Ngân hàng phát hành:</span>
                <span className="font-bold text-slate-800">{result.bankCode}</span>
              </div>
            )}

            <div className="flex justify-between items-center">
              <span className="text-slate-500">Trạng thái xác thực:</span>
              <span className={`font-bold ${isSuccess ? 'text-emerald-700' : 'text-red-700'}`}>
                {isSuccess ? 'Đã xác minh bởi máy chủ EcoMotion' : 'Chưa được ghi nhận'}
              </span>
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex flex-wrap justify-center gap-3 pt-2">
          {isSuccess && result?.bookingCode ? (
            <Link
              to={`/renter/bookings/${result.bookingCode}`}
              className="rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition"
            >
              Xem chi tiết đơn thuê
            </Link>
          ) : (
            <Link
              to={result?.bookingCode ? `/renter/bookings/${result.bookingCode}/payment` : '/renter/bookings'}
              className="rounded-lg bg-brand-700 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition"
            >
              Thử lại thanh toán
            </Link>
          )}

          <Link
            to="/"
            className="rounded-lg border border-slate-200 bg-white px-5 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
          >
            Về trang chủ
          </Link>
        </div>
      </div>
    </div>
  )
}
