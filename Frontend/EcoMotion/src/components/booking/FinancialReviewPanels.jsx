import YearlyChargesEditor from '../yearly-rental/YearlyChargesEditor'
import { useEffect, useRef, useState } from 'react'
import EvidenceImage from './EvidenceImage'
import { financialReviewService as service } from '../../services/financialReviewService'
import { reconciliationPayload, yearlyRulingPayload, financialReviewNote, financialReviewLabel } from '../../utils/financialReview'

const inputClass = 'block w-full rounded-lg border border-slate-300 p-2'
const buttonClass = 'rounded-lg bg-emerald-800 px-4 py-2 text-white disabled:opacity-50'
const outcomes = [['HOLD', 'Chưa đủ bằng chứng, tiếp tục đối soát'], ['TRANSFER_VERIFIED', 'Có chứng cứ chuyển thành công đúng người nhận'], ['FAILED_VERIFIED', 'Đã xác minh không chuyển được hoặc tiền đã quay về'], ['WRONG_RECIPIENT', 'Chuyển nhầm, chưa xác minh thu hồi tiền']]
const readForm = event => Object.fromEntries(new FormData(event.currentTarget))

function AuditHistory({ rows = [] }) {
  return <details className="rounded-lg border p-3"><summary>Lịch sử xử lý ({rows.length})</summary>{rows.map(row => <p key={row.id} className="mt-2 break-words text-sm">{new Date(row.createdAt).toLocaleString('vi-VN')} · {financialReviewLabel(row.outcome)} · {financialReviewNote(row)}</p>)}</details>
}

