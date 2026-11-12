import { useState } from 'react'
import YearlyReturnAssessment from './YearlyReturnAssessment'
import { yearlyButtonClass, yearlyMoney, yearlyUuid } from '../../utils/yearlyRental'

export default function YearlySettlementForm({ view, assessment, busy, run, act, upload, evidence }) {
  const [rows, setRows] = useState(() => assessment.charges.map(c => ({ ...c, key: yearlyUuid(), file: null })))
  const [note, setNote] = useState(view.money?.note || '')
  const [useDeposit, setUseDeposit] = useState(Number(view.money?.depositDeduction || 0) > 0)
  const [deduction, setDeduction] = useState(view.money?.depositDeduction || 0)
  const [preview, setPreview] = useState(null)
  const [prepared, setPrepared] = useState(null)
  const invalidate = () => { setPreview(null); setPrepared(null) }
  const update = (key, patch) => { setRows(old => old.map(r => r.key === key ? { ...r, ...patch } : r)); invalidate() }
  const total = rows.reduce((sum, row) => sum + Number(row.amount || 0), 0)
  async function calculate(event) {
    event.preventDefault()
    await run(async () => {
      const charges = []
      for (const row of rows) {
        const evidenceId = row.file ? (await upload(row.file)).id : row.evidenceId
        if (!evidenceId) throw new Error('Mỗi khoản bổ sung cần ảnh bằng chứng.')
        charges.push({ category: row.category, description: row.description, amount: Number(row.amount), evidenceId, incidentId: row.incidentId ? Number(row.incidentId) : null })
      }
      const body = { version: view.version, otherCharges: total, depositDeduction: useDeposit ? Number(deduction) : 0, note, evidenceId: null, charges }
      const result = await act('settlement/preview', body)
      setPrepared({ ...body, expectedRentPaid: result.money.rentPaid, expectedDepositHeld: result.money.depositHeld })
      setPreview(result)
    }, 'Đã tính bảng tiền. Kiểm tra từng khoản trước khi gửi cho người thuê.', false)
  }
  return <section className="space-y-4 rounded-xl border bg-white p-5">
    <h3 className="text-lg font-bold">Lập quyết toán từ bảng tính tự động</h3>
    <p className="text-sm">Chỉ bổ sung chi phí có bằng chứng. Không nhập lại phí km, trả trễ, phí trả sớm, phí giao đã thu hoặc khoản renter đã trả.</p>
    {Number(assessment.money.otherCharges) > 0 && !assessment.charges.length && <p className="rounded-lg bg-amber-50 p-3 text-sm">Quyết toán cũ có phụ phí tổng {yearlyMoney(assessment.money.otherCharges)} chưa tách khoản. Nếu lập lại, cần khai từng khoản và bằng chứng; bảng cũ không bị thay đổi cho đến khi gửi.</p>}
    <form onSubmit={calculate} className="space-y-4">
      {rows.map((row,index) => <fieldset key={row.key} disabled={busy} className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2"><legend className="px-2 font-semibold">Khoản bổ sung {index+1}</legend>
        <label className="text-sm">Loại<select required value={row.category} onChange={e => update(row.key,{category:e.target.value})} className="mt-1 block w-full rounded border p-2">{[['CLEANING','Vệ sinh'],['MISSING_ACCESSORY','Thiếu phụ kiện'],['UNPAID_DAMAGE','Hư hỏng chưa thanh toán'],['OTHER','Chi phí khác có thỏa thuận']].map(([v,l]) => <option key={v} value={v}>{l}</option>)}</select></label>
        <label className="text-sm">Số tiền (đ)<input type="number" required min="1" step="1" value={row.amount} onChange={e => update(row.key,{amount:e.target.value})} className="mt-1 block w-full rounded border p-2" /></label>
        <label className="text-sm sm:col-span-2">Mô tả căn cứ<textarea required maxLength={500} value={row.description} onChange={e => update(row.key,{description:e.target.value})} className="mt-1 block w-full rounded border p-2" /></label>
        <label className="text-sm">Ảnh / hóa đơn<input type="file" required={!row.evidenceId} accept="image/jpeg,image/png" onChange={e => update(row.key,{file:e.target.files[0]})} className="mt-1 block w-full" />{row.evidenceId && evidence(row.evidenceId)}</label>
        <label className="text-sm">Report liên quan (nếu có)<select value={row.incidentId || ''} onChange={e => update(row.key,{incidentId:e.target.value})} className="mt-1 block w-full rounded border p-2"><option value="">Không gắn report</option>{view.incidents.filter(i => i.category === 'RENTER_DAMAGE' && i.outcome !== 'RENTER_PAID').map(i => <option key={i.id} value={i.id}>#{i.id} · {i.description}</option>)}</select></label>
        <button type="button" className="text-left text-sm text-red-700 underline" onClick={() => { setRows(old => old.filter(r => r.key !== row.key)); invalidate() }}>Bỏ khoản này</button>
      </fieldset>)}
      <button type="button" disabled={busy || rows.length >= 20} className="text-sm font-semibold underline" onClick={() => { setRows(old => [...old,{key:yearlyUuid(),category:'OTHER',description:'',amount:'',file:null}]); invalidate() }}>+ Thêm chi phí có bằng chứng</button>
      <p className="text-sm">Tổng bổ sung: <strong>{yearlyMoney(total)}</strong></p>
      <label className="block text-sm">Ghi chú quyết toán<textarea disabled={busy} required={rows.length > 0} maxLength={1000} value={note} onChange={e => {setNote(e.target.value);invalidate()}} className="mt-1 block w-full rounded border p-2" /></label>
      <label className="flex gap-2 text-sm"><input type="checkbox" disabled={busy} checked={useDeposit} onChange={e => {setUseDeposit(e.target.checked);invalidate()}} />Đề xuất dùng một phần cọc bù khoản thiếu (renter phải đồng ý)</label>
      {useDeposit && <label className="block text-sm">Số tiền đề xuất bù từ cọc<input type="number" disabled={busy} min="0" step="1" required value={deduction} onChange={e => {setDeduction(e.target.value);invalidate()}} className="mt-1 block rounded border p-2" /><span className="text-xs text-slate-500">BE giới hạn theo cọc thực nhận và khoản thiếu thực tế. Đây là cách thanh toán, không phải phụ phí mới.</span></label>}
      <button type="submit" disabled={busy} className={yearlyButtonClass}>Tính lại bảng quyết toán</button>
    </form>
    {preview && prepared && <div className="space-y-4">
      <YearlyReturnAssessment assessment={preview} evidence={evidence} />
      <p className="text-sm">Khấu trừ cọc tối đa theo khoản thiếu hiện tại: {yearlyMoney(preview.maxDepositDeduction)}.</p>
      <button type="button" disabled={busy} className={yearlyButtonClass} onClick={() => run(() => act('settlement', prepared), 'Đã gửi quyết toán chi tiết. Chờ người thuê xem và đồng ý.')}>Gửi bảng quyết toán này cho người thuê</button>
    </div>}
  </section>
}
