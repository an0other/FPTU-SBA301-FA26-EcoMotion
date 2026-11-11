import { useEffect, useRef, useState } from 'react'
import { financialReviewService as service } from '../../services/financialReviewService'
import EvidenceImage from './EvidenceImage'

const input = 'mt-1 block w-full rounded-lg border border-slate-300 p-2 text-xs'
export default function AdminYearlyExceptionPanel({ dispute, onChanged }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const requestRef = useRef(null)
  const refund = dispute.category === 'YEARLY_REFUND'
  useEffect(() => {
    let alive = true
    service.yearly(dispute.bookingCode).then(result => { if (alive) setData(result) }).catch(err => { if (alive) setError(err.message) })
    return () => { alive = false }
  }, [dispute.bookingCode])
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const fields = Object.fromEntries(new FormData(event.currentTarget))
    const body = { ...fields, version: data.workflow.version, disputeId: dispute.id }
    if (refund) body.receivedAmount = Number(fields.receivedAmount)
    else { body.incidentId = Number(fields.incidentId); body.approvedAmount = Number(fields.approvedAmount) }
    const signature = JSON.stringify({ ...body, version: undefined })
    if (requestRef.current?.signature !== signature) requestRef.current = { signature, payload: { ...body, requestId: crypto.randomUUID() } }
    setBusy(true); setError('')
    try {
      await (refund ? service.refundRuling : service.incidentRuling)(dispute.bookingCode, requestRef.current.payload)
      requestRef.current = null
      await onChanged?.()
    } catch (err) {
      setError(err.message)
      try { setData(await service.yearly(dispute.bookingCode)) } catch { /* ignore reload error */ }
    } finally { setBusy(false) }
  }
  return <section className="space-y-3 text-xs"><h3 className="text-sm font-bold">{refund ? 'Đối soát tiền hoàn gói năm' : 'Phân xử report sự cố gói năm'}</h3>{error && <p role="alert" className="text-red-700">{error}</p>}{data && <>
    <details className="rounded-lg border border-slate-200 p-2.5"><summary className="cursor-pointer font-semibold">Bằng chứng của hợp đồng</summary><div className="mt-2 grid gap-2.5 sm:grid-cols-2">{data.evidence.map(item => <figure key={item.id}><EvidenceImage src={item.imageUrl} alt={item.note || 'Bằng chứng hợp đồng'} openable className="max-h-36 max-w-full object-contain" /><figcaption className="text-xs">#{item.id} · {item.note}</figcaption></figure>)}</div></details>
    <form className="space-y-3" onSubmit={submit}>
      {refund ? (
        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          <p className="rounded-xl bg-amber-50 p-2.5 text-xs sm:col-span-2">Chỉ cho chuyển lại khi đã xác minh giao dịch cũ không còn nguy cơ trả trùng. Chưa rõ kết quả hoặc chuyển nhầm chưa thu hồi: chọn tiếp tục đối soát.</p>
          <label className="block font-medium">Kết luận<select className={input} name="outcome" required><option value="HOLD">Chưa đủ bằng chứng, tiếp tục đối soát</option><option value="FAILED_VERIFIED">Đã xác minh chuyển thất bại / tiền đã quay về</option><option value="PARTIAL_RECEIVED">Đã xác minh nhận một phần, phần còn lại không còn treo</option><option value="CONFIRMED_RECEIVED">Đã xác minh nhận đủ tiền đúng người nhận</option></select></label>
          <label className="block font-medium">Tổng tiền renter thực nhận đã xác minh, cộng dồn (đ)<input name="receivedAmount" className={input} type="number" min={data.workflow.refundVerifiedReceived || 0} max={Number(data.workflow.money.rentRefund) + Number(data.workflow.money.depositRefund)} step="0.01" required defaultValue={data.workflow.refundVerifiedReceived || 0} /></label>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          <label className="block font-medium sm:col-span-2">Report cần xử lý<select name="incidentId" className={input} required>{data.workflow.incidents.filter(item => item.status !== 'CLOSED').map(item => <option key={item.id} value={item.id}>#{item.id} · {item.description} · {item.status}</option>)}</select></label>
          <label className="block font-medium">Kết luận<select name="outcome" className={input} required><option value="HOLD">Chưa đủ bằng chứng, tiếp tục xem xét</option><option value="TRANSFER_VERIFIED">Đã xác minh renter nhận khoản hoàn ứng</option><option value="FAILED_TRANSFER">Đã xác minh hoàn ứng thất bại / tiền đã quay về</option><option value="FIXED">Đã xử lý, đóng report</option><option value="RENTER_PAID">Chi phí thuộc renter, đã xử lý riêng</option><option value="REIMBURSE">Nhà xe phải hoàn ứng</option><option value="END_PROVIDER">Kết thúc do phía nhà xe không thể tiếp tục cung cấp</option></select></label>
          <label className="block font-medium">Số tiền hoàn ứng được duyệt (đ)<input name="approvedAmount" className={input} type="number" min="0" step="0.01" required defaultValue="0" /></label>
        </div>
      )}
      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        <label className="block font-medium">Kết luận và hướng xử lý<textarea name="note" rows="2" className={input} required maxLength={1000} /></label>
        <label className="block font-medium">Tham chiếu bằng chứng đã kiểm tra<input name="evidenceReference" className={input} required maxLength={500} /></label>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-2.5">
        <label className="flex flex-1 gap-2 text-xs text-slate-700"><input type="checkbox" required className="mt-0.5 shrink-0" /><span>Tôi đã kiểm tra bằng chứng. Nếu cho chuyển lại, giao dịch cũ đã thất bại hoặc phần còn thiếu không còn treo; không trả trùng tiền.</span></label>
        <button className="shrink-0 rounded-xl bg-emerald-700 px-4 py-2 font-semibold text-white disabled:opacity-50" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu kết luận'}</button>
      </div>
    </form></>}</section>
}
