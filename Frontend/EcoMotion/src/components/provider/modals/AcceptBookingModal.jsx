import { useState } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
}

function formatVnd(amount) {
  if (amount == null) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

export default function AcceptBookingModal({ booking, isOpen, onClose, onSuccess }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  if (!isOpen || !booking) return null

  async function handleConfirm() {
    if (loading) return
    setLoading(true)
    setError(null)
    try {
      const updated = await providerBookingService.acceptBooking(booking.bookingCode)
      onSuccess?.(updated, 'Đã chấp nhận đơn thuê thành công!')
      onClose()
    } catch (err) {
      setError(err?.message || 'Không thể chấp nhận đơn thuê. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Chấp nhận đơn thuê"
        className="w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden rounded-xl bg-white shadow-lg border border-slate-200"
      >
        {/* Header */}
        <div className="flex shrink-0 items-center justify-between border-b border-slate-100 px-6 py-4 bg-emerald-50/50">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
              <Icon name="check_circle" className="text-[22px]" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-900">Chấp nhận đơn thuê</h3>
              <p className="text-xs text-slate-500 font-medium">Xác nhận nhận chuyến xe cho khách</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            aria-label="Đóng cửa sổ"
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <Icon name="close" className="text-[20px]" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800 font-medium">
              <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <p className="text-sm text-slate-600 leading-relaxed">
            Bạn có chắc chắn muốn chấp nhận đơn thuê{' '}
            <strong className="font-mono font-bold text-slate-900">{booking.bookingCode}</strong>?
            Sau khi chấp nhận, người thuê sẽ được thông báo để tiến hành đặt cọc giữ xe.
          </p>

          {/* Booking mini summary */}
          <div className="rounded-xl border border-slate-100 bg-slate-50/80 p-4 space-y-2 text-xs">
            <div className="flex justify-between items-center text-slate-600">
              <span>Khách thuê:</span>
              <strong className="text-slate-900">{booking.renterName}</strong>
            </div>
            <div className="flex justify-between items-center text-slate-600">
              <span>Dòng xe:</span>
              <strong className="text-slate-900">
                {booking.vehicleName} ({booking.licensePlate})
              </strong>
            </div>
            <div className="flex justify-between items-center text-slate-600">
              <span>Hình thức thuê:</span>
              <span className="font-semibold text-emerald-700">
                {booking.rentalType || 'Theo ngày'} ({booking.rentalDuration || '1 ngày'})
              </span>
            </div>
            <div className="flex justify-between items-center border-t border-slate-200 pt-2 text-slate-800">
              <span className="font-semibold">Tổng giá trị đơn:</span>
              <strong className="font-mono text-sm text-emerald-800">
                {formatVnd(booking.totalAmount)}
              </strong>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex shrink-0 items-center justify-end gap-3 border-t border-slate-100 bg-slate-50/50 px-6 py-4">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-emerald-700 px-4 text-xs font-bold text-white shadow-2xs hover:bg-emerald-800 transition disabled:opacity-50"
          >
            {loading && <Icon name="progress_activity" className="animate-spin text-[16px]" />}
            <span>{loading ? 'Đang xử lý...' : 'Xác nhận chấp nhận'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
