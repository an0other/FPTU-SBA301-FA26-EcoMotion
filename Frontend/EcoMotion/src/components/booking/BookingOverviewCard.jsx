import { IconCalendar, IconClock, IconMapPin, IconUsers, IconTag } from '../icons/Icons'

const PLAN_TYPE_LABELS = {
  DAILY: 'Thuê theo ngày (Daily)',
  MONTHLY: 'Thuê theo tháng (Monthly)',
  YEARLY: 'Thuê dài hạn tự lái (Yearly - Rent-to-Own)',
}

export default function BookingOverviewCard({ booking, formatDateTime }) {
  if (!booking) return null

  const isSelfDrive = booking.plan?.planType === 'YEARLY'

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconCalendar className="h-5 w-5 text-emerald-600" />
          Thông tin hành trình & đặt xe
        </h3>
        <span className="font-mono text-xs font-semibold text-slate-500">
          Mã đơn: #{booking.bookingCode}
        </span>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600">
            <IconClock className="h-5 w-5" />
          </div>
          <div>
            <span className="text-xs font-medium text-slate-400">Thời gian nhận xe</span>
            <p className="text-sm font-semibold text-slate-800">
              {formatDateTime(booking.pickupTime)}
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
            <IconClock className="h-5 w-5" />
          </div>
          <div>
            <span className="text-xs font-medium text-slate-400">Dự kiến hoàn trả</span>
            <p className="text-sm font-semibold text-slate-800">
              {formatDateTime(booking.expectedReturnTime)}
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3 sm:col-span-2">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amber-50 text-amber-600">
            <IconMapPin className="h-5 w-5" />
          </div>
          <div>
            <span className="text-xs font-medium text-slate-400">Địa điểm nhận / giao xe</span>
            <p className="text-sm font-medium text-slate-800">
              {booking.pickupAddress || 'Chưa cập nhật địa chỉ'}
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
            <IconTag className="h-5 w-5" />
          </div>
          <div>
            <span className="text-xs font-medium text-slate-400">Gói dịch vụ</span>
            <p className="text-sm font-semibold text-slate-800">
              {PLAN_TYPE_LABELS[booking.plan?.planType] || booking.plan?.planType || 'Tiêu chuẩn'}
            </p>
            {isSelfDrive && (
              <span className="inline-block mt-1 text-[11px] font-medium text-purple-700 bg-purple-100 rounded px-2 py-0.5">
                Xe tự lái
              </span>
            )}
          </div>
        </div>

        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600">
            <IconUsers className="h-5 w-5" />
          </div>
          <div>
            <span className="text-xs font-medium text-slate-400">Số lượng khách</span>
            <p className="text-sm font-semibold text-slate-800">
              {booking.passengerCount ?? 1} hành khách
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
