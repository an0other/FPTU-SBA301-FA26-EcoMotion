import { IconX, IconCheckCircle, IconEye, IconUsers, IconAlertTriangle } from '../../../components/icons/Icons'

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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <div
              className={`h-12 w-12 shrink-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 shadow-2xs ${
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
              <div className="flex items-center gap-2">
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
              <p className="text-xs text-slate-500 flex flex-wrap items-center gap-1.5 mt-0.5">
                <span>Tài xế #{driver.id} • Đơn vị quản lý:</span>
                {driver.providerId && onSelectProvider ? (
                  <button
                    type="button"
                    onClick={() => onSelectProvider(driver.providerId)}
                    className="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 font-bold text-indigo-700 border border-indigo-200 hover:bg-indigo-100 transition-colors"
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

        {/* Content Body */}
        <div className="mt-5 space-y-5 text-xs">
          {/* Reject Notice if rejected */}
          {driver.rejectReason && (
            <div className="flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50/80 p-4 text-rose-800">
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
              <div>
                <strong className="block font-bold">Lý do từ chối trước đó:</strong>
                <p className="mt-0.5">{driver.rejectReason}</p>
              </div>
            </div>
          )}

          {/* Section 1: Personal & Organization */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <span>👤</span> Thông tin tài xế &amp; Đơn vị chủ quản
              </span>
              {driver.userId && onSelectUser && (
                <button
                  type="button"
                  onClick={() => onSelectUser(driver.userId)}
                  className="text-[11px] font-bold text-brand-600 hover:underline"
                >
                  Xem tài khoản gốc (User #{driver.userId}) →
                </button>
              )}
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Họ và tên:</span>
                <strong className="text-slate-900 text-sm">{driver.userFullName || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Email:</span>
                <strong className="text-slate-900">{driver.userEmail || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số điện thoại:</span>
                <strong className="text-slate-900 font-bold">{driver.userPhone || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Nhà cung cấp quản lý:</span>
                {driver.providerId && onSelectProvider ? (
                  <button
                    type="button"
                    onClick={() => onSelectProvider(driver.providerId)}
                    className="font-bold text-indigo-700 hover:underline text-left"
                  >
                    {driver.providerDisplayName || `NCC #${driver.providerId}`} →
                  </button>
                ) : (
                  <strong className="text-brand-700">{driver.providerDisplayName || 'Chưa gán'}</strong>
                )}
              </div>
              <div>
                <span className="text-slate-500 block">Điểm đánh giá uy tín:</span>
                <strong className="text-slate-900">
                  ★ {driver.ratingAvg || '5.0'} ({driver.ratingCount || 0} lượt)
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Ngày tạo hồ sơ:</span>
                <strong className="text-slate-900">
                  {driver.createdAt ? new Date(driver.createdAt).toLocaleString('vi-VN') : '—'}
                </strong>
              </div>
            </div>
          </div>

          {/* Section 2: Driver License Details */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>🪪</span> Giấy phép lái xe (GPLX)
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Số Giấy phép lái xe:</span>
                <strong className="text-slate-900 font-mono text-sm">{driver.licenseNo || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Hạng bằng lái:</span>
                <span className="inline-block rounded-md bg-brand-100 px-2 py-0.5 text-xs font-bold text-brand-800">
                  Hạng {driver.licenseClass || 'B2'}
                </span>
              </div>
              <div>
                <span className="text-slate-500 block">Ngày hết hạn bằng lái:</span>
                <div className="flex items-center gap-1.5 mt-0.5">
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

          {/* Section 3: Driver License Photos */}
          <div>
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-1.5">
              <span>📷</span> Hình ảnh bằng lái xe &amp; Chân dung đối chiếu
            </h4>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div
                onClick={() => licenseImageUrl && onPreviewImage(licenseImageUrl)}
                className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 transition-all hover:border-brand-500"
              >
                {licenseImageUrl ? (
                  <img
                    src={licenseImageUrl}
                    alt="Ảnh Bằng lái xe (GPLX)"
                    className="h-full w-full object-cover transition-transform group-hover:scale-105"
                  />
                ) : (
                  <span className="text-slate-400 text-center p-2">Chưa có ảnh GPLX</span>
                )}
                <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/70 px-2 py-1 text-[10px] font-bold text-white">
                  <IconEye className="h-3 w-3" /> Ảnh Bằng lái xe (GPLX)
                </span>
              </div>

              {/* Portrait */}
              <div
                onClick={() => portraitUrl && onPreviewImage(portraitUrl)}
                className="group relative flex aspect-4/3 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 transition-all hover:border-brand-500"
              >
                {portraitUrl ? (
                  <img
                    src={portraitUrl}
                    alt="Ảnh chân dung"
                    className="h-full w-full object-cover transition-transform group-hover:scale-105"
                  />
                ) : (
                  <span className="text-slate-400 text-center p-2">Chưa có ảnh chân dung</span>
                )}
                <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/70 px-2 py-1 text-[10px] font-bold text-white">
                  <IconEye className="h-3 w-3" /> Ảnh nhận diện chân dung
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Action Controls */}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
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
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                Từ chối hồ sơ
              </button>
            )}

            {driver.status !== 'APPROVED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onApprove(driver.id)}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>Phê duyệt tài xế</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
