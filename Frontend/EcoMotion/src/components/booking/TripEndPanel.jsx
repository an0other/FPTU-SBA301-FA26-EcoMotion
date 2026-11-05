import { useEffect, useRef, useState } from 'react'
import { confirmTripEnd, openDispute, uploadDisputeEvidence } from '../../services/bookingService'
import { formatBookingWhen } from '../../utils/bookingDateTime'

export default function TripEndPanel({ workflow, provider, onRefresh }) {
  const [now, setNow] = useState(Date.now)
  const [reason, setReason] = useState('')
  const [files, setFiles] = useState([])
  const [objecting, setObjecting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const refresh = useRef(onRefresh)
  useEffect(() => { refresh.current = onRefresh }, [onRefresh])
  const latest = workflow?.endConfirmations?.[0]
  useEffect(() => {
    if (latest?.state !== 'WAITING') return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [latest?.id, latest?.state])
  useEffect(() => {
    if (latest?.state !== 'WAITING') return
    let cancelled = false; let timer
    async function sync() {
      try { await refresh.current?.() }
      catch { if (!cancelled) setError('Chưa tải được trạng thái mới. Hệ thống sẽ thử lại sau 10 giây.') }
      finally { if (!cancelled) timer = setTimeout(sync, 10000) }
    }
    timer = setTimeout(sync, Math.max(0, Date.parse(latest.deadline) - Date.now()))
    return () => { cancelled = true; clearTimeout(timer) }
  }, [latest?.id, latest?.state, latest?.deadline])
  if (!latest) return null
  const seconds = Math.max(0, Math.ceil((Date.parse(latest.deadline) - now) / 1000))
  const countdown = [Math.floor(seconds / 3600), Math.floor(seconds % 3600 / 60), seconds % 60].map(n => String(n).padStart(2, '0')).join(':')
  async function act(objection) {
    if (busy) return
    setBusy(true); setError('')
    try {
      if (objection) {
        const evidenceUrls = []
        for (const file of files) evidenceUrls.push(await uploadDisputeEvidence(workflow.bookingCode, file))
        await openDispute(workflow.bookingCode, { category: 'TRIP_NOT_CONFIRMED', description: reason.trim(), evidenceUrls })
      } else await confirmTripEnd(workflow.bookingCode)
      await refresh.current?.(); setObjecting(false)
    } catch (err) { setError(err.message || 'Chưa lưu được phản hồi. Vui lòng tải lại trạng thái.') }
    finally { setBusy(false) }
  }
  return <section aria-label="Xác nhận kết thúc chuyến" className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 text-sm text-slate-800">
    <h2 className="text-base font-semibold">Xác nhận kết thúc chuyến</h2>
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><p>Tài xế báo kết thúc: <strong>{formatBookingWhen(latest.reportedAt)}</strong></p>
        {latest.state === 'WAITING' && <p>Hạn phản hồi: <strong>{formatBookingWhen(latest.deadline)}</strong> (12 giờ)</p>}
      </div>
      {latest.state === 'WAITING' && <div role="timer" aria-label="Thời gian xác nhận kết thúc còn lại" className="font-mono text-4xl font-bold tabular-nums text-emerald-900">{countdown}</div>}
    </div>
    {latest.state === 'WAITING' && <p>Hết hạn không phản đối, hệ thống xác nhận kết thúc và chuyển sang kiểm tra phụ phí. Thời gian chờ không tính thêm tiền thuê.</p>}
    {latest.state === 'CANCELLED' && <p>Lần báo kết thúc này đã được hủy. Chuyến đang tiếp tục; xem lịch sử xử lý bên dưới.</p>}
    {latest.state === 'PAUSED' && <p className="font-semibold text-amber-900">Đã dừng tự động xác nhận. Khiếu nại đang chờ admin xác minh; đóng khiếu nại không tự kết thúc chuyến.</p>}
    {latest.state === 'PAUSED' && latest.disputeId && <a href="#booking-disputes" className="inline-block text-emerald-800 underline">Xem hồ sơ khiếu nại #{latest.disputeId}</a>}
    {latest.state === 'CONFIRMED' && <p className="font-semibold text-emerald-900">{latest.confirmationMethod === 'AUTO' ? 'Hệ thống tự xác nhận do hết thời hạn' : 'Người thuê đã xác nhận kết thúc'}: {formatBookingWhen(latest.confirmedAt)}</p>}
    {!provider && latest.state === 'WAITING' && seconds > 0 && <div className="flex flex-wrap gap-2">
      <button disabled={busy} onClick={() => act(false)} className="min-h-11 rounded-lg bg-emerald-800 px-4 font-semibold text-white">Xác nhận kết thúc</button>
      <button disabled={busy} onClick={() => setObjecting(true)} className="min-h-11 rounded-lg border border-amber-400 px-4 font-semibold text-amber-900">Chuyến chưa kết thúc / Có vấn đề</button>
    </div>}
    {objecting && latest.state === 'WAITING' && seconds > 0 && <form onSubmit={e => { e.preventDefault(); act(true) }} className="space-y-3">
      <label className="block">Nội dung phản đối<textarea required maxLength={2000} value={reason} onChange={e => setReason(e.target.value)} className="mt-1 block w-full rounded-lg border p-3" /></label>
      <label className="block">Bằng chứng (không bắt buộc)<input type="file" accept="image/*" multiple onChange={e => setFiles(Array.from(e.target.files).slice(0, 5))} className="block py-2" /></label>
      <button disabled={busy || !reason.trim()} className="min-h-11 rounded-lg bg-amber-800 px-4 font-semibold text-white">Gửi phản đối và dừng tự động</button>
    </form>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {workflow.endConfirmations.length > 1 && <details><summary>Lịch sử các lần báo kết thúc</summary><ol>{workflow.endConfirmations.slice(1).map(h => <li key={h.id} className="mt-2">{formatBookingWhen(h.reportedAt)} · {h.state === 'CANCELLED' ? 'Đã hủy lần báo kết thúc' : h.state} · {h.adminNote || 'Chưa ghi nhận ghi chú'}</li>)}</ol></details>}
  </section>
}
