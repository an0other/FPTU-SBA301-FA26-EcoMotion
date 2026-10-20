import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";

export default function ProviderHeader({
  activeTab = "home",
  providerName,
  depotAddress,
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  const displayName = providerName || user?.fullName || "EcoMotion Depot";
  const displayAddress = depotAddress || "Hệ thống Depot EcoMotion";
  const initials = user?.fullName
    ? user.fullName
        .trim()
        .split(/\s+/)
        .map((p) => p[0])
        .slice(-2)
        .join("")
        .toUpperCase()
    : "PV";

  return (
    <header className="sticky top-0 z-50 bg-white shadow-[0_1px_8px_rgba(11,28,48,0.04)] border-b border-slate-200">
      <div className="h-20 w-full px-6 lg:px-8 xl:px-12 flex items-center justify-between gap-4">
        {/* Left: Brand Logo & Depot Chip */}
        <div className="flex items-center gap-4 shrink-0">
          <Link to="/provider" className="flex items-center gap-3 shrink-0">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-md shadow-emerald-600/25">
              <span className="material-symbols-outlined text-[24px]">
                electric_car
              </span>
            </span>
            <div className="flex flex-col">
              <span className="text-[19px] font-bold text-[#0b1c30] leading-tight">
                EcoMotion VN
              </span>
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Provider Portal
              </span>
            </div>
          </Link>

          <div
            className="hidden md:flex items-center gap-2.5 px-3.5 py-1.5 bg-[#f0f4f9] rounded-xl border border-slate-200/60 max-w-[360px]"
            title={`${displayName} - ${displayAddress}`}
          >
            <span className="material-symbols-outlined text-emerald-600 text-[22px] shrink-0">
              storefront
            </span>
            <div className="flex flex-col text-left leading-tight truncate">
              <span className="font-bold text-slate-900 text-xs truncate">
                {displayName}
              </span>
              <span className="text-[11px] text-slate-500 font-normal truncate">
                {displayAddress}
              </span>
            </div>
          </div>
        </div>

        {/* Center: Main Navigation */}
        <nav className="hidden lg:flex items-center justify-center flex-1 mx-4 gap-1.5 xl:gap-2">
          <Link
            to="/provider"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "home"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Tổng quan
          </Link>
          <Link
            to="/provider/vehicles"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "vehicles"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Đội xe của tôi
          </Link>
          <Link
            to="/provider/schedules"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "schedules"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Chặn lịch
          </Link>
          <Link
            to="/provider/drivers"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "drivers"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Quản lý tài xế
          </Link>
          <Link
            to="/provider/bookings"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "bookings"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Đơn thuê
          </Link>
          <Link
            to="/provider/finance"
            className={`px-3.5 py-2 rounded-xl text-[13.5px] whitespace-nowrap transition-colors ${
              activeTab === "finance"
                ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
            }`}
          >
            Tài chính
          </Link>
        </nav>

        {/* Right: User Profile (Đã bỏ chuông thông báo) */}
        <div className="flex items-center shrink-0">
          <div className="relative">
            <button
              type="button"
              onClick={() => setUserMenuOpen((prev) => !prev)}
              className="flex items-center gap-2 p-1.5 rounded-xl hover:bg-slate-100 transition-colors"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-100 text-[12px] font-bold text-emerald-700 ring-2 ring-emerald-500/20">
                {initials}
              </span>
              <div className="hidden sm:flex flex-col text-left leading-tight">
                <span className="text-[13px] font-bold text-slate-800 max-w-[120px] truncate">
                  {user?.fullName || "Chủ xe"}
                </span>
                <span className="text-[11px] text-slate-500 font-medium">
                  Provider
                </span>
              </div>
              <span className="material-symbols-outlined text-slate-400 text-[18px]">
                expand_more
              </span>
            </button>

            {userMenuOpen && (
              <div className="absolute right-0 mt-2 w-52 rounded-xl bg-white p-1.5 shadow-xl border border-slate-100 z-50">
                <div className="px-3 py-2 text-xs text-slate-500 truncate">
                  {user?.email}
                </div>
                <div className="h-px bg-slate-100 my-1" />
                <Link
                  to="/me"
                  className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors"
                >
                  <span className="material-symbols-outlined text-[16px] text-slate-400">
                    account_circle
                  </span>
                  Hồ sơ cá nhân
                </Link>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold text-red-600 hover:bg-red-50 transition-colors"
                >
                  <span className="material-symbols-outlined text-[16px]">
                    logout
                  </span>
                  Đăng xuất
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
