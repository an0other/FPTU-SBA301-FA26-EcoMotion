

import { NavLink } from 'react-router-dom'

const linkClass = ({ isActive }) =>
  `px-3.5 py-1.5 text-sm font-medium rounded-lg transition-all duration-180 cursor-pointer ${
    isActive
      ? 'bg-[#15803D] text-white font-semibold shadow-xs'
      : 'text-[#14221A]/70 hover:text-[#14221A] hover:bg-white/50'
  }`

export const Header = () => {
  return (
    <header className="sticky top-0 left-0 right-0 z-50 bg-white/95 backdrop-blur-md border-b border-[#E6EBE8]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-18 sm:h-20 flex items-center justify-between">
        {/* Brand logo & title */}
        <div className="flex items-center gap-6 sm:gap-8">
          <NavLink to="/renter/bookings/new" className="flex items-center gap-2.5 text-left group cursor-pointer">
            <div className="w-9 h-9 rounded-xl bg-[#E8F6EE] flex items-center justify-center text-[#15803D] group-hover:bg-[#15803D] group-hover:text-white transition-colors duration-180">
              <span className="material-symbols-outlined text-[22px]">electric_car</span>
            </div>
            <div className="flex flex-col">
              <span className="text-xl font-bold tracking-tight text-[#15803D] leading-none">
                EcoMotion
              </span>
              <span className="hidden sm:inline-block text-[11px] font-medium tracking-wide text-[#14221A]/60 uppercase mt-0.5">
                Thuê xe có tài xế
              </span>
            </div>
          </NavLink>

          {/* Navigation links */}
          <nav className="hidden md:flex items-center gap-1.5 bg-[#F7F8F6] p-1 rounded-xl border border-[#E6EBE8]">
            <NavLink to="/renter/bookings/new" className={linkClass}>Khám phá xe</NavLink>
            <NavLink to="/policy" className={linkClass}>Chính sách & Cọc</NavLink>
            <NavLink to="/renter/bookings" end className={linkClass}>Đơn của tôi</NavLink>
          </nav>
        </div>

        {/* User profile & Notification */}
        <div className="flex items-center gap-3">
          <button
            aria-label="Thông báo"
            className="w-10 h-10 rounded-full border border-[#E6EBE8] bg-white flex items-center justify-center text-[#14221A]/70 hover:text-[#14221A] hover:bg-[#F7F8F6] transition-colors relative cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[20px]">notifications</span>
            <span className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full bg-[#15803D] ring-2 ring-white"></span>
          </button>

          <div className="flex items-center gap-2.5 pl-1.5 pr-3.5 py-1 rounded-full border border-[#E6EBE8] bg-white shadow-xs">
            <div className="w-8 h-8 rounded-full bg-[#15803D] text-white flex items-center justify-center text-xs font-semibold">
              <span className="material-symbols-outlined text-[18px]">person</span>
            </div>
            <div className="hidden sm:flex flex-col text-left">
              <span className="text-xs font-semibold text-[#14221A]">Renter 01</span>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};
