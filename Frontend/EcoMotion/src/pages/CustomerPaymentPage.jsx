import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import Toast from '../components/Toast'
import { providerFinanceService } from '../services/providerFinanceService'
import { paymentService } from '../services/paymentService'
import { PaymentEvidenceImage } from '../components/payments/PaymentEvidenceImage'
import { isPaymentRequestExpired } from '../utils/paymentDisplay'

function formatVnd(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    if (Number.isNaN(d.getTime())) return isoString
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${hours}:${mins} ${day}/${month}/${year}`
  } catch {
    return isoString
  }
}

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

const STATUS_BADGES = {
  PENDING: { label: 'Chờ thanh toán', bg: 'bg-amber-50 text-amber-800 border-amber-200' },
  PROOF_SUBMITTED: { label: 'Đã gửi bằng chứng', bg: 'bg-blue-50 text-blue-800 border-blue-200' },
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

  // Proof form states
  const [proofModalOpen, setProofModalOpen] = useState(false)
  const [proofFile, setProofFile] = useState(null)
  const [transferredAmount, setTransferredAmount] = useState('')
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
        setTransferredAmount(String(current.remainingAmount ?? current.amount ?? ''))
      }
    } catch (err) {
      console.error('Failed to load payment requests:', err)
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

    const maximumDelay = 2_147_000_000
    const timer = window.setTimeout(
      () => setCurrentTime(Date.now()),
      Math.min(Math.max(expiresAt - Date.now() + 25, 0), maximumDelay),
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
      setToast({ message: 'Vui lòng chọn ảnh chứng từ thanh toán', type: 'error' })
      return
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(proofFile.type) || proofFile.size > 5 * 1024 * 1024) {
      setToast({ message: 'Chỉ chấp nhận JPG, PNG hoặc WebP tối đa 5 MB', type: 'error' })
      return
    }

    try {
      setSubmittingProof(true)
      const payload = {
        file: proofFile,
        transferredAmount: Number(transferredAmount) || (selectedRequest.remainingAmount ?? selectedRequest.amount),
        transactionReference: txRef.trim() || undefined,
        note: proofNote.trim() || undefined,
      }
      await providerFinanceService.submitPaymentProof(selectedRequest.id, payload)
      setToast({ message: 'Gửi bằng chứng thành công! Chủ xe sẽ đối soát và xác nhận đã nhận tiền.', type: 'success' })
      setProofModalOpen(false)
      setProofFile(null)
      fetchPaymentRequests()
    } catch (err) {
      console.error('Failed to submit proof:', err)
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

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="flex items-center gap-2 text-slate-600 text-sm">
          <Icon name="sync" className="animate-spin text-xl text-[#059669]" />
          Đang tải thông tin chuyển khoản VietQR...
        </div>
      </div>
    )
  }

  if (error || !selectedRequest) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="rounded-2xl border border-red-200 bg-white p-8 max-w-md w-full text-center shadow-lg">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <Icon name="error_outline" className="text-2xl" />
          </div>
          <h2 className="mt-3 text-lg font-bold text-slate-900">Không tìm thấy yêu cầu thanh toán</h2>
          <p className="mt-1 text-xs text-slate-500">{error || 'Chưa có yêu cầu thanh toán nào cho đơn thuê này.'}</p>
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
            {bookingIdentifier ? (
              <button
                type="button"
                onClick={handlePayWithVnpay}
                disabled={paymentCreating}
                className="rounded-lg bg-[#1a237e] px-4 py-2 text-xs font-semibold text-white hover:bg-[#0d1642] disabled:opacity-50"
              >
                {paymentCreating ? 'Đang kết nối VNPAY...' : 'Thanh toán qua VNPAY'}
              </button>
            ) : null}
          </div>
        </div>
      </div>
    )
  }

  const badge = STATUS_BADGES[selectedRequest.status] || { label: selectedRequest.status, bg: 'bg-slate-100 text-slate-700' }
  const requestExpired = isPaymentRequestExpired(selectedRequest, currentTime)

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="mx-auto max-w-3xl">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Thanh toán đặt cọc</h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Đơn thuê <span className="font-mono font-bold text-slate-800">#{selectedRequest.bookingCode}</span> • VietQR chuyển khoản hoặc cổng VNPAY
            </p>
          </div>
          <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${badge.bg}`}>
            {badge.label}
          </span>
        </div>

        {/* Payment Request Selector if multiple */}
        {requests.length > 1 && (
          <div className="mb-6 flex gap-2 overflow-x-auto pb-1">
            {requests.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => {
                  setSelectedRequest(r)
                  setTransferredAmount(String(r.remainingAmount ?? r.amount))
                }}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                  selectedRequest.id === r.id ? 'bg-[#064e3b] text-white shadow-xs' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                }`}
              >
                {r.paymentType === 'DEPOSIT' ? 'Đặt cọc' : 'Quyết toán cuối chuyến'} • {formatVnd(r.amount)}
              </button>
            ))}
          </div>
        )}

        {inlineError ? (
          <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800">
            {inlineError}
          </div>
        ) : null}

        {/* Main Payment Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
            {/* VietQR Column */}
            <div className="flex flex-col items-center justify-center p-4 bg-slate-50 rounded-xl border border-slate-200/80">
              {selectedRequest.qrCodeUrl ? (
                <div className="relative group">
                  <img
                    src={selectedRequest.qrCodeUrl}
                    alt="VietQR Chuyển khoản ngân hàng"
                    className="w-64 h-64 object-contain rounded-lg shadow-xs bg-white p-2"
                  />
                  <div className="mt-2 text-center text-[11px] font-medium text-slate-500">
                    Mở ứng dụng ngân hàng quét mã QR để điền tự động
                  </div>
                </div>
              ) : (
                <div className="h-64 w-64 flex items-center justify-center bg-slate-100 rounded-lg text-slate-400 text-xs">
                  Không thể tạo mã VietQR
                </div>
              )}
            </div>

            {/* Banking Details Column */}
            <div className="space-y-4">
              <div>
                <span className="text-xs text-slate-500">Ngân hàng thụ hưởng</span>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="font-bold text-slate-900 text-base">{selectedRequest.bankNameSnapshot}</span>
                  <span className="rounded bg-emerald-100 px-2 py-0.5 font-mono text-[11px] font-bold text-emerald-800">
                    {selectedRequest.bankCodeSnapshot}
                  </span>
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Chủ tài khoản thụ hưởng</span>
                <div className="font-bold text-slate-900 uppercase tracking-wide text-sm mt-0.5">
                  {selectedRequest.accountHolderSnapshot}
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Số tài khoản</span>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="font-mono text-lg font-bold tracking-wider text-[#059669]">
                    {selectedRequest.accountNumberSnapshot || selectedRequest.accountNumberMasked}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(selectedRequest.accountNumberSnapshot, 'số tài khoản')}
                    className="p-1 text-slate-400 hover:text-slate-600 rounded"
                    title="Sao chép số tài khoản"
                  >
                    <Icon name="content_copy" className="text-base" />
                  </button>
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Số tiền cần chuyển</span>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-2xl font-extrabold text-slate-900">
                    {formatVnd(selectedRequest.remainingAmount ?? selectedRequest.amount)}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(String(selectedRequest.remainingAmount ?? selectedRequest.amount), 'số tiền')}
                    className="p-1 text-slate-400 hover:text-slate-600 rounded"
                    title="Sao chép số tiền"
                  >
                    <Icon name="content_copy" className="text-base" />
                  </button>
                </div>
              </div>

              <div>
                <span className="text-xs text-slate-500">Nội dung chuyển khoản (Bắt buộc chính xác)</span>
                <div className="flex items-center gap-2 mt-0.5 rounded-lg border border-amber-200 bg-amber-50 p-2.5">
                  <span className="font-mono font-bold text-amber-900 text-sm">
                    {selectedRequest.transferReference}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(selectedRequest.transferReference, 'nội dung chuyển khoản')}
                    className="ml-auto p-1 text-amber-700 hover:text-amber-900 rounded"
                    title="Sao chép nội dung chuyển khoản"
                  >
                    <Icon name="content_copy" className="text-base" />
                  </button>
                </div>
              </div>

              {selectedRequest.expiresAt && (
                <div className="text-[11px] text-slate-500 flex items-center gap-1">
                  <Icon name="schedule" className="text-sm text-slate-400" />
                  Hạn chót thanh toán: <span className="font-semibold text-slate-700">{formatDateTime(selectedRequest.expiresAt)}</span>
                </div>
              )}
            </div>
          </div>

          {/* Action Footer */}
          <div className="mt-8 border-t border-slate-100 pt-6 flex flex-col gap-4">
            <div className="text-xs text-slate-500 flex items-center gap-1.5">
              <Icon name="info" className="text-base text-slate-400 shrink-0" />
              <span>Chọn VietQR (gửi biên lai) hoặc thanh toán tự động qua VNPAY. Chỉ một cổng được ghi nhận cọc.</span>
            </div>
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-end gap-3">
              <button
                type="button"
                onClick={handlePayWithVnpay}
                disabled={paymentCreating || requestExpired}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-[#1a237e] px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-[#0d1642] disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {paymentCreating ? <Icon name="sync" className="animate-spin text-lg" /> : <Icon name="account_balance" className="text-lg" />}
                {paymentCreating ? 'Đang kết nối VNPAY...' : 'Thanh toán qua VNPAY'}
              </button>
              <button
                type="button"
                onClick={handleOpenProofModal}
                disabled={requestExpired || !['PENDING', 'PARTIALLY_PAID', 'REJECTED'].includes(selectedRequest.status)}
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-[#059669] px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-[#047857] disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Icon name="receipt_long" className="text-lg" />
                Tôi đã chuyển khoản & Gửi bằng chứng
              </button>
              {requestExpired && selectedRequest.paymentType === 'DEPOSIT' && !selectedRequest.evidences?.length ? (
                <button type="button" onClick={handleReissue} className="w-full sm:w-auto rounded-xl border border-[#059669] px-5 py-2.5 text-sm font-semibold text-[#047857]">
                  Cấp lại yêu cầu thanh toán
                </button>
              ) : null}
            </div>
          </div>
        </div>

        {selectedRequest.evidences?.length ? (
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-base font-bold text-slate-900">Bằng chứng đã gửi</h2>
            <p className="mt-1 text-xs text-slate-500">Theo dõi trạng thái đối soát từng biên lai chuyển khoản.</p>
            <div className="mt-4 space-y-3">
              {selectedRequest.evidences.map((evidence) => {
                const evidenceBadge = EVIDENCE_BADGES[evidence.status]
                  || { label: evidence.status, bg: 'bg-slate-100 text-slate-700 border-slate-200' }
                return (
                  <article key={evidence.id} className="flex flex-col gap-3 rounded-xl border border-slate-200 p-3 sm:flex-row">
                    <PaymentEvidenceImage evidence={evidence} alt={`Biên lai ${evidence.id}`} className="h-24 w-full shrink-0 rounded-lg border bg-slate-50 object-contain sm:w-32" />
                    <div className="min-w-0 flex-1 text-xs text-slate-600">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <strong className="text-sm text-slate-900">{formatVnd(evidence.transferredAmount)}</strong>
                        <span className={`rounded-full border px-2.5 py-1 font-semibold ${evidenceBadge.bg}`}>{evidenceBadge.label}</span>
                      </div>
                      <p className="mt-1">Đã chuyển: {formatDateTime(evidence.transferredAt)}</p>
                      {evidence.transactionReference ? <p>Mã giao dịch: <span className="font-mono">{evidence.transactionReference}</span></p> : null}
                      {evidence.note ? <p className="mt-1 text-slate-700">Ghi chú: {evidence.note}</p> : null}
                      {evidence.reviewedAt ? <p>Đối soát: {formatDateTime(evidence.reviewedAt)}</p> : null}
                      {evidence.status === 'REJECTED' && selectedRequest.rejectionReason
                        ? <p className="mt-1 font-medium text-red-700">Lý do: {selectedRequest.rejectionReason}</p> : null}
                    </div>
                  </article>
                )
              })}
            </div>
          </section>
        ) : null}

        <div className="mt-6 text-center">
          <Link to={`/renter/bookings/${encodeURIComponent(bookingIdentifier)}`} className="text-sm font-semibold text-[#047857] hover:underline">Về chi tiết đơn thuê</Link>
        </div>

        {/* Proof Submission Modal */}
        {proofModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
            <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
              <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                <h3 className="text-base font-bold text-slate-900">Gửi bằng chứng chuyển khoản</h3>
                <button
                  type="button"
                  onClick={() => setProofModalOpen(false)}
                  className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                >
                  <Icon name="close" className="text-lg" />
                </button>
              </div>

              <form onSubmit={handleSubmitProof} className="mt-4 space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-700">Ảnh biên lai chuyển khoản *</label>
                  <input
                    type="file"
                    required
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(e) => setProofFile(e.target.files?.[0] ?? null)}
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  />
                  <span className="text-[11px] text-slate-400">JPG, PNG hoặc WebP, tối đa 5 MB. {proofFile ? `Đã chọn: ${proofFile.name}` : ''}</span>
                  {proofFile ? (
                    <img
                      src={proofPreviewUrl}
                      alt="Xem trước chứng từ"
                      className="mt-2 h-32 w-full rounded-lg border border-slate-200 object-contain bg-slate-50"
                    />
                  ) : null}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Số tiền thực tế đã chuyển (VND) *</label>
                  <input
                    type="number"
                    required
                    min="1000"
                    value={transferredAmount}
                    onChange={(e) => setTransferredAmount(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng (Tùy chọn)</label>
                  <input
                    type="text"
                    value={txRef}
                    onChange={(e) => setTxRef(e.target.value)}
                    placeholder="FT24..."
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700">Ghi chú thêm (Tùy chọn)</label>
                  <textarea
                    rows={2}
                    value={proofNote}
                    onChange={(e) => setProofNote(e.target.value)}
                    placeholder="Ví dụ: Đã chuyển từ tài khoản Techcombank..."
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  />
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                  <button
                    type="button"
                    onClick={() => setProofModalOpen(false)}
                    className="rounded-lg border border-slate-300 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    Hủy bỏ
                  </button>
                  <button
                    type="submit"
                    disabled={submittingProof || requestExpired}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-[#059669] px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#047857] disabled:opacity-50"
                  >
                    {submittingProof ? <Icon name="sync" className="animate-spin text-sm" /> : <Icon name="send" className="text-sm" />}
                    Xác nhận đã chuyển
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
