import { useState } from 'react'
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

export default function AdminOverviewTab({ stats, loading, onNavigate, onRefresh }) {
  const [chartView, setChartView] = useState('both') // 'both' | 'donut' | 'bar'
  const [activeSegment, setActiveSegment] = useState(null)

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
      sublabel: `Thu từ ${completedBookings} chuyến xong`,
      value: commission,
      heightPct: Math.max(Math.round((commission / maxBarValue) * 100), 6),
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
      sublabel: 'Dòng tiền khách thanh toán',
      value: gmv,
      heightPct: Math.max(Math.round((gmv / maxBarValue) * 100), 6),
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
      heightPct: Math.max(Math.round((payout / maxBarValue) * 100), 6),
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
      sublabel: 'Đơn đang chạy / chờ hoàn',
      value: escrow,
      heightPct: Math.max(Math.round((escrow / maxBarValue) * 100), 6),
      color: 'amber',
      barBg: 'bg-gradient-to-t from-amber-600 via-amber-500 to-orange-400',
      pillBg: 'bg-amber-50 text-amber-800 border-amber-200',
      dotColor: 'bg-amber-500',
      textColor: 'text-amber-700',
      icon: IconClock,
    },
  ]

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
                <span>Xe mở bán: <strong className="text-slate-900">{stats?.approvedVehicles ?? 0}</strong>/{stats?.totalVehicles ?? 0}</span>
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-slate-50 px-2.5 py-1 border border-slate-200/70 font-medium">
                <IconUsers className="h-3.5 w-3.5 text-blue-600" />
                <span>Tài khoản hoạt động: <strong className="text-slate-900">{stats?.activeUsers ?? 0}</strong>/{stats?.totalUsers ?? 0}</span>
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            {/* View Mode Toggle */}
            <div className="inline-flex items-center rounded-xl bg-slate-100 p-1 text-xs font-medium text-slate-600 shadow-inner">
              <button
                type="button"
                onClick={() => setChartView('both')}
                className={`rounded-lg px-3 py-1.5 transition-all ${
                  chartView === 'both'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                }`}
              >
                Cả 2 biểu đồ
              </button>
              <button
                type="button"
                onClick={() => setChartView('donut')}
                className={`rounded-lg px-3 py-1.5 transition-all ${
                  chartView === 'donut'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                }`}
              >
                Cơ cấu (Tròn)
              </button>
              <button
                type="button"
                onClick={() => setChartView('bar')}
                className={`rounded-lg px-3 py-1.5 transition-all ${
                  chartView === 'bar'
                    ? 'bg-white font-semibold text-slate-900 shadow-xs'
                    : 'hover:text-slate-900'
                }`}
              >
                Quy mô (Cột)
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
                className={`group cursor-pointer rounded-xl border p-3.5 transition-all duration-200 ${
                  isHovered
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
                <div className="mt-1.5 text-lg font-black tracking-tight text-slate-900">
                  {formatVND(item.value)}
                </div>
                <div className="mt-1 text-[11px] text-slate-500 truncate">
                  {item.sublabel}
                </div>
              </div>
            )
          })}
        </div>

        {/* Financial Charts Container */}
        <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Donut Chart: Cash Flow & Revenue Allocation */}
          {(chartView === 'both' || chartView === 'donut') && (
            <div
              className={`${
                chartView === 'both' ? 'lg:col-span-5' : 'lg:col-span-12'
              } flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-gradient-to-br from-slate-50/60 via-white to-white p-5 shadow-xs`}
            >
              <div>
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Cơ cấu phân bổ dòng tiền</h3>
                    <p className="text-[11px] text-slate-500">Tỷ trọng dòng tiền trong hệ thống</p>
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
                      {activeSegment === 'commission'
                        ? 'Hoa hồng sàn'
                        : activeSegment === 'payout'
                        ? 'Đối soát NCC'
                        : activeSegment === 'escrow'
                        ? 'Quỹ cọc lưu ký'
                        : 'Tổng GMV Sàn'}
                    </span>
                    <span className="text-xl font-black tracking-tight text-slate-900">
                      {activeSegment === 'commission'
                        ? formatCompactVND(commission)
                        : activeSegment === 'payout'
                        ? formatCompactVND(payout)
                        : activeSegment === 'escrow'
                        ? formatCompactVND(escrow)
                        : formatCompactVND(gmv)}
                    </span>
                    <span className="mt-0.5 text-[11px] font-bold text-emerald-600">
                      {activeSegment === 'commission'
                        ? `${pctCommission.toFixed(1)}% cơ cấu`
                        : activeSegment === 'payout'
                        ? `${pctPayout.toFixed(1)}% cơ cấu`
                        : activeSegment === 'escrow'
                        ? `${pctEscrow.toFixed(1)}% cơ cấu`
                        : `${completedBookings} chuyến xong`}
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
                  className={`cursor-pointer rounded-xl p-2 transition-all ${
                    activeSegment === 'commission' ? 'bg-emerald-50/80 ring-1 ring-emerald-300' : 'hover:bg-slate-50'
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
                  className={`cursor-pointer rounded-xl p-2 transition-all ${
                    activeSegment === 'payout' ? 'bg-indigo-50/80 ring-1 ring-indigo-300' : 'hover:bg-slate-50'
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
                  className={`cursor-pointer rounded-xl p-2 transition-all ${
                    activeSegment === 'escrow' ? 'bg-amber-50/80 ring-1 ring-amber-300' : 'hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 font-semibold text-slate-700">
                      <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
                      Quỹ cọc đang tạm giữ
                    </span>
                    <span className="font-bold text-slate-900">{formatVND(escrow)}</span>
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
              className={`${
                chartView === 'both' ? 'lg:col-span-7' : 'lg:col-span-12'
              } flex flex-col justify-between rounded-2xl border border-slate-200/80 bg-gradient-to-br from-slate-50/60 via-white to-white p-5 shadow-xs`}
            >
              <div>
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">So sánh quy mô tài chính</h3>
                    <p className="text-[11px] text-slate-500">Đối chiếu 4 chỉ số tài chính theo quy mô tiền tệ</p>
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
                            className={`mb-2 rounded-md px-1.5 py-0.5 text-center text-[11px] font-extrabold shadow-2xs transition-all duration-200 ${
                              isHovered
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
                            }}
                          >
                            <div
                              className={`h-full w-full ${bar.barBg} transition-all duration-300 ${
                                isHovered ? 'brightness-110 ring-2 ring-white/50' : 'opacity-90'
                              }`}
                            />
                          </div>

                          {/* Tooltip on Hover */}
                          {isHovered && (
                            <div className="absolute -top-10 z-20 whitespace-nowrap rounded-lg bg-slate-900 px-2.5 py-1 text-[11px] font-medium text-white shadow-lg pointer-events-none">
                              {bar.shortLabel}: {formatVND(bar.value)}
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
                        className={`cursor-pointer rounded-lg p-1.5 transition-all ${
                          isHovered ? 'bg-slate-100/80 font-bold' : ''
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
              <div className="mt-4 flex items-center justify-between rounded-xl bg-slate-50/80 px-3 py-2 text-[11px] text-slate-500 border border-slate-100">
                <span>Dữ liệu tính từ các giao dịch thanh toán thành công (PAID) và đơn COMPLETED</span>
                <span className="font-semibold text-slate-700">Quy mô lớn nhất: {formatCompactVND(maxBarValue)}</span>
              </div>
            </div>
          )}
        </div>
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
