import { useState } from 'react'
import { updateRefundAccount } from '../../services/bookingService'
import { RefundAccountFields } from './RefundAccountFields'

export function RefundAccountPanel({ bookingCode, refunds, onSaved }) {
  const current = refunds.find(refund => refund.recipientAccountNumber) || {}
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
  return <form onSubmit={submit} className="mx-auto max-w-5xl space-y-4 rounded-xl border border-slate-200 bg-white p-5">
    <h2 className="text-base font-semibold text-slate-900">Tài khoản nhận hoàn</h2>
    <p className="text-sm text-slate-600">Kiểm tra thông tin để nhà xe chuyển khoản hoàn tiền chính xác.</p>
    <RefundAccountFields value={account} onChange={setAccount} disabled={busy} />
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {message && <p role="status" className="text-sm text-emerald-800">{message}</p>}
    <button disabled={busy} className="min-h-11 rounded-lg bg-emerald-800 px-4 text-sm font-semibold text-white hover:bg-emerald-900 disabled:opacity-50">{busy ? 'Đang lưu...' : 'Lưu tài khoản nhận hoàn'}</button>
  </form>
}
