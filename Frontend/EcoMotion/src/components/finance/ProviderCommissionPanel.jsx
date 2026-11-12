import { useCallback, useEffect, useState } from 'react'
import { commissionService } from '../../services/commissionService'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { canPayCommission, commissionAlert, commissionPaymentState, dueText, periodLabel } from '../../utils/commissionDisplay'
import CommissionPaymentHistory from './CommissionPaymentHistory'

function StatusChip({ statement }) {
  const meta = commissionPaymentState(statement, formatCurrency)
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
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
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

  const openPay = (statement) => {
    setPaying(statement)
    setAmount('')
    setReference(''); setNote(''); setFile(null); setPreview(null)
  }

  const pickFile = (event) => {
    const next = event.target.files?.[0] || null
    if (next && (!['image/jpeg', 'image/png', 'image/webp'].includes(next.type) || next.size > 5 * 1024 * 1024)) {
      onToast?.('Ảnh biên lai phải là JPG, PNG hoặc WebP, tối đa 5 MB', 'error')
      event.target.value = ''
      return
    }
    if (preview) URL.revokeObjectURL(preview)
    setFile(next)
    setPreview(next ? URL.createObjectURL(next) : null)
  }

  const reportPaid = async (event) => {
    event.preventDefault()
    if (busy || !paying) return
    setBusy(true)
    try {
      await commissionService.reportPayment(paying.id, {
        amount: Number(amount) || undefined, paymentReference: reference.trim() || undefined, note: note.trim() || undefined, file,
      })
      onToast?.('Đã báo chuyển khoản hoa hồng. Admin sẽ đối chiếu và xác nhận.', 'success')
      setPaying(null); setReference(''); setNote(''); setFile(null)
      if (preview) URL.revokeObjectURL(preview)
      setPreview(null)
      await load()
    } catch (err) {
      onToast?.(err.message || 'Chưa gửi được', 'error')
    } finally { setBusy(false) }
  }

  if (error) return <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</p>
  if (!data) return <p className="p-6 text-sm text-slate-500">Đang tải hoa hồng...</p>
  const current = data.currentPeriod
  const notice = commissionAlert(data, formatCurrency, formatBookingWhen)
  const alertCls = {
    blocked: 'border-rose-300 bg-rose-50 text-rose-900',
    overdue: 'border-amber-300 bg-amber-50 text-amber-900',
    due: 'border-sky-200 bg-sky-50 text-sky-900',
    reported: 'border-slate-200 bg-slate-50 text-slate-800',
  }

  return (
    <div className="space-y-4">
      {notice && (
        <div role={notice.tone === 'blocked' ? 'alert' : 'status'} className={`rounded-xl border p-4 text-sm ${alertCls[notice.tone]}`}>
          <strong className="block">{notice.title}</strong>
          {notice.text}{notice.tone === 'blocked' ? ' Đơn đang chạy không bị ảnh hưởng.' : ''}
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
          {Number(data.creditBalance) > 0 && (
            <p className="mt-2 rounded-lg bg-teal-50 px-2 py-1.5 text-xs font-semibold text-teal-800">
              Số dư nộp thừa {formatCurrency(data.creditBalance)} — tự trừ vào bảng kê kỳ sau.
            </p>
          )}
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
                    <td className="px-4"><StatusChip statement={s} /></td>
                    <td className="px-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button type="button" onClick={() => openDetail(s)} className="rounded-lg border border-slate-200 px-2.5 py-1 font-semibold text-slate-700 hover:bg-slate-50">Chi tiết</button>
                        {canPayCommission(s) && (
                          <button type="button" onClick={() => openPay(s)} className="rounded-lg bg-brand-700 px-2.5 py-1 font-bold text-white hover:bg-brand-800">Nộp hoa hồng</button>
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-3 sm:p-4" onClick={() => !busy && setPaying(null)}>
          <form role="dialog" aria-modal="true" aria-label="Nộp hoa hồng" onClick={(e) => e.stopPropagation()} onSubmit={reportPaid}
            className="flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">Nộp hoa hồng {periodLabel(paying.periodMonth)}</h3>
                <p className="text-xs text-slate-500">Quét mã QR bằng app ngân hàng — số tiền và nội dung đã điền sẵn.</p>
              </div>
              <button type="button" disabled={busy} onClick={() => setPaying(null)} aria-label="Đóng"
                className="rounded-lg px-2 py-1 text-lg leading-none text-slate-400 hover:bg-slate-100 hover:text-slate-700">✕</button>
            </div>

            <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
              <div className="grid gap-4 sm:grid-cols-[200px_1fr] sm:items-center">
                {paying.qrCodeUrl && (
                  <img src={paying.qrCodeUrl} alt="Mã VietQR nộp hoa hồng"
                    className="mx-auto h-48 w-48 rounded-xl border border-slate-200 bg-white object-contain p-1" />
                )}
                <div className="space-y-3">
                  <div className="rounded-xl bg-brand-50 px-4 py-3">
                    <p className="text-[11px] font-semibold uppercase text-brand-800">Còn phải nộp</p>
                    <p className="font-mono text-2xl font-extrabold text-brand-900">{formatCurrency(paying.remainingAmount)}</p>
                  </div>
                  <dl className="space-y-1.5 text-xs">
                    {[
                      ['Ngân hàng', data.platformBank.bankName, false],
                      ['Số tài khoản', data.platformBank.accountNumber, true],
                      ['Chủ tài khoản', data.platformBank.accountHolder, false],
                      ['Nội dung', paying.transferContent, true],
                    ].map(([label, value, copyable]) => (
                      <div key={label} className="flex items-center justify-between gap-2">
                        <dt className="text-slate-500">{label}</dt>
                        <dd className="flex items-center gap-1.5 text-right font-semibold text-slate-900">
                          <span className={copyable ? 'font-mono' : ''}>{value}</span>
                          {copyable && (
                            <button type="button" onClick={() => { navigator.clipboard?.writeText(String(value)); onToast?.(`Đã sao chép ${label.toLowerCase()}`, 'success') }}
                              className="rounded border border-slate-200 px-1.5 py-0.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">Sao chép</button>
                          )}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </div>
              </div>

              <details className="group rounded-xl border border-slate-200 bg-slate-50/60 px-4 py-2.5">
                <summary className="flex cursor-pointer select-none list-none items-center justify-between gap-3 text-xs font-semibold text-slate-700">
                  <span>Chuyển số tiền khác, đính kèm biên lai hoặc mã giao dịch</span>
                  <span className="shrink-0 text-brand-700 group-open:hidden">Xem thêm ▾</span>
                  <span className="hidden shrink-0 text-brand-700 group-open:inline">Thu gọn ▴</span>
                </summary>
                <div className="mt-3 space-y-3">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block text-xs font-semibold text-slate-700">Số tiền bạn đã chuyển
                      <input inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value.replace(/\D/g, ''))}
                        placeholder={`Để trống = ${formatCurrency(paying.remainingAmount)}`}
                        className="mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 font-semibold placeholder:font-normal" />
                    </label>
                    <label className="block text-xs font-semibold text-slate-700">Mã giao dịch ngân hàng
                      <input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="FT24..."
                        className="mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3" />
                    </label>
                  </div>
                  {Number(amount) > 0 && Number(amount) < Number(paying.remainingAmount) && (
                    <p className="text-[11px] text-amber-700">Chuyển thiếu {formatCurrency(Number(paying.remainingAmount) - Number(amount))}: bảng kê sẽ còn nợ phần chênh.</p>
                  )}
                  {Number(amount) > Number(paying.remainingAmount) && (
                    <p className="text-[11px] text-teal-700">Chuyển dư {formatCurrency(Number(amount) - Number(paying.remainingAmount))}: phần dư được trừ vào bảng kê kỳ sau.</p>
                  )}
                  <div className="flex items-center gap-3">
                    <label className="inline-flex h-10 shrink-0 cursor-pointer items-center rounded-lg border border-dashed border-slate-400 bg-white px-3 text-xs font-semibold text-slate-700 hover:border-brand-600 hover:text-brand-700">
                      {file ? 'Đổi ảnh biên lai' : 'Chọn ảnh biên lai'}
                      <input type="file" accept="image/jpeg,image/png,image/webp" onChange={pickFile} className="sr-only" />
                    </label>
                    {preview ? (
                      <div className="flex min-w-0 items-center gap-2">
                        <img src={preview} alt="Ảnh biên lai đã chọn" className="h-12 w-12 shrink-0 rounded-md border border-slate-200 object-cover" />
                        <span className="truncate text-[11px] text-slate-600">{file?.name}</span>
                        <button type="button" onClick={() => { URL.revokeObjectURL(preview); setPreview(null); setFile(null) }}
                          className="shrink-0 text-[11px] font-semibold text-rose-700 hover:underline">Bỏ</button>
                      </div>
                    ) : (
                      <span className="text-[11px] text-slate-500">JPG, PNG, WebP · tối đa 5 MB</span>
                    )}
                  </div>
                  <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Ghi chú cho admin (tuỳ chọn)"
                    className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-xs" />
                </div>
              </details>
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-slate-100 bg-white px-5 py-3">
              <button type="button" disabled={busy} onClick={() => setPaying(null)} className="h-10 rounded-lg border border-slate-300 px-4 text-xs font-semibold text-slate-700">Đóng</button>
              <button type="submit" disabled={busy} className="h-10 rounded-lg bg-brand-700 px-5 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50">{busy ? 'Đang gửi...' : 'Tôi đã chuyển khoản'}</button>
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
              <StatusChip statement={detail} />
            </div>
            <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
              {[['Hoa hồng', detail.commissionAmount], ['Đã nộp', detail.paidAmount], ['Cấn trừ', detail.creditApplied], ['Còn phải nộp', detail.remainingAmount]].map(([label, value]) => (
                <div key={label} className="rounded-lg bg-slate-50 p-2"><dt className="text-slate-500">{label}</dt><dd className="font-mono font-bold text-slate-900">{formatCurrency(value)}</dd></div>
              ))}
            </dl>
            {detail.adminNote && <p className="rounded-lg bg-slate-50 p-2 text-xs text-slate-700">Ghi chú admin: {detail.adminNote}</p>}
            <h4 className="text-xs font-bold uppercase text-slate-500">Các lần nộp</h4>
            <CommissionPaymentHistory payments={detail.payments} loadProof={(paymentId, dispute) => commissionService.providerProof(detail.id, paymentId, dispute)}
              onDispute={async (paymentId, claimed, reason, file) => {
                setDetail(await commissionService.disputePayment(detail.id, paymentId, claimed, reason, file))
                onToast?.('Đã gửi báo sai số tiền. Admin sẽ đối chiếu sao kê và trả lời.', 'success')
                await load()
              }} />
            <h4 className="text-xs font-bold uppercase text-slate-500">Các chuyến trong kỳ</h4>
            <Lines lines={detail.lines} />
            <div className="text-right"><button type="button" onClick={() => setDetail(null)} className="h-9 rounded-lg border border-slate-300 px-4 text-xs font-semibold">Đóng</button></div>
          </div>
        </div>
      )}
    </div>
  )
}
