import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { adminService } from '../services/adminService'
import AdminOverviewTab from './admin/AdminOverviewTab'
import AdminUsersTab from './admin/AdminUsersTab'
import AdminVehiclesTab from './admin/AdminVehiclesTab'
import AdminOperationsTab from './admin/AdminOperationsTab'
import {
  IconZap,
  IconUsers,
  IconCar,
  IconClock,
  IconShield,
  IconChevronDown,
  IconUser,
  IconX,
} from '../components/icons/Icons'

function initialsOf(user) {
  const source = user?.fullName?.trim() || user?.email || ''
  if (!source) return 'AD'
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

export default function AdminDashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [activeTab, setActiveTab] = useState('overview') // 'overview' | 'users' | 'vehicles' | 'operations'
  const [tabOptions, setTabOptions] = useState({})
  const [stats, setStats] = useState(null)
  const [loadingStats, setLoadingStats] = useState(false)
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false)
  const profileDropdownRef = useRef(null)

  useEffect(() => {
    function handleClickOutside(event) {
      if (profileDropdownRef.current && !profileDropdownRef.current.contains(event.target)) {
        setProfileDropdownOpen(false)
      }
    }
    if (profileDropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [profileDropdownOpen])

  useEffect(() => {
    loadStats()
  }, [])

  async function loadStats() {
    setLoadingStats(true)
    try {
      const data = await adminService.getDashboardStats()
      setStats(data)
    } catch (err) {
      console.error('Failed to load admin stats:', err)
    } finally {
      setLoadingStats(false)
    }
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  function handleNavigate(targetTab, options = {}) {
    setActiveTab(targetTab)
    setTabOptions(options)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const pendingUsersCount =
    (stats?.pendingProviders ?? 0) + (stats?.pendingDrivers ?? 0) + (stats?.pendingEkyc ?? 0)
  const pendingVehiclesCount = stats?.pendingVehicles ?? 0
  const openDisputesCount = stats?.openDisputes ?? 0

  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      {/* Top Navigation Header */}
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 shadow-xs backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          {/* Logo & Portal title */}
          <div className="flex items-center gap-3">
            <Link to="/" className="flex items-center gap-2.5">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-600 text-white shadow-md shadow-brand-600/25">
                <IconZap className="h-4.5 w-4.5" />
              </span>
              <span className="flex items-center gap-1.5 font-bold tracking-tight text-slate-900">
                EcoMotion
                <span className="rounded-md bg-purple-100 px-1.5 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-purple-800">
                  Admin Portal
                </span>
              </span>
            </Link>
          </div>

          {/* User Profile Dropdown & Actions */}
          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="hidden rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-all hover:bg-slate-50 sm:inline-block"
            >
              Về trang chủ
            </Link>

            {/* Admin User Info Dropdown */}
            <div ref={profileDropdownRef} className="relative">
              <button
                type="button"
                onClick={() => setProfileDropdownOpen((prev) => !prev)}
                aria-expanded={profileDropdownOpen}
                aria-haspopup="true"
                className={`flex items-center gap-2 rounded-full border py-1 pl-1 pr-2.5 transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                  profileDropdownOpen
                    ? 'border-slate-300 bg-slate-100 shadow-xs'
                    : 'border-slate-200/80 bg-slate-50 hover:border-slate-300 hover:bg-slate-100/80'
                }`}
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-purple-600 text-[11px] font-bold text-white shadow-xs">
                  {initialsOf(user)}
                </span>
                <span className="max-w-[150px] truncate text-xs font-bold text-slate-800">
                  {user?.fullName || user?.email}
                </span>
                <IconChevronDown
                  className={`h-3.5 w-3.5 text-slate-400 transition-transform duration-150 ${
                    profileDropdownOpen ? 'rotate-180' : ''
                  }`}
                />
              </button>

              {profileDropdownOpen && (
                <div className="absolute right-0 top-full z-50 mt-1.5 w-56 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
                  <div className="border-b border-slate-100 bg-slate-50/70 px-3.5 py-2.5">
                    <p className="text-[10px] font-bold uppercase tracking-wider text-purple-700">
                      Quản trị viên
                    </p>
                    <p className="mt-0.5 truncate text-xs font-bold text-slate-800">
                      {user?.fullName || 'Admin User'}
                    </p>
                    {user?.email && (
                      <p className="truncate text-[11px] text-slate-500">{user.email}</p>
                    )}
                  </div>

                  <div className="p-1.5 space-y-0.5">
                    <Link
                      to="/"
                      onClick={() => setProfileDropdownOpen(false)}
                      className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
                    >
                      <IconZap className="h-4 w-4 text-slate-400" />
                      <span>Về trang chủ</span>
                    </Link>

                    <Link
                      to="/me"
                      onClick={() => setProfileDropdownOpen(false)}
                      className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
                    >
                      <IconUser className="h-4 w-4 text-slate-400" />
                      <span>Trang cá nhân</span>
                    </Link>

                    <div className="my-1 border-t border-slate-100" />

                    <button
                      type="button"
                      onClick={() => {
                        setProfileDropdownOpen(false)
                        handleLogout()
                      }}
                      className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-xs font-semibold text-rose-600 hover:bg-rose-50 transition-colors"
                    >
                      <IconX className="h-4 w-4 text-rose-500" />
                      <span>Đăng xuất</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Tab Selection Bar */}
        <div className="border-t border-slate-100 bg-slate-50/50">
          <div className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 sm:px-6">
            <button
              type="button"
              onClick={() => handleNavigate('overview')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-xs font-bold transition-all whitespace-nowrap ${
                activeTab === 'overview'
                  ? 'border-brand-600 text-brand-700 bg-white shadow-xs'
                  : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              <IconShield className="h-4 w-4" />
              <span>A01 — Tổng quan (Overview)</span>
            </button>

            <button
              type="button"
              onClick={() => handleNavigate('users')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-xs font-bold transition-all whitespace-nowrap ${
                activeTab === 'users'
                  ? 'border-brand-600 text-brand-700 bg-white shadow-xs'
                  : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              <IconUsers className="h-4 w-4" />
              <span>A02 — Người dùng &amp; Hồ sơ</span>
              {pendingUsersCount > 0 && (
                <span className="rounded-full bg-amber-500 px-1.5 py-0.2 text-[10px] font-extrabold text-white">
                  {pendingUsersCount}
                </span>
              )}
            </button>

            <button
              type="button"
              onClick={() => handleNavigate('vehicles')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-xs font-bold transition-all whitespace-nowrap ${
                activeTab === 'vehicles'
                  ? 'border-brand-600 text-brand-700 bg-white shadow-xs'
                  : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              <IconCar className="h-4 w-4" />
              <span>A03 — Duyệt xe mở bán</span>
              {pendingVehiclesCount > 0 && (
                <span className="rounded-full bg-amber-500 px-1.5 py-0.2 text-[10px] font-extrabold text-white">
                  {pendingVehiclesCount}
                </span>
              )}
            </button>

            <button
              type="button"
              onClick={() => handleNavigate('operations')}
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-xs font-bold transition-all whitespace-nowrap ${
                activeTab === 'operations'
                  ? 'border-brand-600 text-brand-700 bg-white shadow-xs'
                  : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              <IconClock className="h-4 w-4" />
              <span>A04 — Vận hành &amp; Hoàn tiền</span>
              {openDisputesCount > 0 && (
                <span className="rounded-full bg-rose-500 px-1.5 py-0.2 text-[10px] font-extrabold text-white">
                  {openDisputesCount}
                </span>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Main Body Content */}
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
        {activeTab === 'overview' && (
          <AdminOverviewTab
            stats={stats}
            loading={loadingStats}
            onRefresh={loadStats}
            onNavigate={handleNavigate}
          />
        )}

        {activeTab === 'users' && (
          <AdminUsersTab
            initialSubTab={tabOptions.subTab || 'users'}
            initialFilter={tabOptions}
            onStatsUpdate={loadStats}
          />
        )}

        {activeTab === 'vehicles' && (
          <AdminVehiclesTab
            initialStatus={tabOptions.status || 'ALL'}
            onStatsUpdate={loadStats}
          />
        )}

        {activeTab === 'operations' && (
          <AdminOperationsTab
            initialSubTab={tabOptions.subTab || 'bookings'}
            initialFilter={tabOptions}
            onStatsUpdate={loadStats}
          />
        )}
      </main>
    </div>
  )
}
