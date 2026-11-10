import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { providerBookingService } from '../../../services/providerBookingService'
import { chargeQuote } from '../../../utils/tv5Contracts'
import { chargeRequestIdentity } from '../../../utils/chargeRequestIdentity'

const labels = {
  OVERTIME: 'Quá giờ (số slot vượt)',
  TOLL: 'Phí cầu đường',
  PARKING: 'Phí gửi xe',
  OTHER: 'Phụ phí khác',
}

export default function AddChargeModal({ booking, isOpen, onClose, onSuccess }) {
  const [chargeType, setChargeType] = useState('OVERTIME')
  const [quantity, setQuantity] = useState(1)
  const [description, setDescription] = useState('')
  const [files, setFiles] = useState([])
  const [previews, setPreviews] = useState([])
  const [fieldErrors, setFieldErrors] = useState({})
  const [enteredAmount, setEnteredAmount] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const pendingRequest = useRef(null)
  const submittingRef = useRef(false)
  const previewUrls = useRef([])
  const fileInputRef = useRef(null)
  const replaceIndex = useRef(null)
  const dialogRef = useRef(null)

  useEffect(() => () => previewUrls.current.forEach((url) => URL.revokeObjectURL(url)), [])

  useEffect(() => {
    if (!isOpen) return undefined
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      previousFocus?.focus()
    }
  }, [isOpen])

  const replaceFiles = (nextFiles) => {
    previewUrls.current.forEach((url) => URL.revokeObjectURL(url))
    previewUrls.current = nextFiles.map((file) => URL.createObjectURL(file))
    setPreviews(previewUrls.current)
    setFiles(nextFiles)
  }

  if (!isOpen || !booking) return null

  const moneyCharge = chargeType !== 'OVERTIME'
  const quote = chargeQuote(booking, chargeType, quantity, enteredAmount)

  const submit = async (event) => {
    event.preventDefault()
    if (submittingRef.current) return
    const errors = {}
    if (!description.trim()) errors.description = 'Vui lòng nhập lý do phụ phí.'
    else if (chargeType === 'OTHER' && description.trim().length < 20) errors.description = 'Vui lòng nhập ít nhất 20 ký tự.'
    if (!files.length) errors.files = 'Vui lòng tải ảnh bằng chứng.'
    if (files.length > 5) errors.files = 'Chỉ được chọn tối đa 5 ảnh.'
    if (files.some((file) => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type))) errors.files = 'Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.'
    if (files.some((file) => !file.size || file.size > 5 * 1024 * 1024)) errors.files = 'Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MB.'
    if (chargeType === 'OVERTIME' && (!Number.isInteger(Number(quantity)) || Number(quantity) < 1 || Number(quantity) > 1000000)) errors.amount = 'Số slot phải là số nguyên từ 1 đến 1.000.000.'
    else if (!Number.isFinite(quote.amount) || quote.amount <= 0) errors.amount = 'Vui lòng nhập số tiền hợp lệ lớn hơn 0.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) {
      return
    }
    if (!confirming) {
      setConfirming(true)
      setError('')
      return
    }
    const payload = {
      chargeType,
      quantity: Number(quantity),
      description: description.trim(),
      ...quote,
    }
    const identityPayload = { ...payload, files: files.map(({ name, size, lastModified }) => ({ name, size, lastModified })) }
    pendingRequest.current = chargeRequestIdentity(pendingRequest.current, booking.bookingCode, identityPayload, () => crypto.randomUUID())
    submittingRef.current = true
    setBusy(true)
    setError('')
    try {
      const created = await providerBookingService.addChargeWithEvidence(booking.bookingCode, {
        ...payload,
        clientRequestId: pendingRequest.current.id,
      }, files)
      pendingRequest.current = null
      replaceFiles([])
      onSuccess?.(created, 'Tải ảnh lên thành công và đã ghi nhận phụ phí.')
      setConfirming(false)
      onClose()
    } catch (err) {
      setError(err.message || 'Không thể lưu phụ phí.')
      setFieldErrors((prev) => ({ ...prev, files: 'Không thể hoàn tất tải ảnh và lưu phụ phí. Vui lòng thử lại.' }))
    } finally {
      submittingRef.current = false
      setBusy(false)
    }
  }

  const chooseFiles = (index = null) => {
    replaceIndex.current = index
    if (fileInputRef.current) fileInputRef.current.multiple = index === null
    fileInputRef.current?.click()
  }

  const handleFileSelection = (event) => {
    const selected = Array.from(event.target.files || [])
    if (selected.length) {
      if (replaceIndex.current === null) replaceFiles(selected)
      else {
        const next = [...files]
        next.splice(replaceIndex.current, 1, selected[0])
        replaceFiles(next)
      }
      setFieldErrors((prev) => ({ ...prev, files: '' }))
      setError('')
    }
    replaceIndex.current = null
    event.target.value = ''
  }

  return createPortal(
    <div className="fixed inset-0 z-[1100] flex items-center justify-center bg-slate-900/50 p-3 sm:p-4">
      <form
        ref={dialogRef}
        onSubmit={submit}
        noValidate
        role="dialog"
        tabIndex={-1}
        aria-modal="true"
        aria-label="Thêm phụ phí"
        className="flex max-h-[calc(100dvh-1.5rem)] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg sm:max-h-[calc(100dvh-2rem)]"
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

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 sm:p-6">
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

            {moneyCharge ? (
              <label className="block text-xs font-semibold text-slate-700">
                Số tiền
                <input
                  required
                  type="number"
                  min="1"
                  step="1"
                  value={enteredAmount}
                  onChange={(e) => setEnteredAmount(e.target.value)}
                  className="mt-1.5 block h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none disabled:bg-slate-50"
                />
              </label>
            ) : (
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
            )}
            {fieldErrors.amount && <p role="alert" className="text-xs text-red-600">{fieldErrors.amount}</p>}

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
            {fieldErrors.description && <p role="alert" className="text-xs text-red-600">{fieldErrors.description}</p>}

            <div className="space-y-2" role="group" aria-labelledby="charge-evidence-label" aria-describedby="charge-evidence-help charge-evidence-error">
              <p id="charge-evidence-label" className="text-xs font-semibold text-slate-700">Tải bằng chứng</p>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp"
                onChange={handleFileSelection}
                tabIndex={-1}
                aria-hidden="true"
                className="sr-only"
              />
              <button type="button" onClick={() => chooseFiles()} className="rounded-lg border border-emerald-700 px-3 py-2 text-xs font-semibold text-emerald-800 hover:bg-emerald-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700">Chọn ảnh từ thiết bị</button>
              <p id="charge-evidence-help" className="text-xs text-slate-500">Hỗ trợ JPG, PNG, WebP; tối đa 5 ảnh, 5 MB mỗi ảnh theo cấu hình mặc định.</p>
            </div>
            <p id="charge-evidence-error" role={fieldErrors.files ? 'alert' : undefined} className="text-xs text-red-600">{fieldErrors.files || ''}</p>
            {files.length > 0 && <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {files.map((file, index) => <div key={`${file.name}-${index}`} className="rounded-lg border border-slate-200 p-2">
                {previews[index] && <img src={previews[index]} alt={`Bằng chứng ${index + 1}`} className="h-20 w-full rounded object-cover" />}
                <p className="mt-1 truncate text-xs" title={file.name}>{file.name}</p>
                <p aria-live="polite" className="mt-1 text-xs text-slate-500">{busy ? 'Đang tải lên...' : error ? 'Không thể tải lên hoặc lưu' : 'Chờ xác nhận để tải lên'}</p>
                <div className="mt-2 flex flex-wrap gap-x-2 gap-y-1 text-xs font-semibold">
                  <a href={previews[index]} target="_blank" rel="noopener noreferrer" className="text-emerald-700 underline" aria-label={`Xem ảnh ${file.name}`}>Xem ảnh</a>
                  <button type="button" onClick={() => chooseFiles(index)} className="text-emerald-700 underline" aria-label={`Thay ảnh ${file.name}`}>Thay ảnh</button>
                  <button type="button" onClick={() => replaceFiles(files.filter((_, i) => i !== index))} className="text-red-700 underline" aria-label={`Xóa ảnh ${file.name}`}>Xóa ảnh</button>
                </div>
              </div>)}
            </div>}
          </fieldset>

          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3.5 space-y-1">
            <p className="text-xs text-slate-700">
              Đơn giá {quote.unitPrice.toLocaleString('vi-VN')} × {quantity} × {Math.round(Number(quote.appliedRate) * 100)}%:{' '}
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
            {busy ? 'Đang tải ảnh và lưu...' : confirming ? 'Xác nhận thêm phụ phí' : 'Tiếp tục'}
          </button>
        </div>
      </form>
    </div>
  , document.body)
}
