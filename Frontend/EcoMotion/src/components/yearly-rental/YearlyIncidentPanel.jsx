import { useState } from 'react'
import ActionForm from './YearlyActionForm'
import { yearlyDate, yearlyMoney, yearlyUuid } from '../../utils/yearlyRental'
import YearlyReplacementPicker from './YearlyReplacementPicker'
import YearlyVehicleCard from './YearlyVehicleCard'

const outcomes = { FIXED: 'Đã sửa xong, tiếp tục thuê', REIMBURSE: 'Hoàn trực tiếp chi phí bảo trì', END_PROVIDER: 'Kết thúc do lỗi phía nhà cung cấp', REPLACE: 'Đổi xe', RENTER_PAID: 'Người thuê đã tự thanh toán' }
const categories = { OWNER_MAINTENANCE: 'Hao mòn / trách nhiệm phía nhà cung cấp', RENTER_DAMAGE: 'Hư hỏng do người thuê' }
const statuses = { RESOLUTION_PROPOSED: 'Đã đề xuất, chờ người thuê đồng ý', OPEN: 'Chờ xử lý', AWAITING_TRANSFER: 'Chờ hoàn tiền', TRANSFER_RECORDED: 'Đã chuyển tiền, chờ xác nhận', AWAITING_CONFIRMATION: 'Chờ người thuê xác nhận', CLOSED: 'Đã giải quyết' }
function ResolutionForm({ incident, busy, run, act }) {
  const [responsibility, setResponsibility] = useState(incident.proposedCategory || incident.category)
  const [outcome, setOutcome] = useState('FIXED')
  const allowed = responsibility === 'OWNER_MAINTENANCE' ? ['FIXED', ...(Number(incident.invoiceAmount) > 0 ? ['REIMBURSE'] : []), 'END_PROVIDER', 'REPLACE'] : ['FIXED', 'RENTER_PAID']
  return <div className="space-y-3 rounded-lg bg-slate-50 p-4">
    <label className="block text-sm font-semibold">Trách nhiệm provider đề xuất<select value={responsibility} disabled={busy} className="mt-1 block w-full rounded-lg border p-2" onChange={e => { setResponsibility(e.target.value); setOutcome('FIXED') }}>{Object.entries(categories).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
    <label className="block text-sm font-semibold">Phương án<select value={outcome} disabled={busy} className="mt-1 block w-full rounded-lg border p-2" onChange={e => setOutcome(e.target.value)}>{allowed.map(key => <option key={key} value={key}>{outcomes[key]}</option>)}</select></label>
    <ActionForm key={responsibility + outcome} title="Gửi phương án để người thuê xem và xác nhận" busy={busy} confirm fields={[
      ...(outcome === 'REIMBURSE' ? [{ name: 'approvedAmount', label: 'Tiền hoàn trực tiếp (không quá hóa đơn)', type: 'number', min: 1, max: incident.invoiceAmount, value: incident.invoiceAmount }] : []),
      { name: 'note', label: 'Kết luận, căn cứ và thỏa thuận', type: 'textarea' },
    ]} onSubmit={d => run(() => act('incidents/' + incident.id + '/resolution', { version: incident.version, responsibility, outcome, approvedAmount: outcome === 'REIMBURSE' ? Number(d.get('approvedAmount')) : 0, note: d.get('note') }), 'Đã gửi phương án. Chờ người thuê đồng ý; chưa tự kết thúc hợp đồng hoặc xác nhận đã hoàn tiền.')} />
  </div>
}
export default function YearlyIncidentPanel({ role, code, view, busy, run, act, upload, evidence, reviewOpen = false }) {
  const [requestId, setRequestId] = useState(yearlyUuid)
  const incidents = view.incidents || []
  const replacements = view.replacements || []
  const num = (d, k) => Number(d.get(k))
  const fileId = async (d, k = 'file') => (await upload(d.get(k))).id
  return <section className="space-y-4 rounded-xl border bg-white p-5">
    <h3 className="text-lg font-bold">Sự cố và đổi xe</h3>
    <p className="text-sm text-slate-600">Chi phí sửa chữa được hai bên xử lý trực tiếp và lưu bằng chứng; không tự trừ vào tiền thuê tháng sau.</p>
    {!reviewOpen && role === 'renter' && view.stage === 'ACTIVE' && !incidents.some(i => i.status !== 'CLOSED') && <ActionForm title="Báo sự cố" busy={busy} fields={[
      { name: 'category', label: 'Trách nhiệm theo thông tin người thuê báo (chưa phải kết luận)', type: 'select', options: [['OWNER_MAINTENANCE', 'Hao mòn / lỗi phía nhà cung cấp'], ['RENTER_DAMAGE', 'Hư hỏng do người thuê']] },
      { name: 'occurredOn', label: 'Ngày xảy ra', type: 'date' }, { name: 'description', label: 'Mô tả', type: 'textarea' },
      { name: 'invoiceAmount', label: 'Chi phí theo hóa đơn (đ)', type: 'number', value: 0 }, { name: 'file', label: 'Ảnh sự cố / hóa đơn', type: 'file' },
    ]} onSubmit={d => run(async () => {
      await act('incidents', { requestId, category: d.get('category'), occurredOn: d.get('occurredOn'), description: d.get('description'), invoiceAmount: num(d, 'invoiceAmount'), evidenceId: await fileId(d) })
      setRequestId(yearlyUuid())
    })} />}
    {incidents.length === 0 && <p className="rounded-lg bg-slate-50 p-3 text-sm">Chưa có sự cố. Khi phát sinh, gửi ảnh và mô tả để hai bên thống nhất cách xử lý.</p>}
    {incidents.map(i => <details key={i.id} open={i.status !== 'CLOSED'} className="space-y-3 rounded-xl border p-4"><summary className="cursor-pointer font-semibold">#{i.id} · {i.description} · {statuses[i.status] || i.status}</summary>
      <h4 className="font-semibold">Report sự cố · {yearlyDate(i.occurredOn)}</h4>
      <p className="text-sm text-slate-600">Người thuê báo: {categories[i.reportedCategory || i.category]}</p>
      {!['OPEN', 'RESOLUTION_PROPOSED'].includes(i.status) && <p className="text-sm font-semibold">Trách nhiệm đã chốt: {categories[i.category]}</p>}
      <p className="whitespace-pre-wrap text-sm">{i.description}</p>
      <p className="text-sm">{statuses[i.status] || i.status} · Hóa đơn: {yearlyMoney(i.invoiceAmount)}</p>
      {i.outcome === 'REIMBURSE' && <div role="status" className="space-y-1 rounded-lg bg-blue-50 p-3 text-sm">
        <p className="font-semibold">{i.status === 'RESOLUTION_PROPOSED' ? 'Khoản hoàn provider đề xuất:' : 'Khoản hoàn bảo trì được chốt:'} {yearlyMoney(i.approvedAmount)}</p>
        <p>{i.status === 'AWAITING_TRANSFER' ? (role === 'provider' ? 'Bạn cần hoàn tiền theo kết quả đã chốt. Nếu đã chuyển ngoài ngân hàng, ghi nhận biên lai tại nút bên dưới để người thuê xác nhận.' : 'Đã chốt nghĩa vụ hoàn tiền; chưa ghi nhận biên lai chuyển. Bạn chưa cần làm gì ở bước này.')
          : i.status === 'TRANSFER_RECORDED' ? (role === 'provider' ? 'Đã gửi biên lai chuyển tiền. Chờ người thuê xác nhận thực nhận.' : 'Nhà cung cấp đã gửi biên lai. Kiểm tra tài khoản rồi xác nhận nhận tiền bên dưới.')
          : i.status === 'CLOSED' ? 'Đã xác nhận hoàn chi phí bảo trì. Report đã đóng; tiếp tục thuê theo hợp đồng.' : 'Theo dõi kết quả xử lý tại đây.'}</p>
      </div>}
      {evidence(i.evidenceId)}{i.resolutionNote && <p className="whitespace-pre-wrap text-sm">{i.outcome ? outcomes[i.outcome] + ' · ' : 'Phản hồi: '}{i.resolutionNote}</p>}
      {!reviewOpen && role === 'provider' && ['OPEN', 'RESOLUTION_PROPOSED'].includes(i.status) && view.stage === 'ACTIVE' && <details open={i.status === 'OPEN'}><summary className="cursor-pointer font-semibold">{i.status === 'OPEN' ? 'Đề xuất xử lý sự cố' : 'Chỉnh phương án đã gửi'}</summary><ResolutionForm key={i.version} incident={i} busy={busy} run={run} act={act} /></details>}
      {i.status === 'RESOLUTION_PROPOSED' && <div className="space-y-3 rounded-lg border border-blue-200 bg-blue-50 p-4"><p className="font-semibold">Đề xuất: {categories[i.proposedCategory || i.category]} · {outcomes[i.outcome]}</p><p className="text-sm">{i.resolutionNote}</p><p className="text-sm">{i.outcome === 'END_PROVIDER' ? 'Đồng ý sẽ bắt đầu trả xe do phía provider, hoàn tiền thuê tháng sự cố và các tháng sau đã trả theo quyết toán. Cọc được quyết toán riêng.' : 'Đồng ý phương án chưa đồng nghĩa đã nhận tiền hoặc đã nhận xe thay thế.'}</p>
        {!reviewOpen && role === 'renter' && <><ActionForm title="Tôi đồng ý trách nhiệm và phương án này" busy={busy} confirm onSubmit={() => run(() => act('incidents/' + i.id + '/confirm', { version: i.version }), 'Đã đồng ý phương án. Xem bước tiếp theo trong report hoặc mục Trả xe.')} /><details><summary className="cursor-pointer text-sm underline">Chưa đồng ý? Gửi lại nội dung cần làm rõ</summary><ActionForm title="Phản hồi phương án" busy={busy} confirm fields={[{ name: 'note', label: 'Nội dung cần điều chỉnh và lý do', type: 'textarea' }]} onSubmit={d => run(() => act('incidents/' + i.id + '/reject-resolution', { version: i.version, note: d.get('note') }), 'Đã gửi phản hồi. Report trở lại chờ provider xử lý.')} /></details></>}
      </div>}
      {!reviewOpen && role === 'provider' && i.status === 'AWAITING_TRANSFER' && <ActionForm title={'Tôi đã chuyển khoản hoàn bảo trì ' + yearlyMoney(i.approvedAmount)} submitLabel="Gửi biên lai để người thuê xác nhận"  busy={busy} confirm fields={[
        { name: 'bankReference', label: 'Mã chuyển khoản' }, { name: 'file', label: 'Biên lai hoàn tiền', type: 'file' },
      ]} onSubmit={d => run(async () => act('incidents/' + i.id + '/transfer', { version: i.version, bankReference: d.get('bankReference'), evidenceId: await fileId(d) }), 'Đã ghi nhận chuyển tiền bảo trì. Chờ người thuê xác nhận đã nhận.')} />}
      {evidence(i.transferEvidenceId)}
      {!reviewOpen && role === 'renter' && ['TRANSFER_RECORDED', 'AWAITING_CONFIRMATION'].includes(i.status) && i.outcome !== 'REPLACE' && <ActionForm title={i.status === 'TRANSFER_RECORDED' ? 'Tôi đã nhận đủ ' + yearlyMoney(i.approvedAmount) + ' tiền bảo trì' : 'Xác nhận kết quả xử lý'} confirm busy={busy} onSubmit={() => run(() => act('incidents/' + i.id + '/confirm', { version: i.version }), 'Đã xác nhận kết quả. Report được đóng, tiếp tục thuê theo hợp đồng.')} />}
      {!reviewOpen && role === 'provider' && i.outcome === 'REPLACE' && i.status === 'AWAITING_CONFIRMATION' && !replacements.some(r => r.incidentId === i.id && r.status !== 'CANCELLED') && <YearlyReplacementPicker code={code} incident={i} dueOn={view.contract.dueOn} busy={busy} run={run} act={act} />}
    </details>)}
    {replacements.map(r => <article key={r.id} className="space-y-3 rounded-xl border border-emerald-200 p-4">
      <h4 className="font-semibold">Đổi xe · dự kiến {yearlyDate(r.plannedOn)}</h4>
      <div className="grid gap-4 md:grid-cols-2"><YearlyVehicleCard title="Xe trước khi đổi" vehicle={r.oldVehicle} /><YearlyVehicleCard title="Xe thay thế được đề nghị" vehicle={r.newVehicle} /></div>
      <p className="text-sm">Xe cũ {r.oldVehicleName || `mã ${r.oldVehicleId}`} → xe thay thế {r.newVehicleName || `mã ${r.newVehicleId}`}</p>
      {r.oldPlanPrice != null && r.newPlanPrice != null && <p className="text-sm text-slate-600">Giá thuê năm: {yearlyMoney(r.oldPlanPrice)} → {yearlyMoney(r.newPlanPrice)}. {Number(r.newPlanPrice) > Number(r.oldPlanPrice) ? 'Xe mới đắt hơn: khách không phải trả thêm.' : Number(r.newPlanPrice) < Number(r.oldPlanPrice) ? 'Xe mới rẻ hơn: phần chênh lệch theo số ngày dùng xe mới được trừ vào quyết toán.' : 'Không phát sinh chênh lệch.'}</p>}
      {r.lowerQuality && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-2 text-sm text-rose-900">Xe thay thế có chất lượng thấp hơn: {r.qualityNotes}. Chỉ đổi khi người thuê xác nhận đồng ý{r.qualityAcknowledged ? ' (đã xác nhận)' : ''}.</p>}
      <p className="rounded-lg bg-blue-50 p-3 text-sm">Hạn hợp đồng giữ nguyên: {yearlyDate(view.contract.dueOn)}. Cọc không đổi. Đồng ý đề nghị chưa có nghĩa đã nhận xe mới; cần xác nhận biên bản bàn giao.</p>
      <p className="text-sm">{({ PROPOSED: 'Chờ người thuê đồng ý', ACCEPTED: 'Đã đồng ý, chờ bàn giao', HANDOVER_PROPOSED: 'Chờ xác nhận nhận xe mới', COMPLETED: 'Đã đổi xe', CANCELLED: 'Đã hủy' })[r.status]}</p>
      {!reviewOpen && role === 'renter' && r.status === 'PROPOSED' && <ActionForm title="Đồng ý xe thay thế và chính sách chênh lệch, giữ nguyên cọc và hạn" confirm busy={busy}
        fields={r.lowerQuality ? [{ name: 'ack', label: 'Xe thay thế có chất lượng thấp hơn. Bạn có đồng ý nhận xe này?', type: 'select', options: [['yes', 'Tôi đồng ý nhận xe có chất lượng thấp hơn']] }] : []}
        onSubmit={d => run(() => act('replacements/' + r.id + '/accept', { version: r.version, acknowledgeLowerQuality: d.get('ack') === 'yes' }))} />}
      {!reviewOpen && role === 'provider' && ['ACCEPTED', 'HANDOVER_PROPOSED'].includes(r.status) && <ActionForm title="Ghi nhận bàn giao xe thay thế" busy={busy} fields={[
        { name: 'exchangedOn', label: 'Ngày đổi xe', type: 'date' }, { name: 'oldFinalKm', label: 'Công-tơ-mét xe cũ (km)', type: 'number' },
        { name: 'newInitialKm', label: 'Công-tơ-mét xe mới (km)', type: 'number' },
        { name: 'oldFile', label: 'Ảnh bàn giao xe cũ', type: 'file' }, { name: 'newFile', label: 'Ảnh bàn giao xe mới', type: 'file' },
      ]} onSubmit={d => run(async () => act('replacements/' + r.id + '/inspection', { version: r.version, exchangedOn: d.get('exchangedOn'), oldFinalKm: num(d, 'oldFinalKm'), newInitialKm: num(d, 'newInitialKm'), oldEvidenceId: await fileId(d, 'oldFile'), newEvidenceId: await fileId(d, 'newFile') }))} />}
      {r.oldFinalKm != null && <p className="text-sm">Xe cũ: {r.oldFinalKm} km · xe mới: {r.newInitialKm} km</p>}{evidence(r.oldEvidenceId)}{evidence(r.newEvidenceId)}
      {!reviewOpen && role === 'renter' && r.status === 'HANDOVER_PROPOSED' && <ActionForm title="Đã nhận xe thay thế và kiểm tra số km" confirm busy={busy} onSubmit={() => run(() => act('replacements/' + r.id + '/confirm', { version: r.version }))} />}
      {['PROPOSED', 'ACCEPTED', 'HANDOVER_PROPOSED'].includes(r.status) && <ActionForm title="Hủy đề nghị đổi xe này" confirm busy={busy} onSubmit={() => run(() => act('replacements/' + r.id + '/cancel', { version: r.version }))} />}
    </article>)}
  </section>
}
