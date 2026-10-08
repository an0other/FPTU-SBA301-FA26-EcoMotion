import { IconShield } from '../icons/Icons'

const FUEL_OPTIONS = [
  { value: '', label: 'Tất cả' },
  { value: 'EV', label: 'Điện' },
  { value: 'GASOLINE', label: 'Xăng' },
  { value: 'DIESEL', label: 'Dầu' },
  { value: 'HYBRID', label: 'Hybrid' },
]

const PLAN_OPTIONS = [
  { value: 'HOURLY', label: 'Theo giờ' },
  { value: 'DAILY', label: 'Theo ngày' },
  { value: 'MONTHLY', label: 'Theo tháng' },
  { value: 'YEARLY', label: 'Theo năm' },
]

const PLAN_SUFFIX = {
  HOURLY: '/giờ',
  DAILY: '/ngày',
  MONTHLY: '/tháng',
  YEARLY: '/năm',
}

/**
 * The price ceiling is read on the same scale as the plan it filters (see `VehicleServiceImpl`), so
 * the slider has to switch scale with the plan as well: an hourly slot lives in the hundreds of
 * thousands, a monthly one in the tens of millions. "Mọi gói" keeps the widest range because it
 * falls back to the cheapest plan, which is always the shortest one.
 */
const PLAN_PRICE_RANGES = {
  '': { min: 50000, max: 20000000, step: 100000 },
  HOURLY: { min: 50000, max: 1000000, step: 25000 },
  DAILY: { min: 200000, max: 5000000, step: 50000 },
  MONTHLY: { min: 1000000, max: 40000000, step: 500000 },
  YEARLY: { min: 5000000, max: 200000000, step: 1000000 },
}

function SectionTitle({ children }) {
  return (
    <h3 className="mb-2.5 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
      {children}
    </h3>
  )
}

function PillGroup({ options, value, onChange }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((option) => {
        const active = value === option.value
        return (
          <button
            key={option.value || 'all'}
            type="button"
            onClick={() => onChange(option.value)}
            className={`rounded-full border px-3 py-1.5 text-[12px] font-semibold transition-colors ${
              active
                ? 'border-brand-600 bg-brand-600 text-white'
                : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}

export default function FilterSidebar({ filters, onChange, onReset }) {
  const set = (patch) => onChange({ ...filters, ...patch })

  const range = PLAN_PRICE_RANGES[filters.planType] ?? PLAN_PRICE_RANGES['']
  const planLabel = PLAN_OPTIONS.find((option) => option.value === filters.planType)?.label
  const suffix = PLAN_SUFFIX[filters.planType] ?? ''
  const formatPrice = (value) => new Intl.NumberFormat('vi-VN').format(value)

  return (
    <aside className="flex flex-col gap-5 rounded-xl border border-slate-200 bg-white p-5 shadow-card">
      <div className="flex items-center justify-between">
        <h2 className="text-[15px] font-bold text-slate-900">Bộ lọc</h2>
        <button
          type="button"
          onClick={onReset}
          className="text-[12px] font-semibold text-brand-700 hover:underline"
        >
          Đặt lại
        </button>
      </div>

      <div>
        <SectionTitle>Loại năng lượng</SectionTitle>
        <PillGroup
          options={FUEL_OPTIONS}
          value={filters.fuelType}
          onChange={(fuelType) => set({ fuelType })}
        />
      </div>

      <div>
        <SectionTitle>Hình thức thuê</SectionTitle>
        <PillGroup
          options={PLAN_OPTIONS}
          value={filters.planType}
          // Switching plan changes the scale the ceiling is read on, so the old value would silently
          // filter the new plan out. Clear it and let the renter pick again.
          onChange={(planType) => set({ planType, maxPrice: '' })}
        />
      </div>

      <div>
        <SectionTitle>{`Giá tối đa — ${(planLabel ?? 'gói rẻ nhất').toLowerCase()}`}</SectionTitle>
        <input
          type="range"
          min={range.min}
          max={range.max}
          step={range.step}
          value={filters.maxPrice || range.max}
          onChange={(event) => {
            const value = Number(event.target.value)
            set({ maxPrice: value >= range.max ? '' : value })
          }}
          className="h-1.5 w-full cursor-pointer accent-brand-600"
        />
        <p className="mt-1.5 text-[12px] font-semibold text-slate-700">
          {filters.maxPrice ? `≤ ${formatPrice(filters.maxPrice)} đ${suffix}` : 'Không giới hạn'}
        </p>
        <p className="mt-0.5 text-[11px] text-slate-400">
          {`Thang ${formatPrice(range.min)} – ${formatPrice(range.max)} đ${suffix}`}
        </p>
      </div>

      <div className="flex items-start gap-2.5 rounded-lg bg-brand-50 p-3">
        <IconShield className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
        <p className="text-[11px] leading-relaxed text-brand-800">
          Mọi xe đều có tài xế kèm theo và được xác thực giấy tờ trước khi hiển thị.
        </p>
      </div>
    </aside>
  )
}
