import { useState, useEffect, useCallback } from 'react'
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom'
import Toast from '../components/Toast'
import { BookingErrorDialog } from '../components/booking/BookingErrorDialog'
import { HoldCountdown } from '../components/payments/HoldCountdown'
import { DepositProofForm } from '../components/payments/DepositProofForm'
import { providerFinanceService } from '../services/providerFinanceService'
import { paymentService } from '../services/paymentService'
import {
  formatPaymentMoney,
  isDepositAwaitingProvider,
  isPaymentRequestExpired,
  RECONCILIATION_DURATION_MS,
} from '../utils/paymentDisplay'
import { getMyBooking } from '../services/bookingService'
import { bookingWorkflowService } from '../services/bookingWorkflowService'
import { formatBookingWhen } from '../utils/bookingDateTime'
import { rentalDurationLabel } from '../utils/rentalPricing'
import { BookingOrderSummaryCard } from '../components/booking/BookingOrderSummaryCard'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">{name}</span>
}

const STATUS_BADGES = {
  PENDING: { label: 'Chờ thanh toán cọc', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROOF_SUBMITTED: { label: 'Đang đối soát', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  PARTIALLY_PAID: { label: 'Thanh toán một phần', bg: 'bg-orange-50 text-orange-800 border-orange-200' },
  CONFIRMED: { label: 'Đã xác nhận thanh toán', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Bị từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
  EXPIRED: { label: 'Hết hạn', bg: 'bg-slate-100 text-slate-700 border-slate-300' },
  REVIEW_REQUIRED: { label: 'Cần đối soát', bg: 'bg-purple-50 text-purple-800 border-purple-200' },
  VOIDED: { label: 'Đã huỷ yêu cầu', bg: 'bg-slate-100 text-slate-700 border-slate-300' },
}

export default function CustomerPaymentPage() {
  const { bookingCode } = useParams()
  const location = useLocation()
  const yearlyUpfront = new URLSearchParams(location.search).get('yearly') === 'true'
  const bookingIdentifier = String(bookingCode ?? '').trim().toUpperCase()
  const navigate = useNavigate()

  const [requests, setRequests] = useState([])
  const [selectedRequest, setSelectedRequest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })
  const [currentTime, setCurrentTime] = useState(() => Date.now())
  const [payMethod, setPayMethod] = useState('vietqr')
  const [paymentCreating, setPaymentCreating] = useState(false)
  const [inlineError, setInlineError] = useState(null)
  const [dismissedLoadError, setDismissedLoadError] = useState(null)
  const [submittingProof, setSubmittingProof] = useState(false)
  const [bookingSummary, setBookingSummary] = useState(null)

  const fetchPaymentRequests = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true)
      setError(null)
      setDismissedLoadError(null)
      const data = await providerFinanceService.getPaymentRequestsByBooking(bookingIdentifier)
      setRequests(data || [])
      if (data && data.length > 0) {
        const payableStatuses = ['PENDING', 'REJECTED', 'PARTIALLY_PAID']
        const activeMonthly = data.find((request) => request.paymentType === 'MONTHLY_INSTALLMENT'
          && payableStatuses.includes(request.status))
        const activeFinal = data.find((request) => request.paymentType === 'FINAL_BALANCE'
          && payableStatuses.includes(request.status))
        const activeDeposit = data.find((request) => request.paymentType === 'DEPOSIT'
          && payableStatuses.includes(request.status))
        const current = activeMonthly || activeFinal || activeDeposit
          || data.find((request) => request.status !== 'VOIDED') || data[0]
        setSelectedRequest(current)
      }
    } catch (err) {
      setError(err.message || 'Không thể tải thông tin thanh toán đơn thuê.')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [bookingIdentifier, navigate])

  useEffect(() => {
    if (bookingIdentifier) {
      const task = window.setTimeout(fetchPaymentRequests, 0)
      return () => window.clearTimeout(task)
    }
    return undefined
  }, [bookingIdentifier, fetchPaymentRequests])

  useEffect(() => {
    if (!bookingIdentifier) return undefined
    let cancelled = false
    Promise.all([
      getMyBooking(bookingIdentifier),
      bookingWorkflowService.get(bookingIdentifier).catch(() => null),
    ]).then(([detail, workflow]) => {
      if (cancelled || !detail) return
      if (detail.planType === 'YEARLY') {
        navigate('/renter/bookings/' + encodeURIComponent(bookingIdentifier), { replace: true })
        return
      }
      const hours = detail.endTime && detail.pickupTime
        ? (new Date(detail.endTime) - new Date(detail.pickupTime)) / 3600000
        : null
      setBookingSummary({
        status: detail.status,
        paymentStatus: workflow?.paymentStatus ?? detail.paymentStatus,
        formattedStart: formatBookingWhen(detail.pickupTime),
        formattedEnd: formatBookingWhen(detail.endTime),
        durationLabel: hours != null ? rentalDurationLabel(hours) : '',
        totalAmount: detail.totalAmount,
        depositAmount: detail.depositAmount,
        confirmedPaid: workflow?.confirmedPaid,
        remainingAmount: workflow?.remainingAmount,
        pickupFee: detail.pickupFee,
        charges: (detail.charges ?? []).map((charge) => ({
          chargeId: charge.chargeId,
          chargeType: charge.chargeType,
          label: charge.chargeType === 'OUT_OF_RADIUS' ? 'Phí đón ngoài phạm vi' : charge.description,
          description: charge.description,
          amount: Number(charge.effectiveAmount ?? charge.amount),
          isWaived: Boolean(charge.isWaived),
        })),
      })
    }).catch(() => {
      if (!cancelled) setBookingSummary(null)
    })
    return () => { cancelled = true }
  }, [bookingIdentifier, navigate])

  // Auto-polling while request is pending or reviewing
  useEffect(() => {
    const terminal = ['CONFIRMED', 'VOIDED'].includes(selectedRequest?.status)
    if (!selectedRequest || terminal) return undefined
    const timer = window.setInterval(() => fetchPaymentRequests(true), 5000)
    const handleFocus = () => fetchPaymentRequests(true)
    window.addEventListener('focus', handleFocus)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', handleFocus)
    }
  }, [fetchPaymentRequests, selectedRequest])

  useEffect(() => {
    if (selectedRequest?.status === 'CONFIRMED') {
      navigate(`/payment/success?reference=${encodeURIComponent(selectedRequest.paymentCode || '')}`, {
        replace: true,
      })
    }
  }, [selectedRequest, navigate])

  useEffect(() => {
    const expiresAt = selectedRequest?.expiresAt
      ? new Date(selectedRequest.expiresAt).getTime()
      : Number.NaN
    if (!Number.isFinite(expiresAt) || expiresAt <= currentTime) return undefined
    const timer = window.setTimeout(
      () => setCurrentTime(Date.now()),
      Math.min(Math.max(expiresAt - Date.now() + 25, 0), 2_147_000_000),
    )
    return () => window.clearTimeout(timer)
  }, [currentTime, selectedRequest?.expiresAt])

  const handleCopy = (text, label) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    setToast({ message: `Đã sao chép ${label}`, type: 'success' })
  }

  const handlePayWithVnpay = async () => {
    if (paymentCreating || !bookingIdentifier) return
    setPaymentCreating(true)
    setInlineError(null)
    try {
      const isFinal = selectedRequest?.paymentType === 'FINAL_BALANCE'
      const isMonthlyInstallment = selectedRequest?.paymentType === 'MONTHLY_INSTALLMENT'
      const res = await paymentService.createVnpayPaymentUrl({
        bookingCode: bookingIdentifier,
        paymentType: isMonthlyInstallment
          ? 'MONTHLY_INSTALLMENT'
          : isFinal ? 'FINAL_PAYMENT' : 'DEPOSIT',
        language: 'vn',
      })
      if (res?.paymentUrl) {
        window.location.href = res.paymentUrl
        return
      }
      throw new Error('Cổng VNPAY không trả về đường dẫn thanh toán hợp lệ.')
    } catch (err) {
      setInlineError(
        err?.response?.data?.message
        || err?.original?.response?.data?.message
        || err?.message
        || 'Không thể kết nối cổng thanh toán VNPAY.'
      )
      setPaymentCreating(false)
    }
  }

  const handleSubmitProof = async ({ file, transactionReference, note }) => {
    if (!selectedRequest?.id || submittingProof) return
    setSubmittingProof(true)
    try {
      await providerFinanceService.submitPaymentProof(selectedRequest.id, {
        file,
        transactionReference: transactionReference || undefined,
        note: note || undefined,
      })
      await fetchPaymentRequests(true)
      setCurrentTime(Date.now())
      setToast({
        message: selectedRequest.paymentType === 'FINAL_BALANCE'
          ? 'Đã gửi biên lai quyết toán. Nhà xe sẽ đối soát và xác nhận hoàn tất đơn.'
          : 'Đã gửi biên lai. Hệ thống gia hạn giữ chỗ; nhà xe sẽ đối soát và xác nhận cọc.',
        type: 'success',
      })
    } catch (err) {
      setToast({ message: err.message || 'Không thể gửi biên lai', type: 'error' })
    } finally {
      setSubmittingProof(false)
    }
  }

  const handleReissue = async () => {
    try {
      await providerFinanceService.reissueDepositPayment(bookingIdentifier)
      await fetchPaymentRequests()
      setCurrentTime(Date.now())
      setToast({
        message: yearlyUpfront
          ? 'Đã cấp lại yêu cầu thanh toán khoản trả trước'
          : 'Đã cấp lại yêu cầu thanh toán cọc',
        type: 'success',
      })
    } catch (err) {
      setToast({ message: err.message || 'Không thể cấp lại yêu cầu thanh toán', type: 'error' })
    }
  }

  const bookingHref = `/renter/bookings/${encodeURIComponent(bookingIdentifier)}`

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6">
        <div className="h-16 animate-pulse rounded-2xl bg-slate-200 motion-reduce:animate-none" />
        <div className="mt-6 h-80 animate-pulse rounded-2xl bg-slate-200 motion-reduce:animate-none" />
      </div>
    )
  }

  if (error || !selectedRequest) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <BookingErrorDialog
          title="Chưa thể tiếp tục thanh toán"
          message={inlineError || (error === dismissedLoadError ? '' : error)}
          onClose={() => { setInlineError(null); setDismissedLoadError(error) }}
          onRetry={() => { setInlineError(null); fetchPaymentRequests() }}
        />
        <div className="rounded-2xl border border-red-200 bg-white p-8 max-w-md w-full text-center shadow-lg">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <Icon name="error_outline" className="text-2xl" />
          </div>
          <h2 className="mt-3 text-lg font-bold text-slate-900">Không tìm thấy yêu cầu thanh toán</h2>
          <p className="mt-1 text-xs text-slate-500">{error ? 'Bạn có thể tải lại thông tin thanh toán hoặc quay về chi tiết đơn.' : 'Chưa có yêu cầu thanh toán nào cho đơn thuê này.'}</p>
          <div className="mt-6 flex justify-center gap-3">
            <Link to="/" className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200">
              Về trang chủ
            </Link>
            {bookingIdentifier ? <Link to={`/renter/bookings/${encodeURIComponent(bookingIdentifier)}`} className="rounded-lg border border-emerald-200 px-4 py-2 text-xs font-semibold text-emerald-700">Chi tiết đơn</Link> : null}
            <button
              type="button"
              onClick={fetchPaymentRequests}
              className="rounded-lg bg-[#059669] px-4 py-2 text-xs font-semibold text-white hover:bg-[#047857]"
            >
              Thử lại
            </button>
          </div>
        </div>
      </div>
    )
  }

  const isDeposit = selectedRequest.paymentType === 'DEPOSIT'
  const isFinalBalance = selectedRequest.paymentType === 'FINAL_BALANCE'
  const isMonthlyInstallment = selectedRequest.paymentType === 'MONTHLY_INSTALLMENT'
  const awaitingProvider = isDepositAwaitingProvider(selectedRequest?.status)
  const requestExpired = isPaymentRequestExpired(selectedRequest)
  const countdownExpiresAt = awaitingProvider
    ? (selectedRequest.reconciliationDeadline || selectedRequest.expiresAt)
    : selectedRequest.expiresAt
  const canSubmitProof = (isDeposit || isFinalBalance || isMonthlyInstallment)
    && ['PENDING', 'REJECTED', 'PARTIALLY_PAID', 'EXPIRED'].includes(selectedRequest.status)
  const paymentTitle = isMonthlyInstallment
    ? 'Thanh toán kỳ thuê tháng'
    : isDeposit
      ? yearlyUpfront ? 'Thanh toán khoản trả trước gói năm' : 'Thanh toán tiền đặt cọc'
      : isFinalBalance ? 'Thanh toán quyết toán' : 'Thanh toán đơn thuê'
  const currentBadge = isMonthlyInstallment && selectedRequest.status === 'PENDING'
    ? { label: 'Chờ thanh toán kỳ tháng', bg: STATUS_BADGES.PENDING.bg }
    : yearlyUpfront && selectedRequest.status === 'PENDING'
      ? { label: 'Chờ thanh toán trả trước', bg: STATUS_BADGES.PENDING.bg }
    : STATUS_BADGES[selectedRequest.status]
    || { label: selectedRequest.status, bg: 'bg-slate-100 text-slate-700 border-slate-300' }

  return (
    <div className="min-h-screen bg-slate-50 pb-28 pt-6 sm:pb-16 sm:pt-10">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      <BookingErrorDialog
        title="Không thể mở cổng thanh toán VNPAY"
        message={inlineError}
        onClose={() => setInlineError(null)}
        onRetry={() => { setInlineError(null); handlePayWithVnpay() }}
      />

      <div className="mx-auto max-w-3xl px-4 sm:px-6">
        <header className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Đơn thuê</p>
              <h1 className="mt-0.5 text-xl font-extrabold text-[#14221A] sm:text-2xl">#{selectedRequest.bookingCode}</h1>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-bold ${currentBadge.bg}`}>
                {currentBadge.label}
              </span>
              <Link to={bookingHref} className="inline-flex items-center gap-1 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">
                <Icon name="arrow_back" className="text-sm" /> Chi tiết đơn
              </Link>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600">
            <div>
              <span className="text-slate-400">Khoản mục: </span>
              <strong className="text-slate-900">{paymentTitle}</strong>
            </div>
            {selectedRequest.paymentCode && (
              <div>
                <span className="text-slate-400">Mã thanh toán: </span>
                <strong className="font-mono text-brand-700 font-bold bg-brand-50 px-2 py-0.5 rounded border border-brand-200">
                  {selectedRequest.paymentCode}
                </strong>
              </div>
            )}
            {countdownExpiresAt && !['CONFIRMED', 'VOIDED'].includes(selectedRequest.status) && (
              <HoldCountdown
                expiresAt={countdownExpiresAt}
                variant={awaitingProvider ? 'reconciliation' : 'hold'}
                durationMs={awaitingProvider ? RECONCILIATION_DURATION_MS : undefined}
              />
            )}
          </div>
        </header>

        {selectedRequest.status === 'REJECTED' ? (
          <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-950">
            <p className="font-bold">Nhà xe đã từ chối biên lai chuyển khoản</p>
            {selectedRequest.rejectionReason ? (
              <p className="mt-1 text-xs text-rose-900/90">Lý do: {selectedRequest.rejectionReason}</p>
            ) : null}
            <p className="mt-2 text-xs leading-relaxed text-rose-900/85">
              Đơn vẫn giữ chỗ trong thời gian còn lại. Bạn có thể thanh toán lại bằng VNPAY hoặc chuyển khoản và tải biên lai mới.
            </p>
          </div>
        ) : null}

        {bookingSummary ? (
          <div className="mt-4">
            <BookingOrderSummaryCard
              bookingCode={bookingIdentifier}
              {...bookingSummary}
            />
          </div>
        ) : null}

        {requests.length > 1 && (
          <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
            {requests.map((req) => (
              <button
                type="button"
                key={req.id}
                onClick={() => setSelectedRequest(req)}
                className={`whitespace-nowrap rounded-xl border px-3.5 py-1.5 text-xs font-semibold transition ${
                  selectedRequest.id === req.id
                    ? 'border-brand-600 bg-brand-50 text-brand-900 font-bold shadow-xs'
                    : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                }`}
              >
                {req.paymentType === 'MONTHLY_INSTALLMENT'
                  ? 'Kỳ thuê tháng'
                  : req.paymentType === 'DEPOSIT'
                    ? yearlyUpfront ? 'Trả trước gói năm' : 'Tiền cọc'
                    : 'Quyết toán'} · {formatPaymentMoney(req.amount)}
              </button>
            ))}
          </div>
        )}

        <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Chọn phương thức thanh toán</p>
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <button
              type="button"
              onClick={() => setPayMethod('vietqr')}
              className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${
                payMethod === 'vietqr'
                  ? 'border-brand-600 bg-brand-50/50 shadow-xs ring-1 ring-brand-600'
                  : 'border-slate-200 bg-white hover:border-slate-300'
              }`}
            >
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-800">
                <Icon name="qr_code_2" className="text-2xl" />
              </div>
              <div>
                <strong className="block text-sm text-[#14221A]">Chuyển khoản VietQR</strong>
                <span className="mt-0.5 block text-xs text-slate-500">Quét mã QR qua ứng dụng ngân hàng, tự động đối soát</span>
              </div>
            </button>

            <button
              type="button"
              onClick={() => setPayMethod('vnpay')}
              className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${
                payMethod === 'vnpay'
                  ? 'border-brand-600 bg-brand-50/50 shadow-xs ring-1 ring-brand-600'
                  : 'border-slate-200 bg-white hover:border-slate-300'
              }`}
            >
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-800">
                <Icon name="credit_card" className="text-2xl" />
              </div>
              <div>
                <strong className="block text-sm text-[#14221A]">VNPAY Sandbox</strong>
                <span className="mt-0.5 block text-xs text-slate-500">Thẻ ATM / QR VNPay / Thẻ quốc tế</span>
              </div>
            </button>
          </div>
        </div>

        {payMethod === 'vietqr' ? (
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2 md:items-start">
              {/* QR Code */}
              <div className="flex flex-col items-center justify-center rounded-xl border border-slate-200 bg-slate-50 p-6 text-center">
                {selectedRequest.qrCodeUrl ? (
                  <img
                    src={selectedRequest.qrCodeUrl}
                    alt="VietQR Code"
                    className="h-64 w-64 max-w-full rounded-lg object-contain shadow-xs bg-white p-2"
                  />
                ) : (
                  <div className="flex h-64 w-64 items-center justify-center rounded-lg bg-slate-200 text-slate-400">
                    <Icon name="qr_code" className="text-6xl" />
                  </div>
                )}
                <p className="mt-3 text-xs font-semibold text-slate-600">Quét mã bằng app ngân hàng bất kỳ</p>
                <span className="text-[11px] text-slate-400">Số tiền và nội dung đã được nhúng sẵn</span>
              </div>

              {/* Bank Details */}
              <div className="space-y-4">
                <div>
                  <span className="text-xs text-slate-500">Ngân hàng thụ hưởng</span>
                  <div className="mt-0.5 flex items-center gap-2">
                    <span className="text-base font-bold text-[#14221A]">{selectedRequest.bankNameSnapshot}</span>
                    <span className="rounded bg-brand-100 px-2 py-0.5 font-mono text-[11px] font-bold text-brand-800">
                      {selectedRequest.bankCodeSnapshot}
                    </span>
                  </div>
                </div>

                <div>
                  <span className="text-xs text-slate-500">Chủ tài khoản</span>
                  <div className="mt-0.5 text-sm font-bold uppercase tracking-wide text-[#14221A]">
                    {selectedRequest.accountHolderSnapshot}
                  </div>
                </div>

                <div>
                  <span className="text-xs text-slate-500">Số tài khoản</span>
                  <div className="mt-0.5 flex items-center gap-2">
                    <span className="font-mono text-lg font-bold tracking-wider text-brand-700">
                      {selectedRequest.accountNumberSnapshot || selectedRequest.accountNumberMasked}
                    </span>
                    <button type="button" onClick={() => handleCopy(selectedRequest.accountNumberSnapshot, 'số tài khoản')} className="rounded p-1 text-slate-400 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Sao chép số tài khoản">
                      <Icon name="content_copy" className="text-base" />
                    </button>
                  </div>
                </div>

                <div>
                  <span className="text-xs text-slate-500">Số tiền thanh toán</span>
                  <div className="mt-0.5 flex items-center gap-2">
                    <span className="text-2xl font-extrabold text-[#14221A]">
                      {formatPaymentMoney(selectedRequest.remainingAmount ?? selectedRequest.amount)}
                    </span>
                    <button type="button" onClick={() => handleCopy(String(selectedRequest.remainingAmount ?? selectedRequest.amount), 'số tiền')} className="rounded p-1 text-slate-400 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Sao chép số tiền">
                      <Icon name="content_copy" className="text-base" />
                    </button>
                  </div>
                </div>

                <div>
                  <span className="text-xs text-slate-500">Nội dung chuyển khoản (bắt buộc)</span>
                  <div className="mt-0.5 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 p-2.5">
                    <span className="font-mono text-sm font-bold text-amber-950">{selectedRequest.transferReference}</span>
                    <button type="button" onClick={() => handleCopy(selectedRequest.transferReference, 'nội dung chuyển khoản')} className="ml-auto rounded p-1 text-amber-800 hover:text-amber-950 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Sao chép nội dung chuyển khoản">
                      <Icon name="content_copy" className="text-base" />
                    </button>
                  </div>
                </div>

                {selectedRequest.paymentCode && (
                  <div>
                    <span className="text-xs text-slate-500">Mã thanh toán nội bộ (Đối soát)</span>
                    <div className="mt-0.5 flex items-center gap-2">
                      <span className="font-mono text-sm font-bold text-brand-700 bg-brand-50 px-2 py-1 rounded border border-brand-200">
                        {selectedRequest.paymentCode}
                      </span>
                      <button type="button" onClick={() => handleCopy(selectedRequest.paymentCode, 'mã thanh toán')} className="rounded p-1 text-slate-400 hover:text-slate-700" aria-label="Sao chép mã thanh toán">
                        <Icon name="content_copy" className="text-base" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {awaitingProvider ? (
              <div className="mt-6 rounded-xl border border-blue-200 bg-blue-50/80 p-4 text-xs text-blue-950 flex items-start gap-3">
                <Icon name="hourglass_top" className="text-xl text-blue-700 shrink-0 mt-0.5" />
                <div>
                  <strong className="block font-bold">Đã gửi biên lai — chờ nhà xe xác nhận</strong>
                  <p className="mt-0.5 text-blue-900">
                    {isFinalBalance
                      ? 'Nhà xe đang đối soát biên lai chuyển khoản quyết toán để xác nhận hoàn tất đơn thuê.'
                      : 'Lịch xe vẫn được giữ trong thời gian đối soát. Nút「Kiểm tra trạng thái」chỉ cập nhật trạng thái; không thay cho gửi biên lai.'}
                  </p>
                </div>
              </div>
            ) : (
              <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50/70 p-4 text-xs text-emerald-900 flex items-start gap-3">
                <Icon name="verified_user" className="text-xl text-emerald-700 shrink-0 mt-0.5" />
                <div>
                  <strong className="block font-bold">Sau khi chuyển khoản</strong>
                  <p className="mt-0.5 text-emerald-800">
                    {isFinalBalance
                      ? 'Gửi ảnh biên lai bên dưới để chuyển sang bước nhà xe đối soát và xác nhận tất toán đơn thuê.'
                      : 'Gửi ảnh biên lai bên dưới để gia hạn giữ chỗ và chuyển sang bước nhà xe đối soát. Chỉ bấm「Kiểm tra trạng thái」nếu nhà xe đã xác nhận trên hệ thống.'}
                  </p>
                </div>
              </div>
            )}

            {canSubmitProof && !awaitingProvider ? (
              <DepositProofForm
                disabled={requestExpired}
                busy={submittingProof}
                isFinalBalance={isFinalBalance}
                onSubmit={handleSubmitProof}
              />
            ) : null}
          </div>
        ) : (
          <div className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
                <Icon name="lock" className="text-2xl" />
              </div>
              <div className="space-y-2">
                <h2 className="text-base font-bold text-slate-900">Thanh toán an toàn qua VNPAY Sandbox</h2>
                <p className="text-xs text-slate-600 leading-relaxed">
                  Bạn sẽ được chuyển hướng sang cổng thanh toán VNPAY Sandbox để hoàn tất giao dịch với đúng số tiền đã chốt: <strong>{formatPaymentMoney(selectedRequest.remainingAmount ?? selectedRequest.amount)}</strong>.
                </p>
                <p className="text-xs text-slate-500">
                  Sau khi hoàn tất trên VNPAY, bạn sẽ được tự động chuyển về trang kết quả xác thực.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Action bar */}
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white/95 p-4 backdrop-blur-sm sm:static sm:mt-6 sm:border-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none">
          <div className="mx-auto flex max-w-3xl flex-col gap-3 sm:flex-row sm:justify-end">
            {requestExpired && selectedRequest.paymentType === 'DEPOSIT' ? (
              <button type="button" onClick={handleReissue} className="rounded-xl border border-brand-600 px-5 py-2.5 text-sm font-semibold text-brand-800 hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500/40">
                {yearlyUpfront ? 'Cấp lại yêu cầu trả trước' : 'Cấp lại yêu cầu thanh toán'}
              </button>
            ) : null}

            {payMethod === 'vnpay' ? (
              <button
                type="button"
                onClick={handlePayWithVnpay}
                disabled={paymentCreating || requestExpired}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-700 px-6 py-3 text-sm font-bold text-white shadow-xs hover:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
              >
                {paymentCreating ? <Icon name="sync" className="animate-spin text-lg" /> : <Icon name="account_balance" className="text-lg" />}
                {paymentCreating ? 'Đang kết nối VNPAY...' : 'Tiếp tục với VNPAY'}
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  )
}
