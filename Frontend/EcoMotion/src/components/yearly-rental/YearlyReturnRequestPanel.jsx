import { useState } from 'react'
import { yearlyDate, yearlyToday, yearlyButtonClass } from '../../utils/yearlyRental'

export default function YearlyReturnRequestPanel({ view, busy, run, act, money, editing = false }) {
  const today = yearlyToday()
  const [requestedOn, setRequestedOn] = useState(view.requestedReturnOn && view.requestedReturnOn >= today ? view.requestedReturnOn : today)
  const beforeDue = requestedOn < view.contract.dueOn
  const [mode, setMode] = useState(view.returnMode === 'FULL_CONTRACT' ? 'FULL_CONTRACT' : beforeDue ? 'EARLY' : 'STANDARD')
  const [quote, setQuote] = useState(null)
  const [agreed, setAgreed] = useState(false)
  const options = beforeDue
    ? [['EARLY', 'Kết thúc sớm theo phí hợp đồng'], ['FULL_CONTRACT', 'Trả xe và thanh toán đủ tiền thuê cả năm']]
    : [['STANDARD', 'Trả theo hạn hợp đồng'], ['FULL_CONTRACT', 'Thanh toán đủ tiền thuê cả năm']]
  return <section className="space-y-4 rounded-xl border bg-white p-5">
    <h3 className="text-lg font-bold">Yêu cầu trả xe và kết thúc thuê</h3>
    <p className="text-sm">Hạn hợp đồng: {yearlyDate(view.contract.dueOn)}. Bảng dưới ước tính nếu trả ngày {yearlyDate(requestedOn)}; ngày trả thực tế do hai bên xác nhận khi bàn giao lại.</p>
    <label className="block text-sm font-semibold">Ngày dự kiến trả xe<input type="date" min={today} value={requestedOn} disabled={busy} className="mt-2 block w-full rounded-lg border p-3" onChange={e => { setRequestedOn(e.target.value); setMode(e.target.value < view.contract.dueOn ? 'EARLY' : 'STANDARD'); setQuote(null); setAgreed(false) }} /></label>
    <label className="block text-sm font-semibold">Hình thức trả
      <select className="mt-2 block w-full rounded-lg border p-3" disabled={busy} value={mode} onChange={e => { setMode(e.target.value); setQuote(null); setAgreed(false) }}>
        {options.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
    <button type="button" disabled={busy} className={yearlyButtonClass} onClick={() => run(async () => {
      const result = await act('returns/preview', { mode, returnedOn: requestedOn })
      setQuote({ ...result, mode, day: requestedOn }); setAgreed(false)
    }, '', false)}>Xem phí và số tiền dự kiến</button>
    <div className="space-y-4">
      {quote?.mode === mode && quote.day === requestedOn ? money(quote.money) : <p className="rounded-lg bg-slate-50 p-3 text-sm">Chưa có ước tính tiền. Bạn vẫn có thể gửi ngày trả và hình thức đã chọn; số tiền chính thức được hai bên chốt sau kiểm định. Trả sớm giữ nguyên tiền các tháng đã bắt đầu, cộng phí tối đa một tháng trung bình làm tròn và không vượt tiền thuê còn lại.</p>}
      <p className="rounded-lg bg-amber-50 p-3 text-sm">Đây là ước tính, chưa gồm km vượt và phụ phí kiểm định. Gửi yêu cầu chưa đóng hợp đồng, chưa xác nhận đã trả xe hoặc đã hoàn cọc.</p>
      <label className="flex gap-2 text-sm"><input type="checkbox" checked={agreed} disabled={busy} onChange={e => setAgreed(e.target.checked)} />Tôi đã xem chính sách và muốn gửi yêu cầu trả xe theo hình thức này.</label>
      <button type="button" disabled={busy || !agreed} className={yearlyButtonClass} onClick={() => run(
        () => act('returns', { version: view.version, mode, requestedOn }),
        'Đã gửi yêu cầu trả xe. Chờ nhà cung cấp nhận lại xe, kiểm định và gửi biên bản để bạn xác nhận.'
      )}>{busy ? 'Đang gửi yêu cầu…' : editing ? 'Cập nhật yêu cầu trả xe' : 'Gửi yêu cầu trả xe'}</button>
    </div>
  </section>
}
