import { useEffect, useRef, useState } from 'react'
import EvidenceImage from './EvidenceImage'
import apiClient from '../../services/apiClient'
import { financialReviewService as service } from '../../services/financialReviewService'
import { reconciliationPayload, yearlyRulingPayload, financialReviewNote, financialReviewLabel } from '../../utils/financialReview'

const inputClass = 'block w-full rounded-lg border border-slate-300 p-2'
const buttonClass = 'rounded-lg bg-emerald-800 px-4 py-2 text-white disabled:opacity-50'
const readForm = event => Object.fromEntries(new FormData(event.currentTarget))

function AuditHistory({ rows = [] }) {
  return <details className="rounded-lg border p-3"><summary>Lịch sử xử lý ({rows.length})</summary>{rows.map(row => <p key={row.id} className="mt-2 break-words text-sm">{new Date(row.createdAt).toLocaleString('vi-VN')} · {financialReviewLabel(row.outcome)} · {financialReviewNote(row)}</p>)}</details>
}

const STALE_PROCESSING_MS = 72 * 3600 * 1000

function refundNeedsAdmin(row, now = Date.now()) {
  if (row.receiptStatus === 'REPORTED_NOT_RECEIVED') return true
  if (['PENDING_RECONCILIATION', 'UNDER_REVIEW', 'FAILED'].includes(row.status)) return true
  return row.status === 'PROCESSING' && now - Date.parse(row.createdAt) > STALE_PROCESSING_MS
}

function refundIssue(row) {
  if (row.receiptStatus === 'REPORTED_NOT_RECEIVED') return { label: 'Khách báo chưa nhận tiền', cls: 'border-rose-200 bg-rose-50 text-rose-800' }
  if (row.status === 'PENDING_RECONCILIATION') return { label: 'Chuyển nhầm — chờ xác minh thu hồi', cls: 'border-amber-200 bg-amber-50 text-amber-800' }
  if (row.status === 'UNDER_REVIEW' || row.status === 'FAILED') return { label: 'Chuyển không thành công — chờ nhà xe chuyển lại', cls: 'border-amber-200 bg-amber-50 text-amber-800' }
  return { label: 'Nhà xe chậm chuyển (quá 72 giờ)', cls: 'border-slate-200 bg-slate-50 text-slate-700' }
}

const DECISIONS = [
  { outcome: 'TRANSFER_VERIFIED', label: 'Đã chuyển đúng', hint: 'Sao kê / biên lai cho thấy tiền đã tới đúng tài khoản khách. Khoản hoàn đóng lại.', cls: 'bg-emerald-700 hover:bg-emerald-800' },
  { outcome: 'FAILED_VERIFIED', label: 'Chưa chuyển được', hint: 'Tiền chưa tới hoặc đã quay về. Khoản hoàn mở lại để nhà xe chuyển lại (khách có thể sửa tài khoản).', cls: 'bg-amber-600 hover:bg-amber-700' },
  { outcome: 'WRONG_RECIPIENT', label: 'Chuyển nhầm tài khoản', hint: 'Khoá chuyển lại cho tới khi xác minh tiền đã thu hồi — sau đó chọn "Chưa chuyển được".', cls: 'bg-rose-700 hover:bg-rose-800' },
]

