import { useState } from 'react'
import { IconWallet, IconPlus } from '../icons/Icons'

const CHARGE_TYPES = [
  { value: 'OVERTIME', label: 'Phí quá giờ (Overtime Fee)' },
  { value: 'WAITING', label: 'Phí chờ đợi (Waiting Fee)' },
  { value: 'WEEKEND', label: 'Phụ phí cuối tuần (Weekend Surcharge)' },
  { value: 'OUT_OF_RADIUS', label: 'Phí ngoài bán kính / Giao xe xa (Out of Radius)' },
  { value: 'DEPOSIT', label: 'Tiền đặt cọc bổ sung (Additional Deposit)' },
]

export default function AddChargeModal({
  isOpen,
  onClose,
  onConfirm,
  bookingCode,
  submitting,
  formatCurrency,
}) {
  const [chargeType, setChargeType] = useState('OVERTIME')
  const [description, setDescription] = useState('')
  const [amountInput, setAmountInput] = useState('')
  const [error, setError] = useState('')

  if (!isOpen) return null

  function handleSubmit(e) {
    e.preventDefault()
    const numericAmount = parseFloat(amountInput)
    if (isNaN(numericAmount) || numericAmount <= 0) {
      setError('Vui lòng nhập số tiền hợp lệ lớn hơn 0.')
      return
    }
    setError('')
    onConfirm({
      type: chargeType,
      description: description.trim() || undefined,
      amount: numericAmount,
    })
  }

  const numericValue = parseFloat(amountInput)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div className="relative w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
        <div className="mb-4 flex items-center gap-3 border-b border-slate-100 pb-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-600">
            <IconWallet className="h-6 w-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-900">Thêm phụ phí phát sinh</h3>
            <p className="text-xs text-slate-500">Đơn đặt xe #{bookingCode}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Loại phụ phí <span className="text-red-500">*</span>
            </label>
            <select
              value={chargeType}
              onChange={(e) => setChargeType(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-medium text-slate-800 focus:border-emerald-500 focus:outline-hidden focus:ring-1 focus:ring-emerald-500"
            >
              {CHARGE_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Số tiền phụ thu (VND) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              step="1000"
              min="1000"
              value={amountInput}
              onChange={(e) => {
                setAmountInput(e.target.value)
                setError('')
              }}
              placeholder="VD: 150000"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-800 placeholder:text-slate-400 focus:border-emerald-500 focus:outline-hidden focus:ring-1 focus:ring-emerald-500 font-mono font-bold"
            />
            {!isNaN(numericValue) && numericValue > 0 && (
              <p className="mt-1 text-xs text-emerald-700 font-bold">
                Quy đổi: {formatCurrency(numericValue)}
              </p>
            )}
            {error && <p className="mt-1 text-xs text-red-600 font-medium">{error}</p>}
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Mô tả chi tiết / Lý do phụ thu
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="VD: Khách trả xe trễ 1 giờ, phụ phí ngoài giờ quy định..."
              className="w-full rounded-lg border border-slate-300 p-2.5 text-xs text-slate-800 placeholder:text-slate-400 focus:border-emerald-500 focus:outline-hidden focus:ring-1 focus:ring-emerald-500"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              disabled={submitting}
              onClick={onClose}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100 disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-5 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-emerald-700 disabled:opacity-50"
            >
              {submitting ? (
                <>
                  <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Đang thêm...
                </>
              ) : (
                <>
                  <IconPlus className="h-4 w-4" />
                  Lưu phụ phí
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
