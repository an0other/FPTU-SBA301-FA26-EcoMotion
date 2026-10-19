import { useState } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function DenyBookingModal({ booking, isOpen, onClose, onSuccess }) {
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [validationError, setValidationError] = useState('')

  if (!isOpen || !booking) return null

  async function handleConfirm() {
    if (!reason.trim()) {
      setValidationError('Vui lòng nhập lý do từ chối đơn thuê.')
      return
    }
    setValidationError('')
    setLoading(true)
    setError(null)

    try {
      const updated = await providerBookingService.denyBooking(booking.bookingCode, reason.trim())
      onSuccess?.(updated, 'Đã từ chối đơn thuê.')
      onClose()
    } catch (err) {
      setError(err?.message || 'Không thể từ chối đơn thuê. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl transition-all">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4 bg-red-50/50">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-red-100 text-red-700">
              <Icon name="block" className="text-[22px]" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-900">Từ chối đơn thuê</h3>
              <p className="text-xs text-slate-500 font-medium">Hủy bỏ yêu cầu đặt xe từ khách</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <Icon name="close" className="text-[20px]" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800 font-medium">
              <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <p className="text-sm text-slate-600 leading-relaxed">
            Bạn đang từ chối đơn thuê{' '}
            <strong className="font-mono font-bold text-slate-900">{booking.bookingCode}</strong> của khách{' '}
            <strong className="text-slate-900">{booking.renterName}</strong>.
          </p>

          <div>
            <label htmlFor="deny-reason" className="block text-xs font-bold text-slate-700 mb-1.5">
              Lý do từ chối <span className="text-red-600">*</span>
            </label>
            <textarea
              id="deny-reason"
              rows={3}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value)
                if (validationError) setValidationError('')
              }}
              placeholder="Ví dụ: Xe đang bảo dưỡng đột xuất, hết xe trong khung giờ yêu cầu, không liên lạc được với khách..."
              className={`w-full rounded-xl border bg-white p-3 text-xs text-slate-800 placeholder-slate-400 transition focus:outline-none ${
                validationError
                  ? 'border-red-400 focus:border-red-500 ring-1 ring-red-400'
                  : 'border-slate-200 focus:border-red-500'
              }`}
            />
            {validationError && (
              <p className="mt-1 text-xs text-red-600 font-medium">{validationError}</p>
            )}
          </div>

          <div className="flex items-center gap-2 rounded-xl bg-amber-50 p-3 text-[11.5px] text-amber-800 border border-amber-200/60 font-medium">
            <Icon name="info" className="text-[18px] text-amber-600 shrink-0" />
            <span>Thao tác từ chối sẽ chuyển trạng thái đơn sang REJECTED và không thể hoàn tác.</span>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-slate-100 bg-slate-50/50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
          >
            Đóng
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading}
            className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-red-700 transition disabled:opacity-50"
          >
            {loading && <Icon name="progress_activity" className="animate-spin text-[16px]" />}
            <span>{loading ? 'Đang từ chối...' : 'Xác nhận từ chối'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
