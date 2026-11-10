import { useState } from 'react'
import { updateRefundAccount } from '../../services/bookingService'
import { RefundAccountFields } from './RefundAccountFields'

export function RefundAccountModal({ bookingCode, refunds, onClose, onSaved }) {
  const current = refunds.find(refund => refund.recipientAccountNumber) || {}
  const needsAccount = refunds.some(refund => !refund.recipientAccountNumber)
  const [account, setAccount] = useState({ bankName: current.recipientBankName || '',
    accountHolder: current.recipientAccountHolder || '', accountNumber: current.recipientAccountNumber || '' })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError(''); setMessage('')
    try {
      await updateRefundAccount(bookingCode, account)
      await onSaved()
      setMessage('Đã lưu tài khoản nhận hoàn. Nhà xe có thể xem thông tin này để chuyển khoản.')
    } catch (err) {
      setError(err.message || 'Chưa lưu được tài khoản nhận hoàn.')
    } finally { setBusy(false) }
  }

  return (
    <div onClick={() => !busy && onClose()} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <form onSubmit={submit} onClick={e => e.stopPropagation()} role="dialog" aria-modal="true"
        aria-labelledby="refund-account-title" className="relative flex max-h-[calc(100dvh-2rem)] w-full max-w-lg flex-col overflow-y-auto rounded-xl border border-slate-200 bg-white p-5 shadow-lg sm:p-6">
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 pb-4">
          <div>
            <h2 id="refund-account-title" className="text-lg font-semibold text-slate-900">
              {needsAccount ? 'Bổ sung tài khoản nhận hoàn' : 'Tài khoản nhận hoàn'}
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Đơn <span className="font-mono font-semibold text-slate-700">{bookingCode}</span>
            </p>
          </div>
          <button type="button" onClick={onClose} disabled={busy} aria-label="Đóng"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-700 hover:bg-slate-200 disabled:opacity-50">
            <span className="material-symbols-rounded text-[20px] leading-none" aria-hidden="true">close</span>
          </button>
        </div>

        <div className="space-y-4 pt-4">
          {needsAccount
            ? <p role="status" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">Nhà xe đang chờ thông tin tài khoản để chuyển khoản hoàn tiền. Vui lòng nhập tài khoản nhận hoàn bên dưới.</p>
            : <p className="text-sm text-slate-600">Kiểm tra thông tin để nhà xe chuyển khoản hoàn tiền chính xác.</p>}
          <RefundAccountFields value={account} onChange={setAccount} disabled={busy} />
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
          {message && <p role="status" className="text-sm text-emerald-800">{message}</p>}
        </div>

        <div className="mt-6 flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
          <button type="button" onClick={onClose} disabled={busy}
            className="min-h-11 rounded-lg border border-slate-300 px-4 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
            Đóng
          </button>
          <button type="submit" disabled={busy}
            className="min-h-11 rounded-lg bg-emerald-800 px-4 text-sm font-semibold text-white hover:bg-emerald-900 disabled:opacity-50">
            {busy ? 'Đang lưu...' : 'Lưu tài khoản nhận hoàn'}
          </button>
        </div>
      </form>
    </div>
  )
}
