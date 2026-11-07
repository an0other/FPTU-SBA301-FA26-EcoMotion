import EvidenceImage from '../booking/EvidenceImage'
import { useCallback, useEffect, useRef, useState } from 'react'
import { yearlyWorkflowService as service } from '../../services/yearlyWorkflowService'
import { readErrorMessage } from '../../services/apiClient'
import ActionForm from './YearlyActionForm'
import { yearlyDate, yearlyMoney, yearlyToday, yearlyButtonClass } from '../../utils/yearlyRental'
import YearlyContractPanel from './YearlyContractPanel'
import YearlyPaymentsPanel from './YearlyPaymentsPanel'
import YearlyIncidentPanel from './YearlyIncidentPanel'

const stages = { TERMS_DRAFT: 'Chờ chốt điều khoản', TERMS_ACCEPTED: 'Chuẩn bị hợp đồng và bàn giao', ACTIVE: 'Đang thuê', RETURN_REQUESTED: 'Đã yêu cầu trả xe', RETURN_INSPECTED: 'Chờ xác nhận kiểm định', RETURNED: 'Đã nhận lại xe', SETTLEMENT_PROPOSED: 'Chờ đồng ý quyết toán', SETTLEMENT_ACCEPTED: 'Đã chốt quyết toán', REFUND_TRANSFER_RECORDED: 'Chờ xác nhận tiền hoàn', REFUND_RECEIVED: 'Đã nhận tiền hoàn', COMPLETED: 'Hoàn tất' }
const moneyLabels = { rentDue: 'Tiền thuê phải trả', earlyFee: 'Phí kết thúc sớm', lateFee: 'Phí trả trễ', excessKmFee: 'Phí vượt km', otherCharges: 'Phụ phí khác', rentPaid: 'Tiền thuê đã nhận', depositHeld: 'Tiền bảo đảm đang giữ', depositDeduction: 'Khấu trừ tiền bảo đảm', balanceDue: 'Cần thanh toán bổ sung', rentRefund: 'Tiền thuê cần hoàn', depositRefund: 'Tiền bảo đảm cần hoàn' }
const modes = [['EARLY', 'Trả sớm, chịu phí theo hợp đồng'], ['STANDARD', 'Trả theo hạn hợp đồng'], ['FULL_CONTRACT', 'Trả xe và thanh toán đủ tiền thuê cả năm']]
const n = (d, k) => Number(d.get(k))
const fields = {
  terms: [{ name: 'annualKm', label: 'Tổng km cho phép trong năm', type: 'number' }, { name: 'excessKmRate', label: 'Phí mỗi km vượt (đ)', type: 'number' }, { name: 'graceDays', label: 'Số ngày ân hạn trả xe', type: 'number', value: 0 }, { name: 'lateDailyRate', label: 'Phí trả trễ mỗi ngày (đ)', type: 'number' }],
  transfer: [{ name: 'bankReference', label: 'Mã giao dịch hoàn tiền' }, { name: 'file', label: 'Ảnh biên lai', type: 'file' }],
}
function WorkflowAction({ title, action, body, version, busy, run, act, ...extra }) {
  async function submit(data) {
    return run(async () => act(action, { version, ...await body(data) }))
  }
  return <ActionForm title={title} busy={busy} confirm onSubmit={submit} {...extra} />
}
export default function YearlyWorkflowPanel({ role = 'renter', bookingCode, bookingStatus, onChanged }) {
  const [data, setData] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [preview, setPreview] = useState(null)
  const lock = useRef(false)
  const epoch = useRef(0)
  const [uploads] = useState(() => new WeakMap())
  const changed = useRef(onChanged)
  useEffect(() => { changed.current = onChanged }, [onChanged])
  const reload = useCallback(async () => {
    const request = ++epoch.current
    const result = await service.load(role, bookingCode)
    if (request === epoch.current) setData(result)
  }, [role, bookingCode])
  useEffect(() => {
    reload().catch(e => setError(readErrorMessage(e)))
    return () => { epoch.current += 1 }
  }, [reload])
  async function run(operation, message = 'Đã cập nhật thông tin.', refresh = true) {
    if (lock.current) return false
    lock.current = true; setBusy(true); setError(''); setNotice('')
    try {
      await operation()
      if (refresh) { await reload(); await changed.current?.() }
      setNotice(message)
      return true
    } catch (e) {
      setError(readErrorMessage(e) || 'Chưa thể thực hiện. Vui lòng tải lại trạng thái và thử lại.')
      return false
    } finally { lock.current = false; setBusy(false) }
  }
  async function upload(file) {
    if (!uploads.has(file)) {
      const result = await service.upload(bookingCode, file)
      uploads.set(file, result)
    }
    return uploads.get(file)
  }
  const act = (action, body) => service.action(role, bookingCode, action, body)
  const v = data?.workflow
  const h = data?.handover
  const provider = role === 'provider'
  const fileId = async d => (await upload(d.get('file'))).id
  const evidence = id => {
    const item = data?.evidence.find(e => e.id === id)
    return item?.imageUrl ? <EvidenceImage src={item.imageUrl} alt="Ảnh bằng chứng" openable className="max-h-48 max-w-full rounded-lg object-contain" /> : null
  }
  const form = (title, action, body, extra = {}) => <WorkflowAction key={title + v.version} title={title} action={action} body={body} version={v.version} busy={busy} run={run} act={act} {...extra} />
  const money = value => <dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{Object.entries(moneyLabels).filter(([k]) => value?.[k] != null).map(([k, label]) => <div key={k}><dt className="text-slate-500">{label}</dt><dd className="font-semibold">{yearlyMoney(value[k])}</dd></div>)}</dl>
  const paidInitial = data?.payments.some(p => p.purpose === 'SECURITY_DEPOSIT' && p.status === 'CONFIRMED') && data?.payments.some(p => p.installmentNumber === 1 && p.status === 'CONFIRMED')
  const balancePaid = !v?.money?.balanceDue || data?.payments.some(p => p.purpose === 'ADJUSTMENT' && p.status === 'CONFIRMED')
  return <div className="my-5 space-y-5">
    <div className="flex items-center justify-between gap-3"><h2 className="text-xl font-bold">Hợp đồng thuê năm</h2><button disabled={busy} className={yearlyButtonClass} onClick={() => run(reload, 'Đã tải trạng thái mới nhất.', false)}>Tải lại</button></div>
    {error && <p role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg bg-emerald-50 p-4 text-sm text-emerald-900">{notice}</p>}
    {!v ? <p className="text-sm text-slate-500">{error ? 'Chưa tải được hợp đồng.' : 'Đang tải hợp đồng…'}</p> : <>
      <section className="space-y-4 rounded-xl border bg-white p-5">
        <p className="font-semibold text-emerald-800">{stages[v.stage] || v.stage}</p>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div><dt>Tổng tiền thuê 12 tháng</dt><dd className="font-bold">{yearlyMoney(v.contract.totalRent)}</dd></div>
          <div><dt>Tiền bảo đảm riêng</dt><dd className="font-bold">{yearlyMoney(v.contract.securityDeposit)}</dd></div>
          <div><dt>Ngày nhận {v.contract.actualStart ? 'thực tế' : 'dự kiến'}</dt><dd>{yearlyDate(v.contract.actualStart || v.contract.plannedStart)}</dd></div>
          <div><dt>Ngày trả theo hợp đồng</dt><dd>{yearlyDate(v.contract.dueOn)}</dd></div>
          <div><dt>Mức làm tròn phí theo hợp đồng</dt><dd>{yearlyMoney(v.roundingUnit)}</dd></div>
          <div><dt>Km cả năm / phí vượt mỗi km</dt><dd>{v.annualKm ?? 'Chưa chốt'} km · {yearlyMoney(v.excessKmRate)}</dd></div>
          <div><dt>Ân hạn trả xe / phí trễ mỗi ngày</dt><dd>{v.graceDays ?? 0} ngày · {yearlyMoney(v.lateDailyRate)}</dd></div>
        </dl>
        <p className="text-sm text-slate-600">Thanh toán trước từng tháng. Khi tự nguyện trả sớm, giữ nguyên tiền tháng đã bắt đầu; phí kết thúc tối đa một tháng trung bình làm tròn theo hợp đồng và không vượt tiền thuê còn lại. Tiền bảo đảm được quyết toán riêng.</p>
        {provider && v.stage === 'TERMS_DRAFT' && bookingStatus === 'ACCEPTED' && form('Chốt điều khoản cho người thuê', 'terms', d => Object.fromEntries(fields.terms.map(f => [f.name, n(d, f.name)])), { fields: fields.terms })}
        {!provider && v.stage === 'TERMS_DRAFT' && v.annualKm != null && form('Đồng ý điều khoản nhà cung cấp đã đề xuất', 'terms/accept', () => ({}))}
      </section>
      {!provider && v.termsAccepted && data.contract && <YearlyContractPanel contract={data.contract} onUploaded={() => run(async () => {}, 'Đã lưu hợp đồng.')} />}
      <YearlyPaymentsPanel role={role} rows={data.payments} busy={busy} run={run} />
      {v.termsAccepted && <section className="space-y-4 rounded-xl border bg-white p-5">
        <h3 className="text-lg font-bold">Giao và nhận xe</h3>
        <p className="text-sm">{({ NOT_DISPATCHED: 'Chưa giao xe', DISPATCHED: 'Đang giao xe', ARRIVED: 'Xe đã đến, chờ người thuê nhận', RECEIVED: 'Người thuê đã nhận xe' })[h.stage]}</p>
        {h.initialKm != null && <p className="text-sm">Công-tơ-mét khi giao: {h.initialKm} km</p>}
        {h.evidenceUrl && <EvidenceImage src={h.evidenceUrl} alt="Ảnh giao xe" openable className="max-h-48 max-w-full rounded-lg object-contain" />}
        {provider && paidInitial && h.stage === 'NOT_DISPATCHED' && form('Xác nhận bắt đầu giao xe', 'handover/dispatch', () => ({}))}
        {provider && ['DISPATCHED', 'ARRIVED'].includes(h.stage) && form('Xe đã đến điểm giao', 'handover/arrive', async d => ({ initialKm: n(d, 'initialKm'), evidenceId: await fileId(d) }), { fields: [{ name: 'initialKm', label: 'Công-tơ-mét khi giao (km)', type: 'number' }, { name: 'file', label: 'Ảnh bàn giao và công-tơ-mét', type: 'file' }] })}
        {!provider && h.stage === 'ARRIVED' && form('Tôi đã kiểm tra và nhận xe', 'handover/receive', () => ({}))}
      </section>}
      <YearlyIncidentPanel role={role} view={v} busy={busy} run={run} act={act} upload={upload} evidence={evidence} />
      {!provider && v.stage === 'ACTIVE' && <section className="space-y-4">
        <ActionForm title="Ước tính tiền khi trả xe" busy={busy} fields={[{ name: 'mode', label: 'Hình thức trả', type: 'select', options: modes }, { name: 'returnedOn', label: 'Ngày dự kiến trả', type: 'date', value: yearlyToday() }]} onSubmit={d => run(async () => setPreview(await act('returns/preview', { mode: d.get('mode'), returnedOn: d.get('returnedOn') })), '', false)} />
        {preview && <><p className="text-sm text-amber-800">Ước tính chưa gồm kiểm định, km vượt và phụ phí. Số cuối cùng được hai bên chốt khi nhận lại xe.</p>{money(preview.money)}</>}
        {form('Gửi yêu cầu trả xe', 'returns', d => ({ mode: d.get('mode') }), { fields: [{ name: 'mode', label: 'Hình thức trả', type: 'select', options: modes }] })}
      </section>}
      {provider && ['RETURN_REQUESTED', 'RETURN_INSPECTED'].includes(v.stage) && form('Kiểm định khi nhận lại xe', 'returns/inspection', async d => ({ returnedOn: d.get('returnedOn'), finalKm: n(d, 'finalKm'), evidenceId: await fileId(d) }), { fields: [{ name: 'returnedOn', label: 'Ngày nhận lại xe thực tế', type: 'date', value: yearlyToday() }, { name: 'finalKm', label: 'Công-tơ-mét khi trả (km)', type: 'number' }, { name: 'file', label: 'Ảnh kiểm định và công-tơ-mét', type: 'file' }] })}
      {v.returnedOn && <section className="space-y-2 rounded-xl border bg-white p-5"><p>Nhận lại xe: {yearlyDate(v.returnedOn)} · Công-tơ-mét: {v.finalKm} km · Tổng đã đi: {v.totalKm} km</p>{evidence(v.returnEvidenceId)}</section>}
      {!provider && v.stage === 'RETURN_INSPECTED' && form('Xác nhận ngày trả, tình trạng xe và số km', 'returns/confirm', () => ({}))}
      {provider && ['RETURNED', 'SETTLEMENT_PROPOSED'].includes(v.stage) && form('Lập quyết toán', 'settlement', async d => ({ otherCharges: n(d, 'otherCharges'), depositDeduction: n(d, 'depositDeduction'), note: d.get('note'), evidenceId: d.get('file')?.size ? await fileId(d) : null }), { fields: [{ name: 'otherCharges', label: 'Phụ phí được hai bên thống nhất (đ)', type: 'number', value: 0 }, { name: 'depositDeduction', label: 'Số trừ vào tiền bảo đảm (đ)', type: 'number', value: 0 }, { name: 'note', label: 'Chi tiết quyết toán', type: 'textarea' }, { name: 'file', label: 'Bằng chứng phụ phí (bắt buộc nếu có phụ phí)', type: 'file', required: false }] })}
      {v.money && <section className="space-y-3 rounded-xl border bg-white p-5"><h3 className="text-lg font-bold">Đối soát tiền</h3>{money(v.money)}{v.money.note && <p className="whitespace-pre-wrap text-sm">{v.money.note}</p>}{evidence(v.settlementEvidenceId)}</section>}
      {!provider && v.stage === 'SETTLEMENT_PROPOSED' && form('Đồng ý quyết toán', 'settlement/accept', () => ({}))}
      {provider && v.stage === 'SETTLEMENT_ACCEPTED' && balancePaid && Number(v.money?.rentRefund || 0) + Number(v.money?.depositRefund || 0) > 0 && form('Đã chuyển tiền hoàn cho người thuê', 'refund/transfer', async d => ({ bankReference: d.get('bankReference'), evidenceId: await fileId(d) }), { fields: fields.transfer })}
      {v.refundReference && <p className="text-sm">Giao dịch hoàn: {v.refundReference} {evidence(v.refundEvidenceId)}</p>}
      {!provider && v.stage === 'REFUND_TRANSFER_RECORDED' && form('Tôi đã nhận đủ tiền hoàn', 'refund/confirm', () => ({}))}
      {provider && ['SETTLEMENT_ACCEPTED', 'REFUND_RECEIVED'].includes(v.stage) && balancePaid && (v.stage === 'REFUND_RECEIVED' || Number(v.money?.rentRefund || 0) + Number(v.money?.depositRefund || 0) === 0) && !(v.incidents || []).some(i => i.status !== 'CLOSED') && form('Hoàn tất hợp đồng', 'complete', () => ({}))}
      {provider && ['RETURNED', 'SETTLEMENT_PROPOSED', 'SETTLEMENT_ACCEPTED', 'REFUND_TRANSFER_RECORDED', 'REFUND_RECEIVED', 'COMPLETED'].includes(v.stage) && form('Mở lại lịch cho thuê xe', 'reopen', d => ({ availableOn: d.get('availableOn'), vehicleId: n(d, 'vehicleId') }), { fields: [{ name: 'vehicleId', label: 'Xe đã kiểm định', type: 'select', options: [...new Set([v.contract.currentVehicleId, ...(v.replacements || []).filter(r => r.status === 'COMPLETED').map(r => r.oldVehicleId)])].map(id => [id, 'Xe mã ' + id]) }, { name: 'availableOn', label: 'Ngày bắt đầu nhận lịch thuê mới (sớm nhất ngày mai)', type: 'date' }] })}
    </>}
  </div>
}
