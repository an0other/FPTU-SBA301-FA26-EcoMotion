import { useEffect, useRef, useState } from 'react'
import { financialReviewService as service } from '../../services/financialReviewService'
import EvidenceImage from './EvidenceImage'

const input = 'mt-1 block w-full rounded-lg border border-slate-300 p-2'
export default function AdminYearlyExceptionPanel({ dispute, onChanged }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const requestRef = useRef(null)
  const refund = dispute.category === 'YEARLY_REFUND'
  const [incidentId, setIncidentId] = useState('')
  const [responsibility, setResponsibility] = useState('')
  const incident = data?.workflow.incidents.find(i => String(i.id) === incidentId) || data?.workflow.incidents.find(i => i.status !== 'CLOSED')
  const awaitingTransfer = !!incident?.transferEvidenceId
  const ownerResponsible = (responsibility || incident?.proposedCategory || incident?.category) === 'OWNER_MAINTENANCE'
  const activeReplacement = data?.workflow.replacements.some(r => !['COMPLETED', 'CANCELLED'].includes(r.status))
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
    else { body.incidentId = Number(fields.incidentId); body.approvedAmount = Number(fields.approvedAmount); body.responsibility = responsibility || incident?.proposedCategory || incident?.category }
    const signature = JSON.stringify(body)
    if (requestRef.current?.signature !== signature) requestRef.current = { signature, payload: { ...body, requestId: crypto.randomUUID() } }
    setBusy(true); setError('')
    try {
      await (refund ? service.refundRuling : service.incidentRuling)(dispute.bookingCode, requestRef.current.payload)
      requestRef.current = null
      await onChanged?.()
    } catch (err) {
      setError(err.message)
      try { setData(await service.yearly(dispute.bookingCode)) } catch { }
    } finally { setBusy(false) }
  }
  return <section className="space-y-4"><h3 className="text-lg font-bold">{refund ? 'Đối soát tiền hoàn gói năm' : 'Phân xử report sự cố gói năm'}</h3>{error && <p role="alert" className="text-red-700">{error}</p>}{data && <>
    <details><summary className="cursor-pointer font-semibold">Bằng chứng của hợp đồng</summary><div className="mt-3 grid gap-3 sm:grid-cols-2">{data.evidence.map(item => <figure key={item.id}><EvidenceImage src={item.imageUrl} alt={item.note || 'Bằng chứng hợp đồng'} openable className="max-h-48 max-w-full object-contain" /><figcaption className="text-xs">#{item.id} · {item.note}</figcaption></figure>)}</div></details>
    {!refund && activeReplacement && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm">Đang có đề nghị đổi xe chưa kết thúc. Renter hoặc provider cần hủy đề nghị tại mục Sự cố &amp; đổi xe trước khi Admin kết luận trách nhiệm; hiện chỉ được lưu tiếp tục xem xét.</p>}
    <form className="space-y-4" onSubmit={submit}>
      {refund ? <><p className="rounded-xl bg-amber-50 p-3 text-sm">Chỉ cho chuyển lại khi đã xác minh giao dịch cũ không còn nguy cơ trả trùng. Chưa rõ kết quả hoặc chuyển nhầm chưa thu hồi: chọn tiếp tục đối soát.</p><label className="block text-sm">Kết luận<select className={input} name="outcome" required><option value="HOLD">Chưa đủ bằng chứng, tiếp tục đối soát</option><option value="FAILED_VERIFIED">Đã xác minh chuyển thất bại / tiền đã quay về</option><option value="PARTIAL_RECEIVED">Đã xác minh nhận một phần, phần còn lại không còn treo</option><option value="CONFIRMED_RECEIVED">Đã xác minh nhận đủ tiền đúng người nhận</option></select></label><label className="block text-sm">Tổng tiền renter thực nhận đã xác minh, cộng dồn (đ)<input name="receivedAmount" className={input} type="number" min={data.workflow.refundVerifiedReceived || 0} max={Number(data.workflow.money?.rentRefund || 0) + Number(data.workflow.money?.depositRefund || 0)} step="0.01" required defaultValue={data.workflow.refundVerifiedReceived || 0} /></label></> : <><label className="block text-sm">Report cần xử lý<select name="incidentId" className={input} required value={incident?.id || ''} onChange={e => { setIncidentId(e.target.value); setResponsibility('') }}>{data.workflow.incidents.filter(item => item.status !== 'CLOSED').map(item => <option key={item.id} value={item.id}>#{item.id} · {item.description} · {item.status}</option>)}</select></label><p className="text-sm">Renter báo: {incident?.reportedCategory || incident?.category} · Provider đề xuất: {incident?.proposedCategory || 'Chưa đề xuất'}. Kết luận mới và căn cứ được lưu vào lịch sử.</p>{!awaitingTransfer && <label className="block text-sm">Trách nhiệm Admin xác minh<select name="responsibility" className={input} value={responsibility || incident?.proposedCategory || incident?.category} onChange={e => setResponsibility(e.target.value)}><option value="OWNER_MAINTENANCE">Hao mòn / trách nhiệm provider</option><option value="RENTER_DAMAGE">Trách nhiệm renter</option></select></label>}<label className="block text-sm">Kết luận<select key={String(incident?.id) + awaitingTransfer + activeReplacement + ownerResponsible} name="outcome" className={input} required><option value="HOLD">Chưa đủ bằng chứng, tiếp tục xem xét</option>{awaitingTransfer ? <><option value="TRANSFER_VERIFIED">Đã xác minh renter nhận khoản hoàn ứng</option><option value="FAILED_TRANSFER">Đã xác minh hoàn ứng thất bại / tiền đã quay về</option></> : !activeReplacement && <><option value="FIXED">Đã xử lý, đóng report</option>{!ownerResponsible && <option value="RENTER_PAID">Chi phí thuộc renter, đã xử lý riêng</option>}{ownerResponsible && <>{Number(incident?.invoiceAmount) > 0 && <option value="REIMBURSE">Nhà xe phải hoàn ứng</option>}<option value="END_PROVIDER">Kết thúc do phía nhà xe không thể tiếp tục cung cấp</option></>}</>}</select></label><label className="block text-sm">Số tiền hoàn ứng được duyệt, chỉ dùng cho hoàn ứng (đ)<input name="approvedAmount" className={input} type="number" min="0" max={awaitingTransfer ? 0 : Number(incident?.invoiceAmount || 0)} step="1" required defaultValue="0" /></label></>}
      <label className="block text-sm">Kết luận và hướng xử lý<textarea name="note" className={input} required maxLength={1000} /></label><label className="block text-sm">Tham chiếu bằng chứng đã kiểm tra<input name="evidenceReference" className={input} required maxLength={500} /></label><label className="flex gap-2 text-sm"><input type="checkbox" required />Tôi đã kiểm tra bằng chứng. Nếu cho chuyển lại, giao dịch cũ đã thất bại hoặc phần còn thiếu không còn treo; không trả trùng tiền.</label><button className="rounded-xl bg-emerald-700 px-4 py-2 font-semibold text-white disabled:opacity-50" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu kết luận'}</button>
    </form></>}</section>
}
