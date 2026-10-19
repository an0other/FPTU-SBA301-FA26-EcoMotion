import { useState } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

const CHARGE_TYPE_CONFIG = {
  OVERTIME: {
    label: 'Phụ thu quá giờ (OVERTIME)',
    quantityLabel: 'Số slot vượt',
    defaultUnitPrice: 100000,
  },
  WAITING: {
    label: 'Phụ thu thời gian chờ (WAITING)',
    quantityLabel: 'Số phút chờ',
    defaultUnitPrice: 3000,
  },
  OUT_OF_RADIUS: {
    label: 'Phụ thu ngoài bán kính (OUT_OF_RADIUS)',
    quantityLabel: 'Số km vượt',
    defaultUnitPrice: 15000,
  },
  WEEKEND: {
    label: 'Phụ thu cuối tuần / ngày lễ (WEEKEND)',
    quantityLabel: 'Số ngày / lượt',
    defaultUnitPrice: 200000,
  },
  BASE_RENTAL: {
    label: 'Tiền thuê bổ sung (BASE_RENTAL)',
    quantityLabel: 'Số lượng slot',
    defaultUnitPrice: 500000,
  },
}

export default function AddChargeModal({ booking, isOpen, onClose, onSuccess }) {
  const [chargeType, setChargeType] = useState('OVERTIME')
  const [description, setDescription] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [unitPrice, setUnitPrice] = useState(100000)
  const [appliedRate, setAppliedRate] = useState(1.0)
  const [amount, setAmount] = useState(100000)
  const [evidenceUrl, setEvidenceUrl] = useState('')

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [validationErrors, setValidationErrors] = useState({})

  // Set default unit price and calculate amount when type changes
  const handleTypeChange = (newType) => {
    setChargeType(newType)
    const conf = CHARGE_TYPE_CONFIG[newType]
    if (conf) {
      setUnitPrice(conf.defaultUnitPrice)
      const q = Number(quantity) || 0
      const r = appliedRate != null && appliedRate !== '' ? Number(appliedRate) : 1
      setAmount(Math.round(q * conf.defaultUnitPrice * r))
    }
  }

  const handleQuantityChange = (val) => {
    setQuantity(val)
    const q = Number(val) || 0
    const p = Number(unitPrice) || 0
    const r = appliedRate != null && appliedRate !== '' ? Number(appliedRate) : 1
    setAmount(Math.round(q * p * r))
  }

  const handleUnitPriceChange = (val) => {
    setUnitPrice(val)
    const q = Number(quantity) || 0
    const p = Number(val) || 0
    const r = appliedRate != null && appliedRate !== '' ? Number(appliedRate) : 1
    setAmount(Math.round(q * p * r))
  }

  const handleAppliedRateChange = (val) => {
    setAppliedRate(val)
    const q = Number(quantity) || 0
    const p = Number(unitPrice) || 0
    const r = val != null && val !== '' ? Number(val) : 1
    setAmount(Math.round(q * p * r))
  }

  if (!isOpen || !booking) return null

  function validate() {
    const errors = {}
    if (!description.trim()) {
      errors.description = 'Vui lòng nhập mô tả/lý do phát sinh phụ phí.'
    }
    if (quantity == null || Number(quantity) <= 0) {
      errors.quantity = 'Số lượng phải lớn hơn 0.'
    }
    if (unitPrice == null || Number(unitPrice) < 0) {
      errors.unitPrice = 'Đơn giá không được âm.'
    }
    if (amount == null || Number(amount) <= 0) {
      errors.amount = 'Số tiền phải lớn hơn 0.'
    }
    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!validate()) return

    setLoading(true)
    setError(null)

    try {
      const payload = {
        chargeType,
        description: description.trim(),
        quantity: Number(quantity),
        unitPrice: Number(unitPrice),
        appliedRate: appliedRate ? Number(appliedRate) : null,
        amount: Number(amount),
        evidenceUrl: evidenceUrl.trim() || null,
      }

      const created = await providerBookingService.addCharge(booking.bookingCode, payload)
      onSuccess?.(created, 'Đã thêm phụ phí thành công!')
      onClose()
    } catch (err) {
      setError(err?.message || 'Không thể ghi nhận phụ phí. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const currentConfig = CHARGE_TYPE_CONFIG[chargeType] || CHARGE_TYPE_CONFIG.OVERTIME

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl transition-all flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4 bg-indigo-50/50">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-100 text-indigo-700">
              <Icon name="add_card" className="text-[22px]" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-900">Ghi nhận phụ phí thủ công</h3>
              <p className="text-xs text-slate-500 font-medium">
                Đơn thuê: <span className="font-mono font-bold text-slate-800">{booking.bookingCode}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <Icon name="close" className="text-[20px]" />
          </button>
        </div>

        {/* Content Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 overflow-y-auto flex-1">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs text-red-800 font-medium">
              <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {/* Loại phí */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Loại phụ phí <span className="text-red-500">*</span>
            </label>
            <select
              value={chargeType}
              onChange={(e) => handleTypeChange(e.target.value)}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 px-3 text-xs font-medium text-slate-800 focus:border-indigo-600 focus:bg-white focus:outline-none"
            >
              {Object.entries(CHARGE_TYPE_CONFIG).map(([key, item]) => (
                <option key={key} value={key}>
                  {item.label}
                </option>
              ))}
            </select>
          </div>

          {/* Diễn giải / Lý do */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Diễn giải lý do phát sinh <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => {
                setDescription(e.target.value)
                if (validationErrors.description) {
                  setValidationErrors((prev) => ({ ...prev, description: null }))
                }
              }}
              placeholder="Ví dụ: Vượt 2 giờ do khách yêu cầu ghé thêm địa điểm"
              className={`w-full rounded-xl border bg-slate-50 py-2.5 px-3 text-xs text-slate-800 focus:bg-white focus:outline-none ${
                validationErrors.description
                  ? 'border-red-400 focus:border-red-500 ring-1 ring-red-400'
                  : 'border-slate-200 focus:border-indigo-600'
              }`}
            />
            {validationErrors.description && (
              <p className="mt-1 text-xs text-red-600 font-medium">{validationErrors.description}</p>
            )}
          </div>

          {/* Grid: Quantity & Unit price */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                {currentConfig.quantityLabel} <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min="1"
                step="1"
                value={quantity}
                onChange={(e) => handleQuantityChange(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-indigo-600 focus:bg-white focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                Đơn giá (₫) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min="0"
                step="1000"
                value={unitPrice}
                onChange={(e) => handleUnitPriceChange(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-indigo-600 focus:bg-white focus:outline-none"
              />
            </div>
          </div>

          {/* Grid: Rate & Total amount */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                Hệ số / Tỷ lệ áp dụng
              </label>
              <input
                type="number"
                min="0.1"
                step="0.05"
                value={appliedRate}
                onChange={(e) => handleAppliedRateChange(e.target.value)}
                placeholder="1.0"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-indigo-600 focus:bg-white focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                Tổng thành tiền (₫) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                min="0"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full rounded-xl border border-indigo-300 bg-indigo-50/50 py-2 px-3 text-xs font-bold text-indigo-900 focus:border-indigo-600 focus:bg-white focus:outline-none"
              />
            </div>
          </div>

          {/* Bằng chứng URL */}
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              Đường dẫn hình ảnh / Biên lai chứng minh (tùy chọn)
            </label>
            <input
              type="url"
              value={evidenceUrl}
              onChange={(e) => setEvidenceUrl(e.target.value)}
              placeholder="https://..."
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 px-3 text-xs text-slate-800 focus:border-indigo-600 focus:bg-white focus:outline-none"
            />
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
            <div className="flex justify-between">
              <span>Cách tính:</span>
              <strong className="text-slate-900 font-mono">
                {quantity} × {Number(unitPrice).toLocaleString('vi-VN')} ₫
                {appliedRate && appliedRate !== 1 ? ` × ${appliedRate}` : ''}
              </strong>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-1 text-slate-800">
              <span className="font-semibold">Thành tiền sẽ cộng vào đơn:</span>
              <strong className="text-indigo-800 font-mono text-sm">
                {Number(amount).toLocaleString('vi-VN')} ₫
              </strong>
            </div>
          </div>

          {/* Footer inside form */}
          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-indigo-700 transition disabled:opacity-50"
            >
              {loading && <Icon name="progress_activity" className="animate-spin text-[16px]" />}
              <span>{loading ? 'Đang lưu...' : 'Ghi nhận phụ phí'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
