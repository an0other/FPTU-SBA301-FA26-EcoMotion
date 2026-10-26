import { useCallback, useEffect, useState, useMemo } from 'react'
import ProviderHeader from '../../components/provider/ProviderHeader'
import Toast from '../../components/Toast'
import { providerService } from '../../services/providerService'
import { getErrorMessage } from '../../utils/errorMessage'

export default function ProviderSchedulePage() {
  const [stats, setStats] = useState({
    totalActiveBlocks: 0,
    activeMaintenance: 0,
    activeBlocked: 0,
    upcomingBookings: 0,
  })
  const [schedules, setSchedules] = useState([])
  const [vehicles, setVehicles] = useState([])
  const [loading, setLoading] = useState(true)
  const [toast, setToast] = useState(null)

  // Filters
  const [selectedVehicleId, setSelectedVehicleId] = useState('')
  const [selectedType, setSelectedType] = useState('')
  const [selectedStatus, setSelectedStatus] = useState('')
  const [startDateFilter, setStartDateFilter] = useState('')
  const [endDateFilter, setEndDateFilter] = useState('')

  // Pagination
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 6

  // Real-time clock ticker (auto-detects expired schedules every 15s)
  const [nowTime, setNowTime] = useState(() => Date.now())

  // Modal create
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formVehicleId, setFormVehicleId] = useState('')
  const [formType, setFormType] = useState('MAINTENANCE')
  const [formStartAt, setFormStartAt] = useState('')
  const [formEndAt, setFormEndAt] = useState('')
  const [formReason, setFormReason] = useState('')
  const [formError, setFormError] = useState('')

  // Modal unblock confirmation
  const [cancelingSchedule, setCancelingSchedule] = useState(null)
  const [cancelingLoading, setCancelingLoading] = useState(false)

  // Fetch stats and vehicles
  const loadInitialData = useCallback(async () => {
    try {
      const [statsRes, vehiclesRes] = await Promise.all([
        providerService.getProviderScheduleStats(),
        providerService.getVehicles({ size: 100 }),
      ])
      if (statsRes) setStats(statsRes)
      if (vehiclesRes?.items) setVehicles(vehiclesRes.items)
    } catch (err) {
      console.error('Failed to load schedule stats or vehicles', err)
    }
  }, [])

  // Fetch schedules
  const loadSchedules = useCallback(async () => {
    setLoading(true)
    try {
      const params = {}
      if (selectedVehicleId) params.vehicleId = selectedVehicleId
      if (selectedType) params.type = selectedType
      if (selectedStatus === 'CANCELLED') params.status = 'CANCELLED'
      else if (selectedStatus === 'ACTIVE' || selectedStatus === 'EXPIRED') params.status = 'ACTIVE'
      if (startDateFilter) params.fromDate = new Date(startDateFilter).toISOString()
      if (endDateFilter) params.toDate = new Date(endDateFilter).toISOString()

      const data = await providerService.getProviderSchedules(params)
      setSchedules(data || [])
    } catch (err) {
      setToast({ type: 'error', message: getErrorMessage(err, 'Không thể tải danh sách lịch xe') })
    } finally {
      setLoading(false)
    }
  }, [selectedVehicleId, selectedType, selectedStatus, startDateFilter, endDateFilter])

  useEffect(() => {
    queueMicrotask(loadInitialData)
  }, [loadInitialData])

  // Auto-tick and refresh stats every 15 seconds
  useEffect(() => {
    const timer = setInterval(() => {
      setNowTime(Date.now())
      loadInitialData()
    }, 15000)
    return () => clearInterval(timer)
  }, [loadInitialData])

  useEffect(() => {
    queueMicrotask(() => {
      loadSchedules()
      setCurrentPage(1)
    })
  }, [loadSchedules])

  // Helper date formatter
  const formatDateTime = (isoString) => {
    if (!isoString) return { time: '--:--', date: '--/--/----' }
    const d = new Date(isoString)
    const time = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false })
    const date = d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
    return { time, date }
  }

  // Duration calculator
  const formatDuration = (startIso, endIso) => {
    if (!startIso || !endIso) return ''
    const start = new Date(startIso).getTime()
    const end = new Date(endIso).getTime()
    const totalMinutes = Math.max(1, Math.round((end - start) / (1000 * 60)))

    if (totalMinutes < 60) {
      return `${totalMinutes} phút`
    }
    const hours = Math.floor(totalMinutes / 60)
    const remainMinutes = totalMinutes % 60
    if (hours < 24) {
      return remainMinutes > 0 ? `${hours}h ${remainMinutes}p` : `${hours} giờ`
    }
    const days = Math.floor(hours / 24)
    const remainHours = hours % 24
    return remainHours > 0 ? `${days} ngày ${remainHours}h` : `${days} ngày`
  }

  // Vehicle badge style
  const getVehicleBadgeClass = (model = '') => {
    const m = model.toUpperCase()
    if (m.includes('VF 6')) return 'bg-emerald-50 text-emerald-700 border-emerald-200'
    if (m.includes('VF 8')) return 'bg-teal-50 text-teal-700 border-teal-200'
    if (m.includes('E34')) return 'bg-green-50 text-green-700 border-green-200'
    if (m.includes('IONIQ')) return 'bg-sky-50 text-sky-700 border-sky-200'
    if (m.includes('BMW')) return 'bg-indigo-50 text-indigo-700 border-indigo-200'
    return 'bg-slate-100 text-slate-700 border-slate-200'
  }

  const getVehicleShortName = (vehicle) => {
    if (!vehicle) return 'XE'
    const model = vehicle.model || ''
    if (model.toLowerCase().includes('vf 6')) return 'VF 6'
    if (model.toLowerCase().includes('vf 8')) return 'VF 8'
    if (model.toLowerCase().includes('e34')) return 'e34'
    if (model.toLowerCase().includes('ioniq')) return 'IONIQ'
    if (vehicle.brand && vehicle.brand.toUpperCase() === 'BMW') return 'BMW'
    return model.split(' ')[0] || vehicle.brand || 'XE'
  }

  // Filtered schedules considering real-time expiration
  const displayedSchedules = useMemo(() => {
    if (!selectedStatus) return schedules
    if (selectedStatus === 'ACTIVE') {
      return schedules.filter((item) => item.status === 'ACTIVE' && new Date(item.endAt).getTime() > nowTime)
    }
    if (selectedStatus === 'EXPIRED') {
      return schedules.filter((item) => item.status === 'ACTIVE' && new Date(item.endAt).getTime() <= nowTime)
    }
    if (selectedStatus === 'CANCELLED') {
      return schedules.filter((item) => item.status === 'CANCELLED')
    }
    return schedules
  }, [schedules, selectedStatus, nowTime])

  // Pagination slice
  const paginatedSchedules = useMemo(() => {
    const startIdx = (currentPage - 1) * itemsPerPage
    return displayedSchedules.slice(startIdx, startIdx + itemsPerPage)
  }, [displayedSchedules, currentPage, itemsPerPage])

  const totalPages = Math.ceil(displayedSchedules.length / itemsPerPage) || 1

  const toLocalIso = (d) => {
    if (!d) return ''
    const offset = d.getTimezoneOffset() * 60000
    return new Date(d.getTime() - offset).toISOString().slice(0, 16)
  }

  // Handle open create modal
  const handleOpenCreateModal = () => {
    setFormVehicleId(vehicles.length > 0 ? vehicles[0].id : '')
    setFormType('MAINTENANCE')
    const now = new Date()
    now.setMinutes(0, 0, 0)
    now.setHours(now.getHours() + 1)
    const end = new Date(now)
    end.setHours(end.getHours() + 8)

    setFormStartAt(toLocalIso(now))
    setFormEndAt(toLocalIso(end))
    setFormReason('')
    setFormError('')
    setIsModalOpen(true)
  }

  // Submit create block
  const handleCreateSchedule = async (e) => {
    e.preventDefault()
    setFormError('')

    if (!formVehicleId) {
      setFormError('Vui lòng chọn phương tiện')
      return
    }

    const startDate = new Date(formStartAt)
    const endDate = new Date(formEndAt)

    if (isNaN(startDate.getTime()) || isNaN(endDate.getTime())) {
      setFormError('Thời gian bắt đầu hoặc kết thúc không hợp lệ')
      return
    }

    const now = Date.now()
    if (startDate.getTime() < now - 60000) {
      setFormError('Thời gian bắt đầu không được ở trong quá khứ')
      return
    }

    if (endDate <= startDate) {
      setFormError('Thời gian kết thúc phải sau thời gian bắt đầu')
      return
    }

    setSubmitting(true)
    try {
      await providerService.createBlockSchedule({
        vehicleId: Number(formVehicleId),
        type: formType,
        startAt: startDate.toISOString(),
        endAt: endDate.toISOString(),
        reason: formReason.trim() || undefined,
      })

      setToast({ type: 'success', message: 'Tạo lịch chặn thành công!' })
      setIsModalOpen(false)
      loadInitialData()
      loadSchedules()
    } catch (err) {
      setFormError(getErrorMessage(err, 'Không thể tạo lịch chặn'))
    } finally {
      setSubmitting(false)
    }
  }

  // Submit cancel unblock
  const handleConfirmCancel = async () => {
    if (!cancelingSchedule) return
    setCancelingLoading(true)
    try {
      await providerService.cancelBlockSchedule(cancelingSchedule.id)
      setToast({ type: 'success', message: `Đã mở lại lịch cho xe ${cancelingSchedule.vehicleLicensePlate}` })
      setCancelingSchedule(null)
      loadInitialData()
      loadSchedules()
    } catch (err) {
      setToast({ type: 'error', message: getErrorMessage(err, 'Không thể mở lại lịch xe') })
    } finally {
      setCancelingLoading(false)
    }
  }

  const handleResetFilters = () => {
    setSelectedVehicleId('')
    setSelectedType('')
    setSelectedStatus('')
    setStartDateFilter('')
    setEndDateFilter('')
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans text-slate-800">
      <Toast message={toast?.message} type={toast?.type} onClose={() => setToast(null)} />
      <ProviderHeader activeTab="schedules" />

      <main className="flex-1 w-full max-w-[1600px] mx-auto px-4 sm:px-6 lg:px-8 xl:px-12 py-6 space-y-6">
        {/* Header Title & Actions */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl lg:text-3xl font-extrabold text-slate-900 tracking-tight">
              Quản lý Chặn lịch & Bảo dưỡng đội xe
            </h1>
            <p className="text-sm text-slate-500 mt-1 max-w-3xl">
              Thiết lập lịch bảo dưỡng định kỳ hoặc khóa xe bận việc riêng. Hệ thống tự động mở bán các khoảng trống và chống trùng lịch đặt xe.
            </p>
          </div>

          <div className="shrink-0">
            <button
              type="button"
              onClick={handleOpenCreateModal}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold text-sm rounded-xl shadow-sm transition-all cursor-pointer shadow-emerald-700/20"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <span>+ Thêm lịch chặn / bảo trì</span>
            </button>
          </div>
        </div>

        {/* 4 Metric KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 xl:gap-5">
          {/* Card 1: Tổng lịch đang chặn */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">Tổng lịch đang chặn</p>
                <div className="mt-2 flex items-baseline gap-1.5">
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {String(stats.totalActiveBlocks).padStart(2, '0')}
                  </span>
                  <span className="text-sm font-medium text-slate-500">lịch</span>
                </div>
              </div>
              <div className="w-11 h-11 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-100 shrink-0">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-rose-700 bg-rose-50 px-2.5 py-1 rounded-full border border-rose-200">
                <span className="w-1.5 h-1.5 rounded-full bg-rose-500"></span>
                Đang hiệu lực (ACTIVE)
              </span>
            </div>
          </div>

          {/* Card 2: Xe đang bảo dưỡng */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">Xe đang bảo dưỡng</p>
                <div className="mt-2 flex items-baseline gap-1.5">
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {String(stats.activeMaintenance).padStart(2, '0')}
                  </span>
                  <span className="text-sm font-medium text-slate-500">xe</span>
                </div>
              </div>
              <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-100 shrink-0">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-700 bg-amber-50 px-2.5 py-1 rounded-full border border-amber-200">
                <svg className="w-3.5 h-3.5 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                </svg>
                Loại MAINTENANCE
              </span>
            </div>
          </div>

          {/* Card 3: Xe bận việc riêng */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">Xe bận việc riêng</p>
                <div className="mt-2 flex items-baseline gap-1.5">
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {String(stats.activeBlocked).padStart(2, '0')}
                  </span>
                  <span className="text-sm font-medium text-slate-500">xe</span>
                </div>
              </div>
              <div className="w-11 h-11 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center border border-purple-100 shrink-0">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-purple-700 bg-purple-50 px-2.5 py-1 rounded-full border border-purple-200">
                <svg className="w-3.5 h-3.5 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
                Loại BLOCKED
              </span>
            </div>
          </div>

          {/* Card 4: Lịch khách đặt sắp tới */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs flex flex-col justify-between hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">Lịch khách đặt sắp tới</p>
                <div className="mt-2 flex items-baseline gap-1.5">
                  <span className="text-3xl font-extrabold text-slate-900 tracking-tight">
                    {String(stats.upcomingBookings).padStart(2, '0')}
                  </span>
                  <span className="text-sm font-medium text-slate-500">chuyến</span>
                </div>
              </div>
              <div className="w-11 h-11 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center border border-blue-100 shrink-0">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                </svg>
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-700 bg-blue-50 px-2.5 py-1 rounded-full border border-blue-200">
                <svg className="w-3.5 h-3.5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                </svg>
                Đơn BOOKED của khách
              </span>
            </div>
          </div>
        </div>

        {/* Filter Bar */}
        <div className="bg-white rounded-2xl border border-slate-200/80 p-4 shadow-xs flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-3">
            {/* Filter: Phương tiện */}
            <div className="relative min-w-[220px]">
              <select
                value={selectedVehicleId}
                onChange={(e) => setSelectedVehicleId(e.target.value)}
                className="w-full pl-9 pr-8 py-2 text-xs font-medium bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 transition-all cursor-pointer appearance-none text-slate-700"
              >
                <option value="">Tất cả phương tiện ({vehicles.length} xe)</option>
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.licensePlate} • {v.brand} {v.model}
                  </option>
                ))}
              </select>
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
              </div>
              <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>

            {/* Filter: Phân loại */}
            <div className="relative min-w-[170px]">
              <select
                value={selectedType}
                onChange={(e) => setSelectedType(e.target.value)}
                className="w-full pl-9 pr-8 py-2 text-xs font-medium bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 transition-all cursor-pointer appearance-none text-slate-700"
              >
                <option value="">Phân loại: Tất cả</option>
                <option value="MAINTENANCE">Bảo dưỡng (MAINTENANCE)</option>
                <option value="BLOCKED">Chặn bận (BLOCKED)</option>
                <option value="BOOKED">Khách đặt (BOOKED)</option>
              </select>
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
                </svg>
              </div>
              <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>

            {/* Filter: Trạng thái */}
            <div className="relative min-w-[180px]">
              <select
                value={selectedStatus}
                onChange={(e) => setSelectedStatus(e.target.value)}
                className="w-full pl-9 pr-8 py-2 text-xs font-medium bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 transition-all cursor-pointer appearance-none text-slate-700"
              >
                <option value="">Trạng thái: Tất cả</option>
                <option value="ACTIVE">Đang hiệu lực (ACTIVE)</option>
                <option value="EXPIRED">Đã kết thúc (EXPIRED)</option>
                <option value="CANCELLED">Đã mở lại (CANCELLED)</option>
              </select>
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-400">
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>

            {/* Date Range Filters */}
            <div className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 rounded-xl px-2.5 py-1 text-xs text-slate-600">
              <svg className="w-4 h-4 text-slate-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <input
                type="date"
                value={startDateFilter}
                onChange={(e) => setStartDateFilter(e.target.value)}
                className="bg-transparent focus:outline-none text-xs text-slate-700 cursor-pointer"
                title="Từ ngày"
              />
              <span className="text-slate-400">-</span>
              <input
                type="date"
                value={endDateFilter}
                onChange={(e) => setEndDateFilter(e.target.value)}
                className="bg-transparent focus:outline-none text-xs text-slate-700 cursor-pointer"
                title="Đến ngày"
              />
            </div>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handleResetFilters}
              title="Đặt lại bộ lọc"
              className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
            <span className="text-xs font-semibold text-slate-500">
              Hiển thị <span className="text-slate-900 font-bold">{displayedSchedules.length}</span> bản ghi
            </span>
          </div>
        </div>

        {/* Data Table Container */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/70 text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4 lg:px-6">Mã lịch</th>
                  <th className="py-3.5 px-4 lg:px-6">Phương tiện</th>
                  <th className="py-3.5 px-4 lg:px-6">Phân loại</th>
                  <th className="py-3.5 px-4 lg:px-6">Bắt đầu</th>
                  <th className="py-3.5 px-4 lg:px-6">Kết thúc</th>
                  <th className="py-3.5 px-4 lg:px-6">Trạng thái</th>
                  <th className="py-3.5 px-4 lg:px-6 min-w-[200px]">Lý do / Ghi chú</th>
                  <th className="py-3.5 px-4 lg:px-6 text-right">Hành động</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {loading ? (
                  <tr>
                    <td colSpan="8" className="py-12 text-center text-slate-400">
                      <div className="inline-flex items-center gap-2">
                        <svg className="w-5 h-5 animate-spin text-emerald-600" viewBox="0 0 24 24" fill="none">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                        </svg>
                        <span>Đang đồng bộ lịch xe từ máy chủ...</span>
                      </div>
                    </td>
                  </tr>
                ) : paginatedSchedules.length === 0 ? (
                  <tr>
                    <td colSpan="8" className="py-16 text-center text-slate-400">
                      <div className="max-w-xs mx-auto space-y-2">
                        <div className="w-12 h-12 mx-auto rounded-full bg-slate-100 flex items-center justify-center text-slate-400">
                          <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                        </div>
                        <p className="font-semibold text-slate-700">Không có lịch xe nào phù hợp</p>
                        <p className="text-xs text-slate-400">Thử thay đổi bộ lọc hoặc tạo một lịch chặn / bảo trì mới.</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  paginatedSchedules.map((item) => {
                    const startInfo = formatDateTime(item.startAt)
                    const endInfo = formatDateTime(item.endAt)
                    const durationText = formatDuration(item.startAt, item.endAt)
                    const isCancelled = item.status === 'CANCELLED'
                    const isPast = new Date(item.endAt).getTime() <= nowTime

                    return (
                      <tr
                        key={item.id}
                        className={`hover:bg-slate-50/80 transition-colors ${
                          isCancelled ? 'opacity-70 bg-slate-50/30' : isPast ? 'opacity-85 bg-slate-50/20' : ''
                        }`}
                      >
                        {/* 1. Mã lịch */}
                        <td className="py-4 px-4 lg:px-6 font-mono font-bold text-slate-800">
                          <span className={isCancelled ? 'line-through text-slate-400' : ''}>
                            #SCH-{String(item.id).padStart(3, '0')}
                          </span>
                        </td>

                        {/* 2. Phương tiện */}
                        <td className="py-4 px-4 lg:px-6">
                          <div className="flex items-center gap-2.5">
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] font-extrabold uppercase tracking-wide border shrink-0 ${getVehicleBadgeClass(
                                item.vehicleModel
                              )}`}
                            >
                              {getVehicleShortName({ model: item.vehicleModel, brand: item.vehicleBrand })}
                            </span>
                            <div>
                              <div className="font-bold text-slate-900 leading-tight">
                                {item.vehicleLicensePlate}
                              </div>
                              <div className="text-[11px] text-slate-500">
                                {item.vehicleBrand} {item.vehicleModel}
                              </div>
                            </div>
                          </div>
                        </td>

                        {/* 3. Phân loại */}
                        <td className="py-4 px-4 lg:px-6">
                          {item.type === 'MAINTENANCE' ? (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200/80">
                              <svg className="w-3.5 h-3.5 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                              </svg>
                              <span>Bảo dưỡng (MAINTENANCE)</span>
                            </span>
                          ) : item.type === 'BLOCKED' ? (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-purple-50 text-purple-800 border border-purple-200/80">
                              <svg className="w-3.5 h-3.5 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                              </svg>
                              <span>Chặn bận (BLOCKED)</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-blue-50 text-blue-800 border border-blue-200/80">
                              <svg className="w-3.5 h-3.5 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                              </svg>
                              <span>Khách đặt (BOOKED)</span>
                            </span>
                          )}
                        </td>

                        {/* 4. Bắt đầu */}
                        <td className="py-4 px-4 lg:px-6">
                          <div className="font-bold text-slate-900 leading-tight">{startInfo.time}</div>
                          <div className="text-[11px] text-slate-500">{startInfo.date}</div>
                        </td>

                        {/* 5. Kết thúc & Thời lượng */}
                        <td className="py-4 px-4 lg:px-6">
                          <div className="flex items-center gap-2">
                            <div>
                              <div className="font-bold text-slate-900 leading-tight">{endInfo.time}</div>
                              <div className="text-[11px] text-slate-500">{endInfo.date}</div>
                            </div>
                            {durationText && (
                              <span className="bg-slate-100 text-slate-600 border border-slate-200 px-2 py-0.5 rounded text-[11px] font-medium shrink-0">
                                {durationText}
                              </span>
                            )}
                          </div>
                        </td>

                        {/* 6. Trạng thái */}
                        <td className="py-4 px-4 lg:px-6">
                          {isCancelled ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-500 border border-slate-200">
                              <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                              Đã mở lại (CANCELLED)
                            </span>
                          ) : isPast ? (
                            <span
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600 border border-slate-200"
                              title="Khung giờ chặn đã kết thúc, hệ thống đã tự động mở lại lịch xe"
                            >
                              <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                              Đã kết thúc
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                              Đang hiệu lực (ACTIVE)
                            </span>
                          )}
                        </td>

                        {/* 7. Lý do / Ghi chú (Tuân thủ DB 100%, không bịa field) */}
                        <td className="py-4 px-4 lg:px-6">
                          {item.type === 'BOOKED' ? (
                            <div>
                              <div className="font-semibold text-slate-900 leading-tight">
                                Đơn thuê #{item.bookingCode || 'BK-' + item.bookingId}
                              </div>
                              <div className="text-[11px] text-blue-600 font-medium">
                                Tự động khóa bởi hệ thống đặt xe
                              </div>
                            </div>
                          ) : (
                            <div>
                              <div className="font-medium text-slate-900 leading-tight">
                                {item.reason || (item.type === 'MAINTENANCE' ? 'Bảo dưỡng kỹ thuật định kỳ' : 'Khóa xe bận việc riêng')}
                              </div>
                              {isCancelled ? (
                                <div className="text-[11px] text-slate-400">
                                  Đã hoàn tất mở bán lại
                                </div>
                              ) : isPast ? (
                                <div className="text-[11px] text-slate-400">
                                  Tự động mở bán lại sau khi hết giờ
                                </div>
                              ) : null}
                            </div>
                          )}
                        </td>

                        {/* 8. Hành động */}
                        <td className="py-4 px-4 lg:px-6 text-right">
                          {item.type === 'BOOKED' ? (
                            <span
                              title="Lịch đặt xe của khách hàng được quản lý theo vòng đời đơn thuê"
                              className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-slate-400 bg-slate-50 border border-slate-200 rounded-xl cursor-not-allowed select-none"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                              </svg>
                              <span>Đơn khách</span>
                            </span>
                          ) : isCancelled ? (
                            <span className="text-xs text-slate-400 font-medium select-none">
                              Đã mở lại
                            </span>
                          ) : isPast ? (
                            <span
                              title="Khung giờ chặn đã kết thúc, hệ thống đã tự động mở lại lịch xe"
                              className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-slate-400 bg-slate-100/70 border border-slate-200 rounded-xl cursor-default select-none"
                            >
                              <svg className="w-3.5 h-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                              </svg>
                              <span>Đã hết giờ</span>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setCancelingSchedule(item)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-600 bg-rose-50/50 hover:bg-rose-50 border border-rose-200 hover:border-rose-300 rounded-xl transition-all cursor-pointer shadow-2xs"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z" />
                              </svg>
                              <span>Mở lại lịch</span>
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Table Bottom Notice & Pagination */}
          <div className="p-4 border-t border-slate-100 bg-slate-50/50 flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <div className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-[10px] shrink-0">
                i
              </div>
              <span>
                Hệ thống tự động kích hoạt trạng thái mở bán <strong className="text-slate-700">(Auto-Open)</strong> ngay sau khi hết giờ chặn hoặc khi mở lại lịch.
              </span>
            </div>

            {totalPages > 1 && (
              <div className="flex items-center gap-1.5 shrink-0">
                <button
                  type="button"
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage((p) => Math.max(p - 1, 1))}
                  className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  Trước
                </button>
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => setCurrentPage(p)}
                    className={`w-8 h-8 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                      currentPage === p
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    {p}
                  </button>
                ))}
                <button
                  type="button"
                  disabled={currentPage === totalPages}
                  onClick={() => setCurrentPage((p) => Math.min(p + 1, totalPages))}
                  className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  Tiếp
                </button>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Modal: + Thêm lịch chặn / bảo trì */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl border border-slate-100 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>
                <div>
                  <h3 className="font-extrabold text-slate-900 text-base">Thêm lịch chặn / bảo trì xe</h3>
                  <p className="text-xs text-slate-500">Khóa khoảng thời gian xe không thể nhận đơn đặt mới</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg cursor-pointer"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateSchedule} className="p-6 space-y-4">
              {formError && (
                <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
                  <svg className="w-4 h-4 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  <span>{formError}</span>
                </div>
              )}

              {/* 1. Chọn phương tiện */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1.5 uppercase">
                  1. Chọn phương tiện <span className="text-rose-500">*</span>
                </label>
                <select
                  value={formVehicleId}
                  onChange={(e) => setFormVehicleId(e.target.value)}
                  className="w-full px-3.5 py-2.5 text-xs font-semibold bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 text-slate-800 cursor-pointer"
                >
                  {vehicles.length === 0 ? (
                    <option value="">Không có phương tiện khả dụng</option>
                  ) : (
                    vehicles.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.licensePlate} • {v.brand} {v.model} ({v.seatCount} chỗ)
                      </option>
                    ))
                  )}
                </select>
              </div>

              {/* 2. Loại lịch chặn */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1.5 uppercase">
                  2. Loại lịch chặn <span className="text-rose-500">*</span>
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <label
                    className={`border rounded-xl p-3 flex flex-col gap-1 cursor-pointer transition-all ${
                      formType === 'MAINTENANCE'
                        ? 'border-amber-500 bg-amber-50/50 text-amber-900 ring-2 ring-amber-500/20'
                        : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs flex items-center gap-1.5">
                        <svg className="w-4 h-4 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                        </svg>
                        Bảo dưỡng kỹ thuật
                      </span>
                      <input
                        type="radio"
                        name="scheduleType"
                        value="MAINTENANCE"
                        checked={formType === 'MAINTENANCE'}
                        onChange={() => setFormType('MAINTENANCE')}
                        className="text-amber-600 focus:ring-amber-500"
                      />
                    </div>
                    <span className="text-[11px] text-slate-500">Sửa chữa, thay pin, bảo dưỡng định kỳ</span>
                  </label>

                  <label
                    className={`border rounded-xl p-3 flex flex-col gap-1 cursor-pointer transition-all ${
                      formType === 'BLOCKED'
                        ? 'border-purple-500 bg-purple-50/50 text-purple-900 ring-2 ring-purple-500/20'
                        : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs flex items-center gap-1.5">
                        <svg className="w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                        </svg>
                        Khóa bận việc riêng
                      </span>
                      <input
                        type="radio"
                        name="scheduleType"
                        value="BLOCKED"
                        checked={formType === 'BLOCKED'}
                        onChange={() => setFormType('BLOCKED')}
                        className="text-purple-600 focus:ring-purple-500"
                      />
                    </div>
                    <span className="text-[11px] text-slate-500">Chủ xe dùng cá nhân hoặc công tác</span>
                  </label>
                </div>
              </div>

              {/* 3 & 4. Thời gian bắt đầu & kết thúc */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5 uppercase">
                    3. Bắt đầu <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="datetime-local"
                    value={formStartAt}
                    min={toLocalIso(new Date())}
                    onChange={(e) => setFormStartAt(e.target.value)}
                    required
                    className="w-full px-3.5 py-2 text-xs font-medium bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 text-slate-800"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1.5 uppercase">
                    4. Kết thúc <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="datetime-local"
                    value={formEndAt}
                    min={formStartAt || toLocalIso(new Date())}
                    onChange={(e) => setFormEndAt(e.target.value)}
                    required
                    className="w-full px-3.5 py-2 text-xs font-medium bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 text-slate-800"
                  />
                </div>
              </div>

              {/* 5. Lý do / Ghi chú (Max 255 chars, đúng schema DB) */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-xs font-bold text-slate-700 uppercase">
                    5. Lý do / Ghi chú
                  </label>
                  <span className="text-[11px] text-slate-400">{formReason.length}/255</span>
                </div>
                <textarea
                  rows="3"
                  maxLength={255}
                  value={formReason}
                  onChange={(e) => setFormReason(e.target.value)}
                  placeholder={
                    formType === 'MAINTENANCE'
                      ? 'VD: Bảo dưỡng pin định kỳ 10.000km, thay lọc gió cabin...'
                      : 'VD: Chủ xe đi công tác tỉnh, phục vụ việc cá nhân gia đình...'
                  }
                  className="w-full px-3.5 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 text-slate-800"
                ></textarea>
              </div>

              {/* Actions */}
              <div className="pt-2 flex items-center justify-end gap-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 rounded-xl shadow-xs disabled:opacity-50 transition-all cursor-pointer inline-flex items-center gap-2"
                >
                  {submitting && (
                    <svg className="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                    </svg>
                  )}
                  <span>Xác nhận chặn lịch</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Xác nhận Mở lại lịch */}
      {cancelingSchedule && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full shadow-2xl border border-slate-100 p-6 space-y-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="w-12 h-12 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto">
              <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z" />
              </svg>
            </div>
            <div className="text-center space-y-1.5">
              <h3 className="font-extrabold text-slate-900 text-base">Xác nhận mở lại lịch xe?</h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                Bạn có chắc chắn muốn hủy lịch chặn cho xe{' '}
                <strong className="text-slate-900 font-bold">{cancelingSchedule.vehicleLicensePlate}</strong> ({cancelingSchedule.vehicleBrand} {cancelingSchedule.vehicleModel})?
              </p>
              <p className="text-[11px] text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-lg border border-emerald-100 mt-2">
                Sau khi mở lại, xe sẽ tự động kích hoạt trạng thái sẵn sàng cho khách hàng đặt thuê.
              </p>
            </div>
            <div className="flex items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => setCancelingSchedule(null)}
                className="flex-1 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all cursor-pointer"
              >
                Giữ nguyên
              </button>
              <button
                type="button"
                disabled={cancelingLoading}
                onClick={handleConfirmCancel}
                className="flex-1 py-2 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 active:bg-rose-800 rounded-xl shadow-xs disabled:opacity-50 transition-all cursor-pointer inline-flex items-center justify-center gap-2"
              >
                {cancelingLoading && (
                  <svg className="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                  </svg>
                )}
                <span>Mở lại ngay</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
