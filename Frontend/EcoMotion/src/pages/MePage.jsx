import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { authService } from '../services/authService'
import { homeRouteForRole } from '../utils/roleHome'
import {
  IconCar,
  IconIdCard,
  IconMail,
  IconPhone,
  IconShield,
  IconUser,
} from '../components/icons/Icons'

const STATUS_LABEL = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ kích hoạt',
  LOCKED: 'Đã bị khoá',
}

const EKYC_LABEL = {
  UNVERIFIED: 'Chưa xác thực',
  PENDING: 'Đang chờ duyệt',
  VERIFIED: 'Đã xác thực',
  REJECTED: 'Bị từ chối',
}

const ROLE_LABEL = {
  RENTER: 'Người thuê',
  PROVIDER: 'Nhà cung cấp',
  DRIVER: 'Tài xế',
  ADMIN: 'Quản trị viên',
}

function ProfileRow({ icon: Icon, label, value, muted }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
      <dt className="flex items-center gap-2 text-sm font-medium text-slate-500">
        <Icon className="h-4 w-4 text-brand-600" />
        {label}
      </dt>
      <dd className={`text-sm font-semibold ${muted ? 'text-slate-400' : 'text-slate-900'}`}>
        {value || '—'}
      </dd>
    </div>
  )
}

function initialsOf(nameOrEmail) {
  const source = (nameOrEmail || '').trim()
  if (!source) return '?'
  const parts = source.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return source.slice(0, 2).toUpperCase()
}

export default function MePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const me = await authService.fetchMe()
        if (active) setProfile(me)
      } catch (err) {
        if (active) setError(err.message || 'Không tải được thông tin tài khoản.')
      } finally {
        if (active) setLoading(false)
      }
    }
    load()
    return () => {
      active = false
    }
  }, [])

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  const data = profile || user
  const backTo = homeRouteForRole(data?.role)

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-4">
        <Link to={backTo} className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-slate-900">
          <IconCar className="h-5 w-5 text-brand-600" />
          EcoMotion <span className="text-[11px] font-semibold text-brand-600">VN</span>
        </Link>
        <div className="flex items-center gap-2">
          <Link
            to={backTo}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            ← Quay lại
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            Đăng xuất
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-6 py-10">
        <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-card">
          <div className="mb-8 flex items-center gap-4">
            <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700">
              {initialsOf(data?.fullName || data?.email)}
            </span>
            <div className="min-w-0">
              <h1 className="truncate text-2xl font-bold tracking-tight text-slate-900">
                {data?.fullName || 'Người dùng EcoMotion'}
              </h1>
              <p className="mt-0.5 truncate text-sm text-slate-500">{data?.email}</p>
            </div>
          </div>

          {loading && <p className="text-sm text-slate-500">Đang tải thông tin…</p>}

          {!loading && (
            <dl className="grid gap-3">
              <ProfileRow icon={IconMail} label="Email" value={data?.email} />
              <ProfileRow
                icon={IconUser}
                label="Họ và tên"
                value={data?.fullName}
                muted={!data?.fullName}
              />
              <ProfileRow
                icon={IconPhone}
                label="Số điện thoại"
                value={data?.phone}
                muted={!data?.phone}
              />
              <ProfileRow icon={IconIdCard} label="Vai trò" value={ROLE_LABEL[data?.role] || data?.role} />
              <ProfileRow
                icon={IconShield}
                label="Trạng thái tài khoản"
                value={STATUS_LABEL[data?.status] || data?.status}
              />
              <ProfileRow
                icon={IconShield}
                label="Xác thực danh tính (eKYC)"
                value={EKYC_LABEL[data?.ekycStatus] || data?.ekycStatus}
              />
            </dl>
          )}

          {error && (
            <div className="mt-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-[13px] font-medium text-red-700">
              {error}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
