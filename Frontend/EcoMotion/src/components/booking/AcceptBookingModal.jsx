import { IconCheckCircle } from '../icons/Icons'

export default function AcceptBookingModal({
  isOpen,
  onClose,
  onConfirm,
  booking,
  submitting,
  formatDateTime,
}) {
  if (!isOpen || !booking) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div className="relative w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-600">
            <IconCheckCircle className="h-6 w-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900">Chấp nhận đơn đặt xe</h3>
            <p className="text-xs text-slate-500">Mã đơn: #{booking.bookingCode}</p>
          </div>
        </div>

        <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 mb-5 space-y-2 text-xs">
          <div className="flex justify-between">
            <span className="text-slate-500">Khách hàng:</span>
            <span className="font-semibold text-slate-800">{booking.renter?.fullName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Phương tiện:</span>
            <span className="font-semibold text-slate-800">
              {booking.vehicle?.brand} {booking.vehicle?.model} ({booking.vehicle?.licensePlate})
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Thời gian nhận:</span>
            <span className="font-semibold text-slate-800">
              {formatDateTime(booking.pickupTime)}
            </span>
          </div>
        </div>

        <p className="text-xs text-slate-600 mb-6">
          Khi xác nhận, trạng thái đơn sẽ chuyển sang <strong className="text-emerald-700">Đã xác nhận (CONFIRMED)</strong>. Lịch trình xe sẽ được khóa để đảm bảo không bị trùng lặp với các đơn khác.
        </p>

        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            disabled={submitting}
            onClick={onClose}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-100 disabled:opacity-50"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={onConfirm}
            className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-sm transition hover:bg-emerald-700 disabled:opacity-50"
          >
            {submitting ? (
              <>
                <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Đang xử lý...
              </>
            ) : (
              'Xác nhận duyệt đơn'
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
