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
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
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

function formatPickupTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${hours}:${mins} ${day}/${month}/${year}`
  } catch {
    return isoString
  }
}

function formatVnd(amount) {
  if (amount == null) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
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
  const [currentPage, setCurrentPage] = useState(Number.isNaN(initialPage) ? 1 : initialPage)
  const [pageSize, setPageSize] = useState(Number.isNaN(initialPageSize) ? 10 : initialPageSize)
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

  // URL sync
  useEffect(() => {
    const params = {}
    if (selectedTab && selectedTab !== 'ALL') params.status = selectedTab
    if (debouncedSearch.trim()) params.q = debouncedSearch.trim()
    if (vehicleFilter && vehicleFilter !== 'ALL') params.vehicle = vehicleFilter
    if (driverFilter && driverFilter !== 'ALL') params.driver = driverFilter
    if (dateFrom) params.dateFrom = dateFrom
    if (dateTo) params.dateTo = dateTo
    if (sortBy && sortBy !== 'NEWEST') params.sort = sortBy
    if (currentPage > 1) params.page = String(currentPage)
    if (pageSize !== 10) params.pageSize = String(pageSize)
    setSearchParams(params, { replace: true })
  }, [selectedTab, debouncedSearch, vehicleFilter, driverFilter, dateFrom, dateTo, sortBy, currentPage, pageSize, setSearchParams])

  const {
    bookings,
    summary,
    loading,
    error,
    refetch,
  } = useProviderBookings(selectedTab)

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.BOOKING_CONFIRMED]: () => {
        refetch()
        setToast({ message: 'Có đơn thuê mới vừa được xác nhận.', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_ACCEPTED]: () => {
        refetch()
        setToast({ message: 'Tài xế đã nhận cuốc xe.', type: 'success' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_REJECTED]: () => {
        refetch()
        setToast({ message: 'Tài xế đã từ chối nhận chuyến. Cần phân công lại!', type: 'warning' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_INCIDENT]: () => {
        refetch()
        setToast({ message: 'Cảnh báo: Có sự cố phát sinh trên một chuyến xe!', type: 'error' })
      },
    },
    onResync: () => refetch(),
  })

  // Extract unique vehicles for filter
  const vehicleOptions = useMemo(() => {
    const set = new Set()
    bookings.forEach((b) => {
      if (b.vehicleName) set.add(b.vehicleName)
    })
    return Array.from(set).sort()
  }, [bookings])

  // Status counts
  const statusCounts = useMemo(() => {
    const counts = { ALL: bookings.length }
    bookings.forEach((b) => {
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
  }, [bookings, summary])

  // Client-side filtering & sorting
  const filteredList = useMemo(() => {
    return bookings.filter((item) => {
      // Search
      if (debouncedSearch.trim()) {
        const q = debouncedSearch.trim().toLowerCase()
        const codeMatch = (item.bookingCode || '').toLowerCase().includes(q)
        const nameMatch = (item.renterName || '').toLowerCase().includes(q)
        const phoneMatch = (item.renterPhone || '').toLowerCase().includes(q)
        const vehicleMatch = (item.vehicleName || '').toLowerCase().includes(q)
        const plateMatch = (item.licensePlate || '').toLowerCase().includes(q)
        if (!codeMatch && !nameMatch && !phoneMatch && !vehicleMatch && !plateMatch) return false
      }

      // Status tab
      if (selectedTab !== 'ALL' && item.status !== selectedTab) return false

      // Driver Assignment filter
      if (driverFilter === 'ASSIGNED') {
        if (!item.driverName && !item.driverId) return false
      } else if (driverFilter === 'UNASSIGNED') {
        if (item.driverName || item.driverId) return false
      } else if (driverFilter === 'PENDING_DRIVER') {
        const hasDriver = Boolean(item.driverId || item.driverName)
        const isConfirmed = Boolean(item.driverConfirmedAt || item.status === 'DRIVER_CONFIRMED' || ['DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(item.status))
        if (!hasDriver || isConfirmed) return false
      }

      // Vehicle
      if (vehicleFilter !== 'ALL' && item.vehicleName !== vehicleFilter) return false

      // Date Range
      if (dateFrom && item.pickupTime) {
        const itemDate = item.pickupTime.slice(0, 10)
        if (itemDate < dateFrom) return false
      }
      if (dateTo && item.pickupTime) {
        const itemDate = item.pickupTime.slice(0, 10)
        if (itemDate > dateTo) return false
      }

      return true
    }).sort((a, b) => {
      if (sortBy === 'OLDEST') return new Date(a.pickupTime || 0) - new Date(b.pickupTime || 0)
      if (sortBy === 'PRICE_HIGH') return (b.totalAmount || 0) - (a.totalAmount || 0)
      if (sortBy === 'PRICE_LOW') return (a.totalAmount || 0) - (b.totalAmount || 0)
      // Default: NEWEST
      return new Date(b.pickupTime || b.createdAt || 0) - new Date(a.pickupTime || a.createdAt || 0)
    })
  }, [bookings, debouncedSearch, selectedTab, driverFilter, vehicleFilter, dateFrom, dateTo, sortBy])

  // Pagination calculation
  const totalItems = filteredList.length
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
  const safePage = Math.min(currentPage, totalPages)
  const paginatedList = useMemo(() => {
    const start = (safePage - 1) * pageSize
    return filteredList.slice(start, start + pageSize)
  }, [filteredList, safePage, pageSize])

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
      await providerBookingService.exportBookings(selectedTab)
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
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
              Quản lý đơn đặt xe
            </h1>
            <p className="mt-0.5 text-xs text-slate-500">
              Điều phối và theo dõi toàn bộ lịch đặt xe, tài xế và trạng thái thanh toán
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleExportCsv}
              disabled={exporting || loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
            >
              <Icon name="download" className={`text-[16px] ${exporting ? 'animate-spin' : ''}`} />
              <span>{exporting ? 'Đang xuất...' : 'Xuất CSV'}</span>
            </button>
            <button
              type="button"
              onClick={() => refetch()}
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
            >
              <Icon name="refresh" className={`text-[16px] ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Global Error Notice */}
        {error && (
          <div className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 p-3.5 text-xs text-red-800">
            <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
            <span className="font-medium">{error}</span>
          </div>
        )}

        {/* Filter and Search Panel */}
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-xs space-y-3">
          {/* Status Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
            {STATUS_TAB_DEFINITIONS.map((tab) => {
              const isActive = selectedTab === tab.value
              const count = statusCounts[tab.value] || 0
              return (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => {
                    setSelectedTab(tab.value)
                    setCurrentPage(1)
                  }}
                  className={`flex shrink-0 items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                    isActive
                      ? 'bg-brand-800 text-white shadow-2xs'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200 hover:text-slate-900'
                  }`}
                >
                  <span>{tab.label}</span>
                  <span
                    className={`rounded-full px-1.5 py-0.2 text-[10px] font-bold ${
                      isActive ? 'bg-white/20 text-white' : 'bg-slate-200 text-slate-600'
                    }`}
                  >
                    {count}
                  </span>
                </button>
              )
            })}
          </div>

          {/* Search and Advanced Filters */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-2.5 border-t border-slate-100 pt-3">
            {/* Search Input */}
            <div className="relative lg:col-span-2">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <Icon name="search" className="text-[18px]" />
              </div>
              <input
                type="text"
                value={searchInput}
                onChange={(e) => handleSearchChange(e.target.value)}
                placeholder="Tìm mã đơn, tên khách, số điện thoại, biển số..."
                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 pl-9 pr-7 text-xs text-slate-800 placeholder-slate-400 focus:border-brand-600 focus:outline-none"
              />
              {searchInput && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchInput('')
                    setDebouncedSearch('')
                    setCurrentPage(1)
                  }}
                  className="absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400 hover:text-slate-600"
                >
                  <Icon name="close" className="text-[16px]" />
                </button>
              )}
            </div>

            {/* Driver Filter */}
            <div className="relative">
              <select
                value={driverFilter}
                onChange={(e) => {
                  setDriverFilter(e.target.value)
                  setCurrentPage(1)
                }}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-brand-600 focus:outline-none"
              >
                {DRIVER_ASSIGNMENT_FILTERS.map((f) => (
                  <option key={f.value} value={f.value}>
                    {f.label}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2 text-slate-400">
                <Icon name="expand_more" className="text-[16px]" />
              </div>
            </div>

            {/* Vehicle Filter */}
            <div className="relative">
              <select
                value={vehicleFilter}
                onChange={(e) => {
                  setVehicleFilter(e.target.value)
                  setCurrentPage(1)
                }}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-medium text-slate-700 hover:border-slate-300 focus:border-brand-600 focus:outline-none"
              >
                <option value="ALL">Tất cả phương tiện</option>
                {vehicleOptions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2 text-slate-400">
                <Icon name="expand_more" className="text-[16px]" />
              </div>
            </div>

            {/* From Date */}
            <div>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value)
                  setCurrentPage(1)
                }}
                title="Lọc từ ngày"
                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 px-2.5 text-xs text-slate-700 hover:border-slate-300 focus:border-brand-600 focus:outline-none"
              />
            </div>

            {/* Sort Dropdown */}
            <div className="relative">
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-semibold text-slate-700 hover:border-slate-300 focus:border-brand-600 focus:outline-none"
              >
                <option value="NEWEST">Mới nhất</option>
                <option value="OLDEST">Cũ nhất</option>
                <option value="PRICE_HIGH">Tổng tiền: Cao → Thấp</option>
                <option value="PRICE_LOW">Tổng tiền: Thấp → Cao</option>
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2 text-slate-400">
                <Icon name="swap_vert" className="text-[16px]" />
              </div>
            </div>
          </div>
        </div>

        {/* Bookings Table */}
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1080px] border-collapse text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3.5">Mã đơn</th>
                  <th className="px-4 py-3.5">Khách thuê</th>
                  <th className="px-4 py-3.5">Xe &amp; Biển số</th>
                  <th className="px-4 py-3.5">Thời gian thuê</th>
                  <th className="px-4 py-3.5">Tiền cọc</th>
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
                        <Icon name="sync" className="animate-spin text-xl text-brand-700" />
                        Đang truy xuất danh sách đơn đặt xe...
                      </div>
                    </td>
                  </tr>
                ) : paginatedList.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-16 text-center text-slate-400">
                      <Icon name="inbox" className="mx-auto mb-2 text-4xl text-slate-300" />
                      <div className="text-sm font-semibold text-slate-600">Không tìm thấy đơn đặt xe nào</div>
                      <p className="mt-1 text-xs text-slate-400">
                        {debouncedSearch
                          ? 'Thử thay đổi từ khóa tìm kiếm hoặc đặt lại các bộ lọc'
                          : 'Hiện chưa có đơn đặt xe trong danh mục này'}
                      </p>
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
                    const isPendingConfirmation = Boolean((item.driverId || item.driverName) && !isDriverConfirmed)

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
                              className="text-slate-400 hover:text-slate-700"
                            >
                              <Icon
                                name={copiedCode === item.bookingCode ? 'check' : 'content_copy'}
                                className={`text-[15px] ${copiedCode === item.bookingCode ? 'text-emerald-600' : ''}`}
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
                              <span className="block truncate font-bold text-slate-900">{item.renterName}</span>
                              <span className="block text-[11px] text-slate-500 font-mono">{item.renterPhone || '—'}</span>
                            </div>
                          </div>
                        </td>

                        {/* 3. Phương tiện */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-semibold text-slate-900">{item.vehicleName}</div>
                          <span className="rounded bg-slate-900 px-1.5 py-0.2 font-mono text-[10px] font-bold text-amber-300 inline-block mt-0.5">
                            {item.licensePlate}
                          </span>
                        </td>

                        {/* 4. Thời gian & Điểm đón */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-semibold text-slate-800">{formatPickupTime(item.pickupTime)}</div>
                          <div className="text-[11px] text-slate-500 truncate max-w-[200px]" title={item.pickupAddress}>
                            {item.pickupAddress}
                          </div>
                        </td>

                        {/* 5. Tiền cọc */}
                        <td className="px-4 py-3.5 align-middle">
                          <div className="font-mono font-semibold text-slate-900">
                            {formatVnd(item.depositAmount || item.deposit)}
                          </div>
                          <span className="text-[10px] text-slate-500">
                            {item.paymentStatus || 'VNPAY'}
                          </span>
                        </td>

                        {/* 6. Trạng thái phân công */}
                        <td className="px-4 py-3.5 align-middle">
                          {isUnassigned ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-[11px] font-bold text-amber-800 border border-amber-200">
                              <Icon name="person_search" className="text-[13px]" />
                              <span>Chưa phân công</span>
                            </span>
                          ) : isPendingConfirmation ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-0.5 text-[11px] font-bold text-blue-800 border border-blue-200">
                              <Icon name="hourglass_top" className="text-[13px]" />
                              <span>Chờ tài xế nhận</span>
                            </span>
                          ) : (
                            <div className="space-y-0.5">
                              <div className="font-bold text-slate-900 flex items-center gap-1">
                                <Icon name="person" className="text-[14px] text-emerald-600" />
                                <span>{item.driverName}</span>
                              </div>
                              <span className="text-[10px] text-emerald-700 font-semibold block">
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
                          <div className="font-mono font-bold text-slate-900">{formatVnd(item.totalAmount)}</div>
                          <span className="text-[10px] text-slate-400">{item.rentalType || 'Theo ngày'}</span>
                        </td>

                        {/* 9. Thao tác */}
                        <td className="px-4 py-3.5 align-middle text-center" onClick={(e) => e.stopPropagation()}>
                          <div className="flex items-center justify-center gap-1">
                            {item.status === 'PENDING' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => setAcceptModalBooking(item)}
                                  title="Duyệt đơn"
                                  className="rounded p-1 text-emerald-700 hover:bg-emerald-50"
                                >
                                  <Icon name="check" className="text-[18px]" />
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setDenyModalBooking(item)}
                                  title="Từ chối"
                                  className="rounded p-1 text-red-600 hover:bg-red-50"
                                >
                                  <Icon name="close" className="text-[18px]" />
                                </button>
                              </>
                            )}

                            {isUnassigned && item.status !== 'PENDING' && item.status !== 'CANCELLED' && item.status !== 'REJECTED' && (
                              <button
                                type="button"
                                onClick={() => setAssignModalBooking(item)}
                                title="Phân công tài xế"
                                className="rounded bg-brand-50 px-2 py-1 text-[11px] font-bold text-brand-800 hover:bg-brand-100 border border-brand-200"
                              >
                                Gán tài xế
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                              title="Xem chi tiết"
                              className="rounded p-1 text-slate-400 hover:text-slate-800 hover:bg-slate-100"
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
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value))
                  setCurrentPage(1)
                }}
                className="rounded border border-slate-200 bg-white py-1 px-2 font-semibold text-slate-800 focus:outline-none"
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
              <span>trên tổng số <strong className="font-bold text-slate-900">{totalItems}</strong> đơn</span>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                disabled={safePage <= 1}
                className="rounded border border-slate-200 bg-white p-1 hover:bg-slate-100 disabled:opacity-40"
                aria-label="Trang trước"
              >
                <Icon name="chevron_left" className="text-[18px]" />
              </button>
              <span className="px-2 font-semibold text-slate-800">
                Trang {safePage} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                disabled={safePage >= totalPages}
                className="rounded border border-slate-200 bg-white p-1 hover:bg-slate-100 disabled:opacity-40"
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
