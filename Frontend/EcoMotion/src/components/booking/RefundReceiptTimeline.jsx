import { useEffect, useState } from 'react'
import apiClient from '../../services/apiClient'
import { formatBookingWhen } from '../../utils/bookingDateTime'

export default function RefundReceiptTimeline({ refund, provider, onRefresh }) {
  const [now, setNow] = useState(() => Date.now())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [proof, setProof] = useState(null)
  const [proofBusy, setProofBusy] = useState(false)
  useEffect(() => () => { if (proof?.url) URL.revokeObjectURL(proof.url) }, [proof])
  useEffect(() => {
    if (refund.receiptStatus !== 'WAITING' || !refund.receiptDeadline) return
    let stopped = false
    let timer
    async function refresh() {
      try { await onRefresh?.() } catch { /* Retry while the server is unavailable. */ }
      if (!stopped) timer = setTimeout(refresh, 10000)
    }
    timer = setTimeout(refresh, Math.max(0, Date.parse(refund.receiptDeadline) - Date.now()))
    return () => { stopped = true; clearTimeout(timer) }
  }, [refund.receiptStatus, refund.receiptDeadline, onRefresh])
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  if (!refund.receiptStatus && !refund.proofAvailable) return null
  const waiting = refund.receiptStatus === 'WAITING'
  const reported = refund.receiptStatus === 'REPORTED_NOT_RECEIVED'
  const deadline = Date.parse(refund.receiptDeadline)
  const seconds = Number.isFinite(deadline) ? Math.max(0, Math.ceil((deadline - now) / 1000)) : 0
  const hours = String(Math.floor(seconds / 3600)).padStart(2, '0')
  const minutes = String(Math.floor(seconds % 3600 / 60)).padStart(2, '0')
  const secs = String(seconds % 60).padStart(2, '0')
  const when = value => formatBookingWhen(value) || 'Chưa xác định'
  async function viewProof() {
    setProofBusy(true); setError('')
    try {
      const { data } = await apiClient.get(`/payment-transactions/${refund.paymentTransactionId}/refund-proof`, { responseType: 'blob' })
      setProof({ url: URL.createObjectURL(data), type: data.type })
    } catch { setError('Không tải được biên lai hoàn tiền. Vui lòng thử lại.') }
    finally { setProofBusy(false) }
  }
  async function respond(received) {
    if (busy) return
    setBusy(true); setError('')
    try {
      await apiClient.post(`/refunds/${refund.id}/${received ? 'received' : 'not-received'}`)
      await onRefresh()
    } catch (err) { setError(err.message || 'Chưa gửi được xác nhận. Vui lòng thử lại.') }
    finally { setBusy(false) }
  }
  return <div className="mt-3 space-y-2 border-t border-slate-200 pt-3 text-xs text-slate-700">
    <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
    <ol className="min-w-0 flex-1 space-y-2 border-l-2 border-emerald-200 pl-3" aria-label="Lịch sử xác nhận nhận tiền">
      <li>Nhà xe xác nhận chuyển hoàn: <strong>{when(refund.receiptWaitingAt)}</strong></li>
      <li>Hạn phản hồi của người thuê: <strong>{when(refund.receiptDeadline)}</strong> (72 giờ)</li>
      {waiting && <li>{seconds > 0 ? 'Chờ người thuê xác nhận đã nhận tiền.' : 'Đã hết thời hạn, đang chờ hệ thống ghi nhận.'}</li>}
      {reported && <li className="font-semibold text-amber-800">Khách báo chưa nhận tiền lúc {when(refund.receiptReportedAt)}. Đã dừng tự động xác nhận; nhà xe cần đối soát.</li>}
      {refund.receiptStatus === 'CONFIRMED' && <li className="font-semibold text-emerald-800">Người thuê đã xác nhận nhận tiền: {when(refund.receiptConfirmedAt)}</li>}
      {refund.receiptAutoConfirmedAt && <li className="font-semibold text-slate-800">Tự động xác nhận do hết thời hạn: {when(refund.receiptAutoConfirmedAt)}. Đây không phải xác nhận trực tiếp của người thuê.</li>}
    </ol>
    {waiting && <div className="shrink-0 self-end text-right sm:self-start" role="timer" aria-label="Thời gian còn lại để xác nhận nhận tiền" aria-live="off">
      <p className="mb-1 text-sm font-semibold text-emerald-900">Thời gian xác nhận còn lại</p>
      <p className="font-mono text-4xl font-bold tabular-nums tracking-tight text-emerald-900 sm:text-5xl">{hours}:{minutes}:{secs}</p>
      <p className="mt-1 text-xs text-slate-600">Giờ : Phút : Giây</p>
    </div>}
    </div>
    {waiting && <p>Hết 72 giờ không có phản hồi, hệ thống tự động ghi nhận đã nhận tiền. Nếu tiền chưa vào tài khoản, hãy báo để dừng tự động xác nhận.</p>}
    {!provider && ((waiting && deadline > now) || reported) && <div className="flex flex-wrap gap-2">
      <button type="button" disabled={busy} onClick={() => respond(true)} className="min-h-10 rounded-lg bg-emerald-800 px-3 font-semibold text-white disabled:opacity-50">Đã nhận tiền</button>
      {!reported && <button type="button" disabled={busy} onClick={() => respond(false)} className="min-h-10 rounded-lg border border-amber-400 px-3 font-semibold text-amber-900 disabled:opacity-50">Chưa nhận được tiền</button>}
    </div>}
    {refund.proofAvailable && <button type="button" disabled={proofBusy} onClick={viewProof} className="min-h-10 rounded-lg border border-emerald-800 px-3 font-semibold text-emerald-900 disabled:opacity-50">{proofBusy ? 'Đang tải biên lai...' : 'Xem biên lai hoàn tiền'}</button>}
    {proof && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4" onClick={() => setProof(null)}>
      <section role="dialog" aria-modal="true" aria-label="Biên lai hoàn tiền của nhà xe" className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-xl bg-white p-4" onClick={event => event.stopPropagation()}>
        <div className="mb-3 flex items-center justify-between gap-3"><h3 className="text-base font-semibold">Biên lai hoàn tiền của nhà xe</h3><button type="button" onClick={() => setProof(null)} className="min-h-10 px-3 font-semibold">Đóng</button></div>
        {proof.type.startsWith('image/') ? <img src={proof.url} alt="Biên lai chuyển khoản hoàn tiền do nhà xe cung cấp" className="min-h-0 object-contain" /> : <iframe src={proof.url} title="Nội dung biên lai hoàn tiền" className="h-[65vh] w-full border-0" />}
        <a href={proof.url} download="bien-lai-hoan-tien" className="mt-3 min-h-10 py-2 font-semibold text-emerald-900 underline">Tải biên lai</a>
      </section>
    </div>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
  </div>
}
