import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom'
import Toast from '../components/Toast'
import { BookingErrorDialog } from '../components/booking/BookingErrorDialog'
import { HoldCountdown } from '../components/payments/HoldCountdown'
import { providerFinanceService } from '../services/providerFinanceService'
import { formatPaymentMoney, isPaymentRequestExpired } from '../utils/paymentDisplay'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">{name}</span>
}

const STATUS_BADGES = {
  PENDING: { label: 'Chờ khách chuyển khoản', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROOF_SUBMITTED: { label: 'Chờ Provider xác nhận', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  PARTIALLY_PAID: { label: 'Thanh toán một phần', bg: 'bg-orange-50 text-orange-800 border-orange-200' },
  CONFIRMED: { label: 'Đã xác nhận thanh toán', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Bị từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
  EXPIRED: { label: 'Hết hạn', bg: 'bg-slate-100 text-slate-700 border-slate-300' },
  REVIEW_REQUIRED: { label: 'Chờ Provider xác nhận', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
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
  const [checkingStatus, setCheckingStatus] = useState(false)
  const [dismissedLoadError, setDismissedLoadError] = useState(null)

  // Proof submission states
  const [showProofModal, setShowProofModal] = useState(false)
  const [proofFile, setProofFile] = useState(null)
  const [proofPreview, setProofPreview] = useState(null)
  const [transferredAmount, setTransferredAmount] = useState('')
  const [transactionReference, setTransactionReference] = useState('')
  const [proofNote, setProofNote] = useState('')
  const [submittingProof, setSubmittingProof] = useState(false)
  const [proofError, setProofError] = useState('')
  const fileInputRef = useRef(null)

  const fetchPaymentRequests = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true)
      setError(null)
      setDismissedLoadError(null)
      const data = await providerFinanceService.getPaymentRequestsByBooking(bookingIdentifier)
      setRequests(data || [])
      if (data && data.length > 0) {
        const payableStatuses = ['PENDING', 'REJECTED', 'PARTIALLY_PAID', 'PROOF_SUBMITTED', 'REVIEW_REQUIRED']
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
  }, [bookingIdentifier])

  useEffect(() => {
    if (bookingIdentifier) {
      const task = window.setTimeout(fetchPaymentRequests, 0)
      return () => window.clearTimeout(task)
    }
    return undefined
  }, [bookingIdentifier, fetchPaymentRequests])

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

  // Clean up object url for preview
  useEffect(() => {
    return () => {
      if (proofPreview) URL.revokeObjectURL(proofPreview)
    }
  }, [proofPreview])

  const handleCopy = (text, label) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    setToast({ message: `Đã sao chép ${label}`, type: 'success' })
  }

  const handleCheckStatus = async () => {
    setCheckingStatus(true)
    try {
      await fetchPaymentRequests(true)
      setToast({ message: 'Đã cập nhật trạng thái mới nhất', type: 'success' })
    } catch {
      setToast({ message: 'Không thể kiểm tra trạng thái lúc này', type: 'error' })
    } finally {
      setCheckingStatus(false)
    }
  }

  const handleOpenProofModal = () => {
    const remaining = selectedRequest?.remainingAmount ?? selectedRequest?.amount ?? 0
    setTransferredAmount(String(remaining))
    setTransactionReference('')
    setProofNote('')
    setProofFile(null)
    if (proofPreview) URL.revokeObjectURL(proofPreview)
    setProofPreview(null)
    setProofError('')
    setShowProofModal(true)
  }

  const handleFileChange = (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setProofError('Chỉ hỗ trợ tệp định dạng hình ảnh (JPG, PNG, WebP).')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      setProofError('Dung lượng ảnh không được vượt quá 10MB.')
      return
    }
    setProofError('')
    setProofFile(file)
    if (proofPreview) URL.revokeObjectURL(proofPreview)
    setProofPreview(URL.createObjectURL(file))
  }

  const handleSubmitProof = async (e) => {
    e?.preventDefault()
    if (!proofFile) {
      setProofError('Vui lòng chọn ảnh chụp biên lai giao dịch.')
      return
    }
    const amt = Number(transferredAmount)
    if (!amt || amt <= 0) {
      setProofError('Số tiền chuyển khoản phải lớn hơn 0.')
      return
    }
    setSubmittingProof(true)
    setProofError('')
    try {
      await providerFinanceService.submitPaymentProof(selectedRequest.id, {
        file: proofFile,
        transferredAmount: amt,
        transactionReference: transactionReference.trim() || undefined,
        note: proofNote.trim() || undefined,
      })
      setToast({
        message: 'Đã nộp minh chứng chuyển khoản thành công! Nhà cung cấp sẽ đối soát và xác nhận.',
        type: 'success',
      })
      setShowProofModal(false)
      await fetchPaymentRequests(true)
    } catch (err) {
      setProofError(
        err?.response?.data?.message
        || err?.message
        || 'Không thể gửi minh chứng chuyển khoản. Vui lòng thử lại.'
      )
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
          message={error === dismissedLoadError ? '' : error}
          onClose={() => setDismissedLoadError(error)}
          onRetry={fetchPaymentRequests}
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

  const hasProof = selectedRequest.status === 'PROOF_SUBMITTED'
    || selectedRequest.status === 'REVIEW_REQUIRED'
    || Boolean(selectedRequest.firstProofAt)
    || (selectedRequest.evidences && selectedRequest.evidences.length > 0)

  const requestExpired = isPaymentRequestExpired(selectedRequest) && !hasProof
  const isDeposit = selectedRequest.paymentType === 'DEPOSIT'
  const isFinalBalance = selectedRequest.paymentType === 'FINAL_BALANCE'
  const isMonthlyInstallment = selectedRequest.paymentType === 'MONTHLY_INSTALLMENT'
  const paymentTitle = isMonthlyInstallment
    ? 'Thanh toán kỳ thuê tháng'
    : isDeposit
      ? yearlyUpfront ? 'Thanh toán khoản trả trước gói năm' : 'Thanh toán tiền đặt cọc'
      : isFinalBalance ? 'Thanh toán quyết toán' : 'Thanh toán đơn thuê'

  const currentBadge = hasProof
    ? { label: 'Chờ Provider xác nhận', bg: 'bg-blue-50 text-blue-800 border-blue-200' }
    : isMonthlyInstallment && selectedRequest.status === 'PENDING'
      ? { label: 'Chờ thanh toán kỳ tháng', bg: STATUS_BADGES.PENDING.bg }
      : yearlyUpfront && selectedRequest.status === 'PENDING'
        ? { label: 'Chờ thanh toán trả trước', bg: STATUS_BADGES.PENDING.bg }
      : STATUS_BADGES[selectedRequest.status]
      || { label: selectedRequest.status, bg: 'bg-slate-100 text-slate-700 border-slate-300' }

  const hasBankAccount = Boolean(selectedRequest.accountNumberSnapshot || selectedRequest.accountNumberMasked)
  const latestEvidence = selectedRequest.evidences?.[selectedRequest.evidences.length - 1]

  return (
    <div className="min-h-screen bg-slate-50 pb-28 pt-6 sm:pb-16 sm:pt-10">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

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
            {/* If proof submitted, stop countdown timer and show protection status */}
            {hasProof ? (
              <div className="flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-bold text-blue-800">
                <Icon name="verified_user" className="text-sm text-blue-600" />
                <span>Đã nộp minh chứng · Giữ chỗ được bảo lưu</span>
              </div>
            ) : selectedRequest.expiresAt && !['CONFIRMED', 'VOIDED'].includes(selectedRequest.status) ? (
              <HoldCountdown
                expiresAt={selectedRequest.expiresAt}
                label={isMonthlyInstallment ? 'Hạn thanh toán kỳ này' : 'Thời hạn giữ chỗ'}
              />
            ) : null}
          </div>
        </header>

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

        {/* Protection alert when proof is submitted */}
        {hasProof && (
          <div className="mt-4 flex items-start gap-3 rounded-2xl border border-blue-200 bg-blue-50/80 p-4 text-xs text-blue-900 shadow-xs">
            <Icon name="verified_user" className="text-2xl text-blue-600 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <strong className="block text-sm font-bold text-blue-950">Đã nộp biên lai chuyển khoản</strong>
              <p className="text-blue-800 leading-relaxed">
                Đơn thuê được <strong>bảo lưu và không tự động hủy</strong> trong khi nhà cung cấp xe đối soát giao dịch. Sau khi nhà cung cấp xác nhận, hệ thống sẽ tự động cập nhật đơn của bạn.
              </p>
              {selectedRequest.firstProofAt && (
                <p className="text-[11px] text-blue-600">
                  Thời gian gửi: {new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(selectedRequest.firstProofAt))}
                </p>
              )}
            </div>
          </div>
        )}

        {/* Missing bank account warning */}
        {!hasBankAccount && (
          <div className="mt-4 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900">
            <Icon name="warning" className="text-xl text-amber-600 shrink-0 mt-0.5" />
            <div>
              <strong className="font-bold">Nhà cung cấp chưa cấu hình tài khoản ngân hàng</strong>
              <p className="mt-0.5 text-amber-800">
                Vui lòng liên hệ nhà cung cấp xe để nhận thông tin số tài khoản chính xác trước khi thanh toán.
              </p>
            </div>
          </div>
        )}

        {/* Bank & VietQR Payment Box */}
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
              <p className="mt-3 text-xs font-semibold text-slate-700">Quét mã bằng app ngân hàng bất kỳ</p>
              <span className="text-[11px] text-slate-400">Số tiền và nội dung đã được nhúng sẵn chính xác</span>
            </div>

            {/* Bank Details */}
            <div className="space-y-4">
              <div>
                <span className="text-xs text-slate-500">Ngân hàng thụ hưởng</span>
                <div className="mt-0.5 flex items-center gap-2">
                  <span className="text-base font-bold text-[#14221A]">{selectedRequest.bankNameSnapshot || 'Chưa cấu hình'}</span>
                  {selectedRequest.bankCodeSnapshot && (
                    <span className="rounded bg-brand-100 px-2 py-0.5 font-mono text-[11px] font-bold text-brand-800">
                      {selectedRequest.bankCodeSnapshot}
                    </span>
                  )}
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Chủ tài khoản</span>
                <div className="mt-0.5 text-sm font-bold uppercase tracking-wide text-[#14221A]">
                  {selectedRequest.accountHolderSnapshot || 'Chưa cấu hình'}
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Số tài khoản</span>
                <div className="mt-0.5 flex items-center gap-2">
                  <span className="font-mono text-lg font-bold tracking-wider text-brand-700">
                    {selectedRequest.accountNumberSnapshot || selectedRequest.accountNumberMasked || 'Chưa cấu hình'}
                  </span>
                  {selectedRequest.accountNumberSnapshot && (
                    <button type="button" onClick={() => handleCopy(selectedRequest.accountNumberSnapshot, 'số tài khoản')} className="rounded p-1 text-slate-400 hover:text-slate-700 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Sao chép số tài khoản">
                      <Icon name="content_copy" className="text-base" />
                    </button>
                  )}
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
                  <span className="text-xs text-slate-500">Mã đối soát nội bộ</span>
                  <div className="mt-0.5 flex items-center gap-2">
                    <span className="font-mono text-xs font-bold text-slate-600 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
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

          {/* Transfer Guidance */}
          <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-700 flex items-start gap-3">
            <Icon name="info" className="text-xl text-slate-500 shrink-0 mt-0.5" />
            <div>
              <strong className="block font-bold text-slate-900">Hướng dẫn hoàn tất chuyển khoản</strong>
              <p className="mt-0.5 leading-relaxed text-slate-600">
                1. Chuyển khoản đúng số tài khoản và số tiền hiển thị ở trên.<br />
                2. Ghi chính xác nội dung chuyển khoản <code className="font-mono font-bold text-amber-800">{selectedRequest.transferReference}</code>.<br />
                3. Bấm nút <strong>&quot;Tôi đã chuyển khoản&quot;</strong> bên dưới và tải lên ảnh chụp màn hình biên lai để nhà cung cấp xác nhận.
              </p>
            </div>
          </div>

          {/* Submitted Evidence Preview */}
          {latestEvidence && (
            <div className="mt-6 border-t border-slate-100 pt-5">
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">Minh chứng đã gửi gần nhất</h3>
              <div className="mt-3 flex flex-col sm:flex-row items-start gap-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <a
                  href={`/api/v1/payment-evidences/${latestEvidence.id}/file`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="group relative block h-28 w-28 shrink-0 overflow-hidden rounded-lg border border-slate-200 bg-white"
                  title="Bấm để xem ảnh phóng to"
                >
                  <img
                    src={`/api/v1/payment-evidences/${latestEvidence.id}/file`}
                    alt="Biên lai chuyển khoản"
                    className="h-full w-full object-cover transition group-hover:scale-105"
                  />
                  <div className="absolute inset-0 flex items-center justify-center bg-black/30 opacity-0 group-hover:opacity-100 transition text-white text-[10px] font-bold">
                    Xem ảnh
                  </div>
                </a>
                <div className="flex-1 space-y-1.5 text-xs text-slate-600">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-800">Số tiền:</span>
                    <strong className="text-emerald-700">{formatPaymentMoney(latestEvidence.transferredAmount)}</strong>
                  </div>
                  {latestEvidence.transactionReference && (
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-slate-800">Mã giao dịch ngân hàng:</span>
                      <span className="font-mono">{latestEvidence.transactionReference}</span>
                    </div>
                  )}
                  {latestEvidence.note && (
                    <div className="text-slate-500 italic">
                      &ldquo;{latestEvidence.note}&rdquo;
                    </div>
                  )}
                  <div className="text-[11px] text-slate-400">
                    Trạng thái minh chứng: {latestEvidence.status === 'VERIFIED' ? 'Đã duyệt' : latestEvidence.status === 'REJECTED' ? 'Bị từ chối' : 'Đang chờ Provider duyệt'}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleOpenProofModal}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 shadow-2xs whitespace-nowrap"
                >
                  Gửi biên lai khác
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Action bar */}
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white/95 p-4 backdrop-blur-sm sm:static sm:mt-6 sm:border-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none">
          <div className="mx-auto flex max-w-3xl flex-col gap-3 sm:flex-row sm:justify-end">
            {requestExpired && selectedRequest.paymentType === 'DEPOSIT' && (
              <button
                type="button"
                onClick={handleReissue}
                className="rounded-xl border border-brand-600 px-5 py-2.5 text-sm font-semibold text-brand-800 hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
              >
                {yearlyUpfront ? 'Cấp lại yêu cầu trả trước' : 'Cấp lại yêu cầu thanh toán'}
              </button>
            )}

            <button
              type="button"
              onClick={handleCheckStatus}
              disabled={checkingStatus}
              className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 disabled:opacity-50"
            >
              <Icon name="refresh" className={`text-lg ${checkingStatus ? 'animate-spin' : ''}`} />
              {checkingStatus ? 'Đang kiểm tra...' : 'Kiểm tra trạng thái'}
            </button>

            {!requestExpired && (
              <button
                type="button"
                onClick={handleOpenProofModal}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-700 px-6 py-2.5 text-sm font-bold text-white shadow-xs hover:bg-brand-800 focus-visible:ring-2 focus-visible:ring-brand-500/40 cursor-pointer"
              >
                <Icon name="upload_file" className="text-lg" />
                <span>{hasProof ? 'Gửi thêm minh chứng' : 'Tôi đã chuyển khoản'}</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Proof Submission Modal */}
      {showProofModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Icon name="receipt_long" className="text-xl text-brand-700" />
                Nộp minh chứng chuyển khoản
              </h2>
              <button
                type="button"
                onClick={() => setShowProofModal(false)}
                className="rounded-lg p-1 text-slate-400 hover:text-slate-700"
                aria-label="Đóng"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>

            <form onSubmit={handleSubmitProof} className="mt-4 space-y-4">
              {proofError && (
                <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                  {proofError}
                </div>
              )}

              {/* Amount */}
              <div>
                <label htmlFor="transferred-amount-input" className="block text-xs font-bold text-slate-700 mb-1">
                  Số tiền thực tế đã chuyển (VNĐ) <span className="text-red-500">*</span>
                </label>
                <input
                  id="transferred-amount-input"
                  type="number"
                  min="1000"
                  step="1000"
                  value={transferredAmount}
                  onChange={(e) => setTransferredAmount(e.target.value)}
                  required
                  placeholder="Ví dụ: 500000"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm font-semibold focus:border-brand-600 focus:outline-none focus:ring-1 focus:ring-brand-600"
                />
              </div>

              {/* Transaction Ref */}
              <div>
                <label htmlFor="transaction-reference-input" className="block text-xs font-bold text-slate-700 mb-1">
                  Mã giao dịch ngân hàng / Số tham chiếu (nếu có)
                </label>
                <input
                  id="transaction-reference-input"
                  type="text"
                  value={transactionReference}
                  onChange={(e) => setTransactionReference(e.target.value)}
                  placeholder="Ví dụ: FT260123456789"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-none focus:ring-1 focus:ring-brand-600"
                />
              </div>

              {/* Receipt File */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Ảnh chụp biên lai chuyển khoản <span className="text-red-500">*</span>
                </label>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  onChange={handleFileChange}
                  className="hidden"
                />

                {proofPreview ? (
                  <div className="relative mt-2 rounded-xl border border-slate-200 p-2 flex items-center gap-3 bg-slate-50">
                    <img
                      src={proofPreview}
                      alt="Ảnh biên lai"
                      className="h-16 w-16 rounded-lg object-cover border border-slate-200"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs font-bold text-slate-900">{proofFile?.name}</p>
                      <p className="text-[11px] text-slate-500">
                        {proofFile ? `${(proofFile.size / 1024).toFixed(1)} KB` : ''}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setProofFile(null)
                        if (proofPreview) URL.revokeObjectURL(proofPreview)
                        setProofPreview(null)
                      }}
                      className="rounded-lg p-1 text-slate-400 hover:text-red-600"
                      title="Xóa ảnh"
                    >
                      <Icon name="delete" className="text-lg" />
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="flex w-full flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 p-6 text-center hover:border-brand-600 hover:bg-slate-50 transition"
                  >
                    <Icon name="add_photo_alternate" className="text-3xl text-slate-400" />
                    <span className="mt-1 text-xs font-bold text-slate-700">Chọn ảnh biên lai từ máy</span>
                    <span className="text-[10px] text-slate-400">Hỗ trợ JPG, PNG, WebP tối đa 10MB</span>
                  </button>
                )}
              </div>

              {/* Note */}
              <div>
                <label htmlFor="proof-note-input" className="block text-xs font-bold text-slate-700 mb-1">
                  Ghi chú cho Provider (nếu có)
                </label>
                <input
                  id="proof-note-input"
                  type="text"
                  value={proofNote}
                  onChange={(e) => setProofNote(e.target.value)}
                  placeholder="Ví dụ: Em vừa chuyển từ Techcombank ạ"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:border-brand-600 focus:outline-none focus:ring-1 focus:ring-brand-600"
                />
              </div>

              <div className="mt-6 flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={() => setShowProofModal(false)}
                  disabled={submittingProof}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingProof || !proofFile}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-brand-700 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800 disabled:opacity-50"
                >
                  {submittingProof ? <Icon name="sync" className="animate-spin text-sm" /> : <Icon name="send" className="text-sm" />}
                  {submittingProof ? 'Đang tải lên...' : 'Xác nhận gửi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
