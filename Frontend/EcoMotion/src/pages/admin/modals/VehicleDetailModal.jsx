import { useState } from 'react'
import {
  IconX,
  IconCheckCircle,
  IconEye,
  IconCar,
  IconAlertTriangle,
  IconTag,
} from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'
import AdminPopupModal from './AdminPopupModal'
import { VEHICLE_STATUS_BADGES } from '../../../utils/vehicleStatus'
import { formatAdminRating } from '../../../utils/adminRating'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const STATUS_BADGES = VEHICLE_STATUS_BADGES

const DOC_TYPE_LABELS = {
  REGISTRATION: 'Đăng ký xe (Cà-vẹt)',
  INSPECTION: 'Giấy chứng nhận Đăng kiểm',
  INSURANCE: 'Bảo hiểm xe bắt buộc',
  BUSINESS_BADGE: 'Phù hiệu xe hợp đồng / Kinh doanh',
  OTHER: 'Giấy tờ pháp lý khác',
}

function classifyVehicleDocuments(documents = []) {
  if (!Array.isArray(documents) || documents.length === 0) {
    return { latestDocs: [], olderDocs: [], countByType: {} }
  }
  const maxIdByType = {}
  const countByType = {}
  for (const d of documents) {
    const typeKey = d.docType || 'OTHER'
    countByType[typeKey] = (countByType[typeKey] || 0) + 1
    const numId = Number(d.id || 0)
    if (maxIdByType[typeKey] === undefined || numId > maxIdByType[typeKey]) {
      maxIdByType[typeKey] = numId
    }
  }

  const latestDocs = []
  const olderDocs = []
  for (const d of documents) {
    const typeKey = d.docType || 'OTHER'
    const isLatest =
      typeof d.isLatest === 'boolean'
        ? d.isLatest
        : Number(d.id || 0) === maxIdByType[typeKey]
    if (isLatest) {
      latestDocs.push(d)
    } else {
      olderDocs.push(d)
    }
  }
  return { latestDocs, olderDocs, countByType }
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
  const [popupModal, setPopupModal] = useState(null)

  if (!vehicle) return null

  const images = vehicle.imageUrls && vehicle.imageUrls.length > 0 ? vehicle.imageUrls : []
  const vehicleRating = formatAdminRating(vehicle.ratingAvg, vehicle.ratingCount, 'lượt')

  function handleRejectSubmit(e) {
    e.preventDefault()
    if (!rejectReason.trim()) {
      setPopupModal({
        mode: 'alert',
        tone: 'warning',
        title: 'Thiếu lý do từ chối',
        message: 'Vui lòng nhập lý do từ chối duyệt xe.',
        confirmText: 'Đã hiểu',
      })
      return
    }
    onReject(vehicle.id, rejectReason.trim())
  }

  return (
    <AdminModalShell
      maxWidth="max-w-6xl"
      header={(
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
              <IconCar className="h-5 w-5" />
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
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
      )}
      footer={showRejectForm ? (
        <form onSubmit={handleRejectSubmit} noValidate className="space-y-2.5 rounded-2xl border border-rose-200 bg-rose-50/70 p-3 text-xs">
          <div className="flex items-center gap-2 font-bold text-rose-900">
            <IconAlertTriangle className="h-4 w-4 text-rose-600" />
            <span>Nhập lý do từ chối kiểm duyệt xe này</span>
          </div>
          <textarea
            rows="2"
            required
            placeholder="Ví dụ: Giấy đăng kiểm đã hết hạn, ảnh chụp biển số xe bị mờ, thiếu bảo hiểm TNDS bắt buộc..."
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            className="w-full rounded-xl border border-rose-300 bg-white p-2.5 text-xs outline-none focus:border-rose-500"
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
              className="rounded-xl bg-rose-600 px-4 py-2 font-bold text-white hover:bg-rose-700 disabled:opacity-50"
            >
              {actionLoading ? 'Đang gửi...' : 'Xác nhận từ chối'}
            </button>
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>

          <div className="flex items-center gap-2">
            {vehicle.status !== 'REJECTED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => setShowRejectForm(true)}
                className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                Từ chối duyệt xe
              </button>
            )}

            {vehicle.status !== 'APPROVED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onApprove(vehicle.id)}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>Phê duyệt mở bán xe</span>
              </button>
            )}
          </div>
        </div>
      )}
      extraOverlay={(
        <AdminPopupModal
          open={Boolean(popupModal)}
          mode={popupModal?.mode}
          tone={popupModal?.tone}
          title={popupModal?.title}
          message={popupModal?.message}
          confirmText={popupModal?.confirmText}
          cancelText={popupModal?.cancelText}
          onClose={() => setPopupModal(null)}
          onConfirm={popupModal?.onConfirm}
        />
      )}
    >
      <div className="grid grid-cols-1 items-start gap-3.5 text-xs lg:grid-cols-12">
        {/* Left Column: Image Gallery + Provider + Pricing Plans */}
        <div className="space-y-3 lg:col-span-5">
          {images.length > 0 ? (
            <div className="space-y-2">
              <div className="relative h-44 w-full overflow-hidden rounded-2xl border border-slate-200 bg-slate-900 sm:h-48">
                <img
                  src={images[activeImageIndex]}
                  alt="Ảnh xe chi tiết"
                  className="h-full w-full object-contain"
                />
                <span className="absolute bottom-2.5 right-2.5 rounded-lg bg-black/70 px-2 py-0.5 text-[11px] font-bold text-white">
                  Ảnh {activeImageIndex + 1} / {images.length}
                </span>
              </div>

              {images.length > 1 && (
                <div className="flex gap-2 overflow-x-auto pb-0.5">
                  {images.map((img, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => setActiveImageIndex(idx)}
                      className={`relative h-11 w-16 shrink-0 overflow-hidden rounded-xl border-2 transition-all ${
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
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-5 text-center text-slate-400">
              <IconCar className="mx-auto mb-1.5 h-7 w-7 opacity-50" />
              Chưa có hình ảnh nào được tải lên cho xe này.
            </div>
          )}

          {/* Provider & Depot Location */}
          <div
            onClick={() => vehicle.providerId && onSelectProvider && onSelectProvider(vehicle.providerId)}
            className={`rounded-2xl border p-3 transition-all ${
              vehicle.providerId && onSelectProvider
                ? 'group cursor-pointer border-slate-200 bg-slate-50/60 hover:border-indigo-400 hover:bg-indigo-50/40'
                : 'border-slate-200 bg-slate-50/60'
            }`}
          >
            <div className="mb-1.5 flex items-center justify-between gap-2">
              <h4 className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                <span>📍</span> Nhà cung cấp &amp; Bãi xe (Depot)
              </h4>
              {vehicle.providerId && onSelectProvider && (
                <span className="text-[11px] font-bold text-indigo-600 transition-transform group-hover:translate-x-0.5">
                  Xem NCC →
                </span>
              )}
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <span className="block text-slate-500">Đơn vị chủ xe:</span>
                <strong className="text-slate-900 group-hover:text-indigo-700">
                  {vehicle.providerDisplayName || `NCC #${vehicle.providerId}`}
                </strong>
              </div>
              <div>
                <span className="block text-slate-500">Hotline liên hệ:</span>
                <strong className="font-bold text-brand-700">{vehicle.providerHotline || 'Chưa cung cấp'}</strong>
              </div>
              <div className="col-span-2">
                <span className="block text-slate-500">Bãi xe đón khách:</span>
                <strong className="text-slate-900">{vehicle.providerDepotAddress || 'Chưa cung cấp'}</strong>
              </div>
            </div>
          </div>

          {/* Rental Pricing Plans */}
          {vehicle.rentalPlans && vehicle.rentalPlans.length > 0 && (
            <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
              <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                <IconTag className="h-3.5 w-3.5 text-brand-600" /> Bảng giá gói thuê xe ({vehicle.rentalPlans.length})
              </h4>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {vehicle.rentalPlans.map((plan, idx) => (
                  <div key={idx} className="space-y-0.5 rounded-xl border border-slate-200 bg-white p-2.5 shadow-xs">
                    <span className="block text-[10px] font-bold uppercase tracking-wider text-brand-700">
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
                        Quá giờ: <strong>+{Math.round(Number(plan.overtimeSurchargeRatio) * 100)}%</strong>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Specs & Legal Documents */}
        <div className="space-y-3 lg:col-span-7">
          {/* Technical Specifications & Identity */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
            <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span>⚙️</span> Thông số kỹ thuật &amp; Định danh phương tiện
            </h4>
            <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
              <div>
                <span className="block text-slate-500">Số chỗ ngồi:</span>
                <strong className="text-slate-900">{vehicle.seatCount} chỗ</strong>
              </div>
              <div>
                <span className="block text-slate-500">Nhiên liệu:</span>
                <strong className="text-slate-900">{vehicle.fuelType || 'Điện (EV)'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Hộp số:</span>
                <strong className="text-slate-900">{vehicle.transmission || 'Tự động'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Màu sắc ngoại thất:</span>
                <strong className="text-slate-900">{vehicle.color || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Số khung (Chassis):</span>
                <strong className="block break-all font-mono text-[11px] text-slate-900">{vehicle.chassisNo || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Số máy (Engine):</span>
                <strong className="block break-all font-mono text-[11px] text-slate-900">{vehicle.engineNo || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Buffer chuẩn bị:</span>
                <strong className="text-slate-900">{vehicle.maintenanceBufferMinutes || 30} phút</strong>
              </div>
              <div>
                <span className="block text-slate-500">Đánh giá trung bình:</span>
                <strong className={vehicleRating ? 'text-slate-900' : 'text-slate-500'}>
                  {vehicleRating || 'Chưa có'}
                </strong>
              </div>
            </div>
          </div>

          {/* Mandatory Legal Documents */}
          <div>
            <h4 className="mb-2 flex flex-wrap items-center justify-between gap-2 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span className="flex items-center gap-1.5">
                <span>📑</span> Hồ sơ pháp lý xe ({vehicle.documents?.length || 0} tài liệu)
              </span>
              <span className="text-[10px] font-normal text-slate-400">
                Phân loại bản hiện hành &amp; lịch sử đăng ký cũ
              </span>
            </h4>

            {(!vehicle.documents || vehicle.documents.length === 0) ? (
              <div className="rounded-2xl border border-dashed border-rose-200 bg-rose-50/50 p-3.5 text-center font-medium text-rose-700">
                ⚠️ Cảnh báo: Chủ xe chưa tải lên bất kỳ giấy tờ pháp lý nào! Cần yêu cầu bổ sung trước khi duyệt mở bán.
              </div>
            ) : (() => {
              const { latestDocs, olderDocs, countByType } = classifyVehicleDocuments(vehicle.documents)
              return (
                <div className="space-y-3">
                  {/* Latest / New Registration Documents */}
                  <div className="space-y-2.5 rounded-2xl border border-indigo-200/80 bg-indigo-50/30 p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="rounded-lg bg-indigo-600 px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-white">
                          Hiện hành ({latestDocs.length})
                        </span>
                        <span className="text-xs font-bold text-slate-800">
                          Hồ sơ pháp lý đang áp dụng thẩm định
                        </span>
                      </div>
                      {olderDocs.length > 0 && (
                        <span className="text-[11px] font-medium text-indigo-700">
                          Có {olderDocs.length} bản cũ bên dưới
                        </span>
                      )}
                    </div>

                    <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                      {latestDocs.map((doc) => {
                        const isDocExpired = doc.expiryDate ? new Date(doc.expiryDate) < new Date() : false
                        const hasOlderVersion = (countByType[doc.docType || 'OTHER'] || 0) > 1
                        return (
                          <div
                            key={doc.id}
                            className={`flex flex-col justify-between rounded-2xl border p-2.5 shadow-xs ${
                              doc.status === 'PENDING' || hasOlderVersion
                                ? 'border-indigo-300 bg-white ring-1 ring-indigo-500/20'
                                : 'border-slate-200 bg-white'
                            }`}
                          >
                            <div className="space-y-1">
                              <div className="flex flex-wrap items-center justify-between gap-1">
                                <span className="block text-[11px] font-bold text-brand-700">
                                  {DOC_TYPE_LABELS[doc.docType] || doc.docType}
                                </span>
                                {hasOlderVersion || doc.status === 'PENDING' ? (
                                  <span className="rounded-md bg-indigo-600 px-1.5 py-0.5 text-[9px] font-extrabold uppercase text-white">
                                    Đăng ký mới
                                  </span>
                                ) : (
                                  <span className="rounded-md border border-emerald-200 bg-emerald-50 px-1.5 py-0.5 text-[9px] font-bold uppercase text-emerald-700">
                                    Hiện hành
                                  </span>
                                )}
                              </div>
                              <div className="text-[11px] text-slate-800">
                                <strong>Số hiệu:</strong> <span className="font-mono font-semibold">{doc.docNumber || '—'}</span>
                              </div>
                              <div className="flex items-center gap-1 text-[11px] text-slate-500">
                                <span>Hạn:</span>
                                <strong className={isDocExpired ? 'font-bold text-rose-600' : 'text-slate-700'}>
                                  {doc.expiryDate || 'Vô thời hạn'}
                                </strong>
                                {isDocExpired && (
                                  <span className="rounded bg-rose-100 px-1 text-[10px] font-bold text-rose-700">Hết hạn</span>
                                )}
                              </div>
                              <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                                <span
                                  className={`inline-block rounded border px-1.5 py-0.5 text-[10px] font-bold ${
                                    STATUS_BADGES[doc.status] || 'bg-slate-100 text-slate-700'
                                  }`}
                                >
                                  {doc.status}
                                </span>
                                <span className="font-mono text-[10px] text-slate-400">#{doc.id}</span>
                                {doc.createdAt && (
                                  <span className="text-[10px] text-slate-400">
                                    • {new Date(doc.createdAt).toLocaleDateString('vi-VN')}
                                  </span>
                                )}
                              </div>
                            </div>

                            {doc.fileUrl && (
                              <button
                                type="button"
                                onClick={() => onPreviewDoc(doc.fileUrl)}
                                className="mt-2 flex items-center justify-center gap-1 rounded-xl border border-indigo-200 bg-indigo-50/70 py-1.5 text-[11px] font-bold text-indigo-700 hover:bg-indigo-100"
                              >
                                <IconEye className="h-3.5 w-3.5" />
                                <span>Xem tài liệu mới</span>
                              </button>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  </div>

                  {/* Older / Previous Registration Documents */}
                  {olderDocs.length > 0 && (
                    <div className="space-y-2 rounded-2xl border border-slate-200 bg-slate-50/80 p-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="rounded-lg bg-slate-500 px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-white">
                          Bản đăng ký cũ ({olderDocs.length})
                        </span>
                        <span className="text-xs font-bold text-slate-700">
                          Lịch sử hồ sơ pháp lý đã nộp trước đó
                        </span>
                      </div>

                      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                        {olderDocs.map((doc) => {
                          const isDocExpired = doc.expiryDate ? new Date(doc.expiryDate) < new Date() : false
                          return (
                            <div
                              key={doc.id}
                              className="flex flex-col justify-between rounded-2xl border border-dashed border-slate-300 bg-white/80 p-2.5 opacity-90"
                            >
                              <div className="space-y-1">
                                <div className="flex flex-wrap items-center justify-between gap-1">
                                  <span className="block text-[11px] font-bold text-slate-600">
                                    {DOC_TYPE_LABELS[doc.docType] || doc.docType}
                                  </span>
                                  <span className="rounded-md border border-slate-300 bg-slate-100 px-1.5 py-0.5 text-[9px] font-extrabold uppercase text-slate-600">
                                    Bản cũ
                                  </span>
                                </div>
                                <div className="text-[11px] text-slate-700">
                                  <strong>Số hiệu:</strong> <span className="font-mono">{doc.docNumber || '—'}</span>
                                </div>
                                <div className="flex items-center gap-1 text-[11px] text-slate-500">
                                  <span>Hạn:</span>
                                  <strong className={isDocExpired ? 'font-bold text-rose-600' : 'text-slate-600'}>
                                    {doc.expiryDate || 'Vô thời hạn'}
                                  </strong>
                                </div>
                                <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                                  <span
                                    className={`inline-block rounded border px-1.5 py-0.5 text-[10px] font-bold ${
                                      STATUS_BADGES[doc.status] || 'bg-slate-100 text-slate-700'
                                    }`}
                                  >
                                    {doc.status}
                                  </span>
                                  <span className="font-mono text-[10px] text-slate-400">#{doc.id}</span>
                                  {doc.createdAt && (
                                    <span className="text-[10px] text-slate-400">
                                      • {new Date(doc.createdAt).toLocaleDateString('vi-VN')}
                                    </span>
                                  )}
                                </div>
                              </div>

                              {doc.fileUrl && (
                                <button
                                  type="button"
                                  onClick={() => onPreviewDoc(doc.fileUrl)}
                                  className="mt-2 flex items-center justify-center gap-1 rounded-xl border border-slate-200 bg-slate-100 py-1.5 text-[11px] font-semibold text-slate-600 hover:bg-slate-200"
                                >
                                  <IconEye className="h-3.5 w-3.5" />
                                  <span>Xem bản lưu cũ</span>
                                </button>
                              )}
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )
            })()}
          </div>
        </div>
      </div>
    </AdminModalShell>
  )
}
