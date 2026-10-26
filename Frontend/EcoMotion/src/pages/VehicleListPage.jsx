import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import AppHeader from '../components/AppHeader'
import FilterSidebar from '../components/vehicles/FilterSidebar'
import VehicleCard from '../components/vehicles/VehicleCard'
import Pagination from '../components/vehicles/Pagination'
import { useVehicles } from '../hooks/useVehicles'
import { useMapVehicles } from '../hooks/useMapVehicles'
import { vehicleService } from '../services/vehicleService'

const VehicleMap = lazy(() => import('../components/vehicles/VehicleMap'))

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
  const [draftFilters, setDraftFilters] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)
  const [offset, setOffset] = useState(0)
  const [view, setView] = useState('list')
  const [draftOrigin, setDraftOrigin] = useState(null)
  const [selectedOrigin, setSelectedOrigin] = useState(null)
  const [draftRadius, setDraftRadius] = useState(10)
  const [appliedRadius, setAppliedRadius] = useState(10)
  const [gpsLoading, setGpsLoading] = useState(false)
  const [locationMessage, setLocationMessage] = useState('')
  const [pinLookup, setPinLookup] = useState({ key: null, address: '', loading: false })
  const addressCacheRef = useRef(new Map())
  const [addressQuery, setAddressQuery] = useState('')
  const [addressEditing, setAddressEditing] = useState(false)
  const [addressResults, setAddressResults] = useState([])
  const [addressSearching, setAddressSearching] = useState(false)
  const [addressError, setAddressError] = useState('')

  const { vehicles, total, limit, hasMore, loading, error } = useVehicles(
    { ...appliedFilters, offset }, view === 'list')
  const mapResults = useMapVehicles(appliedFilters, selectedOrigin, appliedRadius, view === 'map')
  const hasPendingOrigin = Boolean(draftOrigin && selectedOrigin
    && (draftOrigin.latitude !== selectedOrigin.latitude
      || draftOrigin.longitude !== selectedOrigin.longitude))
  const pinnedOrigin = draftOrigin || selectedOrigin
  const pinnedLatitude = pinnedOrigin?.latitude
  const pinnedLongitude = pinnedOrigin?.longitude
  const pinKey = pinnedOrigin ? `${pinnedLatitude},${pinnedLongitude}` : null
  const pinAddress = pinLookup.key !== pinKey || pinLookup.loading
    ? 'Đang tìm địa chỉ gần đúng…'
    : pinLookup.address || 'Chưa tra được địa chỉ gần đúng cho vị trí này.'
  const displayedAddress = addressEditing ? addressQuery
    : pinLookup.key === pinKey && !pinLookup.loading ? pinLookup.address : ''

  useEffect(() => {
    if (pinnedLatitude == null || pinnedLongitude == null) return undefined
    const key = `${pinnedLatitude},${pinnedLongitude}`
    const cachedAddress = addressCacheRef.current.get(key)
    if (cachedAddress) {
      setPinLookup({ key, address: cachedAddress, loading: false })
      return undefined
    }
    let active = true
    setPinLookup({ key, address: '', loading: true })
    const timer = window.setTimeout(() => {
      vehicleService.reverseGeocode({ latitude: pinnedLatitude, longitude: pinnedLongitude })
        .then((address) => {
          if (!active) return
          if (address) addressCacheRef.current.set(key, address)
          setPinLookup({ key, address: address || '', loading: false })
        }).catch(() => {
          if (active) setPinLookup({ key, address: '', loading: false })
        })
    }, 350)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [pinnedLatitude, pinnedLongitude])

  const handleMapPick = useCallback((point) => {
    setDraftOrigin(point)
    setAddressQuery('')
    setAddressEditing(false)
    setAddressResults([])
    setAddressError('')
    setLocationMessage('Đã đặt ghim. Bấm “Tìm xe gần đây” để xác nhận vị trí.')
  }, [])

  const handleAddressSearch = async (event) => {
    event.preventDefault()
    const query = displayedAddress.trim()
    if (query.length < 3) {
      setAddressError('Nhập ít nhất 3 ký tự để tìm địa chỉ.')
      setAddressResults([])
      return
    }
    setAddressSearching(true)
    setAddressError('')
    setAddressResults([])
    try {
      const results = await vehicleService.searchAddress(query)
      setAddressResults(results)
      if (!results.length) setAddressError('Không tìm thấy địa chỉ phù hợp tại Việt Nam.')
    } catch (error) {
      setAddressError(error?.status === 503
        ? 'Dịch vụ tra địa chỉ đang không kết nối được. Bạn có thể dùng GPS hoặc đặt ghim trên bản đồ.'
        : 'Không tra được địa chỉ. Vui lòng kiểm tra kết nối rồi thử lại.')
    } finally {
      setAddressSearching(false)
    }
  }

  const handleAddressPick = (result) => {
    const point = { latitude: Number(result.latitude), longitude: Number(result.longitude) }
    addressCacheRef.current.set(`${point.latitude},${point.longitude}`, result.address)
    setDraftOrigin(point)
    setAddressQuery(result.address)
    setAddressEditing(false)
    setAddressResults([])
    setAddressError('')
    setLocationMessage('Đã đặt ghim theo địa chỉ. Bấm “Tìm xe gần đây” để xác nhận vị trí.')
  }

  const handleGps = () => {
    if (!navigator.geolocation) {
      setLocationMessage('Trình duyệt không hỗ trợ GPS. Hãy đặt ghim trên bản đồ.')
      return
    }
    setGpsLoading(true)
    setLocationMessage('')
    setAddressResults([])
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      const point = { latitude: coords.latitude, longitude: coords.longitude }
      setDraftOrigin(point)
      setSelectedOrigin(point)
      setAppliedRadius(draftRadius)
      setAddressQuery('')
      setAddressEditing(false)
      setAddressError('')
      setGpsLoading(false)
    }, () => {
      setGpsLoading(false)
      setLocationMessage('Không lấy được vị trí. Hãy đặt ghim trên bản đồ.')
    }, { enableHighAccuracy: true, timeout: 10000 })
  }

  const handleMapSearch = () => {
    if (!draftOrigin) {
      setLocationMessage('Hãy chọn vị trí bằng GPS hoặc đặt ghim trên bản đồ.')
      return
    }
    setSelectedOrigin({ ...draftOrigin })
    setAppliedRadius(draftRadius)
    setLocationMessage('')
  }

  const handleFiltersChange = (next) => {
    setDraftFilters(next)
  }

  const handleReset = () => {
    setDraftFilters(EMPTY_FILTERS)
  }

  const filtersDirty =
    draftFilters.fuelType !== appliedFilters.fuelType
    || draftFilters.planType !== appliedFilters.planType
    || draftFilters.maxPrice !== appliedFilters.maxPrice
    || draftFilters.sortBy !== appliedFilters.sortBy

  const handleApplyFilters = () => {
    setAppliedFilters((prev) => ({
      ...prev,
      fuelType: draftFilters.fuelType,
      planType: draftFilters.planType,
      maxPrice: draftFilters.maxPrice,
      sortBy: draftFilters.sortBy,
    }))
    setOffset(0)
    if (view === 'map') setAppliedRadius(draftRadius)
  }

  const handleSearchSubmit = ({ keyword, location }) => {
    setAppliedFilters((prev) => ({ ...prev, keyword, location }))
    setOffset(0)
    document.getElementById('vehicle-results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader filters={appliedFilters} onSearchSubmit={handleSearchSubmit}
        showViewSwitch view={view} onViewChange={setView} />

      <nav aria-label="Đường dẫn trang" className="border-b border-slate-200 bg-white py-3">
        <div className="mx-auto max-w-[1400px] px-4 text-xs sm:px-6">
          <span className="font-semibold text-slate-800" aria-current="page">Trang chủ</span>
        </div>
      </nav>

      <main className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-6">
        <h1 className="sr-only">{view === 'map' ? 'Bản đồ tìm xe' : 'Danh sách xe'}</h1>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          <div className={`${view === 'map' ? 'order-2 lg:order-1' : ''} lg:col-span-3`}>
            <FilterSidebar
              filters={draftFilters}
              onChange={handleFiltersChange}
              onReset={handleReset}
              onApply={handleApplyFilters}
              dirty={filtersDirty}
            />
          </div>

          <div id="vehicle-results" className={`${view === 'map' ? 'order-1 lg:order-2' : ''} scroll-mt-24 lg:col-span-9`}>
            {view === 'map' && (
              <div className="space-y-4">
                <h2 className="sr-only">Tìm xe gần bạn</h2>
                <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card">
                  <Suspense fallback={<div className="h-[480px] animate-pulse bg-slate-100 sm:h-[580px]" />}>
                    <VehicleMap draftOrigin={draftOrigin} selectedOrigin={selectedOrigin}
                      vehicles={mapResults.loading || hasPendingOrigin ? [] : mapResults.vehicles}
                      selectedPlanType={appliedFilters.planType} radiusKm={appliedRadius}
                      onPick={handleMapPick} onGps={handleGps} gpsLoading={gpsLoading}
                      draftRadius={draftRadius} onRadiusChange={setDraftRadius}
                      onMapSearch={handleMapSearch}>
                      <div className="rounded-xl bg-white/95 p-2 shadow-lg">
                        <form onSubmit={handleAddressSearch} className="flex gap-2">
                          <label htmlFor="map-address" className="sr-only">Địa chỉ muốn tìm trên bản đồ</label>
                          <input id="map-address" type="search" value={displayedAddress}
                            onFocus={(event) => {
                              setAddressQuery(displayedAddress)
                              setAddressEditing(true)
                              event.target.select()
                            }}
                            onChange={(event) => {
                              setAddressQuery(event.target.value)
                              setAddressEditing(true)
                              setAddressResults([])
                              setAddressError('')
                            }}
                            disabled={addressSearching}
                            placeholder={pinnedOrigin ? pinAddress : 'Nhập địa chỉ hoặc địa điểm'}
                            title={displayedAddress || undefined}
                            className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 disabled:opacity-60" />
                          <button type="submit" disabled={addressSearching}
                            className="shrink-0 rounded-lg border border-brand-600 px-3 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50">
                            {addressSearching ? 'Đang tìm…' : 'Tìm địa chỉ'}
                          </button>
                        </form>
                        {addressError && <p role="alert" className="mt-2 text-xs text-on-error-container">{addressError}</p>}
                        {addressResults.length > 0 && (
                          <ul aria-label="Địa chỉ tìm được" className="mt-2 max-h-48 overflow-y-auto rounded-lg border border-slate-200 bg-white">
                            {addressResults.map((result, index) => (
                              <li key={`${result.latitude}-${result.longitude}-${index}`} className="border-b border-slate-100 last:border-b-0">
                                <button type="button" onClick={() => handleAddressPick(result)}
                                  className="w-full px-3 py-2 text-left text-sm text-slate-700 hover:bg-brand-50">
                                  {result.address}
                                </button>
                              </li>
                            ))}
                          </ul>
                        )}
                        {pinnedOrigin && (
                          <div role="status" className="mt-2 border-t border-slate-200 pt-2 text-xs text-slate-700">
                            <p className="font-semibold text-brand-800">
                              {hasPendingOrigin || !selectedOrigin ? 'Ghim đang chọn' : 'Vị trí tìm kiếm'}
                            </p>
                            {!pinLookup.loading && !pinLookup.address && (
                              <p className="mt-1">{pinAddress}</p>
                            )}
                            <p className="mt-1 text-slate-500">
                              Tọa độ: {Number(pinnedLatitude).toFixed(5)}, {Number(pinnedLongitude).toFixed(5)}
                            </p>
                          </div>
                        )}
                        <p className="mt-2 text-[11px] text-slate-500">
                          Dữ liệu địa chỉ © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer" className="underline">OpenStreetMap contributors</a>
                        </p>
                        {locationMessage && <p role="status" className="mt-2 text-xs text-slate-600">{locationMessage}</p>}
                      </div>
                    </VehicleMap>
                  </Suspense>
                </div>
                {!selectedOrigin && <p className="text-center text-sm text-slate-600">Chọn vị trí để xem xe trong bán kính 10 hoặc 15 km.</p>}
                {hasPendingOrigin && <p role="status" className="text-center text-sm text-slate-600">Xác nhận ghim mới để tìm xe tại vị trí này.</p>}
                {selectedOrigin && !hasPendingOrigin && mapResults.loading && <p role="status" className="text-center text-sm text-slate-600">Đang tìm xe gần vị trí đã chọn…</p>}
                {selectedOrigin && !hasPendingOrigin && mapResults.error && <p role="alert" className="text-center text-sm text-on-error-container">{mapResults.error}</p>}
                {selectedOrigin && !hasPendingOrigin && !mapResults.loading && !mapResults.error && (
                  <p role="status" className="text-center text-sm text-slate-600">
                    {mapResults.vehicles.length
                      ? `Tìm thấy ${mapResults.vehicles.length} xe trong bán kính ${appliedRadius} km. Rê chuột hoặc chạm vào chấm đỏ để xem danh sách xe.`
                      : `Không tìm thấy xe trong bán kính ${appliedRadius} km với bộ lọc hiện tại.`}
                  </p>
                )}
              </div>
            )}

            {view === 'list' && <>
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
                <p className="mb-4 text-sm font-medium text-[#14221A]">
                  {total} xe phù hợp
                </p>
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                  {vehicles.map((vehicle) => (
                    <VehicleCard
                      key={vehicle.id}
                      vehicle={vehicle}
                      selectedPlanType={appliedFilters.planType}
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
            </>}
          </div>
        </div>
      </main>
    </div>
  )
}
