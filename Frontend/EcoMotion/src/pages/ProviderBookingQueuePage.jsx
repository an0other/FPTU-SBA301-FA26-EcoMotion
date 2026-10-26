import { useState, useMemo, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import ProviderLayout from '../components/provider/ProviderLayout'
import BookingStatusBadge from '../components/provider/BookingStatusBadge'
import Toast from '../components/Toast'
import AcceptBookingModal from '../components/provider/modals/AcceptBookingModal'
import DenyBookingModal from '../components/provider/modals/DenyBookingModal'
import AssignDriverModal from '../components/provider/modals/AssignDriverModal'
import { useProviderBookings } from '../hooks/useProviderBookings'
import { providerBookingService } from '../services/providerBookingService'

const STATUS_TAB_DEFINITIONS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'AWAITING_DEPOSIT', label: 'Chờ đặt cọc' },
  { value: 'PENDING', label: 'Chờ duyệt (đơn cũ)' },
  { value: 'ACCEPTED', label: 'Đã duyệt (đơn cũ)' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'DRIVER_ASSIGNED', label: 'Đã phân tài xế' },
  { value: 'DRIVER_CONFIRMED', label: 'Tài xế đã xác nhận' },
  { value: 'DRIVER_ARRIVED', label: 'Tài xế đã đến' },
  { value: 'AWAITING_START_CONFIRMATION', label: 'Chờ xác nhận bắt đầu' },
  { value: 'IN_PROGRESS', label: 'Đang chạy' },
  { value: 'AWAITING_END_CONFIRMATION', label: 'Chờ xác nhận kết thúc' },
  { value: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', label: 'Chờ chốt phụ phí' },
  { value: 'AWAITING_FINAL_PAYMENT', label: 'Chờ quyết toán' },
  { value: 'CANCELLATION_REVIEW', label: 'Đang đối soát huỷ' },
  { value: 'COMPLETED', label: 'Hoàn tất' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

const AVATAR_PALETTES = [
  { bg: 'bg-[#a7f3d0]', text: 'text-[#065f46]' },
  { bg: 'bg-[#bae6fd]', text: 'text-[#0369a1]' },
  { bg: 'bg-[#ddd6fe]', text: 'text-[#5b21b6]' },
  { bg: 'bg-[#fed7aa]', text: 'text-[#9a3412]' },
  { bg: 'bg-[#bbf7d0]', text: 'text-[#166534]' },
  { bg: 'bg-[#fecdd3]', text: 'text-[#9f1239]' },
  { bg: 'bg-[#fef08a]', text: 'text-[#854d0e]' },
]

function getAvatarStyle(name) {
  let hash = 0
  for (const ch of name || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0
  return AVATAR_PALETTES[hash % AVATAR_PALETTES.length]
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

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
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

const STATUS_LEFT_BORDER = {
  PENDING: 'border-l-4 border-l-amber-500',
  AWAITING_DEPOSIT: 'border-l-4 border-l-amber-500',
  ACCEPTED: 'border-l-4 border-l-blue-500',
  CONFIRMED: 'border-l-4 border-l-teal-500',
  DRIVER_ASSIGNED: 'border-l-4 border-l-blue-600',
  DRIVER_CONFIRMED: 'border-l-4 border-l-indigo-600',
  DRIVER_ARRIVED: 'border-l-4 border-l-cyan-600',
  AWAITING_START_CONFIRMATION: 'border-l-4 border-l-cyan-600',
  IN_PROGRESS: 'border-l-4 border-l-[#10b981]',
  AWAITING_END_CONFIRMATION: 'border-l-4 border-l-orange-500',
  AWAITING_EXTRA_CHARGE_CONFIRMATION: 'border-l-4 border-l-orange-500',
  AWAITING_FINAL_PAYMENT: 'border-l-4 border-l-purple-500',
  CANCELLATION_REVIEW: 'border-l-4 border-l-orange-400',
  COMPLETED: 'border-l-4 border-l-slate-400',
  REJECTED: 'border-l-4 border-l-red-400',
  CANCELLED: 'border-l-4 border-l-slate-300',
}

export default function ProviderBookingQueuePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  // Read initial states from URL search params
  const initialTab = searchParams.get('status') || 'ALL'
  const initialSearch = searchParams.get('q') || ''
  const initialType = searchParams.get('type') || 'ALL'
  const initialVehicle = searchParams.get('vehicle') || 'ALL'
  const initialSort = searchParams.get('sort') || 'NEWEST'
  const initialPage = parseInt(searchParams.get('page') || '1', 10)
  const initialPageSize = parseInt(searchParams.get('pageSize') || '10', 10)
  const initialDateFrom = searchParams.get('dateFrom') || ''
  const initialDateTo = searchParams.get('dateTo') || ''

  const [selectedTab, setSelectedTab] = useState(initialTab)
  const [searchInput, setSearchInput] = useState(initialSearch)
  const [debouncedSearch, setDebouncedSearch] = useState(initialSearch)
  const [typeFilter, setTypeFilter] = useState(initialType)
  const [vehicleFilter, setVehicleFilter] = useState(initialVehicle)
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

  // Search debounce 350ms
  const debounceTimerRef = useRef(null)
  const handleSearchChange = (value) => {
    setSearchInput(value)
    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current)
    debounceTimerRef.current = setTimeout(() => {
      setDebouncedSearch(value)
      setCurrentPage(1)
    }, 350)
  }

  // Synchronize state with URL search parameters
  useEffect(() => {
    const params = {}
    if (selectedTab && selectedTab !== 'ALL') params.status = selectedTab
    if (debouncedSearch.trim()) params.q = debouncedSearch.trim()
    if (typeFilter && typeFilter !== 'ALL') params.type = typeFilter
    if (vehicleFilter && vehicleFilter !== 'ALL') params.vehicle = vehicleFilter
    if (dateFrom) params.dateFrom = dateFrom
    if (dateTo) params.dateTo = dateTo
    if (sortBy && sortBy !== 'NEWEST') params.sort = sortBy
    if (currentPage > 1) params.page = String(currentPage)
    if (pageSize !== 10) params.pageSize = String(pageSize)
    setSearchParams(params, { replace: true })
  }, [selectedTab, debouncedSearch, typeFilter, vehicleFilter, dateFrom, dateTo, sortBy, currentPage, pageSize, setSearchParams])

  // Fetch all bookings from backend database
  const { bookings: apiBookings, summary, loading, error, refetch } = useProviderBookings(selectedTab)

  // Calculate live counts per status
  const statusCounts = useMemo(() => {
    if (summary && summary.total > 0) {
      const counts = {
        ALL: summary.total,
        PENDING: summary.pending,
        AWAITING_DEPOSIT: summary.awaitingDeposit,
        ACCEPTED: summary.accepted,
        CONFIRMED: summary.confirmed,
        IN_PROGRESS: summary.inProgress,
        COMPLETED: summary.completed,
        REJECTED: summary.rejected,
        CANCELLED: summary.cancelled,
      }
      if (selectedTab !== 'ALL' && counts[selectedTab] == null) {
        counts[selectedTab] = (apiBookings || []).filter((item) => item.status === selectedTab).length
      }
      return counts
    }
    const list = apiBookings || []
    const counts = { ALL: list.length }
    for (const b of list) {
      counts[b.status] = (counts[b.status] || 0) + 1
      if (['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED'].includes(b.status)) {
        counts.CONFIRMED = (counts.CONFIRMED || 0) + 1
      }
      if (['IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(b.status)) {
        counts.IN_PROGRESS = (counts.IN_PROGRESS || 0) + 1
      }
    }
    return counts
  }, [summary, apiBookings, selectedTab])

  // Extract distinct vehicles for dropdown filter
  const vehicleOptions = useMemo(() => {
    const list = apiBookings || []
    const set = new Set()
    for (const b of list) {
      if (b.vehicleName) set.add(b.vehicleName)
    }
    return Array.from(set).sort()
  }, [apiBookings])

  // Copy booking code with visual feedback
  const handleCopy = (e, code) => {
    e.stopPropagation()
    navigator.clipboard?.writeText(code).catch(() => {})
    setCopiedCode(code)
    setTimeout(() => setCopiedCode(null), 1500)
  }

  // Export to CSV
  const handleExport = async () => {
    try {
      setExporting(true)
      await providerBookingService.exportBookings(selectedTab)
      setToast({ message: 'Xuất dữ liệu đơn thuê thành công!', type: 'success' })
    } catch (err) {
      setToast({ message: err?.message || 'Không thể xuất file. Vui lòng thử lại.', type: 'error' })
    } finally {
      setExporting(false)
    }
  }

  // Filter & sort
  const filteredList = useMemo(() => {
    let list = apiBookings || []

    // 1. Tab status filter
    if (selectedTab !== 'ALL') {
      if (selectedTab === 'CONFIRMED') {
        list = list.filter((item) =>
          ['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED'].includes(item.status)
        )
      } else if (selectedTab === 'IN_PROGRESS') {
        list = list.filter((item) =>
          ['IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(item.status)
        )
      } else {
        list = list.filter((item) => item.status === selectedTab)
      }
    }

    // 2. Debounced search query
    if (debouncedSearch.trim()) {
      const q = debouncedSearch.toLowerCase()
      list = list.filter((item) => {
        const matchesCode = (item.bookingCode || '').toLowerCase().includes(q)
        const matchesRenter = (item.renterName || '').toLowerCase().includes(q)
        const matchesPlate = (item.licensePlate || '').toLowerCase().includes(q)
        const matchesVehicle = (item.vehicleName || '').toLowerCase().includes(q)
        return matchesCode || matchesRenter || matchesPlate || matchesVehicle
      })
    }

    // 3. Rental type filter
    if (typeFilter !== 'ALL') {
      list = list.filter((item) => (item.rentalType || '').includes(typeFilter))
    }

    // 4. Vehicle filter
    if (vehicleFilter !== 'ALL') {
      list = list.filter((item) =>
        (item.vehicleName || '').toLowerCase().includes(vehicleFilter.toLowerCase())
      )
    }

    // 5. Date From
    if (dateFrom) {
      const fromTime = new Date(dateFrom).getTime()
      list = list.filter((item) => {
        if (!item.pickupTime) return true
        return new Date(item.pickupTime).getTime() >= fromTime
      })
    }

    // 6. Date To
    if (dateTo) {
      const toTime = new Date(dateTo).setHours(23, 59, 59, 999)
      list = list.filter((item) => {
        if (!item.pickupTime) return true
        return new Date(item.pickupTime).getTime() <= toTime
      })
    }

    // 7. Sort
    list = [...list].sort((a, b) => {
      if (sortBy === 'NEWEST') {
        const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0
        const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0
        return dateB - dateA
      }
      if (sortBy === 'OLDEST') {
        const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0
        const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0
        return dateA - dateB
      }
      if (sortBy === 'PRICE_HIGH') {
        return (Number(b.totalAmount) || 0) - (Number(a.totalAmount) || 0)
      }
      if (sortBy === 'PRICE_LOW') {
        return (Number(a.totalAmount) || 0) - (Number(b.totalAmount) || 0)
      }
      return 0
    })

    return list
  }, [apiBookings, selectedTab, debouncedSearch, typeFilter, vehicleFilter, dateFrom, dateTo, sortBy])

  // Pagination calculation
  const totalPages = Math.max(1, Math.ceil(filteredList.length / pageSize))
  const paginatedList = useMemo(() => {
    const start = (currentPage - 1) * pageSize
    return filteredList.slice(start, start + pageSize)
  }, [filteredList, currentPage, pageSize])

  // Reset page when filter changes
  const handleTabChange = (tabValue) => {
    setSelectedTab(tabValue)
    setCurrentPage(1)
  }

  // Handle action success
  const handleActionSuccess = (_, message) => {
    refetch()
    setToast({ message, type: 'success' })
  }

  return (
    <ProviderLayout active="Đơn thuê">
      <div className="mx-auto max-w-7xl">
        {/* Toast feedback */}
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: '', type: 'success' })}
        />

        {/* Top Breadcrumb & Heading Bar */}
        <div className="mb-6 flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div className="mb-1 flex items-center gap-1.5 text-xs font-semibold text-slate-500">
              <span className="hover:text-slate-700">Quản lý kinh doanh</span>
              <Icon name="chevron_right" className="text-[14px] text-slate-400" />
              <span className="text-[#064e3b]">Hàng đợi đơn thuê</span>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-black tracking-tight text-slate-900 lg:text-3xl">
                Quản lý đơn thuê
              </h1>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-[#a7f3d0] bg-[#e6f7ec] px-3 py-1 text-xs font-semibold text-[#065f46]">
                <span className="h-2 w-2 animate-pulse rounded-full bg-[#10b981]" />
                Dữ liệu trực tiếp từ máy chủ
              </span>
            </div>
            <p className="mt-1 text-xs font-medium text-slate-500">
              Tiếp nhận, phê duyệt, phân công tài xế và theo dõi các đơn thuê xe theo thời gian thực
            </p>
          </div>

          {/* Top Actions */}
          <div className="flex flex-wrap items-center gap-2.5">
            <button
              type="button"
              onClick={handleExport}
              disabled={exporting}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 shadow-2xs transition hover:bg-slate-50 hover:text-slate-900 disabled:opacity-50"
            >
              <Icon name="download" className={`text-[18px] text-slate-500 ${exporting ? 'animate-bounce' : ''}`} />
              <span>{exporting ? 'Đang xuất...' : 'Xuất file CSV'}</span>
            </button>

            <button
              type="button"
              onClick={() => refetch()}
              title="Làm mới dữ liệu"
              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 shadow-2xs transition hover:bg-slate-50 hover:text-slate-900"
            >
              <Icon name="refresh" className={`text-[18px] text-slate-500 ${loading ? 'animate-spin' : ''}`} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="mb-6 flex items-center justify-between rounded-xl border border-red-200 bg-red-50 p-4 text-xs font-medium text-red-800">
            <div className="flex items-center gap-2">
              <Icon name="error" className="text-[18px] text-red-600" />
              <span>{error}</span>
            </div>
            <button
              type="button"
              onClick={() => refetch()}
              className="rounded-lg bg-red-600 px-3 py-1 font-bold text-white transition hover:bg-red-700"
            >
              Thử lại
            </button>
          </div>
        )}

        {/* 4 KPI Summary Cards (Real Backend API Data) */}
        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* Card 1: Chờ đặt cọc */}
          <div
            onClick={() => handleTabChange('AWAITING_DEPOSIT')}
            className={`cursor-pointer relative rounded-xl border p-4.5 shadow-2xs transition hover:shadow-xs ${
              selectedTab === 'AWAITING_DEPOSIT'
                ? 'border-amber-500 bg-amber-50/50 ring-2 ring-amber-500/20'
                : 'border-amber-200/80 bg-white hover:border-amber-300'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-slate-800">Chờ đặt cọc</span>
              <span className="rounded bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">
                Chưa thanh toán
              </span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-3xl font-black tracking-tight text-slate-900">
                {String(statusCounts.AWAITING_DEPOSIT || 0).padStart(2, '0')}
              </span>
              <span className="text-xs font-medium text-slate-500">đơn đang giữ lịch</span>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-amber-700">
              <Icon name="warning" className="text-[16px] text-amber-600" />
              <span>Chờ khách gửi minh chứng VietQR</span>
            </div>
          </div>

          {/* Card 2: Đã chấp nhận */}
          <div
            onClick={() => handleTabChange('ACCEPTED')}
            className={`cursor-pointer relative rounded-xl border p-4.5 shadow-2xs transition hover:shadow-xs ${
              selectedTab === 'ACCEPTED'
                ? 'border-blue-500 bg-blue-50/50 ring-2 ring-blue-500/20'
                : 'border-blue-200/80 bg-white hover:border-blue-300'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-slate-800">Đã duyệt (đơn cũ)</span>
              <span className="rounded bg-blue-100 px-2 py-0.5 text-[11px] font-bold text-blue-800">
                Tương thích cũ
              </span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-3xl font-black tracking-tight text-slate-900">
                {String(statusCounts.ACCEPTED || 0).padStart(2, '0')}
              </span>
              <span className="text-xs font-medium text-slate-500">chờ thanh toán cọc</span>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-xs font-medium text-blue-700">
              <Icon name="schedule" className="text-[16px] text-blue-600" />
              <span>Khách có hạn đặt cọc giữ xe</span>
            </div>
          </div>

          {/* Card 3: Sắp chạy */}
          <div
            onClick={() => handleTabChange('CONFIRMED')}
            className={`cursor-pointer relative rounded-xl border p-4.5 shadow-2xs transition hover:shadow-xs ${
              selectedTab === 'CONFIRMED'
                ? 'border-teal-500 bg-teal-50/50 ring-2 ring-teal-500/20'
                : 'border-teal-200/80 bg-white hover:border-teal-300'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-slate-800">Sắp chạy</span>
              <span className="rounded bg-teal-100 px-2 py-0.5 text-[11px] font-bold text-teal-800">
                Đã đặt cọc
              </span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-3xl font-black tracking-tight text-slate-900">
                {String(statusCounts.CONFIRMED || 0).padStart(2, '0')}
              </span>
              <span className="text-xs font-medium text-slate-500">đã giữ chỗ</span>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-xs font-medium text-teal-700">
              <Icon name="verified" className="text-[16px] text-teal-600" />
              <span>Đã cọc &amp; chuẩn bị nhận xe</span>
            </div>
          </div>

          {/* Card 4: Đang chạy */}
          <div
            onClick={() => handleTabChange('IN_PROGRESS')}
            className={`cursor-pointer relative rounded-xl border p-4.5 shadow-2xs transition hover:shadow-xs ${
              selectedTab === 'IN_PROGRESS'
                ? 'border-emerald-500 bg-emerald-50/50 ring-2 ring-emerald-500/20'
                : 'border-emerald-200/80 bg-white hover:border-emerald-300'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-slate-800">Đang chạy</span>
              <span className="rounded bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-800">
                Đang thực hiện
              </span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-3xl font-black tracking-tight text-slate-900">
                {String(statusCounts.IN_PROGRESS || 0).padStart(2, '0')}
              </span>
              <span className="text-xs font-medium text-slate-500">xe trên tuyến</span>
            </div>
            <div className="mt-3 flex items-center gap-1.5 text-xs font-medium text-emerald-700">
              <Icon name="navigation" className="text-[16px] text-emerald-600" />
              <span>Đang lưu thông trên đường</span>
            </div>
          </div>
        </div>

        {/* Filter Controls Bar */}
        <div className="mb-5 rounded-2xl border border-[#dbece0] bg-white p-4 shadow-2xs space-y-3.5">
          {/* Row 1: Status Filter Tabs */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 text-slate-600">
            {STATUS_TAB_DEFINITIONS.map((tab) => {
              const isActive = selectedTab === tab.value
              const count = statusCounts[tab.value] || 0
              return (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => handleTabChange(tab.value)}
                  className={`flex shrink-0 items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
                    isActive
                      ? 'bg-[#064e3b] text-white shadow-2xs'
                      : 'bg-[#f4fbf7] text-slate-600 hover:bg-[#e6f5eb] hover:text-[#064e3b]'
                  }`}
                >
                  <span>{tab.label}</span>
                  <span
                    className={`rounded-full px-1.5 py-0.2 text-[10px] font-bold ${
                      isActive ? 'bg-white/20 text-white' : 'bg-slate-200/70 text-slate-600'
                    }`}
                  >
                    {count}
                  </span>
                </button>
              )
            })}
          </div>

          {/* Row 2: Search and Advanced Filters */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-7 gap-2.5 border-t border-slate-100 pt-3">
            {/* Search Input */}
            <div className="relative sm:col-span-2">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <Icon name="search" className="text-[18px]" />
              </div>
              <input
                type="text"
                value={searchInput}
                onChange={(e) => handleSearchChange(e.target.value)}
                placeholder="Tìm mã đơn, tên khách, biển số, xe..."
                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 pl-9 pr-8 text-xs text-slate-800 placeholder-slate-400 shadow-2xs transition focus:border-[#10b981] focus:outline-none"
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

            {/* Dòng xe */}
            <div className="relative">
              <select
                value={vehicleFilter}
                onChange={(e) => {
                  setVehicleFilter(e.target.value)
                  setCurrentPage(1)
                }}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-medium text-slate-700 shadow-2xs transition hover:border-slate-300 focus:border-[#10b981] focus:outline-none"
              >
                <option value="ALL">Tất cả xe</option>
                {vehicleOptions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                <Icon name="expand_more" className="text-[16px]" />
              </div>
            </div>

            {/* Hình thức thuê */}
            <div className="relative">
              <select
                value={typeFilter}
                onChange={(e) => {
                  setTypeFilter(e.target.value)
                  setCurrentPage(1)
                }}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-medium text-slate-700 shadow-2xs transition hover:border-slate-300 focus:border-[#10b981] focus:outline-none"
              >
                <option value="ALL">Tất cả hình thức</option>
                <option value="giờ">Theo giờ</option>
                <option value="ngày">Theo ngày</option>
                <option value="tháng">Theo tháng</option>
                <option value="năm">Theo năm</option>
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                <Icon name="expand_more" className="text-[16px]" />
              </div>
            </div>

            {/* Từ ngày */}
            <div className="relative">
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value)
                  setCurrentPage(1)
                }}
                title="Đón xe từ ngày"
                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-xs font-medium text-slate-700 shadow-2xs transition hover:border-slate-300 focus:border-[#10b981] focus:outline-none"
              />
            </div>

            {/* Đến ngày */}
            <div className="relative">
              <input
                type="date"
                value={dateTo}
                onChange={(e) => {
                  setDateTo(e.target.value)
                  setCurrentPage(1)
                }}
                title="Đón xe đến ngày"
                className="w-full rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-xs font-medium text-slate-700 shadow-2xs transition hover:border-slate-300 focus:border-[#10b981] focus:outline-none"
              />
            </div>

            {/* Sắp xếp */}
            <div className="relative">
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-semibold text-slate-700 shadow-2xs transition hover:border-slate-300 focus:border-[#10b981] focus:outline-none"
              >
                <option value="NEWEST">Mới nhất</option>
                <option value="OLDEST">Cũ nhất</option>
                <option value="PRICE_HIGH">Giá: Cao → Thấp</option>
                <option value="PRICE_LOW">Giá: Thấp → Cao</option>
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                <Icon name="swap_vert" className="text-[16px]" />
              </div>
            </div>
          </div>
        </div>

        {/* Bookings Table Card */}
        <div className="overflow-hidden rounded-2xl border border-[#dbece0] bg-white shadow-2xs">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1020px] border-collapse text-left text-xs">
              <thead>
                <tr className="border-b border-[#e2efe6] bg-[#f4fbf7] text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3.5">Mã đơn</th>
                  <th className="px-4 py-3.5">Khách thuê</th>
                  <th className="px-4 py-3.5">Xe &amp; Biển số</th>
                  <th className="px-4 py-3.5">Trạng thái</th>
                  <th className="px-4 py-3.5">Hình thức</th>
                  <th className="px-4 py-3.5">Lịch &amp; Điểm đón</th>
                  <th className="px-4 py-3.5">Tài xế</th>
                  <th className="px-4 py-3.5 text-right">Tổng tiền</th>
                  <th className="px-4 py-3.5 text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr>
                    <td colSpan={9} className="py-16 text-center text-slate-500">
                      <div className="inline-flex items-center gap-2 text-sm font-semibold">
                        <Icon name="hourglass_empty" className="animate-spin text-[20px] text-[#064e3b]" />
                        Đang tải danh sách đơn thuê từ cơ sở dữ liệu...
                      </div>
                    </td>
                  </tr>
                ) : paginatedList.length === 0 ? (
                  <tr>
                    <td colSpan={9} className="py-16 text-center text-slate-400">
                      <Icon name="inbox" className="mx-auto mb-2 text-4xl text-slate-300" />
                      <div className="text-sm font-semibold text-slate-600">Không tìm thấy đơn thuê nào</div>
                      <p className="mt-1 text-xs text-slate-400">
                        {debouncedSearch
                          ? 'Thử tìm kiếm với từ khóa khác hoặc điều chỉnh bộ lọc'
                          : 'Chưa có đơn thuê nào trong trạng thái này'}
                      </p>
                    </td>
                  </tr>
                ) : (
                  paginatedList.map((item) => {
                    const avatar = getAvatarStyle(item.renterName)
                    const borderAccent = STATUS_LEFT_BORDER[item.status] || ''

                    return (
                      <tr
                        key={item.bookingCode}
                        onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                        className={`cursor-pointer transition-colors hover:bg-[#f6fcf8] ${borderAccent}`}
                      >
                        {/* Cột 1: Mã đơn */}
                        <td className="px-4 py-4 align-middle">
                          <div className="flex items-center gap-1.5">
                            <span className="rounded bg-[#eef8f2] px-2 py-0.5 font-mono text-xs font-bold text-slate-800">
                              {item.bookingCode}
                            </span>
                            <button
                              type="button"
                              onClick={(e) => handleCopy(e, item.bookingCode)}
                              title="Sao chép mã"
                              className="text-slate-400 transition hover:text-slate-700"
                            >
                              <Icon
                                name={copiedCode === item.bookingCode ? 'check' : 'content_copy'}
                                className={`text-[15px] ${copiedCode === item.bookingCode ? 'text-emerald-600' : ''}`}
                              />
                            </button>
                          </div>
                        </td>

                        {/* Cột 2: Khách thuê */}
                        <td className="px-4 py-4 align-middle">
                          <div className="flex items-center gap-2.5">
                            <div
                              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold shadow-2xs ${avatar.bg} ${avatar.text}`}
                            >
                              {initialsOf(item.renterName)}
                            </div>
                            <div>
                              <div className="font-bold text-slate-900 leading-snug">{item.renterName}</div>
                              <div className="flex items-center gap-1 text-[11px] text-slate-500">
                                <Icon name="group" className="text-[13px] text-slate-400" />
                                <span>{item.passengerCount ? `${item.passengerCount} người` : '1 người'}</span>
                              </div>
                            </div>
                          </div>
                        </td>

                        {/* Cột 3: Xe & Biển số */}
                        <td className="px-4 py-4 align-middle">
                          <div className="flex items-center gap-2.5">
                            {item.vehicleImageUrl ? (
                              <img
                                src={item.vehicleImageUrl}
                                alt={item.vehicleName}
                                className="h-8 w-12 shrink-0 rounded object-cover shadow-2xs"
                              />
                            ) : (
                              <div className="flex h-8 w-12 shrink-0 items-center justify-center rounded bg-[#eef8f2] text-[#064e3b]">
                                <Icon name="directions_car" className="text-[16px]" />
                              </div>
                            )}
                            <div>
                              <div className="font-bold text-slate-900 leading-snug">{item.vehicleName}</div>
                              {item.atRisk && <div className="mt-1 text-[11px] font-bold text-amber-700">⚠ Xe chưa về, đơn kế tiếp có nguy cơ trễ</div>}
                              <span className="inline-block rounded bg-slate-100 px-1 py-0.2 font-mono text-[10px] font-semibold text-slate-600">
                                {item.licensePlate}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* Cột 4: Trạng thái Badge */}
                        <td className="px-4 py-4 align-middle">
                          <BookingStatusBadge status={item.status} />
                        </td>

                        {/* Cột 5: Hình thức */}
                        <td className="px-4 py-4 align-middle">
                          <div className="inline-flex flex-col rounded-md bg-[#eef8f2] px-2 py-0.5 text-center">
                            <span className="text-[10px] font-semibold text-[#065f46]">
                              {item.rentalType || 'Theo ngày'}
                            </span>
                            <span className="text-xs font-bold text-[#064e3b]">
                              {item.rentalDuration || '1 ngày'}
                            </span>
                          </div>
                        </td>

                        {/* Cột 6: Lịch & Điểm đón */}
                        <td className="px-4 py-4 align-middle">
                          <div className="font-bold text-slate-800">
                            {formatPickupTime(item.pickupTime)}
                          </div>
                          <div className="mt-0.5 flex items-center gap-1 text-[11px] text-slate-500">
                            <Icon name="location_on" className="text-[13px] text-slate-400 shrink-0" />
                            <span className="truncate max-w-[180px]" title={item.pickupAddress}>
                              {item.pickupAddress}
                            </span>
                          </div>
                        </td>

                        {/* Cột 7: Tài xế */}
                        <td className="px-4 py-4 align-middle">
                          {item.driverName ? (
                            <div className="flex items-center gap-1.5">
                              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#a7f3d0] text-[10px] font-bold text-[#064e3b]">
                                {initialsOf(item.driverName)}
                              </span>
                              <span className="font-semibold text-slate-800">
                                {item.driverName}
                              </span>
                            </div>
                          ) : item.status === 'PENDING' || item.status === 'ACCEPTED' ? (
                            <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                              <Icon name="warning" className="text-[12px] text-amber-600" />
                              Chưa phân công
                            </span>
                          ) : (
                            <span className="text-slate-400 font-bold">—</span>
                          )}
                        </td>

                        {/* Cột 8: Tổng tiền */}
                        <td className="px-4 py-4 align-middle text-right font-mono text-xs font-bold text-slate-900">
                          {formatVnd(item.totalAmount)}
                        </td>

                        {/* Cột 9: Thao tác */}
                        <td className="px-4 py-4 align-middle text-center" onClick={(e) => e.stopPropagation()}>
                          <div className="flex items-center justify-center gap-1">
                            {item.status === 'PENDING' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => setAcceptModalBooking(item)}
                                  title="Chấp nhận đơn"
                                  className="rounded p-1 text-emerald-600 hover:bg-emerald-50 transition"
                                >
                                  <Icon name="check" className="text-[18px]" />
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setDenyModalBooking(item)}
                                  title="Từ chối đơn"
                                  className="rounded p-1 text-red-600 hover:bg-red-50 transition"
                                >
                                  <Icon name="close" className="text-[18px]" />
                                </button>
                              </>
                            )}

                            {item.status !== 'REJECTED' && item.status !== 'CANCELLED' && (
                              <button
                                type="button"
                                onClick={() => setAssignModalBooking(item)}
                                title={item.driverName ? 'Đổi tài xế' : 'Gán tài xế'}
                                className="rounded p-1 text-teal-600 hover:bg-teal-50 transition"
                              >
                                <Icon name="badge" className="text-[18px]" />
                              </button>
                            )}

                            <button
                              type="button"
                              onClick={() => navigate(`/provider/bookings/${item.bookingCode}`)}
                              title="Xem chi tiết"
                              className="rounded p-1 text-slate-500 hover:bg-slate-100 transition"
                            >
                              <Icon name="visibility" className="text-[18px]" />
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

          {/* Table Pagination Footer */}
          {!loading && filteredList.length > 0 && (
            <div className="flex flex-col items-center justify-between gap-3 border-t border-[#e2efe6] bg-[#f9fdfa] px-5 py-3 text-xs text-slate-500 sm:flex-row">
              <div className="flex items-center gap-3">
                <span>
                  Hiển thị <strong className="text-slate-800">{(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredList.length)}</strong> của <strong className="text-slate-800">{filteredList.length}</strong> đơn
                </span>
                <div className="flex items-center gap-1.5 border-l border-slate-200 pl-3">
                  <span>Trang:</span>
                  <select
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value))
                      setCurrentPage(1)
                    }}
                    className="rounded border border-slate-200 bg-white py-0.5 px-1.5 text-xs font-semibold text-slate-700"
                  >
                    <option value={10}>10 / trang</option>
                    <option value={20}>20 / trang</option>
                    <option value={50}>50 / trang</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center gap-1">
                <button
                  type="button"
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  className="flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 disabled:opacity-40"
                >
                  <Icon name="chevron_left" className="text-[16px]" />
                </button>

                {Array.from({ length: totalPages }, (_, i) => i + 1).map((pageNum) => (
                  <button
                    key={pageNum}
                    type="button"
                    onClick={() => setCurrentPage(pageNum)}
                    className={`flex h-7 w-7 items-center justify-center rounded-md text-xs font-bold transition ${
                      currentPage === pageNum
                        ? 'bg-[#064e3b] text-white shadow-2xs'
                        : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    {pageNum}
                  </button>
                ))}

                <button
                  type="button"
                  disabled={currentPage === totalPages}
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  className="flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 disabled:opacity-40"
                >
                  <Icon name="chevron_right" className="text-[16px]" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Action Modals */}
      <AcceptBookingModal
        booking={acceptModalBooking}
        isOpen={Boolean(acceptModalBooking)}
        onClose={() => setAcceptModalBooking(null)}
        onSuccess={handleActionSuccess}
      />

      <DenyBookingModal
        booking={denyModalBooking}
        isOpen={Boolean(denyModalBooking)}
        onClose={() => setDenyModalBooking(null)}
        onSuccess={handleActionSuccess}
      />

      <AssignDriverModal
        booking={assignModalBooking}
        isOpen={Boolean(assignModalBooking)}
        onClose={() => setAssignModalBooking(null)}
        onSuccess={handleActionSuccess}
      />
    </ProviderLayout>
  )
}
