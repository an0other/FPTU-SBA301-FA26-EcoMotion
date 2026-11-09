import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import {
  IconArrowRight,
  IconIdCard,
  IconMail,
  IconPhone,
  IconShield,
  IconUser,
  IconWallet,
} from '../components/icons/Icons'
import { useAuth } from '../hooks/useAuth'
import { authService } from '../services/authService'
import { yearlyRentalService } from '../services/yearlyRentalService'

const STATUS_LABEL = {
  ACTIVE: 'Đang hoạt động',
  PENDING: 'Chờ kích hoạt',
  LOCKED: 'Đã bị khóa',
}

const EKYC_LABEL = {
  UNVERIFIED: 'Chưa xác thực',
  PENDING: 'Đang chờ duyệt',
  VERIFIED: 'Đã xác thực',
  REJECTED: 'Bị từ chối',
}

const ROLE_LABEL = {
  RENTER: 'Khách thuê',
  PROVIDER: 'Nhà cung cấp',
  DRIVER: 'Tài xế',
  ADMIN: 'Quản trị viên',
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

function formatLicenseDate(value) {
  if (!value) return '—'
  const date = new Date(`${value}T00:00:00+07:00`)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'medium',
      timeZone: 'Asia/Ho_Chi_Minh',
    }).format(date)
}

function isLicenseExpired(license) {
  if (license.licenseExpiryUnlimited || !license.licenseExpiry) return false
  const expiry = new Date(`${license.licenseExpiry}T23:59:59+07:00`)
  return Number.isNaN(expiry.getTime()) || expiry.getTime() < Date.now()
}

function hasKnownLicenseTerm(license) {
  return Boolean(license.licenseExpiryUnlimited || license.licenseExpiry)
}

function ProfileRow({ icon: Icon, label, value, muted = false }) {
  return (
    <div className="flex min-w-0 items-start gap-3 border-b border-slate-100 py-4 last:border-b-0">
      <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
        <Icon className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <dt className="text-xs font-medium text-slate-500">{label}</dt>
        <dd className={`mt-1 break-words text-sm font-semibold ${muted ? 'text-slate-400' : 'text-slate-900'}`}>
          {value || 'Chưa cập nhật'}
        </dd>
      </div>
    </div>
  )
}

function StatusCard({ icon: Icon, label, value, tone = 'brand' }) {
  const tones = {
    brand: 'bg-brand-50 text-brand-800 ring-brand-100',
    slate: 'bg-slate-50 text-slate-700 ring-slate-200',
  }

  return (
    <div className={`rounded-xl p-4 ring-1 ${tones[tone]}`}>
      <div className="flex items-center gap-2 text-xs font-semibold opacity-80">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <p className="mt-2 text-sm font-bold">{value || 'Chưa có thông tin'}</p>
    </div>
  )
}

