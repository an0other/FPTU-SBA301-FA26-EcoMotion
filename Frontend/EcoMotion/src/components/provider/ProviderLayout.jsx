import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

const NAV_ITEMS = [
  { label: 'Tổng quan', icon: 'grid_view' },
  { label: 'Xe của tôi', icon: 'directions_car' },
  { label: 'Tài xế', icon: 'badge' },
  { label: 'Đơn thuê', icon: 'local_taxi', to: '/provider/bookings' },
  { label: 'Chặn lịch', icon: 'event_busy' },
  { label: 'Tài chính', icon: 'account_balance_wallet' },
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

  return (
    <div className="min-h-screen bg-[#effdf3] text-on-surface">
      <aside className="fixed left-0 top-0 z-50 hidden h-screen w-60 flex-col justify-between border-r border-outline-variant/35 bg-surface-container-lowest shadow-[0_1px_8px_rgba(0,0,0,0.04)] md:flex">
        <div className="flex flex-col">
          <Link
            to="/provider"
            className="flex h-16 items-center gap-2 border-b border-outline-variant/20 px-5"
          >
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-on-primary shadow-sm">
              <Icon name="electric_car" className="text-[22px]" />
            </span>
            <span className="flex flex-col">
              <span className="text-base font-semibold leading-none tracking-tight">
                Eco<span className="text-primary">Motion</span>
              </span>
              <span className="mt-0.5 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">
                Đối tác vận chuyển
              </span>
            </span>
          </Link>

          <div className="px-3.5 py-2">
            <span className="mb-1 block px-2 text-[11px] font-semibold uppercase text-on-surface-variant/80">
              Vận hành
            </span>
            <nav aria-label="Điều hướng nhà cung cấp" className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) => {
                const isActive = item.label === active
                const base = 'flex items-center gap-2 rounded-lg px-2 py-2 text-sm font-medium'
                if (item.to) {
                  return (
                    <Link
                      key={item.label}
                      to={item.to}
                      aria-current={isActive ? 'page' : undefined}
                      className={`${base} transition-colors ${
                        isActive
                          ? 'bg-primary-container font-semibold text-on-primary-container shadow-sm'
                          : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                      }`}
                    >
                      <Icon name={item.icon} className="text-[20px]" />
                      {item.label}
                    </Link>
                  )
                }
                return (
                  <span
                    key={item.label}
                    title="Đang được phát triển"
                    className={`${base} cursor-not-allowed text-on-surface-variant/50`}
                  >
                    <Icon name={item.icon} className="text-[20px]" />
                    {item.label}
                  </span>
                )
              })}
            </nav>
          </div>
        </div>

        <div className="border-t border-outline-variant/25 bg-surface-container-low/40 p-3.5">
          <div className="flex items-center justify-between rounded-lg border border-outline-variant/40 bg-surface-container-lowest p-2">
            <span className="flex items-center gap-2">
              <span className="h-2 w-2 animate-pulse rounded-full bg-primary-container" />
              <span className="text-[11px] font-semibold text-on-surface-variant">Sẵn sàng điều phối</span>
            </span>
            <span className="font-mono-badge text-xs font-semibold text-primary">VN-01</span>
          </div>
        </div>
      </aside>

      <div className="md:pl-60">
        <header className="fixed left-0 right-0 top-0 z-40 flex h-16 items-center justify-between border-b border-outline-variant/35 bg-surface-container-lowest/90 px-4 shadow-[0_1px_8px_rgba(0,0,0,0.04)] backdrop-blur-md sm:px-7 md:left-60">
          <Link to="/provider/bookings" className="text-sm font-semibold text-primary md:hidden">
            Đơn thuê
          </Link>
          <span className="hidden md:block" />
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-3">
              <div className="hidden text-right sm:block">
                <div className="max-w-[240px] truncate text-[15px] font-semibold leading-tight">
                  {user?.fullName || user?.email}
                </div>
                <div className="text-[11px] font-semibold text-on-surface-variant">Đối tác vận chuyển</div>
              </div>
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-on-primary">
                <Icon name="person" className="text-[18px]" />
              </span>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              aria-label="Đăng xuất"
              title="Đăng xuất"
              className="rounded-lg p-2 text-on-surface-variant transition-colors hover:bg-error-container hover:text-on-error-container"
            >
              <Icon name="logout" className="text-[20px]" />
            </button>
          </div>
        </header>

        <main className="min-h-screen w-full px-4 pb-7 pt-[88px] sm:px-7">
          {children}
          <footer className="mt-2 flex flex-col items-center justify-between gap-3 border-t border-outline-variant/30 py-4 text-xs text-on-surface-variant md:flex-row">
            <span>© 2026 EcoMotion Chauffeur Platform. Nền tảng điều hành vận chuyển &amp; cho thuê xe có tài xế.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}
