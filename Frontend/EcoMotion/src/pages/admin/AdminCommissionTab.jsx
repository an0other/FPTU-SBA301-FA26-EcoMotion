import { useCallback, useEffect, useState } from 'react'
import { commissionService } from '../../services/commissionService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { COMMISSION_STATUS, commissionPaymentState, periodLabel } from '../../utils/commissionDisplay'
import CommissionPaymentHistory from '../../components/finance/CommissionPaymentHistory'

function lastMonthKey() {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

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
  const [detail, setDetail] = useState(null)

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
    due: sum((r) => (r.status === 'PAID' ? 0 : r.remainingAmount)),
    paid: sum((r) => r.paidAmount),
    overdue: sum((r) => (r.status === 'OVERDUE' ? r.remainingAmount : 0)),
  }

  const generate = async () => {
    setBusy(true)
    try {
      const result = await commissionService.generate(generateMonth)
      setMessage({
        type: 'success',
        text: result.created > 0
          ? `Đã lập ${result.created} bảng kê mới cho ${periodLabel(result.month)}.`
          : `Không có bảng kê mới cho ${periodLabel(result.month)}: các nhà xe đã có bảng kê kỳ này hoặc không có chuyến hoàn tất trong kỳ.`,
      })
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

  // Luôn lấy bảng kê mới nhất từ server: nhà xe có thể vừa báo chuyển sau khi bảng được tải.
  const openConfirm = async (row) => {
    try {
      const fresh = await commissionService.adminDetail(row.id)
      if (fresh.status === 'PAID') {
        setMessage({ type: 'success', text: `${fresh.providerName} — ${periodLabel(fresh.periodMonth)} đã được xác nhận nộp đủ.` })
        await load()
        return
      }
      const latest = (fresh.payments || []).find((p) => p.status === 'REPORTED')
      setConfirming(fresh)
      setAmount(String(Math.round(Number(latest?.declaredAmount ?? fresh.remainingAmount))))
      setNote('')
      if (fresh.status !== row.status) await load()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không tải được bảng kê' })
    }
  }

  const openDetail = async (row) => {
    try {
      setDetail(await commissionService.adminDetail(row.id))
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không tải được chi tiết bảng kê' })
    }
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
          <button type="button" disabled={loading} onClick={load} className="h-9 rounded-lg border border-slate-300 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Làm mới</button>
        </div>
      </div>

      {message && <p role="status" className={`rounded-lg px-3 py-2 text-xs font-semibold ${message.type === 'error' ? 'bg-red-50 text-red-800' : 'bg-emerald-50 text-emerald-800'}`}>{message.text}</p>}

      <div className="grid gap-3 sm:grid-cols-3">
        {[['Còn phải thu', totals.due, 'text-slate-900'], ['Đã thu', totals.paid, 'text-emerald-700'], ['Quá hạn chưa thu', totals.overdue, 'text-rose-700']].map(([label, value, cls]) => (
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
              const meta = commissionPaymentState(r, formatCurrency)
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
                    {(r.payments || []).some((p) => p.disputeStatus === 'OPEN') && (
                      <span className="mt-1 inline-flex rounded-full border border-rose-200 bg-rose-50 px-2.5 py-0.5 text-[10px] font-bold text-rose-800">Nhà xe báo sai số tiền</span>
                    )}
                    {r.status === 'PAYMENT_REPORTED' && (() => {
                      const latest = (r.payments || []).find((p) => p.status === 'REPORTED')
                      return (
                        <span className="mt-1 block text-[11px] text-slate-500">
                          Khai đã chuyển {formatCurrency(latest?.declaredAmount)} · {formatBookingWhen(r.reportedAt)}
                          {r.paymentReference ? ` · Mã GD ${r.paymentReference}` : ''}{latest?.hasProof ? ' · có ảnh biên lai' : ''}
                        </span>
                      )
                    })()}
                  </td>
                  <td className="px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <button type="button" onClick={() => openDetail(r)} className="rounded-lg border border-slate-200 px-2.5 py-1 font-semibold text-slate-700 hover:bg-slate-50">Chi tiết</button>
                    {r.status !== 'PAID' && (
                      <>
                        {r.status === 'PAYMENT_REPORTED' && (
                          <button type="button" disabled={busy} onClick={() => reject(r)} className="rounded-lg border border-rose-200 px-2.5 py-1 font-semibold text-rose-700 hover:bg-rose-50">Chưa thấy tiền</button>
                        )}
                        <button type="button" disabled={busy} onClick={() => openConfirm(r)}
                          className="rounded-lg bg-emerald-700 px-2.5 py-1 font-bold text-white hover:bg-emerald-800">Xác nhận đã nhận</button>
                      </>
                    )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={() => { setDetail(null); load() }}>
          <div role="dialog" aria-modal="true" aria-label="Chi tiết bảng kê hoa hồng" onClick={(e) => e.stopPropagation()}
            className="max-h-[90vh] w-full max-w-2xl space-y-4 overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">{detail.providerName} — {periodLabel(detail.periodMonth)}</h3>
                <p className="text-xs text-slate-500">Hạn nộp {formatBookingWhen(detail.dueAt)} · tỷ lệ {Math.round(Number(detail.commissionRate) * 100)}% · nội dung CK <span className="font-mono font-bold">{detail.transferContent}</span></p>
              </div>
              <span className={`inline-flex shrink-0 rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${commissionPaymentState(detail, formatCurrency).cls}`}>{commissionPaymentState(detail, formatCurrency).label}</span>
            </div>
            <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
              {[['Hoa hồng', detail.commissionAmount], ['Đã nhận', detail.paidAmount], [Number(detail.overpaidAmount) > 0 ? 'Nộp dư' : 'Cấn trừ', Number(detail.overpaidAmount) > 0 ? detail.overpaidAmount : detail.creditApplied], ['Còn phải nộp', detail.remainingAmount]].map(([label, value]) => (
                <div key={label} className="rounded-lg bg-slate-50 p-3"><dt className="text-slate-500">{label}</dt><dd className="mt-1 font-mono text-sm font-bold text-slate-900">{formatCurrency(value)}</dd></div>
              ))}
            </dl>
            <h4 className="text-xs font-bold uppercase text-slate-500">Các lần nộp</h4>
            <CommissionPaymentHistory payments={detail.payments} loadProof={(paymentId, dispute) => commissionService.adminProof(detail.id, paymentId, dispute)} expandLatest
              onResolve={async (paymentId, accept, reason) => {
                setDetail(await commissionService.resolvePaymentDispute(detail.id, paymentId, accept, reason))
                setMessage({ type: 'success', text: accept ? 'Đã chấp nhận số tiền nhà xe khai.' : 'Đã giữ nguyên số đã xác nhận và gửi lý do cho nhà xe.' })
                await load()
              }} />
            <h4 className="text-xs font-bold uppercase text-slate-500">Các chuyến trong kỳ</h4>
            <div className="overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-xs">
                <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
                  <tr><th className="px-3 py-2 text-left">Đơn</th><th className="px-3 text-left">Hoàn tất</th><th className="px-3 text-right">Doanh thu</th><th className="px-3 text-right">Hoa hồng</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {(detail.lines || []).length === 0 ? (
                    <tr><td colSpan={4} className="py-6 text-center text-slate-400">Không có chuyến trong bảng kê.</td></tr>
                  ) : detail.lines.map((line) => (
                    <tr key={line.bookingId}>
                      <td className="px-3 py-2 font-mono font-semibold">{line.bookingCode}</td>
                      <td className="px-3">{formatBookingWhen(line.completedAt)}</td>
                      <td className="px-3 text-right font-mono">{formatCurrency(line.revenue)}</td>
                      <td className="px-3 text-right font-mono font-bold">{formatCurrency(line.commission)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex justify-end">
              <button type="button" onClick={() => { setDetail(null); load() }} className="h-9 rounded-lg border border-slate-300 px-4 text-xs font-semibold">Đóng</button>
            </div>
          </div>
        </div>
      )}

      {confirming && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={() => !busy && setConfirming(null)}>
          <form role="dialog" aria-modal="true" aria-label="Xác nhận đã nhận hoa hồng" onClick={(e) => e.stopPropagation()} onSubmit={submitConfirm}
            className="max-h-[90vh] w-full max-w-md space-y-4 overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900">Xác nhận hoa hồng — {confirming.providerName}</h3>
            <p className="text-xs text-slate-600">{periodLabel(confirming.periodMonth)} · còn phải nộp {formatCurrency(confirming.remainingAmount)} · nội dung CK <span className="font-mono font-bold">{confirming.transferContent}</span></p>
            {(confirming.payments || []).some((p) => p.status === 'REPORTED') ? (
              <CommissionPaymentHistory payments={(confirming.payments || []).filter((p) => p.status === 'REPORTED').slice(0, 1)}
                loadProof={(paymentId) => commissionService.adminProof(confirming.id, paymentId)} expandLatest />
            ) : (
              <p className="rounded-lg bg-slate-50 p-2 text-[11px] text-slate-600">Nhà xe chưa báo chuyển. Chỉ xác nhận khi đã thấy tiền trong sao kê.</p>
            )}
            <label className="block text-xs font-semibold text-slate-700">Số tiền thực nhận theo sao kê
              <input required inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))} className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm font-semibold" />
            </label>
            {Number(amount) > 0 && Number(amount) < Number(confirming.remainingAmount) && (
              <p className="text-[11px] text-amber-700">Nhận thiếu {formatCurrency(Number(confirming.remainingAmount) - Number(amount))}: bảng kê chuyển sang "Nộp thiếu" và vẫn còn nợ phần chênh.</p>
            )}
            {Number(amount) > Number(confirming.remainingAmount) && (
              <p className="text-[11px] text-teal-700">Nhận dư {formatCurrency(Number(amount) - Number(confirming.remainingAmount))}: bảng kê "Nộp dư", phần dư tự trừ vào bảng kê kỳ sau của nhà xe.</p>
            )}
            {(() => {
              const declared = Number((confirming.payments || []).find((p) => p.status === 'REPORTED')?.declaredAmount || 0)
              const less = declared > 0 && Number(amount) > 0 && Number(amount) < declared
              return (
                <label className="block text-xs font-semibold text-slate-700">
                  {less ? 'Lý do ghi nhận ít hơn số nhà xe khai (bắt buộc)' : 'Ghi chú (tuỳ chọn)'}
                  {less && <span className="mt-0.5 block font-normal text-rose-700">Nhà xe khai {formatCurrency(declared)}, bạn ghi nhận {formatCurrency(Number(amount))}. Nhà xe sẽ thấy lý do và có thể báo sai số tiền.</span>}
                  <textarea rows={2} required={less} value={note} onChange={(e) => setNote(e.target.value)}
                    placeholder={less ? 'Ví dụ: sao kê ngày 12/11 chỉ có giao dịch 300.000 với nội dung HH-2-202610' : ''}
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2" />
                </label>
              )
            })()}
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