export function AdminRefundReviewPanel() {
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState([])
  const pending = useRef(null)
  async function load() {
    try { setRows(await service.refunds()); setError('') } catch (e) { setError(e.message) }
  }
  useEffect(() => { let live = true; service.refunds().then(data => { if (live) setRows(data) }).catch(e => { if (live) setError(e.message) }); return () => { live = false } }, [])
  async function choose(row) {
    setSelected(row); pending.current = null
    try { setHistory(await service.refundHistory(row.id)) } catch (e) { setError(e.message) }
  }
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const body = { ...readForm(event), version: selected.version }
    const signature = JSON.stringify(body)
    if (pending.current?.signature !== signature) pending.current = { signature, requestId: crypto.randomUUID() }
    setBusy(true); setError('')
    try {
      await service.reconcile(selected.id, reconciliationPayload(selected, body, pending.current.requestId))
      setSelected(null); pending.current = null; await load()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const reviewable = rows.filter(r => ['PENDING_RECONCILIATION', 'UNDER_REVIEW', 'PROCESSING', 'FAILED'].includes(r.status) || r.receiptStatus === 'REPORTED_NOT_RECEIVED')
  return <section className="space-y-3 rounded-xl border bg-white p-4">
    <h3 className="font-bold">Đối soát hoàn tiền và báo chưa nhận</h3>
    <p className="text-sm">Chỉ kết luận khi có chứng cứ. Chuyển nhầm hoặc chưa rõ kết quả không được gửi hoàn thêm.</p>
    <button className={buttonClass} type="button" onClick={load}>Tải lại hồ sơ hoàn</button>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {!reviewable.length && <p>Không có hồ sơ cần đối soát.</p>}
    {reviewable.map(row => <button key={row.id} type="button" className="block w-full rounded-lg border p-3 text-left" onClick={() => choose(row)}>{row.bookingCode} · {Number(row.amount).toLocaleString('vi-VN')} đ · {row.status} {row.receiptStatus === 'REPORTED_NOT_RECEIVED' && '· Khách báo chưa nhận'}</button>)}
    {selected && <form key={selected.id} onSubmit={submit} className="space-y-3 rounded-lg bg-slate-50 p-3">
      <p className="font-semibold">Hồ sơ hoàn #{selected.id} · {selected.bookingCode}</p>
      <label className="block">Kết luận<select name="outcome" required className={inputClass}>{outcomes.map(([id,label]) => <option value={id} key={id}>{label}</option>)}</select></label>
      <label className="block">Lý do và kết quả kiểm tra<textarea name="note" required maxLength={1000} className={inputClass} /></label>
      <label className="block">Tham chiếu chứng cứ ngân hàng/cổng thanh toán<input name="evidenceReference" required maxLength={500} className={inputClass} /></label>
      <label className="block">Mã giao dịch được xác minh<input name="transactionReference" required maxLength={100} defaultValue={selected.gatewayRefundRef || ''} className={inputClass} /></label>
      <button disabled={busy} className={buttonClass}>{busy ? 'Đang lưu…' : 'Lưu kết luận đối soát'}</button>
      <AuditHistory rows={history} />
    </form>}
  </section>
}

export function AdminYearlyReviewPanel({ dispute, onChanged }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const pending = useRef(null)
  const [charges, setCharges] = useState([])
  useEffect(() => { let live = true; service.yearly(dispute.bookingCode).then(d => { if (live) { setData(d); setCharges(d.workflow.settlementCharges || []) } }).catch(e => { if (live) setError(e.message) }); return () => { live = false } }, [dispute.bookingCode])
  const cancellation = dispute.category === 'YEARLY_CANCELLATION'
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const form = readForm(event)
    form.charges = charges.map(c => ({ ...c, amount: Number(c.amount), evidenceId: Number(c.evidenceId) }))
    form.otherCharges = form.charges.reduce((sum, c) => sum + c.amount, 0)
    const body = { ...form, disputeId: dispute.id }
    if (cancellation) body.refundAmount = Number(form.refundAmount)
    else Object.assign(body, { version: data.workflow.version, finalKm: Number(form.finalKm), otherCharges: Number(form.otherCharges), depositDeduction: Number(form.depositDeduction), evidenceId: Number(form.evidenceId) })
    const signature = JSON.stringify(body)
    if (pending.current?.signature !== signature) pending.current = { signature, requestId: crypto.randomUUID() }
    setBusy(true); setError('')
    try {
      await (cancellation ? service.cancellation : service.settlement)(dispute.bookingCode, yearlyRulingPayload(dispute, data.workflow, form, pending.current.requestId))
      pending.current = null; await onChanged?.()
    } catch (e) { setError(e.message); try { setData(await service.yearly(dispute.bookingCode)) } catch { } } finally { setBusy(false) }
  }
  return <section className="space-y-3 rounded-lg border border-amber-300 p-4">
    <h3 className="font-bold">Phân xử {cancellation ? 'hủy trước nhận xe' : 'kiểm định và quyết toán năm'}</h3>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {data && <><AuditHistory rows={data.history} /><details className="rounded-lg border p-3"><summary>Bằng chứng của hợp đồng</summary><div className="grid gap-3 sm:grid-cols-2">{data.evidence.map(item => <figure key={item.id}><EvidenceImage src={item.imageUrl} alt={item.note || 'Bằng chứng hợp đồng'} openable className="max-h-48 max-w-full rounded-lg object-contain" /><figcaption className="text-sm">{item.note || item.originalFileName}</figcaption></figure>)}</div></details><form onSubmit={submit} className="space-y-3">
      {cancellation ? <>
        <label className="block">Tổng tiền được hoàn theo kết luận (đ)<input name="refundAmount" type="number" min="0" step="0.01" required className={inputClass} /></label>
        <label className="block">Chứng cứ hợp đồng và đối soát<input name="evidenceReference" required maxLength={500} className={inputClass} /></label>
      </> : <>
        <label className="block">Ngày trả xe được xác minh<input name="returnedOn" type="date" required defaultValue={data.workflow.returnedOn || ''} className={inputClass} /></label>
        <label className="block">Km khi trả xe<input name="finalKm" type="number" min="0" step="1" required defaultValue={data.workflow.finalKm ?? ''} className={inputClass} /></label>
        <YearlyChargesEditor rows={charges} onChange={setCharges} evidence={data.evidence} incidents={data.workflow.incidents} busy={busy} />
        <label className="block">Khấu trừ tiền bảo đảm (đ)<input name="depositDeduction" type="number" min="0" step="0.01" required defaultValue={data.workflow.money.depositDeduction} className={inputClass} /></label>
        <label className="block">Bằng chứng đã kiểm tra<select name="evidenceId" required className={inputClass}><option value="">Chọn bằng chứng của hợp đồng</option>{data.evidence.map(e => <option key={e.id} value={e.id}>{e.note || e.originalFileName || 'Bằng chứng'} · {new Date(e.createdAt).toLocaleDateString('vi-VN')}</option>)}</select></label>
      </>}
      <label className="block">Kết luận phân xử<textarea name="note" required maxLength={1000} className={inputClass} /></label>
      <p className="text-sm">Kết luận sẽ tính lại tiền phải thu/hoàn; chưa xác nhận tiền đã chuyển.</p>
      <button disabled={busy} className={buttonClass}>{busy ? 'Đang xử lý…' : 'Áp dụng phán quyết'}</button>
    </form></>}
  </section>
}
