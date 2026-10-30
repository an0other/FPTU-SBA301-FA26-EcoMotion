import { useState } from 'react'
import { driverTripService } from '../../services/driverTripService'

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
}

const REJECT_REASONS = [
  { value: 'SCHEDULE_CONFLICT', label: 'Trùng lịch trình / Chuyến khác đang chạy' },
  { value: 'HEALTH_ISSUE', label: 'Vấn đề sức khỏe / Không đảm bảo an toàn' },
  { value: 'CANNOT_ARRIVE_ON_TIME', label: 'Khoảng cách quá xa / Không kịp giờ đón' },
  { value: 'EMERGENCY', label: 'Sự cố khẩn cấp đột xuất' },
  { value: 'OTHER', label: 'Lý do khác' },
]

export default function DriverRejectTripModal({ trip, isOpen, onClose, onSuccess }) {
  const [reason, setReason] = useState('SCHEDULE_CONFLICT')
  const [note, setNote] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (!isOpen || !trip) return null

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (loading) return
    if (!reason) {
      setError('Vui lòng chọn lý do từ chối chuyến.')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const tripId = trip.tripId || trip.bookingCode
      await driverTripService.rejectTrip(tripId, {
        rejectionReason: reason,
        rejectionNote: note.trim() || undefined,
      })
      onSuccess?.('Đã từ chối nhận chuyến thành công. Hệ thống sẽ điều phối tài xế khác.')
      onClose()
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Không thể từ chối chuyến. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Từ chối nhận chuyến xe"
        className="w-full max-w-md max-h-[90vh] overflow-y-auto rounded-xl bg-white p-5 shadow-lg border border-slate-200"
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
          <div className="flex items-center gap-2 text-rose-700">
            <Icon name="cancel" className="text-xl" />
            <h3 className="text-base font-bold text-slate-900">Từ chối nhận chuyến xe</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <Icon name="close" className="text-lg" />
          </button>
        </div>

        <p className="text-xs text-slate-600 mb-3 leading-relaxed">
          Đơn thuê: <span className="font-mono font-bold text-slate-900">#{trip.bookingCode}</span>. Vui lòng nêu rõ lý do để chủ xe có căn cứ phân công tài xế thay thế ngay lập tức.
        </p>

        {error && (
          <div className="mb-3 rounded-lg bg-red-50 p-2.5 text-xs text-red-800 border border-red-200">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3.5">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              Lý do từ chối chính <span className="text-red-500">*</span>
            </label>
            <div className="space-y-1.5">
              {REJECT_REASONS.map((r) => (
                <label
                  key={r.value}
                  className={`flex items-center gap-2.5 p-2.5 rounded-xl border text-xs cursor-pointer transition ${
                    reason === r.value
                      ? 'border-rose-500 bg-rose-50/60 text-rose-950 font-bold'
                      : 'border-slate-200 text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <input
                    type="radio"
                    name="rejectReason"
                    value={r.value}
                    checked={reason === r.value}
                    onChange={(e) => setReason(e.target.value)}
                    className="text-rose-600 focus:ring-rose-500"
                  />
                  <span>{r.label}</span>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Ghi chú thêm (tùy chọn)
            </label>
            <textarea
              rows="2"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Chi tiết hoàn cảnh phát sinh..."
              className="w-full rounded-xl border border-slate-200 p-2.5 text-xs text-slate-800 placeholder-slate-400 focus:border-rose-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex h-10 items-center justify-center gap-1.5 rounded-lg bg-rose-600 px-4 text-xs font-bold text-white hover:bg-rose-700 transition disabled:opacity-50"
            >
              {loading && <Icon name="sync" className="animate-spin text-[16px]" />}
              <span>{loading ? 'Đang gửi...' : 'Xác nhận từ chối'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
