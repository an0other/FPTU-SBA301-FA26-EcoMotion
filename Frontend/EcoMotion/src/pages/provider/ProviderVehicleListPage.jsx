import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { providerService } from '../../services/providerService'
import { formatCurrency, TRANSMISSION_LABELS } from '../../utils/formatCurrency'
import Toast from '../../components/Toast'
import ProviderHeader from '../../components/provider/ProviderHeader'

export default function ProviderVehicleListPage() {
  const { user } = useAuth()

  const [summary, setSummary] = useState(null)
  const [vehicles, setVehicles] = useState([])
  const [stats, setStats] = useState({ total: 0, approved: 0, pending: 0, hidden: 0, rejected: 0 })
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [fuelFilter, setFuelFilter] = useState('')
  const [sortBy, setSortBy] = useState('newest')
  const [viewMode, setViewMode] = useState('grid')
  const [togglingIds, setTogglingIds] = useState(new Set())

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedKeyword(keyword)
      setPage(0)
    }, 300)
    return () => clearTimeout(handler)
  }, [keyword])

  useEffect(() => {
    let ignore = false

    async function fetchData() {
      try {
        const [sumRes, fleetRes] = await Promise.allSettled([
          providerService.getDashboardSummary(),
          providerService.getVehicles({
            keyword: debouncedKeyword || undefined,
            fuelType: fuelFilter || undefined,
            status: statusFilter || undefined,
            sortBy,
            page,
            size: 9,
          }),
        ])

        if (ignore) return

        if (sumRes.status === 'fulfilled' && sumRes.value) {
          setSummary(sumRes.value)
        }

        if (fleetRes.status === 'fulfilled' && fleetRes.value) {
          setVehicles(fleetRes.value.items || [])
          setStats(fleetRes.value.stats || { total: 0, approved: 0, pending: 0, hidden: 0, rejected: 0 })
          setTotalPages(fleetRes.value.totalPages || 0)
          setTotalItems(fleetRes.value.totalItems || 0)
          setError(null)
        } else if (fleetRes.status === 'rejected') {
          console.error('Fetch fleet failed:', fleetRes.reason)
          setError('Không thể tải danh sách phương tiện từ hệ thống.')
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    fetchData()

    return () => {
      ignore = true
    }
  }, [debouncedKeyword, fuelFilter, statusFilter, sortBy, page])

  async function handleToggleVisibility(vehicle) {
    if (vehicle.status === 'PENDING' || vehicle.status === 'REJECTED') {
      setToast({
        message: vehicle.status === 'PENDING'
          ? 'Xe đang chờ duyệt, chưa thể bật nhận khách.'
          : 'Xe bị từ chối hồ sơ, không thể bật nhận khách.',
        type: 'error',
      })
      return
    }

    const newVisible = vehicle.status === 'HIDDEN'
    const targetStatus = newVisible ? 'APPROVED' : 'HIDDEN'
    const previousStatus = vehicle.status

    setVehicles((prev) =>
      prev.map((v) => (v.id === vehicle.id ? { ...v, status: targetStatus } : v))
    )
    setStats((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        approved: newVisible ? prev.approved + 1 : Math.max(0, prev.approved - 1),
        hidden: newVisible ? Math.max(0, prev.hidden - 1) : prev.hidden + 1,
      }
    })
    setTogglingIds((prev) => new Set(prev).add(vehicle.id))

    try {
      await providerService.toggleVehicleVisibility(vehicle.id, newVisible)
      setToast({
        message: newVisible
          ? `Đã bật nhận khách cho xe ${vehicle.licensePlate}`
          : `Đã tạm ẩn xe ${vehicle.licensePlate}`,
        type: 'success',
      })
    } catch (err) {
      console.error('Toggle failed:', err)
      setVehicles((prev) =>
        prev.map((v) => (v.id === vehicle.id ? { ...v, status: previousStatus } : v))
      )
      setStats((prev) => {
        if (!prev) return prev
        return {
          ...prev,
          approved: newVisible ? Math.max(0, prev.approved - 1) : prev.approved + 1,
          hidden: newVisible ? prev.hidden + 1 : Math.max(0, prev.hidden - 1),
        }
      })
      setToast({
        message: err?.response?.data?.message || 'Thao tác cập nhật trạng thái thất bại.',
        type: 'error',
      })
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev)
        next.delete(vehicle.id)
        return next
      })
    }
  }

  function handleFilterStatus(target) {
    setStatusFilter(target)
    setPage(0)
  }

  function handleClearFilters() {
    setKeyword('')
    setDebouncedKeyword('')
    setStatusFilter('')
    setFuelFilter('')
    setSortBy('newest')
    setPage(0)
  }

  const providerName = summary?.providerName || user?.fullName || 'Nhà xe đối tác'
  const depotAddress = summary?.depotAddress || '120 Phổ Quang, Q. Tân Bình, TP.HCM'

  function renderStatusBadge(status) {
    switch (status) {
      case 'APPROVED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            Đang hoạt động
          </span>
        )
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            Chờ duyệt
          </span>
        )
      case 'HIDDEN':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600 border border-slate-300">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
            Tạm ẩn
          </span>
        )
      case 'REJECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            Cần bổ sung
          </span>
        )
      default:
        return null
    }
  }

  function renderFuelBadge(fuelType) {
    switch (fuelType) {
      case 'EV':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11.5px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
            ⚡ Thuần điện
          </span>
        )
      case 'HYBRID':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11.5px] font-bold bg-teal-50 text-teal-800 border border-teal-200">
            🔋 Xăng điện (Hybrid)
          </span>
        )
      case 'DIESEL':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11.5px] font-bold bg-amber-50 text-amber-900 border border-amber-200">
            🛢️ Dầu Diesel
          </span>
        )
      case 'GASOLINE':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[11.5px] font-bold bg-blue-50 text-blue-900 border border-blue-200">
            ⛽ Xăng
          </span>
        )
      default:
        return null
    }
  }

  return (
    <div className="min-h-screen bg-[#f8f9ff] text-[#334155] font-sans">
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Header */}
      <ProviderHeader
        activeTab="vehicles"
        providerName={providerName}
        depotAddress={depotAddress}
      />

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto px-6 lg:px-12 py-8 space-y-8">
        {/* Title and Top Actions */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-[#0b1c30] tracking-tight">
              Quản lý Đội xe
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Theo dõi tình trạng hoạt động, kiểm duyệt và bật/tắt nhận khách trực tiếp.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Link
              to="/provider/vehicles/new"
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm shadow-sm transition-colors"
            >
              <span className="material-symbols-outlined text-[20px]">add</span>
              <span>Đăng ký xe mới</span>
            </Link>
          </div>
        </div>

        {/* 5 Mosaic KPI Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 sm:gap-4">
          {/* All */}
          <button
            type="button"
            onClick={() => handleFilterStatus('')}
            className={`p-4 rounded-2xl border text-left transition-all ${
              statusFilter === ''
                ? 'bg-slate-900 text-white border-slate-900 shadow-md ring-2 ring-slate-900/20'
                : 'bg-white text-slate-700 border-slate-200/80 hover:border-slate-300 hover:shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider opacity-80">Tất cả xe</span>
              <span className="material-symbols-outlined text-[20px] opacity-70">directions_car</span>
            </div>
            <p className="text-2xl sm:text-3xl font-extrabold mt-2 leading-none">{stats.total}</p>
          </button>

          {/* Approved */}
          <button
            type="button"
            onClick={() => handleFilterStatus('APPROVED')}
            className={`p-4 rounded-2xl border text-left transition-all ${
              statusFilter === 'APPROVED'
                ? 'bg-emerald-700 text-white border-emerald-700 shadow-md ring-2 ring-emerald-600/30'
                : 'bg-white text-slate-700 border-slate-200/80 hover:border-emerald-300 hover:shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-600">Đang nhận khách</span>
              <span className="material-symbols-outlined text-[20px] text-emerald-600">check_circle</span>
            </div>
            <p className="text-2xl sm:text-3xl font-extrabold text-emerald-700 mt-2 leading-none">{stats.approved}</p>
          </button>

          {/* Pending */}
          <button
            type="button"
            onClick={() => handleFilterStatus('PENDING')}
            className={`p-4 rounded-2xl border text-left transition-all ${
              statusFilter === 'PENDING'
                ? 'bg-amber-600 text-white border-amber-600 shadow-md ring-2 ring-amber-500/30'
                : 'bg-white text-slate-700 border-slate-200/80 hover:border-amber-300 hover:shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-amber-600">Chờ duyệt hồ sơ</span>
              <span className="material-symbols-outlined text-[20px] text-amber-600">hourglass_top</span>
            </div>
            <p className="text-2xl sm:text-3xl font-extrabold text-amber-700 mt-2 leading-none">{stats.pending}</p>
          </button>

          {/* Hidden */}
          <button
            type="button"
            onClick={() => handleFilterStatus('HIDDEN')}
            className={`p-4 rounded-2xl border text-left transition-all ${
              statusFilter === 'HIDDEN'
                ? 'bg-slate-700 text-white border-slate-700 shadow-md ring-2 ring-slate-500/30'
                : 'bg-white text-slate-700 border-slate-200/80 hover:border-slate-400 hover:shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Tạm ẩn</span>
              <span className="material-symbols-outlined text-[20px] text-slate-500">visibility_off</span>
            </div>
            <p className="text-2xl sm:text-3xl font-extrabold text-slate-700 mt-2 leading-none">{stats.hidden}</p>
          </button>

          {/* Rejected */}
          <button
            type="button"
            onClick={() => handleFilterStatus('REJECTED')}
            className={`p-4 rounded-2xl border text-left transition-all col-span-2 sm:col-span-1 ${
              statusFilter === 'REJECTED'
                ? 'bg-rose-700 text-white border-rose-700 shadow-md ring-2 ring-rose-500/30'
                : 'bg-white text-slate-700 border-slate-200/80 hover:border-rose-300 hover:shadow-xs'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-rose-600">Cần bổ sung</span>
              <span className="material-symbols-outlined text-[20px] text-rose-600">error</span>
            </div>
            <p className="text-2xl sm:text-3xl font-extrabold text-rose-700 mt-2 leading-none">{stats.rejected}</p>
          </button>
        </div>

        {/* Control Toolbar */}
        <div className="bg-white p-4 sm:p-5 rounded-2xl border border-slate-200/80 shadow-[0_1px_6px_rgba(11,28,48,0.03)] space-y-4">
          <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 sm:gap-4">
            {/* Search Input */}
            <div className="relative flex-1">
              <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-[20px]">
                search
              </span>
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm kiếm theo biển số, hãng xe, dòng xe..."
                className="w-full pl-10 pr-10 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-hidden focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 placeholder:text-slate-400"
              />
              {keyword && (
                <button
                  type="button"
                  onClick={() => setKeyword('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  ✕
                </button>
              )}
            </div>

            {/* Filter & Sort Controls */}
            <div className="flex flex-wrap items-center gap-2.5">
              {/* Fuel Filter */}
              <select
                value={fuelFilter}
                onChange={(e) => {
                  setFuelFilter(e.target.value)
                  setPage(0)
                }}
                className="px-3 py-2.5 rounded-xl border border-slate-200 text-xs sm:text-sm font-medium text-slate-700 bg-white focus:outline-hidden focus:border-emerald-600"
              >
                <option value="">Tất cả nhiên liệu</option>
                <option value="EV">⚡ Thuần điện (EV)</option>
                <option value="HYBRID">🔋 Xăng điện (Hybrid)</option>
                <option value="DIESEL">🛢️ Dầu Diesel</option>
                <option value="GASOLINE">⛽ Xăng</option>
              </select>

              {/* Sort By */}
              <select
                value={sortBy}
                onChange={(e) => {
                  setSortBy(e.target.value)
                  setPage(0)
                }}
                className="px-3 py-2.5 rounded-xl border border-slate-200 text-xs sm:text-sm font-medium text-slate-700 bg-white focus:outline-hidden focus:border-emerald-600"
              >
                <option value="newest">Mới nhất</option>
                <option value="price-asc">Giá: Thấp đến cao</option>
                <option value="price-desc">Giá: Cao đến thấp</option>
                <option value="rating">Đánh giá cao nhất</option>
                <option value="name-asc">Tên xe: A → Z</option>
              </select>

              {/* Grid / Table Switch */}
              <div className="inline-flex rounded-xl border border-slate-200 p-0.5 bg-slate-100">
                <button
                  type="button"
                  title="Chế độ lưới"
                  onClick={() => setViewMode('grid')}
                  className={`p-2 rounded-lg transition-colors ${
                    viewMode === 'grid' ? 'bg-white text-emerald-700 shadow-xs' : 'text-slate-500 hover:text-slate-900'
                  }`}
                >
                  <span className="material-symbols-outlined text-[18px]">grid_view</span>
                </button>
                <button
                  type="button"
                  title="Chế độ bảng"
                  onClick={() => setViewMode('table')}
                  className={`p-2 rounded-lg transition-colors ${
                    viewMode === 'table' ? 'bg-white text-emerald-700 shadow-xs' : 'text-slate-500 hover:text-slate-900'
                  }`}
                >
                  <span className="material-symbols-outlined text-[18px]">table_rows</span>
                </button>
              </div>
            </div>
          </div>

          {/* Active Filter Chips */}
          {(statusFilter || fuelFilter || debouncedKeyword) && (
            <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-100 text-xs">
              <span className="text-slate-400">Đang lọc theo:</span>
              {statusFilter && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 font-medium">
                  Trạng thái: {statusFilter}
                  <button type="button" onClick={() => handleFilterStatus('')} className="hover:text-emerald-900">
                    ✕
                  </button>
                </span>
              )}
              {fuelFilter && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 font-medium">
                  Nhiên liệu: {fuelFilter}
                  <button type="button" onClick={() => setFuelFilter('')} className="hover:text-blue-900">
                    ✕
                  </button>
                </span>
              )}
              {debouncedKeyword && (
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-100 text-slate-700 font-medium">
                  Từ khóa: &quot;{debouncedKeyword}&quot;
                  <button type="button" onClick={() => setKeyword('')} className="hover:text-slate-900">
                    ✕
                  </button>
                </span>
              )}
              <button
                type="button"
                onClick={handleClearFilters}
                className="text-xs text-rose-600 hover:underline font-semibold ml-auto"
              >
                Xóa tất cả bộ lọc
              </button>
            </div>
          )}
        </div>

        {/* Loading / Error States */}
        {loading && (
          <div className="py-20 text-center">
            <span className="material-symbols-outlined animate-spin text-4xl text-emerald-600">
              progress_activity
            </span>
            <p className="mt-3 text-sm text-slate-500 font-medium">Đang tải danh sách đội xe...</p>
          </div>
        )}

        {error && !loading && (
          <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-center">
            <span className="material-symbols-outlined text-4xl text-rose-500">error_outline</span>
            <p className="mt-2 text-sm font-semibold text-rose-700">{error}</p>
            <button
              type="button"
              onClick={handleClearFilters}
              className="mt-4 px-4 py-2 rounded-xl bg-white border border-rose-200 text-xs font-bold text-rose-700 hover:bg-rose-100"
            >
              Thử lại
            </button>
          </div>
        )}

        {/* Empty State */}
        {!loading && !error && vehicles.length === 0 && (
          <div className="bg-white p-12 rounded-3xl border border-slate-200/80 text-center shadow-xs">
            <div className="w-16 h-16 rounded-2xl bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-4">
              <span className="material-symbols-outlined text-[36px]">no_crash</span>
            </div>
            <h3 className="text-base font-bold text-slate-900">Không tìm thấy phương tiện nào</h3>
            <p className="text-xs text-slate-500 max-w-md mx-auto mt-1">
              Không có xe nào khớp với điều kiện lọc hiện tại của bạn. Hãy thử thay đổi từ khóa hoặc loại bỏ bộ lọc.
            </p>
            <button
              type="button"
              onClick={handleClearFilters}
              className="mt-5 px-5 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold shadow-xs"
            >
              Đặt lại bộ lọc
            </button>
          </div>
        )}

        {/* Vehicles List (Grid View) */}
        {!loading && !error && vehicles.length > 0 && viewMode === 'grid' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {vehicles.map((vehicle) => {
              const isApproved = vehicle.status === 'APPROVED'
              const isPendingOrRejected = vehicle.status === 'PENDING' || vehicle.status === 'REJECTED'
              const isToggling = togglingIds.has(vehicle.id)

              return (
                <div
                  key={vehicle.id}
                  className="bg-white rounded-3xl border border-slate-200/80 overflow-hidden shadow-[0_2px_12px_rgba(11,28,48,0.04)] hover:shadow-[0_8px_24px_rgba(11,28,48,0.08)] transition-all flex flex-col"
                >
                  {/* Vehicle Image Cover */}
                  <div className="relative aspect-16/10 bg-slate-100 overflow-hidden group">
                    <img
                      src={vehicle.coverImageUrl || '/placeholder-car.jpg'}
                      alt={`${vehicle.brand} ${vehicle.model}`}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      onError={(e) => {
                        e.target.onerror = null
                        e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80'
                      }}
                    />

                    {/* Top Overlay Badges */}
                    <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none">
                      <div className="pointer-events-auto">
                        <span className="inline-flex items-center px-2.5 py-1 rounded-md border border-slate-300 bg-white/95 backdrop-blur-xs font-mono text-xs font-bold text-slate-900 shadow-sm">
                          {vehicle.licensePlate}
                        </span>
                      </div>
                      <div className="pointer-events-auto">
                        {renderStatusBadge(vehicle.status)}
                      </div>
                    </div>

                    {/* Bottom Fuel Chip */}
                    <div className="absolute bottom-3 left-3 pointer-events-none">
                      <div className="pointer-events-auto">
                        {renderFuelBadge(vehicle.fuelType)}
                      </div>
                    </div>
                  </div>

                  {/* Vehicle Details */}
                  <div className="p-5 flex-1 flex flex-col justify-between space-y-4">
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <h3 className="text-lg font-bold text-[#0b1c30] leading-snug">
                            {vehicle.brand} {vehicle.model}
                          </h3>
                          <p className="text-xs text-slate-400 mt-0.5">
                            Năm SX: {vehicle.manufactureYear} • {vehicle.color || 'Tiêu chuẩn'}
                          </p>
                        </div>

                        {vehicle.ratingCount > 0 ? (
                          <div className="flex items-center gap-1 text-xs font-bold text-amber-500 bg-amber-50 px-2 py-0.5 rounded-md">
                            <span>★</span>
                            <span>{Number(vehicle.ratingAvg).toFixed(1)}</span>
                            <span className="text-slate-400 font-normal text-[10px]">({vehicle.ratingCount})</span>
                          </div>
                        ) : (
                          <span className="text-[11px] text-slate-400 bg-slate-100 px-2 py-0.5 rounded-md font-medium">
                            Chưa có đánh giá
                          </span>
                        )}
                      </div>

                      {/* Specs Row */}
                      <div className="flex items-center gap-4 text-xs text-slate-600 mt-3 pt-3 border-t border-slate-100">
                        <span className="inline-flex items-center gap-1">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">airline_seat_recline_normal</span>
                          {vehicle.seatCount} chỗ
                        </span>
                        <span className="inline-flex items-center gap-1">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">settings</span>
                          {TRANSMISSION_LABELS[vehicle.transmission] || vehicle.transmission}
                        </span>
                      </div>

                      {/* Price Row */}
                      <div className="mt-3">
                        {vehicle.dailyPrice ? (
                          <div className="flex items-baseline gap-1">
                            <span className="text-base font-extrabold text-emerald-700">
                              {formatCurrency(vehicle.dailyPrice)}
                            </span>
                            <span className="text-xs text-slate-500 font-medium">/ ngày</span>
                          </div>
                        ) : vehicle.hourlyPrice ? (
                          <div className="flex items-baseline gap-1">
                            <span className="text-base font-extrabold text-emerald-700">
                              {formatCurrency(vehicle.hourlyPrice)}
                            </span>
                            <span className="text-xs text-slate-500 font-medium">/ giờ</span>
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400 italic">Chưa cài đặt giá</span>
                        )}
                      </div>
                    </div>

                    {/* Bottom Controls: Direct Visibility Toggle Switch */}
                    <div className="pt-3 border-t border-slate-100 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          disabled={isPendingOrRejected || isToggling}
                          onClick={() => handleToggleVisibility(vehicle)}
                          title={
                            isPendingOrRejected
                              ? (vehicle.status === 'PENDING' ? 'Xe đang chờ kiểm duyệt' : 'Xe bị từ chối hồ sơ')
                              : (isApproved ? 'Nhấn để tạm ẩn xe' : 'Nhấn để bật nhận khách')
                          }
                          className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden ${
                            isPendingOrRejected
                              ? 'opacity-40 cursor-not-allowed bg-slate-200'
                              : isApproved
                              ? 'bg-emerald-600'
                              : 'bg-slate-300'
                          }`}
                        >
                          <span
                            className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out ${
                              isApproved ? 'translate-x-5' : 'translate-x-0'
                            }`}
                          />
                        </button>
                        <span className="text-xs font-semibold text-slate-700">
                          {isApproved ? 'Nhận khách' : isPendingOrRejected ? 'Chưa khả dụng' : 'Tạm ẩn'}
                        </span>
                      </div>

                      <Link
                        to={`/provider/vehicles/${vehicle.id}/edit`}
                        className="text-xs font-bold text-emerald-700 hover:text-emerald-800 flex items-center gap-1"
                        title="Chỉnh sửa thông tin xe (Màn O03)"
                      >
                        <span className="material-symbols-outlined text-[16px]">edit</span>
                        Sửa thông tin
                      </Link>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Vehicles List (Table View) */}
        {!loading && !error && vehicles.length > 0 && viewMode === 'table' && (
          <div className="bg-white rounded-3xl border border-slate-200/80 overflow-hidden shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-[#f0f4f9] text-xs font-bold uppercase tracking-wider text-slate-600 border-b border-slate-200">
                  <tr>
                    <th className="py-3.5 px-4">Phương tiện</th>
                    <th className="py-3.5 px-4">Thông số</th>
                    <th className="py-3.5 px-4">Nhiên liệu</th>
                    <th className="py-3.5 px-4">Giá thuê</th>
                    <th className="py-3.5 px-4">Trạng thái</th>
                    <th className="py-3.5 px-4">Nhận khách</th>
                    <th className="py-3.5 px-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {vehicles.map((vehicle) => {
                    const isApproved = vehicle.status === 'APPROVED'
                    const isPendingOrRejected = vehicle.status === 'PENDING' || vehicle.status === 'REJECTED'
                    const isToggling = togglingIds.has(vehicle.id)

                    return (
                      <tr key={vehicle.id} className="hover:bg-slate-50/70 transition-colors">
                        <td className="py-3.5 px-4">
                          <div className="flex items-center gap-3">
                            <img
                              src={vehicle.coverImageUrl || '/placeholder-car.jpg'}
                              alt=""
                              className="w-12 h-9 rounded-lg object-cover bg-slate-100 border border-slate-200"
                              onError={(e) => {
                                e.target.onerror = null
                                e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=200&q=80'
                              }}
                            />
                            <div>
                              <p className="font-bold text-[#0b1c30]">{vehicle.brand} {vehicle.model}</p>
                              <span className="inline-block mt-0.5 px-1.5 py-0.5 rounded border border-slate-300 font-mono text-[11px] font-bold text-slate-800">
                                {vehicle.licensePlate}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 px-4 text-xs text-slate-600">
                          {vehicle.manufactureYear} • {vehicle.seatCount} chỗ • {TRANSMISSION_LABELS[vehicle.transmission] || vehicle.transmission}
                        </td>
                        <td className="py-3.5 px-4">
                          {renderFuelBadge(vehicle.fuelType)}
                        </td>
                        <td className="py-3.5 px-4">
                          {vehicle.dailyPrice ? (
                            <span className="font-bold text-emerald-700">{formatCurrency(vehicle.dailyPrice)}/ngày</span>
                          ) : vehicle.hourlyPrice ? (
                            <span className="font-bold text-emerald-700">{formatCurrency(vehicle.hourlyPrice)}/giờ</span>
                          ) : (
                            <span className="text-xs text-slate-400 italic">—</span>
                          )}
                        </td>
                        <td className="py-3.5 px-4">
                          {renderStatusBadge(vehicle.status)}
                        </td>
                        <td className="py-3.5 px-4">
                          <button
                            type="button"
                            disabled={isPendingOrRejected || isToggling}
                            onClick={() => handleToggleVisibility(vehicle)}
                            className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden ${
                              isPendingOrRejected
                                ? 'opacity-40 cursor-not-allowed bg-slate-200'
                                : isApproved
                                ? 'bg-emerald-600'
                                : 'bg-slate-300'
                            }`}
                          >
                            <span
                              className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out ${
                                isApproved ? 'translate-x-5' : 'translate-x-0'
                              }`}
                            />
                          </button>
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <Link
                            to={`/provider/vehicles/${vehicle.id}/edit`}
                            className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 hover:text-emerald-800"
                            title="Chỉnh sửa thông tin xe (Màn O03)"
                          >
                            <span className="material-symbols-outlined text-[16px]">edit</span>
                            Sửa
                          </Link>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Pagination Controls */}
        {!loading && !error && totalPages > 1 && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t border-slate-200">
            <span className="text-xs text-slate-500 font-medium">
              Hiển thị {page * 9 + 1} - {Math.min((page + 1) * 9, totalItems)} trong tổng số {totalItems} xe
            </span>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Trước
              </button>

              {Array.from({ length: totalPages }).map((_, idx) => {
                if (
                  idx === 0 ||
                  idx === totalPages - 1 ||
                  Math.abs(idx - page) <= 1
                ) {
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => setPage(idx)}
                      className={`w-8 h-8 rounded-xl text-xs font-bold transition-colors ${
                        page === idx
                          ? 'bg-emerald-600 text-white'
                          : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      {idx + 1}
                    </button>
                  )
                }
                if (Math.abs(idx - page) === 2) {
                  return (
                    <span key={idx} className="px-1 text-slate-400 text-xs">
                      ...
                    </span>
                  )
                }
                return null
              })}

              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                className="px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Sau
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
