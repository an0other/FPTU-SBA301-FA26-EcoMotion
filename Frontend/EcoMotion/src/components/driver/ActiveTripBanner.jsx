import { Link } from 'react-router-dom'
import { formatTripTime } from '../../utils/driverTripUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function ActiveTripBanner({ trip }) {
  if (!trip || trip.status !== 'IN_PROGRESS') return null

  const vehicleInfo = trip.vehicle
    ? `${trip.vehicle.brand || ''} ${trip.vehicle.name || trip.vehicle.model || ''}`.trim()
    : 'Xe của nhà cung cấp'

  return (
    <section
      aria-label="Chuyến đang thực hiện"
      className="mb-6 overflow-hidden rounded-2xl border-2 border-amber-400 bg-gradient-to-br from-amber-50 via-white to-amber-50/50 p-4 shadow-sm shadow-amber-200/50 sm:p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-amber-200/60 pb-3">
        <div className="flex items-center gap-2">
          <span className="relative flex h-3 w-3">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-amber-400 opacity-75" />
            <span className="relative inline-flex h-3 w-3 rounded-full bg-amber-500" />
          </span>
          <span className="text-xs font-bold uppercase tracking-wider text-amber-800">
            Chuyến đang thực hiện
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs font-bold text-slate-700 bg-white px-2 py-0.5 rounded border border-amber-200">
            {trip.bookingCode}
          </span>
          <span className="text-xs font-semibold text-amber-900">
            {formatTripTime(trip.scheduledPickupAt)}
          </span>
        </div>
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <div className="flex items-start gap-2">
            <Icon name="navigation" className="mt-0.5 text-[18px] text-amber-600 shrink-0" />
            <div>
              <span className="block text-[11px] font-medium text-slate-500">Điểm đón khách</span>
              <p className="text-xs font-semibold text-slate-800 leading-snug break-words">
                {trip.pickupAddress || 'Đang cập nhật'}
              </p>
            </div>
          </div>
          {trip.customer?.fullName && (
            <div className="flex items-center gap-2">
              <Icon name="person" className="text-[18px] text-slate-400 shrink-0" />
              <div className="text-xs">
                <span className="font-semibold text-slate-800">{trip.customer.fullName}</span>
                {trip.customer.phone && (
                  <a
                    href={`tel:${trip.customer.phone}`}
                    className="ml-2 font-mono text-brand-600 hover:underline"
                  >
                    {trip.customer.phone}
                  </a>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="flex flex-col justify-between gap-3">
          <div className="flex items-center gap-2 text-xs">
            <Icon name="directions_car" className="text-[18px] text-slate-400 shrink-0" />
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="font-medium text-slate-700">{vehicleInfo}</span>
              {trip.vehicle?.licensePlate && (
                <span className="rounded bg-slate-900 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-300">
                  {trip.vehicle.licensePlate}
                </span>
              )}
            </div>
          </div>

          <div className="flex justify-end">
            <Link
              to={`/driver/trips/${encodeURIComponent(trip.bookingCode)}`}
              className="inline-flex w-full items-center justify-center gap-1.5 rounded-xl bg-amber-500 px-4 py-2.5 text-xs font-bold text-white shadow-sm transition hover:bg-amber-600 sm:w-auto"
            >
              <span>Xem chi tiết chuyến</span>
              <Icon name="arrow_forward" className="text-[16px]" />
            </Link>
          </div>
        </div>
      </div>
    </section>
  )
}
