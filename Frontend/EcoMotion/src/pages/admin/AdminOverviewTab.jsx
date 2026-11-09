import { useState, useMemo } from 'react'
import {
  IconCar,
  IconShield,
  IconUsers,
  IconWallet,
  IconClock,
  IconAlertTriangle,
  IconCheckCircle,
  IconArrowRight,
  IconSparkles,
  IconRefresh,
  IconCalendar,
  IconChevronDown,
  IconChevronRight,
  IconX,
} from '../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

function formatCompactVND(amount) {
  if (amount == null || amount === 0) return '0 ₫'
  const num = Number(amount)
  if (num >= 1_000_000_000) {
    return `${(num / 1_000_000_000).toFixed(1).replace(/\.0$/, '')} tỷ ₫`
  }
  if (num >= 1_000_000) {
    return `${(num / 1_000_000).toFixed(1).replace(/\.0$/, '')} tr ₫`
  }
  if (num >= 1_000) {
    return `${(num / 1_000).toFixed(0)}k ₫`
  }
  return `${num.toLocaleString('vi-VN')} ₫`
}

function generateFallbackMonths() {
  const list = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const y = d.getFullYear()
    const m = d.getMonth() + 1
    const key = `${y}-${String(m).padStart(2, '0')}`
    const label = `Tháng ${String(m).padStart(2, '0')}/${y}`
    list.push({ key, year: y, month: m, label })
  }
  return list
}

function IconTrendingUp({ className = 'h-3.5 w-3.5' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
      <polyline points="17 6 23 6 23 12" />
    </svg>
  )
}

function IconTrendingDown({ className = 'h-3.5 w-3.5' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 18 13.5 8.5 8.5 13.5 1 6" />
      <polyline points="17 18 23 18 23 12" />
    </svg>
  )
}

