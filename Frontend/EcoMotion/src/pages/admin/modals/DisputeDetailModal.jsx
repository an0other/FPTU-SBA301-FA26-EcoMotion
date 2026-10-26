import { useState } from 'react'
import { IconX, IconAlertTriangle, IconCheckCircle, IconShield } from '../../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const DISPUTE_STATUS_BADGES = {
  OPEN: 'bg-rose-50 text-rose-700 border-rose-200',
  UNDER_REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
  IN_REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
  RESOLVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTED: 'bg-slate-100 text-slate-700 border-slate-200',
}

export default function DisputeDetailModal({ dispute, onClose, onResolve, actionLoading }) {
  if (!dispute) return null

  const isResolvedOrRejected = dispute.status === 'RESOLVED' || dispute.status === 'REJECTED'

  const [form, setForm] = useState({
    status: isResolvedOrRejected ? dispute.status : 'RESOLVED',
    decision: 'KEEP_CHARGE',
    resolution: dispute.resolution || '',
  })

  function handleSubmit(e) {
    e.preventDefault()
    if (!form.resolution.trim()) {
      alert('Vui lòng nhập nội dung phán quyết / giải pháp phân xử.')
      return
    }
    onResolve(dispute.id, form.status, form.decision, form.resolution.trim())
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
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
              <p className="mt-0.5 text-xs text-slate-500">
                Liên quan Đơn thuê: <strong>#{dispute.bookingCode || dispute.bookingId}</strong> • Phân loại: <strong>{dispute.category}</strong>
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
          {/* Dispute Origin Info */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <div>
              <span className="text-slate-500 block">Bên khiếu nại (Người gửi):</span>
              <strong className="text-slate-900">{dispute.raisedByName || `User #${dispute.raisedById}`}</strong>
              <div className="text-slate-500">{dispute.raisedByEmail || '—'}</div>
            </div>
            <div>
              <span className="text-slate-500 block">Quản trị viên thụ lý:</span>
              <strong className="text-slate-900">{dispute.handledByName || 'Chưa thụ lý'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Thời điểm phát sinh:</span>
              <strong className="text-slate-900">
                {dispute.createdAt ? new Date(dispute.createdAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            <div>
              <span className="text-slate-500 block">Cập nhật lần cuối:</span>
              <strong className="text-slate-900">
                {dispute.updatedAt ? new Date(dispute.updatedAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
          </div>

          {/* Charge in Dispute */}
          {dispute.chargeId && (
            <div className="rounded-2xl border border-purple-200 bg-purple-50/40 p-4 space-y-1">
              <span className="block font-bold text-purple-900 uppercase tracking-wider text-[11px]">
                Khoản phụ phí đang tranh chấp #{dispute.chargeId}
              </span>
              <div className="flex items-center justify-between">
                <span className="text-slate-700 font-medium">{dispute.chargeDescription || 'Phụ phí phát sinh'}</span>
                <span className="text-base font-extrabold text-purple-700">
                  {formatVND(dispute.chargeAmount)}
                </span>
              </div>
            </div>
          )}

          {/* If already resolved, show resolution card */}
          {isResolvedOrRejected ? (
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4 space-y-2">
              <div className="flex items-center gap-2 font-bold text-emerald-900">
                <IconCheckCircle className="h-4 w-4 text-emerald-600" />
                <span>Tranh chấp đã được phân xử xong</span>
              </div>
              <p className="rounded-xl border border-emerald-200 bg-white p-3 font-medium text-slate-800">
                {dispute.resolution || 'Không có ghi chú phân xử'}
              </p>
            </div>
          ) : (
            /* Resolution Form */
            <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs space-y-4">
              <h4 className="font-bold uppercase tracking-wider text-slate-600 flex items-center gap-1.5">
                <IconShield className="h-4 w-4 text-brand-600" /> Biểu mẫu Phán quyết & Giải quyết tranh chấp
              </h4>

              {/* Status Selector */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">
                  Trạng thái sau khi xử lý:
                </label>
                <select
                  value={form.status}
                  onChange={(e) => setForm((prev) => ({ ...prev, status: e.target.value }))}
                  className="w-full rounded-xl border border-slate-300 p-2.5 text-xs font-semibold text-slate-700 outline-none focus:border-brand-500"
                >
                  <option value="RESOLVED">RESOLVED (Đã phân xử chấp thuận)</option>
                  <option value="REJECTED">REJECTED (Bác bỏ khiếu nại)</option>
                  <option value="UNDER_REVIEW">UNDER_REVIEW (Đang điều tra thêm)</option>
                </select>
              </div>

              {/* Decision Radio for Charges */}
              {dispute.category === 'CHARGE' && (
                <div className="space-y-2 rounded-xl border border-slate-100 bg-slate-50 p-3">
                  <span className="block font-bold text-slate-700">Phương án xử lý khoản tiền:</span>
                  <div className="space-y-2">
                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="decision"
                        value="KEEP_CHARGE"
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

              {/* Resolution Notes */}
              <div>
                <label className="block font-bold text-slate-700 mb-1">
                  Nội dung phán quyết / Ghi chú giải quyết <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows="3"
                  required
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
    </div>
  )
}
