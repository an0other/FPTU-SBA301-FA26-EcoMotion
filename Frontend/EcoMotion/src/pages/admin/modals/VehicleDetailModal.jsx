import { useState } from 'react'
import {
  IconX,
  IconCheckCircle,
  IconEye,
  IconCar,
  IconAlertTriangle,
  IconTag,
} from '../../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const STATUS_BADGES = {
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
  BLOCKED: 'bg-slate-100 text-slate-700 border-slate-300',
  MAINTENANCE: 'bg-blue-50 text-blue-700 border-blue-200',
}

const DOC_TYPE_LABELS = {
  REGISTRATION: 'Đăng ký xe (Cà-vẹt)',
  INSPECTION: 'Giấy chứng nhận Đăng kiểm',
  INSURANCE: 'Bảo hiểm xe bắt buộc',
  OTHER: 'Giấy tờ pháp lý khác',
}

export default function VehicleDetailModal({
  vehicle,
  onClose,
  onApprove,
  onReject,
  actionLoading,
  onPreviewDoc,
  onSelectProvider,
}) {
  const [activeImageIndex, setActiveImageIndex] = useState(0)
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [rejectReason, setRejectReason] = useState('')

  if (!vehicle) return null

  const images = vehicle.imageUrls && vehicle.imageUrls.length > 0 ? vehicle.imageUrls : []

  function handleRejectSubmit(e) {
    e.preventDefault()
    if (!rejectReason.trim()) {
      alert('Vui lòng nhập lý do từ chối duyệt xe.')
      return
    }
    onReject(vehicle.id, rejectReason.trim())
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
              <IconCar className="h-6 w-6" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xl font-bold text-slate-900">
                  {vehicle.brand} {vehicle.model} ({vehicle.manufactureYear})
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    STATUS_BADGES[vehicle.status] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {vehicle.status}
                </span>
              </div>
              <p className="mt-0.5 text-xs text-slate-500 flex flex-wrap items-center gap-1.5">
                <span>
                  Biển số: <strong className="font-mono text-brand-700">{vehicle.licensePlate}</strong> • Nhà cung cấp:
                </span>
                {vehicle.providerId && onSelectProvider ? (
                  <button
                    type="button"
                    onClick={() => onSelectProvider(vehicle.providerId)}
                    className="inline-flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 font-bold text-indigo-700 border border-indigo-200 hover:bg-indigo-100 transition-colors"
                  >
                    {vehicle.providerDisplayName || `NCC #${vehicle.providerId}`} →
                  </button>
                ) : (
                  <strong>{vehicle.providerDisplayName || `NCC #${vehicle.providerId}`}</strong>
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
        <div className="mt-5 space-y-6 text-xs">
          {/* Section 1: Image Gallery */}
          {images.length > 0 ? (
            <div className="space-y-3">
              <div className="relative aspect-16/9 w-full overflow-hidden rounded-2xl border border-slate-200 bg-slate-900">
                <img
                  src={images[activeImageIndex]}
                  alt="Ảnh xe chi tiết"
                  className="h-full w-full object-contain"
                />
                <span className="absolute bottom-3 right-3 rounded-lg bg-black/70 px-2.5 py-1 text-[11px] font-bold text-white">
                  Ảnh {activeImageIndex + 1} / {images.length}
                </span>
              </div>

              {images.length > 1 && (
                <div className="flex gap-2 overflow-x-auto pb-1">
                  {images.map((img, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => setActiveImageIndex(idx)}
                      className={`relative h-16 w-24 shrink-0 overflow-hidden rounded-xl border-2 transition-all ${
                        activeImageIndex === idx
                          ? 'border-brand-600 ring-2 ring-brand-500/30'
                          : 'border-slate-200 opacity-70 hover:opacity-100'
                      }`}
                    >
                      <img src={img} alt="Thumbnail" className="h-full w-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-8 text-center text-slate-400">
              <IconCar className="mx-auto h-8 w-8 mb-2 opacity-50" />
              Chưa có hình ảnh nào được tải lên cho xe này.
            </div>
          )}

          {/* Section 2: Technical Specifications & Identity */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>⚙️</span> Thông số kỹ thuật &amp; Định danh phương tiện
            </h4>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div>
                <span className="text-slate-500 block">Số chỗ ngồi:</span>
                <strong className="text-slate-900">{vehicle.seatCount} chỗ</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Nhiên liệu:</span>
                <strong className="text-slate-900">{vehicle.fuelType || 'Điện (EV)'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Hộp số:</span>
                <strong className="text-slate-900">{vehicle.transmission || 'Tự động'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Màu sắc ngoại thất:</span>
                <strong className="text-slate-900">{vehicle.color || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số khung (Chassis):</span>
                <strong className="text-slate-900 font-mono text-[11px]">{vehicle.chassisNo || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số máy (Engine):</span>
                <strong className="text-slate-900 font-mono text-[11px]">{vehicle.engineNo || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Thời gian chuẩn bị (Buffer):</span>
                <strong className="text-slate-900">{vehicle.maintenanceBufferMinutes || 30} phút</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Đánh giá trung bình:</span>
                <strong className="text-slate-900">★ {vehicle.ratingAvg || '5.0'} ({vehicle.ratingCount || 0} lượt)</strong>
              </div>
            </div>
          </div>

          {/* Section 3: Provider & Depot Location (Clickable to Provider Detail) */}
          <div
            onClick={() => vehicle.providerId && onSelectProvider && onSelectProvider(vehicle.providerId)}
            className={`rounded-2xl border p-4 transition-all ${
              vehicle.providerId && onSelectProvider
                ? 'cursor-pointer border-slate-200 bg-slate-50/60 hover:border-indigo-400 hover:bg-indigo-50/40 group'
                : 'border-slate-200 bg-slate-50/60'
            }`}
          >
            <div className="mb-2 flex items-center justify-between">
              <h4 className="font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <span>📍</span> Thông tin Nhà cung cấp &amp; Điểm tập kết xe (Depot)
              </h4>
              {vehicle.providerId && onSelectProvider && (
                <span className="text-[11px] font-bold text-indigo-600 group-hover:translate-x-0.5 transition-transform">
                  Xem chi tiết Nhà cung cấp →
                </span>
              )}
            </div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Đơn vị chủ xe:</span>
                <strong className="text-slate-900 group-hover:text-indigo-700">
                  {vehicle.providerDisplayName || `NCC #${vehicle.providerId}`}
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Hotline liên hệ:</span>
                <strong className="text-brand-700 font-bold">{vehicle.providerHotline || 'Chưa cung cấp'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Bãi xe đón khách:</span>
                <strong className="text-slate-900">{vehicle.providerDepotAddress || 'Chưa cung cấp'}</strong>
              </div>
            </div>
          </div>

          {/* Section 4: Mandatory Legal Documents (3 Documents) */}
          <div>
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <span>📑</span> Hồ sơ pháp lý xe ({vehicle.documents?.length || 0} tài liệu)
              </span>
              <span className="text-[11px] font-normal text-slate-400">Yêu cầu Đăng ký, Đăng kiểm và Bảo hiểm hợp lệ</span>
            </h4>

            {(!vehicle.documents || vehicle.documents.length === 0) ? (
              <div className="rounded-2xl border border-dashed border-rose-200 bg-rose-50/50 p-4 text-center text-rose-700 font-medium">
                ⚠️ Cảnh báo: Chủ xe chưa tải lên bất kỳ giấy tờ pháp lý nào! Cần yêu cầu bổ sung trước khi duyệt mở bán.
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {vehicle.documents.map((doc) => {
                  const isDocExpired = doc.expiryDate ? new Date(doc.expiryDate) < new Date() : false
                  return (
                    <div
                      key={doc.id}
                      className="flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-3.5 shadow-xs"
                    >
                      <div className="space-y-1">
                        <span className="block text-[11px] font-bold text-brand-700">
                          {DOC_TYPE_LABELS[doc.docType] || doc.docType}
                        </span>
                        <div className="text-xs text-slate-800">
                          <strong>Số hiệu:</strong> <span className="font-mono">{doc.docNumber || '—'}</span>
                        </div>
                        <div className="text-[11px] text-slate-500 flex items-center gap-1">
                          <span>Hạn dùng:</span>
                          <strong className={isDocExpired ? 'text-rose-600 font-bold' : 'text-slate-700'}>
                            {doc.expiryDate || 'Vô thời hạn'}
                          </strong>
                          {isDocExpired && (
                            <span className="rounded bg-rose-100 px-1 text-[10px] text-rose-700 font-bold">Hết hạn</span>
                          )}
                        </div>
                        <span
                          className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-bold ${
                            STATUS_BADGES[doc.status] || 'bg-slate-100 text-slate-700'
                          }`}
                        >
                          {doc.status}
                        </span>
                      </div>

                      {doc.fileUrl && (
                        <button
                          type="button"
                          onClick={() => onPreviewDoc(doc.fileUrl)}
                          className="mt-3 flex items-center justify-center gap-1 rounded-xl border border-slate-200 bg-slate-50 py-1.5 text-[11px] font-semibold text-slate-700 hover:bg-slate-100"
                        >
                          <IconEye className="h-3.5 w-3.5" />
                          <span>Xem ảnh tài liệu</span>
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Section 5: Rental Pricing Plans */}
          {vehicle.rentalPlans && vehicle.rentalPlans.length > 0 && (
            <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
              <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
                <IconTag className="h-4 w-4 text-brand-600" /> Bảng giá các gói thuê xe (Pricing Plans)
              </h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {vehicle.rentalPlans.map((plan, idx) => (
                  <div key={idx} className="rounded-xl border border-slate-200 bg-white p-3 shadow-xs space-y-1">
                    <span className="block font-bold text-brand-700 uppercase tracking-wider text-[11px]">
                      Gói thuê: {plan.planType}
                    </span>
                    <div className="text-sm font-extrabold text-slate-900">
                      {formatVND(plan.slotPrice)}
                      <span className="text-[10px] font-normal text-slate-500">
                        {' '}
                        / {plan.slotLengthMinutes ? `${plan.slotLengthMinutes / 60}h` : 'slot'}
                      </span>
                    </div>
                    {plan.upfrontAmount != null && (
                      <div className="text-[11px] text-slate-500">
                        Cọc trước: <strong>{formatVND(plan.upfrontAmount)}</strong>
                      </div>
                    )}
                    {plan.overtimeSurchargeRatio != null && (
                      <div className="text-[11px] text-slate-500">
                        Hệ số ngoài giờ: <strong>{plan.overtimeSurchargeRatio}x</strong>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Reject Reason Form (if opened) */}
          {showRejectForm && (
            <form onSubmit={handleRejectSubmit} className="rounded-2xl border border-rose-200 bg-rose-50/70 p-4 space-y-3">
              <div className="flex items-center gap-2 text-rose-900 font-bold">
                <IconAlertTriangle className="h-4 w-4 text-rose-600" />
                <span>Nhập lý do từ chối kiểm duyệt xe này</span>
              </div>
              <textarea
                rows="3"
                required
                placeholder="Ví dụ: Giấy đăng kiểm đã hết hạn, ảnh chụp biển số xe bị mờ, thiếu bảo hiểm TNDS bắt buộc..."
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="w-full rounded-xl border border-rose-300 bg-white p-3 text-xs outline-none focus:border-rose-500"
              />
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowRejectForm(false)}
                  className="rounded-xl border border-slate-200 bg-white px-4 py-2 font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="rounded-xl bg-rose-600 px-4 py-2 font-bold text-white hover:bg-rose-700"
                >
                  {actionLoading ? 'Đang gửi...' : 'Xác nhận từ chối'}
                </button>
              </div>
            </form>
          )}
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
            {!showRejectForm && vehicle.status !== 'REJECTED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => setShowRejectForm(true)}
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                Từ chối duyệt xe
              </button>
            )}

            {vehicle.status !== 'APPROVED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onApprove(vehicle.id)}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>Phê duyệt mở bán xe</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
