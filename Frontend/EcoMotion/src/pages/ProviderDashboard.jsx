import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { IconCar } from '../components/icons/Icons'

export default function ProviderDashboard() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-slate-900">
          <IconCar className="h-5 w-5 text-emerald-600" />
          EcoMotion <span className="text-[11px] font-semibold text-emerald-600">Provider</span>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/me"
            className="rounded-lg border border-slate-200 bg-white px-4.5 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            Trang của tôi
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-200 bg-white px-4.5 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            Đăng xuất
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-6 py-10">
        <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-card">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-slate-100 pb-6 mb-6">
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Trang nhà cung cấp xe (Provider)</h1>
              <p className="mt-1 text-sm text-slate-500">Quản lý các hoạt động cho thuê và đội xe của bạn.</p>
            </div>
            <Link
              to="/provider/bookings"
              className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-700 shadow-sm"
            >
              Xem danh sách đơn (Booking Queue) →
            </Link>
          </div>

          <dl className="grid gap-3">
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Email</dt>
              <dd className="font-semibold text-slate-900">{user?.email}</dd>
            </div>
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Vai trò</dt>
              <dd>
                <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-emerald-700">
                  {user?.role}
                </span>
              </dd>
            </div>
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Trạng thái tài khoản</dt>
              <dd className="font-semibold text-slate-900">{user?.status}</dd>
            </div>
          </dl>
        </div>
      </main>
    </div>
  )
}
