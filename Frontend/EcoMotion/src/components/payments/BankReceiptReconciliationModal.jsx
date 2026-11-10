import { useRef, useState } from 'react'
import { providerFinanceService } from '../../services/providerFinanceService'
import { bankReceiptPayload } from '../../utils/bankReceipt'

export function BankReceiptReconciliationModal({ request, onClose, onRecorded }) {
  const [requestId] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submitting = useRef(false)
  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm'
  async function submit(event) {
    event.preventDefault()
    if (submitting.current) return
    try {
      const data = Object.fromEntries(new FormData(event.currentTarget))
      const payload = bankReceiptPayload({ ...data, bankReceiptVerified: data.bankReceiptVerified === 'on' }, requestId)
      submitting.current = true
      setBusy(true)
      setError('')
      await providerFinanceService.recordBankReceipt(request.id, payload)
      onRecorded()
    } catch (err) {
      setError(err.message || 'Không thể ghi nhận đối soát. Hãy kiểm tra lịch sử trước khi gửi lại.')
    } finally {
      submitting.current = false
      setBusy(false)
    }
  }
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
    <section role="dialog" aria-modal="true" aria-labelledby="bank-receipt-title" className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
      <h3 id="bank-receipt-title" className="font-bold text-slate-900">Ghi nhận tiền cần đối soát — {request.bookingCode}</h3>
      <p className="my-3 text-sm text-slate-600">Chỉ dùng khi đã đối chiếu khoản ghi có trong tài khoản ngân hàng nhận. Ảnh QR hoặc biên lai khách gửi chưa đủ để xác nhận thực nhận. Khoản này chưa thanh toán nghĩa vụ, không xác nhận booking và không tự tạo hoàn tiền.</p>
      <form onSubmit={submit} className="space-y-3">
        <fieldset disabled={busy} className="space-y-3">
          <label className="block text-sm">Số tiền thực nhận (VND)<input name="receivedAmount" type="number" min="0.01" max="999999999999.99" step="0.01" required className={inputClass} /></label>
          <label className="block text-sm">Mã giao dịch ngân hàng<input name="bankTransactionRef" required maxLength={100} className={inputClass} /></label>
          <label className="block text-sm">Tham chiếu sao kê tài khoản nhận<input name="bankStatementReference" required maxLength={200} className={inputClass} /></label>
          <label className="block text-sm">Thời điểm ngân hàng ghi có<input name="receivedAt" type="datetime-local" required className={inputClass} /></label>
          <label className="block text-sm">Ghi chú đối soát<textarea name="note" maxLength={500} rows={2} className={inputClass} /></label>
          <label className="flex items-start gap-2 text-sm"><input name="bankReceiptVerified" type="checkbox" required className="mt-1" />Tôi đã xác minh số tiền, mã giao dịch và tài khoản nhận từ sao kê ngân hàng.</label>
        </fieldset>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <div className="flex justify-end gap-2 border-t pt-3">
          <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border px-3 py-2 text-sm">Đóng</button>
          <button type="submit" disabled={busy} className="rounded-lg bg-brand-700 px-3 py-2 text-sm font-semibold text-white">{busy ? 'Đang ghi nhận…' : 'Ghi tiền thực nhận để đối soát'}</button>
        </div>
      </form>
    </section>
  </div>
}
