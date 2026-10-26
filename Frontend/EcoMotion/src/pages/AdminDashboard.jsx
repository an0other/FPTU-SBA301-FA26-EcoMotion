import { useState, useEffect } from 'react'
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

          {/* User Profile & Actions */}
          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="hidden rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition-all hover:bg-slate-50 sm:inline-block"
            >
              Về trang chủ
            </Link>

            <div className="flex items-center gap-2 rounded-full border border-slate-200/80 bg-slate-50 py-1 pl-1 pr-3">
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-purple-600 text-[11px] font-bold text-white">
                {initialsOf(user)}
              </span>
              <span className="max-w-[150px] truncate text-xs font-bold text-slate-800">
                {user?.fullName || user?.email}
              </span>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 transition-all hover:border-rose-200 hover:bg-rose-50"
            >
              Đăng xuất
            </button>
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
