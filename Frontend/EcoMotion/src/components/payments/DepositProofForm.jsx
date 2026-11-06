import { useEffect, useMemo, useState } from 'react'
import { ReceiptDropzone } from './ReceiptDropzone'

export function DepositProofForm({ disabled, busy, onSubmit, isFinalBalance = false }) {
  const [file, setFile] = useState(null)
  const [fileError, setFileError] = useState('')
  const [transactionReference, setTransactionReference] = useState('')
  const [note, setNote] = useState('')

  // Preview tạo từ file đang chọn; URL được thu hồi khi file đổi hoặc form unmount.
  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : ''), [file])
  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  // ReceiptDropzone trả về (file) khi hợp lệ, hoặc (null, thông báo lỗi tiếng Việt) khi sai định dạng/dung lượng.
  const handleFile = (next, message) => {
    setFileError(message || '')
    setFile(next)
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!file || disabled || busy) return
    onSubmit({ file, transactionReference: transactionReference.trim(), note: note.trim() })
  }

  return (
    <form onSubmit={handleSubmit} className="mt-6 space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div>
        <p className="text-sm font-bold text-[#14221A]">
          {isFinalBalance ? 'Gửi biên lai thanh toán quyết toán' : 'Gửi xác nhận đã chuyển khoản'}
        </p>
        <p className="mt-1 text-xs text-slate-600">
          {isFinalBalance
            ? 'Tải ảnh biên lai hoặc màn hình chuyển khoản thành công để nhà xe đối soát và xác nhận hoàn tất đơn thuê.'
            : 'Tải ảnh biên lai hoặc màn hình chuyển khoản thành công. Hệ thống gia hạn giữ chỗ và chuyển sang bước đối soát (nhà xe xác nhận), không tự huỷ đơn sau 15 phút.'}
        </p>
      </div>
      <div>
        <ReceiptDropzone
          file={file}
          previewUrl={previewUrl}
          onFile={handleFile}
          error={fileError}
          disabled={disabled || busy}
        />
        {file ? (
          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-600">
            <span>Bấm vào khung ảnh để chọn ảnh khác.</span>
            <button
              type="button"
              onClick={() => handleFile(null)}
              disabled={disabled || busy}
              className="min-h-9 rounded-lg border border-red-300 px-3 font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
            >
              Xóa ảnh đã chọn
            </button>
          </div>
        ) : null}
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
        {busy ? 'Đang gửi biên lai...' : isFinalBalance ? 'Gửi biên lai quyết toán' : 'Gửi xác nhận đã chuyển khoản'}
      </button>
    </form>
  )
}
