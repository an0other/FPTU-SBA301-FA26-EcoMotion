import { useState } from 'react'
import { IconX, IconLock, IconCheckCircle, IconUsers, IconShield, IconEye } from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'

const ROLE_LABELS = {
  RENTER: 'Khách thuê (RENTER)',
  PROVIDER: 'Chủ xe / NCC (PROVIDER)',
  DRIVER: 'Tài xế (DRIVER)',
  ADMIN: 'Quản trị viên (ADMIN)',
}

const STATUS_BADGES = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  VERIFIED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
}

function resolveValidImageUrl(url) {
  if (!url || typeof url !== 'string') return null
  if (/^\/images\/(portrait|license)-\d+\.webp$/i.test(url.trim())) return null
  return url
}

export default function UserDetailModal({
  user,
  onClose,
  onToggleStatus,
  actionLoading,
  onPreviewImage,
  onViewDriverDetail,
  onViewProviderDetail,
  onViewEkycDetail,
}) {
  const [avatarError, setAvatarError] = useState(false)
  if (!user) return null

  const validPortraitUrl = resolveValidImageUrl(user.portraitUrl)
  const validAvatarUrl = resolveValidImageUrl(user.avatarUrl)
  const validLicenseUrl = resolveValidImageUrl(user.licenseImageUrl)
  const displayAvatar = !avatarError ? (validPortraitUrl || validAvatarUrl) : null
  const isDriver = user.role === 'DRIVER' || Boolean(user.portraitUrl || user.licenseImageUrl || user.licenseNo)

  return (
    <AdminModalShell
      maxWidth={isDriver ? 'max-w-5xl' : 'max-w-2xl'}
      header={(
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div
              className={`relative h-12 w-12 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-2xs ${
                displayAvatar ? 'cursor-pointer hover:ring-2 hover:ring-brand-500' : ''
              }`}
              onClick={() => displayAvatar && onPreviewImage && onPreviewImage(displayAvatar)}
              title={displayAvatar ? 'Bấm để xem ảnh phóng to' : ''}
            >
              {displayAvatar ? (
                <img
                  src={displayAvatar}
                  alt={user.fullName || 'Avatar'}
                  className="h-full w-full object-cover transition-transform duration-200 hover:scale-105"
                  onError={() => setAvatarError(true)}
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center text-slate-400">
                  <IconUsers className="h-6 w-6" />
                </div>
              )}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
                  {user.fullName || 'Người dùng hệ thống'}
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    STATUS_BADGES[user.status] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {user.status}
                </span>
              </div>
              <p className="text-xs text-slate-500">
                User #{user.id} • Vai trò: <strong>{ROLE_LABELS[user.role] || user.role}</strong>
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <IconX className="h-5 w-5" />
          </button>
        </div>
      )}
      footer={(
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Đóng
            </button>
            {isDriver && onViewDriverDetail && (
              <button
                type="button"
                onClick={() => onViewDriverDetail(user)}
                className="rounded-xl border border-brand-200 bg-brand-50 px-4 py-2 text-xs font-bold text-brand-700 transition-all hover:bg-brand-100"
              >
                Xem chi tiết duyệt tài xế →
              </button>
            )}
          </div>

          {user.role !== 'ADMIN' && (
            <button
              type="button"
              disabled={actionLoading}
              onClick={() => onToggleStatus(user)}
              className={`flex items-center gap-1.5 rounded-xl px-5 py-2 text-xs font-bold transition-all ${
                user.status === 'LOCKED'
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100'
              }`}
            >
              {user.status === 'LOCKED' ? (
                <>
                  <IconCheckCircle className="h-4 w-4" />
                  <span>Mở khóa tài khoản</span>
                </>
              ) : (
                <>
                  <IconLock className="h-4 w-4" />
                  <span>Khóa tài khoản</span>
                </>
              )}
            </button>
          )}
        </div>
      )}
    >
      <div className={`grid grid-cols-1 items-start gap-3.5 text-xs ${isDriver ? 'lg:grid-cols-12' : ''}`}>
        {/* Left Column: Account info (+ driver license info if driver) */}
        <div className={`space-y-3 ${isDriver ? 'lg:col-span-7' : ''}`}>
          {/* Section 1: Basic account info */}
          <div className="grid grid-cols-1 gap-2.5 rounded-2xl border border-slate-200 bg-slate-50/70 p-3 sm:grid-cols-2">
            <div>
              <span className="block text-slate-500">Địa chỉ Email:</span>
              <strong className="break-all font-semibold text-slate-900">{user.email || '—'}</strong>
            </div>
            <div>
              <span className="block text-slate-500">Số điện thoại liên hệ:</span>
              <strong className="font-semibold text-slate-900">{user.phone || 'Chưa cập nhật'}</strong>
            </div>
            <div>
              <span className="block text-slate-500">Trạng thái định danh eKYC:</span>
              <div className="mt-0.5 flex flex-wrap items-center gap-2">
                <span className="inline-block rounded-full border bg-slate-100 px-2 py-0.5 text-[10px] font-bold">
                  {user.ekycStatus || 'PENDING'}
                </span>
                {onViewEkycDetail && (
                  <button
                    type="button"
                    onClick={() => onViewEkycDetail(user.id)}
                    className="text-[11px] font-bold text-brand-600 hover:underline"
                  >
                    Xem hồ sơ eKYC →
                  </button>
                )}
              </div>
            </div>
            <div>
              <span className="block text-slate-500">Thời điểm tạo tài khoản:</span>
              <strong className="text-slate-900">
                {user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            {user.role === 'PROVIDER' && user.providerId && onViewProviderDetail && (
              <div className="flex items-center justify-between border-t border-slate-200/70 pt-2 sm:col-span-2">
                <div>
                  <span className="block text-slate-500">Đơn vị Nhà cung cấp liên kết:</span>
                  <strong className="text-indigo-700">{user.providerDisplayName || `NCC #${user.providerId}`}</strong>
                </div>
                <button
                  type="button"
                  onClick={() => onViewProviderDetail(user.providerId)}
                  className="rounded-xl border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-700 transition-colors hover:bg-indigo-100"
                >
                  Xem hồ sơ Nhà cung cấp →
                </button>
              </div>
            )}
          </div>

          {/* Driver text details */}
          {isDriver && (
            <div className="space-y-2.5 rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50/40 via-white to-slate-50 p-3">
              <div className="flex items-center justify-between border-b border-indigo-100/70 pb-2">
                <h4 className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-indigo-950">
                  <span>🪪</span> Thông tin Giấy phép lái xe (Tài xế)
                </h4>
                {user.driverStatus && (
                  <span
                    className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                      STATUS_BADGES[user.driverStatus] || 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    Trạng thái duyệt: {user.driverStatus}
                  </span>
                )}
              </div>

              <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                <div>
                  <span className="block text-slate-500">Số GPLX:</span>
                  <strong className="font-mono text-sm text-slate-900">{user.licenseNo || '—'}</strong>
                </div>
                <div>
                  <span className="block text-slate-500">Hạng bằng lái:</span>
                  <span className="mt-0.5 inline-block rounded-md border border-brand-200 bg-brand-50 px-2 py-0.5 text-xs font-bold text-brand-800">
                    {user.licenseClass ? `Hạng ${user.licenseClass}` : '—'}
                  </span>
                </div>
                <div>
                  <span className="block text-slate-500">Ngày hết hạn:</span>
                  <strong className="mt-0.5 block text-slate-900">{user.licenseExpiry || 'Không thời hạn'}</strong>
                </div>
                {user.providerDisplayName && (
                  <div className="flex items-center justify-between sm:col-span-3">
                    <div>
                      <span className="block text-slate-500">Đơn vị / Nhà cung cấp chủ quản:</span>
                      <strong className="text-brand-700">{user.providerDisplayName}</strong>
                    </div>
                    {user.providerId && onViewProviderDetail && (
                      <button
                        type="button"
                        onClick={() => onViewProviderDetail(user.providerId)}
                        className="rounded-lg border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-[11px] font-bold text-indigo-700 transition-colors hover:bg-indigo-100"
                      >
                        Xem Nhà cung cấp →
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Column (Driver only): Portrait & License Photos */}
        {isDriver && (
          <div className="rounded-2xl border border-indigo-100 bg-indigo-50/25 p-3 lg:col-span-5">
            <span className="mb-2 block text-[11px] font-bold uppercase tracking-wider text-slate-600">
              Hình ảnh tài xế &amp; Bằng lái xe (GPLX)
            </span>
            <div className="grid grid-cols-2 gap-2.5">
              {/* Portrait photo */}
              <div
                onClick={() => (validPortraitUrl || validAvatarUrl) && onPreviewImage && onPreviewImage(validPortraitUrl || validAvatarUrl)}
                className="group relative flex h-44 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs transition-all hover:border-brand-500"
              >
                {validPortraitUrl || validAvatarUrl ? (
                  <img
                    src={validPortraitUrl || validAvatarUrl}
                    alt="Ảnh chân dung tài xế"
                    className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center p-3 text-center text-slate-400">
                    <IconUsers className="mb-1 h-7 w-7" />
                    <span className="text-[11px]">Chưa có ảnh chân dung</span>
                  </div>
                )}
                <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/75 px-2 py-0.5 text-[10px] font-bold text-white shadow-xs">
                  <IconEye className="h-3 w-3" /> Ảnh chân dung
                </span>
              </div>

              {/* License image */}
              <div
                onClick={() => validLicenseUrl && onPreviewImage && onPreviewImage(validLicenseUrl)}
                className="group relative flex h-44 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs transition-all hover:border-brand-500"
              >
                {validLicenseUrl ? (
                  <img
                    src={validLicenseUrl}
                    alt="Ảnh GPLX"
                    className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                  />
                ) : (
                  <div className="flex flex-col items-center justify-center p-3 text-center text-slate-400">
                    <IconShield className="mb-1 h-7 w-7" />
                    <span className="text-[11px]">Chưa có ảnh bằng lái</span>
                  </div>
                )}
                <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/75 px-2 py-0.5 text-[10px] font-bold text-white shadow-xs">
                  <IconEye className="h-3 w-3" /> Ảnh GPLX
                </span>
              </div>
            </div>
          </div>
        )}
      </div>
    </AdminModalShell>
  )
}
