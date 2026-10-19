import { IconCar, IconZap, IconSettings2, IconUsers } from '../icons/Icons'

const TRANSMISSION_LABELS = {
  AUTOMATIC: 'Tự động (AT)',
  MANUAL: 'Số sàn (MT)',
}

const FUEL_LABELS = {
  EV: 'Thuần điện (EV)',
  HYBRID: 'Hybrid (Xăng - Điện)',
  GASOLINE: 'Xăng',
}

export default function VehicleInfoCard({ vehicle, plan, formatCurrency }) {
  if (!vehicle) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2 mb-3">
          <IconCar className="h-5 w-5 text-emerald-600" />
          Thông tin phương tiện
        </h3>
        <p className="text-sm text-slate-500">Chưa có thông tin xe.</p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconCar className="h-5 w-5 text-emerald-600" />
          Thông tin phương tiện
        </h3>
        <span className="rounded border border-slate-400 bg-white px-2.5 py-0.5 font-mono text-xs font-black tracking-wider text-slate-800 shadow-xs">
          {vehicle.licensePlate || 'CHƯA GẮN BIỂN'}
        </span>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        {/* Vehicle Icon / Thumbnail */}
        <div className="flex h-24 w-28 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-50 to-slate-100 border border-slate-200 text-emerald-600">
          <IconCar className="h-12 w-12" />
        </div>

        {/* Vehicle specs */}
        <div className="flex-1 space-y-2">
          <div>
            <h4 className="text-lg font-extrabold text-slate-900">
              {vehicle.brand} {vehicle.model}
            </h4>
            <p className="text-xs text-slate-500">
              Màu sắc: <span className="font-medium text-slate-700">{vehicle.color || 'Trắng'}</span>
            </p>
          </div>

          <div className="flex flex-wrap gap-2 text-xs text-slate-600">
            <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2.5 py-1 font-medium">
              <IconUsers className="h-3.5 w-3.5 text-slate-500" />
              {vehicle.seatCount} chỗ
            </span>
            <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2.5 py-1 font-medium">
              <IconSettings2 className="h-3.5 w-3.5 text-slate-500" />
              {TRANSMISSION_LABELS[vehicle.transmission] || vehicle.transmission}
            </span>
            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 text-emerald-800 px-2.5 py-1 font-semibold">
              <IconZap className="h-3.5 w-3.5 text-emerald-600" />
              {FUEL_LABELS[vehicle.fuelType] || vehicle.fuelType || 'EV'}
            </span>
          </div>

          {plan?.slotPrice && (
            <div className="pt-1 text-xs text-slate-500">
              Đơn giá theo gói: <span className="font-bold text-slate-800">{formatCurrency(plan.slotPrice)}</span>
              {plan.slotLengthMinutes ? ` / ${Math.round(plan.slotLengthMinutes / 60)} giờ` : ''}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
