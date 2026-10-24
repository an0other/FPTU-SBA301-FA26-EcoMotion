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
}) {
  if (!ekyc) return null

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
              <div className="flex items-center gap-2">
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
              </div>
              <p className="text-xs text-slate-500">
                User ID #{ekyc.userId} • Email: {ekyc.email} • SĐT: {ekyc.phone || 'Chưa có'}
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
          {/* Identity Information Grid */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>🪪</span> Dữ liệu cá nhân trích xuất từ thẻ CCCD/CMND
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <span className="text-slate-500 block">Số định danh CCCD:</span>
                <strong className="text-slate-900 font-mono text-sm">{ekyc.cccdNo || 'Chưa cung cấp'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Họ và tên chủ thẻ:</span>
                <strong className="text-slate-900 text-sm uppercase">{ekyc.fullName || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Ngày sinh:</span>
                <strong className="text-slate-900">{ekyc.dateOfBirth || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Giới tính:</span>
                <strong className="text-slate-900">{ekyc.gender || '—'}</strong>
              </div>
              <div className="sm:col-span-2 lg:col-span-4">
                <span className="text-slate-500 block">Nơi thường trú:</span>
                <p className="mt-1 rounded-xl border border-slate-200 bg-white p-2.5 font-medium text-slate-800">
                  {ekyc.address || 'Chưa cung cấp'}
                </p>
              </div>
            </div>
          </div>

          {/* Biometric & Document Comparison: 3 Columns */}
          <div>
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <span>🔍</span> Đối chiếu hình ảnh sinh trắc học & Thẻ CCCD
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
