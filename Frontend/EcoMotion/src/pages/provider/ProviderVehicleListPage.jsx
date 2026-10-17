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
          <span className="px-2.5 py-1 rounded-full bg-emerald-100/95 backdrop-blur-xs text-emerald-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse" />
            Đã duyệt
          </span>
        )
      case 'PENDING':
        return (
          <span className="px-2.5 py-1 rounded-full bg-amber-100/95 backdrop-blur-xs text-amber-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-600" />
            Chờ duyệt
          </span>
        )
      case 'HIDDEN':
        return (
          <span className="px-2.5 py-1 rounded-full bg-slate-200/95 backdrop-blur-xs text-slate-700 text-xs font-semibold shadow-xs flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-slate-500" />
            Tạm ẩn
          </span>
        )
      case 'REJECTED':
        return (
          <span className="px-2.5 py-1 rounded-full bg-rose-100/95 backdrop-blur-xs text-rose-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-rose-600" />
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
          <span className="px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            ⚡ EV Thuần điện
          </span>
        )
      case 'HYBRID':
        return (
          <span className="px-2.5 py-1 rounded-full bg-teal-100 text-teal-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            🔋 Xăng điện (Hybrid)
          </span>
        )
      case 'DIESEL':
        return (
          <span className="px-2.5 py-1 rounded-full bg-slate-200 text-slate-800 text-xs font-semibold shadow-xs flex items-center gap-1">
            🛢️ Dầu Diesel
          </span>
        )
      case 'GASOLINE':
        return (
          <span className="px-2.5 py-1 rounded-full bg-amber-100 text-amber-900 text-xs font-semibold shadow-xs flex items-center gap-1">
            ⛽ Xăng
          </span>
        )
      default:
        return null
    }
  }

  function renderFuelLabel(fuelType) {
    switch (fuelType) {
      case 'EV':
        return 'EV Thuần điện'
      case 'HYBRID':
        return 'Lai điện (Hybrid)'
      case 'DIESEL':
        return 'Dầu Diesel'
      case 'GASOLINE':
        return 'Xăng'
      default:
        return fuelType || 'Xăng'
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
      <main className="max-w-7xl mx-auto px-6 lg:px-12 py-8 space-y-6">
        {/* Header Section with Title & Actions */}
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 pb-2">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase text-emerald-700 tracking-wider font-semibold">Cổng vận hành xe</span>
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-emerald-600" />
              <span className="text-xs text-slate-500">{depotAddress}</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-[#0b1c30] tracking-tight">
              Đội xe của tôi
            </h1>
            <p className="text-sm text-slate-500">
              Danh sách xe thuộc quyền sở hữu của bạn · Quản lý trạng thái, hiển thị nhận chuyến và bảo dưỡng
            </p>
          </div>

          {/* Action Button Group */}
          <div className="flex items-center gap-3 shrink-0">
            <button
              type="button"
              onClick={() => setToast({ message: 'Tính năng xuất dữ liệu báo cáo đang được phát triển.', type: 'info' })}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-sm transition-all shadow-xs"
            >
              <span className="material-symbols-outlined text-[20px] text-slate-500">cloud_download</span>
              Xuất dữ liệu
            </button>
            <Link
              to="/provider/vehicles/new"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm shadow-md shadow-emerald-600/25 transition-all group"
              title="Dẫn sang màn hình O03 - Đăng ký thông tin xe 3 tầng"
            >
              <span className="material-symbols-outlined text-[20px] transition-transform group-hover:rotate-90">add</span>
              <span>Đăng ký xe mới</span>
            </Link>
          </div>
        </div>

        {/* Stats & KPI Overview Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3.5">
          {/* Card 1: Tổng phương tiện */}
          <button
            type="button"
            onClick={() => handleFilterStatus('')}
            className={`bg-white p-4 rounded-xl shadow-xs border transition-all flex items-center justify-between text-left ${
              statusFilter === ''
                ? 'border-emerald-600 ring-2 ring-emerald-500/20'
                : 'border-slate-200/70 hover:border-slate-300'
            }`}
          >
            <div>
              <p className="text-xs text-slate-500 font-medium">Tổng phương tiện</p>
              <p className="text-2xl font-bold text-slate-900 mt-0.5">{stats.total}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-700 shrink-0">
              <span className="material-symbols-outlined text-[22px]">directions_car</span>
            </div>
          </button>

          {/* Card 2: Đang hoạt động */}
          <button
            type="button"
            onClick={() => handleFilterStatus('APPROVED')}
            className={`bg-white p-4 rounded-xl shadow-xs border transition-all flex items-center justify-between text-left ${
              statusFilter === 'APPROVED'
                ? 'bg-emerald-50/50 border-emerald-600 ring-2 ring-emerald-500/20'
                : 'border-slate-200/70 hover:border-emerald-300'
            }`}
          >
            <div>
              <p className="text-xs text-emerald-700 font-semibold">Đang hoạt động</p>
              <p className="text-2xl font-bold text-slate-900 mt-0.5">{stats.approved}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[22px]">verified</span>
            </div>
          </button>

          {/* Card 3: Chờ kiểm duyệt */}
          <button
            type="button"
            onClick={() => handleFilterStatus('PENDING')}
            className={`bg-white p-4 rounded-xl shadow-xs border transition-all flex items-center justify-between text-left ${
              statusFilter === 'PENDING'
                ? 'bg-amber-50/50 border-amber-600 ring-2 ring-amber-500/20'
                : 'border-slate-200/70 hover:border-amber-300'
            }`}
          >
            <div>
              <p className="text-xs text-amber-700 font-semibold">Chờ kiểm duyệt</p>
              <p className="text-2xl font-bold text-slate-900 mt-0.5">{stats.pending}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[22px]">pending_actions</span>
            </div>
          </button>

          {/* Card 4: Tạm ẩn nhận khách */}
          <button
            type="button"
            onClick={() => handleFilterStatus('HIDDEN')}
            className={`bg-white p-4 rounded-xl shadow-xs border transition-all flex items-center justify-between text-left ${
              statusFilter === 'HIDDEN'
                ? 'bg-slate-100 border-slate-600 ring-2 ring-slate-400/20'
                : 'border-slate-200/70 hover:border-slate-400'
            }`}
          >
            <div>
              <p className="text-xs text-slate-600 font-semibold">Tạm ẩn nhận khách</p>
              <p className="text-2xl font-bold text-slate-900 mt-0.5">{stats.hidden}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-slate-100 text-slate-600 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[22px]">visibility_off</span>
            </div>
          </button>

          {/* Card 5: Cần bổ sung hồ sơ */}
          <button
            type="button"
            onClick={() => handleFilterStatus('REJECTED')}
            className={`bg-white p-4 rounded-xl shadow-xs border transition-all flex items-center justify-between text-left col-span-2 sm:col-span-1 ${
              statusFilter === 'REJECTED'
                ? 'bg-rose-50/50 border-rose-600 ring-2 ring-rose-500/20'
                : 'border-slate-200/70 hover:border-rose-300'
            }`}
          >
            <div>
              <p className="text-xs text-rose-600 font-semibold">Cần bổ sung hồ sơ</p>
              <p className="text-2xl font-bold text-slate-900 mt-0.5">{stats.rejected}</p>
            </div>
            <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[22px]">error</span>
            </div>
          </button>
        </div>

        {/* Interactive Filters & Toolbelt Panel */}
        <div className="bg-white rounded-xl p-4 shadow-xs border border-slate-200/70 space-y-4">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
            {/* Search Input */}
            <div className="relative flex-1 max-w-xl">
              <span className="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-[20px]">
                search
              </span>
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm kiếm theo biển số (51G-992.14), tên dòng xe, số khung..."
                className="w-full h-11 pl-10 pr-10 bg-slate-100/70 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-600/20 transition-all border border-transparent focus:border-emerald-600/30"
              />
              {keyword && (
                <button
                  type="button"
                  onClick={() => setKeyword('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs"
                >
                  ✕
                </button>
              )}
            </div>

            {/* Filter Selects & View Toggle */}
            <div className="flex flex-wrap items-center gap-3">
              {/* Fuel Dropdown */}
              <div className="relative min-w-[160px]">
                <select
                  aria-label="Lọc theo loại nhiên liệu"
                  value={fuelFilter}
                  onChange={(e) => {
                    setFuelFilter(e.target.value)
                    setPage(0)
                  }}
                  className="w-full h-11 px-3.5 pr-8 bg-slate-100/70 rounded-xl text-xs sm:text-sm font-medium text-slate-800 appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-600/20 border border-transparent"
                >
                  <option value="">Nhiên liệu: Tất cả</option>
                  <option value="EV">⚡ EV (Thuần điện)</option>
                  <option value="GASOLINE">⛽ GASOLINE (Xăng)</option>
                  <option value="DIESEL">🛢️ DIESEL (Dầu)</option>
                  <option value="HYBRID">🔋 HYBRID (Lai điện)</option>
                </select>
                <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none text-[18px]">
                  expand_more
                </span>
              </div>

              {/* Sort Dropdown */}
              <div className="relative min-w-[170px]">
                <select
                  aria-label="Sắp xếp danh sách xe"
                  value={sortBy}
                  onChange={(e) => {
                    setSortBy(e.target.value)
                    setPage(0)
                  }}
                  className="w-full h-11 px-3.5 pr-8 bg-slate-100/70 rounded-xl text-xs sm:text-sm font-medium text-slate-800 appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-600/20 border border-transparent"
                >
                  <option value="newest">Mới cập nhật nhất</option>
                  <option value="license_asc">Biển số (A - Z)</option>
                  <option value="license_desc">Biển số (Z - A)</option>
                  <option value="oldest">Đời xe (Cũ → Mới)</option>
                  <option value="price-desc">Đơn giá thuê cao nhất</option>
                  <option value="price-asc">Đơn giá thuê thấp nhất</option>
                </select>
                <span className="material-symbols-outlined absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none text-[18px]">
                  sort
                </span>
              </div>

              {/* Grid/Table Switcher */}
              <div className="flex items-center bg-slate-100/70 p-1 rounded-xl">
                <button
                  type="button"
                  onClick={() => setViewMode('grid')}
                  title="Chế độ xem Thẻ (Grid)"
                  className={`p-1.5 rounded-lg transition-colors ${
                    viewMode === 'grid'
                      ? 'bg-white text-emerald-700 shadow-xs'
                      : 'text-slate-400 hover:text-slate-700'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">grid_view</span>
                </button>
                <button
                  type="button"
                  onClick={() => setViewMode('table')}
                  title="Chế độ xem Bảng (Table)"
                  className={`p-1.5 rounded-lg transition-colors ${
                    viewMode === 'table'
                      ? 'bg-white text-emerald-700 shadow-xs'
                      : 'text-slate-400 hover:text-slate-700'
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]">table_rows</span>
                </button>
              </div>
            </div>
          </div>

          {/* Quick Status Filter Pills */}
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-100">
            <span className="text-xs text-slate-400 mr-1 font-medium">Bộ lọc nhanh:</span>
            <button
              type="button"
              onClick={() => handleFilterStatus('')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                statusFilter === ''
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Tất cả ({stats.total})
            </button>
            <button
              type="button"
              onClick={() => handleFilterStatus('APPROVED')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                statusFilter === 'APPROVED'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
              }`}
            >
              ● Đã duyệt ({stats.approved})
            </button>
            <button
              type="button"
              onClick={() => handleFilterStatus('PENDING')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                statusFilter === 'PENDING'
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-amber-50 text-amber-700 hover:bg-amber-100'
              }`}
            >
              ▲ Chờ duyệt ({stats.pending})
            </button>
            <button
              type="button"
              onClick={() => handleFilterStatus('HIDDEN')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                statusFilter === 'HIDDEN'
                  ? 'bg-slate-700 text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              ○ Tạm ẩn ({stats.hidden})
            </button>
            <button
              type="button"
              onClick={() => handleFilterStatus('REJECTED')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all ${
                statusFilter === 'REJECTED'
                  ? 'bg-rose-600 text-white shadow-xs'
                  : 'bg-rose-50 text-rose-700 hover:bg-rose-100'
              }`}
            >
              ✕ Bị từ chối ({stats.rejected})
            </button>
          </div>
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
          <div className="bg-white p-12 rounded-2xl border border-slate-200/70 text-center shadow-xs">
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

        {/* Vehicles List (Grid View - Matching Stitch AI 1:1) */}
        {!loading && !error && vehicles.length > 0 && viewMode === 'grid' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6" id="fleet-grid-container">
            {vehicles.map((vehicle) => {
              const isApproved = vehicle.status === 'APPROVED'
              const isPendingOrRejected = vehicle.status === 'PENDING' || vehicle.status === 'REJECTED'
              const isToggling = togglingIds.has(vehicle.id)

              return (
                <div
                  key={vehicle.id}
                  className="group bg-white rounded-2xl shadow-xs hover:shadow-md transition-all duration-300 flex flex-col overflow-hidden border border-slate-200/70"
                >
                  {/* Visual Cover */}
                  <div className="relative h-48 w-full overflow-hidden bg-slate-100">
                    <img
                      src={vehicle.coverImageUrl || '/placeholder-car.jpg'}
                      alt={`${vehicle.brand} ${vehicle.model}`}
                      className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                      onError={(e) => {
                        e.target.onerror = null
                        e.target.src = 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80'
                      }}
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-black/20 pointer-events-none" />

                    {/* Top Badges */}
                    <div className="absolute top-3 left-3 flex items-center gap-1.5">
                      {renderStatusBadge(vehicle.status)}
                    </div>

                    <div className="absolute top-3 right-3 flex items-center gap-1.5">
                      {renderFuelBadge(vehicle.fuelType)}
                    </div>

                    {/* Embossed License Plate (VN Standard) */}
                    <div className="absolute bottom-3 left-3 bg-white px-3 py-1 rounded-md shadow-md flex items-center gap-1.5 font-bold tracking-widest text-slate-900 text-xs">
                      <span className="w-2 h-2 rounded-full bg-rose-500 inline-block" />
                      {vehicle.licensePlate}
                    </div>
                  </div>

                  {/* Vehicle Info Body */}
                  <div className="p-5 flex-1 flex flex-col justify-between space-y-4">
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <h2 className="font-bold text-base text-slate-900 group-hover:text-emerald-700 transition-colors">
                            {vehicle.brand} {vehicle.model}
                          </h2>
                          <p className="text-xs text-slate-500 mt-0.5">
                            {vehicle.seatCount} Chỗ · {renderFuelLabel(vehicle.fuelType)} · {TRANSMISSION_LABELS[vehicle.transmission] || vehicle.transmission}
                          </p>
                        </div>
                        <div className="text-right shrink-0">
                          {vehicle.dailyPrice ? (
                            <>
                              <span className="font-bold text-base text-emerald-700">{formatCurrency(vehicle.dailyPrice)}</span>
                              <span className="block text-[11px] text-slate-400">/ ngày</span>
                            </>
                          ) : vehicle.hourlyPrice ? (
                            <>
                              <span className="font-bold text-base text-emerald-700">{formatCurrency(vehicle.hourlyPrice)}</span>
                              <span className="block text-[11px] text-slate-400">/ giờ</span>
                            </>
                          ) : (
                            <span className="text-xs text-slate-400 italic">Chưa đặt giá</span>
                          )}
                        </div>
                      </div>

                      {/* Specs Grid */}
                      <div className="grid grid-cols-2 gap-2 mt-4 pt-3 bg-slate-100/70 rounded-xl p-3 text-xs text-slate-600 font-medium">
                        <div className="flex items-center gap-2">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">calendar_today</span>
                          <span>Năm SX: <strong className="text-slate-900">{vehicle.manufactureYear || '—'}</strong></span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">airline_seat_recline_normal</span>
                          <span>Số chỗ: <strong className="text-slate-900">{vehicle.seatCount} Chỗ</strong></span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">settings</span>
                          <span>Hộp số: <strong className="text-slate-900">{TRANSMISSION_LABELS[vehicle.transmission] || vehicle.transmission}</strong></span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="material-symbols-outlined text-[16px] text-slate-400">palette</span>
                          <span>Màu: <strong className="text-slate-900">{vehicle.color || '—'}</strong></span>
                        </div>
                      </div>
                    </div>

                    {/* Availability Toggle */}
                    <div className="flex items-center justify-between pt-2">
                      <div className="flex items-center gap-2">
                        <span className={`w-2.5 h-2.5 rounded-full ${isApproved ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                        <span className="text-xs font-semibold text-slate-800">Hiển thị nhận khách</span>
                      </div>
                      {/* Interactive Switch */}
                      <label className={`relative inline-flex items-center ${isPendingOrRejected || isToggling ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}>
                        <input
                          type="checkbox"
                          checked={isApproved}
                          disabled={isPendingOrRejected || isToggling}
                          onChange={() => handleToggleVisibility(vehicle)}
                          className="sr-only peer"
                        />
                        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600" />
                      </label>
                    </div>

                    {/* Bottom Action Buttons Group */}
                    <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-100">
                      <button
                        type="button"
                        onClick={() => setToast({ message: `Hồ sơ xe & đăng kiểm cho xe ${vehicle.licensePlate} (Màn O04) đang được kết nối.`, type: 'info' })}
                        className="flex flex-col items-center justify-center py-2 px-1 rounded-lg bg-slate-100/70 hover:bg-emerald-50 hover:text-emerald-700 text-slate-600 transition-colors"
                        title="Mở O04 - Quản lý giấy tờ, đăng kiểm & bảng giá thuê"
                      >
                        <span className="material-symbols-outlined text-[18px]">description</span>
                        <span className="text-[11px] font-medium mt-0.5">Hồ sơ xe</span>
                      </button>
                      <Link
                        to={`/provider/vehicles/${vehicle.id}/edit`}
                        className="flex flex-col items-center justify-center py-2 px-1 rounded-lg bg-slate-100/70 hover:bg-slate-200 text-slate-600 transition-colors"
                        title="Mở O03 - Chỉnh sửa thông tin phương tiện"
                      >
                        <span className="material-symbols-outlined text-[18px]">edit_square</span>
                        <span className="text-[11px] font-medium mt-0.5">Sửa thông tin</span>
                      </Link>
                      <button
                        type="button"
                        onClick={() => setToast({ message: `Chức năng khóa lịch, bảo dưỡng xe ${vehicle.licensePlate} (Màn O05) đang được kết nối.`, type: 'info' })}
                        className="flex flex-col items-center justify-center py-2 px-1 rounded-lg bg-slate-100/70 hover:bg-amber-50 hover:text-amber-700 text-slate-600 transition-colors"
                        title="Mở O05 - Lập lịch khóa xe, bảo dưỡng định kỳ"
                      >
                        <span className="material-symbols-outlined text-[18px]">event_busy</span>
                        <span className="text-[11px] font-medium mt-0.5">Khóa lịch</span>
                      </button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Vehicles List (Table View) */}
        {!loading && !error && vehicles.length > 0 && viewMode === 'table' && (
          <div className="bg-white rounded-2xl border border-slate-200/70 overflow-hidden shadow-xs">
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
                          <label className={`relative inline-flex items-center ${isPendingOrRejected || isToggling ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}>
                            <input
                              type="checkbox"
                              checked={isApproved}
                              disabled={isPendingOrRejected || isToggling}
                              onChange={() => handleToggleVisibility(vehicle)}
                              className="sr-only peer"
                            />
                            <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600" />
                          </label>
                        </td>
                        <td className="py-3.5 px-4 text-right">
                          <div className="inline-flex items-center gap-2">
                            <Link
                              to={`/provider/vehicles/${vehicle.id}/edit`}
                              className="p-1 rounded-md text-slate-500 hover:text-emerald-700 hover:bg-emerald-50"
                              title="Sửa thông tin"
                            >
                              <span className="material-symbols-outlined text-[18px]">edit</span>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Operational Notice Banner (From Stitch AI) */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-2xl bg-amber-50/80 border border-amber-200/80">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined text-[20px]">info</span>
            </div>
            <p className="text-xs text-amber-900 leading-relaxed">
              Phương tiện ở trạng thái <strong>&quot;Đã duyệt&quot;</strong> mới có thể gạt công tắc nhận khách. Hãy luôn kiểm tra hạn bảo hiểm TNDS &amp; đăng kiểm tại <span className="text-emerald-700 font-medium cursor-pointer hover:underline">Hồ sơ xe (O04)</span> để tránh bị gián đoạn đơn thuê.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setToast({ message: 'Quy chuẩn: Xe phải có đầy đủ Đăng ký xe, Đăng kiểm tối thiểu 30 ngày và Bảo hiểm TNDS bắt buộc.', type: 'info' })}
            className="px-4 py-2 rounded-xl bg-white hover:bg-slate-50 text-slate-800 text-xs font-semibold shrink-0 transition-colors shadow-xs"
          >
            Xem quy chuẩn kiểm duyệt
          </button>
        </div>

        {/* Pagination & Results Footprint (Matching Stitch AI) */}
        {!loading && !error && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t border-slate-100 pb-8">
            <p className="text-xs text-slate-500">
              Hiển thị <strong className="text-slate-900 font-semibold">{totalItems > 0 ? page * 9 + 1 : 0} – {Math.min((page + 1) * 9, totalItems)}</strong> trong tổng số <strong className="text-slate-900 font-semibold">{totalItems}</strong> phương tiện
            </p>

            {totalPages > 1 && (
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="p-2 rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  title="Trang trước"
                >
                  <span className="material-symbols-outlined text-[20px]">chevron_left</span>
                </button>

                {Array.from({ length: totalPages }).map((_, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setPage(idx)}
                    className={`w-9 h-9 rounded-lg text-xs font-semibold transition-colors ${
                      page === idx
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : 'hover:bg-slate-100 text-slate-700'
                    }`}
                  >
                    {idx + 1}
                  </button>
                ))}

                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="p-2 rounded-lg text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  title="Trang kế tiếp"
                >
                  <span className="material-symbols-outlined text-[20px]">chevron_right</span>
                </button>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
