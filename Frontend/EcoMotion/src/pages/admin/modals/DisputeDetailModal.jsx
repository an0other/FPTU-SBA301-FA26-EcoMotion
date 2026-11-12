import AdminYearlyExceptionPanel from '../../../components/booking/AdminYearlyExceptionPanel'
import { AdminYearlyReviewPanel } from '../../../components/booking/FinancialReviewPanels'
import { TRIP_END_ADVANCED, buildDisputeResolution, defaultVerdict, disputeVerdictLabel, verdictOptions } from '../../../utils/disputeVerdict'
import { useState } from 'react'
import { IconX, IconAlertTriangle, IconCheckCircle, IconShield, IconCar, IconUsers } from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'
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
  if (dispute && ['YEARLY_SETTLEMENT', 'YEARLY_CANCELLATION', 'YEARLY_REFUND', 'YEARLY_INCIDENT'].includes(dispute.category)) {
    return (
      <AdminModalShell
        maxWidth="max-w-4xl"
        role="dialog"
        ariaModal="true"
        ariaLabel="Phân xử hợp đồng năm"
        panelClassName="rounded-2xl border border-slate-200 bg-white shadow-2xl"
        header={(
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-bold text-slate-900">Phân xử hợp đồng năm</h3>
                {dispute.bookingCode && (
                  <span className="rounded-md border border-brand-200 bg-brand-50 px-2 py-0.5 font-mono text-xs font-bold text-brand-700">
                    #{dispute.bookingCode}
                  </span>
                )}
              </div>
              {dispute.description && (
                <p className="mt-1 text-xs text-slate-600">{dispute.description}</p>
              )}
            </div>
            <button
              type="button"
              onClick={props.onClose}
              className="shrink-0 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Đóng
            </button>
          </div>
        )}
      >
        {['OPEN', 'IN_REVIEW'].includes(dispute.status) ? (
          ['YEARLY_REFUND', 'YEARLY_INCIDENT'].includes(dispute.category) ? (
            <AdminYearlyExceptionPanel
              dispute={dispute}
              onChanged={() => {
                props.onChanged?.()
                props.onClose?.()
              }}
            />
          ) : (
            <AdminYearlyReviewPanel
              dispute={dispute}
              onChanged={() => {
                props.onChanged?.()
                props.onClose?.()
              }}
            />
          )
        ) : (
          <p className="text-sm text-slate-700">{dispute.resolution}</p>
        )}
      </AdminModalShell>
    )
  }
  return dispute ? <DisputeDetailForm key={dispute.id} dispute={dispute} {...props} /> : null
}

