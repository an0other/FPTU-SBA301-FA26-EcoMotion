import { AdminYearlyReviewPanel } from '../../../components/booking/FinancialReviewPanels'
import { useState } from 'react'
import { IconX, IconAlertTriangle, IconCheckCircle, IconShield, IconCar, IconUsers } from '../../../components/icons/Icons'
import AdminPopupModal from './AdminPopupModal'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const DISPUTE_STATUS_BADGES = {
  OPEN: 'bg-rose-50 text-rose-700 border-rose-200',
  IN_REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
  RESOLVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTED: 'bg-slate-100 text-slate-700 border-slate-200',
}

export default function DisputeDetailModal({ dispute, ...props }) {
  if (dispute && ['YEARLY_SETTLEMENT', 'YEARLY_CANCELLATION'].includes(dispute.category)) return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"><section role="dialog" aria-modal="true" aria-label="Phân xử hợp đồng năm" className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-white p-5"><button type="button" onClick={props.onClose} className="mb-3 rounded-lg border px-3 py-2">Đóng</button><p className="mb-3">{dispute.description}</p>{['OPEN', 'IN_REVIEW'].includes(dispute.status) ? <AdminYearlyReviewPanel dispute={dispute} onChanged={() => { props.onChanged?.(); props.onClose?.() }} /> : <p>{dispute.resolution}</p>}</section></div>
  return dispute ? <DisputeDetailForm key={dispute.id} dispute={dispute} {...props} /> : null
}