function GrowthBadge({ rate, diff, isCurrency = false, label = '', size = 'sm' }) {
  if (rate === null || rate === undefined) {
    return (
      <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-slate-500 border border-slate-200/60">
        <span>Kỳ đầu</span>
      </span>
    )
  }

  const numRate = Number(rate)
  const isPositive = numRate > 0

  if (numRate === 0) {
    return (
      <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-600 border border-slate-200">
        <span>0.0%</span>
        {label && <span className="font-normal text-slate-400">vs {label}</span>}
      </span>
    )
  }

  const badgeClass = isPositive
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200/80'
    : 'bg-rose-50 text-rose-700 border-rose-200/80'

  const formattedDiff = diff != null
    ? (isCurrency ? formatCompactVND(Math.abs(diff)) : `${Math.abs(diff)}`)
    : null

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 font-extrabold border shadow-2xs ${badgeClass} ${size === 'xs' ? 'text-[9px]' : size === 'md' ? 'text-xs px-2 py-1' : 'text-[10px]'
        }`}
      title={diff != null ? `${isPositive ? '+' : '-'}${isCurrency ? formatVND(diff) : diff} so với ${label || 'kỳ trước'}` : ''}
    >
      {isPositive ? <IconTrendingUp className="h-3 w-3 shrink-0" /> : <IconTrendingDown className="h-3 w-3 shrink-0" />}
      <span>{isPositive ? `+${numRate.toFixed(1)}%` : `${numRate.toFixed(1)}%`}</span>
      {formattedDiff && (
        <span className="font-medium opacity-85 text-[9px] hidden sm:inline">
          ({isPositive ? '+' : '-'}{formattedDiff})
        </span>
      )}
      {label && <span className="font-normal text-[9px] opacity-75">vs {label}</span>}
    </span>
  )
}

export default function AdminOverviewTab({
  stats,
  loading,
  selectedMonthKey = 'ALL',
  onMonthChange,
  onNavigate,
  onRefresh,
}) {
  const [chartView, setChartView] = useState('both') // 'both' | 'donut' | 'bar'
  const [activeSegment, setActiveSegment] = useState(null)
  const monthlySummaries = stats?.monthlySummaries

  // Derive month options from backend monthlySummaries or fallback
  const monthOptions = useMemo(() => {
    if (monthlySummaries && monthlySummaries.length > 0) {
      return monthlySummaries.map((m) => {
        const key = `${m.year}-${String(m.month).padStart(2, '0')}`
        return {
          key,
          year: m.year,
          month: m.month,
          label: m.label,
        }
      })
    }
    return generateFallbackMonths()
  }, [monthlySummaries])

  // Chronological months for trend chart (oldest to newest), starting from the earliest month with data
  const chronologicalMonths = useMemo(() => {
    const raw = [...(monthlySummaries || [])].reverse()
    const firstActiveIndex = raw.findIndex(
      (m) => Number(m.grossMerchandiseValue ?? 0) > 0 || Number(m.completedBookings ?? 0) > 0
    )
    if (firstActiveIndex > 0) {
      return raw.slice(firstActiveIndex)
    }
    return raw
  }, [monthlySummaries])

  const maxTrendGmv = useMemo(() => {
    if (!chronologicalMonths || chronologicalMonths.length === 0) return 1
    return Math.max(...chronologicalMonths.map((m) => Number(m.grossMerchandiseValue ?? 0)), 1)
  }, [chronologicalMonths])

  const isAllTime = selectedMonthKey === 'ALL'
  const currentMonthOption = monthOptions.find((opt) => opt.key === selectedMonthKey)
  const currentPeriodLabel = isAllTime ? 'Toàn thời gian' : (currentMonthOption?.label || selectedMonthKey)

  // Month navigation helpers
  const currentMonthIndex = monthOptions.findIndex((opt) => opt.key === selectedMonthKey)
  const canGoNextMonth = !isAllTime && currentMonthIndex > 0 // index 0 is newest month
  const canGoPrevMonth = isAllTime ? monthOptions.length > 0 : currentMonthIndex < monthOptions.length - 1

  function handlePrevMonth() {
    if (isAllTime) {
      if (monthOptions.length > 0) onMonthChange?.(monthOptions[0].key)
    } else if (canGoPrevMonth) {
      onMonthChange?.(monthOptions[currentMonthIndex + 1].key)
    }
  }

  function handleNextMonth() {
    if (!isAllTime && canGoNextMonth) {
      onMonthChange?.(monthOptions[currentMonthIndex - 1].key)
    }
  }

  if (loading && !stats) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="flex items-center gap-3 text-slate-500">
          <span className="h-6 w-6 animate-spin rounded-full border-2 border-brand-600 border-t-transparent" />
          <span>Đang tải dữ liệu tổng quan...</span>
        </div>
      </div>
    )
  }

  // Financial metrics
  const commission = Number(stats?.platformCommission ?? 0)
  const gmv = Number(stats?.grossMerchandiseValue ?? stats?.totalRevenue ?? 0)
  const payout = Number(stats?.providerPayoutDue ?? 0)
  const escrow = Number(stats?.escrowDeposits ?? 0)
  const completedBookings = stats?.completedBookings ?? 0

  // Total for donut distribution
  const totalAllocated = commission + payout + escrow
  const pctCommission = totalAllocated > 0 ? (commission / totalAllocated) * 100 : 0
  const pctPayout = totalAllocated > 0 ? (payout / totalAllocated) * 100 : 0
  const pctEscrow = totalAllocated > 0 ? (escrow / totalAllocated) * 100 : 0

  const circumference = 376.99 // 2 * PI * 60
  const dashCommission = (pctCommission / 100) * circumference
  const dashPayout = (pctPayout / 100) * circumference
  const dashEscrow = (pctEscrow / 100) * circumference

  // Max value for Bar Chart scaling
  const maxBarValue = Math.max(gmv, payout, commission, escrow, 1)

  const barData = [
    {
      id: 'commission',
      label: 'Doanh thu sàn (Hoa hồng)',
      shortLabel: 'Hoa hồng sàn',
      sublabel: isAllTime
        ? `Thu từ ${completedBookings} chuyến xong`
        : `Thu từ ${completedBookings} chuyến xong trong tháng`,
      value: commission,
      growthRate: stats?.commissionGrowthRate,
      growthDiff: stats?.commissionDiff,
      isCurrency: true,
      heightPct: commission > 0 ? Math.max(Math.round((commission / maxBarValue) * 100), 6) : 0,
      color: 'emerald',
      barBg: 'bg-gradient-to-t from-emerald-600 via-emerald-500 to-teal-400',
      pillBg: 'bg-emerald-50 text-emerald-800 border-emerald-200',
      dotColor: 'bg-emerald-500',
      textColor: 'text-emerald-700',
      icon: IconWallet,
    },
    {
      id: 'gmv',
      label: 'Tổng giao dịch (GMV)',
      shortLabel: 'Tổng GMV',
      sublabel: isAllTime
        ? `Tổng giá trị ${completedBookings} chuyến`
        : `Tổng giá trị ${completedBookings} chuyến tháng này`,
      value: gmv,
      growthRate: stats?.gmvGrowthRate,
      growthDiff: stats?.gmvDiff,
      isCurrency: true,
      heightPct: gmv > 0 ? Math.max(Math.round((gmv / maxBarValue) * 100), 6) : 0,
      color: 'sky',
      barBg: 'bg-gradient-to-t from-sky-600 via-sky-500 to-blue-400',
      pillBg: 'bg-sky-50 text-sky-800 border-sky-200',
      dotColor: 'bg-sky-500',
      textColor: 'text-sky-700',
      icon: IconSparkles,
    },
    {
      id: 'payout',
      label: 'Đối soát trả Chủ xe',
      shortLabel: 'Trả Chủ xe',
      sublabel: 'Nghĩa vụ chi trả NCC',
      value: payout,
      growthRate: stats?.payoutGrowthRate,
      growthDiff: stats?.payoutDiff,
      isCurrency: true,
      heightPct: payout > 0 ? Math.max(Math.round((payout / maxBarValue) * 100), 6) : 0,
      color: 'indigo',
      barBg: 'bg-gradient-to-t from-indigo-600 via-indigo-500 to-indigo-400',
      pillBg: 'bg-indigo-50 text-indigo-800 border-indigo-200',
      dotColor: 'bg-indigo-500',
      textColor: 'text-indigo-700',
      icon: IconShield,
    },
    {
      id: 'escrow',
      label: 'Quỹ cọc đang tạm giữ',
      shortLabel: 'Quỹ cọc',
      sublabel:
        escrow > 0
          ? 'Đơn đang chạy / chờ hoàn'
          : !isAllTime
            ? 'Không còn cọc tạm giữ (0 ₫)'
            : '0 đơn đang cọc',
      value: escrow,
      growthRate: null,
      growthDiff: null,
      isCurrency: true,
      heightPct: escrow > 0 ? Math.max(Math.round((escrow / maxBarValue) * 100), 6) : 0,
      color: 'amber',
      barBg: 'bg-gradient-to-t from-amber-600 via-amber-500 to-orange-400',
      pillBg: 'bg-amber-50 text-amber-800 border-amber-200',
      dotColor: 'bg-amber-500',
      textColor: 'text-amber-700',
      icon: IconClock,
    },
  ]

  const centerTitle =
    activeSegment === 'commission'
      ? 'Hoa hồng sàn'
      : activeSegment === 'payout'
        ? 'Đối soát NCC'
        : activeSegment === 'escrow'
          ? 'Quỹ cọc lưu ký'
          : activeSegment === 'gmv'
            ? 'Tổng GMV Sàn'
            : 'Tổng dòng tiền'

  const centerValue =
    activeSegment === 'commission'
      ? formatCompactVND(commission)
      : activeSegment === 'payout'
        ? formatCompactVND(payout)
        : activeSegment === 'escrow'
          ? formatCompactVND(escrow)
          : activeSegment === 'gmv'
            ? formatCompactVND(gmv)
            : totalAllocated > 0
              ? formatCompactVND(totalAllocated)
              : '0 ₫'

  const centerSub =
    activeSegment === 'commission'
      ? `${pctCommission.toFixed(1)}% cơ cấu`
      : activeSegment === 'payout'
        ? `${pctPayout.toFixed(1)}% cơ cấu`
        : activeSegment === 'escrow'
          ? escrow > 0
            ? `${pctEscrow.toFixed(1)}% cơ cấu`
            : '0.0% (Không còn cọc)'
          : activeSegment === 'gmv'
            ? `${completedBookings} chuyến xong`
            : totalAllocated > 0
              ? `${formatCompactVND(gmv)} GMV`
              : 'Chưa có dòng tiền'

  const kpis = [
    {
      title: 'Xe chờ phê duyệt',
      count: stats?.pendingVehicles ?? 0,
      icon: IconCar,
      color: 'amber',
      bgGradient: 'from-amber-500/10 via-amber-500/5 to-transparent',
      borderColor: 'border-amber-200',
      textColor: 'text-amber-700',
      badgeBg: 'bg-amber-100 text-amber-800',
      actionLabel: 'Duyệt xe ngay',
      onClick: () => onNavigate('vehicles', { status: 'PENDING' }),
      priority: (stats?.pendingVehicles ?? 0) > 0,
    },
    {
      title: 'NCC đối tác chờ duyệt',
      count: stats?.pendingProviders ?? 0,
      icon: IconShield,
      color: 'indigo',
      bgGradient: 'from-indigo-500/10 via-indigo-500/5 to-transparent',
      borderColor: 'border-indigo-200',
      textColor: 'text-indigo-700',
      badgeBg: 'bg-indigo-100 text-indigo-800',
      actionLabel: 'Duyệt đối tác',
      onClick: () => onNavigate('users', { subTab: 'providers', status: 'PENDING' }),
      priority: (stats?.pendingProviders ?? 0) > 0,
    },
    {
      title: 'Tài xế chờ duyệt',
      count: stats?.pendingDrivers ?? 0,
      icon: IconUsers,
      color: 'purple',
      bgGradient: 'from-purple-500/10 via-purple-500/5 to-transparent',
      borderColor: 'border-purple-200',
      textColor: 'text-purple-700',
      badgeBg: 'bg-purple-100 text-purple-800',
      actionLabel: 'Duyệt tài xế',
      onClick: () => onNavigate('users', { subTab: 'drivers', status: 'PENDING' }),
      priority: (stats?.pendingDrivers ?? 0) > 0,
    },
    {
      title: 'Hồ sơ eKYC chờ duyệt',
      count: stats?.pendingEkyc ?? 0,
      icon: IconCheckCircle,
      color: 'blue',
      bgGradient: 'from-blue-500/10 via-blue-500/5 to-transparent',
      borderColor: 'border-blue-200',
      textColor: 'text-blue-700',
      badgeBg: 'bg-blue-100 text-blue-800',
      actionLabel: 'Duyệt eKYC',
      onClick: () => onNavigate('users', { subTab: 'ekyc', status: 'PENDING' }),
      priority: (stats?.pendingEkyc ?? 0) > 0,
    },
    {
      title: 'Khiếu nại / Dispute mở',
      count: stats?.openDisputes ?? 0,
      icon: IconAlertTriangle,
      color: 'rose',
      bgGradient: 'from-rose-500/10 via-rose-500/5 to-transparent',
      borderColor: 'border-rose-200',
      textColor: 'text-rose-700',
      badgeBg: 'bg-rose-100 text-rose-800',
      actionLabel: 'Xử lý tranh chấp',
      onClick: () => onNavigate('operations', { subTab: 'disputes', status: 'OPEN' }),
      priority: (stats?.openDisputes ?? 0) > 0,
    },
    {
      title: 'Tổng đơn thuê',
      count: stats?.totalBookings ?? 0,
      icon: IconClock,
      color: 'emerald',
      bgGradient: 'from-emerald-500/10 via-emerald-500/5 to-transparent',
      borderColor: 'border-emerald-200',
      textColor: 'text-emerald-700',
      badgeBg: 'bg-emerald-100 text-emerald-800',
      actionLabel: 'Xem danh sách đơn',
      onClick: () => onNavigate('operations', { subTab: 'bookings' }),
      priority: false,
    },
  ]

  return (
    <div className="space-y-8">
      {/* Top Financial Dashboard Section */}
      <div className="overflow-hidden rounded-2xl border border-slate-200/90 bg-white p-5 sm:p-7 shadow-card">
        {/* Header Bar */}
        <div className="flex flex-col gap-4 border-b border-slate-100 pb-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2.5">
              <h2 className="text-xl sm:text-2xl font-black tracking-tight text-slate-900">
                Bảng điều khiển Tài chính &amp; Dòng tiền
              </h2>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200/60 shadow-2xs">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                Trực tuyến
              </span>
            </div>
            <div className="mt-2.5 flex flex-wrap items-center gap-2 text-xs text-slate-600">
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-slate-50 px-2.5 py-1 border border-slate-200/70 font-medium">
                <IconCar className="h-3.5 w-3.5 text-emerald-600" />
                <span>
                  Xe mở bán: <strong className="text-slate-900">{stats?.approvedVehicles ?? 0}</strong>/{stats?.totalVehicles ?? 0}
                </span>
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-slate-50 px-2.5 py-1 border border-slate-200/70 font-medium">
                <IconUsers className="h-3.5 w-3.5 text-blue-600" />
                <span>
                  Tài khoản hoạt động: <strong className="text-slate-900">{stats?.activeUsers ?? 0}</strong>/{stats?.totalUsers ?? 0}
                </span>
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            {/* Month Filter Selector */}
            <div className="inline-flex items-center gap-1 rounded-xl bg-slate-100 p-1 text-xs font-semibold text-slate-700 shadow-inner">
              <span className="flex items-center gap-1.5 px-2 text-slate-500">
                <IconCalendar className="h-4 w-4 text-brand-600" />
                <span className="hidden sm:inline">Kỳ đối soát:</span>
              </span>

              <div className="relative">
                <select
                  value={selectedMonthKey}
                  onChange={(e) => onMonthChange?.(e.target.value)}
                  className="appearance-none rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-8 text-xs font-bold text-slate-900 shadow-xs transition-all hover:border-slate-300 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 cursor-pointer"
                >
                  <option value="ALL">Toàn thời gian (All-time)</option>
                  {monthOptions.map((opt) => (
                    <option key={opt.key} value={opt.key}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <IconChevronDown className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
              </div>

              {/* Prev / Next quick stepper buttons */}
              <button
                type="button"
                onClick={handlePrevMonth}
                disabled={!canGoPrevMonth}
                title="Tháng trước đó"
                className="rounded-lg p-1.5 text-slate-600 hover:bg-white hover:shadow-xs disabled:opacity-30 disabled:hover:bg-transparent transition-all"
              >
                <IconChevronRight className="h-3.5 w-3.5 rotate-180" />
              </button>
              <button
                type="button"
                onClick={handleNextMonth}
                disabled={!canGoNextMonth}
                title="Tháng sau đó"
                className="rounded-lg p-1.5 text-slate-600 hover:bg-white hover:shadow-xs disabled:opacity-30 disabled:hover:bg-transparent transition-all"
              >
                <IconChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>

            {/* View Mode Toggle */}
            <div className="inline-flex items-center rounded-xl bg-slate-100 p-1 text-xs font-medium text-slate-600 shadow-inner">
              <button
                type="button"
                onClick={() => setChartView('both')}
                className={`rounded-lg px-3 py-1.5 transition-all ${chartView === 'both'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                  }`}
              >
                Cả 2 biểu đồ
              </button>
              <button
                type="button"
                onClick={() => setChartView('donut')}
                className={`rounded-lg px-3 py-1.5 transition-all ${chartView === 'donut'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                  }`}
              >
                Cơ cấu (Tròn)
              </button>
              <button
                type="button"
                onClick={() => setChartView('bar')}
                className={`rounded-lg px-3 py-1.5 transition-all ${chartView === 'bar'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                  }`}
              >
                Quy mô (Cột)
              </button>
              <button
                type="button"
                onClick={() => setChartView('trend')}
                className={`rounded-lg px-3 py-1.5 transition-all ${chartView === 'trend'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                  }`}
              >
                Xu hướng các tháng
              </button>
            </div>

            <button
              type="button"
              onClick={onRefresh}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 shadow-xs transition-all hover:border-slate-300 hover:bg-slate-50 active:scale-95"
            >
              <IconRefresh className="h-3.5 w-3.5 text-slate-500" />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Selected Period Status Pill / Alert */}
        {!isAllTime && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200/90 bg-gradient-to-r from-blue-50/90 via-sky-50/50 to-white px-4 py-2.5 text-xs text-blue-900">
            <div className="flex flex-wrap items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
                <IconClock className="h-3.5 w-3.5" />
              </span>
              <span>
                Đang hiển thị cấu trúc tài chính: <strong className="text-slate-900">{currentPeriodLabel}</strong>
              </span>
              {escrow === 0 && (
                <span className="inline-flex items-center gap-1 rounded-md bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-800">
                  <IconCheckCircle className="h-3 w-3 text-emerald-600" />
                  Quỹ cọc = 0 ₫ (Không còn cọc tạm giữ)
                </span>
              )}
            </div>

            <button
              type="button"
              onClick={() => onMonthChange?.('ALL')}
              className="inline-flex items-center gap-1 font-bold text-blue-700 hover:text-blue-900 hover:underline"
            >
              <span>Xem toàn thời gian</span>
              <IconX className="h-3 w-3" />
            </button>
          </div>
        )}

        {/* 4 Summary Metric Cards (Interactive Key) */}
        <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {barData.map((item) => {
            const Icon = item.icon
            const isHovered = activeSegment === item.id
            return (
              <div
                key={item.id}
                onMouseEnter={() => setActiveSegment(item.id)}
                onMouseLeave={() => setActiveSegment(null)}
                className={`group cursor-pointer rounded-xl border p-3.5 transition-all duration-200 ${isHovered
                    ? 'border-brand-500 bg-brand-50/30 shadow-sm ring-1 ring-brand-500/40 -translate-y-0.5'
                    : 'border-slate-200/80 bg-slate-50/40 hover:border-slate-300 hover:bg-white hover:shadow-xs'
                  }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-500 line-clamp-1">
                    {item.shortLabel}
                  </span>
                  <span className={`flex h-6 w-6 items-center justify-center rounded-md ${item.pillBg}`}>
                    <Icon className="h-3.5 w-3.5" />
                  </span>
                </div>
                <div className="mt-1.5 flex items-baseline justify-between gap-1">
                  <span className="text-lg font-black tracking-tight text-slate-900">
                    {formatVND(item.value)}
                  </span>
                  {item.growthRate != null && !isAllTime && (
                    <GrowthBadge
                      rate={item.growthRate}
                      diff={item.growthDiff}
                      isCurrency={item.isCurrency}
                      size="xs"
                    />
                  )}
                </div>
                <div className="mt-1 flex items-center justify-between text-[11px] text-slate-500 truncate">
                  <span className="truncate">{item.sublabel}</span>
                  {item.growthRate != null && !isAllTime && stats?.comparedToLabel && (
                    <span className="text-[10px] text-slate-400 font-medium shrink-0 ml-1">
                      vs {stats.comparedToLabel.replace('Tháng ', 'T')}
                    </span>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* Month-over-Month (MoM) Growth Comparison Callout */}
        {!isAllTime && stats?.comparedToLabel && (
          <div className="mt-4 rounded-2xl border border-indigo-100 bg-gradient-to-r from-indigo-50/70 via-sky-50/40 to-emerald-50/30 p-4 sm:p-5 shadow-xs">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-indigo-100/70 pb-3">
              <div className="flex items-center gap-2.5">
                <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-xs">
                  <IconTrendingUp className="h-4 w-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900 flex flex-wrap items-center gap-2">
                    <span>So sánh quy mô tài chính: {currentPeriodLabel} vs {stats.comparedToLabel}</span>
                    <span className="rounded-full bg-emerald-100 text-emerald-800 px-2 py-0.5 text-[10px] font-extrabold border border-emerald-200">
                      Tăng trưởng MoM
                    </span>
                  </h3>
                  <p className="text-[11px] text-slate-500">
                    Đối chiếu doanh thu hoa hồng sàn, tổng GMV và số chuyến hoàn thành giữa 2 kỳ liên tiếp
                  </p>
                </div>
              </div>
              <div className="text-xs font-semibold text-indigo-800 bg-white/90 border border-indigo-200/80 rounded-xl px-3 py-1 shadow-2xs self-start sm:self-auto">
                Kỳ đối chiếu: <strong>{stats.comparedToLabel}</strong>
              </div>
            </div>

            {/* 4 MoM Growth Stat Blocks */}
            <div className="mt-3.5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              {/* Block 1: Commission */}
              <div className="rounded-xl border border-emerald-200/80 bg-white/95 p-3 shadow-2xs">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-600">Hoa hồng sàn</span>
                  <span className="h-2 w-2 rounded-full bg-emerald-500" />
                </div>
                <div className="mt-1 flex items-baseline gap-1.5">
                  <span className="text-base sm:text-lg font-black text-slate-900">{formatCompactVND(commission)}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-1.5">
                  <GrowthBadge
                    rate={stats?.commissionGrowthRate}
                    diff={stats?.commissionDiff}
                    isCurrency={true}
                    label={stats.comparedToLabel ? stats.comparedToLabel.replace('Tháng ', 'T') : ''}
                    size="sm"
                  />
                </div>
                <div className="mt-1 text-[10px] text-slate-500 truncate">
                  {stats?.commissionDiff != null ? (
                    Number(stats.commissionDiff) >= 0
                      ? `Tăng +${formatVND(stats.commissionDiff)} so với kỳ trước`
                      : `Giảm ${formatVND(stats.commissionDiff)} so với kỳ trước`
                  ) : 'Kỳ so sánh gốc'}
                </div>
              </div>

              {/* Block 2: GMV */}
              <div className="rounded-xl border border-sky-200/80 bg-white/95 p-3 shadow-2xs">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-600">Tổng GMV</span>
                  <span className="h-2 w-2 rounded-full bg-sky-500" />
                </div>
                <div className="mt-1 flex items-baseline gap-1.5">
                  <span className="text-base sm:text-lg font-black text-slate-900">{formatCompactVND(gmv)}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-1.5">
                  <GrowthBadge
                    rate={stats?.gmvGrowthRate}
                    diff={stats?.gmvDiff}
                    isCurrency={true}
                    label={stats.comparedToLabel ? stats.comparedToLabel.replace('Tháng ', 'T') : ''}
                    size="sm"
                  />
                </div>
                <div className="mt-1 text-[10px] text-slate-500 truncate">
                  {stats?.gmvDiff != null ? (
                    Number(stats.gmvDiff) >= 0
                      ? `Tăng +${formatVND(stats.gmvDiff)} GMV sàn`
                      : `Giảm ${formatVND(stats.gmvDiff)} GMV sàn`
                  ) : 'Kỳ so sánh gốc'}
                </div>
              </div>

              {/* Block 3: Payout */}
              <div className="rounded-xl border border-indigo-200/80 bg-white/95 p-3 shadow-2xs">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-600">Trả Chủ xe</span>
                  <span className="h-2 w-2 rounded-full bg-indigo-500" />
                </div>
                <div className="mt-1 flex items-baseline gap-1.5">
                  <span className="text-base sm:text-lg font-black text-slate-900">{formatCompactVND(payout)}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-1.5">
                  <GrowthBadge
                    rate={stats?.payoutGrowthRate}
                    diff={stats?.payoutDiff}
                    isCurrency={true}
                    label={stats.comparedToLabel ? stats.comparedToLabel.replace('Tháng ', 'T') : ''}
                    size="sm"
                  />
                </div>
                <div className="mt-1 text-[10px] text-slate-500 truncate">
                  {stats?.payoutDiff != null ? (
                    Number(stats.payoutDiff) >= 0
                      ? `Tăng +${formatVND(stats.payoutDiff)} trả NCC`
                      : `Giảm ${formatVND(stats.payoutDiff)} trả NCC`
                  ) : 'Kỳ so sánh gốc'}
                </div>
              </div>

              {/* Block 4: Completed Trips */}
              <div className="rounded-xl border border-purple-200/80 bg-white/95 p-3 shadow-2xs">
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-semibold text-slate-600">Chuyến xong</span>
                  <span className="h-2 w-2 rounded-full bg-purple-500" />
                </div>
                <div className="mt-1 flex items-baseline gap-1.5">
                  <span className="text-base sm:text-lg font-black text-slate-900">{completedBookings}</span>
                  <span className="text-xs text-slate-500">chuyến</span>
                </div>
                <div className="mt-1.5 flex items-center gap-1.5">
                  <GrowthBadge
                    rate={stats?.bookingsGrowthRate}
                    diff={stats?.bookingsDiff}
                    isCurrency={false}
                    label={stats.comparedToLabel ? stats.comparedToLabel.replace('Tháng ', 'T') : ''}
                    size="sm"
                  />
                </div>
                <div className="mt-1 text-[10px] text-slate-500 truncate">
                  {stats?.bookingsDiff != null ? (
                    stats.bookingsDiff >= 0
                      ? `Tăng +${stats.bookingsDiff} chuyến xong`
                      : `Giảm ${stats.bookingsDiff} chuyến xong`
                  ) : 'Kỳ so sánh gốc'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Financial Charts Container */}
        <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Donut Chart: Cash Flow & Revenue Allocation */}
          {(chartView === 'both' || chartView === 'donut') && (
            <div
              className={`${chartView === 'both' ? 'lg:col-span-5' : 'lg:col-span-12'
                } flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-gradient-to-br from-slate-50/60 via-white to-white p-5 shadow-xs`}
            >
              <div>
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Cơ cấu phân bổ dòng tiền</h3>
                    <p className="text-[11px] text-slate-500">
                      Tỷ trọng dòng tiền trong hệ thống — {currentPeriodLabel}
                    </p>
                  </div>
                  <span className="rounded-lg bg-emerald-50 px-2 py-1 text-[11px] font-semibold text-emerald-700 border border-emerald-100">
                    Phân bổ %
                  </span>
                </div>

                {/* SVG Donut */}
                <div className="relative my-4 flex items-center justify-center">
                  <svg className="h-48 w-48 -rotate-90 transform sm:h-52 sm:w-52" viewBox="0 0 160 160">
                    <defs>
                      <linearGradient id="donutCommission" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#10b981" />
                        <stop offset="100%" stopColor="#059669" />
                      </linearGradient>
                      <linearGradient id="donutPayout" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#6366f1" />
                        <stop offset="100%" stopColor="#4f46e5" />
                      </linearGradient>
                      <linearGradient id="donutEscrow" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#f59e0b" />
                        <stop offset="100%" stopColor="#d97706" />
                      </linearGradient>
                    </defs>

                    {/* Background track */}
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      stroke="#f1f5f9"
                      strokeWidth="18"
                      fill="transparent"
                    />

                    {/* Segment 1: Commission (Hoa hồng sàn) */}
                    {pctCommission > 0 && (
                      <circle
                        cx="80"
                        cy="80"
                        r="60"
                        stroke="url(#donutCommission)"
                        strokeWidth={activeSegment === 'commission' ? 22 : 18}
                        fill="transparent"
                        strokeDasharray={`${dashCommission} ${circumference}`}
                        strokeDashoffset={0}
                        strokeLinecap="round"
                        className="transition-all duration-300 cursor-pointer"
                        onMouseEnter={() => setActiveSegment('commission')}
                        onMouseLeave={() => setActiveSegment(null)}
                      />
                    )}

                    {/* Segment 2: Payout (Trả chủ xe) */}
                    {pctPayout > 0 && (
                      <circle
                        cx="80"
                        cy="80"
                        r="60"
                        stroke="url(#donutPayout)"
                        strokeWidth={activeSegment === 'payout' ? 22 : 18}
                        fill="transparent"
                        strokeDasharray={`${dashPayout} ${circumference}`}
                        strokeDashoffset={-dashCommission}
                        strokeLinecap="round"
                        className="transition-all duration-300 cursor-pointer"
                        onMouseEnter={() => setActiveSegment('payout')}
                        onMouseLeave={() => setActiveSegment(null)}
                      />
                    )}

                    {/* Segment 3: Escrow (Quỹ cọc lưu ký) */}
                    {pctEscrow > 0 && (
                      <circle
                        cx="80"
                        cy="80"
                        r="60"
                        stroke="url(#donutEscrow)"
                        strokeWidth={activeSegment === 'escrow' ? 22 : 18}
                        fill="transparent"
                        strokeDasharray={`${dashEscrow} ${circumference}`}
                        strokeDashoffset={-(dashCommission + dashPayout)}
                        strokeLinecap="round"
                        className="transition-all duration-300 cursor-pointer"
                        onMouseEnter={() => setActiveSegment('escrow')}
                        onMouseLeave={() => setActiveSegment(null)}
                      />
                    )}
                  </svg>

                  {/* Center Stat Overlay */}
                  <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                      {centerTitle}
                    </span>
                    <span className="text-xl font-black tracking-tight text-slate-900">
                      {centerValue}
                    </span>
                    <span className="mt-0.5 text-[11px] font-bold text-emerald-600">
                      {centerSub}
                    </span>
                  </div>
                </div>
              </div>

              {/* Legend with Progress Bars */}
              <div className="space-y-2.5 border-t border-slate-100 pt-3">
                {/* Legend Row 1: Commission */}
                <div
                  onMouseEnter={() => setActiveSegment('commission')}
                  onMouseLeave={() => setActiveSegment(null)}
                  className={`cursor-pointer rounded-xl p-2 transition-all ${activeSegment === 'commission' ? 'bg-emerald-50/80 ring-1 ring-emerald-300' : 'hover:bg-slate-50'
                    }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 font-semibold text-slate-700">
                      <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                      Hoa hồng sàn (Doanh thu)
                    </span>
                    <span className="font-bold text-slate-900">{formatVND(commission)}</span>
                  </div>
                  <div className="mt-1.5 flex items-center gap-2">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-emerald-500 transition-all duration-500"
                        style={{ width: `${pctCommission}%` }}
                      />
                    </div>
                    <span className="text-[10px] font-bold text-emerald-700 w-10 text-right">
                      {pctCommission.toFixed(1)}%
                    </span>
                  </div>
                </div>

                {/* Legend Row 2: Payout */}
                <div
                  onMouseEnter={() => setActiveSegment('payout')}
                  onMouseLeave={() => setActiveSegment(null)}
                  className={`cursor-pointer rounded-xl p-2 transition-all ${activeSegment === 'payout' ? 'bg-indigo-50/80 ring-1 ring-indigo-300' : 'hover:bg-slate-50'
                    }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 font-semibold text-slate-700">
                      <span className="h-2.5 w-2.5 rounded-full bg-indigo-500" />
                      Đối soát trả Chủ xe (NCC)
                    </span>
                    <span className="font-bold text-slate-900">{formatVND(payout)}</span>
                  </div>
                  <div className="mt-1.5 flex items-center gap-2">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-indigo-500 transition-all duration-500"
                        style={{ width: `${pctPayout}%` }}
                      />
                    </div>
                    <span className="text-[10px] font-bold text-indigo-700 w-10 text-right">
                      {pctPayout.toFixed(1)}%
                    </span>
                  </div>
                </div>

                {/* Legend Row 3: Escrow */}
                <div
                  onMouseEnter={() => setActiveSegment('escrow')}
                  onMouseLeave={() => setActiveSegment(null)}
                  className={`cursor-pointer rounded-xl p-2 transition-all ${activeSegment === 'escrow' ? 'bg-amber-50/80 ring-1 ring-amber-300' : 'hover:bg-slate-50'
                    }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 font-semibold text-slate-700">
                      <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
                      Quỹ cọc đang tạm giữ
                    </span>
                    <div className="flex items-center gap-1.5">
                      {escrow === 0 && !isAllTime && (
                        <span className="rounded bg-slate-100 px-1.5 py-0.2 text-[10px] font-medium text-slate-500">
                          Hết cọc
                        </span>
                      )}
                      <span className="font-bold text-slate-900">{formatVND(escrow)}</span>
                    </div>
                  </div>
                  <div className="mt-1.5 flex items-center gap-2">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full bg-amber-500 transition-all duration-500"
                        style={{ width: `${pctEscrow}%` }}
                      />
                    </div>
                    <span className="text-[10px] font-bold text-amber-700 w-10 text-right">
                      {pctEscrow.toFixed(1)}%
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Bar Chart: Scale Comparison of Financial Metrics */}
          {(chartView === 'both' || chartView === 'bar') && (
            <div
              className={`${chartView === 'both' ? 'lg:col-span-7' : 'lg:col-span-12'
                } flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-gradient-to-br from-slate-50/60 via-white to-white p-5 shadow-xs`}
            >
              <div>
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">So sánh quy mô tài chính</h3>
                    <p className="text-[11px] text-slate-500">
                      Đối chiếu 4 chỉ số tài chính theo quy mô tiền tệ — {currentPeriodLabel}
                    </p>
                  </div>
                  <span className="rounded-lg bg-blue-50 px-2 py-1 text-[11px] font-semibold text-blue-700 border border-blue-100">
                    Cột so sánh (VND)
                  </span>
                </div>

                {/* Vertical Bar Chart Container */}
                <div className="relative mt-5 pt-8">
                  {/* Grid Lines */}
                  <div className="pointer-events-none absolute inset-x-0 bottom-12 top-8 flex flex-col justify-between">
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">100%</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">75%</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">50%</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">25%</span>
                    </div>
                    <div className="flex items-center border-b border-slate-200">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">0%</span>
                    </div>
                  </div>

                  {/* Bars Grid */}
                  <div className="relative z-10 grid h-52 grid-cols-4 gap-3 sm:gap-6 items-end px-4 sm:px-8 pb-3">
                    {barData.map((bar) => {
                      const isHovered = activeSegment === bar.id
                      return (
                        <div
                          key={bar.id}
                          onMouseEnter={() => setActiveSegment(bar.id)}
                          onMouseLeave={() => setActiveSegment(null)}
                          className="group relative flex h-full cursor-pointer flex-col items-center justify-end"
                        >
                          {/* Value on Top of Bar */}
                          <div
                            className={`mb-2 rounded-md px-1.5 py-0.5 text-center text-[11px] font-extrabold shadow-2xs transition-all duration-200 ${isHovered
                                ? 'scale-110 bg-slate-900 text-white'
                                : 'bg-white/95 text-slate-800 border border-slate-200/80'
                              }`}
                          >
                            {formatCompactVND(bar.value)}
                          </div>

                          {/* Bar Element */}
                          <div
                            className="relative w-full max-w-[56px] overflow-hidden rounded-t-xl transition-all duration-500 ease-out shadow-xs"
                            style={{
                              height: `${bar.heightPct}%`,
                              minHeight: bar.value > 0 ? '12px' : '3px',
                            }}
                          >
                            <div
                              className={`h-full w-full ${bar.value > 0 ? bar.barBg : 'bg-slate-200'
                                } transition-all duration-300 ${isHovered ? 'brightness-110 ring-2 ring-white/50' : 'opacity-90'
                                }`}
                            />
                          </div>

                          {/* Tooltip on Hover */}
                          {isHovered && (
                            <div className="absolute -top-10 z-20 whitespace-nowrap rounded-lg bg-slate-900 px-2.5 py-1 text-[11px] font-medium text-white shadow-lg pointer-events-none">
                              {bar.shortLabel}: {formatVND(bar.value)}
                              {bar.id === 'escrow' && bar.value === 0 && !isAllTime && ' (Không còn cọc giữ)'}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>

                {/* Bottom Bar Labels */}
                <div className="grid grid-cols-4 gap-2 border-t border-slate-100 pt-3 text-center sm:gap-4">
                  {barData.map((bar) => {
                    const isHovered = activeSegment === bar.id
                    return (
                      <div
                        key={bar.id}
                        onMouseEnter={() => setActiveSegment(bar.id)}
                        onMouseLeave={() => setActiveSegment(null)}
                        className={`cursor-pointer rounded-lg p-1.5 transition-all ${isHovered ? 'bg-slate-100/80 font-bold' : ''
                          }`}
                      >
                        <div className="flex items-center justify-center gap-1.5">
                          <span className={`h-2 w-2 rounded-full ${bar.dotColor}`} />
                          <span className="text-[11px] font-bold text-slate-800 truncate">
                            {bar.shortLabel}
                          </span>
                        </div>
                        <div className="mt-0.5 text-[10px] text-slate-500 hidden sm:block truncate">
                          {bar.sublabel}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>

              {/* Footer Note */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-50/80 px-3 py-2 text-[11px] text-slate-500 border border-slate-100">
                <span>
                  {isAllTime
                    ? 'Dữ liệu tính từ các giao dịch thanh toán thành công (PAID) và đơn COMPLETED toàn thời gian'
                    : `Dữ liệu tính cho ${currentPeriodLabel} — Đơn đã hoàn thành & cọc đang lưu ký`}
                </span>
                <span className="font-semibold text-slate-700">Quy mô lớn nhất: {formatCompactVND(maxBarValue)}</span>
              </div>
            </div>
          )}

          {/* Multi-Month Financial Scale & Trend Comparison Chart */}
          {chartView === 'trend' && (
            <div className="lg:col-span-12 flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-gradient-to-br from-slate-50/60 via-white to-white p-5 sm:p-6 shadow-xs">
              <div>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-slate-100 pb-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700">
                        <IconTrendingUp className="h-4 w-4" />
                      </span>
                      <h3 className="text-base font-bold text-slate-900">
                        Biểu đồ so sánh quy mô tài chính theo từng tháng
                      </h3>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      So sánh GMV &amp; Hoa hồng sàn qua các tháng gần nhất (MoM) — Bấm vào tháng để lọc chi tiết
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center gap-1.5 rounded-lg bg-sky-50 px-2.5 py-1 text-xs font-semibold text-sky-700 border border-sky-200">
                      <span className="h-2 w-2 rounded-full bg-sky-500" />
                      Tổng GMV
                    </span>
                    <span className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200">
                      <span className="h-2 w-2 rounded-full bg-emerald-500" />
                      Hoa hồng sàn (Doanh thu)
                    </span>
                    <span className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700 border border-indigo-200">
                      Tăng trưởng MoM
                    </span>
                  </div>
                </div>

                {/* 6-Month Comparative Scale Chart */}
                <div className="relative mt-6 pt-10 pb-4">
                  {/* Grid Lines */}
                  <div className="pointer-events-none absolute inset-x-0 bottom-16 top-10 flex flex-col justify-between">
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">100% ({formatCompactVND(maxTrendGmv)})</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">75%</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">50%</span>
                    </div>
                    <div className="flex items-center border-b border-dashed border-slate-200/80">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">25%</span>
                    </div>
                    <div className="flex items-center border-b border-slate-200">
                      <span className="pr-1 text-[9px] font-medium text-slate-400">0 ₫</span>
                    </div>
                  </div>

                  {/* Columns Grid */}
                  <div
                    className={`relative z-10 grid gap-3 sm:gap-4 items-end px-2 sm:px-6 ${
                      chronologicalMonths.length === 1
                        ? 'grid-cols-1 max-w-xs mx-auto'
                        : chronologicalMonths.length === 2
                          ? 'grid-cols-2 max-w-md mx-auto'
                          : chronologicalMonths.length === 3
                            ? 'grid-cols-2 sm:grid-cols-3'
                            : chronologicalMonths.length === 4
                              ? 'grid-cols-2 sm:grid-cols-4'
                              : chronologicalMonths.length === 5
                                ? 'grid-cols-2 sm:grid-cols-3 md:grid-cols-5'
                                : 'grid-cols-2 sm:grid-cols-3 md:grid-cols-6'
                    }`}
                  >
                    {chronologicalMonths.map((m) => {
                      const rowKey = `${m.year}-${String(m.month).padStart(2, '0')}`
                      const isSelected = selectedMonthKey === rowKey
                      const gmvVal = Number(m.grossMerchandiseValue ?? 0)
                      const commVal = Number(m.platformCommission ?? 0)
                      const gmvHeight = maxTrendGmv > 0 ? Math.max(Math.round((gmvVal / maxTrendGmv) * 100), 8) : 8
                      const commHeight = maxTrendGmv > 0 ? Math.max(Math.round((commVal / maxTrendGmv) * 100), 5) : 5

                      return (
                        <div
                          key={rowKey}
                          onClick={() => onMonthChange?.(rowKey)}
                          className={`group relative flex flex-col items-center justify-end rounded-xl p-2 transition-all cursor-pointer ${isSelected
                              ? 'bg-brand-50/60 ring-2 ring-brand-500 shadow-sm'
                              : 'hover:bg-slate-50/80'
                            }`}
                        >
                          {/* Top Growth Rate Tag */}
                          <div className="mb-2 flex flex-col items-center">
                            {m.commissionGrowthRate != null ? (
                              <GrowthBadge
                                rate={m.commissionGrowthRate}
                                diff={m.commissionDiff}
                                isCurrency={true}
                                size="xs"
                              />
                            ) : (
                              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[9px] font-semibold text-slate-500">
                                Kỳ gốc
                              </span>
                            )}
                          </div>

                          {/* Dual Bars: GMV & Commission */}
                          <div className="flex h-56 items-end justify-center gap-1.5 sm:gap-2 w-full">
                            {/* GMV Bar */}
                            <div className="flex flex-col items-center h-full justify-end flex-1 max-w-[36px]">
                              <span className="mb-1 text-[10px] font-bold text-sky-800 opacity-90 truncate group-hover:scale-105 transition-transform">
                                {formatCompactVND(gmvVal)}
                              </span>
                              <div
                                style={{ height: `${gmvHeight}%` }}
                                className="w-full rounded-t-lg bg-gradient-to-t from-sky-600 via-sky-500 to-sky-400 transition-all duration-500 shadow-xs group-hover:brightness-110"
                                title={`Tổng GMV: ${formatVND(gmvVal)}`}
                              />
                            </div>

                            {/* Commission Bar */}
                            <div className="flex flex-col items-center h-full justify-end flex-1 max-w-[36px]">
                              <span className="mb-1 text-[10px] font-bold text-emerald-800 opacity-90 truncate group-hover:scale-105 transition-transform">
                                {formatCompactVND(commVal)}
                              </span>
                              <div
                                style={{ height: `${commHeight}%` }}
                                className="w-full rounded-t-lg bg-gradient-to-t from-emerald-600 via-emerald-500 to-teal-400 transition-all duration-500 shadow-xs group-hover:brightness-110"
                                title={`Hoa hồng sàn: ${formatVND(commVal)}`}
                              />
                            </div>
                          </div>

                          {/* Month Bottom Label */}
                          <div className="mt-3 text-center border-t border-slate-100 pt-2 w-full">
                            <div className="text-xs font-bold text-slate-800 truncate">
                              {m.label}
                            </div>
                            <div className="mt-0.5 text-[10px] font-medium text-slate-500 flex items-center justify-center gap-1">
                              <span>{m.completedBookings} chuyến</span>
                              {isSelected && (
                                <span className="rounded bg-brand-600 text-white px-1 py-0.2 text-[8px] font-extrabold">
                                  CHỌN
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>

              {/* Footer Note */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-50/80 px-4 py-2.5 text-xs text-slate-600 border border-slate-100">
                <span className="flex items-center gap-1.5">
                  <IconSparkles className="h-3.5 w-3.5 text-indigo-500" />
                  <span>
                    Dữ liệu đối soát tài chính theo từng tháng được tổng hợp tự động từ các đơn hàng thực tế trong cơ sở dữ liệu.
                  </span>
                </span>
                <span className="font-semibold text-slate-700">
                  GMV cao nhất: {formatCompactVND(maxTrendGmv)}
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Monthly Breakdown History Table */}
        {stats?.monthlySummaries && stats.monthlySummaries.length > 0 && (
          <div className="mt-8 border-t border-slate-100 pt-6">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between mb-4">
              <div>
                <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <span>Bảng đối soát tài chính theo từng tháng</span>
                  <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[10px] font-extrabold text-purple-700 border border-purple-200">
                    {stats.monthlySummaries.length} kỳ đối soát
                  </span>
                </h3>
                <p className="text-xs text-slate-500">
                  Đối chiếu quy mô và tăng trưởng theo từng tháng (MoM). Bấm "Xem biểu đồ" hoặc chọn tháng để lọc chi tiết.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setChartView(chartView === 'trend' ? 'both' : 'trend')}
                  className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-semibold transition-all shadow-2xs ${chartView === 'trend'
                      ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                      : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                    }`}
                >
                  <IconTrendingUp className="h-3.5 w-3.5 text-indigo-600" />
                  <span>{chartView === 'trend' ? 'Đóng biểu đồ xu hướng' : 'Xem biểu đồ xu hướng MoM'}</span>
                </button>
              </div>
            </div>

            <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-2xs">
              <table className="w-full text-left text-xs text-slate-700">
                <thead className="border-b border-slate-200 bg-slate-50/80 text-[11px] font-bold text-slate-600 uppercase tracking-wider">
                  <tr>
                    <th className="px-4 py-3">Kỳ đối soát</th>
                    <th className="px-4 py-3 text-right">Tổng GMV (MoM)</th>
                    <th className="px-4 py-3 text-right">Hoa hồng sàn (MoM)</th>
                    <th className="px-4 py-3 text-right">Trả Chủ xe</th>
                    <th className="px-4 py-3 text-right">Quỹ cọc lưu ký</th>
                    <th className="px-4 py-3 text-center">Chuyến xong</th>
                    <th className="px-4 py-3 text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {stats.monthlySummaries.map((m) => {
                    const rowKey = `${m.year}-${String(m.month).padStart(2, '0')}`
                    const isSelected = selectedMonthKey === rowKey
                    const hasEscrow = Number(m.escrowDeposits ?? 0) > 0

                    return (
                      <tr
                        key={rowKey}
                        className={`transition-colors ${isSelected ? 'bg-brand-50/40 font-semibold' : 'hover:bg-slate-50/70'
                          }`}
                      >
                        <td className="px-4 py-3 font-bold text-slate-900 flex items-center gap-2">
                          <IconCalendar className="h-3.5 w-3.5 text-brand-600" />
                          <span>{m.label}</span>
                          {isSelected && (
                            <span className="rounded-full bg-brand-600 text-white px-1.5 py-0.2 text-[9px] font-extrabold uppercase">
                              Đang xem
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-900">
                          <div>{formatVND(m.grossMerchandiseValue)}</div>
                          {m.gmvGrowthRate != null && (
                            <div className="mt-0.5 flex justify-end">
                              <GrowthBadge
                                rate={m.gmvGrowthRate}
                                diff={m.gmvDiff}
                                isCurrency={true}
                                size="xs"
                              />
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right font-semibold text-emerald-600">
                          <div>{formatVND(m.platformCommission)}</div>
                          {m.commissionGrowthRate != null && (
                            <div className="mt-0.5 flex justify-end">
                              <GrowthBadge
                                rate={m.commissionGrowthRate}
                                diff={m.commissionDiff}
                                isCurrency={true}
                                size="xs"
                              />
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right font-semibold text-indigo-600">
                          <div>{formatVND(m.providerPayoutDue)}</div>
                          {m.payoutGrowthRate != null && (
                            <div className="mt-0.5 flex justify-end">
                              <GrowthBadge
                                rate={m.payoutGrowthRate}
                                diff={m.payoutDiff}
                                isCurrency={true}
                                size="xs"
                              />
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {hasEscrow ? (
                            <span className="font-bold text-amber-700">{formatVND(m.escrowDeposits)}</span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-slate-400">
                              <span>0 ₫</span>
                              <span className="text-[10px] text-slate-400 font-normal">(Đã tất toán)</span>
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-center font-bold text-slate-800">
                          <div>{m.completedBookings}</div>
                          {m.bookingsGrowthRate != null && (
                            <div className="mt-0.5 flex justify-center">
                              <GrowthBadge
                                rate={m.bookingsGrowthRate}
                                diff={m.bookingsDiff}
                                isCurrency={false}
                                size="xs"
                              />
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-center">
                          {isSelected ? (
                            <button
                              type="button"
                              onClick={() => onMonthChange?.('ALL')}
                              className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-bold text-slate-600 hover:bg-slate-50 shadow-2xs"
                            >
                              Bỏ lọc
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => onMonthChange?.(rowKey)}
                              className="rounded-lg bg-brand-50 border border-brand-200/80 px-2.5 py-1 text-[11px] font-bold text-brand-700 hover:bg-brand-600 hover:text-white transition-all shadow-2xs"
                            >
                              Xem chi tiết
                            </button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* KPI Cards Grid */}
      <div>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-bold tracking-tight text-slate-900">Hàng đợi xử lý &amp; Phê duyệt</h3>
          <span className="text-xs text-slate-500">Bấm thẻ để chuyển nhanh đến màn hình tương ứng</span>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {kpis.map((kpi, idx) => {
            const Icon = kpi.icon
            return (
              <div
                key={idx}
                onClick={kpi.onClick}
                className={`group relative flex cursor-pointer flex-col justify-between overflow-hidden rounded-2xl border ${kpi.borderColor} bg-white p-5 shadow-card transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md`}
              >
                <div className={`absolute inset-0 bg-gradient-to-br ${kpi.bgGradient} opacity-60 pointer-events-none`} />

                <div className="relative z-10 flex items-start justify-between">
                  <div className="space-y-1">
                    <span className="text-xs font-semibold text-slate-500">{kpi.title}</span>
                    <div className="text-3xl font-extrabold tracking-tight text-slate-900">
                      {kpi.count}
                    </div>
                  </div>

                  <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${kpi.badgeBg}`}>
                    <Icon className="h-5 w-5" />
                  </span>
                </div>

                <div className="relative z-10 mt-5 flex items-center justify-between border-t border-slate-100 pt-3">
                  <span className="text-xs font-semibold text-slate-600 group-hover:text-brand-600 transition-colors">
                    {kpi.actionLabel}
                  </span>
                  <IconArrowRight className="h-4 w-4 text-slate-400 group-hover:translate-x-1 group-hover:text-brand-600 transition-all" />
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Quick Navigation Area */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-card">
        <h3 className="mb-4 text-base font-bold text-slate-900">Khu vực làm việc trực tiếp</h3>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <button
            type="button"
            onClick={() => onNavigate('users')}
            className="flex items-center gap-3 rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 text-left transition-all hover:border-brand-300 hover:bg-brand-50/40"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
              <IconUsers className="h-5 w-5" />
            </span>
            <div>
              <div className="text-xs font-bold text-slate-900">A02 — Người dùng &amp; Hồ sơ</div>
              <div className="text-[11px] text-slate-500">Khóa user, duyệt NCC, tài xế, eKYC</div>
            </div>
          </button>

          <button
            type="button"
            onClick={() => onNavigate('vehicles')}
            className="flex items-center gap-3 rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 text-left transition-all hover:border-brand-300 hover:bg-brand-50/40"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
              <IconCar className="h-5 w-5" />
            </span>
            <div>
              <div className="text-xs font-bold text-slate-900">A03 — Duyệt xe mở bán</div>
              <div className="text-[11px] text-slate-500">Kiểm định giấy tờ, duyệt độc lập</div>
            </div>
          </button>

          <button
            type="button"
            onClick={() => onNavigate('operations')}
            className="flex items-center gap-3 rounded-xl border border-slate-200/80 bg-slate-50/50 p-4 text-left transition-all hover:border-brand-300 hover:bg-brand-50/40"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-purple-100 text-purple-700">
              <IconClock className="h-5 w-5" />
            </span>
            <div>
              <div className="text-xs font-bold text-slate-900">A04 — Vận hành &amp; Hoàn tiền</div>
              <div className="text-[11px] text-slate-500">Đơn thuê, phân xử tranh chấp, hoàn tiền</div>
            </div>
          </button>
        </div>
      </div>
    </div>
  )
}
