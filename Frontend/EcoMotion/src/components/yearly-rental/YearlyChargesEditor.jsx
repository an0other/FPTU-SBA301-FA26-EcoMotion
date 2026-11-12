export default function YearlyChargesEditor({ rows, onChange, evidence, incidents = [], busy }) {
  const update = (index, patch) => onChange(rows.map((r, i) => i === index ? { ...r, ...patch } : r))
  return <section className="space-y-3"><h4 className="font-semibold">Phụ phí Admin xác minh theo từng khoản</h4>
    {rows.map((row, index) => <fieldset key={index} disabled={busy} className="grid gap-3 rounded-lg border p-3 sm:grid-cols-2"><legend>Khoản {index + 1}</legend>
      <label>Loại<select className="block w-full rounded border p-2" value={row.category} onChange={e => update(index, { category: e.target.value })}>{[['CLEANING','Vệ sinh'],['MISSING_ACCESSORY','Thiếu phụ kiện'],['UNPAID_DAMAGE','Hư hỏng chưa trả'],['OTHER','Chi phí khác']].map(([v,l]) => <option key={v} value={v}>{l}</option>)}</select></label>
      <label>Số tiền (đ)<input className="block w-full rounded border p-2" type="number" min="1" step="1" required value={row.amount} onChange={e => update(index, { amount: e.target.value })} /></label>
      <label className="sm:col-span-2">Căn cứ giữ hoặc điều chỉnh khoản phí<textarea className="block w-full rounded border p-2" required maxLength={500} value={row.description} onChange={e => update(index, { description: e.target.value })} /></label>
      <label>Bằng chứng<select className="block w-full rounded border p-2" required value={row.evidenceId || ''} onChange={e => update(index, { evidenceId: Number(e.target.value) })}><option value="">Chọn bằng chứng</option>{evidence.map(e => <option key={e.id} value={e.id}>#{e.id} · {e.note || 'Ảnh hợp đồng'}</option>)}</select></label>
      <label>Report liên quan<select className="block w-full rounded border p-2" value={row.incidentId || ''} onChange={e => update(index, { incidentId: e.target.value ? Number(e.target.value) : null })}><option value="">Không gắn report</option>{incidents.filter(i => i.category === 'RENTER_DAMAGE' && i.outcome !== 'RENTER_PAID').map(i => <option key={i.id} value={i.id}>#{i.id} · {i.description}</option>)}</select></label>
      <button type="button" className="text-left text-red-700 underline" onClick={() => onChange(rows.filter((_, i) => i !== index))}>Loại khoản này khỏi phán quyết</button>
    </fieldset>)}
    <button type="button" disabled={busy || rows.length >= 20} className="underline" onClick={() => onChange([...rows, {category:'OTHER', amount:'', description:'', evidenceId:'', incidentId:null}])}>Thêm khoản có bằng chứng</button>
    <p className="text-sm">Tổng được tính từ từng khoản. Khoản đã tự thanh toán không được thu lại. Kết luận bên dưới cần giải thích cả các khoản bị loại.</p>
  </section>
}
