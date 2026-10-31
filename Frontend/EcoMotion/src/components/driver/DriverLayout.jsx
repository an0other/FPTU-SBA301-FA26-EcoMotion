import { Link, useNavigate } from 'react-router-dom'
import DriverAvailability from './DriverAvailability'
import { useAuth } from '../../hooks/useAuth'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function DriverLayout({ activeTab = 'schedule', children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  const driverName = user?.fullName || 'Tài xế EcoMotion'
  const driverEmail = user?.email || ''

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800 flex flex-col">
      {/* Top Header */}
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white px-4 py-3 shadow-xs sm:px-6">
        <div className="mx-auto flex max-w-4xl items-center justify-between">
          <Link to="/driver/schedule" className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white shadow-xs">
              <Icon name="commute" className="text-[20px]" />
            </span>
            <div>
              <span className="flex items-center gap-1.5">
                <span className="text-base font-bold tracking-tight text-slate-900">
                  Eco<span className="text-brand-600">Motion</span>
                </span>
                <span className="rounded bg-teal-100 px-1.5 py-0.2 text-[10px] font-bold text-teal-800">
                  Tài xế
                </span>
              </span>
              <span className="block text-[11px] text-slate-400">Điều hành chuyến xe</span>
            </div>
          </Link>

          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2.5 border-r border-slate-200 pr-3">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-brand-700">
                {driverName.charAt(0).toUpperCase()}
              </span>
              <div className="text-right">
                <div className="text-xs font-semibold text-slate-800 leading-tight">
                  {driverName}
                </div>
                <div className="text-[10px] text-slate-400 truncate max-w-[150px]">
                  {driverEmail}
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              title="Đăng xuất"
              className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition hover:bg-slate-100 hover:text-red-600"
            >
              <Icon name="logout" className="text-[16px]" />
              <span className="hidden sm:inline">Đăng xuất</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 mx-auto w-full max-w-4xl px-4 py-5 pb-24 sm:px-6 sm:pb-8">
        <DriverAvailability />
        {children}
      </main>

      {/* Mobile Bottom Navigation Bar */}
      <nav
        aria-label="Điều hướng tài xế di động"
        className="fixed bottom-0 left-0 right-0 z-40 border-t border-slate-200 bg-white px-4 py-2 sm:hidden shadow-xs"
      >
        <div className="flex items-center justify-around">
          <Link
            to="/driver/schedule"
            className={`flex flex-col items-center gap-0.5 text-xs font-medium ${
              activeTab === 'schedule' ? 'text-brand-600 font-bold' : 'text-slate-500'
            }`}
          >
            <Icon name="calendar_month" className="text-[22px]" />
            <span>Lịch trình</span>
          </Link>
          <Link
            to="/me"
            className={`flex flex-col items-center gap-0.5 text-xs font-medium ${
              activeTab === 'profile' ? 'text-brand-600 font-bold' : 'text-slate-500'
            }`}
          >
            <Icon name="person" className="text-[22px]" />
            <span>Tài khoản</span>
          </Link>
        </div>
      </nav>
    </div>
  )
}
