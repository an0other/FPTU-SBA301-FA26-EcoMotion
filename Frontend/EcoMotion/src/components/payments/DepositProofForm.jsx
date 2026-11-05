import { useState } from 'react'

export function DepositProofForm({ disabled, busy, onSubmit }) {
  const [file, setFile] = useState(null)
  const [transactionReference, setTransactionReference] = useState('')
  const [note, setNote] = useState('')

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!file || disabled || busy) return
    onSubmit({ file, transactionReference: transactionReference.trim(), note: note.trim() })
  }

  return (
    <form onSubmit={handleSubmit} className="mt-6 space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div>
        <p className="text-sm font-bold text-[#14221A]">Gửi xác nhận đã chuyển khoản</p>
        <p className="mt-1 text-xs text-slate-600">
          Tải ảnh biên lai hoặc màn hình chuyển khoản thành công. Hệ thống gia hạn giữ chỗ và chuyển sang bước đối soát (nhà xe xác nhận), không tự huỷ đơn sau 15 phút.
        </p>
      </div>
      <div>
        <label className="block text-xs font-semibold text-slate-700">Ảnh biên lai (JPG, PNG, WebP) *</label>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          disabled={disabled || busy}
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          className="mt-1 block w-full text-xs text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-700 file:px-3 file:py-2 file:text-xs file:font-semibold file:text-white"
        />
      </div>
      <div>
        <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng (tuỳ chọn)</label>
        <input
          type="text"
          value={transactionReference}
          disabled={disabled || busy}
          onChange={(event) => setTransactionReference(event.target.value)}
          placeholder="FT24..."
          className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-xs"
        />
      </div>
      <div>
        <label className="block text-xs font-semibold text-slate-700">Ghi chú (tuỳ chọn)</label>
        <textarea
          rows={2}
          value={note}
          disabled={disabled || busy}
          onChange={(event) => setNote(event.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-xs"
        />
      </div>
      <button
        type="submit"
        disabled={disabled || busy || !file}
        className="inline-flex w-full items-center justify-center rounded-xl bg-brand-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-800 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
      >
        {busy ? 'Đang gửi biên lai...' : 'Gửi xác nhận đã chuyển khoản'}
      </button>
    </form>
  )
}
