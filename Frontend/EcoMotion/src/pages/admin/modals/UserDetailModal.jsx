import { useState } from 'react'
import { IconX, IconLock, IconCheckCircle, IconUsers, IconShield, IconEye } from '../../../components/icons/Icons'

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <div
              className={`relative h-14 w-14 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-2xs ${
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
                  <IconUsers className="h-7 w-7" />
                </div>
              )}
            </div>
            <div>
              <div className="flex items-center gap-2">
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

        {/* Content Body */}
        <div className="mt-5 space-y-4 text-xs">
          {/* Section 1: Basic account info */}
          <div className="grid grid-cols-1 gap-3 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2">
            <div>
              <span className="text-slate-500 block">Địa chỉ Email:</span>
              <strong className="text-slate-900 font-semibold">{user.email || '—'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Số điện thoại liên hệ:</span>
              <strong className="text-slate-900 font-semibold">{user.phone || 'Chưa cập nhật'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Trạng thái định danh eKYC:</span>
              <div className="mt-0.5 flex items-center gap-2">
                <span className="inline-block rounded-full border px-2 py-0.5 text-[10px] font-bold bg-slate-100">
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
              <span className="text-slate-500 block">Thời điểm tạo tài khoản:</span>
              <strong className="text-slate-900">
                {user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            {user.role === 'PROVIDER' && user.providerId && onViewProviderDetail && (
              <div className="sm:col-span-2 pt-1 border-t border-slate-200/70 flex items-center justify-between">
                <div>
                  <span className="text-slate-500 block">Đơn vị Nhà cung cấp liên kết:</span>
                  <strong className="text-indigo-700">{user.providerDisplayName || `NCC #${user.providerId}`}</strong>
                </div>
                <button
                  type="button"
                  onClick={() => onViewProviderDetail(user.providerId)}
                  className="rounded-xl border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-700 hover:bg-indigo-100 transition-colors"
                >
                  Xem hồ sơ Nhà cung cấp →
                </button>
              </div>
            )}
          </div>

          {/* Section 2: Driver Specific Details & Photos */}
          {isDriver && (
            <div className="rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50/40 via-white to-slate-50 p-4 space-y-4">
              <div className="flex items-center justify-between border-b border-indigo-100/70 pb-2.5">
                <h4 className="font-bold uppercase tracking-wider text-indigo-950 text-[11px] flex items-center gap-1.5">
                  <span>🪪</span> Hồ sơ &amp; Hình ảnh Tài xế
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

              {/* Driver info fields */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <span className="text-slate-500 block">Số Giấy phép lái xe:</span>
                  <strong className="text-slate-900 font-mono text-sm">{user.licenseNo || '—'}</strong>
                </div>
                <div>
                  <span className="text-slate-500 block">Hạng bằng lái:</span>
                  <span className="inline-block mt-0.5 rounded-md bg-brand-50 border border-brand-200 px-2 py-0.5 text-xs font-bold text-brand-800">
                    {user.licenseClass ? `Hạng ${user.licenseClass}` : '—'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 block">Ngày hết hạn bằng lái:</span>
                  <strong className="text-slate-900 mt-0.5 block">{user.licenseExpiry || 'Không thời hạn'}</strong>
                </div>
                {user.providerDisplayName && (
                  <div className="sm:col-span-3 flex items-center justify-between">
                    <div>
                      <span className="text-slate-500 block">Đơn vị / Nhà cung cấp chủ quản:</span>
                      <strong className="text-brand-700">{user.providerDisplayName}</strong>
                    </div>
                    {user.providerId && onViewProviderDetail && (
                      <button
                        type="button"
                        onClick={() => onViewProviderDetail(user.providerId)}
                        className="rounded-lg border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-[11px] font-bold text-indigo-700 hover:bg-indigo-100 transition-colors"
                      >
                        Xem Nhà cung cấp →
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Photos Gallery */}
              <div>
                <span className="text-slate-600 font-bold block mb-2">Hình ảnh tài xế &amp; Bằng lái xe (GPLX):</span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                  {/* Portrait photo */}
                  <div
                    onClick={() => (validPortraitUrl || validAvatarUrl) && onPreviewImage && onPreviewImage(validPortraitUrl || validAvatarUrl)}
                    className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs hover:border-brand-500 transition-all"
                  >
                    {validPortraitUrl || validAvatarUrl ? (
                      <img
                        src={validPortraitUrl || validAvatarUrl}
                        alt="Ảnh chân dung tài xế"
                        className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                      />
                    ) : (
                      <div className="flex flex-col items-center justify-center p-4 text-slate-400">
                        <IconUsers className="h-8 w-8 mb-1" />
                        <span className="text-[11px]">Chưa có ảnh chân dung</span>
                      </div>
                    )}
                    <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/75 px-2 py-1 text-[10px] font-bold text-white shadow-xs">
                      <IconEye className="h-3 w-3" /> Ảnh chân dung
                    </span>
                  </div>

                  {/* License image */}
                  <div
                    onClick={() => validLicenseUrl && onPreviewImage && onPreviewImage(validLicenseUrl)}
                    className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs hover:border-brand-500 transition-all"
                  >
                    {validLicenseUrl ? (
                      <img
                        src={validLicenseUrl}
                        alt="Ảnh GPLX"
                        className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                      />
                    ) : (
                      <div className="flex flex-col items-center justify-center p-4 text-slate-400">
                        <IconShield className="h-8 w-8 mb-1" />
                        <span className="text-[11px]">Chưa có ảnh bằng lái</span>
                      </div>
                    )}
                    <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/75 px-2 py-1 text-[10px] font-bold text-white shadow-xs">
                      <IconEye className="h-3 w-3" /> Ảnh Bằng lái xe (GPLX)
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Action Controls */}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Đóng
            </button>
            {isDriver && onViewDriverDetail && (
              <button
                type="button"
                onClick={() => onViewDriverDetail(user)}
                className="rounded-xl border border-brand-200 bg-brand-50 px-4 py-2.5 text-xs font-bold text-brand-700 hover:bg-brand-100 transition-all"
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
              className={`flex items-center gap-1.5 rounded-xl px-5 py-2.5 text-xs font-bold transition-all ${
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
      </div>
    </div>
  )
}
