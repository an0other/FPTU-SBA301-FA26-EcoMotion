import { IconX, IconWallet, IconCar, IconUsers } from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'

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

export default function BookingDetailModal({
  booking,
  onClose,
  onSelectRenter,
  onSelectVehicle,
  onSelectProvider,
  onSelectDriver,
}) {
  if (!booking) return null

  const canClickRenter = Boolean((booking.renterUserId || booking.renterId || booking.renterEmail) && onSelectRenter)
  const canClickVehicle = Boolean(booking.vehicleId && onSelectVehicle)
  const canClickProvider = Boolean(booking.providerId && onSelectProvider)
  const canClickDriver = Boolean(booking.driverId && onSelectDriver)

  return (
    <AdminModalShell
      maxWidth="max-w-5xl"
      header={(
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-lg font-bold text-slate-900">
                Chi tiết Kiểm duyệt Đơn #{booking.bookingCode}
              </h3>
              <span
                className={`rounded-full border px-2.5 py-0.5 text-[11px] font-bold tracking-wider ${
                  BOOKING_STATUS_BADGES[booking.status] || 'bg-slate-100 text-slate-700'
                }`}
              >
                {booking.status}
              </span>
            </div>
            <p className="mt-0.5 text-xs text-slate-500">
              Mã hệ thống: #{booking.id} • Thời điểm tạo đơn:{' '}
              {booking.createdAt ? new Date(booking.createdAt).toLocaleString('vi-VN') : '—'}
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
      )}
      footer={(
        <div className="flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>
        </div>
      )}
    >
      <div className="space-y-3.5 text-xs">
        {/* Section 1: 4-Party Entities Grid */}
        <div>
          <span className="mb-1.5 block text-[11px] font-bold uppercase tracking-wider text-slate-500">
            Các bên tham gia đơn thuê (Bấm vào từng thẻ để xem chi tiết)
          </span>
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
            {/* Renter */}
            <div
              onClick={() => canClickRenter && onSelectRenter(booking)}
              title={canClickRenter ? 'Bấm để xem chi tiết khách thuê' : ''}
              className={`group space-y-0.5 rounded-2xl border p-3 transition-all ${
                canClickRenter
                  ? 'cursor-pointer border-slate-200 bg-slate-50/70 hover:border-brand-400 hover:bg-brand-50/40 hover:shadow-sm'
                  : 'border-slate-200 bg-slate-50/70'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                  <IconUsers className="h-3.5 w-3.5 text-brand-600" /> Khách thuê
                </span>
                {canClickRenter && (
                  <span className="text-[10px] font-bold text-brand-600 transition-transform group-hover:translate-x-0.5">
                    Chi tiết →
                  </span>
                )}
              </div>
              <div className="text-sm font-bold text-slate-900 transition-colors group-hover:text-brand-700">
                {booking.renterName || `Khách #${booking.renterId}`}
              </div>
              <div className="truncate text-slate-500">{booking.renterEmail || '—'}</div>
              <div className="font-medium text-slate-700">SĐT: {booking.renterPhone || '—'}</div>
            </div>

            {/* Vehicle */}
            <div
              onClick={() => canClickVehicle && onSelectVehicle(booking.vehicleId)}
              title={canClickVehicle ? 'Bấm để xem chi tiết phương tiện' : ''}
              className={`group space-y-0.5 rounded-2xl border p-3 transition-all ${
                canClickVehicle
                  ? 'cursor-pointer border-slate-200 bg-slate-50/70 hover:border-emerald-400 hover:bg-emerald-50/40 hover:shadow-sm'
                  : 'border-slate-200 bg-slate-50/70'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                  <IconCar className="h-3.5 w-3.5 text-emerald-600" /> Xe phục vụ
                </span>
                {canClickVehicle && (
                  <span className="text-[10px] font-bold text-emerald-600 transition-transform group-hover:translate-x-0.5">
                    Chi tiết →
                  </span>
                )}
              </div>
              <div className="text-sm font-bold text-slate-900 transition-colors group-hover:text-emerald-700">
                {booking.vehicleModel || `Xe #${booking.vehicleId}`}
              </div>
              <div className="font-mono font-bold text-brand-700">
                Biển số: {booking.vehicleLicensePlate || '—'}
              </div>
              <div className="text-slate-500">Mã xe: #{booking.vehicleId}</div>
            </div>

            {/* Provider */}
            <div
              onClick={() => canClickProvider && onSelectProvider(booking.providerId)}
              title={canClickProvider ? 'Bấm để xem chi tiết nhà cung cấp' : ''}
              className={`group space-y-0.5 rounded-2xl border p-3 transition-all ${
                canClickProvider
                  ? 'cursor-pointer border-slate-200 bg-slate-50/70 hover:border-indigo-400 hover:bg-indigo-50/40 hover:shadow-sm'
                  : 'border-slate-200 bg-slate-50/70'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                  <span>🏢</span> Đơn vị chủ xe
                </span>
                {canClickProvider && (
                  <span className="text-[10px] font-bold text-indigo-600 transition-transform group-hover:translate-x-0.5">
                    Chi tiết →
                  </span>
                )}
              </div>
              <div className="text-sm font-bold text-slate-900 transition-colors group-hover:text-indigo-700">
                {booking.providerDisplayName || `NCC #${booking.providerId}`}
              </div>
              <div className="text-slate-500">Mã đối tác: #{booking.providerId}</div>
            </div>

            {/* Driver */}
            <div
              onClick={() => canClickDriver && onSelectDriver(booking.driverId)}
              title={canClickDriver ? 'Bấm để xem chi tiết tài xế' : ''}
              className={`group space-y-0.5 rounded-2xl border p-3 transition-all ${
                canClickDriver
                  ? 'cursor-pointer border-slate-200 bg-slate-50/70 hover:border-amber-400 hover:bg-amber-50/40 hover:shadow-sm'
                  : 'border-slate-200 bg-slate-50/70'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                  <span>👤</span> Tài xế lái xe
                </span>
                {canClickDriver && (
                  <span className="text-[10px] font-bold text-amber-600 transition-transform group-hover:translate-x-0.5">
                    Chi tiết →
                  </span>
                )}
              </div>
              <div className="text-sm font-bold text-slate-900 transition-colors group-hover:text-amber-700">
                {booking.driverName || (booking.driverId ? `Tài xế #${booking.driverId}` : 'Chưa phân công')}
              </div>
              <div className="text-slate-500">
                {booking.driverId ? `Mã TX: #${booking.driverId}` : 'Chờ nhà xe điều phối'}
              </div>
            </div>
          </div>
        </div>

        {/* Section 2: Itinerary & Timeline details */}
        <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
          <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
            <span>📍</span> Lịch trình đón trả &amp; Mốc thời gian
          </h4>
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
            <div className="col-span-2 sm:col-span-3 lg:col-span-2">
              <span className="block text-slate-500">Địa chỉ đón khách:</span>
              <p className="mt-0.5 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 font-medium text-slate-800">
                {booking.pickupAddress || 'Chưa cung cấp'}
              </p>
            </div>
            <div>
              <span className="block text-slate-500">Thời gian hẹn đón:</span>
              <strong className="text-slate-900">
                {booking.pickupTime ? new Date(booking.pickupTime).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            <div>
              <span className="block text-slate-500">Số hành khách:</span>
              <strong className="text-slate-900">{booking.passengerCount} người</strong>
            </div>
            <div>
              <span className="block text-slate-500">Tổng thanh toán:</span>
              <strong className="text-sm font-extrabold text-brand-700">
                {formatVND(booking.totalAmount)}
              </strong>
            </div>
            <div>
              <span className="block text-slate-500">Bắt đầu thực tế:</span>
              <strong className="text-slate-900">
                {booking.actualStartAt ? new Date(booking.actualStartAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            <div>
              <span className="block text-slate-500">Kết thúc thực tế:</span>
              <strong className="text-slate-900">
                {booking.actualEndAt ? new Date(booking.actualEndAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            {booking.cancelReason && (
              <div className="col-span-2 rounded-xl border border-rose-200 bg-rose-50 px-2.5 py-1.5 text-rose-800 sm:col-span-3 lg:col-span-3">
                <strong>Lý do hủy đơn:</strong> {booking.cancelReason}
              </div>
            )}
          </div>
        </div>

        {/* Section 3: Itemized Charges Breakdown Table */}
        <div>
          <h4 className="mb-1.5 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
            <IconWallet className="h-3.5 w-3.5 text-brand-600" /> Bóc tách chi phí &amp; Các khoản phụ phí (Charges Breakdown)
          </h4>

          {(!booking.charges || booking.charges.length === 0) ? (
            <div className="rounded-2xl border border-dashed border-slate-200 p-3 text-center text-slate-400">
              Chưa có khoản phụ phí riêng lẻ nào được ghi nhận cho đơn này.
            </div>
          ) : (
            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <table className="w-full text-left">
                <thead className="bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-3 py-2">Khoản thu / Phụ phí</th>
                    <th className="px-3 py-2">Diễn giải</th>
                    <th className="px-3 py-2 text-center">SL</th>
                    <th className="px-3 py-2">Đơn giá</th>
                    <th className="px-3 py-2 text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {booking.charges.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-50/60">
                      <td className="px-3 py-2 font-bold text-slate-800">
                        {c.chargeType}
                        {c.isWaived && (
                          <span className="ml-2 rounded-md border border-rose-200 bg-rose-50 px-1.5 py-0.5 text-[10px] font-bold text-rose-700">
                            Đã miễn giảm
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-slate-600">{c.description || '—'}</td>
                      <td className="px-3 py-2 text-center text-slate-600">{c.quantity || 1}</td>
                      <td className="px-3 py-2 text-slate-600">{formatVND(c.unitPrice)}</td>
                      <td
                        className={`px-3 py-2 text-right font-bold ${
                          c.isWaived ? 'text-slate-400 line-through' : 'text-slate-900'
                        }`}
                      >
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
    </AdminModalShell>
  )
}
