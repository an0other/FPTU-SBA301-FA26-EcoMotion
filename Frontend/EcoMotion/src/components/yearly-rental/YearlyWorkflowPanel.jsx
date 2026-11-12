import { financialReviewNote, financialReviewLabel } from '../../utils/financialReview'
import { cancelMyBooking } from '../../services/bookingService'
import EvidenceImage from '../booking/EvidenceImage'
import { useCallback, useEffect, useRef, useState } from 'react'
import { yearlyWorkflowService as service } from '../../services/yearlyWorkflowService'
import { readErrorMessage } from '../../services/apiClient'
import ActionForm from './YearlyActionForm'
import { yearlyDate, yearlyMoney, yearlyToday, yearlyButtonClass, yearlyRequestState } from '../../utils/yearlyRental'
import YearlyContractPanel from './YearlyContractPanel'
import YearlyPaymentsPanel from './YearlyPaymentsPanel'
import YearlyIncidentPanel from './YearlyIncidentPanel'
import YearlyRegistrationPanel from './YearlyRegistrationPanel'
import YearlyReturnRequestPanel from './YearlyReturnRequestPanel'
import YearlyRentalStatus from './YearlyRentalStatus'
import YearlyVehicleCard from './YearlyVehicleCard'
import YearlyReturnAssessment from './YearlyReturnAssessment'
import YearlySettlementForm from './YearlySettlementForm'
import YearlyContractTerms from './YearlyContractTerms'
import YearlyRefundsPanel from './YearlyRefundsPanel'
import YearlyObligationsSummary from './YearlyObligationsSummary'

