import { useState, useMemo, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ProviderLayout from '../components/provider/ProviderLayout'
import BookingStatusBadge from '../components/provider/BookingStatusBadge'
import Toast from '../components/Toast'
import AcceptBookingModal from '../components/provider/modals/AcceptBookingModal'
import DenyBookingModal from '../components/provider/modals/DenyBookingModal'
import AssignDriverModal from '../components/provider/modals/AssignDriverModal'
import { useProviderBookings } from '../hooks/useProviderBookings'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { providerBookingService } from '../services/providerBookingService'

const STATUS_TAB_DEFINITIONS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'ACCEPTED', label: 'Đã chấp nhận' },
  { value: 'CONFIRMED', label: 'Đã cọc, chờ phân công' },
  { value: 'IN_PROGRESS', label: 'Đang chạy' },
  { value: 'COMPLETED', label: 'Hoàn tất' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

const DRIVER_ASSIGNMENT_FILTERS = [
  { value: 'ALL', label: 'Tất cả tài xế' },
  { value: 'ASSIGNED', label: 'Đã gán tài xế' },
  { value: 'UNASSIGNED', label: 'Chưa gán tài xế' },
  { value: 'PENDING_DRIVER', label: 'Chờ tài xế xác nhận' },
]

const PAYMENT_STATUS_META = {
  VERIFYING: { label: 'Đang xác minh', className: 'bg-amber-50 text-amber-800 border-amber-200' },
  RECONCILIATION_REQUIRED: { label: 'Đã nhận tiền, cần đối soát', className: 'bg-amber-50 text-amber-800 border-amber-200' },
  UNPAID: {
    label: 'Chưa thanh toán',
    className: 'bg-slate-100 text-slate-600 border-slate-200',
  },
  AWAITING_DEPOSIT: {
    label: 'Chờ đặt cọc',
    className: 'bg-amber-50 text-amber-800 border-amber-200',
  },
  DEPOSIT_PAID: {
    label: 'Đã đặt cọc',
    className: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  PARTIALLY_PAID: {
    label: 'Thanh toán một phần',
    className: 'bg-blue-50 text-blue-800 border-blue-200',
  },
  FULLY_PAID: {
    label: 'Đã tất toán',
    className: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  PAID: {
    label: 'Đã thanh toán',
    className: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  },
  REFUND_PENDING: {
    label: 'Chờ hoàn tiền',
    className: 'bg-orange-50 text-orange-800 border-orange-200',
  },
  REFUNDED: {
    label: 'Đã hoàn tiền',
    className: 'bg-purple-50 text-purple-800 border-purple-200',
  },
  FAILED: {
    label: 'Thanh toán lỗi',
    className: 'bg-rose-50 text-rose-800 border-rose-200',
  },
}

function getPaymentStatusMeta(paymentStatus) {
  if (paymentStatus && PAYMENT_STATUS_META[paymentStatus]) {
    return PAYMENT_STATUS_META[paymentStatus]
  }
  return {
    label: paymentStatus || 'Chưa có dữ liệu thanh toán',
    className: 'bg-slate-100 text-slate-600 border-slate-200',
  }
}

function formatPickupTime(isoString) {
  if (!isoString) return '—'
  const value = new Date(isoString)
  if (Number.isNaN(value.getTime())) return '—'
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(value)
}

function formatDateVi(yyyyMmDd) {
  if (!yyyyMmDd || !/^\d{4}-\d{2}-\d{2}$/.test(yyyyMmDd)) return ''
  const [year, month, day] = yyyyMmDd.split('-')
  return `${day}/${month}/${year}`
}

function formatVnd(amount) {
  if (amount == null) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function Icon({ name, className = '' }) {
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

export default function ProviderBookingQueuePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  // Read initial states from URL query
  const initialTab = searchParams.get('status') || 'ALL'
  const [actionFilter, setActionFilter] = useState(searchParams.get('action') || 'ALL')
  const initialSearch = searchParams.get('q') || ''
  const initialVehicle = searchParams.get('vehicle') || 'ALL'
  const initialDriverFilter = searchParams.get('driver') || 'ALL'
  const initialSort = searchParams.get('sort') || 'NEWEST'
  const initialPage = parseInt(searchParams.get('page') || '1', 10)
  const initialPageSize = parseInt(searchParams.get('pageSize') || '10', 10)
  const initialDateFrom = searchParams.get('dateFrom') || ''
  const initialDateTo = searchParams.get('dateTo') || ''

  const [selectedTab, setSelectedTab] = useState(initialTab)
  const [searchInput, setSearchInput] = useState(initialSearch)
  const [debouncedSearch, setDebouncedSearch] = useState(initialSearch)
  const [vehicleFilter, setVehicleFilter] = useState(initialVehicle)
  const [driverFilter, setDriverFilter] = useState(initialDriverFilter)
  const [dateFrom, setDateFrom] = useState(initialDateFrom)
  const [dateTo, setDateTo] = useState(initialDateTo)
  const [sortBy, setSortBy] = useState(initialSort)
  const [currentPage, setCurrentPage] = useState(Number.isNaN(initialPage) ? 1 : Math.max(1, initialPage))
  const [pageSize, setPageSize] = useState(Number.isNaN(initialPageSize) ? 10 : Math.max(1, Math.min(100, initialPageSize)))
  const [copiedCode, setCopiedCode] = useState(null)
  const [exporting, setExporting] = useState(false)

  // Action modals state
  const [acceptModalBooking, setAcceptModalBooking] = useState(null)
  const [denyModalBooking, setDenyModalBooking] = useState(null)
  const [assignModalBooking, setAssignModalBooking] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Search debounce
  const debounceTimerRef = useRef(null)
  const handleSearchChange = (value) => {
    setSearchInput(value)
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
    debounceTimerRef.current = setTimeout(() => {
      setDebouncedSearch(value)
      setCurrentPage(1)
    }, 300)
  }

  const hasActiveFilters =
    selectedTab !== 'ALL' ||
    actionFilter !== 'ALL' ||
    Boolean(searchInput.trim()) ||
    Boolean(debouncedSearch.trim()) ||
    vehicleFilter !== 'ALL' ||
    driverFilter !== 'ALL' ||
    Boolean(dateFrom) ||
    Boolean(dateTo) ||
    sortBy !== 'NEWEST'

  const handleClearFilters = () => {
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
    setSelectedTab('ALL')
    setActionFilter('ALL')
    setSearchInput('')
    setDebouncedSearch('')
    setVehicleFilter('ALL')
    setDriverFilter('ALL')
    setDateFrom('')
    setDateTo('')
    setSortBy('NEWEST')
    setCurrentPage(1)
  }

  // URL sync
  useEffect(() => {
    const params = {}
    if (selectedTab && selectedTab !== 'ALL') params.status = selectedTab
    if (actionFilter !== 'ALL') params.action = actionFilter
    if (debouncedSearch.trim()) params.q = debouncedSearch.trim()
    if (vehicleFilter && vehicleFilter !== 'ALL') params.vehicle = vehicleFilter
    if (driverFilter && driverFilter !== 'ALL') params.driver = driverFilter
    if (dateFrom) params.dateFrom = dateFrom
    if (dateTo) params.dateTo = dateTo
    if (sortBy && sortBy !== 'NEWEST') params.sort = sortBy
    if (currentPage > 1) params.page = String(currentPage)
    if (pageSize !== 10) params.pageSize = String(pageSize)
    setSearchParams(params, { replace: true })
  }, [selectedTab, actionFilter, debouncedSearch, vehicleFilter, driverFilter, dateFrom, dateTo, sortBy, currentPage, pageSize, setSearchParams])

  const {
    bookings,
    total,
    summary,
    loading,
    error,
    refetch,
  } = useProviderBookings(selectedTab, {
    action: actionFilter,
    search: debouncedSearch,
    vehicle: vehicleFilter,
    driver: driverFilter,
    dateFrom,
    dateTo,
    sort: sortBy,
    offset: (currentPage - 1) * pageSize,
    limit: pageSize,
  })

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.BOOKING_CONFIRMED]: () => {
        setToast({ message: 'Có đơn thuê mới vừa được xác nhận.', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_CONFIRMED]: () => {
        setToast({ message: 'Tài xế đã nhận cuốc xe.', type: 'success' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_REJECTED]: () => {
        setToast({ message: 'Tài xế đã từ chối nhận chuyến. Cần phân công lại!', type: 'warning' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_INCIDENT]: () => {
        setToast({ message: 'Cảnh báo: Có sự cố phát sinh trên một chuyến xe!', type: 'error' })
      },
    },
    onResync: () => refetch(),
  })

  // Extract unique vehicles for filter
  const vehicleOptions = useMemo(() => summary.vehicleOptions || [], [summary.vehicleOptions])

  // Status counts
  const safeBookings = useMemo(() => (Array.isArray(bookings) ? bookings : []), [bookings])
  const statusCounts = useMemo(() => {
    const counts = { ALL: safeBookings.length }
    safeBookings.forEach((b) => {
      counts[b.status] = (counts[b.status] || 0) + 1
    })
    if (summary) {
      if (summary.pending != null) counts.PENDING = summary.pending
      if (summary.accepted != null) counts.ACCEPTED = summary.accepted
      if (summary.confirmed != null) counts.CONFIRMED = summary.confirmed
      if (summary.inProgress != null) counts.IN_PROGRESS = summary.inProgress
      if (summary.completed != null) counts.COMPLETED = summary.completed
      if (summary.rejected != null) counts.REJECTED = summary.rejected
      if (summary.cancelled != null) counts.CANCELLED = summary.cancelled
      if (summary.total != null) counts.ALL = summary.total
    }
    return counts
  }, [safeBookings, summary])

  // Pagination calculation
  const totalItems = total
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
  const safePage = Math.min(currentPage, totalPages)
  const paginatedList = safeBookings
  useEffect(() => {
    if (loading || currentPage <= totalPages) return
    const timer = setTimeout(() => setCurrentPage(totalPages), 0)
    return () => clearTimeout(timer)
  }, [loading, currentPage, totalPages])

  const handleCopy = (e, code) => {
    e.stopPropagation()
    navigator.clipboard.writeText(code)
    setCopiedCode(code)
    setTimeout(() => setCopiedCode(null), 1500)
    setToast({ message: `Đã sao chép mã đơn ${code}`, type: 'success' })
  }

  const handleExportCsv = async () => {
    try {
      setExporting(true)
      await providerBookingService.exportBookings(selectedTab, {
        action: actionFilter,
        search: debouncedSearch,
        vehicle: vehicleFilter,
        driver: driverFilter,
        dateFrom,
        dateTo,
        sort: sortBy,
      })
      setToast({ message: 'Xuất danh sách đơn thuê thành công!', type: 'success' })
    } catch {
      setToast({ message: 'Xuất dữ liệu thất bại', type: 'error' })
    } finally {
      setExporting(false)
    }
  }

  return (
    <ProviderLayout active="Đơn thuê">
      <div className="mx-auto max-w-7xl space-y-4">
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: '', type: 'success' })}
        />

        {/* Header Strip */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 sm:gap-4">
          <div className="min-w-0">
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
              Quản lý đơn đặt xe
            </h1>
            <p className="mt-1 text-xs sm:text-sm text-slate-500">
              Điều phối và theo dõi toàn bộ lịch đặt xe, tài xế và trạng thái thanh toán
            </p>
          </div>

          <div className="flex items-center gap-2 sm:gap-2.5 shrink-0">
            <button
              type="button"
              onClick={handleExportCsv}
              disabled={exporting || loading}
              className="inline-flex h-10 sm:h-[42px] items-center justify-center gap-2 rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3.5 sm:px-4 text-xs sm:text-[13px] font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 disabled:opacity-50 transition"
            >
              <Icon name="download" className={`text-[18px] ${exporting ? 'animate-spin' : ''}`} />
              <span>{exporting ? 'Đang xuất...' : 'Xuất CSV'}</span>
            </button>
            <button
              type="button"
              onClick={() => refetch()}
              disabled={loading}
              className="inline-flex h-10 sm:h-[42px] items-center justify-center gap-2 rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3.5 sm:px-4 text-xs sm:text-[13px] font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 disabled:opacity-50 transition"
            >
              <Icon name="refresh" className={`text-[18px] ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Global Error Notice */}
        {error && (
          <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800">
            <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
            <span className="font-medium">{error}</span>
          </div>
        )}

        {/* Filter and Search Panel */}
        <div className="rounded-xl border border-slate-200 bg-white p-3.5 sm:p-4 shadow-2xs space-y-3.5">
          {/* Status Tabs Row */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
            <div
              role="tablist"
              aria-label="Lọc theo trạng thái đơn thuê"
              className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full scrollbar-none overscroll-x-contain min-w-0 flex-1"
            >
              {STATUS_TAB_DEFINITIONS.map((tab) => {
                const isActive = selectedTab === tab.value;
                const count = statusCounts[tab.value] || 0;
                return (
                  <button
                    key={tab.value}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    onClick={() => {
                      setSelectedTab(tab.value);
                      setCurrentPage(1);
                    }}
                    className={`inline-flex h-9 shrink-0 items-center gap-2 rounded-lg px-3 text-xs font-semibold whitespace-nowrap transition ${
                      isActive
                        ? 'bg-emerald-700 text-white shadow-2xs font-bold'
                        : 'bg-slate-100 text-slate-700 hover:bg-slate-200/80 hover:text-slate-900'
                    }`}
                  >
                    <span>{tab.label}</span>
                    <span
                      className={`inline-flex h-5 min-w-[20px] items-center justify-center rounded-full px-1.5 text-[11px] font-bold leading-none ${
                        isActive
                          ? 'bg-white/20 text-white'
                          : 'bg-white text-slate-700 border border-slate-200/80'
                      }`}
                    >
                      {count}
                    </span>
                  </button>
                );
              })}
            </div>

            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="hidden sm:inline-flex h-9 shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition"
              >
                <Icon name="filter_alt_off" className="text-[16px] text-slate-500" />
                <span>Xóa bộ lọc</span>
              </button>
            )}
          </div>

          <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
            <label htmlFor="booking-action-filter" className="font-semibold text-slate-600">Việc cần xử lý</label>
            <select id="booking-action-filter" value={actionFilter} onChange={e => { setActionFilter(e.target.value); setCurrentPage(1) }} className="h-10 max-w-full rounded-lg border border-slate-200 bg-white px-3 text-slate-700">
              <option value="ALL">Tất cả công việc</option><option value="APPROVAL">Chờ duyệt</option><option value="PAYMENT">Chờ thanh toán</option><option value="ASSIGN_DRIVER">Chờ phân công tài xế</option><option value="DRIVER_RESPONSE">Chờ tài xế phản hồi</option><option value="UPCOMING">Sắp diễn ra</option><option value="ACTIVE">Đang diễn ra</option><option value="NEEDS_ATTENTION">Cần xử lý sự cố / đối soát</option><option value="SETTLEMENT">Chờ quyết toán</option><option value="COMPLETED">Đã hoàn tất</option><option value="CLOSED">Đã hủy / từ chối</option>
            </select>
          </div>

          {/* Search and Advanced Filters Grid: Responsive layout with intentional breakpoints */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[1.5fr_1fr_1fr_1fr_1fr_1.1fr] gap-3 items-end border-t border-slate-100 pt-3.5">
            {/* 1. Search Input: full row on tablet portrait, standard on others */}
            <div className="col-span-1 sm:col-span-2 lg:col-span-1 min-w-0">
              <label
                htmlFor="booking-search-input"
                className="mb-1.5 block text-xs font-semibold text-slate-600"
              >
                Tìm kiếm
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                  <Icon name="search" className="text-[18px]" />
                </div>
                <input
                  id="booking-search-input"
                  type="text"
                  value={searchInput}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  placeholder="Tìm mã đơn, khách, SĐT, biển số..."
                  className="h-full w-full rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-10 pr-9 text-xs sm:text-[13px] text-slate-800 placeholder:text-slate-400 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                />
                {searchInput && (
                  <div className="absolute inset-y-0 right-0 flex items-center pr-2">
                    <button
                      type="button"
                      onClick={() => {
                        setSearchInput('');
                        setDebouncedSearch('');
                        setCurrentPage(1);
                      }}
                      aria-label="Xóa từ khóa tìm kiếm"
                      className="flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                    >
                      <Icon name="close" className="text-[16px]" />
                    </button>
                  </div>
                )}
              </div>
            </div>

            {/* 2. Driver Filter */}
            <div className="col-span-1 min-w-0">
              <label
                htmlFor="booking-driver-filter"
                className="mb-1.5 block text-xs font-semibold text-slate-600"
              >
                Tài xế
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <select
                  id="booking-driver-filter"
                  aria-label="Lọc theo tài xế"
                  value={driverFilter}
                  onChange={(e) => {
                    setDriverFilter(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="h-full w-full appearance-none rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-3.5 pr-9 text-xs sm:text-[13px] font-medium text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                >
                  {DRIVER_ASSIGNMENT_FILTERS.map((f) => (
                    <option key={f.value} value={f.value}>
                      {f.label}
                    </option>
                  ))}
                </select>
                <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                  <Icon name="expand_more" className="text-[18px]" />
                </div>
              </div>
            </div>

            {/* 3. Vehicle Filter */}
            <div className="col-span-1 min-w-0">
              <label
                htmlFor="booking-vehicle-filter"
                className="mb-1.5 block text-xs font-semibold text-slate-600"
              >
                Phương tiện
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <select
                  id="booking-vehicle-filter"
                  aria-label="Lọc theo phương tiện"
                  value={vehicleFilter}
                  onChange={(e) => {
                    setVehicleFilter(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="h-full w-full appearance-none rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-3.5 pr-9 text-xs sm:text-[13px] font-medium text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                >
                  <option value="ALL">Tất cả phương tiện</option>
                  {vehicleOptions.map((name) => (
                    <option key={name} value={name}>
                      {name}
                    </option>
                  ))}
                </select>
                <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                  <Icon name="expand_more" className="text-[18px]" />
                </div>
              </div>
            </div>

            {/* 4. From Date */}
            <div className="col-span-1 min-w-0">
              <label
                htmlFor="booking-date-from"
                className="mb-1.5 flex items-center justify-between gap-1 text-xs font-semibold text-slate-600"
              >
                <span>Từ ngày</span>
                {dateFrom && (
                  <span className="font-mono text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-200">
                    {formatDateVi(dateFrom)}
                  </span>
                )}
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <input
                  id="booking-date-from"
                  aria-label="Từ ngày"
                  type="date"
                  lang="vi-VN"
                  value={dateFrom}
                  max={dateTo || undefined}
                  onChange={(e) => {
                    setDateFrom(e.target.value);
                    setCurrentPage(1);
                  }}
                  title="Lọc từ ngày"
                  className="h-full w-full rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3 text-xs sm:text-[13px] text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                />
              </div>
            </div>

            {/* 5. To Date */}
            <div className="col-span-1 min-w-0">
              <label
                htmlFor="booking-date-to"
                className="mb-1.5 flex items-center justify-between gap-1 text-xs font-semibold text-slate-600"
              >
                <span>Đến ngày</span>
                {dateTo && (
                  <span className="font-mono text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-200">
                    {formatDateVi(dateTo)}
                  </span>
                )}
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <input
                  id="booking-date-to"
                  aria-label="Đến ngày"
                  type="date"
                  lang="vi-VN"
                  value={dateTo}
                  min={dateFrom || undefined}
                  onChange={(e) => {
                    setDateTo(e.target.value);
                    setCurrentPage(1);
                  }}
                  title="Lọc đến ngày"
                  className="h-full w-full rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3 text-xs sm:text-[13px] text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                />
              </div>
            </div>

            {/* 6. Sort Dropdown */}
            <div className="col-span-1 sm:col-span-2 lg:col-span-1 min-w-0">
              <label
                htmlFor="booking-sort-select"
                className="mb-1.5 block text-xs font-semibold text-slate-600"
              >
                Sắp xếp
              </label>
              <div className="relative h-10 sm:h-[42px] w-full">
                <select
                  id="booking-sort-select"
                  aria-label="Sắp xếp đơn đặt xe"
                  value={sortBy}
                  onChange={(e) => {
                    setSortBy(e.target.value);
                    setCurrentPage(1);
                  }}
                  className="h-full w-full appearance-none rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-3.5 pr-9 text-xs sm:text-[13px] font-semibold text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                >
                  <option value="NEWEST">Mới cập nhật nhất</option>
                  <option value="CREATED_DESC">Mới tạo nhất</option>
                  <option value="OLDEST">Giờ đón sớm nhất</option>
                  <option value="PRICE_HIGH">Tổng tiền: Cao → Thấp</option>
                  <option value="PRICE_LOW">Tổng tiền: Thấp → Cao</option>
                </select>
                <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                  <Icon name="swap_vert" className="text-[18px]" />
                </div>
              </div>
            </div>
          </div>

          {/* Mobile Clear Filters Button */}
          {hasActiveFilters && (
            <div className="flex sm:hidden justify-end pt-1">
              <button
                type="button"
                onClick={handleClearFilters}
                className="inline-flex h-10 w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100"
              >
                <Icon name="filter_alt_off" className="text-[16px] text-slate-500" />
                <span>Xóa bộ lọc</span>
              </button>
            </div>
          )}
        </div>

        {/* Bookings Table */}
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1080px] border-collapse text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3.5">Mã đơn</th>
                  <th className="px-4 py-3.5">Khách thuê</th>
                  <th className="px-4 py-3.5">Xe &amp; Biển số</th>
                  <th className="px-4 py-3.5">Thời gian thuê</th>
                  <th className="px-4 py-3.5">Tiền cọc &amp; Thanh toán</th>
                  <th className="px-4 py-3.5">Phân công tài xế</th>
                  <th className="px-4 py-3.5">Trạng thái đơn</th>
                  <th className="px-4 py-3.5 text-right">Tổng tiền</th>
                  <th className="px-4 py-3.5 text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr>
                    <td colSpan={9} className="py-16 text-center text-slate-500">
                      <div className="inline-flex items-center gap-2 text-sm font-semibold">
                        <Icon name="sync" className="animate-spin text-xl text-emerald-700" />
                        <span>Đang truy xuất danh sách đơn đặt xe...</span>
                      </div>
                    </td>
                  </tr>
                ) : paginatedList.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-16 text-center text-slate-400">
                      <Icon name="inbox" className="mx-auto mb-2 text-4xl text-slate-300" />
                      <div className="text-sm font-semibold text-slate-700">
                        Không tìm thấy đơn đặt xe nào
                      </div>
                      <p className="mt-1 text-xs text-slate-500">
                        {hasActiveFilters
                          ? 'Thử thay đổi từ khóa tìm kiếm hoặc đặt lại các bộ lọc hiện tại'
                          : 'Hiện chưa có đơn đặt xe trong danh mục này'}
                      </p>
                      {hasActiveFilters && (
                        <button
                          type="button"
                          onClick={handleClearFilters}
                          className="mt-3 inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-emerald-700 hover:bg-emerald-50 transition"
                        >
                          <Icon name="restart_alt" className="text-[16px]" />
                          <span>Xóa bộ lọc</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ) : (
                  paginatedList.map((item) => {
                    const hasActiveIncident = item.incidentCount > 0 || item.hasIncident
                    const isUnassigned = !item.driverName && !item.driverId
                    const isDriverConfirmed = Boolean(
                      item.driverConfirmedAt ||
                        item.status === 'DRIVER_CONFIRMED' ||
                        ['DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(item.status) ||
                        ['CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(item.executionStatus)
                    )
                    const isPendingConfirmation = Boolean(
                      (item.driverId || item.driverName) && !isDriverConfirmed
                    )
                    const paymentMeta = getPaymentStatusMeta(item.paymentStatus, item.status)

                    return (
                      <tr
                        key={item.bookingCode}
                        onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                        className="cursor-pointer transition-colors hover:bg-slate-50/80"
                      >
                        {/* 1. Mã đơn */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="flex items-center gap-1.5">
                            <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs font-bold text-slate-800 border border-slate-200">
                              {item.bookingCode}
                            </span>
                            <button
                              type="button"
                              onClick={(e) => handleCopy(e, item.bookingCode)}
                              title="Sao chép mã"
                              className="inline-flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                            >
                              <Icon
                                name={copiedCode === item.bookingCode ? 'check' : 'content_copy'}
                                className={`text-[15px] ${
                                  copiedCode === item.bookingCode ? 'text-emerald-600' : ''
                                }`}
                              />
                            </button>
                            {hasActiveIncident && (
                              <span
                                title="Đang có cảnh báo sự cố cần xử lý"
                                className="flex h-5 w-5 items-center justify-center rounded-full bg-red-100 text-red-700"
                              >
                                <Icon name="warning" className="text-[13px]" />
                              </span>
                            )}
                          </div>
                        </td>

                        {/* 2. Khách thuê */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="flex items-center gap-2.5">
                            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-bold text-slate-700">
                              {initialsOf(item.renterName)}
                            </div>
                            <div className="min-w-0">
                              <span className="block truncate font-bold text-slate-900">
                                {item.renterName}
                              </span>
                              <span className="block text-[11px] text-slate-500 font-mono">
                                {item.renterPhone || '—'}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* 3. Phương tiện */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-semibold text-slate-900">{item.vehicleName}</div>
                          <span className="rounded bg-slate-900 px-1.5 py-0.5 font-mono text-[10px] font-bold text-amber-300 inline-block mt-0.5">
                            {item.licensePlate}
                          </span>
                        </td>

                        {/* 4. Thời gian & Điểm đón */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-semibold text-slate-800">
                            {formatPickupTime(item.pickupTime)}
                          </div>
                          <div
                            className="text-[11px] text-slate-500 truncate max-w-[200px]"
                            title={item.pickupAddress}
                          >
                            {item.pickupAddress}
                          </div>
                        </td>

                        {/* 5. Tiền cọc & Thanh toán */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-mono font-semibold text-slate-900">
                            {formatVnd(item.depositAmount || item.deposit)}
                          </div>
                          <span
                            className={`mt-1 inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-semibold ${paymentMeta.className}`}
                          >
                            {paymentMeta.label}
                          </span>
                        </td>

                        {/* 6. Trạng thái phân công */}
                        <td className="px-4 py-3.5 align-middle">
                          {isUnassigned ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-[11px] font-bold text-amber-800 border border-amber-200 whitespace-nowrap">
                              <Icon name="person_search" className="text-[13px]" />
                              <span>Chưa phân công</span>
                            </span>
                          ) : isPendingConfirmation ? (
                            <div className="space-y-0.5">
                              {item.driverName && (
                                <div className="font-bold text-slate-900 flex items-center gap-1">
                                  <Icon name="person" className="text-[14px] text-blue-600" />
                                  <span className="truncate max-w-[130px]">{item.driverName}</span>
                                </div>
                              )}
                              <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-800 border border-blue-200 whitespace-nowrap">
                                <Icon name="hourglass_top" className="text-[12px]" />
                                <span>Chờ tài xế nhận</span>
                              </span>
                            </div>
                          ) : (
                            <div className="space-y-0.5">
                              <div className="font-bold text-slate-900 flex items-center gap-1">
                                <Icon name="person" className="text-[14px] text-emerald-600" />
                                <span className="truncate max-w-[130px]">{item.driverName}</span>
                              </div>
                              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700 font-semibold border border-emerald-200 whitespace-nowrap">
                                {isDriverConfirmed ? 'Tài xế đã nhận' : 'Đã phân công'}
                              </span>
                            </div>
                          )}
                        </td>

                        {/* 7. Trạng thái đơn */}
                        <td className="px-4 py-3.5 align-middle">
                          <BookingStatusBadge status={item.status} />
                        </td>

                        {/* 8. Tổng tiền */}
                        <td className="px-4 py-3.5 align-middle text-right">
                          <div className="font-mono font-bold text-slate-900">
                            {formatVnd(item.totalAmount)}
                          </div>
                          <span className="text-[10px] text-slate-400">
                            {item.rentalType || 'Theo ngày'}
                          </span>
                        </td>

                        {/* 9. Thao tác */}
                        <td
                          className="px-4 py-3.5 align-middle text-center"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <div className="flex items-center justify-center gap-1.5">
                            {item.status === 'PENDING' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => setAcceptModalBooking(item)}
                                  title="Duyệt đơn"
                                  className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-emerald-200 bg-emerald-50/70 text-emerald-700 hover:bg-emerald-100 transition"
                                >
                                  <Icon name="check" className="text-[18px]" />
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setDenyModalBooking(item)}
                                  title="Từ chối"
                                  className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-red-200 bg-red-50/70 text-red-600 hover:bg-red-100 transition"
                                >
                                  <Icon name="close" className="text-[18px]" />
                                </button>
                              </>
                            )}

                            {isUnassigned && item.status === 'CONFIRMED' && (
                              <button
                                type="button"
                                onClick={() => setAssignModalBooking(item)}
                                title="Phân công tài xế"
                                className="inline-flex h-8 items-center justify-center rounded-lg bg-emerald-50 px-2.5 text-[11px] font-bold text-emerald-800 hover:bg-emerald-100 border border-emerald-200 whitespace-nowrap transition"
                              >
                                Gán tài xế
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                              title="Xem chi tiết"
                              className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:text-slate-900 hover:bg-slate-100 transition"
                            >
                              <Icon name="chevron_right" className="text-[18px]" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Table Footer with Compact Pagination */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600">
            <div className="flex items-center gap-2">
              <span>Hiển thị</span>
              <select
                aria-label="Số dòng mỗi trang"
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value))
                  setCurrentPage(1)
                }}
                className="h-8 rounded-lg border border-slate-200 bg-white px-2 font-semibold text-slate-800 focus:border-emerald-600 focus:outline-none"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
              <span>
                trên tổng số <strong className="font-bold text-slate-900">{totalItems}</strong> đơn
              </span>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={safePage <= 1}
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 transition"
                aria-label="Trang trước"
              >
                <Icon name="chevron_left" className="text-[18px]" />
              </button>
              <span className="px-2.5 font-semibold text-slate-800">
                Trang {safePage} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={safePage >= totalPages}
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 transition"
                aria-label="Trang sau"
              >
                <Icon name="chevron_right" className="text-[18px]" />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Row Action Modals */}
      <AcceptBookingModal
        booking={acceptModalBooking}
        isOpen={Boolean(acceptModalBooking)}
        onClose={() => setAcceptModalBooking(null)}
        onSuccess={() => {
          refetch()
          setToast({ message: 'Đã duyệt đơn thuê thành công!', type: 'success' })
        }}
      />

      <DenyBookingModal
        booking={denyModalBooking}
        isOpen={Boolean(denyModalBooking)}
        onClose={() => setDenyModalBooking(null)}
        onSuccess={() => {
          refetch()
          setToast({ message: 'Đã từ chối đơn thuê thành công.', type: 'info' })
        }}
      />

      <AssignDriverModal
        onRefresh={refetch}
        booking={assignModalBooking}
        currentDriverId={assignModalBooking?.driverId}
        isOpen={Boolean(assignModalBooking)}
        onClose={() => setAssignModalBooking(null)}
        onSuccess={() => {
          refetch()
          setToast({ message: 'Phân công tài xế thành công!', type: 'success' })
        }}
      />
    </ProviderLayout>
  )
}
