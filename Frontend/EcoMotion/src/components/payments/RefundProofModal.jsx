import { useState } from 'react'
import { providerFinanceService } from '../../services/providerFinanceService'

export function RefundProofModal({ refund, onClose, onSuccess }) {
  const [file, setFile] = useState(null)
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    if (!file || !reference.trim()) {
      setError('Cần biên lai và mã giao dịch ngân hàng.')
      return
    }
    if (
      file.size > 5 * 1024 * 1024 ||
      !['image/jpeg', 'image/png', 'image/webp', 'application/pdf'].includes(file.type)
    ) {
      setError('Chọn JPG, PNG, WebP hoặc PDF tối đa 5 MB.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await providerFinanceService.completeRefund(refund.paymentTransactionId, {
        file,
        bankReference: reference.trim(),
        note: note.trim(),
      })
      await onSuccess()
      onClose()
    } catch (err) {
      setError(err.message || 'Không thể xác nhận hoàn tiền. Vui lòng tải lại và thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <form
        onSubmit={submit}
        className="w-full max-w-lg max-h-[90vh] flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg"
        role="dialog"
        aria-modal="true"
        aria-label="Biên lai hoàn tiền"
      >
        <div className="flex shrink-0 items-center justify-between border-b border-slate-200 bg-slate-50 px-6 py-4">
          <div>
            <h2 className="text-base font-bold text-slate-900">
              Xác nhận đã chuyển khoản hoàn tiền
            </h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Đơn <span className="font-mono font-semibold text-slate-700">{refund.bookingCode}</span>. Chỉ tải biên lai sau khi đã thực hiện chuyển khoản.
            </p>
          </div>
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            aria-label="Đóng cửa sổ"
            className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-200 hover:text-slate-700"
          >
            <span className="material-symbols-rounded text-[20px] leading-none">close</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          <label className="block text-xs font-semibold text-slate-700">
            Biên lai
            <input
              required
              type="file"
              accept="image/jpeg,image/png,image/webp,application/pdf"
              onChange={(e) => setFile(e.target.files[0])}
              className="mt-1.5 block w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700 file:mr-3 file:rounded-md file:border-0 file:bg-emerald-50 file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-emerald-700 hover:file:bg-emerald-100"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Mã giao dịch
            <input
              required
              maxLength={100}
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              placeholder="Ví dụ: FT2610982341"
              className="mt-1.5 block h-10 w-full rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-slate-700">
            Ghi chú
            <textarea
              rows={3}
              maxLength={500}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Ghi chú đối soát (nếu có)..."
              className="mt-1.5 block w-full rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-800 focus:border-emerald-600 focus:outline-none"
            />
          </label>

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
            onClick={onClose}
            className="inline-flex h-10 items-center justify-center rounded-lg border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Đóng
          </button>
          <button
            disabled={busy}
            className="inline-flex h-10 items-center justify-center rounded-lg bg-emerald-700 px-4 text-xs font-bold text-white hover:bg-emerald-800 disabled:opacity-50"
          >
            {busy ? 'Đang xác minh...' : 'Gửi biên lai xác nhận'}
          </button>
        </div>
      </form>
    </div>
  )
}
