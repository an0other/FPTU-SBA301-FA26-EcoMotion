import { useState } from 'react'
import ActionForm from './YearlyActionForm'
import { yearlyDate, yearlyMoney, yearlyUuid } from '../../utils/yearlyRental'
import { providerService } from '../../services/providerService'

const outcomes = { FIXED: 'Đã sửa xong, tiếp tục thuê', REIMBURSE: 'Hoàn trực tiếp chi phí bảo trì', END_PROVIDER: 'Kết thúc do lỗi phía nhà cung cấp', REPLACE: 'Đổi xe', RENTER_PAID: 'Người thuê đã tự thanh toán' }
const statuses = { OPEN: 'Chờ xử lý', AWAITING_TRANSFER: 'Chờ hoàn tiền', TRANSFER_RECORDED: 'Đã chuyển tiền, chờ xác nhận', AWAITING_CONFIRMATION: 'Chờ người thuê xác nhận', CLOSED: 'Đã giải quyết' }
export default function YearlyIncidentPanel({ role, view, busy, run, act, upload, evidence, reviewOpen = false }) {
  const [vehicles, setVehicles] = useState([])
  const [requestId, setRequestId] = useState(yearlyUuid)
  const incidents = view.incidents || []
  const replacements = view.replacements || []
  const num = (d, k) => Number(d.get(k))
  const fileId = async (d, k = 'file') => (await upload(d.get(k))).id
  return <section className="space-y-4 rounded-xl border bg-white p-5">
    <h3 className="text-lg font-bold">Sự cố và đổi xe</h3>
    <p className="text-sm text-slate-600">Chi phí sửa chữa được hai bên xử lý trực tiếp và lưu bằng chứng; không tự trừ vào tiền thuê tháng sau.</p>
    {!reviewOpen && role === 'renter' && view.stage === 'ACTIVE' && !incidents.some(i => i.status !== 'CLOSED') && <ActionForm title="Báo sự cố" busy={busy} fields={[
      { name: 'category', label: 'Loại sự cố', type: 'select', options: [['OWNER_MAINTENANCE', 'Hao mòn / lỗi phía nhà cung cấp'], ['RENTER_DAMAGE', 'Hư hỏng do người thuê']] },
      { name: 'occurredOn', label: 'Ngày xảy ra', type: 'date' }, { name: 'description', label: 'Mô tả', type: 'textarea' },
      { name: 'invoiceAmount', label: 'Chi phí theo hóa đơn (đ)', type: 'number', value: 0 }, { name: 'file', label: 'Ảnh sự cố / hóa đơn', type: 'file' },
    ]} onSubmit={d => run(async () => {
      await act('incidents', { requestId, category: d.get('category'), occurredOn: d.get('occurredOn'), description: d.get('description'), invoiceAmount: num(d, 'invoiceAmount'), evidenceId: await fileId(d) })
      setRequestId(yearlyUuid())
    })} />}
    {incidents.map(i => <article key={i.id} className="space-y-3 rounded-xl border p-4">
      <h4 className="font-semibold">{i.category === 'OWNER_MAINTENANCE' ? 'Hao mòn / lỗi phía nhà cung cấp' : 'Hư hỏng do người thuê'} · {yearlyDate(i.occurredOn)}</h4>
      <p className="whitespace-pre-wrap text-sm">{i.description}</p>
      <p className="text-sm">{statuses[i.status] || i.status} · Hóa đơn: {yearlyMoney(i.invoiceAmount)}</p>
      {evidence(i.evidenceId)}{i.outcome && <p className="text-sm">{outcomes[i.outcome]} · {i.resolutionNote}</p>}
      {!reviewOpen && role === 'provider' && i.status === 'OPEN' && <ActionForm title="Kết luận sự cố" busy={busy} confirm fields={[
        { name: 'outcome', label: 'Phương án', type: 'select', options: (i.category === 'OWNER_MAINTENANCE' ? ['FIXED', 'REIMBURSE', 'END_PROVIDER', 'REPLACE'] : ['FIXED', 'RENTER_PAID']).map(k => [k, outcomes[k]]) },
        { name: 'approvedAmount', label: 'Số tiền hoàn trực tiếp, chỉ áp dụng khi hoàn bảo trì (đ)', type: 'number', value: 0 },
        { name: 'note', label: 'Kết luận và thỏa thuận', type: 'textarea' },
      ]} onSubmit={d => run(() => act('incidents/' + i.id + '/resolution', { version: i.version, outcome: d.get('outcome'), approvedAmount: num(d, 'approvedAmount'), note: d.get('note') }))} />}
      {!reviewOpen && role === 'provider' && i.status === 'AWAITING_TRANSFER' && <ActionForm title={'Ghi nhận hoàn trực tiếp ' + yearlyMoney(i.approvedAmount)} busy={busy} confirm fields={[
        { name: 'bankReference', label: 'Mã chuyển khoản' }, { name: 'file', label: 'Biên lai hoàn tiền', type: 'file' },
      ]} onSubmit={d => run(async () => act('incidents/' + i.id + '/transfer', { version: i.version, bankReference: d.get('bankReference'), evidenceId: await fileId(d) }))} />}
      {evidence(i.transferEvidenceId)}
      {!reviewOpen && role === 'renter' && ['TRANSFER_RECORDED', 'AWAITING_CONFIRMATION'].includes(i.status) && i.outcome !== 'REPLACE' && <ActionForm title={i.status === 'TRANSFER_RECORDED' ? 'Xác nhận đã nhận tiền hoàn' : 'Xác nhận kết quả xử lý'} confirm busy={busy} onSubmit={() => run(() => act('incidents/' + i.id + '/confirm', { version: i.version }))} />}
      {!reviewOpen && role === 'provider' && i.outcome === 'REPLACE' && i.status !== 'CLOSED' && !replacements.some(r => r.incidentId === i.id && r.status !== 'CANCELLED') && <>
        <button type="button" disabled={busy} className="text-sm font-semibold text-emerald-800 underline" onClick={() => run(async () => {
          const result = await providerService.getVehicles({ page: 0, size: 100, status: 'APPROVED' })
          setVehicles(result.items || result.content || [])
        }, '', false)}>Tải danh sách xe thay thế</button>
        {!!vehicles.length && <ActionForm title="Đề nghị đổi xe cùng nhà cung cấp" busy={busy} fields={[
          { name: 'vehicleId', label: 'Xe thay thế', type: 'select', options: vehicles.filter(v => v.id !== view.contract.currentVehicleId).map(v => [v.id, [v.brand, v.model, v.licensePlate].filter(Boolean).join(' · ')]) },
          { name: 'plannedOn', label: 'Ngày bàn giao dự kiến', type: 'date' },
        ]} onSubmit={d => run(() => act('replacements', { incidentId: i.id, vehicleId: num(d, 'vehicleId'), plannedOn: d.get('plannedOn') }))} />}
      </>}
    </article>)}
    {replacements.map(r => <article key={r.id} className="space-y-3 rounded-xl border border-emerald-200 p-4">
      <h4 className="font-semibold">Đổi xe · dự kiến {yearlyDate(r.plannedOn)}</h4>
      <p className="text-sm">Xe cũ mã {r.oldVehicleId} → xe thay thế mã {r.newVehicleId}</p>
      <p className="text-sm">{({ PROPOSED: 'Chờ người thuê đồng ý', ACCEPTED: 'Đã đồng ý, chờ bàn giao', HANDOVER_PROPOSED: 'Chờ xác nhận nhận xe mới', COMPLETED: 'Đã đổi xe', CANCELLED: 'Đã hủy' })[r.status]}</p>
      {!reviewOpen && role === 'renter' && r.status === 'PROPOSED' && <ActionForm title="Đồng ý đổi xe, giữ nguyên giá, cọc và hạn hợp đồng" confirm busy={busy} onSubmit={() => run(() => act('replacements/' + r.id + '/accept', { version: r.version }))} />}
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
