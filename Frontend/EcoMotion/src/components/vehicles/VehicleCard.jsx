import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { IconMapPin, IconStar } from '../icons/Icons'

const FUEL_STYLES = {
  EV: { label: 'Điện', className: 'text-brand-700' },
  GASOLINE: { label: 'Xăng', className: 'text-amber-700' },
  DIESEL: { label: 'Dầu', className: 'text-slate-700' },
  HYBRID: { label: 'Hybrid', className: 'text-teal-700' },
}

const TRANSMISSION_LABELS = {
  AUTOMATIC: 'Tự động',
  MANUAL: 'Số sàn',
}

const PLAN_META = {
  HOURLY: { label: 'Giờ', suffix: '/giờ' },
  DAILY: { label: 'Ngày', suffix: '/ngày' },
  MONTHLY: { label: 'Tháng', suffix: '/tháng' },
  YEARLY: { label: 'Năm', suffix: '/năm' },
}

export default function VehicleCard({ vehicle, selectedPlanType }) {
  const fuel = FUEL_STYLES[vehicle.fuelType] ?? {
    label: vehicle.fuelType ?? 'Khác',
    className: 'bg-slate-200 text-slate-700',
  }
  const carName = `${vehicle.brand} ${vehicle.model}${vehicle.manufactureYear ? ` - ${vehicle.manufactureYear}` : ''}`
  const plans = vehicle.plans ?? []
  const cheapest = plans.length
    ? plans.reduce((min, p) => (Number(p.slotPrice) < Number(min.slotPrice) ? p : min))
    : null
  const featured = selectedPlanType
    ? plans.find((plan) => plan.planType === selectedPlanType) ?? cheapest
    : cheapest
  const priceLabel = selectedPlanType
    ? `Gói ${(PLAN_META[selectedPlanType]?.label ?? selectedPlanType).toLowerCase()}`
    : 'Từ'
  const transmission = TRANSMISSION_LABELS[vehicle.transmission] ?? vehicle.transmission ?? '—'
  const reviewCount = Number(vehicle.ratingCount ?? 0)
  const hasRating = reviewCount > 0

  return (
    <article className="group flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card transition-all hover:shadow-lg">
      <div className="relative aspect-[16/10] overflow-hidden bg-slate-100">
        {vehicle.coverImageUrl ? (
          <img
            src={vehicle.coverImageUrl}
            alt={carName}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-sm text-slate-400">
            Chưa có ảnh
          </div>
        )}

        <span className="absolute right-3 top-3 flex items-center gap-1 rounded-md bg-white/90 px-2 py-1 text-[11px] font-bold text-slate-800 backdrop-blur">
          <IconStar className={`h-3.5 w-3.5 ${hasRating ? 'text-amber-500' : 'text-slate-300'}`} />
          {hasRating ? (
            <>
              {Number(vehicle.ratingAvg ?? 0).toFixed(1)}
              <span className="font-medium text-slate-500">({reviewCount})</span>
            </>
          ) : (
            'Mới'
          )}
        </span>
      </div>

      <div className="flex flex-1 flex-col gap-3 p-4">
        <h3 className="truncate text-[15px] font-semibold text-slate-900">
          <Link
            to={`/vehicles/${vehicle.id}`}
            className="transition-colors hover:text-brand-700"
          >
            {carName}
          </Link>
        </h3>

        <div className="grid grid-cols-3 gap-1.5 rounded-lg bg-slate-50 p-2 text-center">
          <div>
            <p className="text-[10px] font-medium text-slate-500">Số chỗ</p>
            <p className="text-[13px] font-bold text-slate-900">{vehicle.seatCount ?? '—'}</p>
          </div>
          <div className="border-x border-slate-200">
            <p className="text-[10px] font-medium text-slate-500">Hộp số</p>
            <p className="truncate text-[13px] font-bold text-slate-900">{transmission}</p>
          </div>
          <div>
            <p className="text-[10px] font-medium text-slate-500">Nhiên liệu</p>
            <p className={`truncate text-[13px] font-bold ${fuel.className}`}>{fuel.label}</p>
          </div>
        </div>

        {vehicle.providerDepotAddress && (
          <p className="flex items-center gap-1.5 text-[12px] text-slate-500">
            <IconMapPin className="h-3.5 w-3.5 shrink-0 text-brand-600" />
            <span className="min-w-0 truncate" title={vehicle.providerDepotAddress}>
              {vehicle.providerDepotAddress}
            </span>
          </p>
        )}

        <div className="mt-auto flex items-end justify-between gap-3 border-t border-slate-100 pt-3">
          <div className="min-w-0">
            <p className="text-[10px] font-medium uppercase tracking-wide text-slate-400">
              {priceLabel}
            </p>
            <p className="truncate text-[15px] font-bold text-brand-700">
              {featured ? formatCurrency(featured.slotPrice) : 'Liên hệ'}
              {featured && (
                <span className="ml-0.5 text-[10px] font-medium text-slate-500">
                  / slot
                </span>
              )}
            </p>
          </div>
          <Link
            to={`/vehicles/${vehicle.id}`}
            className="shrink-0 rounded-lg bg-brand-600 px-4 py-2 text-[13px] font-semibold text-white transition-colors hover:bg-brand-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
          >
            Xem chi tiết
          </Link>
        </div>
      </div>
    </article>
  )
}
