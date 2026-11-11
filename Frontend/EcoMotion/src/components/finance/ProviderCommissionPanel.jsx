import { useCallback, useEffect, useState } from 'react'
import { commissionService } from '../../services/commissionService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { canPayCommission, commissionStatus, dueText, periodLabel } from '../../utils/commissionDisplay'

function StatusChip({ status }) {
  const meta = commissionStatus(status)
  return <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${meta.cls}`}>{meta.label}</span>
}

function Lines({ lines }) {
  if (!lines?.length) return <p className="text-xs text-slate-500">Chưa có chuyến hoàn tất nào trong kỳ.</p>
  return (
    <table className="w-full text-xs">
      <thead className="text-[11px] uppercase text-slate-500">
        <tr><th className="py-1.5 text-left">Đơn</th><th className="text-left">Hoàn tất</th><th className="text-right">Doanh thu</th><th className="text-right">Hoa hồng</th></tr>
      </thead>
      <tbody className="divide-y divide-slate-100">
        {lines.map((line) => (
          <tr key={line.bookingId}>
            <td className="py-1.5 font-mono font-semibold text-brand-800">{line.bookingCode}</td>
            <td className="text-slate-600">{formatBookingWhen(line.completedAt)}</td>
            <td className="text-right font-mono">{formatCurrency(line.revenue)}</td>
            <td className="text-right font-mono font-semibold">{formatCurrency(line.commission)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default function ProviderCommissionPanel({ onToast }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [paying, setPaying] = useState(null)
  const [detail, setDetail] = useState(null)
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [showCurrentLines, setShowCurrentLines] = useState(false)

  const load = useCallback(async () => {
    try {
      setData(await commissionService.providerOverview())
      setError('')
    } catch (err) {
      setError(err.message || 'Không tải được thông tin hoa hồng')
    }
  }, [])

  useEffect(() => {
    const timer = setTimeout(load, 0)
    return () => clearTimeout(timer)
  }, [load])

  const openDetail = async (statement) => {
    try { setDetail(await commissionService.providerStatement(statement.id)) } catch (err) { onToast?.(err.message, 'error') }
  }

  const reportPaid = async (event) => {
    event.preventDefault()
    if (busy || !paying) return
    setBusy(true)
    try {
      await commissionService.reportPayment(paying.id, { paymentReference: reference.trim() || undefined })
      onToast?.('Đã báo chuyển khoản hoa hồng. Admin sẽ đối chiếu và xác nhận.', 'success')
      setPaying(null); setReference('')
      await load()
    } catch (err) {
      onToast?.(err.message || 'Chưa gửi được', 'error')
    } finally { setBusy(false) }
  }

  if (error) return <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</p>
  if (!data) return <p className="p-6 text-sm text-slate-500">Đang tải hoa hồng...</p>
  const current = data.currentPeriod

  return (
    <div className="space-y-4">
      {data.bookingBlocked && (
        <div role="alert" className="rounded-xl border border-rose-300 bg-rose-50 p-4 text-sm text-rose-900">
          <strong className="block">Đang tạm ngưng nhận đơn mới</strong>
          Có bảng kê hoa hồng quá hạn hơn {data.graceDays} ngày. Xe của bạn tạm ẩn với khách. Nộp hoa hồng và bấm「Tôi đã chuyển khoản」để mở lại trong lúc admin đối soát — nếu admin không thấy tiền, khoá lại ngay. Đơn đang chạy không bị ảnh hưởng.
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <section className="rounded-xl border border-slate-200 bg-white p-5 md:col-span-2">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="text-xs font-semibold uppercase text-slate-500">Kỳ đang chạy · {periodLabel(current.periodMonth)}</p>
              <p className="mt-1 text-2xl font-extrabold tabular-nums text-slate-900">{formatCurrency(current.commissionAmount)}</p>
              <p className="text-xs text-slate-500">
                Hoa hồng tạm tính {Math.round(Number(current.commissionRate) * 100)}% trên doanh thu {formatCurrency(current.grossRevenue)} · {current.bookingCount} chuyến
              </p>
            </div>
            <div className="text-right text-xs text-slate-600">
              <p>Chốt sổ: <strong>23:59 ngày cuối tháng</strong></p>
              <p>Hạn nộp: <strong>{formatBookingWhen(current.dueAt)}</strong></p>
            </div>
          </div>
          <button type="button" onClick={() => setShowCurrentLines((v) => !v)} className="mt-3 text-xs font-semibold text-brand-700 hover:underline">
            {showCurrentLines ? 'Ẩn danh sách chuyến' : 'Xem các chuyến được tính'}
          </button>
          {showCurrentLines && <div className="mt-2"><Lines lines={current.lines} /></div>}
        </section>
        <section className="rounded-xl border border-slate-200 bg-white p-5">
          <p className="text-xs font-semibold uppercase text-slate-500">Còn phải nộp</p>
          <p className={`mt-1 text-2xl font-extrabold tabular-nums ${Number(data.outstandingAmount) > 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
            {formatCurrency(data.outstandingAmount)}
          </p>
          <p className="mt-2 text-[11px] leading-relaxed text-slate-500">
            Hoa hồng tính trên tiền thực nhận của chuyến hoàn tất (đã trừ tiền hoàn, không tính phần khách chuyển dư). Đơn đang khiếu nại được dời sang kỳ sau.
          </p>
        </section>
      </div>

      <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <h3 className="border-b border-slate-100 px-5 py-3 text-sm font-bold text-slate-900">Bảng kê các tháng</h3>
        {data.statements.length === 0 ? (
          <p className="p-6 text-center text-sm text-slate-500">Chưa có bảng kê nào. Bảng kê được lập tự động lúc 00:05 ngày 1 hằng tháng.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-xs">
              <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
                <tr>
                  <th className="px-4 py-2.5 text-left">Kỳ</th><th className="px-4 text-right">Doanh thu</th><th className="px-4 text-right">Hoa hồng</th>
                  <th className="px-4 text-right">Đã nộp</th><th className="px-4 text-left">Hạn nộp</th><th className="px-4 text-left">Trạng thái</th><th className="px-4 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.statements.map((s) => (
                  <tr key={s.id} className={s.blocking ? 'bg-rose-50/50' : ''}>
                    <td className="px-4 py-3 font-semibold text-slate-800">{periodLabel(s.periodMonth)}<span className="block text-[11px] font-normal text-slate-500">{s.bookingCount} chuyến · {Math.round(Number(s.commissionRate) * 100)}%</span></td>
                    <td className="px-4 text-right font-mono">{formatCurrency(s.grossRevenue)}</td>
                    <td className="px-4 text-right font-mono font-bold">{formatCurrency(s.commissionAmount)}</td>
                    <td className="px-4 text-right font-mono">{formatCurrency(s.paidAmount)}</td>
                    <td className="px-4 text-slate-600">{formatBookingWhen(s.dueAt)}<span className={`block text-[11px] ${s.overdueDays > 0 ? 'font-semibold text-rose-700' : 'text-slate-500'}`}>{dueText(s)}</span></td>
                    <td className="px-4"><StatusChip status={s.status} /></td>
                    <td className="px-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button type="button" onClick={() => openDetail(s)} className="rounded-lg border border-slate-200 px-2.5 py-1 font-semibold text-slate-700 hover:bg-slate-50">Chi tiết</button>
                        {canPayCommission(s) && (
                          <button type="button" onClick={() => setPaying(s)} className="rounded-lg bg-brand-700 px-2.5 py-1 font-bold text-white hover:bg-brand-800">Nộp hoa hồng</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {paying && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={() => !busy && setPaying(null)}>
          <form role="dialog" aria-modal="true" aria-label="Nộp hoa hồng" onClick={(e) => e.stopPropagation()} onSubmit={reportPaid}
            className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900">Nộp hoa hồng {periodLabel(paying.periodMonth)}</h3>
            {paying.qrCodeUrl && <img src={paying.qrCodeUrl} alt="Mã VietQR nộp hoa hồng" className="mx-auto h-56 w-56 rounded-lg border border-slate-200 object-contain" />}
            <dl className="space-y-1.5 rounded-xl bg-slate-50 p-3 text-xs">
              <div className="flex justify-between"><dt className="text-slate-500">Số tiền</dt><dd className="font-mono font-bold">{formatCurrency(paying.remainingAmount)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Ngân hàng</dt><dd>{data.platformBank.bankName}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Số tài khoản</dt><dd className="font-mono">{data.platformBank.accountNumber}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Chủ tài khoản</dt><dd>{data.platformBank.accountHolder}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Nội dung</dt><dd className="font-mono font-bold">{paying.transferContent}</dd></div>
            </dl>
            <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng (tuỳ chọn)
              <input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="FT24..." className="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3" />
            </label>
            <div className="flex justify-end gap-2">
              <button type="button" disabled={busy} onClick={() => setPaying(null)} className="h-10 rounded-lg border border-slate-300 px-4 text-xs font-semibold text-slate-700">Đóng</button>
              <button type="submit" disabled={busy} className="h-10 rounded-lg bg-brand-700 px-4 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50">{busy ? 'Đang gửi...' : 'Tôi đã chuyển khoản'}</button>
            </div>
          </form>
        </div>
      )}

      {detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" onClick={() => setDetail(null)}>
          <div role="dialog" aria-modal="true" aria-label="Chi tiết bảng kê" onClick={(e) => e.stopPropagation()} className="max-h-[85vh] w-full max-w-2xl space-y-3 overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h3 className="text-base font-bold text-slate-900">Bảng kê {periodLabel(detail.periodMonth)}</h3>
                <p className="text-xs text-slate-500">{detail.bookingCount} chuyến · doanh thu {formatCurrency(detail.grossRevenue)} · hoa hồng {formatCurrency(detail.commissionAmount)}</p>
              </div>
              <StatusChip status={detail.status} />
            </div>
            {detail.adminNote && <p className="rounded-lg bg-slate-50 p-2 text-xs text-slate-700">Ghi chú admin: {detail.adminNote}</p>}
            <Lines lines={detail.lines} />
            <div className="text-right"><button type="button" onClick={() => setDetail(null)} className="h-9 rounded-lg border border-slate-300 px-4 text-xs font-semibold">Đóng</button></div>
          </div>
        </div>
      )}
    </div>
  )
}
