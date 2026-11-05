import { useState } from 'react'
import { formatCurrency } from '../../utils/formatCurrency'
import { RefundAccountFields } from './RefundAccountFields'

export const CancelBookingModal = ({ bookingCode, isOpen, onClose, onConfirmCancel, refundExplanation,
  refundAmount = 0, requiresBankAccount = false }) => {
  const [account, setAccount] = useState({ bankName: '', accountHolder: '', accountNumber: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  if (!isOpen) return null

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await onConfirmCancel(requiresBankAccount ? account : undefined)
    } catch (err) {
      setError(err.message || 'Chưa hủy được đơn. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div onClick={() => !busy && onClose()} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
      <form onSubmit={submit} onClick={e => e.stopPropagation()} role="dialog" aria-modal="true"
        aria-labelledby="cancel-booking-title" className="relative flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-y-auto rounded-xl border border-slate-200 bg-white p-5 shadow-lg sm:p-8">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-red-50 text-red-700">
            <span className="material-symbols-outlined" aria-hidden="true">warning</span>
          </div>
          <button type="button" onClick={onClose} disabled={busy} aria-label="Đóng" className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-slate-700 hover:bg-slate-200 disabled:opacity-50">
            <span className="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>
        <h2 id="cancel-booking-title" className="text-xl font-semibold text-slate-900">Huỷ đơn này?</h2>
        <p className="mt-2 text-sm font-semibold text-emerald-800">{bookingCode}</p>
        <p className="mt-3 text-sm leading-6 text-slate-700">{refundExplanation || 'Đơn sẽ được hủy theo yêu cầu của bạn.'}</p>
        {refundAmount > 0 && <p className="mt-3 text-sm text-slate-700">Số tiền dự kiến hoàn: <strong className="tabular-nums text-slate-900">{formatCurrency(refundAmount)}</strong></p>}
        {requiresBankAccount && <section className="mt-5 space-y-3 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-slate-900">Tài khoản nhận hoàn</h3>
          <p className="text-xs leading-5 text-slate-600">Nhà xe sẽ chuyển khoản vào tài khoản bạn cung cấp dưới đây.</p>
          <RefundAccountFields value={account} onChange={setAccount} disabled={busy}
            className="grid grid-cols-1 gap-3 sm:grid-cols-2 [&>label:first-child]:sm:col-span-2" />
        </section>}
        {error && <p role="alert" className="mt-4 text-sm text-red-700">{error}</p>}
        <div className="mt-6 flex items-center gap-3">
          <button type="button" onClick={onClose} disabled={busy} className="min-h-11 flex-1 rounded-lg border border-slate-300 px-4 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Giữ đơn</button>
          <button type="submit" disabled={busy} className="min-h-11 flex-1 rounded-lg bg-red-700 px-4 text-sm font-semibold text-white hover:bg-red-800 disabled:opacity-50">{busy ? 'Đang hủy...' : 'Xác nhận huỷ'}</button>
        </div>
      </form>
    </div>
  )
}
