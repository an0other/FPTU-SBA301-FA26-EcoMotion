import { NavLink } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

const linkClass = ({ isActive }) =>
  `px-4 py-2 text-sm font-semibold rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 ${
    isActive
      ? 'bg-brand-600 text-white'
      : 'text-slate-600 hover:bg-slate-100 hover:text-brand-700'
  }`

export function BookingSubnav() {
  const { user } = useAuth()

  return (
    <div className="sticky top-20 z-20 border-b border-slate-200 bg-white">
      <nav
        aria-label="Điều hướng đặt xe"
        className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-center gap-3 px-4 py-2.5 sm:px-6"
      >
        <NavLink to="/" end className={linkClass}>
          Khám phá xe
        </NavLink>
        <NavLink to="/policy" className={linkClass}>
          Chính sách &amp; Cọc
        </NavLink>
        {user?.role === 'RENTER' ? (
          <NavLink to="/renter/bookings" end className={linkClass}>
            Đơn của tôi
          </NavLink>
        ) : null}
      </nav>
    </div>
  )
}
