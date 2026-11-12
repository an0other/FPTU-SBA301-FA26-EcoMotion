import { useEffect, useState } from 'react'
import apiClient, { readErrorMessage } from '../../services/apiClient'
import { yearlyToday, yearlyDate, yearlyMoney, yearlyButtonClass } from '../../utils/yearlyRental'
import YearlyVehicleCard from './YearlyVehicleCard'

export default function YearlyReplacementPicker({ code, incident, dueOn, busy, run, act }) {
  const [day, setDay] = useState(yearlyToday)
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState(null)
  const [vehicleId, setVehicleId] = useState('')
  const key = day + '/' + revision
  useEffect(() => {
    if (!day || day < yearlyToday() || day >= dueOn) return
    let active = true
    apiClient.get('/provider/yearly-bookings/' + encodeURIComponent(code) + '/replacement-options', { params: { incidentId: incident.id, plannedOn: day } })
      .then(response => { if (active) setResult({ key, options: response.data.data || [] }) })
      .catch(error => { if (active) setResult({ key, error: readErrorMessage(error), options: [] }) })
    return () => { active = false }
  }, [code, incident.id, day, dueOn, key])
  const valid = day && day >= yearlyToday() && day < dueOn
  const ready = valid && result?.key === key
  const options = ready ? result.options : []
  const selected = options.find(o => String(o.vehicle.id) === vehicleId)
  async function submit(event) {
    event.preventDefault()
    if (busy || !selected) return
    const ok = await run(() => act('replacements', { incidentId: incident.id, vehicleId: selected.vehicle.id, plannedOn: day }),
      'Đã gửi đề nghị đổi xe. Chờ người thuê xem thông tin xe và đồng ý.')
    if (!ok) { setVehicleId(''); setRevision(v => v + 1) }
  }
  return <form onSubmit={submit} className="space-y-4 rounded-xl border border-emerald-200 p-4">
    <h4 className="font-bold">Chọn xe thay thế còn trống</h4>
    <label className="block text-sm">Ngày bàn giao dự kiến
      <input type="date" required min={yearlyToday()} value={day} disabled={busy} onChange={e => { setDay(e.target.value); setVehicleId('') }} className="mt-2 block rounded-lg border p-2" />
    </label>
    <p className="text-sm">Chỉ hiện xe cùng nhà cung cấp, đủ điều kiện và trống từ ngày đổi đến hết hợp đồng {yearlyDate(dueOn)}, có tính thời gian đệm.</p>
    {!valid && <p role="alert" className="text-sm text-red-700">Chọn ngày từ hôm nay đến trước ngày hết hợp đồng.</p>}
    {valid && !ready && <p role="status">Đang kiểm tra lịch xe…</p>}
    {ready && result.error && <p role="alert" className="text-sm text-red-700">{result.error}</p>}
    {ready && !result.error && !options.length && <p role="status" className="rounded-lg bg-amber-50 p-3">Không có xe phù hợp còn trống cho khoảng này. Chọn ngày khác hoặc cập nhật lại lịch.</p>}
    <div className="grid gap-3 md:grid-cols-2">{options.map(option => <label key={option.vehicle.id} className={'cursor-pointer rounded-xl border-2 p-2 ' + (vehicleId === String(option.vehicle.id) ? 'border-emerald-600' : 'border-transparent')}>
      <input type="radio" name="replacementVehicle" required disabled={busy} value={option.vehicle.id} checked={vehicleId === String(option.vehicle.id)} onChange={e => setVehicleId(e.target.value)} /> Chọn xe này
      <YearlyVehicleCard title="Xe có thể thay thế" vehicle={option.vehicle} />
      <p className="mt-2 text-sm">Giá thuê năm tham chiếu: {yearlyMoney(option.annualPrice)}</p>
      {option.lowerQuality && <p className="text-sm text-amber-800">{option.qualityNotes} — cần người thuê đồng ý.</p>}
    </label>)}</div>
    <button type="button" disabled={busy} onClick={() => { setVehicleId(''); setRevision(v => v + 1) }} className="text-sm font-semibold underline">Cập nhật danh sách xe trống</button>
    {!!options.length && <><label className="flex gap-2 text-sm"><input type="checkbox" required disabled={busy} />Tôi đã kiểm tra xe và ngày bàn giao để gửi đề nghị.</label><button type="submit" disabled={busy || !selected} className={yearlyButtonClass}>Gửi đề nghị đổi xe cho người thuê</button></>}
  </form>
}
