import { IconX, IconWallet, IconCheckCircle, IconClock, IconCar, IconUsers, IconAlertTriangle } from '../../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const BOOKING_STATUS_BADGES = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  ACCEPTED: 'bg-blue-50 text-blue-700 border-blue-200',
  CONFIRMED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  DRIVER_ASSIGNED: 'bg-sky-50 text-sky-700 border-sky-200',
  IN_PROGRESS: 'bg-cyan-50 text-cyan-700 border-cyan-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200',
  DISPUTED: 'bg-purple-50 text-purple-700 border-purple-200',
}

export default function BookingDetailModal({ booking, onClose, onOpenRefund }) {
  if (!booking) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-xl font-bold text-slate-900">
                Chi tiết Kiểm duyệt Đơn #{booking.bookingCode}
              </h3>
              <span
                className={`rounded-full border px-3 py-0.5 text-xs font-bold tracking-wider ${
                  BOOKING_STATUS_BADGES[booking.status] || 'bg-slate-100 text-slate-700'
                }`}
              >
                {booking.status}
              </span>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">
              Mã hệ thống: #{booking.id} • Thời điểm tạo đơn: {booking.createdAt ? new Date(booking.createdAt).toLocaleString('vi-VN') : '—'}
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <IconX className="h-5 w-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="mt-5 space-y-6 text-xs">
          {/* Section 1: 4-Party Entities Grid */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {/* Renter */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-1">
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1">
                <IconUsers className="h-3.5 w-3.5 text-brand-600" /> Khách thuê (Renter)
              </span>
              <div className="font-bold text-slate-900 text-sm">{booking.renterName || `Khách #${booking.renterId}`}</div>
              <div className="text-slate-500 truncate">{booking.renterEmail || '—'}</div>
              <div className="text-slate-700 font-medium">SĐT: {booking.renterPhone || '—'}</div>
            </div>

            {/* Vehicle */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-1">
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1">
                <IconCar className="h-3.5 w-3.5 text-emerald-600" /> Xe phục vụ (Vehicle)
              </span>
              <div className="font-bold text-slate-900 text-sm">{booking.vehicleModel || `Xe #${booking.vehicleId}`}</div>
              <div className="font-mono font-bold text-brand-700">Biển số: {booking.vehicleLicensePlate || '—'}</div>
              <div className="text-slate-500">Mã xe: #{booking.vehicleId}</div>
            </div>

            {/* Provider */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-1">
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1">
                <span>🏢</span> Đơn vị chủ xe (Provider)
              </span>
              <div className="font-bold text-slate-900 text-sm">{booking.providerDisplayName || `NCC #${booking.providerId}`}</div>
              <div className="text-slate-500">Mã đối tác: #{booking.providerId}</div>
            </div>

            {/* Driver */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-3.5 space-y-1">
              <span className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1">
                <span>👤</span> Tài xế lái xe (Driver)
              </span>
              <div className="font-bold text-slate-900 text-sm">
                {booking.driverName || (booking.driverId ? `Tài xế #${booking.driverId}` : 'Chưa phân công')}
              </div>
              <div className="text-slate-500">{booking.driverId ? `Mã TX: #${booking.driverId}` : 'Chờ nhà xe điều phối'}</div>
            </div>
          </div>

          {/* Section 2: Itinerary & Timeline details */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>📍</span> Lịch trình đón trả & Mốc thời gian
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div className="sm:col-span-3">
                <span className="text-slate-500 block">Địa chỉ đón khách:</span>
                <p className="mt-1 rounded-xl border border-slate-200 bg-white p-2.5 font-medium text-slate-800">
                  {booking.pickupAddress || 'Chưa cung cấp'}
                </p>
              </div>
              <div>
                <span className="text-slate-500 block">Thời gian hẹn đón:</span>
                <strong className="text-slate-900">{booking.pickupTime ? new Date(booking.pickupTime).toLocaleString('vi-VN') : '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số hành khách:</span>
                <strong className="text-slate-900">{booking.passengerCount} người</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Tổng thanh toán:</span>
                <strong className="text-base font-extrabold text-brand-700">{formatVND(booking.totalAmount)}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Bắt đầu chuyến đi thực tế:</span>
                <strong className="text-slate-900">{booking.actualStartAt ? new Date(booking.actualStartAt).toLocaleString('vi-VN') : '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Kết thúc chuyến đi thực tế:</span>
                <strong className="text-slate-900">{booking.actualEndAt ? new Date(booking.actualEndAt).toLocaleString('vi-VN') : '—'}</strong>
              </div>
              {booking.cancelReason && (
                <div className="sm:col-span-3 rounded-xl border border-rose-200 bg-rose-50 p-2.5 text-rose-800">
                  <strong>Lý do hủy đơn:</strong> {booking.cancelReason}
                </div>
              )}
            </div>
          </div>

          {/* Section 3: Itemized Charges Breakdown Table */}
          <div>
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-1.5">
              <IconWallet className="h-4 w-4 text-brand-600" /> Bóc tách chi phí & Các khoản phụ phí (Charges Breakdown)
            </h4>

            {(!booking.charges || booking.charges.length === 0) ? (
              <div className="rounded-2xl border border-dashed border-slate-200 p-4 text-center text-slate-400">
                Chưa có khoản phụ phí riêng lẻ nào được ghi nhận cho đơn này.
              </div>
            ) : (
              <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
                <table className="w-full text-left">
                  <thead className="bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                    <tr>
                      <th className="p-3">Khoản thu / Phụ phí</th>
                      <th className="p-3">Diễn giải</th>
                      <th className="p-3 text-center">Số lượng</th>
                      <th className="p-3">Đơn giá</th>
                      <th className="p-3 text-right">Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {booking.charges.map((c) => (
                      <tr key={c.id} className="hover:bg-slate-50/60">
                        <td className="p-3 font-bold text-slate-800">
                          {c.chargeType}
                          {c.isWaived && (
                            <span className="ml-2 rounded-md bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-700 border border-rose-200">
                              Đã miễn giảm
                            </span>
                          )}
                        </td>
                        <td className="p-3 text-slate-600">{c.description || '—'}</td>
                        <td className="p-3 text-center text-slate-600">{c.quantity || 1}</td>
                        <td className="p-3 text-slate-600">{formatVND(c.unitPrice)}</td>
                        <td className={`p-3 text-right font-bold ${c.isWaived ? 'line-through text-slate-400' : 'text-slate-900'}`}>
                          {formatVND(c.amount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Action Controls */}
        <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>

          <button
            type="button"
            onClick={() => {
              onClose()
              if (onOpenRefund) onOpenRefund(booking.id)
            }}
            className="flex items-center gap-1.5 rounded-xl border border-brand-200 bg-brand-50 px-4 py-2.5 text-xs font-bold text-brand-700 hover:bg-brand-100"
          >
            <IconWallet className="h-4 w-4" />
            <span>Thực hiện Hoàn tiền (Refund)</span>
          </button>
        </div>
      </div>
    </div>
  )
}
