import { useState } from 'react'
import AppHeader from '../components/AppHeader'
import FilterSidebar from '../components/vehicles/FilterSidebar'
import VehicleListToolbar from '../components/vehicles/VehicleListToolbar'
import VehicleCard from '../components/vehicles/VehicleCard'
import Pagination from '../components/vehicles/Pagination'
import { useVehicles } from '../hooks/useVehicles'

// "Mọi gói" was dropped from the plan filter, so the list always opens on the hourly plan — the
// shortest one, which is what most renters want and what the card price is quoted against.
// `sortBy` sits in here rather than in its own state because it travels with the other filters: the
// backend orders the whole fleet before cutting the page. Ordering in the browser only reshuffled
// the six cards already on screen, which left page 2 unrelated to page 1.
const EMPTY_FILTERS = {
  keyword: '',
  location: '',
  fuelType: '',
  planType: 'HOURLY',
  maxPrice: '',
  sortBy: 'default',
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
  const [offset, setOffset] = useState(0)

  const { vehicles, total, limit, hasMore, loading, error } = useVehicles({ ...filters, offset })

  // Any filter change invalidates the current page, so jump back to the first one. Changing the
  // sort counts as one: the whole fleet gets reordered, so staying on page 3 would be meaningless.
  const handleFiltersChange = (next) => {
    setFilters(next)
    setOffset(0)
  }

  const handleReset = () => {
    setFilters(EMPTY_FILTERS)
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
        shown={vehicles.length}
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

            {!error && !loading && vehicles.length === 0 && (
              <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-card">
                <p className="text-[15px] font-semibold text-slate-900">
                  Không tìm thấy xe phù hợp
                </p>
                <p className="mt-1 text-[13px] text-slate-500">
                  Thử nới lỏng bộ lọc hoặc đặt lại để xem toàn bộ đội xe.
                </p>
              </div>
            )}

            {!error && !loading && vehicles.length > 0 && (
              <>
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                  {vehicles.map((vehicle) => (
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
