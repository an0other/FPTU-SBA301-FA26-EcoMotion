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
} from '../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

export default function AdminOverviewTab({ stats, loading, onNavigate, onRefresh }) {
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
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-2xl border border-brand-200/80 bg-gradient-to-br from-brand-50 via-white to-slate-50 p-6 sm:p-8 shadow-card">
        <div className="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-white px-3 py-1 text-xs font-semibold text-brand-700 shadow-xs mb-3">
              <IconSparkles className="h-3.5 w-3.5 text-brand-600" />
              <span>Cổng quản trị vận hành EcoMotion</span>
            </div>
            <h2 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              Trung tâm điều hành TV6
            </h2>
            <p className="mt-1 max-w-2xl text-sm text-slate-600">
              Kiểm soát phê duyệt hồ sơ người dùng, duyệt phương tiện mở bán, thụ lý khiếu nại tranh chấp và giám sát dòng tiền giao dịch.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-slate-600">
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-white/90 px-2.5 py-1 border border-slate-200/60 shadow-2xs">
                <IconCar className="h-3.5 w-3.5 text-emerald-600" />
                <span>Xe mở bán: <strong className="text-slate-900">{stats?.approvedVehicles ?? 0}</strong>/{stats?.totalVehicles ?? 0}</span>
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-lg bg-white/90 px-2.5 py-1 border border-slate-200/60 shadow-2xs">
                <IconUsers className="h-3.5 w-3.5 text-blue-600" />
                <span>Tài khoản hoạt động: <strong className="text-slate-900">{stats?.activeUsers ?? 0}</strong>/{stats?.totalUsers ?? 0}</span>
              </span>
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-3">
            <button
              type="button"
              onClick={onRefresh}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 shadow-xs transition-all hover:border-slate-300 hover:bg-slate-50 active:scale-95"
            >
              Làm mới số liệu
            </button>
          </div>
        </div>

        {/* Financial Highlights */}
        <div className="mt-6 grid grid-cols-1 gap-4 border-t border-brand-100 pt-6 sm:grid-cols-2 lg:grid-cols-4">
          {/* Card 1: Doanh thu sàn (Hoa hồng thực nhận) */}
          <div className="rounded-2xl border border-emerald-200/80 bg-gradient-to-br from-emerald-50/80 via-white to-white p-4.5 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-emerald-800">Doanh thu sàn (Hoa hồng)</span>
              <span className="rounded-lg bg-emerald-100 p-1.5 text-emerald-700">
                <IconWallet className="h-4 w-4" />
              </span>
            </div>
            <div className="mt-2 text-2xl font-black text-emerald-950">
              {formatVND(stats?.platformCommission)}
            </div>
            <p className="mt-1 text-[11px] font-medium text-emerald-700">
              Thu từ {stats?.completedBookings ?? 0} chuyến hoàn tất
            </p>
          </div>

          {/* Card 2: Tổng dòng tiền qua sàn (GMV) */}
          <div className="rounded-2xl border border-slate-200/80 bg-white/90 p-4.5 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700">Tổng giao dịch (GMV)</span>
              <span className="rounded-lg bg-blue-50 p-1.5 text-blue-600">
                <IconSparkles className="h-4 w-4" />
              </span>
            </div>
            <div className="mt-2 text-2xl font-black text-slate-900">
              {formatVND(stats?.grossMerchandiseValue ?? stats?.totalRevenue)}
            </div>
            <p className="mt-1 text-[11px] text-slate-500">Toàn bộ dòng tiền khách đã thanh toán</p>
          </div>

          {/* Card 3: Tiền đối soát cho Chủ xe (Provider Share) */}
          <div className="rounded-2xl border border-slate-200/80 bg-white/90 p-4.5 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700">Đối soát trả Chủ xe</span>
              <span className="rounded-lg bg-indigo-50 p-1.5 text-indigo-600">
                <IconShield className="h-4 w-4" />
              </span>
            </div>
            <div className="mt-2 text-2xl font-black text-indigo-950">
              {formatVND(stats?.providerPayoutDue)}
            </div>
            <p className="mt-1 text-[11px] text-slate-500">Tiền thuê NCC nhận sau trừ hoa hồng</p>
          </div>

          {/* Card 4: Quỹ tiền cọc đang tạm giữ (Escrow Deposit) */}
          <div className="rounded-2xl border border-slate-200/80 bg-white/90 p-4.5 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700">Quỹ cọc đang tạm giữ</span>
              <span className="rounded-lg bg-amber-50 p-1.5 text-amber-600">
                <IconClock className="h-4 w-4" />
              </span>
            </div>
            <div className="mt-2 text-2xl font-black text-amber-950">
              {formatVND(stats?.escrowDeposits)}
            </div>
            <p className="mt-1 text-[11px] text-slate-500">Tiền cọc đơn đang chạy / chờ hoàn</p>
          </div>
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
