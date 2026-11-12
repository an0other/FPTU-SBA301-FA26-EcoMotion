import { IconX, IconCheckCircle, IconEye, IconUsers, IconAlertTriangle } from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'
import { formatAdminRating } from '../../../utils/adminRating'

const STATUS_BADGES = {
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
}

function resolveValidImageUrl(url) {
  if (!url || typeof url !== 'string') return null
  if (/^\/images\/(portrait|license)-\d+\.webp$/i.test(url.trim())) return null
  return url
}

export default function DriverDetailModal({
  driver,
  onClose,
  onApprove,
  onRejectClick,
  actionLoading,
  onPreviewImage,
  onSelectProvider,
  onSelectUser,
}) {
  if (!driver) return null

  const portraitUrl = resolveValidImageUrl(driver.portraitUrl)
  const licenseImageUrl = resolveValidImageUrl(driver.licenseImageUrl)
  const isExpired = driver.licenseExpiry ? new Date(driver.licenseExpiry) < new Date() : false
  const driverRating = formatAdminRating(driver.ratingAvg, driver.ratingCount, 'lượt')

  return (
    <AdminModalShell
      maxWidth="max-w-5xl"
      header={(
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div
              className={`h-11 w-11 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-2xs ${
                portraitUrl ? 'cursor-pointer hover:ring-2 hover:ring-brand-500' : ''
              }`}
              onClick={() => portraitUrl && onPreviewImage && onPreviewImage(portraitUrl)}
              title={portraitUrl ? 'Bấm để phóng to ảnh' : ''}
            >
              {portraitUrl ? (
                <img
                  src={portraitUrl}
                  alt={driver.userFullName}
                  className="h-full w-full object-cover"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none'
                    if (e.currentTarget.nextElementSibling) {
                      e.currentTarget.nextElementSibling.style.display = 'flex'
                    }
                  }}
                />
              ) : null}
              <div
                className="flex h-full w-full items-center justify-center text-slate-400"
                style={{ display: portraitUrl ? 'none' : 'flex' }}
              >
                <IconUsers className="h-6 w-6" />
              </div>
            </div>

            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
                  {driver.userFullName || 'Hồ sơ Thẩm định Tài xế'}
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    STATUS_BADGES[driver.status] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {driver.status}
                </span>
              </div>
              <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
                <span>Tài xế #{driver.id} • Đơn vị quản lý:</span>
                {driver.providerId && onSelectProvider ? (
                  <button
                    type="button"
                    onClick={() => onSelectProvider(driver.providerId)}
                    className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-indigo-50 px-2 py-0.5 font-bold text-indigo-700 transition-colors hover:bg-indigo-100"
                  >
                    {driver.providerDisplayName || `NCC #${driver.providerId}`} →
                  </button>
                ) : (
                  <strong>{driver.providerDisplayName || 'Chưa liên kết'}</strong>
                )}
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
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>

          <div className="flex items-center gap-2">
            {driver.status !== 'REJECTED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => {
                  onClose()
                  onRejectClick(driver)
                }}
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                Từ chối hồ sơ
              </button>
            )}

            {driver.status !== 'APPROVED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onApprove(driver.id)}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>Phê duyệt tài xế</span>
              </button>
            )}
          </div>
        </div>
      )}
    >
      <div className="space-y-3 text-xs">
        {/* Reject Notice if rejected */}
        {driver.rejectReason && (
          <div className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50/80 p-3 text-rose-800">
            <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
            <div>
              <strong className="font-bold">Lý do từ chối trước đó:</strong>{' '}
              <span>{driver.rejectReason}</span>
            </div>
          </div>
        )}

        {/* Top 2-Column Info Grid */}
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-12">
          {/* Section 1: Personal & Organization */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3 lg:col-span-7">
            <h4 className="mb-2 flex flex-wrap items-center justify-between gap-2 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span className="flex items-center gap-1.5">
                <span>👤</span> Thông tin tài xế &amp; Đơn vị chủ quản
              </span>
              {driver.userId && onSelectUser && (
                <button
                  type="button"
                  onClick={() => onSelectUser(driver.userId)}
                  className="text-[11px] font-bold normal-case tracking-normal text-brand-600 hover:underline"
                >
                  Xem User #{driver.userId} →
                </button>
              )}
            </h4>
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3">
              <div>
                <span className="block text-slate-500">Họ và tên:</span>
                <strong className="text-sm text-slate-900">{driver.userFullName || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Email:</span>
                <strong className="break-all text-slate-900">{driver.userEmail || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Số điện thoại:</span>
                <strong className="font-bold text-slate-900">{driver.userPhone || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Nhà cung cấp quản lý:</span>
                {driver.providerId && onSelectProvider ? (
                  <button
                    type="button"
                    onClick={() => onSelectProvider(driver.providerId)}
                    className="text-left font-bold text-indigo-700 hover:underline"
                  >
                    {driver.providerDisplayName || `NCC #${driver.providerId}`} →
                  </button>
                ) : (
                  <strong className="text-brand-700">{driver.providerDisplayName || 'Chưa gán'}</strong>
                )}
              </div>
              <div>
                <span className="block text-slate-500">Đánh giá uy tín:</span>
                <strong className={driverRating ? 'text-slate-900' : 'text-slate-500'}>
                  {driverRating || 'Chưa có'}
                </strong>
              </div>
              <div>
                <span className="block text-slate-500">Ngày tạo hồ sơ:</span>
                <strong className="text-slate-900">
                  {driver.createdAt ? new Date(driver.createdAt).toLocaleString('vi-VN') : '—'}
                </strong>
              </div>
            </div>
          </div>

          {/* Section 2: Driver License Details */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3 lg:col-span-5">
            <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span>🪪</span> Giấy phép lái xe (GPLX)
            </h4>
            <div className="grid grid-cols-2 gap-2.5">
              <div className="col-span-2">
                <span className="block text-slate-500">Số Giấy phép lái xe:</span>
                <strong className="font-mono text-sm text-slate-900">{driver.licenseNo || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Hạng bằng lái:</span>
                <span className="mt-0.5 inline-block rounded-md bg-brand-100 px-2 py-0.5 text-xs font-bold text-brand-800">
                  Hạng {driver.licenseClass || 'B2'}
                </span>
              </div>
              <div>
                <span className="block text-slate-500">Ngày hết hạn:</span>
                <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
                  <strong className={`font-semibold ${isExpired ? 'text-rose-600' : 'text-slate-900'}`}>
                    {driver.licenseExpiry || 'Không thời hạn'}
                  </strong>
                  {isExpired && (
                    <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-bold text-rose-700">
                      Đã hết hạn!
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Section 3: Driver License & Portrait Photos */}
        <div>
          <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
            <span>📷</span> Hình ảnh bằng lái xe &amp; Chân dung đối chiếu
          </h4>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div
              onClick={() => licenseImageUrl && onPreviewImage(licenseImageUrl)}
              className="group relative flex h-36 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 transition-all hover:border-brand-500"
            >
              {licenseImageUrl ? (
                <img
                  src={licenseImageUrl}
                  alt="Ảnh Bằng lái xe (GPLX)"
                  className="h-full w-full object-cover transition-transform group-hover:scale-105"
                />
              ) : (
                <span className="p-2 text-center text-slate-400">Chưa có ảnh GPLX</span>
              )}
              <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/70 px-2 py-0.5 text-[10px] font-bold text-white">
                <IconEye className="h-3 w-3" /> Ảnh Bằng lái xe (GPLX)
              </span>
            </div>

            {/* Portrait */}
            <div
              onClick={() => portraitUrl && onPreviewImage(portraitUrl)}
              className="group relative flex h-36 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 transition-all hover:border-brand-500"
            >
              {portraitUrl ? (
                <img
                  src={portraitUrl}
                  alt="Ảnh chân dung"
                  className="h-full w-full object-cover transition-transform group-hover:scale-105"
                />
              ) : (
                <span className="p-2 text-center text-slate-400">Chưa có ảnh chân dung</span>
              )}
              <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/70 px-2 py-0.5 text-[10px] font-bold text-white">
                <IconEye className="h-3 w-3" /> Ảnh chân dung
              </span>
            </div>
          </div>
        </div>
      </div>
    </AdminModalShell>
  )
}