export default function MePage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [license, setLicense] = useState(null)
  const [licenseLoading, setLicenseLoading] = useState(true)
  const [licenseError, setLicenseError] = useState('')

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

  const data = profile || user

  useEffect(() => {
    if (user?.role !== 'RENTER') return undefined

    let active = true
    yearlyRentalService.getDrivingLicense()
      .then((result) => {
        if (active) {
          setLicense(result)
          setLicenseError('')
        }
      })
      .catch((err) => {
        if (active) {
          setLicenseError(err.message || 'Không tải được thông tin GPLX.')
        }
      })
      .finally(() => {
        if (active) setLicenseLoading(false)
      })

    return () => {
      active = false
    }
  }, [user?.role])

  const licenseExpired = license ? isLicenseExpired(license) : false
  const licenseCurrent = Boolean(
    license?.licenseVerified && hasKnownLicenseTerm(license) && !licenseExpired,
  )
  const licenseStatus = !license?.licenseNo
    ? 'Chưa có GPLX'
    : licenseExpired
      ? 'Đã hết hạn'
      : license.licenseVerified
        ? license.licenseExpiryUnlimited
          ? 'Đã đọc · không thời hạn'
          : license.licenseExpiry
            ? 'Đã đọc · còn hạn'
            : 'Đã đọc · chưa rõ thời hạn'
        : 'Chưa xác minh qua ảnh'

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />

      <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        <nav aria-label="Đường dẫn trang" className="mb-6 flex items-center gap-2 text-xs">
          <Link to="/" className="font-medium text-slate-500 hover:text-brand-700">
            Trang chủ
          </Link>
          <span aria-hidden="true" className="text-slate-300">/</span>
          <span aria-current="page" className="font-semibold text-slate-800">
            Hồ sơ của tôi
          </span>
        </nav>

        <div className="mb-6">
          {['RENTER', 'PROVIDER'].includes(user?.role) && <Link to="/identity/accounts" className="mb-3 inline-block rounded-xl border border-brand-200 bg-white px-4 py-2 text-sm font-semibold text-brand-700">Liên kết tài khoản Renter / Provider →</Link>}
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-brand-700">
            Tài khoản EcoMotion
          </p>
          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Hồ sơ của tôi
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-500">
            Xem thông tin cá nhân, vai trò và trạng thái xác thực của tài khoản.
          </p>
        </div>

        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,0.8fr)]">
          <section
            aria-labelledby="profile-information-heading"
            className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
          >
            <div className="flex items-center gap-4 border-b border-slate-100 bg-gradient-to-r from-brand-50/80 to-white p-5 sm:p-7">
              <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-brand-100 text-xl font-bold text-brand-800 ring-4 ring-white">
                {initialsOf(data?.fullName || data?.email)}
              </span>
              <div className="min-w-0">
                <h2 className="truncate text-lg font-bold text-slate-900 sm:text-xl">
                  {data?.fullName || 'Người dùng EcoMotion'}
                </h2>
                <p className="mt-1 truncate text-sm text-slate-500">{data?.email}</p>
                <span className="mt-2 inline-flex rounded-full border border-brand-200 bg-white px-2.5 py-1 text-[11px] font-semibold text-brand-800">
                  {ROLE_LABEL[data?.role] || data?.role || 'Thành viên'}
                </span>
              </div>
            </div>

            <div className="px-5 sm:px-7">
              <div className="flex items-center justify-between gap-3 border-b border-slate-100 py-4">
                <h2 id="profile-information-heading" className="text-sm font-bold text-slate-900">
                  Thông tin cá nhân
                </h2>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-500">
                  Chỉ xem
                </span>
              </div>

              {loading ? (
                <div role="status" className="space-y-4 py-5">
                  <span className="sr-only">Đang tải hồ sơ…</span>
                  {[0, 1, 2].map((item) => (
                    <div key={item} className="h-12 animate-pulse rounded-lg bg-slate-100" />
                  ))}
                </div>
              ) : (
                <dl>
                  <ProfileRow icon={IconUser} label="Họ và tên" value={data?.fullName} muted={!data?.fullName} />
                  <ProfileRow icon={IconMail} label="Email" value={data?.email} />
                  <ProfileRow icon={IconPhone} label="Số điện thoại" value={data?.phone} muted={!data?.phone} />
                  <ProfileRow icon={IconIdCard} label="Vai trò" value={ROLE_LABEL[data?.role] || data?.role} />
                </dl>
              )}
            </div>
          </section>

          <aside className="space-y-5">
            <section aria-labelledby="account-status-heading" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
              <h2 id="account-status-heading" className="text-sm font-bold text-slate-900">
                Trạng thái tài khoản
              </h2>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">
                Thông tin trạng thái và xác thực hiện tại.
              </p>
              {loading ? (
                <div role="status" className="mt-4 h-24 animate-pulse rounded-xl bg-slate-100">
                  <span className="sr-only">Đang tải trạng thái…</span>
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  <StatusCard
                    icon={IconShield}
                    label="Tài khoản"
                    value={STATUS_LABEL[data?.status] || data?.status}
                    tone="brand"
                  />
                  <StatusCard
                    icon={IconIdCard}
                    label="Xác thực danh tính (eKYC)"
                    value={EKYC_LABEL[data?.ekycStatus] || data?.ekycStatus}
                    tone="slate"
                  />
                </div>
              )}
            </section>

            {data?.role === 'RENTER' && (
              <section
                aria-labelledby="driving-license-heading"
                className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"
              >
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
                    <IconIdCard className="h-5 w-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <h2 id="driving-license-heading" className="text-sm font-bold text-slate-900">
                      Giấy phép lái xe
                    </h2>
                    <p className="mt-1 text-xs leading-relaxed text-slate-500">
                      Lưu GPLX đã đọc được và còn hiệu lực. Hạng bằng sẽ được đối chiếu với xe và thời hạn thuê khi đăng ký gói năm.
                    </p>
                  </div>
                </div>

                {licenseLoading ? (
                  <div role="status" className="mt-4 h-20 animate-pulse rounded-xl bg-slate-100">
                    <span className="sr-only">Đang tải thông tin GPLX…</span>
                  </div>
                ) : licenseError ? (
                  <p role="alert" className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-xs text-red-700">
                    {licenseError}
                  </p>
                ) : license?.licenseNo ? (
                  <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-xs font-medium text-slate-500">Trạng thái GPLX</span>
                      <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                        licenseCurrent
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}>
                        {licenseStatus}
                      </span>
                    </div>
                    <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                      <div>
                        <dt className="text-xs text-slate-500">Hạng bằng đã đọc</dt>
                        <dd className="mt-1 font-semibold text-slate-900">{license.licenseClass || 'Chưa đọc được'}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-slate-500">Thời hạn</dt>
                        <dd className="mt-1 font-semibold text-slate-900">
                          {license.licenseExpiryUnlimited
                            ? 'Không thời hạn'
                            : license.licenseExpiry
                              ? `Đến ${formatLicenseDate(license.licenseExpiry)}`
                              : 'Chưa có thông tin'}
                        </dd>
                      </div>
                    </dl>
                    {license.licenseVerified && (
                      <p className="mt-3 text-xs leading-relaxed text-slate-600">
                        {licenseCurrent
                          ? 'GPLX đã được đọc và còn hiệu lực. Hạng bằng sẽ được dùng để đối chiếu điều kiện của gói năm bạn chọn.'
                          : 'Hệ thống đã đọc được GPLX. Khi đăng ký gói năm, hệ thống sẽ đối chiếu hạng bằng và thời hạn với điều kiện thuê.'}
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
                    Bạn chưa tải GPLX lên hồ sơ.
                  </p>
                )}

                <Link
                  to="/renter/yearly-rental"
                  className="mt-4 flex items-center justify-between gap-3 rounded-xl border border-brand-200 px-3.5 py-3 text-sm font-semibold text-brand-800 transition hover:bg-brand-50"
                >
                  {license?.licenseNo ? 'Quản lý GPLX' : 'Thêm GPLX'}
                  <IconArrowRight className="h-4 w-4" />
                </Link>
              </section>
            )}

            {data?.role === 'RENTER' && (
              <section aria-label="Lối tắt khách thuê" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
                <h2 className="text-sm font-bold text-slate-900">Quản lý thuê xe</h2>
                <p className="mt-1 text-xs leading-relaxed text-slate-500">
                  Truy cập nhanh đơn thuê và các khoản đã thanh toán.
                </p>
                <div className="mt-4 space-y-2">
                  <Link
                    to="/renter/bookings"
                    className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-3.5 py-3 text-sm font-semibold text-slate-700 transition hover:border-brand-200 hover:bg-brand-50 hover:text-brand-800"
                  >
                    Đơn thuê của tôi
                    <IconArrowRight className="h-4 w-4" />
                  </Link>
                  <Link
                    to="/renter/payments"
                    className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-3.5 py-3 text-sm font-semibold text-slate-700 transition hover:border-brand-200 hover:bg-brand-50 hover:text-brand-800"
                  >
                    <span className="flex items-center gap-2">
                      <IconWallet className="h-4 w-4" />
                      Lịch sử giao dịch
                    </span>
                    <IconArrowRight className="h-4 w-4" />
                  </Link>
                </div>
              </section>
            )}
          </aside>
        </div>

        {error && (
          <div role="alert" className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            {error}
          </div>
        )}
      </main>
    </div>
  )
}
