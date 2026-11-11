import { useCallback, useEffect, useState } from 'react'
import { commissionService } from '../../services/commissionService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { COMMISSION_STATUS, commissionStatus, periodLabel } from '../../utils/commissionDisplay'

function lastMonthKey() {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** Admin: bảng kê hoa hồng tháng của các nhà xe — lập bảng kê, xác nhận đã nhận tiền, theo dõi quá hạn. */
export default function AdminCommissionTab() {
  const [month, setMonth] = useState('')
  const [status, setStatus] = useState('ALL')
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState(null)
  const [confirming, setConfirming] = useState(null)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [generateMonth, setGenerateMonth] = useState(lastMonthKey())

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await commissionService.adminList({ month, status }))
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không tải được bảng kê' })
    } finally { setLoading(false) }
  }, [month, status])

  useEffect(() => {
    const timer = setTimeout(load, 0)
    return () => clearTimeout(timer)
  }, [load])

  const sum = (pick) => rows.reduce((total, r) => total + Number(pick(r) || 0), 0)
  const totals = {
    due: sum((r) => r.commissionAmount),
    paid: sum((r) => r.paidAmount),
    overdue: sum((r) => (r.status === 'OVERDUE' ? r.remainingAmount : 0)),
  }

  const generate = async () => {
    setBusy(true)
    try {
      const result = await commissionService.generate(generateMonth)
      setMessage({ type: 'success', text: `Đã lập ${result.created} bảng kê mới cho ${periodLabel(result.month)}.` })
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không lập được bảng kê' })
    } finally { setBusy(false) }
  }

  const submitConfirm = async (event) => {
    event.preventDefault()
    if (!confirming || busy) return
    setBusy(true)
    try {
      await commissionService.confirm(confirming.id, Number(amount), note.trim() || undefined)
      setMessage({ type: 'success', text: `Đã xác nhận hoa hồng ${confirming.providerName} — ${periodLabel(confirming.periodMonth)}.` })
      setConfirming(null)
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không xác nhận được' })
    } finally { setBusy(false) }
  }

  const reject = async (row) => {
    setBusy(true)
    try {
      await commissionService.rejectReport(row.id, 'Chưa thấy tiền trong sao kê')
      setMessage({ type: 'success', text: 'Đã trả bảng kê về trạng thái chưa nộp.' })
      await load()
    } catch (err) {
      setMessage({ type: 'error', text: err.message })
    } finally { setBusy(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Hoa hồng nhà xe</h2>
          <p className="text-xs text-slate-500">Kỳ = tháng dương lịch · lập bảng kê 00:05 ngày 1 · hạn nộp 17:00 ngày 5 · quá hạn 3 ngày tự khoá nhận đơn mới.</p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <label className="text-xs font-semibold text-slate-600">Lập bảng kê cho
            <input type="month" value={generateMonth} onChange={(e) => setGenerateMonth(e.target.value)} className="mt-1 block h-9 rounded-lg border border-slate-300 px-2 text-xs" />
          </label>
          <button type="button" disabled={busy} onClick={generate} className="h-9 rounded-lg bg-brand-700 px-3 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50">Lập bảng kê ngay</button>
        </div>
      </div>

      {message && <p role="status" className={`rounded-lg px-3 py-2 text-xs font-semibold ${message.type === 'error' ? 'bg-red-50 text-red-800' : 'bg-emerald-50 text-emerald-800'}`}>{message.text}</p>}

      <div className="grid gap-3 sm:grid-cols-3">
        {[['Phải thu', totals.due, 'text-slate-900'], ['Đã thu', totals.paid, 'text-emerald-700'], ['Quá hạn chưa thu', totals.overdue, 'text-rose-700']].map(([label, value, cls]) => (
          <div key={label} className="rounded-xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">{label}</p>
            <p className={`mt-1 text-xl font-extrabold tabular-nums ${cls}`}>{formatCurrency(value)}</p>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} className="h-9 rounded-lg border border-slate-300 px-2 text-xs" aria-label="Lọc theo tháng" />
        {month && <button type="button" onClick={() => setMonth('')} className="text-xs font-semibold text-slate-600 hover:underline">Mọi tháng</button>}
        {['ALL', ...Object.keys(COMMISSION_STATUS)].map((key) => (
          <button key={key} type="button" onClick={() => setStatus(key)}
            className={`rounded-full border px-3 py-1 text-xs font-semibold ${status === key ? 'border-brand-600 bg-brand-50 text-brand-800' : 'border-slate-200 bg-white text-slate-600'}`}>
            {key === 'ALL' ? 'Tất cả' : COMMISSION_STATUS[key].label}
          </button>
        ))}
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[900px] text-xs">
          <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
            <tr>
              <th className="px-4 py-2.5 text-left">Nhà xe</th><th className="px-4 text-left">Kỳ</th><th className="px-4 text-right">Doanh thu</th>
              <th className="px-4 text-right">Hoa hồng</th><th className="px-4 text-right">Đã nhận</th><th className="px-4 text-left">Hạn nộp</th>
              <th className="px-4 text-left">Trạng thái</th><th className="px-4 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr><td colSpan={8} className="py-10 text-center text-slate-500">Đang tải...</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={8} className="py-10 text-center text-slate-400">Chưa có bảng kê phù hợp.</td></tr>
            ) : rows.map((r) => {
              const meta = commissionStatus(r.status)
              return (
                <tr key={r.id} className={r.blocking ? 'bg-rose-50/60' : ''}>
                  <td className="px-4 py-3 font-semibold text-slate-800">{r.providerName}<span className="block text-[11px] font-normal text-slate-500">{r.bookingCount} chuyến · {Math.round(Number(r.commissionRate) * 100)}%</span></td>
                  <td className="px-4">{periodLabel(r.periodMonth)}</td>
                  <td className="px-4 text-right font-mono">{formatCurrency(r.grossRevenue)}</td>
                  <td className="px-4 text-right font-mono font-bold">{formatCurrency(r.commissionAmount)}</td>
                  <td className="px-4 text-right font-mono">{formatCurrency(r.paidAmount)}</td>
                  <td className="px-4 text-slate-600">{formatBookingWhen(r.dueAt)}{r.overdueDays > 0 && <span className="block font-semibold text-rose-700">Quá hạn {r.overdueDays} ngày{r.blocking ? ' · đang khoá nhận đơn' : ''}</span>}</td>
                  <td className="px-4">
                    <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${meta.cls}`}>{meta.label}</span>
                    {r.status === 'PAYMENT_REPORTED' && (
                      <span className="mt-1 block text-[11px] text-slate-500">Báo lúc {formatBookingWhen(r.reportedAt)}{r.paymentReference ? ` · Mã GD ${r.paymentReference}` : ''}</span>
                    )}
                  </td>
                  <td className="px-4 text-right">
                    {r.status !== 'PAID' && (
                      <div className="flex justify-end gap-2">
                        {r.status === 'PAYMENT_REPORTED' && (
                          <button type="button" disabled={busy} onClick={() => reject(r)} className="rounded-lg border border-rose-200 px-2.5 py-1 font-semibold text-rose-700 hover:bg-rose-50">Chưa thấy tiền</button>
                        )}
                        <button type="button" onClick={() => { setConfirming(r); setAmount(String(Math.round(Number(r.remainingAmount)))); setNote('') }}
                          className="rounded-lg bg-emerald-700 px-2.5 py-1 font-bold text-white hover:bg-emerald-800">Xác nhận đã nhận</button>
                      </div>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {confirming && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={() => !busy && setConfirming(null)}>
          <form role="dialog" aria-modal="true" aria-label="Xác nhận đã nhận hoa hồng" onClick={(e) => e.stopPropagation()} onSubmit={submitConfirm}
            className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900">Xác nhận hoa hồng — {confirming.providerName}</h3>
            <p className="text-xs text-slate-600">{periodLabel(confirming.periodMonth)} · còn phải nộp {formatCurrency(confirming.remainingAmount)} · nội dung CK <span className="font-mono font-bold">{confirming.transferContent}</span></p>
            <label className="block text-xs font-semibold text-slate-700">Số tiền thực nhận theo sao kê
              <input required inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))} className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold" />
            </label>
            {Number(amount) > 0 && Number(amount) < Number(confirming.remainingAmount) && (
              <p className="text-[11px] text-amber-700">Nhận thiếu {formatCurrency(Number(confirming.remainingAmount) - Number(amount))}: bảng kê vẫn còn nợ phần chênh.</p>
            )}
            <label className="block text-xs font-semibold text-slate-700">Ghi chú (tuỳ chọn)
              <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-300 p-2" />
            </label>
            <div className="flex justify-end gap-2">
              <button type="button" disabled={busy} onClick={() => setConfirming(null)} className="h-10 rounded-lg border border-slate-300 px-4 text-xs font-semibold">Huỷ</button>
              <button type="submit" disabled={busy || !Number(amount)} className="h-10 rounded-lg bg-emerald-700 px-4 text-xs font-bold text-white disabled:opacity-50">{busy ? 'Đang lưu...' : 'Xác nhận'}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}
