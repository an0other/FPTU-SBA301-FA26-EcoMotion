import { useState } from 'react'
import { providerBookingService } from '../../../services/providerBookingService'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
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
}

// Anomaly threshold: 2,000,000 VNĐ
const ANOMALY_THRESHOLD = 2000000

export default function AddChargeModal({ booking, isOpen, onClose, onSuccess }) {
  const [chargeType, setChargeType] = useState('OVERTIME')
  const [description, setDescription] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [unitPrice, setUnitPrice] = useState(100000)
  const [appliedRate, setAppliedRate] = useState(1.0)
  const [amount, setAmount] = useState(100000)
  const [evidenceUrl, setEvidenceUrl] = useState('')

  // Confirmation step state
  const [step, setStep] = useState(1) // 1: Form, 2: Confirmation
  const [confirmedByProvider, setConfirmedByProvider] = useState(false)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [validationErrors, setValidationErrors] = useState({})

  if (!isOpen || !booking) return null

  const isAbnormal = Number(amount) >= ANOMALY_THRESHOLD

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

  function validate() {
    const errors = {}
    if (!description.trim()) {
      errors.description = 'Vui lòng nhập lý do phát sinh phụ phí rõ ràng.'
    }
    if (quantity == null || Number(quantity) <= 0) {
      errors.quantity = 'Số lượng phải lớn hơn 0.'
    }
    if (unitPrice == null || Number(unitPrice) < 0) {
      errors.unitPrice = 'Đơn giá không được âm.'
    }
    if (amount == null || Number(amount) <= 0) {
      errors.amount = 'Số tiền phụ thu phải lớn hơn 0.'
    }
    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleProceedToConfirm = (e) => {
    e.preventDefault()
    if (!validate()) return
    setStep(2)
  }

  const handleSubmit = async () => {
    if (isAbnormal && !confirmedByProvider) {
      setError('Vui lòng tích xác nhận đồng ý với mức phụ phí bất thường.')
      return
    }

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
      onSuccess?.(created, 'Đã ghi nhận phụ phí thành công!')
      onClose()
      setStep(1)
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Không thể ghi nhận phụ phí. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const currentConfig = CHARGE_TYPE_CONFIG[chargeType] || CHARGE_TYPE_CONFIG.OVERTIME

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
      <div className="w-full max-w-lg overflow-hidden rounded-lg bg-white shadow-md border border-slate-200 flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 bg-slate-50">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-amber-100 text-amber-800">
              <Icon name="receipt_long" className="text-xl" />
            </span>
            <div>
              <h3 className="text-base font-bold text-slate-900">
                {step === 1 ? 'Ghi nhận phụ phí' : 'Xác nhận ghi nhận phụ phí'}
              </h3>
              <p className="text-xs text-slate-500 font-medium">
                Đơn thuê: <span className="font-mono font-bold text-slate-800">{booking.bookingCode}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition"
          >
            <Icon name="close" className="text-xl" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 overflow-y-auto flex-1 space-y-4">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-800">
              <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
              <span className="font-medium">{error}</span>
            </div>
          )}

          {step === 1 ? (
            <form id="add-charge-form" onSubmit={handleProceedToConfirm} className="space-y-4">
              {/* Charge Type Selection */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1.5">
                  Loại phụ phí phát sinh <span className="text-red-500">*</span>
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {Object.entries(CHARGE_TYPE_CONFIG).map(([key, conf]) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => handleTypeChange(key)}
                      className={`p-2.5 rounded-xl border text-left text-xs transition ${
                        chargeType === key
                          ? 'border-brand-600 bg-brand-50 text-brand-900 font-bold'
                          : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                      }`}
                    >
                      {conf.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Description */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Lý do chi tiết <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows="2"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Ghi rõ lý do phát sinh (ví dụ: Khách trả xe trễ 1 giờ 30 phút theo thỏa thuận...)"
                  className={`w-full rounded-xl border bg-slate-50 p-2.5 text-xs text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none ${
                    validationErrors.description
                      ? 'border-red-400 ring-1 ring-red-400'
                      : 'border-slate-200 focus:border-brand-600'
                  }`}
                />
                {validationErrors.description && (
                  <p className="mt-1 text-xs text-red-600 font-medium">{validationErrors.description}</p>
                )}
              </div>

              {/* Quantity & Unit price */}
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
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-brand-600 focus:bg-white focus:outline-none"
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
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-brand-600 focus:bg-white focus:outline-none"
                  />
                </div>
              </div>

              {/* Applied Rate & Total Amount */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Hệ số áp dụng
                  </label>
                  <input
                    type="number"
                    min="0.1"
                    step="0.05"
                    value={appliedRate}
                    onChange={(e) => handleAppliedRateChange(e.target.value)}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-brand-600 focus:bg-white focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 mb-1">
                    Tổng phụ thu (₫) <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    className="w-full rounded-xl border border-brand-500 bg-brand-50/50 py-2 px-3 text-xs font-bold text-brand-900 focus:border-brand-600 focus:bg-white focus:outline-none"
                  />
                </div>
              </div>

              {/* Evidence URL */}
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Đường dẫn ảnh minh chứng / Biên bản bàn giao (tùy chọn)
                </label>
                <input
                  type="url"
                  value={evidenceUrl}
                  onChange={(e) => setEvidenceUrl(e.target.value)}
                  placeholder="https://..."
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 focus:border-brand-600 focus:bg-white focus:outline-none"
                />
              </div>

              {/* Abnormal Warning Box */}
              {isAbnormal && (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900 flex items-start gap-2">
                  <Icon name="warning" className="text-amber-600 text-[18px] shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold">Cảnh báo chi phí bất thường: </span>
                    Số tiền phụ thu ({Number(amount).toLocaleString('vi-VN')} ₫) vượt mức khuyến nghị thông thường (2.000.000 ₫). Vui lòng đảm bảo đã có sự đồng thuận từ khách hàng và bổ sung minh chứng rõ ràng.
                  </div>
                </div>
              )}
            </form>
          ) : (
            /* Step 2: Confirmation Preview */
            <div className="space-y-4">
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3 text-xs">
                <div className="flex justify-between items-center border-b border-slate-200 pb-2">
                  <span className="text-slate-500">Loại phụ phí:</span>
                  <span className="font-bold text-slate-800">{currentConfig.label}</span>
                </div>
                <div className="flex justify-between items-start border-b border-slate-200 pb-2">
                  <span className="text-slate-500">Lý do phát sinh:</span>
                  <span className="font-medium text-slate-800 max-w-[280px] text-right">{description}</span>
                </div>
                <div className="flex justify-between items-center border-b border-slate-200 pb-2">
                  <span className="text-slate-500">Công thức tính:</span>
                  <span className="font-mono text-slate-700">
                    {quantity} × {Number(unitPrice).toLocaleString('vi-VN')} ₫
                    {appliedRate && appliedRate !== 1 ? ` × ${appliedRate}` : ''}
                  </span>
                </div>
                <div className="flex justify-between items-center pt-1 text-sm">
                  <span className="font-bold text-slate-900">Tổng phụ thu cộng vào đơn:</span>
                  <span className="font-mono font-bold text-brand-800 text-base">
                    {Number(amount).toLocaleString('vi-VN')} ₫
                  </span>
                </div>
              </div>

              {evidenceUrl && (
                <div className="rounded-xl border border-slate-200 p-3 text-xs flex items-center justify-between">
                  <span className="text-slate-500">Minh chứng kèm theo:</span>
                  <a
                    href={evidenceUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="font-medium text-brand-700 underline truncate max-w-[240px]"
                  >
                    Xem tài liệu
                  </a>
                </div>
              )}

              {isAbnormal && (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-3.5 text-xs text-amber-900 space-y-2">
                  <div className="font-bold flex items-center gap-1.5">
                    <Icon name="report" className="text-amber-700 text-[18px]" />
                    Xác nhận cam kết phụ phí lớn
                  </div>
                  <label className="flex items-start gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={confirmedByProvider}
                      onChange={(e) => setConfirmedByProvider(e.target.checked)}
                      className="mt-0.5 rounded border-amber-400 text-brand-600 focus:ring-brand-500"
                    />
                    <span className="leading-snug">
                      Tôi xác nhận phụ phí này đã được thông báo và đối chiếu với khách thuê, có đầy đủ căn cứ thực tế.
                    </span>
                  </label>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t border-slate-200 px-6 py-4 bg-slate-50">
          {step === 1 ? (
            <>
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                form="add-charge-form"
                className="rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800"
              >
                Tiếp tục xác nhận
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => setStep(1)}
                disabled={loading}
                className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Quay lại sửa
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={loading || (isAbnormal && !confirmedByProvider)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50"
              >
                {loading && <Icon name="sync" className="animate-spin text-[16px]" />}
                <span>{loading ? 'Đang lưu...' : 'Lưu phụ phí vào đơn'}</span>
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