// B1: thêm nhãn vehicleChangeDiscount (giảm trừ do đổi xe rẻ hơn).
const moneyLabels = { rentDue: 'Tiền thuê phải trả', earlyFee: 'Phí kết thúc sớm', lateFee: 'Phí trả trễ', excessKmFee: 'Phí vượt km', otherCharges: 'Phụ phí khác', rentPaid: 'Tiền thuê đã nhận', depositHeld: 'Tiền bảo đảm đang giữ', depositDeduction: 'Khấu trừ tiền bảo đảm', vehicleChangeDiscount: 'Giảm trừ do đổi xe rẻ hơn (đã trừ vào nghĩa vụ)', balanceDue: 'Cần thanh toán bổ sung', rentRefund: 'Tiền thuê cần hoàn', depositRefund: 'Tiền bảo đảm cần hoàn' }
const n = (d, k) => Number(d.get(k))
const fields = {
  terms: [{ name: 'annualKm', label: 'Tổng km cho phép trong năm', type: 'number' }, { name: 'excessKmRate', label: 'Phí mỗi km vượt (đ)', type: 'number' }, { name: 'graceDays', label: 'Số ngày ân hạn trả xe', type: 'number', value: 0 }, { name: 'lateDailyRate', label: 'Phí trả trễ mỗi ngày (đ)', type: 'number' }],
  transfer: [{ name: 'bankReference', label: 'Mã giao dịch hoàn tiền' }, { name: 'file', label: 'Ảnh biên lai', type: 'file' }],
}
function WorkflowAction({ title, action, body, version, busy, run, act, message, ...extra }) {
  async function submit(data) {
    return run(async () => act(action, { version, ...await body(data) }), message || 'Đã cập nhật thông tin.')
  }
  return <ActionForm title={title} busy={busy} confirm onSubmit={submit} {...extra} />
}
export default function YearlyWorkflowPanel({ role = 'renter', bookingCode, bookingStatus: initialBookingStatus, onChanged }) {
  const [data, setData] = useState(null)
  const [activeTab, setActiveTab] = useState('overview')
  const [returnSelection, setReturnSelection] = useState(null)
  const [reviewExpanded, setReviewExpanded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const feedbackRef = useRef(null)
  useEffect(() => {
    if (notice || error) feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [notice, error])
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
  }, [reload, initialBookingStatus])
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (!lock.current && document.visibilityState === 'visible') reload().catch(e => setError(readErrorMessage(e)))
    }, 20000)
    return () => window.clearInterval(timer)
  }, [reload, initialBookingStatus])
  async function run(operation, message = 'Đã cập nhật thông tin.', refresh = true) {
    if (lock.current) return false
    lock.current = true; setBusy(true); setError(''); setNotice('')
    try {
      await operation()
      setNotice(message)
      if (refresh) {
        try { await reload(); await changed.current?.() }
        catch { setError('Thao tác đã được ghi nhận nhưng chưa tải được trạng thái mới. Bấm Tải lại; không gửi lại thao tác hoặc chuyển tiền lần nữa.') }
      }
      return true
    } catch (e) {
      if (e.status === 409 || e.response?.status === 409) { await reload().catch(() => undefined) }
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
  const act = async (action, body) => {
    const result = await service.action(role, bookingCode, action, body)
    if (/^(returns|settlement|refund)/.test(action)) setActiveTab('return')
    if (/^(incidents|replacements)/.test(action)) setActiveTab('incidents')
    return result
  }
  const v = data?.workflow
  const availableOps = data?.operations || {}
  const dueReturn = v?.stage === 'ACTIVE' && v.contract.dueOn <= yearlyToday()
    && !v.incidents.some(i => i.status !== 'CLOSED') && !(data?.reviews?.reviews || []).some(r => ['OPEN', 'IN_REVIEW'].includes(r.status))
  const ops = dueReturn ? { ...availableOps, nextTab: 'return', nextLabel: role === 'renter' ? 'Làm thủ tục trả xe' : 'Xem thủ tục trả xe', needsAction: role === 'renter' && availableOps.canRequestReturn, nextMessage: role === 'renter' ? 'Đã đến hạn trả theo hợp đồng. Gửi yêu cầu trả xe để hai bên kiểm định, chốt phí và hoàn cọc; chưa hoàn tất khi chưa quyết toán.' : 'Đã đến hạn trả theo hợp đồng. Theo dõi yêu cầu trả xe của renter để nhận lại, kiểm định và quyết toán.' } : availableOps
  const currentReturnStep = !v || ['ACTIVE', 'RETURN_REQUESTED', 'RETURN_INSPECTED'].includes(v.stage) ? 1 : ['RETURNED', 'SETTLEMENT_PROPOSED'].includes(v.stage) || (v.stage === 'SETTLEMENT_ACCEPTED' && data.payments.some(p => p.purpose === 'ADJUSTMENT' && !['CONFIRMED', 'VOIDED', 'EXPIRED'].includes(p.status) && Number(p.remainingAmount ?? Number(p.amount) - Number(p.paidAmount || 0)) > 0)) ? 2 : 3
  const returnStep = returnSelection && returnSelection.stage === v?.stage ? returnSelection.step : currentReturnStep
  const provider = role === 'provider'
  const bookingStatus = v?.bookingStatus || data?.contract?.bookingStatus || initialBookingStatus
  const requestState = yearlyRequestState(bookingStatus, v?.stage)
  const { terminal, pending } = requestState
  const reviewOpen = (data?.reviews?.reviews || []).some(r => ['OPEN', 'IN_REVIEW'].includes(r.status))
  const fileId = async d => (await upload(d.get('file'))).id
  const evidence = id => {
    const item = data?.evidence.find(e => e.id === id)
    return item?.imageUrl ? <EvidenceImage src={item.imageUrl} alt="Ảnh bằng chứng" openable className="max-h-48 max-w-full rounded-lg object-contain" /> : null
  }
  const actionMessages = {
    'returns/inspection': 'Đã gửi biên bản kiểm định. Chờ người thuê xác nhận ngày trả và số km.',
    'returns/confirm': 'Đã xác nhận trả xe. Thời gian sử dụng đã kết thúc; chờ nhà cung cấp lập quyết toán.',
    settlement: 'Đã gửi quyết toán. Chờ người thuê xem và đồng ý.',
    'settlement/accept': 'Đã đồng ý quyết toán. Xử lý khoản bổ sung hoặc khoản hoàn theo bảng tiền.',
    'refund/transfer': 'Đã ghi nhận chuyển tiền hoàn. Chờ người thuê xác nhận thực nhận.',
    'refund/confirm': 'Đã xác nhận tiền của lần chuyển này. Nếu còn thiếu, nhà cung cấp tiếp tục hoàn phần còn lại; chỉ hoàn tất khi đã đủ tổng tiền.',
    complete: 'Hợp đồng đã hoàn tất.',
    reopen: 'Đã cập nhật lịch sẵn sàng cho thuê của xe.',
  }
  const form = (title, action, body, extra = {}) => terminal ? null : <WorkflowAction key={title + v.version + (action === 'handover/receive' ? ':' + data.handover.version : '')} title={title} action={action} body={body} version={v.version} busy={busy} run={run} message={actionMessages[action]} act={act} {...extra} />
  const money = value => <dl className="grid gap-3 rounded-xl bg-slate-50 p-4 text-sm sm:grid-cols-2">{Object.entries(moneyLabels).filter(([k]) => value?.[k] != null).map(([k, label]) => <div key={k}><dt className="text-slate-500">{label}</dt><dd className="font-semibold">{yearlyMoney(value[k])}</dd></div>)}</dl>
  if (v && !v.contract.actualStart && data.registration) return <div className="my-5 space-y-4">
    <div className="flex justify-end"><button disabled={busy} className={yearlyButtonClass} onClick={() => run(reload, 'Đã cập nhật trạng thái.', false)}>Cập nhật trạng thái</button></div>
    {error && <p ref={feedbackRef} role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{error}</p>}
    {notice && <p ref={feedbackRef} role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-900">{notice}</p>}
    <YearlyRegistrationPanel role={role} code={bookingCode} data={data} busy={busy} run={run} act={act} form={form} fields={fields} upload={upload} />
    {data.refunds.length > 0 && <YearlyRefundsPanel role={role} code={bookingCode} refunds={data.refunds} busy={busy} run={run} />}
  </div>
  return <div className="my-5 space-y-5">
    <div className="flex items-center justify-between gap-3"><h2 className="text-xl font-bold">Hợp đồng thuê năm</h2><button disabled={busy} className={yearlyButtonClass} onClick={() => run(reload, 'Đã tải trạng thái mới nhất.', false)}>Tải lại</button></div>
    {error && <p ref={feedbackRef} role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</p>}
    {notice && <p ref={feedbackRef} role="status" className="rounded-lg bg-emerald-50 p-4 text-sm text-emerald-900">{notice}</p>}
    {v && <YearlyRentalStatus view={v} provider={provider} reviewOpen={reviewOpen} payments={data.payments} operations={ops} />}
    {v && <>
      <YearlyObligationsSummary view={v} payments={data.payments} refunds={data.refunds} onOpen={setActiveTab} />
      <div className="flex flex-wrap items-center gap-3 rounded-xl border bg-white p-4">
        <div className="min-w-0 flex-1"><p className="text-sm font-semibold">{ops.needsAction ? 'Việc cần bạn xử lý' : 'Theo dõi bước tiếp theo'}</p><p className="mt-1 text-sm text-slate-600">{ops.nextMessage}</p></div>
        <button className={yearlyButtonClass} onClick={() => { setReturnSelection(null); setActiveTab(ops.nextTab || 'overview') }}>{ops.nextLabel || 'Xem tổng quan'}</button>
      </div>
      <nav aria-label="Các mục hợp đồng năm" className="sticky top-0 z-10 flex flex-wrap gap-2 rounded-xl border bg-white p-3">
        {[['overview', 'Tổng quan & xe'], ['payments', 'Thanh toán'], ['incidents', 'Sự cố & đổi xe'], ['return', 'Trả xe & quyết toán'], ['documents', 'Hợp đồng & lịch sử']].map(([id, label]) =>
          <button key={id} type="button" aria-pressed={activeTab === id} aria-controls={'yearly-pane-' + id} onClick={() => setActiveTab(id)} className={'rounded-lg px-4 py-2 text-sm font-semibold ' + (activeTab === id ? 'bg-emerald-800 text-white' : 'bg-slate-50 text-slate-700')}>
            {label}{id === 'incidents' && v.incidents.some(i => i.status !== 'CLOSED') ? ' •' : ''}
          </button>)}
      </nav>
    </>}
    {!v ? <p className="text-sm text-slate-500">{error ? 'Chưa tải được hợp đồng.' : 'Đang tải hợp đồng…'}</p> : <>
      <div id="yearly-pane-overview" hidden={activeTab !== 'overview'} className="space-y-4">
      <YearlyVehicleCard title="Xe đang gắn với hợp đồng" vehicle={v.currentVehicle} />
      <section className="space-y-4 rounded-xl border bg-white p-5">
        <p className="font-semibold text-emerald-800">{requestState.label}</p>
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
        {!terminal && provider && v.stage === 'TERMS_DRAFT' && bookingStatus === 'ACCEPTED' && form('Chốt điều khoản cho người thuê', 'terms', d => Object.fromEntries(fields.terms.map(f => [f.name, n(d, f.name)])), { fields: fields.terms })}
        {!terminal && !provider && v.stage === 'TERMS_DRAFT' && v.annualKm != null && form('Đồng ý điều khoản nhà cung cấp đã đề xuất', 'terms/accept', () => ({}))}
      </section>
      {provider && ops.reopenVehicleIds?.length > 0 && form('Xác nhận xe đã kiểm định và mở lịch cho thuê', 'reopen', d => ({ availableOn: d.get('availableOn'), vehicleId: n(d, 'vehicleId') }), { description: 'Xe cũ sau đổi và xe đã trả được mở lịch riêng, không cần chờ quyết toán hoàn tất.', fields: [{ name: 'vehicleId', label: 'Xe đang chờ kiểm định', type: 'select', options: ops.reopenVehicleIds.map(id => [id, id === v.contract.currentVehicleId ? [v.currentVehicle?.brand, v.currentVehicle?.model, v.currentVehicle?.licensePlate].filter(Boolean).join(' · ') || 'Xe hiện tại' : v.replacements.find(r => r.oldVehicleId === id)?.oldVehicleName || 'Xe đã đổi']) }, { name: 'availableOn', label: 'Nhận lịch thuê mới từ ngày (sớm nhất ngày mai)', type: 'date' }] })}
      </div>
      {pending && <p ref={feedbackRef} role="status" className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm">{provider ? 'Chấp nhận hoặc từ chối yêu cầu ở đầu trang. Sau khi chấp nhận, bạn chốt điều khoản km/phí; người thuê đồng ý và tải hợp đồng đã ký trước khi thanh toán.' : 'Yêu cầu đã gửi, chưa cần thanh toán. Provider cần chấp nhận trước khi hai bên chốt điều khoản và hợp đồng.'}</p>}
      {terminal && <p ref={feedbackRef} role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-900">Đơn không còn ở bước chốt điều khoản. Không gửi lại tiền hoặc bàn giao xe theo hồ sơ này; xem lý do và đối soát các khoản đã nhận nếu có.</p>}
      <div id="yearly-pane-documents" hidden={activeTab !== 'documents'} className="space-y-4">
        <YearlyContractTerms view={v} />
        <p className="rounded-xl border bg-white p-4 text-sm">Hợp đồng đã ký, điều khoản và lịch sử xử lý được giữ để tra cứu. Xe hiện tại có thể khác xe ban đầu sau các lần đổi.</p>
      {!terminal && !provider && v.termsAccepted && data.contract && <YearlyContractPanel contract={data.contract} onUploaded={() => run(async () => {}, 'Đã lưu hợp đồng.')} />}
        {provider && <button disabled={busy} className={yearlyButtonClass} onClick={() => run(async () => {
          const blob = await service.downloadProviderContract(bookingCode)
          const url = URL.createObjectURL(blob), link = document.createElement('a')
          link.href = url; link.download = 'hop-dong-' + bookingCode + '.pdf'; link.click()
          window.setTimeout(() => URL.revokeObjectURL(url), 1000)
        }, 'Đã tải hợp đồng.', false)}>Tải hợp đồng đã ký</button>}
        {(data.reviews?.history || []).length > 0 && <section className="space-y-3 rounded-xl border bg-white p-4"><h3 className="font-bold">Lịch sử xử lý hợp đồng</h3><ol className="space-y-2">{data.reviews.history.map(row => <li key={row.id} className="border-l-2 border-emerald-200 pl-3 text-sm"><p className="font-semibold">{financialReviewLabel(row.outcome)}</p><p className="text-slate-500">{new Date(row.createdAt).toLocaleString('vi-VN')} · Tài khoản #{row.actorId}</p><p>{financialReviewNote(row)}</p></li>)}</ol></section>}
        <ol className="space-y-2">{v.replacements.filter(r => r.status === 'COMPLETED').map(r => <li key={r.id} className="rounded-lg border p-3 text-sm">Đổi xe ngày {yearlyDate(r.exchangedOn)}: {r.oldVehicleName} → {r.newVehicleName}</li>)}</ol>
      </div>
      {!provider && !v.contract.actualStart && ['PENDING', 'ACCEPTED', 'AWAITING_DEPOSIT', 'CONFIRMED'].includes(bookingStatus) && <ActionForm title="Hủy yêu cầu trước nhận xe" description="Chưa nhận xe thì không cần xác nhận nhận xe để hủy. Tiền đã thu sẽ được đối soát theo hợp đồng trước khi đóng hồ sơ." busy={busy} confirm onSubmit={() => run(() => cancelMyBooking(bookingCode), 'Đã gửi yêu cầu hủy. Theo dõi trạng thái đối soát nếu có tiền đã nhận.')} />}

      <div id="yearly-pane-payments" hidden={activeTab !== 'payments'} className="space-y-4">
      {!['SETTLEMENT_PROPOSED', 'SETTLEMENT_ACCEPTED', 'REFUND_TRANSFER_RECORDED', 'REFUND_RECEIVED', 'COMPLETED'].includes(v.stage) && <section className="space-y-3 rounded-xl border bg-white p-5"><h3 className="text-lg font-bold">Tổng quan thanh toán hiện tại</h3><dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt>Tổng tiền thuê theo hợp đồng</dt><dd className="font-semibold">{yearlyMoney(v.contract.totalRent)}</dd></div><div><dt>Tiền bảo đảm đã xác nhận nhận</dt><dd className="font-semibold">{yearlyMoney(data.payments.filter(p => p.purpose === 'SECURITY_DEPOSIT').reduce((sum,p) => sum + Number(p.paidAmount || 0),0))}</dd></div><div><dt>Tiền đã nhận cho các kỳ (kỳ đầu có thể gồm phí giao)</dt><dd className="font-semibold">{yearlyMoney(data.payments.filter(p => p.installmentNumber > 0).reduce((sum,p) => sum + Number(p.paidAmount || 0),0))}</dd></div></dl><p className="text-xs text-slate-500">Chưa có quyết toán cuối hợp đồng. Phí trả sớm, phí trễ, khấu trừ và tiền hoàn chỉ được hiển thị sau khi có đề xuất quyết toán, không mặc định là 0 đồng.</p></section>}
      <YearlyPaymentsPanel role={role} rows={data.payments} busy={busy} run={run} canCollect={!terminal && !reviewOpen} canPayRent={!!ops.canPayRent} canPayAdjustment={!!ops.canPayAdjustment} allowExceptions={!terminal && !reviewOpen && v.stage !== 'COMPLETED'} canReportMismatch={!reviewOpen && !!data.booking?.disputeCategories?.includes('PAYMENT_MISMATCH')} />
      <YearlyRefundsPanel role={role} code={bookingCode} refunds={data.refunds} busy={busy} run={run} />
      </div>
      <div id="yearly-pane-incidents" hidden={activeTab !== 'incidents'} className="space-y-4">
      <YearlyIncidentPanel key={bookingCode} role={role} code={bookingCode} view={v} busy={busy} run={run} act={act} upload={upload} evidence={evidence} reviewOpen={reviewOpen} />
      {!provider && !reviewOpen && v.stage !== 'COMPLETED' && v.incidents.some(item => item.status !== 'CLOSED') && <details className="rounded-xl border p-4"><summary className="cursor-pointer text-sm font-semibold">Có vấn đề chưa được giải quyết? Yêu cầu hỗ trợ</summary><ActionForm title="Yêu cầu Admin xem xét report chưa được giải quyết" busy={busy} confirm fields={[{ name: 'incidentId', label: 'Report cần xem xét', type: 'select', options: v.incidents.filter(item => item.status !== 'CLOSED').map(item => [item.id, '#' + item.id + ' · ' + item.description]) }, { name: 'note', label: 'Vấn đề cần Admin xử lý', type: 'textarea' }, { name: 'file', label: 'Bằng chứng', type: 'file' }]} onSubmit={d => run(async () => act('incidents/' + d.get('incidentId') + '/review', { requestId: crypto.randomUUID(), version: v.version, note: d.get('note'), evidenceId: await fileId(d) }))} /></details>}
      {reviewOpen && <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">Hồ sơ đang được Admin xem xét. Các bước chốt liên quan sẽ tiếp tục sau khi có kết luận; xem tiến độ và phản hồi ở lịch sử xử lý.</p>}
      </div>
      <div id="yearly-pane-return" hidden={activeTab !== 'return'} className="space-y-4">
        <h3 className="text-lg font-bold">Trả xe và quyết toán hợp đồng</h3>
        <nav aria-label="Các bước trả xe và quyết toán" className="grid gap-2 sm:grid-cols-3">{[[1, 'Trả xe & kiểm định'], [2, 'Chốt tiền & thanh toán'], [3, 'Hoàn tiền & hoàn tất']].map(([step, label]) => <button type="button" key={step} disabled={step > currentReturnStep} aria-pressed={returnStep === step} aria-current={currentReturnStep === step ? 'step' : undefined} onClick={() => setReturnSelection({ stage: v.stage, step })} className={'rounded-xl border p-4 text-left text-sm disabled:opacity-40 ' + (returnStep === step ? 'border-emerald-700 bg-emerald-50 font-bold text-emerald-900' : 'bg-white text-slate-600')}>{step}. {label}<span className="mt-1 block text-xs">{step < currentReturnStep ? 'Đã qua bước này · xem lại' : step === currentReturnStep ? 'Bước hiện tại' : 'Chưa đến bước này'}</span></button>)}</nav>

        {v.stage === 'COMPLETED' && <section className="space-y-2 rounded-xl border border-emerald-200 bg-emerald-50 p-5"><h4 className="font-bold">Đã hoàn tất hồ sơ</h4><p>Ngày trả: {yearlyDate(v.returnedOn)} · Tổng quãng đường: {v.totalKm} km</p><p>Chốt quyết toán: {yearlyDate(v.settlementAcceptedAt)} · Hoàn tất: {yearlyDate(v.completedAt)}</p><p>Chứng từ và bảng tiền bên dưới chỉ để tra cứu. Không cần trả thêm các kỳ đã dừng thu.</p></section>}
        {v.stage !== 'ACTIVE' && <p className="rounded-lg bg-blue-50 p-4 text-sm">Kiểm tra trạng thái ở đầu trang. Chỉ xác nhận sau khi đã xem biên bản hoặc bảng tiền tại mục này; gửi yêu cầu chưa đồng nghĩa đã trả xe hay đã hoàn cọc.</p>}
      <div hidden={returnStep !== 1} className="space-y-4">
      {(ops.canRequestReturn || ops.canEditReturn) && <YearlyReturnRequestPanel key={v.version} editing={ops.canEditReturn} view={v} busy={busy} run={run} act={act} money={money} />}
      {ops.canInspectReturn && v.stage === 'RETURN_REQUESTED' && form('Kiểm định khi nhận lại xe', 'returns/inspection', async d => ({ returnedOn: d.get('returnedOn'), finalKm: n(d, 'finalKm'), evidenceId: d.get('file')?.size ? await fileId(d) : v.returnEvidenceId }), { fields: [{ name: 'returnedOn', label: 'Ngày nhận lại xe thực tế', type: 'date', value: v.returnedOn || yearlyToday() }, { name: 'finalKm', label: 'Công-tơ-mét khi trả (km)', type: 'number', value: v.finalKm }, { name: 'file', label: 'Ảnh kiểm định và công-tơ-mét', type: 'file', required: !v.returnEvidenceId }] })}
      {ops.canInspectReturn && v.stage === 'RETURN_INSPECTED' && <div className="space-y-3 rounded-xl border bg-white p-4">
        <p role="status" className="font-semibold text-emerald-800">Đã gửi kiểm định — chờ người thuê xác nhận ngày trả và số km.</p>
        <details><summary className="cursor-pointer text-sm underline">Chỉnh sửa biên bản đã gửi</summary>{form('Kiểm định khi nhận lại xe', 'returns/inspection', async d => ({ returnedOn: d.get('returnedOn'), finalKm: n(d, 'finalKm'), evidenceId: d.get('file')?.size ? await fileId(d) : v.returnEvidenceId }), { fields: [{ name: 'returnedOn', label: 'Ngày nhận lại xe thực tế', type: 'date', value: v.returnedOn || yearlyToday() }, { name: 'finalKm', label: 'Công-tơ-mét khi trả (km)', type: 'number', value: v.finalKm }, { name: 'file', label: 'Ảnh kiểm định và công-tơ-mét', type: 'file', required: !v.returnEvidenceId }] })}</details>
      </div>}
      {v.requestedReturnOn && <p className="rounded-lg border bg-white p-4 text-sm">Ngày dự kiến trả đã gửi: <strong>{yearlyDate(v.requestedReturnOn)}</strong>. Ngày trả thực tế được chốt theo biên bản hai bên.</p>}
      {ops.canWithdrawReturn && form('Rút yêu cầu trả xe và tiếp tục thuê', 'returns/withdraw', () => ({}), { description: 'Chỉ rút trước khi provider gửi biên bản kiểm định. Hợp đồng và lịch thanh toán được tiếp tục.' })}
      {v.returnedOn && <section className="space-y-2 rounded-xl border bg-white p-5"><h3 className="font-bold">Biên bản nhận lại xe cần kiểm tra</h3><p>Km đầu chặng xe hiện tại: {v.currentInitialKm} km · Các chặng đã hoàn tất: {v.completedKm} km</p><p>Nhận lại xe: {yearlyDate(v.returnedOn)} · Công-tơ-mét: {v.finalKm} km · Tổng đã đi: {v.totalKm} km</p>{evidence(v.returnEvidenceId)}</section>}
      {data.assessmentError && <p role="alert" className="rounded-lg bg-red-50 p-4 text-sm">Chưa thể tính đối chiếu: {data.assessmentError}. Cần đối soát trước khi chốt tiền; vẫn có thể xác nhận biên bản trả xe nếu ngày, ảnh và km đúng. Không tự coi phí bằng 0.</p>}
      {data.returnAssessment && <YearlyReturnAssessment assessment={data.returnAssessment} evidence={evidence} payments={data.payments} adminResolved={data.reviews?.history?.some(row => row.outcome === 'SETTLEMENT_RULING')} />}
      {ops.canConfirmReturn && form('Tôi đã xem biên bản và xác nhận trả xe', 'returns/confirm', () => ({}), { submitLabel: 'Xác nhận biên bản trả xe' })}
      {!provider && !reviewOpen && v.stage === 'RETURN_INSPECTED' && <button className="rounded-lg border border-amber-500 px-4 py-3 text-sm font-semibold text-amber-900" onClick={() => setReviewExpanded(true)}>Chưa đồng ý biên bản — yêu cầu xem xét</button>}
      </div>
      <div hidden={returnStep !== 2} className="space-y-4">
      {data.returnAssessment && <YearlyReturnAssessment assessment={data.returnAssessment} evidence={evidence} payments={data.payments} adminResolved={data.reviews?.history?.some(row => row.outcome === 'SETTLEMENT_RULING')} />}
      {ops.canProposeSettlement && data.returnAssessment && <YearlySettlementForm key={v.version} view={v} assessment={data.returnAssessment} busy={busy} run={run} act={act} upload={upload} evidence={evidence} />}
      {v.money && !data.returnAssessment && ['SETTLEMENT_PROPOSED', 'SETTLEMENT_ACCEPTED', 'REFUND_TRANSFER_RECORDED', 'REFUND_RECEIVED', 'COMPLETED'].includes(v.stage) && <section className="space-y-3 rounded-xl border bg-white p-5"><h3 className="text-lg font-bold">Đối soát tiền</h3>{money(v.money)}{v.money.note && <p className="whitespace-pre-wrap text-sm">{v.money.note}</p>}{evidence(v.settlementEvidenceId)}</section>}
      {!provider && !reviewOpen && ['RETURN_INSPECTED','SETTLEMENT_PROPOSED'].includes(v.stage) && <button className="rounded-lg border border-amber-500 px-4 py-3 text-sm font-semibold text-amber-900" onClick={() => { setReviewExpanded(true); document.getElementById('yearly-review-' + bookingCode)?.scrollIntoView({behavior:'smooth',block:'center'}) }}>Chưa đồng ý biên bản / quyết toán — yêu cầu xem xét</button>}
      {ops.canAcceptSettlement && data.returnAssessment && form('Đồng ý quyết toán', 'settlement/accept', () => ({}))}
      {v.stage === 'SETTLEMENT_ACCEPTED' && currentReturnStep === 2 && <section className="rounded-xl border border-amber-200 bg-amber-50 p-5"><h4 className="font-bold">Xử lý khoản bổ sung trước khi hoàn tiền</h4><p className="my-2 text-sm">Mở khoản bổ sung quyết toán để gửi biên lai hoặc đối soát. Không chuyển vào các kỳ thuê đã dừng thu.</p><button className={yearlyButtonClass} onClick={() => setActiveTab('payments')}>Mở thanh toán bổ sung</button></section>}
      </div>
      <div hidden={returnStep !== 3} className="space-y-4">
      {v.money && <section className="rounded-xl border bg-white p-5"><h4 className="mb-3 font-bold">Khoản hoàn theo quyết toán</h4><dl className="grid gap-3 text-sm sm:grid-cols-3"><div><dt>Tiền thuê hoàn</dt><dd className="font-bold">{yearlyMoney(v.money.rentRefund)}</dd></div><div><dt>Cọc hoàn sau khấu trừ</dt><dd className="font-bold">{yearlyMoney(v.money.depositRefund)}</dd></div><div><dt>Tổng phải hoàn</dt><dd className="font-bold">{yearlyMoney(Number(v.money.rentRefund) + Number(v.money.depositRefund))}</dd></div></dl><p className="mt-3 text-xs text-slate-500">Các khoản chuyển dư được hoàn riêng tại mục Thanh toán. Gửi biên lai chưa đồng nghĩa người thuê đã nhận tiền.</p></section>}
      {provider && v.stage === 'SETTLEMENT_ACCEPTED' && !ops.canTransferRefund && !ops.canComplete && <p role="status" className="rounded-xl bg-amber-50 p-4 text-sm">Chưa thể chuyển hoàn hoặc đóng hồ sơ. {ops.nextMessage} Kiểm tra thêm các khoản hoàn riêng tại mục Thanh toán và sự cố chưa đóng; không chuyển tiền lần nữa khi kết quả cũ chưa rõ.</p>}
      {ops.canTransferRefund && form('Đã chuyển tiền hoàn cho người thuê', 'refund/transfer', async d => ({ amount: n(d, 'amount'), bankReference: d.get('bankReference'), evidenceId: await fileId(d) }), { fields: [{ name: 'amount', label: 'Số tiền của lần chuyển hoàn này (đ)', type: 'money', min: 1, max: v.refundRemaining, value: v.refundRemaining }, ...fields.transfer] })}
      {Number(v.refundVerifiedReceived || 0) > 0 && <p className="rounded-xl bg-emerald-50 p-3 text-sm">Tiền hoàn đã xác minh nhận: {yearlyMoney(v.refundVerifiedReceived)}. Còn phải chuyển: {yearlyMoney(v.refundRemaining)}. Chỉ chuyển phần còn thiếu.</p>}
      {!provider && !reviewOpen && v.stage === 'REFUND_TRANSFER_RECORDED' && <details className="rounded-xl border p-4"><summary className="cursor-pointer font-semibold">Chưa nhận / nhận thiếu tiền hoàn?</summary><ActionForm title="Chưa nhận hoặc nhận thiếu tiền hoàn" busy={busy} confirm fields={[{ name: 'receivedAmount', label: 'Tổng tiền hoàn thực tế đã nhận, nhập 0 nếu chưa nhận (đ)', type: 'number', value: v.refundVerifiedReceived || 0 }, { name: 'note', label: 'Mô tả khoản chưa nhận / nhận thiếu', type: 'textarea' }, { name: 'file', label: 'Bằng chứng liên quan', type: 'file' }]} onSubmit={d => run(async () => act('refund/review', { requestId: crypto.randomUUID(), version: v.version, receivedAmount: n(d, 'receivedAmount'), note: d.get('note'), evidenceId: await fileId(d) }))} /></details>}
      {!!v.refundTransfers?.length && <section className="space-y-3 rounded-xl border bg-white p-4"><h4 className="font-bold">Lịch sử từng lần chuyển hoàn</h4>{v.refundTransfers.map(t => <article key={t.key} className="space-y-2 rounded-lg border p-3 text-sm"><p className="font-semibold">{yearlyMoney(t.amount)} · {t.bankReference}</p><p>{({PENDING:'Chờ xác minh thực nhận', CONFIRMED:'Đã xác minh nhận đủ lần chuyển', PARTIAL_FINAL:'Đã xác minh một phần; phần còn lại không còn treo', FAILED:'Đã xác minh thất bại'})[t.status] || t.status} · Đã xác minh: {yearlyMoney(t.verifiedAmount)}</p><p>Gửi: {new Date(t.submittedAt).toLocaleString('vi-VN')}{t.verifiedAt ? ' · Xác minh: ' + new Date(t.verifiedAt).toLocaleString('vi-VN') : ''}</p><details><summary className="cursor-pointer underline">Xem biên lai của lần chuyển</summary>{evidence(t.evidenceId)}</details></article>)}</section>}
      {!v.refundTransfers?.length && v.refundReference && <p className="text-sm">Giao dịch hoàn: {v.refundReference} {evidence(v.refundEvidenceId)}</p>}
      {ops.canConfirmRefund && form('Xác nhận đã nhận đủ tiền của lần chuyển này', 'refund/confirm', () => ({}))}
      {ops.canComplete && form('Hoàn tất hợp đồng', 'complete', () => ({}))}
      </div>
      <details id={'yearly-review-' + bookingCode} open={reviewExpanded} onToggle={e => setReviewExpanded(e.currentTarget.open)} className="space-y-3 rounded-xl border bg-white p-4"><summary className="cursor-pointer font-semibold">Có nội dung chưa đồng ý / lịch sử xem xét</summary>
        <h3 className="font-bold">Khiếu nại và phân xử hợp đồng</h3>
        {(data.reviews?.reviews || []).map(review => <div key={review.id} className="rounded-lg border p-3 text-sm"><p>Hồ sơ #{review.id} · {review.status}</p><p>{review.description}</p>{review.resolution && <p>Kết luận: {review.resolution}</p>}</div>)}
        {!provider && ['RETURN_INSPECTED', 'SETTLEMENT_PROPOSED'].includes(v.stage) && !(data.reviews?.reviews || []).some(r => ['OPEN', 'IN_REVIEW'].includes(r.status)) && <ActionForm title="Yêu cầu xem xét kiểm định / quyết toán" busy={busy} confirm fields={[{ name: 'note', label: 'Nội dung không đồng ý và lý do', type: 'textarea' }, { name: 'file', label: 'Ảnh bằng chứng', type: 'file' }]} onSubmit={d => run(async () => act('reviews', { requestId: crypto.randomUUID(), version: v.version, note: d.get('note'), evidenceId: (await upload(d.get('file'))).id }))} />}
        {provider && (data.reviews?.reviews || []).some(r => ['OPEN', 'IN_REVIEW'].includes(r.status)) && <ActionForm title="Phản hồi hồ sơ đang xem xét" busy={busy} confirm fields={[{ name: 'disputeId', label: 'Hồ sơ', type: 'select', options: data.reviews.reviews.filter(r => ['OPEN', 'IN_REVIEW'].includes(r.status)).map(r => [r.id, 'Hồ sơ #' + r.id]) }, { name: 'note', label: 'Ý kiến và giải trình', type: 'textarea' }, { name: 'file', label: 'Ảnh bằng chứng', type: 'file' }]} onSubmit={d => run(async () => act('reviews/reply', { requestId: crypto.randomUUID(), disputeId: Number(d.get('disputeId')), note: d.get('note'), evidenceId: (await upload(d.get('file'))).id }))} />}
        {(data.reviews?.history || []).length > 0 && <details><summary>Lịch sử xử lý</summary>{data.reviews.history.map(row => <p key={row.id} className="mt-2 break-words text-sm">{new Date(row.createdAt).toLocaleString('vi-VN')} · {financialReviewLabel(row.outcome)} · {financialReviewNote(row)}</p>)}</details>}
      </details>
      </div>
    </>}
  </div>
}
