import { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { providerService } from "../../services/providerService";

const NAV_ITEMS = [
  { key: "home", to: "/provider", label: "Tổng quan", labelFull: "Tổng quan", icon: "dashboard" },
  { key: "vehicles", to: "/provider/vehicles", label: "Đội xe", labelFull: "Đội xe của tôi", icon: "directions_car" },
  { key: "schedules", to: "/provider/schedules", label: "Chặn lịch", labelFull: "Chặn lịch", icon: "event_busy" },
  { key: "drivers", to: "/provider/drivers", label: "Tài xế", labelFull: "Quản lý tài xế", icon: "badge" },
  { key: "bookings", to: "/provider/bookings", label: "Đơn thuê", labelFull: "Đơn thuê", icon: "assignment" },
  { key: "finance", to: "/provider/finance", label: "Tài chính", labelFull: "Tài chính", icon: "payments" },
];

export default function ProviderHeader({
  activeTab,
  providerName,
  depotAddress,
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation(); 
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [prevPathname, setPrevPathname] = useState(location.pathname);

  if (prevPathname !== location.pathname) {
    setPrevPathname(location.pathname);
    setUserMenuOpen(false);
    setMobileNavOpen(false);
  }

  const currentTab = (() => {
    if (activeTab) return activeTab;
    const path = location.pathname;
    if (path.startsWith("/provider/vehicles")) return "vehicles";
    if (path.startsWith("/provider/schedules")) return "schedules";
    if (path.startsWith("/provider/drivers")) return "drivers";
    if (path.startsWith("/provider/bookings")) return "bookings";
    if (path.startsWith("/provider/finance")) return "finance";
    if (path === "/provider" || path === "/provider/") return "home";
    return "home";
  })();

  const [depotInfo, setDepotInfo] = useState(() => {
    try {
      const cached = sessionStorage.getItem("ecomotion_provider_depot");
      if (cached) {
        const parsed = JSON.parse(cached);
        if (parsed.name && parsed.name !== "Chủ xe") return parsed;
      }
    } catch {}
    return {
      name: providerName && providerName !== "Chủ xe" ? providerName : "",
      address: depotAddress || "",
    };
  });

  useEffect(() => {
    if (providerName && providerName !== "Chủ xe" && depotAddress) {
      const info = { name: providerName, address: depotAddress };
      try {
        sessionStorage.setItem(
          "ecomotion_provider_depot",
          JSON.stringify(info)
        );
      } catch {}
      return;
    }

    let isMounted = true;
    async function fetchDepot() {
      try {
        const data = await providerService.getDashboardSummary();
        if (isMounted && data) {
          const info = {
            name: data.providerName || user?.fullName || "EcoMotion Depot",
            address: data.depotAddress || "Hệ thống Depot EcoMotion",
          };
          setDepotInfo(info);
          try {
            sessionStorage.setItem("ecomotion_provider_depot", JSON.stringify(info));
          } catch {}
        }
      } catch (err) {
        console.error("Failed to auto-fetch depot info:", err);
      }
    }
    void fetchDepot();
    return () => {
      isMounted = false;
    };
  }, [providerName, depotAddress, user?.fullName]);

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  const depotDisplayName =
    (providerName && providerName !== "Chủ xe" ? providerName : null) ||
    (depotInfo.name && depotInfo.name !== "Chủ xe" ? depotInfo.name : null) ||
    "EcoMotion Depot";
  const displayAddress =
    depotAddress || depotInfo.address || "Hệ thống Depot EcoMotion";

  const userDisplayName =
    (user?.fullName && user.fullName.trim() !== "" && user.fullName !== "Chủ xe")
      ? user.fullName.trim()
      : (user?.email || "Chủ xe");

  const initials = (() => {
    const parts = userDisplayName.split(/\s+/).filter(Boolean);
    if (parts.length === 0) return "CX";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  })();

  return (
    <header className="sticky top-0 z-40 bg-white shadow-[0_1px_8px_rgba(11,28,48,0.04)] border-b border-slate-200">
      <div className="h-16 sm:h-20 w-full px-4 sm:px-6 lg:px-8 xl:px-12 flex items-center justify-between gap-2 sm:gap-4">
        
        {/* Brand & Depot Info */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0 min-w-0">
          <Link to="/provider" className="flex items-center gap-2 sm:gap-2.5 shrink-0">
            <span className="flex h-9 w-9 sm:h-10 sm:w-10 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-md shadow-emerald-600/25 shrink-0">
              <span className="material-symbols-outlined text-[22px] sm:text-[24px]">
                electric_car
              </span>
            </span>
            <div className="flex flex-col">
              <span className="text-[16px] sm:text-[18px] font-bold text-[#0b1c30] leading-tight">
                EcoMotion VN
              </span>
              <span className="text-[9.5px] sm:text-[10.5px] font-semibold text-slate-400 uppercase tracking-wider">
                Provider Portal
              </span>
            </div>
          </Link>

          {/* Depot Badge: constrained with ellipsis & tooltip */}
          <div
            className="hidden sm:flex items-center gap-2 px-2.5 sm:px-3 py-1.5 bg-[#f0f4f9] rounded-xl border border-slate-200/70 max-w-[140px] lg:max-w-[170px] xl:max-w-[220px] 2xl:max-w-[280px] shrink min-w-0"
            title={`${depotDisplayName} - ${displayAddress}`}
          >
            <span className="material-symbols-outlined text-emerald-600 text-[18px] sm:text-[20px] shrink-0">
              storefront
            </span>
            <div className="flex flex-col text-left leading-tight min-w-0 flex-1 overflow-hidden">
              <span className="font-bold text-slate-900 text-xs truncate">
                {depotDisplayName}
              </span>
              <span className="hidden xl:block text-[10.5px] text-slate-500 font-normal truncate">
                {displayAddress}
              </span>
            </div>
          </div>
        </div>

        {/* Desktop Navigation Menu */}
        <nav
          aria-label="Điều hướng Provider"
          className="hidden md:flex items-center justify-center gap-1 xl:gap-1.5 min-w-0 shrink-0"
        >
          {NAV_ITEMS.map((item) => {
            const isActive = currentTab === item.key;
            return (
              <Link
                key={item.key}
                to={item.to}
                className={`px-2 xl:px-3.5 py-1.5 xl:py-2 rounded-xl text-xs xl:text-[13px] whitespace-nowrap transition-colors flex items-center gap-1.5 ${
                  isActive
                    ? "bg-emerald-50 text-emerald-700 font-bold shadow-2xs ring-1 ring-emerald-600/15"
                    : "text-slate-600 hover:text-slate-900 hover:bg-slate-100 font-medium"
                }`}
              >
                <span className="xl:hidden">{item.label}</span>
                <span className="hidden xl:inline">{item.labelFull}</span>
              </Link>
            );
          })}
        </nav>

        {/* Right Section: User Profile & Mobile Nav Toggle */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          <div className="relative">
            <button
              type="button"
              onClick={() => setUserMenuOpen((prev) => !prev)}
              className="flex items-center gap-1.5 sm:gap-2 p-1 sm:p-1.5 rounded-xl hover:bg-slate-100 transition-colors"
              aria-label="Tài khoản người dùng"
            >
              <span className="flex h-8 w-8 sm:h-9 sm:w-9 items-center justify-center rounded-full bg-emerald-100 text-[12px] font-bold text-emerald-700 ring-2 ring-emerald-500/20 shrink-0">
                {initials}
              </span>
              <div className="hidden sm:flex flex-col text-left leading-tight min-w-0">
                <span
                  className="text-[12.5px] font-bold text-slate-800 max-w-[90px] xl:max-w-[130px] truncate"
                  title={userDisplayName}
                >
                  {userDisplayName}
                </span>
                <span className="text-[10.5px] text-slate-500 font-medium">
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

          <button
            type="button"
            onClick={() => setMobileNavOpen((prev) => !prev)}
            className="md:hidden flex items-center justify-center h-10 w-10 rounded-xl text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition-colors"
            aria-label="Mở menu điều hướng"
          >
            <span className="material-symbols-outlined text-[24px]">menu</span>
          </button>
        </div>
      </div>

      {mobileNavOpen && (
        <div className="fixed inset-0 z-50 md:hidden flex justify-end">
          
          <div
            className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs transition-opacity"
            onClick={() => setMobileNavOpen(false)}
          />

          <div className="relative w-full max-w-[320px] sm:max-w-[360px] bg-white h-full shadow-2xl flex flex-col z-10 animate-in slide-in-from-right duration-200">
            
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-600 text-white shadow-xs">
                  <span className="material-symbols-outlined text-[20px]">
                    electric_car
                  </span>
                </span>
                <span className="font-bold text-slate-900 text-base">
                  EcoMotion Provider
                </span>
              </div>
              <button
                type="button"
                onClick={() => setMobileNavOpen(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
                aria-label="Đóng menu"
              >
                <span className="material-symbols-outlined text-[22px]">close</span>
              </button>
            </div>

            <div className="p-4 border-b border-slate-100 bg-slate-50/70">
              <div className="flex items-start gap-2.5">
                <span className="material-symbols-outlined text-emerald-600 text-[22px] shrink-0 mt-0.5">
                  storefront
                </span>
                <div className="flex flex-col text-left leading-tight min-w-0">
                  <span className="font-bold text-slate-900 text-xs truncate">
                    {depotDisplayName}
                  </span>
                  <span className="text-[11px] text-slate-500 font-normal mt-0.5 line-clamp-2">
                    {displayAddress}
                  </span>
                </div>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-1">
              <div className="px-3 py-1.5 text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                Menu Quản lý
              </div>
              {NAV_ITEMS.map((item) => {
                const isActive = currentTab === item.key;
                return (
                  <Link
                    key={item.key}
                    to={item.to}
                    onClick={() => setMobileNavOpen(false)}
                    className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                      isActive
                        ? "bg-emerald-50 text-emerald-700 font-bold shadow-xs"
                        : "text-slate-700 hover:bg-slate-50 hover:text-slate-900"
                    }`}
                  >
                    <span
                      className={`material-symbols-outlined text-[20px] ${
                        isActive ? "text-emerald-600" : "text-slate-400"
                      }`}
                    >
                      {item.icon}
                    </span>
                    <span className="flex-1">{item.labelFull}</span>
                    {isActive && (
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-600" />
                    )}
                  </Link>
                );
              })}
            </div>

            <div className="p-4 border-t border-slate-100 bg-slate-50/50 space-y-3">
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-[13px] font-bold text-emerald-700 ring-2 ring-emerald-500/20 shrink-0">
                  {initials}
                </span>
                <div className="flex flex-col text-left min-w-0 flex-1 leading-tight">
                  <span className="font-bold text-slate-900 text-sm truncate">
                    {userDisplayName}
                  </span>
                  <span className="text-xs text-slate-500 truncate mt-0.5">
                    {user?.email}
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 pt-1">
                <Link
                  to="/me"
                  onClick={() => setMobileNavOpen(false)}
                  className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 transition-colors"
                >
                  <span className="material-symbols-outlined text-[16px] text-slate-500">
                    account_circle
                  </span>
                  Hồ sơ
                </Link>
                <button
                  type="button"
                  onClick={() => {
                    setMobileNavOpen(false);
                    handleLogout();
                  }}
                  className="flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-red-600 bg-red-50/60 border border-red-100 hover:bg-red-50 transition-colors"
                >
                  <span className="material-symbols-outlined text-[16px]">
                    logout
                  </span>
                  Đăng xuất
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
