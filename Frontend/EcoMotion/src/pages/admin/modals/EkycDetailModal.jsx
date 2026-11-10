import { IconX, IconCheckCircle, IconEye, IconShield } from '../../../components/icons/Icons'

const STATUS_BADGES = {
  VERIFIED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function EkycDetailModal({
  ekyc,
  onClose,
  onStatusUpdate,
  actionLoading,
  onPreviewImage,
  onSelectUser,
}) {
  if (!ekyc) return null

  const isMissingData =
    !ekyc.cccdNo ||
    !String(ekyc.cccdNo).trim() ||
    !ekyc.dateOfBirth ||
    !ekyc.gender ||
    !ekyc.address ||
    !ekyc.cccdImageUrl ||
    !ekyc.portraitUrl

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-700">
              <IconShield className="h-5 w-5" />
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
                  Thẩm định Hồ sơ eKYC: {ekyc.fullName || 'Khách thuê'}
                </h3>
                <span
                  className={`rounded-full border px-3 py-0.5 text-xs font-bold ${
                    STATUS_BADGES[ekyc.ekycStatus] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {ekyc.ekycStatus}
                </span>
                {ekyc.ekycStatus === 'PENDING' && (
                  <span className="rounded-md bg-indigo-50 border border-indigo-200 px-2 py-0.5 text-[11px] font-semibold text-indigo-700">
                    Cập nhật hồ sơ / Chờ thẩm định CCCD
                  </span>
                )}
              </div>
              <p className="mt-0.5 text-xs text-slate-500 flex flex-wrap items-center gap-1.5">
                {ekyc.userId && onSelectUser ? (
                  <button
                    type="button"
                    onClick={() => onSelectUser(ekyc.userId)}
                    className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2 py-0.5 font-bold text-brand-700 border border-brand-200 hover:bg-brand-100 transition-colors"
                  >
                    User ID #{ekyc.userId} →
                  </button>
                ) : (
                  <span>User ID #{ekyc.userId}</span>
                )}
                <span>• Email: {ekyc.email} • SĐT: {ekyc.phone || 'Chưa có'}</span>
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
          {/* Context explanation for PENDING manual eKYC review */}
          {ekyc.ekycStatus === 'PENDING' && (
            <div className="rounded-2xl border border-indigo-200 bg-indigo-50/70 p-3.5 text-indigo-900">
              <div className="font-bold text-xs">
                📌 Lý do hồ sơ ở trạng thái Chờ thẩm định (PENDING):
              </div>
              <p className="mt-1 text-[11px] text-indigo-800 leading-relaxed">
                Hồ sơ được chuyển vào hàng đợi thẩm định thủ công khi khách thuê <strong>cập nhật / chỉnh sửa lại thông tin CCCD &amp; ảnh sinh trắc học</strong> trong trang cá nhân, hoặc khi hệ thống OCR tự động cần Admin đối chiếu lại độ khớp giữa ảnh CCCD và ảnh chân dung. Việc duyệt/từ chối eKYC không làm thay đổi trạng thái đăng nhập của tài khoản.
              </p>
            </div>
          )}

          {/* Missing data warning banner */}
          {isMissingData && (
            <div className="rounded-2xl border border-rose-200 bg-rose-50/80 p-3.5 text-rose-900">
              <div className="font-bold text-xs text-rose-800">
                ⚠️ Cảnh báo: Hồ sơ thiếu thông tin định danh hoặc hình ảnh đối chiếu bắt buộc!
              </div>
              <p className="mt-1 text-[11px] text-rose-700 leading-relaxed">
                Hồ sơ này đang trống Số CCCD, ngày sinh, giới tính, địa chỉ hoặc thiếu ảnh chụp CCCD/chân dung. Hệ thống <strong>không cho phép xác nhận eKYC hợp lệ (VERIFIED)</strong> khi thông tin trống — vui lòng bấm <strong>Từ chối hồ sơ eKYC</strong> để yêu cầu khách thuê bổ sung.
              </p>
            </div>
          )}

          {/* Identity Information Grid */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <div className="mb-3 flex items-center justify-between">
              <h4 className="font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <span>🪪</span> Dữ liệu cá nhân trích xuất từ thẻ CCCD/CMND
              </h4>
              {ekyc.userId && onSelectUser && (
                <button
                  type="button"
                  onClick={() => onSelectUser(ekyc.userId)}
                  className="text-[11px] font-bold text-brand-600 hover:underline"
                >
                  Xem tài khoản người dùng →
                </button>
              )}
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="min-w-0">
                <span className="text-slate-500 block">Số định danh CCCD:</span>
                <strong className="text-slate-900 font-mono text-sm break-all block">
                  {ekyc.cccdNo || 'Chưa cung cấp'}
                </strong>
              </div>
              <div className="min-w-0">
                <span className="text-slate-500 block">Họ và tên chủ thẻ:</span>
                <strong className="text-slate-900 text-sm uppercase break-words block">
                  {ekyc.fullName || '—'}
                </strong>
              </div>
              <div className="min-w-0">
                <span className="text-slate-500 block">Ngày sinh:</span>
                <strong className="text-slate-900 block">{ekyc.dateOfBirth || '—'}</strong>
              </div>
              <div className="min-w-0">
                <span className="text-slate-500 block">Giới tính:</span>
                <strong className="text-slate-900 block">{ekyc.gender || '—'}</strong>
              </div>
              <div className="sm:col-span-2 lg:col-span-4 min-w-0">
                <span className="text-slate-500 block">Nơi thường trú:</span>
                <p className="mt-1 rounded-xl border border-slate-200 bg-white p-2.5 font-medium text-slate-800 break-words">
                  {ekyc.address || 'Chưa cung cấp'}
                </p>
              </div>
            </div>
          </div>

          {/* Biometric & Document Comparison: 3 Columns */}
          <div>
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <span>🔍</span> Đối chiếu hình ảnh sinh trắc học &amp; Thẻ CCCD
              </span>
              <span className="text-[11px] font-normal text-slate-400">Bấm vào từng ảnh để phóng to toàn màn hình</span>
            </h4>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              {/* CCCD Front */}
              <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-3 shadow-xs">
                <span className="text-[11px] font-bold text-slate-700 mb-2">1. Mặt trước CCCD</span>
                <div
                  onClick={() => ekyc.cccdImageUrl && onPreviewImage(ekyc.cccdImageUrl)}
                  className="group relative flex aspect-4/3 cursor-pointer items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                >
                  {ekyc.cccdImageUrl ? (
                    <img
                      src={ekyc.cccdImageUrl}
                      alt="CCCD Mặt trước"
                      className="h-full w-full object-cover transition-transform group-hover:scale-105"
                    />
                  ) : (
                    <span className="text-slate-400 text-center p-2">Không có ảnh mặt trước</span>
                  )}
                  <span className="absolute bottom-2 right-2 flex items-center gap-1 rounded bg-black/70 px-2 py-0.5 text-[10px] text-white">
                    <IconEye className="h-3 w-3" /> Phóng to
                  </span>
                </div>
              </div>

              {/* CCCD Back */}
              <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-3 shadow-xs">
                <span className="text-[11px] font-bold text-slate-700 mb-2">2. Mặt sau CCCD</span>
                <div
                  onClick={() => ekyc.cccdImageBackUrl && onPreviewImage(ekyc.cccdImageBackUrl)}
                  className="group relative flex aspect-4/3 cursor-pointer items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                >
                  {ekyc.cccdImageBackUrl ? (
                    <img
                      src={ekyc.cccdImageBackUrl}
                      alt="CCCD Mặt sau"
                      className="h-full w-full object-cover transition-transform group-hover:scale-105"
                    />
                  ) : (
                    <span className="text-slate-400 text-center p-2">Không có ảnh mặt sau</span>
                  )}
                  <span className="absolute bottom-2 right-2 flex items-center gap-1 rounded bg-black/70 px-2 py-0.5 text-[10px] text-white">
                    <IconEye className="h-3 w-3" /> Phóng to
                  </span>
                </div>
              </div>

              {/* Portrait Selfie */}
              <div className="flex flex-col rounded-2xl border border-slate-200 bg-white p-3 shadow-xs">
                <span className="text-[11px] font-bold text-slate-700 mb-2">3. Ảnh chân dung Selfie đối chiếu</span>
                <div
                  onClick={() => ekyc.portraitUrl && onPreviewImage(ekyc.portraitUrl)}
                  className="group relative flex aspect-4/3 cursor-pointer items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-100 hover:border-brand-500"
                >
                  {ekyc.portraitUrl ? (
                    <img
                      src={ekyc.portraitUrl}
                      alt="Chân dung selfie"
                      className="h-full w-full object-cover transition-transform group-hover:scale-105"
                    />
                  ) : (
                    <span className="text-slate-400 text-center p-2">Không có ảnh selfie</span>
                  )}
                  <span className="absolute bottom-2 right-2 flex items-center gap-1 rounded bg-black/70 px-2 py-0.5 text-[10px] text-white">
                    <IconEye className="h-3 w-3" /> Phóng to
                  </span>
                </div>
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
            {ekyc.ekycStatus !== 'REJECTED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(ekyc.userId, 'REJECTED')}
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                Từ chối hồ sơ eKYC
              </button>
            )}

            {ekyc.ekycStatus !== 'VERIFIED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(ekyc.userId, 'VERIFIED')}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>Xác nhận eKYC hợp lệ</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
