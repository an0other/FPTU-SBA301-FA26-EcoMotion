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
import { providerFinanceService } from '../services/providerFinanceService'

// Area 2: Exactly 6 main status tabs
const STATUS_TAB_DEFINITIONS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'IN_PROGRESS', label: 'Đang chạy' },
  { value: 'COMPLETED', label: 'Hoàn tất' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

const DRIVER_ASSIGNMENT_FILTERS = [
  { value: 'ALL', label: 'Tất cả tài xế' },
  { value: 'UNASSIGNED', label: 'Chưa phân công' },
  { value: 'PENDING_DRIVER', label: 'Chờ tài xế xác nhận' },
  { value: 'ASSIGNED', label: 'Đã phân công' },
]

const ACTION_WORKFLOW_FILTERS = [
  { value: 'ALL', label: 'Tất cả công việc' },
  { value: 'PAYMENT_CONFIRM', label: 'Khách đã chuyển khoản – cần xác nhận' },
  { value: 'APPROVAL', label: 'Chờ duyệt đơn' },
  { value: 'PAYMENT', label: 'Chờ thanh toán cọc' },
  { value: 'ASSIGN_DRIVER', label: 'Chờ phân công tài xế' },
  { value: 'DRIVER_RESPONSE', label: 'Chờ tài xế phản hồi' },
  { value: 'UPCOMING', label: 'Chuyến sắp diễn ra' },
  { value: 'ACTIVE', label: 'Chuyến đang diễn ra' },
  { value: 'NEEDS_ATTENTION', label: 'Cần xử lý sự cố / đối soát' },
  { value: 'SETTLEMENT', label: 'Chờ quyết toán' },
  { value: 'COMPLETED', label: 'Đã hoàn tất' },
  { value: 'CLOSED', label: 'Đã hủy / từ chối' },
]

const SORT_OPTIONS = [
  { value: 'NEWEST', label: 'Mới cập nhật nhất' },
  { value: 'CREATED_DESC', label: 'Mới tạo nhất' },
  { value: 'OLDEST', label: 'Giờ đón sớm nhất' },
  { value: 'PRICE_HIGH', label: 'Tổng tiền: Cao → Thấp' },
  { value: 'PRICE_LOW', label: 'Tổng tiền: Thấp → Cao' },
]

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

export default function ProviderBookingQueuePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  // Read initial states from URL query
  const initialTab = searchParams.get('status') || 'ALL'
  const initialAction = searchParams.get('action') || 'ALL'
  const initialSearch = searchParams.get('q') || ''
  const initialVehicle = searchParams.get('vehicle') || 'ALL'
  const initialDriverFilter = searchParams.get('driver') || 'ALL'
  const initialSort = searchParams.get('sort') || 'NEWEST'
  const initialPage = parseInt(searchParams.get('page') || '1', 10)
  const initialPageSize = parseInt(searchParams.get('pageSize') || '10', 10)
  const initialDateFrom = searchParams.get('dateFrom') || ''
  const initialDateTo = searchParams.get('dateTo') || ''

  const [selectedTab, setSelectedTab] = useState(initialTab)
  const [actionFilter, setActionFilter] = useState(initialAction)
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

  // UI interaction states
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false)
  const [actionMenuCode, setActionMenuCode] = useState(null)

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

  // Count active advanced filters
  const activeAdvancedFilterCount = useMemo(() => {
    let count = 0
    if (driverFilter !== 'ALL') count++
    if (vehicleFilter !== 'ALL') count++
    if (actionFilter !== 'ALL') count++
    return count
  }, [driverFilter, vehicleFilter, actionFilter])

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
    setShowAdvancedFilters(false)
  }

  const handleResetAdvancedFilters = () => {
    setActionFilter('ALL')
    setVehicleFilter('ALL')
    setDriverFilter('ALL')
    setCurrentPage(1)
  }

  // Click outside listener for 3-dots dropdown menu and advanced filters popover
  useEffect(() => {
    const handleDocumentClick = (event) => {
      if (actionMenuCode && !event.target.closest('[data-action-menu]')) {
        setActionMenuCode(null)
      }
      if (showAdvancedFilters && !event.target.closest('[data-advanced-filter]')) {
        setShowAdvancedFilters(false)
      }
    }
    document.addEventListener('click', handleDocumentClick)
    return () => document.removeEventListener('click', handleDocumentClick)
  }, [actionMenuCode, showAdvancedFilters])

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

  const awaitingPaymentCount = summary?.paymentAwaitingConfirmation || 0
  const [awaitingPaymentCodes, setAwaitingPaymentCodes] = useState(() => new Set())
  useEffect(() => {
    let cancelled = false
    if (!awaitingPaymentCount) {
      queueMicrotask(() => { if (!cancelled) setAwaitingPaymentCodes(new Set()) })
      return () => { cancelled = true }
    }
    providerFinanceService.listProviderPaymentRequests({ status: 'PROOF_SUBMITTED', limit: 100 })
      .then((page) => { if (!cancelled) setAwaitingPaymentCodes(new Set((page?.items || []).map((pr) => pr.bookingCode))) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [awaitingPaymentCount, bookings])

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
      [TV5_REALTIME_EVENTS.PAYMENT_PROOF_SUBMITTED]: () => {
        setToast({ message: 'Khách vừa báo đã chuyển khoản — kiểm tra sao kê và xác nhận.', type: 'info' })
        refetch()
      },
      [TV5_REALTIME_EVENTS.DRIVER_INCIDENT]: () => {
        setToast({ message: 'Cảnh báo: Có sự cố phát sinh trên một chuyến xe!', type: 'error' })
      },
    },
    onResync: () => refetch(),
  })

  // Extract unique vehicles for filter
  const vehicleOptions = useMemo(() => summary?.vehicleOptions || [], [summary?.vehicleOptions])

  // Status counts mapped strictly to the 6 tabs
  const safeBookings = useMemo(() => (Array.isArray(bookings) ? bookings : []), [bookings])
  const statusCounts = useMemo(() => {
    if (summary && summary.total != null) {
      return {
        ALL: summary.total || 0,
        PENDING: (summary.pending || 0) + (summary.awaitingDeposit || 0) + (summary.accepted || 0),
        CONFIRMED: summary.confirmed || 0,
        IN_PROGRESS: summary.inProgress || 0,
        COMPLETED: summary.completed || 0,
        CANCELLED: (summary.cancelled || 0) + (summary.rejected || 0),
      }
    }
    const counts = { ALL: safeBookings.length, PENDING: 0, CONFIRMED: 0, IN_PROGRESS: 0, COMPLETED: 0, CANCELLED: 0 }
    safeBookings.forEach((b) => {
      if (['PENDING', 'ACCEPTED', 'AWAITING_DEPOSIT'].includes(b.status)) counts.PENDING++
      else if (['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED'].includes(b.status)) counts.CONFIRMED++
      else if (b.status === 'IN_PROGRESS') counts.IN_PROGRESS++
      else if (['COMPLETED', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(b.status)) counts.COMPLETED++
      else if (['CANCELLED', 'REJECTED'].includes(b.status)) counts.CANCELLED++
    })
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

        {/* Area 1: Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 sm:gap-4">
          <div className="min-w-0">
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">
              Quản lý đơn đặt xe
            </h1>
            <p className="mt-1 text-xs sm:text-sm text-slate-500">
              Xem và xử lý đơn đặt xe
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
          </div>
        </div>

        {awaitingPaymentCount > 0 && actionFilter !== 'PAYMENT_CONFIRM' && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950">
            <span className="flex items-center gap-2">
              <Icon name="payments" className="text-[20px] text-amber-700" />
              <span><strong>{awaitingPaymentCount} đơn</strong> khách đã báo chuyển khoản — cần bạn đối chiếu sao kê và xác nhận.</span>
            </span>
            <button
              type="button"
              onClick={() => { setSelectedTab('ALL'); setActionFilter('PAYMENT_CONFIRM'); setCurrentPage(1) }}
              className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-amber-700"
            >
              Xem các đơn này
            </button>
          </div>
        )}

        {/* Global Error Notice */}
        {error && (
          <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800">
            <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
            <span className="font-medium">{error}</span>
          </div>
        )}

        {/* Area 2: Status Tabs (Exactly 6 main tabs) */}
        <div className="rounded-xl border border-slate-200 bg-white p-3 sm:p-3.5 shadow-2xs">
          <div className="flex items-center justify-between gap-2.5">
            <div
              role="tablist"
              aria-label="Lọc theo trạng thái đơn thuê"
              className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full scrollbar-none overscroll-x-contain min-w-0 flex-1"
            >
              {STATUS_TAB_DEFINITIONS.map((tab) => {
                const isActive = selectedTab === tab.value
                const count = statusCounts[tab.value] || 0
                return (
                  <button
                    key={tab.value}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    aria-label={tab.value === 'PENDING' ? 'Chờ xử lý (Chờ duyệt)' : tab.label}
                    onClick={() => {
                      setSelectedTab(tab.value)
                      setCurrentPage(1)
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
                )
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
        </div>

        {/* Area 3: Primary & Advanced Filter Controls */}
        <div className="rounded-xl border border-slate-200 bg-white p-3.5 sm:p-4 shadow-2xs space-y-3 relative">
          <div className="flex flex-col lg:flex-row lg:items-end gap-3">
            {/* Search Input */}
            <div className="flex-1 min-w-[220px]">
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
                        setSearchInput('')
                        setDebouncedSearch('')
                        setCurrentPage(1)
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

            {/* Date Range: From Date */}
            <div className="w-full sm:w-auto min-w-[140px]">
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
                    setDateFrom(e.target.value)
                    setCurrentPage(1)
                  }}
                  title="Lọc từ ngày"
                  className="h-full w-full rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3 text-xs sm:text-[13px] text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                />
              </div>
            </div>

            {/* Date Range: To Date */}
            <div className="w-full sm:w-auto min-w-[140px]">
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
                    setDateTo(e.target.value)
                    setCurrentPage(1)
                  }}
                  title="Lọc đến ngày"
                  className="h-full w-full rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3 text-xs sm:text-[13px] text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                />
              </div>
            </div>

            {/* Sort Select */}
            <div className="w-full sm:w-auto min-w-[170px]">
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
                    setSortBy(e.target.value)
                    setCurrentPage(1)
                  }}
                  className="h-full w-full appearance-none rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-3.5 pr-9 text-xs sm:text-[13px] font-semibold text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none transition"
                >
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                  <Icon name="swap_vert" className="text-[18px]" />
                </div>
              </div>
            </div>

            {/* Advanced Filters Button */}
            <div className="relative shrink-0" data-advanced-filter>
              <button
                type="button"
                onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
                className={`inline-flex h-10 sm:h-[42px] items-center justify-center gap-2 rounded-lg sm:rounded-xl border px-3.5 text-xs sm:text-[13px] font-semibold shadow-2xs transition ${
                  showAdvancedFilters || activeAdvancedFilterCount > 0
                    ? 'border-emerald-600 bg-emerald-50/70 text-emerald-800'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                <Icon name="tune" className="text-[18px]" />
                <span>Bộ lọc nâng cao</span>
                {activeAdvancedFilterCount > 0 && (
                  <span className="inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-emerald-700 px-1.5 text-[10px] font-bold text-white">
                    {activeAdvancedFilterCount}
                  </span>
                )}
              </button>

              {/* Advanced Filter Popover Dropdown */}
              {showAdvancedFilters && (
                <div className="absolute right-0 sm:left-auto top-full mt-2 w-[320px] sm:w-[420px] z-30 rounded-xl border border-slate-200 bg-white p-4 shadow-xl space-y-3.5 animate-in fade-in duration-150">
                  <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
                    <div className="flex items-center gap-1.5">
                      <Icon name="tune" className="text-[16px] text-emerald-700" />
                      <span className="text-xs font-bold text-slate-900">Bộ lọc nâng cao</span>
                    </div>
                    {activeAdvancedFilterCount > 0 && (
                      <button
                        type="button"
                        onClick={handleResetAdvancedFilters}
                        className="text-[11px] font-semibold text-rose-600 hover:underline"
                      >
                        Đặt lại nâng cao
                      </button>
                    )}
                  </div>

                  {/* 1. Driver Filter */}
                  <div>
                    <label
                      htmlFor="booking-driver-filter"
                      className="mb-1 block text-xs font-semibold text-slate-600"
                    >
                      Tài xế
                    </label>
                    <div className="relative h-10 w-full">
                      <select
                        id="booking-driver-filter"
                        aria-label="Lọc theo tài xế"
                        value={driverFilter}
                        onChange={(e) => {
                          setDriverFilter(e.target.value)
                          setCurrentPage(1)
                        }}
                        className="h-full w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-8 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:outline-none transition"
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

                  {/* 2. Vehicle Filter */}
                  <div>
                    <label
                      htmlFor="booking-vehicle-filter"
                      className="mb-1 block text-xs font-semibold text-slate-600"
                    >
                      Phương tiện
                    </label>
                    <div className="relative h-10 w-full">
                      <select
                        id="booking-vehicle-filter"
                        aria-label="Lọc theo phương tiện"
                        value={vehicleFilter}
                        onChange={(e) => {
                          setVehicleFilter(e.target.value)
                          setCurrentPage(1)
                        }}
                        className="h-full w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-8 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:outline-none transition"
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

                  {/* 3. Action Filter */}
                  <div>
                    <label
                      htmlFor="booking-action-filter"
                      className="mb-1 block text-xs font-semibold text-slate-600"
                    >
                      Việc cần xử lý
                    </label>
                    <div className="relative h-10 w-full">
                      <select
                        id="booking-action-filter"
                        aria-label="Việc cần xử lý"
                        value={actionFilter}
                        onChange={(e) => {
                          setActionFilter(e.target.value)
                          setCurrentPage(1)
                        }}
                        className="h-full w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3 pr-8 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-emerald-600 focus:outline-none transition"
                      >
                        {ACTION_WORKFLOW_FILTERS.map((a) => (
                          <option key={a.value} value={a.value}>
                            {a.label}
                          </option>
                        ))}
                      </select>
                      <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                        <Icon name="expand_more" className="text-[18px]" />
                      </div>
                    </div>
                  </div>

                  <div className="pt-2 flex justify-end">
                    <button
                      type="button"
                      onClick={() => setShowAdvancedFilters(false)}
                      className="inline-flex h-8 items-center justify-center rounded-lg bg-emerald-700 px-3.5 text-xs font-semibold text-white hover:bg-emerald-800 transition"
                    >
                      Áp dụng
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Active Filter Chips / Mobile Clear */}
          {hasActiveFilters && (
            <div className="flex sm:hidden justify-end pt-1">
              <button
                type="button"
                onClick={handleClearFilters}
                className="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100"
              >
                <Icon name="filter_alt_off" className="text-[16px] text-slate-500" />
                <span>Xóa bộ lọc</span>
              </button>
            </div>
          )}
        </div>

        {/* Area 4: Booking Table (Desktop: Exactly 8 columns) */}
        <div className="hidden sm:block overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] table-fixed border-collapse text-left text-xs">
              <colgroup>
                <col className="w-[24%]" />
                <col className="w-[23%]" />
                <col className="w-[18%]" />
                <col className="w-[15%]" />
                <col className="w-[10%]" />
                <col className="w-[10%]" />
              </colgroup>
              <thead>
                <tr className="border-b border-slate-200 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3 text-left font-semibold">Đơn thuê</th>
                  <th className="px-5 py-3 text-left font-semibold">Xe và lịch thuê</th>
                  <th className="px-5 py-3 text-left font-semibold">Tài xế</th>
                  <th className="px-5 py-3 text-left font-semibold">Trạng thái</th>
                  <th className="px-5 py-3 text-right font-semibold">Tổng tiền</th>
                  <th className="px-5 py-3 text-right font-semibold">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr>
                    <td colSpan={6} className="py-16 text-center text-slate-500">
                      <div className="inline-flex items-center gap-2 text-sm font-medium">
                        <Icon name="sync" className="animate-spin text-xl text-emerald-700" />
                        <span>Đang tải danh sách đơn thuê…</span>
                      </div>
                    </td>
                  </tr>
                ) : paginatedList.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-16 text-center text-slate-400">
                      <Icon name="inbox" className="mx-auto mb-2 text-4xl text-slate-300" />
                      <div className="text-sm font-semibold text-slate-700">Không tìm thấy đơn thuê nào</div>
                      <p className="mt-1 text-xs text-slate-500">
                        {hasActiveFilters
                          ? 'Thử thay đổi từ khóa tìm kiếm hoặc đặt lại các bộ lọc hiện tại'
                          : 'Hiện chưa có đơn thuê trong danh mục này'}
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
                    const hasActiveIncident = item.incidentCount > 0 || item.hasIncident || item.atRisk
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
                    const paymentNote = {
                      RECONCILIATION_REQUIRED: { label: 'Cần đối soát', cls: 'text-amber-800' },
                      FAILED: { label: 'Thanh toán lỗi', cls: 'text-rose-700' },
                      REFUND_PENDING: { label: 'Chờ hoàn tiền', cls: 'text-orange-800' },
                    }[item.paymentStatus]

                    return (
                      <tr
                        key={item.bookingCode}
                        onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                        className="group cursor-pointer transition-colors hover:bg-slate-50"
                      >
                        {/* Đơn thuê: mã, copy, cảnh báo; dòng dưới là khách thuê */}
                        <td className="px-5 py-4 align-top">
                          <div className="flex items-center gap-1.5">
                            <span className="font-mono text-[13px] font-bold text-slate-900">{item.bookingCode}</span>
                            <button
                              type="button"
                              onClick={(e) => handleCopy(e, item.bookingCode)}
                              title="Sao chép mã đơn"
                              aria-label={`Sao chép mã đơn ${item.bookingCode}`}
                              className="inline-flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
                            >
                              <Icon
                                name={copiedCode === item.bookingCode ? 'check' : 'content_copy'}
                                className={`text-[15px] ${copiedCode === item.bookingCode ? 'text-emerald-600' : ''}`}
                              />
                            </button>
                            {hasActiveIncident && (
                              <span title="Đang có cảnh báo sự cố cần xử lý" className="inline-flex text-red-600">
                                <Icon name="warning" className="text-[16px]" />
                              </span>
                            )}
                          </div>
                          <div className="mt-1 truncate text-xs text-slate-600">
                            {item.renterName}
                            {item.renterPhone && <span className="font-mono text-slate-400"> · {item.renterPhone}</span>}
                          </div>
                        </td>

                        {/* Xe và lịch thuê */}
                        <td className="px-5 py-4 align-top">
                          <div className="truncate font-semibold text-slate-900" title={item.vehicleName}>
                            {item.vehicleName}
                          </div>
                          <div className="mt-1 flex items-center gap-2 text-xs text-slate-500">
                            <span className="font-mono text-[11px] font-semibold text-slate-700">{item.licensePlate}</span>
                            <span aria-hidden="true" className="text-slate-300">·</span>
                            <span className="whitespace-nowrap">{formatPickupTime(item.pickupTime)}</span>
                          </div>
                          <div className="text-[11px] text-slate-400">
                            {item.rentalDuration || item.rentalType || 'Theo ngày'}
                          </div>
                        </td>

                        {/* Tài xế */}
                        <td className="px-5 py-4 align-top">
                          {item.driverReplacementRequired ? (
                            <div className="text-xs font-semibold text-rose-700">Cần phân công lại</div>
                          ) : isUnassigned ? (
                            <div className="text-xs text-slate-500">Chưa phân công</div>
                          ) : (
                            <>
                              <div className="truncate font-semibold text-slate-900" title={item.driverName}>
                                {item.driverName}
                              </div>
                              <div className={`mt-1 text-xs ${isPendingConfirmation ? 'text-blue-700' : 'text-emerald-700'}`}>
                                {isPendingConfirmation ? 'Chờ tài xế nhận' : 'Tài xế đã nhận'}
                              </div>
                            </>
                          )}
                        </td>

                        {/* Trạng thái đơn và ghi chú thanh toán */}
                        <td className="px-5 py-4 align-top">
                          <BookingStatusBadge status={item.status} />
                          {awaitingPaymentCodes.has(item.bookingCode) && (
                            <div className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">
                              <Icon name="payments" className="text-[13px]" /> Khách đã chuyển khoản – cần xác nhận
                            </div>
                          )}
                          {paymentNote && (
                            <div className={`mt-1.5 text-[11px] font-semibold ${paymentNote.cls}`}>{paymentNote.label}</div>
                          )}
                        </td>

                        {/* Tổng tiền */}
                        <td className="px-5 py-4 align-top text-right">
                          <div className="font-mono text-sm font-bold text-slate-900 whitespace-nowrap">
                            {formatVnd(item.totalAmount)}
                          </div>
                          {(item.depositAmount || item.deposit) ? (
                            <div className="mt-1 font-mono text-[11px] text-slate-400 whitespace-nowrap">
                              Cọc {formatVnd(item.depositAmount || item.deposit)}
                            </div>
                          ) : null}
                        </td>

                        {/* Thao tác: một nút chính theo trạng thái + menu phụ */}
                        <td className="px-5 py-4 align-top text-right" onClick={(e) => e.stopPropagation()}>
                          <div className="flex items-center justify-end gap-2">
                            {item.status === 'PENDING' ? (
                              <button
                                type="button"
                                onClick={() => setAcceptModalBooking(item)}
                                className="inline-flex h-8 items-center justify-center rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white hover:bg-emerald-800 transition"
                              >
                                Duyệt
                              </button>
                            ) : (item.status === 'CONFIRMED' && (isUnassigned || item.driverReplacementRequired)) ? (
                              <button
                                type="button"
                                onClick={() => setAssignModalBooking(item)}
                                className="inline-flex h-8 items-center justify-center rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white hover:bg-emerald-800 transition"
                              >
                                Phân công
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                                className="inline-flex h-8 items-center justify-center rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                              >
                                Xem
                              </button>
                            )}

                            <div className="relative" data-action-menu>
                              <button
                                type="button"
                                onClick={() => setActionMenuCode(actionMenuCode === item.bookingCode ? null : item.bookingCode)}
                                title="Thao tác khác"
                                aria-label="Thao tác khác"
                                className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition"
                              >
                                <Icon name="more_horiz" className="text-[18px]" />
                              </button>

                              {actionMenuCode === item.bookingCode && (
                                <div className="absolute right-0 top-full z-30 mt-1 w-48 rounded-lg border border-slate-200 bg-white py-1 text-left shadow-md">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setActionMenuCode(null)
                                      navigate(`/provider/bookings/${item.bookingCode}`)
                                    }}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                  >
                                    <Icon name="visibility" className="text-[16px] text-slate-400" />
                                    <span>Xem chi tiết</span>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      setActionMenuCode(null)
                                      handleCopy(e, item.bookingCode)
                                    }}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                  >
                                    <Icon name="content_copy" className="text-[16px] text-slate-400" />
                                    <span>Sao chép mã đơn</span>
                                  </button>

                                  {item.status === 'PENDING' && (
                                    <button
                                      type="button"
                                      onClick={() => {
                                        setActionMenuCode(null)
                                        setDenyModalBooking(item)
                                      }}
                                      className="flex w-full items-center gap-2 px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50"
                                    >
                                      <Icon name="close" className="text-[16px] text-rose-500" />
                                      <span>Từ chối đơn</span>
                                    </button>
                                  )}

                                  {['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED'].includes(item.status) && (
                                    <button
                                      type="button"
                                      onClick={() => {
                                        setActionMenuCode(null)
                                        setAssignModalBooking(item)
                                      }}
                                      className="flex w-full items-center gap-2 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
                                    >
                                      <Icon name="person_sync" className="text-[16px] text-slate-400" />
                                      <span>{item.driverId ? 'Đổi tài xế' : 'Phân công tài xế'}</span>
                                    </button>
                                  )}
                                </div>
                              )}
                            </div>
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
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-slate-50/80 px-4 py-3 text-xs text-slate-600">
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

        {/* Mobile View: Clean Card List */}
        <div className="block sm:hidden space-y-3">
          {loading ? (
            <div className="py-12 text-center text-slate-500 rounded-xl border border-slate-200 bg-white p-4">
              <div className="inline-flex items-center gap-2 text-sm font-semibold">
                <Icon name="sync" className="animate-spin text-xl text-emerald-700" />
                <span>Đang tải danh sách đơn đặt xe...</span>
              </div>
            </div>
          ) : paginatedList.length === 0 ? (
            <div className="py-12 text-center text-slate-400 rounded-xl border border-slate-200 bg-white p-4">
              <Icon name="inbox" className="mx-auto mb-2 text-4xl text-slate-300" />
              <div className="text-sm font-semibold text-slate-700">
                Không tìm thấy đơn đặt xe nào
              </div>
              <p className="mt-1 text-xs text-slate-500">
                {hasActiveFilters
                  ? 'Thử thay đổi từ khóa tìm kiếm hoặc đặt lại bộ lọc'
                  : 'Hiện chưa có đơn đặt xe'}
              </p>
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="mt-3 inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-emerald-700"
                >
                  <Icon name="restart_alt" className="text-[16px]" />
                  <span>Xóa bộ lọc</span>
                </button>
              )}
            </div>
          ) : (
            paginatedList.map((item) => {
              const hasActiveIncident = item.incidentCount > 0 || item.hasIncident || item.atRisk
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

              return (
                <div
                  key={item.bookingCode}
                  onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                  className="rounded-xl border border-slate-200 bg-white p-4 shadow-2xs space-y-3 cursor-pointer active:bg-slate-50 transition"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5">
                      <span className="font-mono text-xs font-bold text-slate-900">
                        {item.bookingCode}
                      </span>
                      <button
                        type="button"
                        onClick={(e) => handleCopy(e, item.bookingCode)}
                        className="inline-flex h-6 w-6 items-center justify-center rounded text-slate-400 hover:bg-slate-100"
                      >
                        <Icon
                          name={copiedCode === item.bookingCode ? 'check' : 'content_copy'}
                          className={`text-[14px] ${copiedCode === item.bookingCode ? 'text-emerald-600' : ''}`}
                        />
                      </button>
                      {hasActiveIncident && (
                        <span className="flex h-4 w-4 items-center justify-center rounded-full bg-red-100 text-red-700">
                          <Icon name="warning" className="text-[11px]" />
                        </span>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <BookingStatusBadge status={item.status} />
                      {awaitingPaymentCodes.has(item.bookingCode) && (
                        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">Cần xác nhận thanh toán</span>
                      )}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs border-y border-slate-100 py-2.5">
                    <div>
                      <span className="text-[11px] text-slate-500 block">Khách thuê</span>
                      <span className="font-semibold text-slate-900 truncate block">{item.renterName}</span>
                      <span className="text-[11px] text-slate-500 font-mono block">{item.renterPhone || '—'}</span>
                    </div>
                    <div>
                      <span className="text-[11px] text-slate-500 block">Phương tiện</span>
                      <span className="font-semibold text-slate-900 truncate block">{item.vehicleName}</span>
                      <span className="font-mono text-[10px] font-bold text-slate-700 bg-slate-100 px-1 py-0.5 rounded border border-slate-200 inline-block mt-0.5">
                        {item.licensePlate}
                      </span>
                    </div>
                    <div>
                      <span className="text-[11px] text-slate-500 block">Giờ đón</span>
                      <span className="font-medium text-slate-800 block">{formatPickupTime(item.pickupTime)}</span>
                    </div>
                    <div>
                      <span className="text-[11px] text-slate-500 block">Tài xế</span>
                      <span className="font-medium text-slate-800 block">
                        {item.driverReplacementRequired ? (
                          <span className="text-rose-600 font-bold">Cần phân công lại</span>
                        ) : isUnassigned ? (
                          <span className="text-slate-500">Chưa phân công</span>
                        ) : isPendingConfirmation ? (
                          <span className="text-blue-700 font-semibold">{item.driverName} (Chờ nhận)</span>
                        ) : (
                          <span className="text-emerald-700 font-semibold">{item.driverName}</span>
                        )}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center justify-between gap-2 pt-0.5">
                    <div>
                      <span className="text-[11px] text-slate-500 block">Tổng thanh toán</span>
                      <span className="font-mono font-bold text-sm text-slate-900">{formatVnd(item.totalAmount)}</span>
                    </div>
                    <div className="flex items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
                      {item.status === 'PENDING' ? (
                        <button
                          type="button"
                          onClick={() => setAcceptModalBooking(item)}
                          className="inline-flex h-8 items-center justify-center gap-1 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition"
                        >
                          <Icon name="check" className="text-[16px]" />
                          <span>Duyệt</span>
                        </button>
                      ) : (item.status === 'CONFIRMED' && (isUnassigned || item.driverReplacementRequired)) ? (
                        <button
                          type="button"
                          onClick={() => setAssignModalBooking(item)}
                          className="inline-flex h-8 items-center justify-center gap-1 rounded-lg bg-emerald-700 px-3 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-800 transition"
                        >
                          <Icon name="person_add" className="text-[16px]" />
                          <span>Phân công</span>
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                          className="inline-flex h-8 items-center justify-center gap-1 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                        >
                          <span>Xem</span>
                          <Icon name="chevron_right" className="text-[16px]" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })
          )}

          {/* Mobile Pagination */}
          <div className="flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-xs text-slate-600">
            <span>
              Tổng: <strong className="font-bold text-slate-900">{totalItems}</strong> đơn
            </span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={safePage <= 1}
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white disabled:opacity-40"
              >
                <Icon name="chevron_left" className="text-[18px]" />
              </button>
              <span className="px-2 font-semibold text-slate-800">
                {safePage}/{totalPages}
              </span>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={safePage >= totalPages}
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 bg-white disabled:opacity-40"
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
