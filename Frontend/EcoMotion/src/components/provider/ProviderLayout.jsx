import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

const NAV_ITEMS = [
  { label: 'Tổng quan', icon: 'grid_view' },
  { label: 'Xe của tôi', icon: 'directions_car' },
  { label: 'Tài xế', icon: 'badge' },
  { label: 'Đơn thuê', icon: 'local_taxi', to: '/provider/bookings' },
  { label: 'Chặn lịch', icon: 'event_busy' },
  { label: 'Tài chính', icon: 'account_balance_wallet', to: '/provider/finance' },
]

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function ProviderLayout({ active, children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  const businessName = user?.fullName || 'Nhà xe Hoàng Long Eco'

  return (
    <div className="min-h-screen bg-[#f4fbf7] text-slate-800">
      {/* Sidebar */}
      <aside className="fixed left-0 top-0 z-50 hidden h-screen w-60 flex-col justify-between border-r border-[#dbece0] bg-white shadow-[0_1px_8px_rgba(0,0,0,0.03)] md:flex">
        <div className="flex flex-col">
          <Link
            to="/provider/bookings"
            className="flex h-16 items-center gap-2.5 border-b border-[#e6f3ea] px-5"
          >
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#064e3b] text-white shadow-xs">
              <Icon name="electric_car" className="text-[20px]" />
            </span>
            <span className="flex flex-col">
              <span className="text-base font-bold leading-none tracking-tight text-slate-900">
                Eco<span className="text-[#059669]">Motion</span>
              </span>
              <span className="mt-1 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                Đối tác vận chuyển
              </span>
            </span>
          </Link>

          <div className="px-3 py-3">
            <span className="mb-1.5 block px-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              Vận hành
            </span>
            <nav aria-label="Điều hướng nhà cung cấp" className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) => {
                const isActive = item.label === active
                const base = 'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-all'
                if (item.to) {
                  return (
                    <Link
                      key={item.label}
                      to={item.to}
                      aria-current={isActive ? 'page' : undefined}
                      className={`${base} ${
                        isActive
                          ? 'bg-[#064e3b] font-semibold text-white shadow-xs'
                          : 'text-slate-600 hover:bg-[#eef8f2] hover:text-[#064e3b]'
                      }`}
                    >
                      <Icon
                        name={item.icon}
                        className={`text-[19px] ${isActive ? 'text-white' : 'text-slate-500'}`}
                      />
                      {item.label}
                    </Link>
                  )
                }
                return (
                  <span
                    key={item.label}
                    title="Đang được phát triển"
                    className={`${base} cursor-not-allowed text-slate-400/80 hover:bg-slate-50`}
                  >
                    <Icon name={item.icon} className="text-[19px] text-slate-400" />
                    {item.label}
                  </span>
                )
              })}
            </nav>
          </div>
        </div>

        <div className="border-t border-[#e6f3ea] bg-[#f9fdfa] p-3.5">
          <div className="flex items-center justify-between rounded-lg border border-[#d2ecd9] bg-white px-3 py-2 shadow-xs">
            <span className="flex items-center gap-2">
              <span className="h-2 w-2 animate-pulse rounded-full bg-[#10b981]" />
              <span className="text-[11px] font-semibold text-slate-600">Nền tảng điều phối</span>
            </span>
            <span className="rounded bg-[#eef8f2] px-1.5 py-0.5 font-mono text-[11px] font-bold text-[#064e3b]">
              VN-01
            </span>
          </div>
        </div>
      </aside>

      {/* Main Container */}
      <div className="md:pl-60">
        {/* Top Header */}
        <header className="fixed left-0 right-0 top-0 z-40 flex h-16 items-center justify-between border-b border-[#dbece0] bg-white/95 px-4 shadow-[0_1px_4px_rgba(0,0,0,0.03)] backdrop-blur-md sm:px-7 md:left-60">
          {/* Left search in header */}
          <div className="flex items-center gap-3">
            <div className="relative hidden sm:block">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <Icon name="search" className="text-[18px]" />
              </div>
              <input
                type="text"
                placeholder="Tìm chuyến xe, khách số, tài xế..."
                className="w-64 rounded-full border border-[#d2ecd9] bg-[#f2f9f5] py-1.5 pl-9 pr-3 text-xs text-slate-800 placeholder-slate-400 transition focus:border-[#10b981] focus:bg-white focus:outline-none lg:w-80"
              />
            </div>
          </div>

          {/* Right profile info & notification */}
          <div className="flex items-center gap-4">
            <button
              type="button"
              title="Thông báo"
              className="relative rounded-full p-1.5 text-slate-500 transition hover:bg-[#eef8f2] hover:text-[#064e3b]"
            >
              <Icon name="notifications" className="text-[20px]" />
              <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-red-500 ring-2 ring-white" />
            </button>

            <div className="flex items-center gap-3 border-l border-slate-200 pl-4">
              <div className="text-right">
                <div className="max-w-[200px] truncate text-[13px] font-bold text-slate-900 leading-tight">
                  {businessName}
                </div>
                <div className="text-[10px] font-semibold text-[#059669]">
                  Đối tác Doanh nghiệp 5Sao
                </div>
              </div>
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#064e3b] text-white shadow-xs">
                <Icon name="person" className="text-[18px]" />
              </span>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
              className="rounded-lg p-1.5 text-slate-400 transition hover:bg-red-50 hover:text-red-600"
            >
              <Icon name="logout" className="text-[18px]" />
            </button>
          </div>
        </header>

        {/* Content */}
        <main className="min-h-screen w-full px-4 pb-8 pt-20 sm:px-7">
          {children}

          {/* Footer */}
          <footer className="mt-10 flex flex-col items-center justify-between gap-3 border-t border-[#dbece0] pt-5 text-xs text-slate-500 md:flex-row">
            <span>© 2026 EcoMotion Chauffeur Platform. Nền tảng điều hành vận chuyển &amp; cho thuê xe có tài xế.</span>
            <div className="flex flex-wrap items-center gap-4 text-slate-600">
              <a href="#rules" className="transition hover:text-[#064e3b]">Quy chế hoạt động</a>
              <span>•</span>
              <a href="#partner-policy" className="transition hover:text-[#064e3b]">Chính sách đối tác</a>
              <span>•</span>
              <a href="#pricing" className="transition hover:text-[#064e3b]">Biểu phí</a>
              <span>•</span>
              <span className="font-medium text-slate-700">Hỗ trợ kỹ thuật: <strong className="text-[#064e3b]">1900 6868</strong></span>
            </div>
          </footer>
        </main>
      </div>
    </div>
  )
}