function DisputeDetailForm({
  dispute, onClose, onResolve, actionLoading, error,
  onSelectBooking, onSelectUser, onSelectVehicle, onSelectProvider, onSelectDriver,
}) {
  const isResolvedOrRejected = dispute.status === 'RESOLVED' || dispute.status === 'REJECTED'
  const formId = `dispute-resolve-form-${dispute.id}`

  const [form, setForm] = useState({
    status: isResolvedOrRejected ? dispute.status : 'RESOLVED',
    decision: dispute.decision || 'WAIVE_CHARGE',
    verdict: defaultVerdict(dispute.category),
    correctedEndAt: '',
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
        } catch { /* ignore malformed evidence JSON */ }
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
    if (isResolvedOrRejected) {
      onResolve(dispute.id, dispute.status, dispute.decision || undefined, form.resolution.trim(),
        dispute.refundAwardTotal ?? undefined, form.tripEndAction || undefined)
      return
    }
    const payload = buildDisputeResolution(dispute.category, form)
    if (payload.error) {
      setPopupModal({ mode: 'alert', tone: 'warning', title: 'Thiếu thông tin phán quyết', message: payload.error, confirmText: 'Đã hiểu' })
      return
    }
    onResolve(dispute.id, payload.status, payload.decision, payload.resolution, payload.refundAwardTotal,
      payload.tripEndAction, payload.correctedEndAt)
  }

  return (
    <AdminModalShell
      maxWidth="max-w-6xl"
      role="dialog"
      ariaModal="true"
      ariaLabel={`Phân xử khiếu nại #${dispute.id}`}
      header={(
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-purple-50 text-purple-700">
              <IconAlertTriangle className="h-5 w-5" />
            </span>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
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
              <p className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-slate-500">
                <span>Liên quan Đơn thuê:</span>
                {dispute.bookingId && onSelectBooking ? (
                  <button
                    type="button"
                    onClick={() => onSelectBooking(dispute.bookingId)}
                    className="inline-flex items-center gap-1 rounded-md border border-brand-200 bg-brand-50 px-2 py-0.5 font-mono font-bold text-brand-700 transition-colors hover:bg-brand-100"
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
      )}
      footer={(
        <div className="flex flex-wrap items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>
          {!isResolvedOrRejected && (
            <button
              type="submit"
              form={formId}
              disabled={actionLoading}
              className="rounded-xl bg-purple-700 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-purple-800 disabled:opacity-50"
            >
              {actionLoading ? 'Đang lưu...' : 'Lưu kết quả phân xử'}
            </button>
          )}
        </div>
      )}
      extraOverlay={(
        <>
          {/* Full-size Image Preview Modal */}
          {previewImage && (
            <div
              className="fixed inset-0 z-60 flex items-center justify-center bg-black/80 p-4"
              onClick={() => setPreviewImage(null)}
            >
              <div className="relative max-h-[88vh] max-w-4xl overflow-hidden rounded-2xl bg-white p-2 shadow-2xl">
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
                  className="max-h-[82vh] w-auto rounded-xl object-contain"
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
        </>
      )}
    >
      <div className="grid grid-cols-1 items-start gap-3.5 text-xs lg:grid-cols-12">
        {/* Left Column: Related Parties + Complaint Description + Evidence + Disputed Charge */}
        <div className="space-y-3 lg:col-span-6">
          {/* Interactive Related Entities Grid */}
          <div>
            <span className="mb-1.5 block text-[11px] font-bold uppercase tracking-wider text-slate-500">
              Các bên &amp; thành phần liên quan (Bấm xem chi tiết)
            </span>
            <div className="grid grid-cols-2 gap-2 rounded-2xl border border-slate-200 bg-slate-50/60 p-2.5 sm:grid-cols-3">
              {/* Raised By User */}
              <div
                onClick={() => dispute.raisedById && onSelectUser && onSelectUser(dispute.raisedById)}
                className={`rounded-xl border p-2 transition-all ${
                  dispute.raisedById && onSelectUser
                    ? 'group cursor-pointer border-slate-200 bg-white hover:border-brand-400 hover:bg-brand-50/40'
                    : 'border-slate-200/60 bg-white/70'
                }`}
              >
                <div className="flex items-center justify-between text-[11px]">
                  <span className="flex items-center gap-1 text-slate-500">
                    <IconUsers className="h-3 w-3 text-brand-600" /> Bên khiếu nại
                  </span>
                  {dispute.raisedById && onSelectUser && (
                    <span className="text-[10px] font-bold text-brand-600">→</span>
                  )}
                </div>
                <strong className="mt-0.5 block truncate text-slate-900 group-hover:text-brand-700">
                  {dispute.raisedByName || `User #${dispute.raisedById}`}
                </strong>
                <div className="truncate text-[11px] text-slate-500">{dispute.raisedByEmail || '—'}</div>
              </div>

              {/* Linked Booking */}
              <div
                onClick={() => dispute.bookingId && onSelectBooking && onSelectBooking(dispute.bookingId)}
                className={`rounded-xl border p-2 transition-all ${
                  dispute.bookingId && onSelectBooking
                    ? 'group cursor-pointer border-slate-200 bg-white hover:border-brand-400 hover:bg-brand-50/40'
                    : 'border-slate-200/60 bg-white/70'
                }`}
              >
                <div className="flex items-center justify-between text-[11px]">
                  <span className="text-slate-500">Đơn thuê</span>
                  {dispute.bookingId && onSelectBooking && (
                    <span className="text-[10px] font-bold text-brand-600">Xem →</span>
                  )}
                </div>
                <strong className="mt-0.5 block truncate font-mono text-slate-900 group-hover:text-brand-700">
                  #{dispute.bookingCode || dispute.bookingId}
                </strong>
                <div className="text-[11px] text-slate-500">ID: #{dispute.bookingId}</div>
              </div>

              {/* Vehicle if available */}
              {dispute.vehicleId && (
                <div
                  onClick={() => onSelectVehicle && onSelectVehicle(dispute.vehicleId)}
                  className={`rounded-xl border p-2 transition-all ${
                    onSelectVehicle
                      ? 'group cursor-pointer border-slate-200 bg-white hover:border-emerald-400 hover:bg-emerald-50/40'
                      : 'border-slate-200/60 bg-white/70'
                  }`}
                >
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="flex items-center gap-1 text-slate-500">
                      <IconCar className="h-3 w-3 text-emerald-600" /> Xe
                    </span>
                    {onSelectVehicle && (
                      <span className="text-[10px] font-bold text-emerald-600">→</span>
                    )}
                  </div>
                  <strong className="mt-0.5 block truncate text-slate-900 group-hover:text-emerald-700">
                    {dispute.vehicleModel || `Xe #${dispute.vehicleId}`}
                  </strong>
                  <div className="truncate font-mono text-[11px] text-slate-500">
                    {dispute.vehicleLicensePlate || `#${dispute.vehicleId}`}
                  </div>
                </div>
              )}

              {/* Provider if available */}
              {dispute.providerId && (
                <div
                  onClick={() => onSelectProvider && onSelectProvider(dispute.providerId)}
                  className={`rounded-xl border p-2 transition-all ${
                    onSelectProvider
                      ? 'group cursor-pointer border-slate-200 bg-white hover:border-indigo-400 hover:bg-indigo-50/40'
                      : 'border-slate-200/60 bg-white/70'
                  }`}
                >
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-500">🏢 Chủ xe</span>
                    {onSelectProvider && (
                      <span className="text-[10px] font-bold text-indigo-600">→</span>
                    )}
                  </div>
                  <strong className="mt-0.5 block truncate text-slate-900 group-hover:text-indigo-700">
                    {dispute.providerDisplayName || `NCC #${dispute.providerId}`}
                  </strong>
                  <div className="text-[11px] text-slate-500">NCC #{dispute.providerId}</div>
                </div>
              )}

              {/* Driver if available */}
              {dispute.driverId && (
                <div
                  onClick={() => onSelectDriver && onSelectDriver(dispute.driverId)}
                  className={`rounded-xl border p-2 transition-all ${
                    onSelectDriver
                      ? 'group cursor-pointer border-slate-200 bg-white hover:border-amber-400 hover:bg-amber-50/40'
                      : 'border-slate-200/60 bg-white/70'
                  }`}
                >
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-500">👤 Tài xế</span>
                    {onSelectDriver && (
                      <span className="text-[10px] font-bold text-amber-600">→</span>
                    )}
                  </div>
                  <strong className="mt-0.5 block truncate text-slate-900 group-hover:text-amber-700">
                    {dispute.driverName || `Tài xế #${dispute.driverId}`}
                  </strong>
                  <div className="text-[11px] text-slate-500">TX #{dispute.driverId}</div>
                </div>
              )}

              {/* Handled By Admin */}
              <div className="rounded-xl border border-slate-200/60 bg-white/70 p-2">
                <span className="block text-[11px] text-slate-500">Thụ lý:</span>
                <strong className="mt-0.5 block truncate text-slate-900">
                  {dispute.handledByName || 'Chưa thụ lý'}
                </strong>
                <div className="truncate text-[10px] text-slate-500">
                  {dispute.createdAt ? new Date(dispute.createdAt).toLocaleString('vi-VN') : '—'}
                </div>
              </div>
            </div>
          </div>

          {/* Description of Dispute from User */}
          <div className="space-y-1.5 rounded-2xl border border-rose-200 bg-rose-50/40 p-3">
            <div className="flex items-center gap-2">
              <span className="flex h-5 w-5 items-center justify-center rounded-lg bg-rose-100 text-rose-700">
                <IconAlertTriangle className="h-3 w-3" />
              </span>
              <span className="text-[11px] font-bold uppercase tracking-wider text-rose-900">
                Nội dung khiếu nại / Lý do từ khách thuê
              </span>
            </div>
            <p className="whitespace-pre-wrap rounded-xl border border-rose-200 bg-white p-2.5 text-xs font-semibold leading-relaxed text-slate-800 shadow-2xs">
              {dispute.description || <span className="italic text-slate-400">Không có mô tả chi tiết</span>}
            </p>
          </div>

          {dispute.category === 'PAYMENT_MISMATCH' && (
            <div className="grid grid-cols-3 gap-2 rounded-2xl border border-amber-200 bg-amber-50/60 p-3 text-xs">
              <div>
                <p className="text-[11px] font-semibold text-amber-900">Loại sai lệch</p>
                <p className="mt-0.5 font-bold text-slate-900">
                  {{ OVERPAID: 'Khách chuyển dư', UNDER_RECORDED: 'Nhà xe ghi thiếu', OTHER: 'Khác' }[dispute.mismatchType] || '—'}
                </p>
              </div>
              <div>
                <p className="text-[11px] font-semibold text-amber-900">Khách khai chuyển</p>
                <p className="mt-0.5 font-bold text-slate-900">
                  {dispute.claimedAmount != null ? formatVND(dispute.claimedAmount) : '—'}
                </p>
              </div>
              <div>
                <p className="text-[11px] font-semibold text-amber-900">Nhà xe ghi nhận</p>
                <p className="mt-0.5 font-bold text-slate-900">
                  {dispute.recordedAmount != null ? formatVND(dispute.recordedAmount) : '—'}
                </p>
              </div>
            </div>
          )}

          {/* Evidence Photos Gallery */}
          <div className="space-y-1.5 rounded-2xl border border-slate-200 bg-slate-50/70 p-3">
            <div className="flex items-center justify-between">
              <span className="block text-[11px] font-bold uppercase tracking-wider text-slate-700">
                Hình ảnh bằng chứng ({evidenceUrls.length} ảnh)
              </span>
              {evidenceUrls.length > 0 && (
                <span className="text-[10px] text-slate-500">Bấm ảnh để phóng to</span>
              )}
            </div>
            {evidenceUrls.length > 0 ? (
              <div className="grid grid-cols-3 gap-2 pt-0.5">
                {evidenceUrls.map((url, idx) => (
                  <div
                    key={idx}
                    onClick={() => setPreviewImage(url)}
                    className="group relative h-20 cursor-pointer overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xs transition-all hover:border-brand-500 hover:shadow-md"
                  >
                    <img
                      src={url}
                      alt={`Bằng chứng ${idx + 1}`}
                      className="h-full w-full object-cover transition-transform duration-200 group-hover:scale-105"
                    />
                    <div className="absolute inset-0 flex items-center justify-center bg-black/40 text-[11px] font-bold text-white opacity-0 backdrop-blur-2xs transition-opacity group-hover:opacity-100">
                      Xem ảnh lớn
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="py-0.5 text-xs italic text-slate-400">
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
              <div className="space-y-1.5 rounded-2xl border border-purple-200 bg-purple-50/40 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="block text-[11px] font-bold uppercase tracking-wider text-purple-900">
                    Khoản phụ phí tranh chấp #{dispute.chargeId}
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
                        ? 'Đã miễn giảm (0 ₫)'
                        : 'Giữ nguyên phụ phí'}
                    </span>
                  )}
                </div>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-slate-700">{dispute.chargeDescription || 'Phụ phí phát sinh'}</span>
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-sm font-extrabold ${
                        isResolvedOrRejected && isChargeWaived
                          ? 'text-slate-400 line-through'
                          : 'text-purple-700'
                      }`}
                    >
                      {formatVND(dispute.chargeAmount)}
                    </span>
                    {isResolvedOrRejected && isChargeWaived && (
                      <span className="text-xs font-black text-emerald-700">→ 0 ₫</span>
                    )}
                  </div>
                </div>
              </div>
            )
          })()}
        </div>

        {/* Right Column: Resolved Summary OR Resolution Adjudication Form */}
        <div className="lg:col-span-6">
          {isResolvedOrRejected ? (() => {
            const effectiveDecision =
              dispute.decision ||
              (dispute.category === 'CHARGE' || dispute.chargeId
                ? dispute.chargeWaived === true || dispute.status === 'RESOLVED'
                  ? 'WAIVE_CHARGE'
                  : 'KEEP_CHARGE'
                : null)
            const isWaivedDecision = effectiveDecision === 'WAIVE_CHARGE' || effectiveDecision === 'PARTIAL_WAIVE'
            const isPartialDecision = effectiveDecision === 'PARTIAL_WAIVE'

            return (
              <div className="space-y-2.5 rounded-2xl border border-emerald-200 bg-emerald-50/50 p-3.5">
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
                    {disputeVerdictLabel(dispute)}
                  </span>
                </div>

                {/* Explicit Decision Ruling Box for Charge Disputes (C9 / C10) */}
                {(dispute.category === 'CHARGE' || dispute.chargeId || effectiveDecision) && (
                  <div
                    className={`rounded-xl border p-3 ${
                      isWaivedDecision
                        ? 'border-emerald-300 bg-emerald-100/60 text-emerald-950'
                        : 'border-amber-300 bg-amber-50/90 text-amber-950'
                    }`}
                  >
                    <div className="text-[11px] font-bold uppercase tracking-wider opacity-75">
                      Quyết định xử lý đã chốt:
                    </div>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <span
                        className={`rounded-lg px-2 py-0.5 text-[11px] font-extrabold text-white ${
                          isWaivedDecision ? 'bg-emerald-700' : 'bg-amber-700'
                        }`}
                      >
                        {isPartialDecision
                          ? `GIẢM MỘT PHẦN (${formatVND(dispute.refundAwardTotal)})`
                          : isWaivedDecision
                          ? 'MIỄN GIẢM PHỤ PHÍ (WAIVE_CHARGE)'
                          : 'GIỮ NGUYÊN PHỤ PHÍ (KEEP_CHARGE)'}
                      </span>
                      <span className="text-xs font-bold">
                        {isPartialDecision
                          ? `Khoản phụ phí đã giảm ${formatVND(dispute.refundAwardTotal)}; khách trả phần còn lại`
                          : isWaivedDecision
                          ? `Đã gạch bỏ khoản phụ phí ${formatVND(dispute.chargeAmount)} khỏi quyết toán của khách thuê`
                          : `Giữ nguyên khoản phụ phí ${formatVND(dispute.chargeAmount)} — Khách thuê phải thanh toán`}
                      </span>
                    </div>
                    <p className="mt-1 text-[11px] opacity-90">
                      {isPartialDecision
                        ? 'Số tiền phụ phí đã được cập nhật trong quyết toán; phần khách đã trả dư (nếu có) được tạo khoản hoàn.'
                        : isWaivedDecision
                        ? 'Hệ thống đã cập nhật trạng thái miễn phí (waived = true) cho khoản phụ phí này trong hóa đơn quyết toán của đơn thuê.'
                        : 'Khoản phụ phí vẫn được giữ hiệu lực thu (waived = false) trong hóa đơn quyết toán để khách thuê thanh toán số tiền còn thiếu.'}
                    </p>
                  </div>
                )}

                <div>
                  <span className="mb-1 block text-[11px] font-bold uppercase tracking-wider text-slate-500">
                    Nội dung phán quyết / Ghi chú của Admin:
                  </span>
                  <p className="rounded-xl border border-emerald-200 bg-white p-2.5 font-medium text-slate-800">
                    {dispute.resolution || 'Chưa ghi nhận'}
                  </p>
                </div>

                <p className="text-xs text-slate-600">
                  Người xử lý: <strong>{dispute.handledByName || 'Admin'}</strong> · Thời điểm phân xử:{' '}
                  <strong>
                    {dispute.resolvedAt
                      ? new Date(dispute.resolvedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
                      : 'Chưa ghi nhận'}
                  </strong>
                </p>

                {dispute.refundAwardTotal != null && (
                  <p className="text-sm font-semibold text-slate-900">
                    {dispute.category === 'PAYMENT_MISMATCH'
                      ? 'Tổng số khách thực chuyển (đã đối chiếu): '
                      : dispute.category === 'CHARGE' ? 'Số tiền phụ phí được giảm: '
                      : dispute.category === 'DEPOSIT_REFUND' ? 'Tổng tiền cọc được hoàn: ' : 'Khoản bồi hoàn cho khách: '}
                    {formatVND(dispute.refundAwardTotal)}
                  </p>
                )}
              </div>
            )
          })() : (
            <form
              id={formId}
              onSubmit={handleSubmit}
              noValidate
              className="space-y-2.5 rounded-2xl border border-slate-200 bg-white p-3.5 shadow-xs"
            >
              <h4 className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                <IconShield className="h-4 w-4 text-brand-600" /> Biểu mẫu Phán quyết &amp; Giải quyết tranh chấp
              </h4>

              {error && <p role="alert" className="text-xs font-semibold text-red-700">{error}</p>}

              <fieldset className="space-y-1.5 rounded-xl border border-slate-100 bg-slate-50 p-2.5">
                <legend className="px-1 font-bold text-slate-700">Phán quyết</legend>
                {verdictOptions(dispute.category).map((option) => (
                  <label
                    key={option.value}
                    className={`flex cursor-pointer items-start gap-2 rounded-lg border p-2 ${
                      form.verdict === option.value ? 'border-purple-400 bg-white' : 'border-transparent'
                    }`}
                  >
                    <input
                      type="radio"
                      name="verdict"
                      value={option.value}
                      checked={form.verdict === option.value}
                      onChange={() => setForm((prev) => ({ ...prev, verdict: option.value }))}
                      className="mt-0.5"
                    />
                    <span>
                      <strong className="block text-slate-900">{option.label}</strong>
                      {option.hint && <span className="text-[11px] text-slate-500">{option.hint}</span>}
                    </span>
                  </label>
                ))}
                {dispute.category === 'TRIP_NOT_CONFIRMED' && (
                  <details className="rounded-lg border border-slate-200 bg-white px-3 py-1.5">
                    <summary className="cursor-pointer text-[11px] font-semibold text-slate-600">Tuỳ chọn nâng cao</summary>
                    <div className="mt-1.5 space-y-1">
                      {TRIP_END_ADVANCED.map((option) => (
                        <label key={option.value} className="flex cursor-pointer items-center gap-2 text-[11px]">
                          <input
                            type="radio"
                            name="verdict"
                            value={option.value}
                            checked={form.verdict === option.value}
                            onChange={() => setForm((prev) => ({ ...prev, verdict: option.value }))}
                          />
                          {option.label}
                        </label>
                      ))}
                    </div>
                  </details>
                )}
              </fieldset>

              {dispute.category === 'TRIP_NOT_CONFIRMED' && form.verdict === 'ACCEPT_RENTER_TIME' && (
                <label className="block space-y-1 font-semibold text-slate-800">
                  <span>Giờ kết thúc đúng (theo xác minh GPS / bằng chứng)</span>
                  <input
                    type="datetime-local"
                    required
                    value={form.correctedEndAt}
                    onChange={(e) => setForm((prev) => ({ ...prev, correctedEndAt: e.target.value }))}
                    className="block min-h-10 w-full rounded-lg border border-slate-300 px-3"
                  />
                </label>
              )}

              {dispute.category === 'CHARGE' && form.verdict === 'PARTIAL_WAIVE' && (
                <label className="block space-y-1 font-semibold text-slate-800">
                  <span>
                    Số tiền giảm (đ){dispute.chargeAmount != null ? ` — phụ phí hiện tại ${formatVND(dispute.chargeAmount)}` : ''}
                  </span>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    required
                    value={form.refundAwardTotal}
                    onChange={(e) => setForm((prev) => ({ ...prev, refundAwardTotal: e.target.value }))}
                    className="block min-h-10 w-full rounded-lg border border-slate-300 px-3"
                  />
                </label>
              )}

              {dispute.category === 'DEPOSIT_REFUND' && form.verdict === 'RESOLVED' && (
                <label className="block space-y-1 font-semibold text-slate-800">
                  <span>Tổng tiền cọc khách được hoàn (đ)</span>
                  <input
                    type="number"
                    required
                    min="0.01"
                    step="0.01"
                    value={form.refundAwardTotal}
                    onChange={(e) => setForm((prev) => ({ ...prev, refundAwardTotal: e.target.value }))}
                    className="block min-h-10 w-full rounded-lg border border-slate-300 px-3"
                  />
                  <span className="block text-[11px] font-normal text-slate-600">
                    Nhập tổng mức hoàn theo phán quyết. Hệ thống trừ phần đã hoàn hoặc đang xử lý để chỉ tạo phần thiếu; chưa chuyển tiền ở bước này.
                  </span>
                </label>
              )}

              {dispute.category === 'PAYMENT_MISMATCH' && form.verdict === 'RESOLVED' && (
                <label className="block space-y-1 font-semibold text-slate-800">
                  <span>Tổng số khách thực chuyển cho khoản này (đ) — đã đối chiếu sao kê</span>
                  <input
                    type="number"
                    required
                    min="1"
                    step="1"
                    value={form.refundAwardTotal}
                    onChange={(e) => setForm((prev) => ({ ...prev, refundAwardTotal: e.target.value }))}
                    placeholder={dispute.claimedAmount != null ? String(Math.round(Number(dispute.claimedAmount))) : ''}
                    className="block min-h-10 w-full rounded-lg border border-slate-300 px-3"
                  />
                  <span className="block text-[11px] font-normal text-slate-600">
                    Hệ thống ghi nhận phần chênh lệch so với số nhà xe đã ghi: phần còn thiếu được áp vào, phần dư thành khoản nhà xe bắt buộc hoàn cho khách. Bắt buộc nhập — nếu chỉ ghi kết luận, hãy chọn Bác bỏ hoặc Khác.
                  </span>
                </label>
              )}

              {['SERVICE_QUALITY', 'OTHER'].includes(dispute.category) && form.verdict === 'RESOLVED' && (
                <label className="block space-y-1 font-semibold text-slate-800">
                  <span>Bồi hoàn cho khách (đ) — tuỳ chọn</span>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={form.refundAwardTotal}
                    onChange={(e) => setForm((prev) => ({ ...prev, refundAwardTotal: e.target.value }))}
                    className="block min-h-10 w-full rounded-lg border border-slate-300 px-3"
                  />
                  <span className="block text-[11px] font-normal text-slate-600">
                    Có số tiền: hệ thống tạo khoản hoàn mà nhà xe phải chuyển cho khách (không vượt số khách đã trả; chỉ áp dụng đơn đã hoàn tất). Để trống: chỉ ghi nhận kết luận, không phát sinh tiền.
                  </span>
                </label>
              )}

              {/* Resolution Notes */}
              <div>
                <label className="mb-1 block font-bold text-slate-700">
                  Nội dung phán quyết / Ghi chú giải quyết <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows="3"
                  required
                  maxLength={1000}
                  placeholder="Ghi rõ lý do phân xử, căn cứ biên bản bàn giao xe hoặc thỏa thuận giữa các bên..."
                  value={form.resolution}
                  onChange={(e) => setForm((prev) => ({ ...prev, resolution: e.target.value }))}
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-xs outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
                />
              </div>
            </form>
          )}
        </div>
      </div>
    </AdminModalShell>
  )
}
