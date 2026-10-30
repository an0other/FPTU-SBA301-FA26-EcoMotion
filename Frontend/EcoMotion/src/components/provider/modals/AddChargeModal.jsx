import { useState } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'
import { chargeQuote } from '../../../utils/tv5Contracts'

const labels = {
  OVERTIME: 'Quá giờ (số slot vượt)',
  WAITING: 'Chờ (số phút tính phí)',
  OUT_OF_RADIUS: 'Ngoài bán kính (số km vượt)',
  WEEKEND: 'Cuối tuần (số slot)',
}

export default function AddChargeModal({ booking, isOpen, onClose, onSuccess }) {
  const [chargeType, setChargeType] = useState('OVERTIME')
  const [quantity, setQuantity] = useState(1)
  const [description, setDescription] = useState('')
  const [evidenceUrl, setEvidenceUrl] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (!isOpen || !booking) return null

  const quote = chargeQuote(booking, chargeType, quantity)

  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    if (
      !description.trim() ||
      !evidenceUrl.trim() ||
      !Number.isInteger(Number(quantity)) ||
      Number(quantity) < 1 ||
      Number(quantity) > 1000000 ||
      !Number.isFinite(quote.amount) ||
      quote.amount <= 0
    ) {
      setError('Cần lý do, bằng chứng và số lượng hợp lệ theo chính sách đơn thuê.')
      return
    }
    if (!confirming) {
      setConfirming(true)
      setError('')
      return
    }
    setBusy(true)
    setError('')
    try {
      const created = await providerBookingService.addCharge(booking.bookingCode, {
        chargeType,
        quantity: Number(quantity),
        description: description.trim(),
        evidenceUrl: evidenceUrl.trim(),
        ...quote,
      })
      onSuccess?.(created, 'Đã ghi nhận phụ phí theo chính sách chốt của đơn.')
      setConfirming(false)
      onClose()
    } catch (err) {
      setError(err.message || 'Không thể lưu phụ phí.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <form
        onSubmit={submit}
        role="dialog"
        aria-modal="true"
        aria-label="Thêm phụ phí"
        className="w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg"
      >
        <div className="flex shrink-0 items-center justify-between border-b border-slate-200 bg-slate-50 px-6 py-4">
          <div>
            <h2 className="text-base font-bold text-slate-900">
              Thêm phụ phí — <span className="font-mono">{booking.bookingCode}</span>
            </h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Tính toán tự động theo biểu phí đã chốt của đơn thuê
            </p>
          </div>
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setConfirming(false)
              onClose()
            }}
            aria-label="Đóng cửa sổ"
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-200 hover:text-slate-700"
          >
            <span className="material-symbols-rounded text-[20px] leading-none">close</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          <fieldset disabled={busy || confirming} className="space-y-3.5">
            <label className="block text-xs font-semibold text-slate-700">
              Loại phí
              <div className="relative mt-1.5 h-10 w-full">
                <select
                  value={chargeType}
                  onChange={(e) => setChargeType(e.target.value)}
                  className="h-10 w-full appearance-none rounded-lg border border-slate-200 bg-white pl-3.5 pr-9 text-xs font-medium text-slate-800 focus:border-emerald-600 focus:outline-none disabled:bg-slate-50"
                >
                  {Object.entries(labels).map(([key, label]) => (
                    <option key={key} value={key}>
                      {label}
                    </option>
                  ))}
                </select>
                <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
                  <span className="material-symbols-rounded text-[18px] leading-none">expand_more</span>
                </div>
              </div>
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Số lượng tính phí
              <input
                required
                type="number"
                min="1"
                max="1000000"
                step="1"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="mt-1.5 block h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none disabled:bg-slate-50"
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Lý do
              <textarea
                required
                rows={3}
                maxLength={500}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Nhập chi tiết lý do phát sinh phụ phí..."
                className="mt-1.5 block w-full rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none disabled:bg-slate-50"
              />
            </label>

            <label className="block text-xs font-semibold text-slate-700">
              Đường dẫn bằng chứng
              <input
                required
                type="url"
                maxLength={500}
                value={evidenceUrl}
                onChange={(e) => setEvidenceUrl(e.target.value)}
                placeholder="https://..."
                className="mt-1.5 block h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none disabled:bg-slate-50"
              />
            </label>
          </fieldset>

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3.5 space-y-1">
            <p className="text-xs text-slate-700">
              Đơn giá {quote.unitPrice.toLocaleString('vi-VN')} × {quantity} × {quote.appliedRate}:{' '}
              <strong className="font-mono text-sm text-emerald-800">
                {quote.amount.toLocaleString('vi-VN')} ₫
              </strong>
            </p>
            <p className="text-[11px] text-slate-500">
              Giá và tỷ lệ lấy từ chính sách đã chốt. Backend tính lại khi lưu.
            </p>
          </div>

          {confirming && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-medium text-amber-800">
              Xác nhận số lượng, lý do và bằng chứng trước khi ghi nhận phụ phí.
            </div>
          )}

          {error && (
            <p
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs font-medium text-red-700"
            >
              {error}
            </p>
          )}
        </div>

        <div className="flex shrink-0 items-center justify-end gap-2.5 border-t border-slate-200 bg-slate-50 px-6 py-4">
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setConfirming(false)
              onClose()
            }}
            className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Đóng
          </button>
          {confirming && (
            <button
              type="button"
              disabled={busy}
              onClick={() => setConfirming(false)}
              className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
            >
              Sửa lại
            </button>
          )}
          <button
            disabled={busy}
            className="inline-flex h-10 items-center justify-center rounded-lg bg-emerald-700 px-4 text-xs font-bold text-white hover:bg-emerald-800 disabled:opacity-50"
          >
            {busy ? 'Đang lưu...' : confirming ? 'Xác nhận thêm phụ phí' : 'Tiếp tục'}
          </button>
        </div>
      </form>
    </div>
  )
}