function DisputeDetailForm({
  dispute, onClose, onResolve, actionLoading, error,
  onSelectBooking, onSelectUser, onSelectVehicle, onSelectProvider, onSelectDriver,
}) {
  const isResolvedOrRejected = dispute.status === 'RESOLVED' || dispute.status === 'REJECTED'

  const [form, setForm] = useState({
    status: isResolvedOrRejected ? dispute.status : 'RESOLVED',
    decision: dispute.decision || 'WAIVE_CHARGE',
    refundAwardTotal: dispute.refundAwardTotal ?? '',
    tripEndAction: '',
    resolution: dispute.resolution || '',
  })
  const [previewImage, setPreviewImage] = useState(null)
  const [popupModal, setPopupModal] = useState(null)

  const evidenceUrls = (() => {
    if (!dispute?.evidenceUrls) return []
    if (Array.isArray(dispute.evidenceUrls)) return dispute.evidenceUrls.filter(Boolean)
    if (typeof dispute.evidenceUrls === 'string') {
      const trimmed = dispute.evidenceUrls.trim()
      if (trimmed.startsWith('[')) {
        try {
          const parsed = JSON.parse(trimmed)
          if (Array.isArray(parsed)) return parsed.filter(Boolean)
        } catch {}
      }
      return trimmed ? [trimmed] : []
    }
    return []
  })()

  function handleSubmit(e) {
    e.preventDefault()
    if (!form.resolution.trim()) {
      setPopupModal({
        mode: 'alert',
        tone: 'warning',
        title: 'Thiếu nội dung phán quyết',
        message: 'Vui lòng nhập nội dung phán quyết / giải pháp phân xử.',
        confirmText: 'Đã hiểu',
      })
      return
    }
    onResolve(dispute.id, form.status,
      dispute.category === 'CHARGE' && form.status !== 'IN_REVIEW' ? form.decision : undefined,
      form.resolution.trim(),
      (dispute.category === 'DEPOSIT_REFUND' || (dispute.category === 'PAYMENT_MISMATCH' && form.refundAwardTotal !== ''))
        && form.status === 'RESOLVED' ? Number(form.refundAwardTotal) : undefined,
      dispute.category === 'TRIP_NOT_CONFIRMED' && form.status !== 'IN_REVIEW' ? form.tripEndAction || undefined : undefined)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div role="dialog" aria-modal="true" aria-label={`Phân xử khiếu nại #${dispute.id}`} className="relative max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-purple-50 text-purple-700">
              <IconAlertTriangle className="h-6 w-6" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xl font-bold text-slate-900">
                  Hồ sơ Phân xử Khiếu nại DISP-#{dispute.id}
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    DISPUTE_STATUS_BADGES[dispute.status] || 'bg-slate-100'
                  }`}
                >
                  {dispute.status}
                </span>
              </div>
              <p className="mt-0.5 text-xs text-slate-500 flex flex-wrap items-center gap-1">
                <span>Liên quan Đơn thuê:</span>
                {dispute.bookingId && onSelectBooking ? (
                  <button
                    type="button"
                    onClick={() => onSelectBooking(dispute.bookingId)}
                    className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2 py-0.5 font-mono font-bold text-brand-700 border border-brand-200 hover:bg-brand-100 transition-colors"
                  >
                    #{dispute.bookingCode || dispute.bookingId} →
                  </button>
                ) : (
                  <strong>#{dispute.bookingCode || dispute.bookingId}</strong>
                )}
                <span>• Phân loại: <strong>{dispute.category}</strong></span>
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
          {/* Interactive Related Entities Grid */}
          <div>
            <span className="mb-2 block text-[11px] font-bold uppercase tracking-wider text-slate-500">
              Các bên &amp; thành phần liên quan (Bấm để xem chi tiết)
            </span>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
              {/* Raised By User */}
              <div
                onClick={() => dispute.raisedById && onSelectUser && onSelectUser(dispute.raisedById)}
                className={`rounded-xl border p-2.5 transition-all ${
                  dispute.raisedById && onSelectUser
                    ? 'cursor-pointer border-slate-200 bg-white hover:border-brand-400 hover:bg-brand-50/40 group'
                    : 'border-transparent'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-slate-500 flex items-center gap-1">
                    <IconUsers className="h-3.5 w-3.5 text-brand-600" /> Bên khiếu nại (Người gửi):
                  </span>
                  {dispute.raisedById && onSelectUser && (
                    <span className="text-[10px] font-bold text-brand-600">Chi tiết →</span>
                  )}
                </div>
                <strong className="text-slate-900 group-hover:text-brand-700 block mt-0.5">
                  {dispute.raisedByName || `User #${dispute.raisedById}`}
                </strong>
                <div className="text-slate-500">{dispute.raisedByEmail || '—'}</div>
              </div>

              {/* Linked Booking */}
              <div
                onClick={() => dispute.bookingId && onSelectBooking && onSelectBooking(dispute.bookingId)}
                className={`rounded-xl border p-2.5 transition-all ${
                  dispute.bookingId && onSelectBooking
                    ? 'cursor-pointer border-slate-200 bg-white hover:border-brand-400 hover:bg-brand-50/40 group'
                    : 'border-transparent'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-slate-500">Đơn thuê liên quan:</span>
                  {dispute.bookingId && onSelectBooking && (
                    <span className="text-[10px] font-bold text-brand-600">Xem đơn →</span>
                  )}
                </div>
                <strong className="text-slate-900 font-mono group-hover:text-brand-700 block mt-0.5">
                  #{dispute.bookingCode || dispute.bookingId}
                </strong>
                <div className="text-slate-500">Mã hệ thống: #{dispute.bookingId}</div>
              </div>

              {/* Vehicle if available */}
              {dispute.vehicleId && (
                <div
                  onClick={() => onSelectVehicle && onSelectVehicle(dispute.vehicleId)}
                  className={`rounded-xl border p-2.5 transition-all ${
                    onSelectVehicle
                      ? 'cursor-pointer border-slate-200 bg-white hover:border-emerald-400 hover:bg-emerald-50/40 group'
                      : 'border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-slate-500 flex items-center gap-1">
                      <IconCar className="h-3.5 w-3.5 text-emerald-600" /> Xe phục vụ:
                    </span>
                    {onSelectVehicle && (
                      <span className="text-[10px] font-bold text-emerald-600">Chi tiết →</span>
                    )}
                  </div>
                  <strong className="text-slate-900 group-hover:text-emerald-700 block mt-0.5">
                    {dispute.vehicleModel || `Xe #${dispute.vehicleId}`}
                  </strong>
                  <div className="text-slate-500 font-mono">{dispute.vehicleLicensePlate || `#${dispute.vehicleId}`}</div>
                </div>
              )}

              {/* Provider if available */}
              {dispute.providerId && (
                <div
                  onClick={() => onSelectProvider && onSelectProvider(dispute.providerId)}
                  className={`rounded-xl border p-2.5 transition-all ${
                    onSelectProvider
                      ? 'cursor-pointer border-slate-200 bg-white hover:border-indigo-400 hover:bg-indigo-50/40 group'
                      : 'border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-slate-500">🏢 Đơn vị chủ xe (NCC):</span>
                    {onSelectProvider && (
                      <span className="text-[10px] font-bold text-indigo-600">Chi tiết →</span>
                    )}
                  </div>
                  <strong className="text-slate-900 group-hover:text-indigo-700 block mt-0.5">
                    {dispute.providerDisplayName || `NCC #${dispute.providerId}`}
                  </strong>
                  <div className="text-slate-500">Mã đối tác: #{dispute.providerId}</div>
                </div>
              )}

              {/* Driver if available */}
              {dispute.driverId && (
                <div
                  onClick={() => onSelectDriver && onSelectDriver(dispute.driverId)}
                  className={`rounded-xl border p-2.5 transition-all ${
                    onSelectDriver
                      ? 'cursor-pointer border-slate-200 bg-white hover:border-amber-400 hover:bg-amber-50/40 group'
                      : 'border-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-slate-500">👤 Tài xế lái xe:</span>
                    {onSelectDriver && (
                      <span className="text-[10px] font-bold text-amber-600">Chi tiết →</span>
                    )}
                  </div>
                  <strong className="text-slate-900 group-hover:text-amber-700 block mt-0.5">
                    {dispute.driverName || `Tài xế #${dispute.driverId}`}
                  </strong>
                  <div className="text-slate-500">Mã TX: #{dispute.driverId}</div>
                </div>
              )}

              {/* Handled By Admin */}
              <div className="p-1">
                <span className="text-slate-500 block">Quản trị viên thụ lý:</span>
                <strong className="text-slate-900">{dispute.handledByName || 'Chưa thụ lý'}</strong>
                <div className="text-slate-500 mt-1">
                  Tạo lúc: {dispute.createdAt ? new Date(dispute.createdAt).toLocaleString('vi-VN') : '—'}
                </div>
              </div>
            </div>
          </div>

          {/* Description of Dispute from User */}
          <div className="rounded-2xl border border-rose-200 bg-rose-50/40 p-4 space-y-2">
            <div className="flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-rose-100 text-rose-700">
                <IconAlertTriangle className="h-3.5 w-3.5" />
              </span>
              <span className="font-bold uppercase tracking-wider text-rose-900 text-[11px]">
                Nội dung khiếu nại / Lý do chi tiết từ khách thuê
              </span>
            </div>
            <p className="rounded-xl border border-rose-200 bg-white p-3 font-semibold text-slate-800 text-xs whitespace-pre-wrap leading-relaxed shadow-2xs">
              {dispute.description || <span className="text-slate-400 italic">Không có mô tả chi tiết</span>}
            </p>
          </div>

          {dispute.category === 'PAYMENT_MISMATCH' && (
            <div className="grid grid-cols-1 gap-2 rounded-2xl border border-amber-200 bg-amber-50/60 p-4 text-xs sm:grid-cols-3">
              <div><p className="font-semibold text-amber-900">Loại sai lệch</p><p className="mt-1 font-bold text-slate-900">{{ OVERPAID: 'Khách chuyển dư', UNDER_RECORDED: 'Nhà xe ghi thiếu', OTHER: 'Khác' }[dispute.mismatchType] || '—'}</p></div>
              <div><p className="font-semibold text-amber-900">Khách khai đã chuyển</p><p className="mt-1 font-bold text-slate-900">{dispute.claimedAmount != null ? formatVND(dispute.claimedAmount) : '—'}</p></div>
              <div><p className="font-semibold text-amber-900">Nhà xe đã ghi nhận</p><p className="mt-1 font-bold text-slate-900">{dispute.recordedAmount != null ? formatVND(dispute.recordedAmount) : '—'}</p></div>
            </div>
          )}

          {/* Evidence Photos Gallery */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 space-y-2">
            <div className="flex items-center justify-between">
              <span className="block font-bold text-slate-700 uppercase tracking-wider text-[11px]">
                Hình ảnh bằng chứng đính kèm ({evidenceUrls.length} ảnh)
              </span>
              {evidenceUrls.length > 0 && (
                <span className="text-[10px] text-slate-500">Bấm ảnh để phóng to</span>
              )}
            </div>
            {evidenceUrls.length > 0 ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 pt-1">
                {evidenceUrls.map((url, idx) => (
                  <div
                    key={idx}
                    onClick={() => setPreviewImage(url)}
                    className="group relative aspect-video overflow-hidden rounded-xl border border-slate-200 bg-white cursor-pointer shadow-xs hover:border-brand-500 hover:shadow-md transition-all"
                  >
                    <img
                      src={url}
                      alt={`Bằng chứng ${idx + 1}`}
                      className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
                    />
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white text-[11px] font-bold backdrop-blur-2xs">
                      Xem ảnh lớn
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic py-1">
                Khách thuê không đính kèm hình ảnh bằng chứng cho khiếu nại này.
              </p>
            )}
          </div>

          {/* Charge in Dispute */}
          {dispute.chargeId && (() => {
            const resolvedChargeDecision =
              dispute.decision ||
              (isResolvedOrRejected
                ? dispute.chargeWaived === true || dispute.status === 'RESOLVED'
                  ? 'WAIVE_CHARGE'
                  : 'KEEP_CHARGE'
                : null)
            const isChargeWaived =
              resolvedChargeDecision === 'WAIVE_CHARGE' || dispute.chargeWaived === true

            return (
              <div className="rounded-2xl border border-purple-200 bg-purple-50/40 p-4 space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="block font-bold text-purple-900 uppercase tracking-wider text-[11px]">
                    Khoản phụ phí đang tranh chấp #{dispute.chargeId}
                  </span>
                  {isResolvedOrRejected && (
                    <span
                      className={`rounded-full border px-2.5 py-0.5 text-[10px] font-extrabold uppercase ${
                        isChargeWaived
                          ? 'border-emerald-300 bg-emerald-100 text-emerald-800'
                          : 'border-amber-300 bg-amber-100 text-amber-800'
                      }`}
                    >
                      {isChargeWaived
                        ? 'Đã miễn giảm phụ phí (0 ₫ phải trả)'
                        : 'Giữ nguyên phụ phí (Khách phải trả)'}
                    </span>
                  )}
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-slate-700 font-medium">{dispute.chargeDescription || 'Phụ phí phát sinh'}</span>
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-base font-extrabold ${
                        isResolvedOrRejected && isChargeWaived
                          ? 'line-through text-slate-400'
                          : 'text-purple-700'
                      }`}
                    >
                      {formatVND(dispute.chargeAmount)}
                    </span>
                    {isResolvedOrRejected && isChargeWaived && (
                      <span className="text-sm font-black text-emerald-700">→ 0 ₫ (Đã gạch bỏ)</span>
                    )}
                  </div>
                </div>
              </div>
            )
          })()}

          {/* If already resolved, show resolution card */}
          {isResolvedOrRejected ? (() => {
            const effectiveDecision =
              dispute.decision ||
              (dispute.category === 'CHARGE' || dispute.chargeId
                ? dispute.chargeWaived === true || dispute.status === 'RESOLVED'
                  ? 'WAIVE_CHARGE'
                  : 'KEEP_CHARGE'
                : null)
            const isWaivedDecision = effectiveDecision === 'WAIVE_CHARGE'

            return (
              <div className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2 font-bold text-emerald-900">
                    <IconCheckCircle className="h-4 w-4 text-emerald-600" />
                    <span>Tranh chấp đã được phân xử xong</span>
                  </div>
                  <span
                    className={`rounded-full border px-2.5 py-0.5 text-[10px] font-extrabold uppercase ${
                      dispute.status === 'RESOLVED'
                        ? 'border-emerald-300 bg-emerald-100 text-emerald-800'
                        : 'border-rose-300 bg-rose-100 text-rose-800'
                    }`}
                  >
                    {dispute.status === 'RESOLVED'
                      ? 'RESOLVED — Chấp thuận khiếu nại'
                      : 'REJECTED — Bác bỏ khiếu nại'}
                  </span>
                </div>

                {/* Explicit Decision Ruling Box for Charge Disputes (C9 / C10) */}
                {(dispute.category === 'CHARGE' || dispute.chargeId || effectiveDecision) && (
                  <div
                    className={`rounded-xl border p-3.5 ${
                      isWaivedDecision
                        ? 'border-emerald-300 bg-emerald-100/60 text-emerald-950'
                        : 'border-amber-300 bg-amber-50/90 text-amber-950'
                    }`}
                  >
                    <div className="text-[11px] font-bold uppercase tracking-wider opacity-75">
                      Quyết định xử lý đã chốt trước đó:
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <span
                        className={`rounded-lg px-2.5 py-1 text-xs font-extrabold text-white ${
                          isWaivedDecision ? 'bg-emerald-700' : 'bg-amber-700'
                        }`}
                      >
                        {isWaivedDecision
                          ? 'MIỄN GIẢM PHỤ PHÍ (WAIVE_CHARGE)'
                          : 'GIỮ NGUYÊN PHỤ PHÍ (KEEP_CHARGE)'}
                      </span>
                      <span className="text-xs font-bold">
                        {isWaivedDecision
                          ? `Đã gạch bỏ khoản phụ phí ${formatVND(dispute.chargeAmount)} khỏi quyết toán của khách thuê`
                          : `Giữ nguyên khoản phụ phí ${formatVND(dispute.chargeAmount)} — Khách thuê phải thanh toán`}
                      </span>
                    </div>
                    <p className="mt-1.5 text-[11px] opacity-90">
                      {isWaivedDecision
                        ? 'Hệ thống đã cập nhật trạng thái miễn phí (waived = true) cho khoản phụ phí này trong hóa đơn quyết toán của đơn thuê.'
                        : 'Khoản phụ phí vẫn được giữ hiệu lực thu (waived = false) trong hóa đơn quyết toán để khách thuê thanh toán số tiền còn thiếu.'}
                    </p>
                  </div>
                )}

                <div>
                  <span className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                    Nội dung phán quyết / Ghi chú của Admin:
                  </span>
                  <p className="rounded-xl border border-emerald-200 bg-white p-3 font-medium text-slate-800">
                    {dispute.resolution || 'Chưa ghi nhận'}
                  </p>
                </div>

                <p className="mt-2 text-xs text-slate-600">
                  Người xử lý: <strong>{dispute.handledByName || 'Admin'}</strong> · Thời điểm phân xử:{' '}
                  <strong>
                    {dispute.resolvedAt
                      ? new Date(dispute.resolvedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
                      : 'Chưa ghi nhận'}
                  </strong>
                </p>
                {dispute.category === 'TRIP_NOT_CONFIRMED' && !dispute.tripEndAction && <form onSubmit={handleSubmit} className="space-y-2 border-t pt-3">
                  <label className="block">Hành động sau xác minh<select required value={form.tripEndAction} onChange={e => setForm(prev => ({ ...prev, tripEndAction: e.target.value }))} className="block w-full rounded-lg border p-2"><option value="">Chọn hành động</option><option value="RESUME_TRIP">Tiếp tục chuyến</option><option value="REOPEN_CONFIRMATION">Mở lại thời hạn xác nhận 12 giờ</option></select></label>
                  {error && <p role="alert">{error}</p>}<button disabled={actionLoading} className="min-h-11 rounded-lg bg-emerald-800 px-4 text-white">Áp dụng hành động với chuyến</button>
                </form>}
                {dispute.refundAwardTotal != null && <p className="mt-2 text-sm font-semibold">Tổng mức hoàn theo phán quyết: {formatVND(dispute.refundAwardTotal)}</p>}
              </div>
            )
          })() : (
            /* Resolution Form */
            <form onSubmit={handleSubmit} noValidate className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs space-y-4">
              <h4 className="font-bold uppercase tracking-wider text-slate-600 flex items-center gap-1.5">
                <IconShield className="h-4 w-4 text-brand-600" /> Biểu mẫu Phán quyết &amp; Giải quyết tranh chấp
              </h4>

              {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
              {/* Status Selector */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">
                  Trạng thái sau khi xử lý:
                </label>
                <select
                  value={form.status}
                  onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value, decision: e.target.value === 'REJECTED' ? 'KEEP_CHARGE' : 'WAIVE_CHARGE' }))}
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-xs font-semibold text-slate-700 outline-none focus:border-brand-500"
                >
                  <option value="RESOLVED">RESOLVED (Đã phân xử chấp thuận)</option>
                  <option value="REJECTED">REJECTED (Bác bỏ khiếu nại)</option>
                  <option value="IN_REVIEW">IN_REVIEW (Đang điều tra thêm)</option>
                </select>
              </div>

              {/* Decision Radio for Charges */}
              {dispute.category === 'CHARGE' && form.status !== 'IN_REVIEW' && (
                <div className="space-y-2 rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <span className="block font-bold text-slate-700">Phương án xử lý khoản tiền:</span>
                  <div className="space-y-2">
                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="decision"
                        value="KEEP_CHARGE"
                        disabled={form.status !== 'REJECTED'}
                        checked={form.decision === 'KEEP_CHARGE'}
                        onChange={(e) => setForm((prev) => ({ ...prev, decision: e.target.value }))}
                        className="mt-0.5"
                      />
                      <div>
                        <strong className="block text-slate-900">KEEP_CHARGE (Giữ nguyên khoản phụ phí)</strong>
                        <span className="text-slate-500 text-[11px]">
                          Khách thuê phải thanh toán khoản phí này cho nhà cung cấp theo đúng hợp đồng.
                        </span>
                      </div>
                    </label>

                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="decision"
                        value="WAIVE_CHARGE"
                        disabled={form.status !== 'RESOLVED'}
                        checked={form.decision === 'WAIVE_CHARGE'}
                        onChange={(e) => setForm((prev) => ({ ...prev, decision: e.target.value }))}
                        className="mt-0.5"
                      />
                      <div>
                        <strong className="block text-emerald-800">WAIVE_CHARGE (Miễn giảm khoản phụ phí)</strong>
                        <span className="text-slate-500 text-[11px]">
                          Hệ thống sẽ gạch bỏ phụ phí này khỏi quyết toán của khách thuê.
                        </span>
                      </div>
                    </label>
                  </div>
                </div>
              )}

              {dispute.category === 'DEPOSIT_REFUND' && form.status === 'RESOLVED' && <label className="block space-y-2 font-semibold text-slate-800">
                <span>Tổng tiền cọc khách được hoàn (đ)</span>
                <input type="number" required min="0.01" step="0.01" value={form.refundAwardTotal}
                  onChange={e => setForm(prev => ({ ...prev, refundAwardTotal: e.target.value }))}
                  className="block min-h-11 w-full rounded-lg border border-slate-300 px-3" />
                <span className="block text-xs font-normal text-slate-600">Nhập tổng mức hoàn theo phán quyết. Hệ thống trừ phần đã hoàn hoặc đang xử lý để chỉ tạo phần thiếu; chưa chuyển tiền ở bước này.</span>
              </label>}
              {dispute.category === 'PAYMENT_MISMATCH' && form.status === 'RESOLVED' && <label className="block space-y-2 font-semibold text-slate-800">
                <span>Tổng số khách thực chuyển cho khoản này (đ) — đã đối chiếu sao kê</span>
                <input type="number" min="1" step="1" value={form.refundAwardTotal}
                  onChange={e => setForm(prev => ({ ...prev, refundAwardTotal: e.target.value }))}
                  placeholder={dispute.claimedAmount != null ? String(Math.round(Number(dispute.claimedAmount))) : ''}
                  className="block min-h-11 w-full rounded-lg border border-slate-300 px-3" />
                <span className="block text-xs font-normal text-slate-600">Hệ thống ghi nhận phần chênh lệch so với số nhà xe đã ghi: phần còn thiếu của khoản được áp vào, phần dư thành khoản nhà xe bắt buộc hoàn cho khách. Để trống nếu chỉ ghi kết luận.</span>
              </label>}
              {dispute.category === 'TRIP_NOT_CONFIRMED' && form.status !== 'IN_REVIEW' && <label className="block">Hành động với chuyến
                <select value={form.tripEndAction} onChange={e => setForm(prev => ({ ...prev, tripEndAction: e.target.value }))} className="mt-2 block w-full rounded-lg border p-3">
                  <option value="">Chỉ ghi kết luận, giữ nguyên tạm dừng</option><option value="RESUME_TRIP">Tiếp tục chuyến</option><option value="REOPEN_CONFIRMATION">Mở lại thời hạn xác nhận 12 giờ</option>
                </select></label>}
              {/* Resolution Notes */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">
                  Nội dung phán quyết / Ghi chú giải quyết <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows="3"
                  required
                  maxLength={1000}
                  placeholder="Ghi rõ lý do phân xử, căn cứ biên bản bàn giao xe hoặc thỏa thuận giữa các bên..."
                  value={form.resolution}
                  onChange={(e) => setForm((prev) => ({ ...prev, resolution: e.target.value }))}
                  className="w-full rounded-xl border border-slate-300 p-3 text-xs outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs text-slate-600 hover:bg-slate-50"
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="rounded-xl bg-purple-700 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-purple-800"
                >
                  {actionLoading ? 'Đang lưu...' : 'Lưu kết quả phân xử'}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>

      {/* Full-size Image Preview Modal */}
      {previewImage && (
        <div
          className="fixed inset-0 z-60 flex items-center justify-center bg-black/80 p-4"
          onClick={() => setPreviewImage(null)}
        >
          <div className="relative max-w-4xl max-h-[88vh] overflow-hidden rounded-2xl bg-white p-2 shadow-2xl">
            <button
              type="button"
              onClick={() => setPreviewImage(null)}
              className="absolute right-3 top-3 rounded-full bg-black/60 p-1.5 text-white hover:bg-black"
            >
              <IconX className="h-5 w-5" />
            </button>
            <img
              src={previewImage}
              alt="Xem chi tiết bằng chứng"
              className="max-h-[82vh] w-auto object-contain rounded-xl"
            />
          </div>
        </div>
      )}

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
    </div>
  )
}
