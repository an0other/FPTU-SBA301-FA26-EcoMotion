import { useMemo, useState } from 'react'
import AppHeader from '../components/AppHeader'
import FilterSidebar from '../components/vehicles/FilterSidebar'
import VehicleListToolbar from '../components/vehicles/VehicleListToolbar'
import VehicleCard from '../components/vehicles/VehicleCard'
import Pagination from '../components/vehicles/Pagination'
import { useVehicles } from '../hooks/useVehicles'

// "Mọi gói" was dropped from the plan filter, so the list always opens on the hourly plan — the
// shortest one, which is what most renters want and what the card price is quoted against.
const EMPTY_FILTERS = {
  keyword: '',
  location: '',
  fuelType: '',
  planType: 'HOURLY',
  maxPrice: '',
}

/**
 * Price of one card, used by the client-side sort. When a plan filter is active the sort compares
 * that plan's price (the same figure the card prints), otherwise the cheapest plan wins.
 */
function priceOf(vehicle, planType) {
  const plans = vehicle.plans ?? []
  if (!plans.length) return Number.POSITIVE_INFINITY
  const scoped = planType ? plans.filter((plan) => plan.planType === planType) : plans
  const pool = scoped.length ? scoped : plans
  return pool.reduce((min, plan) => Math.min(min, Number(plan.slotPrice)), Number.POSITIVE_INFINITY)
}

/** Applied client-side because the backend returns the fleet already filtered. */
function sortVehicles(vehicles, sortBy, planType) {
  const copy = [...vehicles]
  switch (sortBy) {
    case 'price-asc':
      return copy.sort((a, b) => priceOf(a, planType) - priceOf(b, planType))
    case 'price-desc':
      return copy.sort((a, b) => priceOf(b, planType) - priceOf(a, planType))
    case 'rating':
      return copy.sort((a, b) => Number(b.ratingAvg ?? 0) - Number(a.ratingAvg ?? 0))
    default:
      return copy
  }
}

function SkeletonCard() {
  return (
    <div className="animate-pulse overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card">
      <div className="aspect-[16/10] bg-slate-200" />
      <div className="flex flex-col gap-3 p-4">
        <div className="h-4 w-3/4 rounded bg-slate-200" />
        <div className="h-3 w-1/2 rounded bg-slate-100" />
        <div className="h-12 rounded-lg bg-slate-100" />
        <div className="h-9 w-full rounded-lg bg-slate-100" />
      </div>
    </div>
  )
}

export default function VehicleListPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [sortBy, setSortBy] = useState('default')
  const [offset, setOffset] = useState(0)

  const { vehicles, total, limit, hasMore, loading, error } = useVehicles({ ...filters, offset })

  const sorted = useMemo(
    () => sortVehicles(vehicles, sortBy, filters.planType),
    [vehicles, sortBy, filters.planType],
  )

  // Any filter change invalidates the current page, so jump back to the first one. Sorting is
  // client-side and only reorders the current page, so it does not reset paging.
  const handleFiltersChange = (next) => {
    setFilters(next)
    setOffset(0)
  }

  const handleReset = () => {
    setFilters(EMPTY_FILTERS)
    setSortBy('default')
    setOffset(0)
  }

  // The header search pod is a draft: it hands its two fields over on submit (button or Enter), so
  // typing never hits the API. The sidebar filters, by contrast, apply immediately.
  const handleSearchSubmit = ({ keyword, location }) => {
    setFilters((prev) => ({ ...prev, keyword, location }))
    setOffset(0)
    document.getElementById('vehicle-results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader filters={filters} onSearchSubmit={handleSearchSubmit} />

      <VehicleListToolbar
        planType={filters.planType}
        location={filters.location}
        keyword={filters.keyword}
        total={total}
        offset={offset}
        shown={sorted.length}
        sortBy={sortBy}
        onSortChange={setSortBy}
        onHome={handleReset}
      />

      <main className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-6">
        <div className="mb-5">
          <h1 className="text-2xl font-bold tracking-tight text-ink">Thuê xe có tài xế</h1>
          <p className="mt-1 text-[14px] text-slate-500">
            Chọn đúng chiếc xe bạn muốn — thuê theo giờ, ngày hoặc tháng.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          <div className="lg:col-span-3">
            <FilterSidebar
              filters={filters}
              onChange={handleFiltersChange}
              onReset={handleReset}
            />
          </div>

          <div id="vehicle-results" className="scroll-mt-24 lg:col-span-9">
            {error && (
              <div className="rounded-xl border border-error-container bg-error-container/40 p-6 text-center">
                <p className="text-[14px] font-semibold text-on-error-container">{error}</p>
              </div>
            )}

            {!error && loading && (
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 6 }).map((_, index) => (
                  <SkeletonCard key={index} />
                ))}
              </div>
            )}

            {!error && !loading && sorted.length === 0 && (
              <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-card">
                <p className="text-[15px] font-semibold text-slate-900">
                  Không tìm thấy xe phù hợp
                </p>
                <p className="mt-1 text-[13px] text-slate-500">
                  Thử nới lỏng bộ lọc hoặc đặt lại để xem toàn bộ đội xe.
                </p>
              </div>
            )}

            {!error && !loading && sorted.length > 0 && (
              <>
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                  {sorted.map((vehicle) => (
                    <VehicleCard
                      key={vehicle.id}
                      vehicle={vehicle}
                      selectedPlanType={filters.planType}
                    />
                  ))}
                </div>

                <Pagination
                  total={total}
                  offset={offset}
                  limit={limit}
                  hasMore={hasMore}
                  onPageChange={setOffset}
                />
              </>
            )}
          </div>
        </div>
      </main>
    </div>
  )
}
