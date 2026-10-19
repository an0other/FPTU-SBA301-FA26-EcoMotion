import { useState } from 'react'
import { IconAlertTriangle } from '../icons/Icons'

const REJECTION_REASONS = [
  { value: 'PROVIDER_NO_VEHICLE', label: 'Nhà xe không đủ xe hoặc xe đang bảo trì đột xuất' },
  { value: 'PROVIDER_NO_DRIVER', label: 'Không thể bố trí tài xế phù hợp trong khung giờ này' },
  { value: 'PREVIOUS_RENTER_OVERDUE', label: 'Khách thuê trước chưa hoàn trả xe kịp thời gian' },
  { value: 'FORCE_MAJEURE', label: 'Trường hợp bất khả kháng (Thời tiết xấu, sự cố giao thông)' },
  { value: 'SYSTEM', label: 'Sự cố kỹ thuật hoặc thông tin hệ thống chưa khớp' },
]

export default function RejectBookingModal({
  isOpen,
  onClose,
  onConfirm,
  booking,
  submitting,
}) {
  const [selectedReason, setSelectedReason] = useState('')
  const [note, setNote] = useState('')
  const [validationError, setValidationError] = useState('')

  if (!isOpen || !booking) return null

  function handleSubmit(e) {
    e.preventDefault()
    if (!selectedReason) {
      setValidationError('Vui lòng chọn lý do từ chối đơn đặt xe.')
      return
    }
    setValidationError('')
    onConfirm({ reason: selectedReason, note: note.trim() })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs">
      <div className="relative w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl border border-slate-100">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <IconAlertTriangle className="h-6 w-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900">Từ chối đơn đặt xe</h3>
            <p className="text-xs text-slate-500">Mã đơn: #{booking.bookingCode}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              Lý do từ chối <span className="text-red-500">*</span>
            </label>
            <select
              value={selectedReason}
              onChange={(e) => {
                setSelectedReason(e.target.value)
                setValidationError('')
              }}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-medium text-slate-800 focus:border-red-500 focus:outline-hidden focus:ring-1 focus:ring-red-500"
            >
              <option value="">-- Chọn lý do từ chối --</option>
              {REJECTION_REASONS.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
            {validationError && (
              <p className="mt-1 text-xs text-red-600 font-medium">{validationError}</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              Ghi chú thêm cho khách hàng (không bắt buộc)
            </label>
            <textarea
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Nhập chi tiết lý do hoặc đề xuất đổi xe nếu có..."
              className="w-full rounded-lg border border-slate-300 p-3 text-xs text-slate-800 placeholder:text-slate-400 focus:border-red-500 focus:outline-hidden focus:ring-1 focus:ring-red-500"
            />
          </div>

          <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800">
            Hành động này sẽ cập nhật trạng thái đơn thành <strong>Từ chối (REJECTED)</strong> và không thể hoàn tác.
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              disabled={submitting}
              onClick={onClose}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-100 disabled:opacity-50"
            >
              Đóng
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm transition hover:bg-red-700 disabled:opacity-50"
            >
              {submitting ? (
                <>
                  <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Đang từ chối...
                </>
              ) : (
                'Xác nhận từ chối'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
