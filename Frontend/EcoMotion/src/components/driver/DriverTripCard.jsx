import { Link } from 'react-router-dom'
import {
  formatTripDateTime,
  getRelativeDayLabel,
  deriveExecutionStatus,
  getExecutionStatusConfig,
} from '../../utils/driverTripUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function DriverTripCard({ trip }) {
  if (!trip) return null

  const executionStatus = deriveExecutionStatus(trip)
  const statusConfig = getExecutionStatusConfig(executionStatus)
  const relativeDay = getRelativeDayLabel(trip.scheduledPickupAt)
  const vehicle = trip.vehicle
  const customer = trip.customer

  const vehicleTitle = vehicle
    ? `${vehicle.brand || ''} ${vehicle.name || vehicle.model || ''}`.trim()
    : 'Chưa chỉ định xe'

  return (
    <article
      aria-label={`Chuyến đi ${trip.bookingCode}`}
      className="group relative flex flex-col justify-between overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-4 shadow-xs transition hover:border-brand-200 hover:shadow-md sm:p-5"
    >
      {/* Header: Booking Code & Status Badge */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm font-bold text-slate-900">
            {trip.bookingCode || 'BK-CHƯA-MÃ'}
          </span>
          {trip.rentalType && (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-600">
              {trip.rentalType === 'HOURLY' ? 'Theo giờ' : 'Theo ngày'}
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5">
          {relativeDay && (
            <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-700 border border-blue-200">
              {relativeDay}
            </span>
          )}
          <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-bold ${statusConfig.badgeClass}`}
          >
            <span className={`h-1.5 w-1.5 rounded-full ${statusConfig.dotClass}`} />
            {statusConfig.label}
          </span>
        </div>
      </div>

      {/* Main Body */}
      <div className="my-3 space-y-3">
        {/* Pickup Time */}
        <div className="flex items-center gap-2 text-slate-700">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-teal-50 text-teal-700">
            <Icon name="schedule" className="text-[18px]" />
          </span>
          <div>
            <span className="block text-[10px] font-semibold uppercase tracking-wider text-slate-400">
              Giờ đón dự kiến
            </span>
            <span className="text-xs font-bold text-slate-900">
              {formatTripDateTime(trip.scheduledPickupAt)}
            </span>
          </div>
        </div>

        {/* Pickup Location */}
        <div className="flex items-start gap-2 text-slate-700">
          <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-amber-50 text-amber-700">
            <Icon name="pin_drop" className="text-[18px]" />
          </span>
          <div className="min-w-0 flex-1">
            <span className="block text-[10px] font-semibold uppercase tracking-wider text-slate-400">
              Địa điểm đón
            </span>
            <p className="text-xs font-medium text-slate-800 break-words leading-relaxed">
              {trip.pickupAddress || 'Đang cập nhật địa chỉ đón'}
            </p>
          </div>
        </div>

        {/* Vehicle & Customer row */}
        <div className="grid grid-cols-1 gap-2.5 rounded-xl bg-slate-50/70 p-3 text-xs sm:grid-cols-2">
          {/* Vehicle info */}
          <div className="flex items-center gap-2">
            <Icon name="directions_car" className="text-[18px] text-slate-400 shrink-0" />
            <div className="min-w-0">
              <span className="block truncate font-semibold text-slate-800">
                {vehicleTitle}
              </span>
              <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                {vehicle?.licensePlate && (
                  <span className="font-mono font-bold text-slate-700 bg-white px-1 py-0.2 rounded border border-slate-200">
                    {vehicle.licensePlate}
                  </span>
                )}
                {vehicle?.seatCount && <span>• {vehicle.seatCount} chỗ</span>}
              </div>
            </div>
          </div>

          {/* Customer info */}
          <div className="flex items-center gap-2">
            <Icon name="person" className="text-[18px] text-slate-400 shrink-0" />
            <div className="min-w-0">
              <span className="block truncate font-semibold text-slate-800">
                {customer?.fullName || 'Khách thuê'}
              </span>
              {customer?.phone ? (
                <a
                  href={`tel:${customer.phone}`}
                  onClick={(e) => e.stopPropagation()}
                  className="inline-flex items-center gap-1 font-mono text-[11px] font-medium text-brand-600 hover:underline"
                >
                  <Icon name="call" className="text-[12px]" />
                  {customer.phone}
                </a>
              ) : (
                <span className="text-[11px] text-slate-400">Số ĐT bảo mật</span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Footer: View detail button */}
      <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-3">
        <span className="text-[11px] text-slate-400">
          {trip.passengerCount ? `${trip.passengerCount} hành khách` : 'Chuyến thuê có tài xế'}
        </span>
        <Link
          to={`/driver/trips/${encodeURIComponent(trip.bookingCode)}`}
          className="inline-flex items-center gap-1 rounded-xl bg-brand-50 px-3 py-1.5 text-xs font-bold text-brand-700 transition hover:bg-brand-600 hover:text-white"
        >
          <span>Xem chi tiết</span>
          <Icon name="chevron_right" className="text-[16px]" />
        </Link>
      </div>
    </article>
  )
}