export function AdminRefundReviewPanel({ onCount }) {
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState([])
  const [proof, setProof] = useState(null)
  const [notice, setNotice] = useState('')
  const pending = useRef(null)
  const reviewable = rows.filter(row => refundNeedsAdmin(row))
  async function load() {
    setLoading(true)
    try { setRows(await service.refunds()); setError('') } catch (e) { setError(e.message) } finally { setLoading(false) }
  }
  useEffect(() => {
    let live = true
    service.refunds().then(data => { if (live) setRows(data) }).catch(e => { if (live) setError(e.message) }).finally(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [])
  useEffect(() => { onCount?.(reviewable.length) }, [reviewable.length, onCount])
  async function choose(row) {
    setSelected(row); pending.current = null; setNotice('')
    try { setHistory(await service.refundHistory(row.id)) } catch (e) { setError(e.message) }
  }
  async function viewProof(row) {
    try {
      const { data } = await apiClient.get(`/payment-transactions/${row.paymentTransactionId}/refund-proof`, { responseType: 'blob' })
      setProof({ url: URL.createObjectURL(data), type: data.type })
    } catch { setError('Không tải được biên lai hoàn tiền.') }
  }
  async function decide(event, outcome) {
    event.preventDefault()
    if (busy) return
    const form = new FormData(event.currentTarget.form)
    const body = { outcome, note: String(form.get('note') || ''), transactionReference: String(form.get('transactionReference') || ''), version: selected.version }
    const signature = JSON.stringify(body)
    if (pending.current?.signature !== signature) pending.current = { signature, requestId: crypto.randomUUID() }
    setBusy(true); setError('')
    try {
      await service.reconcile(selected.id, reconciliationPayload(selected, body, pending.current.requestId))
      setNotice(`Đã lưu kết luận cho khoản hoàn ${selected.bookingCode}.`)
      setSelected(null); pending.current = null; await load()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const money = value => `${Number(value || 0).toLocaleString('vi-VN')} đ`
  return <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 className="text-base font-bold text-slate-900">Đối soát hoàn tiền</h3>
        <p className="mt-1 text-xs text-slate-600">Chỉ hiện ca cần admin: khách báo chưa nhận, chuyển không thành công, chuyển nhầm, hoặc nhà xe chậm chuyển quá 72 giờ. Khoản hoàn bình thường nhà xe tự xử lý ở trang Tài chính.</p>
      </div>
      <button className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50" type="button" onClick={load}>Làm mới</button>
    </div>
    {notice && <p role="status" className="rounded-lg bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-800">{notice}</p>}
    {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-800">{error}</p>}
    {loading ? <p role="status" className="py-6 text-center text-sm text-slate-500">Đang tải...</p>
      : !reviewable.length ? <p className="py-6 text-center text-sm text-slate-500">Không có ca hoàn tiền nào cần admin xử lý.</p>
      : <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="w-full min-w-[820px] text-xs">
          <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
            <tr><th className="px-3 py-2 text-left">Đơn</th><th className="px-3 text-left">Vấn đề</th><th className="px-3 text-right">Số tiền</th><th className="px-3 text-left">Tài khoản khách</th><th className="px-3 text-left">Lý do hoàn</th><th className="px-3 text-right">Thao tác</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {reviewable.map(row => {
              const issue = refundIssue(row)
              return <tr key={row.id} className={selected?.id === row.id ? 'bg-brand-50/40' : ''}>
                <td className="px-3 py-2.5 font-mono font-semibold">{row.bookingCode}</td>
                <td className="px-3"><span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold ${issue.cls}`}>{issue.label}</span></td>
                <td className="px-3 text-right font-mono font-bold">{money(row.amount)}</td>
                <td className="px-3">{row.recipientAccountNumber ? <>{row.recipientAccountHolder}<span className="block text-[11px] text-slate-500">{row.recipientBankName} · <span className="font-mono">{row.recipientAccountNumber}</span></span></> : <span className="text-amber-700">Chưa cung cấp</span>}</td>
                <td className="max-w-[220px] px-3 text-slate-600"><span className="line-clamp-2">{row.reason}</span></td>
                <td className="px-3 text-right">
                  <div className="flex justify-end gap-2">
                    {row.proofAvailable && <button type="button" onClick={() => viewProof(row)} className="rounded-lg border border-slate-200 px-2.5 py-1 font-semibold text-slate-700 hover:bg-slate-50">Biên lai</button>}
                    <button type="button" onClick={() => choose(row)} className="rounded-lg bg-slate-900 px-2.5 py-1 font-bold text-white hover:bg-brand-700">Kết luận</button>
                  </div>
                </td>
              </tr>
            })}
          </tbody>
        </table>
      </div>}
    {selected && <form key={selected.id} onSubmit={e => e.preventDefault()} className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs">
      <p className="text-sm font-bold text-slate-900">Kết luận khoản hoàn {selected.bookingCode} · {money(selected.amount)}</p>
      <label className="block font-semibold text-slate-700">Kết quả kiểm tra (bắt buộc)
        <textarea name="note" required maxLength={1000} rows={2} placeholder="Ví dụ: Đã đối chiếu sao kê Vietcombank ngày 10/11, tiền đã tới tài khoản khách." className={inputClass} />
      </label>
      <label className="block font-semibold text-slate-700">Mã giao dịch chuyển hoàn (tuỳ chọn)
        <input name="transactionReference" maxLength={100} defaultValue={selected.gatewayRefundRef || ''} className={inputClass} />
      </label>
      <div className="grid gap-2 sm:grid-cols-3">
        {DECISIONS.filter(d => d.outcome !== 'WRONG_RECIPIENT' || selected.status !== 'PENDING_RECONCILIATION').map(d => (
          <button key={d.outcome} type="button" disabled={busy} onClick={e => decide(e, d.outcome)}
            className={`rounded-lg px-3 py-2 text-left text-white disabled:opacity-50 ${d.cls}`}>
            <span className="block text-xs font-bold">{d.label}</span>
            <span className="mt-0.5 block text-[11px] font-normal opacity-90">{d.hint}</span>
          </button>
        ))}
      </div>
      <div className="flex items-center justify-between">
        <AuditHistory rows={history} />
        <button type="button" onClick={() => setSelected(null)} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">Đóng</button>
      </div>
    </form>}
    {proof && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4" onClick={() => { URL.revokeObjectURL(proof.url); setProof(null) }}>
      {proof.type?.startsWith('image/') ? <img src={proof.url} alt="Biên lai hoàn tiền" className="max-h-[85vh] max-w-full rounded-lg bg-white" />
        : <iframe title="Biên lai hoàn tiền" src={proof.url} className="h-[85vh] w-full max-w-3xl rounded-lg bg-white" />}
    </div>}
  </section>
}

export function AdminYearlyReviewPanel({ dispute, onChanged }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const pending = useRef(null)
  useEffect(() => { let live = true; service.yearly(dispute.bookingCode).then(d => { if (live) setData(d) }).catch(e => { if (live) setError(e.message) }); return () => { live = false } }, [dispute.bookingCode])
  const cancellation = dispute.category === 'YEARLY_CANCELLATION'
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const form = readForm(event)
    const body = { ...form, disputeId: dispute.id }
    if (cancellation) body.refundAmount = Number(form.refundAmount)
    else Object.assign(body, { version: data.workflow.version, finalKm: Number(form.finalKm), otherCharges: Number(form.otherCharges), depositDeduction: Number(form.depositDeduction), evidenceId: Number(form.evidenceId) })
    const signature = JSON.stringify(body)
    if (pending.current?.signature !== signature) pending.current = { signature, requestId: crypto.randomUUID() }
    setBusy(true); setError('')
    try {
      await (cancellation ? service.cancellation : service.settlement)(dispute.bookingCode, yearlyRulingPayload(dispute, data.workflow, form, pending.current.requestId))
      pending.current = null; await onChanged?.()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return <section className="space-y-2.5 rounded-lg border border-amber-300 p-3 text-xs">
    <h3 className="text-sm font-bold">Phân xử {cancellation ? 'hủy trước nhận xe' : 'kiểm định và quyết toán năm'}</h3>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {data && <>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <AuditHistory rows={data.history} />
        <details className="rounded-lg border p-2.5"><summary className="cursor-pointer font-semibold">Bằng chứng của hợp đồng</summary><div className="mt-2 grid gap-2 sm:grid-cols-2">{data.evidence.map(item => <figure key={item.id}><EvidenceImage src={item.imageUrl} alt={item.note || 'Bằng chứng hợp đồng'} openable className="max-h-36 max-w-full rounded-lg object-contain" /><figcaption className="text-xs">{item.note || item.originalFileName}</figcaption></figure>)}</div></details>
      </div>
      <form onSubmit={submit} className="space-y-2.5">
        <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          {cancellation ? <>
            <label className="block font-medium">Tổng tiền được hoàn theo kết luận (đ)<input name="refundAmount" type="number" min="0" step="0.01" required className={`${inputClass} mt-1`} /></label>
            <label className="block font-medium">Chứng cứ hợp đồng và đối soát<input name="evidenceReference" required maxLength={500} className={`${inputClass} mt-1`} /></label>
          </> : <>
            <label className="block font-medium">Ngày trả xe được xác minh<input name="returnedOn" type="date" required defaultValue={data.workflow.returnedOn || ''} className={`${inputClass} mt-1`} /></label>
            <label className="block font-medium">Km khi trả xe<input name="finalKm" type="number" min="0" step="1" required defaultValue={data.workflow.finalKm ?? ''} className={`${inputClass} mt-1`} /></label>
            <label className="block font-medium">Phụ phí được giữ lại (đ)<input name="otherCharges" type="number" min="0" step="0.01" required defaultValue={data.workflow.money.otherCharges} className={`${inputClass} mt-1`} /></label>
            <label className="block font-medium">Khấu trừ tiền bảo đảm (đ)<input name="depositDeduction" type="number" min="0" step="0.01" required defaultValue={data.workflow.money.depositDeduction} className={`${inputClass} mt-1`} /></label>
            <label className="block font-medium sm:col-span-2">Bằng chứng đã kiểm tra<select name="evidenceId" required className={`${inputClass} mt-1`}><option value="">Chọn bằng chứng của hợp đồng</option>{data.evidence.map(e => <option key={e.id} value={e.id}>{e.note || e.originalFileName || 'Bằng chứng'} · {new Date(e.createdAt).toLocaleDateString('vi-VN')}</option>)}</select></label>
          </>}
          <label className="block font-medium sm:col-span-2">Kết luận phân xử<textarea name="note" rows="2" required maxLength={1000} className={`${inputClass} mt-1`} /></label>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-amber-200/70 pt-2">
          <p className="text-xs text-slate-600">Kết luận sẽ tính lại tiền phải thu/hoàn; chưa xác nhận tiền đã chuyển.</p>
          <button disabled={busy} className={buttonClass}>{busy ? 'Đang xử lý…' : 'Áp dụng phán quyết'}</button>
        </div>
      </form>
    </>}
  </section>
}
