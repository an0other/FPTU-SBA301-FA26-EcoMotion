import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import Toast from '../components/Toast'
import { providerFinanceService } from '../services/providerFinanceService'

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
}

export default function CustomerPaymentPage() {
  const { bookingId } = useParams()
  const [requests, setRequests] = useState([])
  const [selectedRequest, setSelectedRequest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Proof form states
  const [proofModalOpen, setProofModalOpen] = useState(false)
  const [proofUrl, setProofUrl] = useState('')
  const [transferredAmount, setTransferredAmount] = useState('')
  const [txRef, setTxRef] = useState('')
  const [proofNote, setProofNote] = useState('')
  const [submittingProof, setSubmittingProof] = useState(false)

  const fetchPaymentRequests = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await providerFinanceService.getPaymentRequestsByBooking(bookingId)
      setRequests(data || [])
      if (data && data.length > 0) {
        setSelectedRequest(data[0])
        setTransferredAmount(String(data[0].remainingAmount || data[0].amount || ''))
      }
    } catch (err) {
      console.error('Failed to load payment requests:', err)
      setError(err.message || 'Không thể tải thông tin thanh toán đơn thuê.')
    } finally {
      setLoading(false)
    }
  }, [bookingId])

  useEffect(() => {
    if (bookingId) {
      fetchPaymentRequests()
    }
  }, [bookingId, fetchPaymentRequests])

  const handleCopy = (text, label) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    setToast({ message: `Đã sao chép ${label}`, type: 'success' })
  }

  const handleSubmitProof = async (e) => {
    e.preventDefault()
    if (!selectedRequest) return
    if (!proofUrl.trim()) {
      setToast({ message: 'Vui lòng nhập đường dẫn ảnh bằng chứng thanh toán', type: 'error' })
      return
    }

    try {
      setSubmittingProof(true)
      const payload = {
        proofImageUrl: proofUrl.trim(),
        transferredAmount: Number(transferredAmount) || selectedRequest.amount,
        transactionReference: txRef.trim() || undefined,
        note: proofNote.trim() || undefined,
      }
      await providerFinanceService.submitPaymentProof(selectedRequest.id, payload)
      setToast({ message: 'Gửi bằng chứng thành công! Chủ xe sẽ đối soát và xác nhận đã nhận tiền.', type: 'success' })
      setProofModalOpen(false)
      fetchPaymentRequests()
    } catch (err) {
      console.error('Failed to submit proof:', err)
      setToast({ message: err.message || 'Gửi bằng chứng thất bại', type: 'error' })
    } finally {
      setSubmittingProof(false)
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

  const badge = STATUS_BADGES[selectedRequest.status] || { label: selectedRequest.status, bg: 'bg-slate-100 text-slate-700' }

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="mx-auto max-w-3xl">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Thanh toán chuyển khoản trực tiếp</h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Đơn thuê <span className="font-mono font-bold text-slate-800">#{selectedRequest.bookingCode}</span> • Thanh toán trực tiếp vào tài khoản ngân hàng của chủ xe
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
                  setTransferredAmount(String(r.remainingAmount || r.amount))
                }}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                  selectedRequest.id === r.id ? 'bg-[#064e3b] text-white shadow-xs' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                }`}
              >
                {r.paymentType === 'DEPOSIT' ? 'Đặt cọc (DEPOSIT)' : 'Quyết toán (FINAL BALANCE)'} • {formatVnd(r.amount)}
              </button>
            ))}
          </div>
        )}

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
                    {formatVnd(selectedRequest.remainingAmount || selectedRequest.amount)}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(String(selectedRequest.remainingAmount || selectedRequest.amount), 'số tiền')}
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
          <div className="mt-8 border-t border-slate-100 pt-6 flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="text-xs text-slate-500 flex items-center gap-1.5">
              <Icon name="info" className="text-base text-slate-400 shrink-0" />
              <span>Sau khi chuyển khoản xong, vui lòng bấm nút bên cạnh để gửi ảnh biên lai đối soát.</span>
            </div>

            <button
              type="button"
              onClick={() => setProofModalOpen(true)}
              disabled={selectedRequest.status === 'CONFIRMED'}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-[#059669] px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-[#047857] disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Icon name="receipt_long" className="text-lg" />
              Tôi đã chuyển khoản & Gửi bằng chứng
            </button>
          </div>
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
                  <label className="block text-xs font-semibold text-slate-700">Đường dẫn ảnh biên lai (URL) *</label>
                  <input
                    type="url"
                    required
                    value={proofUrl}
                    onChange={(e) => setProofUrl(e.target.value)}
                    placeholder="https://example.com/receipt.jpg"
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs focus:border-[#059669] focus:outline-hidden focus:ring-1 focus:ring-[#059669]"
                  />
                  <span className="text-[11px] text-slate-400">Bạn có thể dán link ảnh biên lai chụp từ ứng dụng ngân hàng.</span>
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
                    disabled={submittingProof}
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
