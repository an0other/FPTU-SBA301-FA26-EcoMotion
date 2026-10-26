import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import Toast from '../components/Toast'
import { HoldCountdown } from '../components/payments/HoldCountdown'
import { ReceiptDropzone } from '../components/payments/ReceiptDropzone'
import { providerFinanceService } from '../services/providerFinanceService'
import { paymentService } from '../services/paymentService'
import { PaymentEvidenceImage } from '../components/payments/PaymentEvidenceImage'
import { formatPaymentMoney, isPaymentRequestExpired } from '../utils/paymentDisplay'

function formatDateTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    if (Number.isNaN(d.getTime())) return isoString
    return new Intl.DateTimeFormat('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'Asia/Ho_Chi_Minh',
    }).format(d)
  } catch {
    return isoString
  }
}

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`} aria-hidden="true">{name}</span>
}

const STATUS_BADGES = {
  PENDING: { label: 'Chờ thanh toán cọc', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROOF_SUBMITTED: { label: 'Chờ đối soát biên lai', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
  PARTIALLY_PAID: { label: 'Thanh toán một phần', bg: 'bg-orange-50 text-orange-800 border-orange-200' },
  CONFIRMED: { label: 'Đã xác nhận thanh toán', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Bị từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
  EXPIRED: { label: 'Hết hạn', bg: 'bg-slate-100 text-slate-700 border-slate-300' },
  REVIEW_REQUIRED: { label: 'Cần đối soát', bg: 'bg-purple-50 text-purple-800 border-purple-200' },
  VOIDED: { label: 'Đã huỷ yêu cầu', bg: 'bg-slate-100 text-slate-700 border-slate-300' },
}

const EVIDENCE_BADGES = {
  PENDING: { label: 'Đang chờ đối soát', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  ACCEPTED: { label: 'Đã được chấp nhận', bg: 'bg-emerald-50 text-emerald-800 border-emerald-200' },
  REJECTED: { label: 'Bị từ chối', bg: 'bg-red-50 text-red-800 border-red-200' },
}

export default function CustomerPaymentPage() {
  const { bookingCode } = useParams()
  const bookingIdentifier = String(bookingCode ?? '').trim().toUpperCase()
  const [requests, setRequests] = useState([])
  const [selectedRequest, setSelectedRequest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })
  const [currentTime, setCurrentTime] = useState(() => Date.now())
  const [payMethod, setPayMethod] = useState('vietqr')

  const [proofModalOpen, setProofModalOpen] = useState(false)
  const [proofFile, setProofFile] = useState(null)
  const [proofFileError, setProofFileError] = useState('')
  const [txRef, setTxRef] = useState('')
  const [proofNote, setProofNote] = useState('')
  const [submittingProof, setSubmittingProof] = useState(false)
  const [paymentCreating, setPaymentCreating] = useState(false)
  const [inlineError, setInlineError] = useState(null)
  const proofPreviewUrl = useMemo(
    () => (proofFile ? URL.createObjectURL(proofFile) : ''),
    [proofFile],
  )

  useEffect(() => () => {
    if (proofPreviewUrl) URL.revokeObjectURL(proofPreviewUrl)
  }, [proofPreviewUrl])

  const fetchPaymentRequests = useCallback(async (silent = false) => {
    try {
      if (!silent) setLoading(true)
      setError(null)
      const data = await providerFinanceService.getPaymentRequestsByBooking(bookingIdentifier)
      setRequests(data || [])
      if (data && data.length > 0) {
        const activeFinal = data.find((request) => request.paymentType === 'FINAL_BALANCE'
          && request.status !== 'VOIDED')
        const activeDeposit = data.find((request) => request.paymentType === 'DEPOSIT'
          && request.status !== 'VOIDED')
        const current = activeFinal || activeDeposit || data[0]
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

  useEffect(() => {
    const terminal = ['CONFIRMED', 'VOIDED'].includes(selectedRequest?.status)
    if (!selectedRequest || terminal) return undefined
    const timer = window.setInterval(() => fetchPaymentRequests(true), 10000)
    const handleFocus = () => fetchPaymentRequests(true)
    window.addEventListener('focus', handleFocus)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', handleFocus)
    }
  }, [fetchPaymentRequests, selectedRequest])

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

  const handleSubmitProof = async (e) => {
    e.preventDefault()
    if (!selectedRequest) return
    if (isPaymentRequestExpired(selectedRequest)) {
      setCurrentTime(Date.now())
      setProofModalOpen(false)
      setToast({ message: 'Yêu cầu thanh toán đã hết hạn', type: 'error' })
      return
    }
    if (!proofFile) {
      setProofFileError('Vui lòng chọn ảnh chứng từ thanh toán')
      return
    }

    try {
      setSubmittingProof(true)
      await providerFinanceService.submitPaymentProof(selectedRequest.id, {
        file: proofFile,
        transactionReference: txRef.trim() || undefined,
        note: proofNote.trim() || undefined,
      })
      setToast({ message: 'Đã gửi biên lai. Chủ xe sẽ đối soát số tiền trên sao kê.', type: 'success' })
      setProofModalOpen(false)
      setProofFile(null)
      fetchPaymentRequests()
    } catch (err) {
      setToast({ message: err.message || 'Gửi bằng chứng thất bại', type: 'error' })
    } finally {
      setSubmittingProof(false)
    }
  }

  const handleOpenProofModal = () => {
    if (isPaymentRequestExpired(selectedRequest)) {
      setCurrentTime(Date.now())
      setToast({ message: 'Yêu cầu thanh toán đã hết hạn', type: 'error' })
      return
    }
    setProofFile(null)
    setProofFileError('')
    setTxRef('')
    setProofNote('')
    setProofModalOpen(true)
  }

  const handlePayWithVnpay = async () => {
    if (paymentCreating || !bookingIdentifier) return
    setPaymentCreating(true)
    setInlineError(null)
    try {
      const isFinal = selectedRequest?.paymentType === 'FINAL_BALANCE'
      const res = await paymentService.createVnpayPaymentUrl({
        bookingCode: bookingIdentifier,
        paymentType: isFinal ? 'FINAL_PAYMENT' : 'DEPOSIT',
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

  const handleReissue = async () => {
    try {
      await providerFinanceService.reissueDepositPayment(bookingIdentifier)
      await fetchPaymentRequests()
      setCurrentTime(Date.now())
      setToast({ message: 'Đã cấp lại yêu cầu thanh toán cọc', type: 'success' })
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
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
          <Icon name="error_outline" className="text-2xl" />
        </div>
        <h2 className="mt-3 text-lg font-bold text-[#14221A]">Không tìm thấy yêu cầu thanh toán</h2>
        <p className="mt-1 text-sm text-slate-600">{error || 'Chưa có yêu cầu thanh toán nào cho đơn thuê này.'}</p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link to={bookingHref} className="rounded-xl border border-brand-200 px-4 py-2 text-sm font-semibold text-brand-800 hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500/40">
            Chi tiết đơn
          </Link>
          <button
            type="button"
            onClick={fetchPaymentRequests}
            className="rounded-xl bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700 focus-visible:ring-2 focus-visible:ring-brand-500/40"
          >
            Thử lại
          </button>
        </div>
      </div>
    )
  }

  const badge = STATUS_BADGES[selectedRequest.status] || { label: selectedRequest.status, bg: 'bg-slate-100 text-slate-700' }
  const badgeLabel =
    selectedRequest.status === 'PENDING'
      ? selectedRequest.paymentType === 'FINAL_BALANCE'
        ? 'Chờ thanh toán quyết toán'
        : 'Chờ thanh toán cọc'
      : badge.label
  const requestExpired = isPaymentRequestExpired(selectedRequest, currentTime)
  const canSendProof = !requestExpired && ['PENDING', 'PARTIALLY_PAID', 'REJECTED'].includes(selectedRequest.status)
  const title = selectedRequest.paymentType === 'FINAL_BALANCE' ? 'Thanh toán quyết toán' : 'Thanh toán đặt cọc'

  return (
    <div className="pb-24 sm:pb-10">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="border-b border-[#E6EBE8] bg-white px-4 py-3 sm:px-6">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center gap-3">
          <Link
            to={bookingHref}
            className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm font-semibold text-[#14221A] hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-brand-500/40"
          >
            <Icon name="arrow_back" className="text-lg" />
            Đơn {selectedRequest.bookingCode}
          </Link>
          <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${badge.bg}`}>
            {badgeLabel}
          </span>
          <div className="ml-auto">
            <HoldCountdown expiresAt={selectedRequest.expiresAt} />
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
        <h1 className="text-2xl font-bold tracking-tight text-[#14221A]">{title}</h1>
        <p className="mt-1 text-sm text-slate-600">
          Quét VietQR hoặc thanh toán VNPAY. Sau khi một cổng xác nhận cọc, cổng còn lại tự đóng.
        </p>

        {requests.length > 1 && (
          <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
            {requests.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => setSelectedRequest(r)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors focus-visible:ring-2 focus-visible:ring-brand-500/40 ${
                  selectedRequest.id === r.id ? 'bg-brand-800 text-white' : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                {r.paymentType === 'DEPOSIT' ? 'Đặt cọc' : 'Quyết toán'} · {formatPaymentMoney(r.amount)}
              </button>
            ))}
          </div>
        )}

        {inlineError ? (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{inlineError}</div>
        ) : null}

        <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <button
            type="button"
            onClick={() => setPayMethod('vietqr')}
            className={`rounded-2xl border p-4 text-left transition-colors focus-visible:ring-2 focus-visible:ring-brand-500/40 ${
              payMethod === 'vietqr' ? 'border-brand-600 bg-brand-50 ring-2 ring-brand-500/20' : 'border-slate-200 bg-white hover:border-brand-300'
            }`}
          >
            <p className="text-sm font-bold text-[#14221A]">VietQR</p>
            <p className="mt-1 text-xs text-slate-600">Quét mã, chuyển đúng số trên QR, gửi ảnh biên lai.</p>
          </button>
          <button
            type="button"
            onClick={() => setPayMethod('vnpay')}
            className={`rounded-2xl border p-4 text-left transition-colors focus-visible:ring-2 focus-visible:ring-brand-500/40 ${
              payMethod === 'vnpay' ? 'border-brand-600 bg-brand-50 ring-2 ring-brand-500/20' : 'border-slate-200 bg-white hover:border-brand-300'
            }`}
          >
            <p className="text-sm font-bold text-[#14221A]">VNPAY</p>
            <p className="mt-1 text-xs text-slate-600">Thanh toán tự động, không cần gửi biên lai.</p>
          </button>
        </div>

        {payMethod === 'vietqr' ? (
          <div className="mt-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-8">
            <div className="grid grid-cols-1 items-center gap-8 md:grid-cols-2">
              <div className="flex flex-col items-center justify-center rounded-xl border border-slate-200/80 bg-slate-50 p-4">
                {selectedRequest.qrCodeUrl ? (
                  <>
                    <img
                      src={selectedRequest.qrCodeUrl}
                      alt={`VietQR ${formatPaymentMoney(selectedRequest.amount)} ${selectedRequest.transferReference || ''}`}
                      className="h-64 w-64 max-w-full rounded-lg bg-white object-contain p-2"
                    />
                    <p className="mt-2 text-center text-[11px] font-medium text-slate-500">
                      Mở ứng dụng ngân hàng và quét mã để điền sẵn số tiền và nội dung
                    </p>
                  </>
                ) : (
                  <div className="flex h-64 w-64 items-center justify-center rounded-lg bg-slate-100 text-xs text-slate-400">
                    Không thể tạo mã VietQR
                  </div>
                )}
              </div>

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
                  <span className="text-xs text-slate-500">Số tiền trên QR</span>
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
                  <span className="text-xs text-slate-500">Nội dung chuyển khoản</span>
                  <div className="mt-0.5 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 p-2.5">
                    <span className="font-mono text-sm font-bold text-amber-950">{selectedRequest.transferReference}</span>
                    <button type="button" onClick={() => handleCopy(selectedRequest.transferReference, 'nội dung chuyển khoản')} className="ml-auto rounded p-1 text-amber-800 hover:text-amber-950 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Sao chép nội dung chuyển khoản">
                      <Icon name="content_copy" className="text-base" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="mt-5 rounded-2xl border border-slate-200 bg-white p-6">
            <p className="text-sm text-slate-600">Bạn sẽ được chuyển sang VNPAY Sandbox với đúng số cọc hệ thống đã khóa.</p>
          </div>
        )}

        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white/95 p-4 backdrop-blur-sm sm:static sm:mt-6 sm:border-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none">
          <div className="mx-auto flex max-w-3xl flex-col gap-3 sm:flex-row sm:justify-end">
            {requestExpired && selectedRequest.paymentType === 'DEPOSIT' && !selectedRequest.evidences?.length ? (
              <button type="button" onClick={handleReissue} className="rounded-xl border border-brand-600 px-5 py-2.5 text-sm font-semibold text-brand-800 hover:bg-brand-50 focus-visible:ring-2 focus-visible:ring-brand-500/40">
                Cấp lại yêu cầu thanh toán
              </button>
            ) : null}
            {payMethod === 'vnpay' ? (
              <button
                type="button"
                onClick={handlePayWithVnpay}
                disabled={paymentCreating || requestExpired}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
              >
                {paymentCreating ? <Icon name="sync" className="animate-spin text-lg" /> : <Icon name="account_balance" className="text-lg" />}
                {paymentCreating ? 'Đang kết nối VNPAY...' : 'Tiếp tục với VNPAY'}
              </button>
            ) : (
              <button
                type="button"
                onClick={handleOpenProofModal}
                disabled={!canSendProof}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
              >
                <Icon name="receipt_long" className="text-lg" />
                Tôi đã chuyển khoản
              </button>
            )}
          </div>
        </div>

        {selectedRequest.evidences?.length ? (
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-bold text-[#14221A]">Biên lai đã gửi</h2>
            <p className="mt-1 text-xs text-slate-500">Đối soát theo số trên QR, không theo số khách tự khai.</p>
            <div className="mt-4 space-y-3">
              {selectedRequest.evidences.map((evidence) => {
                const evidenceBadge = EVIDENCE_BADGES[evidence.status]
                  || { label: evidence.status, bg: 'bg-slate-100 text-slate-700 border-slate-200' }
                return (
                  <article key={evidence.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-3 sm:flex-row">
                    <PaymentEvidenceImage evidence={evidence} alt="Biên lai chuyển khoản" className="h-24 w-full shrink-0 rounded-lg border bg-slate-50 object-contain sm:w-32" />
                    <div className="min-w-0 flex-1 text-xs text-slate-600">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <strong className="text-sm text-[#14221A]">{formatPaymentMoney(selectedRequest.amount)}</strong>
                        <span className={`rounded-full border px-2.5 py-1 font-semibold ${evidenceBadge.bg}`}>{evidenceBadge.label}</span>
                      </div>
                      <p className="mt-1">Gửi lúc: {formatDateTime(evidence.transferredAt)}</p>
                      {evidence.transactionReference ? <p>Mã giao dịch: <span className="font-mono">{evidence.transactionReference}</span></p> : null}
                      {evidence.note ? <p className="mt-1 text-slate-700">Ghi chú: {evidence.note}</p> : null}
                      {evidence.status === 'REJECTED' && selectedRequest.rejectionReason
                        ? <p className="mt-1 font-medium text-red-700">Lý do: {selectedRequest.rejectionReason}</p> : null}
                    </div>
                  </article>
                )
              })}
            </div>
          </section>
        ) : null}
      </div>

      {proofModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-base font-bold text-[#14221A]">Gửi biên lai chuyển khoản</h3>
              <button type="button" onClick={() => setProofModalOpen(false)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-brand-500/40" aria-label="Đóng">
                <Icon name="close" className="text-lg" />
              </button>
            </div>
            <form onSubmit={handleSubmitProof} className="mt-5 space-y-5">
              <ReceiptDropzone
                file={proofFile}
                previewUrl={proofPreviewUrl}
                error={proofFileError}
                onFile={(next, message) => {
                  setProofFile(next)
                  setProofFileError(message || '')
                }}
              />
              <details className="rounded-xl border border-slate-200 p-3">
                <summary className="cursor-pointer text-xs font-semibold text-slate-700">Mã giao dịch hoặc ghi chú (không bắt buộc)</summary>
                <div className="mt-3 space-y-3">
                  <input
                    type="text"
                    value={txRef}
                    onChange={(e) => setTxRef(e.target.value)}
                    placeholder="FT24…"
                    spellCheck={false}
                    className="w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-brand-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
                  />
                  <textarea
                    rows={2}
                    value={proofNote}
                    onChange={(e) => setProofNote(e.target.value)}
                    placeholder="Ví dụ: Đã chuyển từ Techcombank…"
                    className="w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-brand-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
                  />
                </div>
              </details>
              <div className="flex justify-end gap-3 border-t border-slate-100 pt-4">
                <button type="button" onClick={() => setProofModalOpen(false)} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submittingProof || requestExpired}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-brand-500/40"
                >
                  {submittingProof ? <Icon name="sync" className="animate-spin text-sm" /> : <Icon name="send" className="text-sm" />}
                  Gửi biên lai
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
