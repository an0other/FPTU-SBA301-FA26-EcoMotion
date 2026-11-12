import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { COMMISSION_PAYMENT_STATUS } from '../../utils/commissionDisplay'

function ProofImage({ paymentId, loadProof, dispute = false }) {
  const [state, setState] = useState({ url: null, error: false })
  const loader = useRef(loadProof)
  useEffect(() => {
    let live = true
    let objectUrl
    loader.current(paymentId, dispute).then((blob) => {
      if (!live) return
      objectUrl = URL.createObjectURL(blob)
      setState({ url: objectUrl, error: false })
    }).catch(() => { if (live) setState({ url: null, error: true }) })
    return () => { live = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [paymentId, dispute])
  const [zoomed, setZoomed] = useState(false)
  useEffect(() => {
    if (!zoomed) return undefined
    const onKey = (event) => { if (event.key === 'Escape') { event.stopPropagation(); setZoomed(false) } }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [zoomed])
  if (state.error) return <p role="alert" className="text-[11px] text-red-700">Không tải được ảnh biên lai.</p>
  if (!state.url) return <p role="status" className="text-[11px] text-slate-500">Đang tải ảnh…</p>
  return (
    <>
      <button type="button" onClick={() => setZoomed(true)} className="block w-fit text-left" aria-label="Mở ảnh biên lai">
        <img src={state.url} alt="Ảnh biên lai chuyển khoản hoa hồng" className="max-h-72 cursor-zoom-in rounded-lg border border-slate-200 bg-white object-contain" />
        <span className="text-[11px] font-semibold text-brand-700 underline">Mở ảnh đầy đủ</span>
      </button>
      {zoomed && createPortal(
        <div role="dialog" aria-modal="true" aria-label="Ảnh biên lai chuyển khoản hoa hồng"
          className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/80 p-4"
          onClick={(event) => { event.stopPropagation(); setZoomed(false) }}>
          <button type="button" onClick={(event) => { event.stopPropagation(); setZoomed(false) }}
            className="absolute right-4 top-4 rounded-full bg-white/90 px-3 py-1.5 text-sm font-bold text-slate-800 shadow hover:bg-white">
            Đóng ✕
          </button>
          <img src={state.url} alt="Ảnh biên lai chuyển khoản hoa hồng" onClick={(event) => event.stopPropagation()}
            className="max-h-[90vh] max-w-full rounded-xl bg-white object-contain shadow-2xl" />
        </div>,
        document.body,
      )}
    </>
  )
}

const DISPUTE_STATUS = {
  OPEN: { label: 'Nhà xe báo sai số tiền — chờ admin xem xét', cls: 'border-rose-200 bg-rose-50 text-rose-800' },
  ACCEPTED: { label: 'Admin đã chấp nhận số nhà xe khai', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  REJECTED: { label: 'Admin giữ nguyên số đã xác nhận', cls: 'border-slate-200 bg-slate-50 text-slate-700' },
}

function DisputeForm({ payment, onDispute }) {
  const [editing, setEditing] = useState(false)
  const [claimed, setClaimed] = useState(String(Math.round(Number(payment.declaredAmount || payment.confirmedAmount || 0))))
  const [reason, setReason] = useState('')
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])
  const pick = (event) => {
    const next = event.target.files?.[0] || null
    if (next && (!['image/jpeg', 'image/png', 'image/webp'].includes(next.type) || next.size > 5 * 1024 * 1024)) {
      setError('Ảnh minh chứng phải là JPG, PNG hoặc WebP, tối đa 5 MB.')
      event.target.value = ''
      return
    }
    setError('')
    setFile(next)
    setPreview(next ? URL.createObjectURL(next) : null)
  }
  if (!editing) {
    return (
      <button type="button" onClick={() => setEditing(true)}
        className="rounded-lg border border-rose-300 px-3 py-1.5 text-[11px] font-semibold text-rose-700 hover:bg-rose-50">
        Báo sai số tiền
      </button>
    )
  }
  const submit = async () => {
    if (Number(claimed) <= Number(payment.confirmedAmount)) { setError('Số bạn đã chuyển phải lớn hơn số admin đã xác nhận.'); return }
    if (!reason.trim()) { setError('Mô tả lý do, ví dụ sao kê bên bạn ghi đã chuyển bao nhiêu.'); return }
    if (!file) { setError('Đính kèm ảnh biên lai hoặc sao kê chứng minh số tiền đã chuyển.'); return }
    setBusy(true); setError('')
    try { await onDispute(payment.id, Number(claimed), reason.trim(), file); setEditing(false) } catch (err) { setError(err.message || 'Chưa gửi được') } finally { setBusy(false) }
  }
  return (
    <div className="space-y-2 rounded-lg border border-rose-200 bg-rose-50/60 p-3">
      <p className="font-semibold text-rose-900">Báo sai số tiền admin xác nhận</p>
      <label className="block text-[11px] font-semibold text-slate-700">Số tiền bạn thực chuyển
        <input inputMode="numeric" value={claimed} onChange={(e) => setClaimed(e.target.value.replace(/\D/g, ''))}
          className="mt-1 h-9 w-full rounded-lg border border-slate-300 bg-white px-3 font-semibold" />
      </label>
      <textarea rows={2} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500}
        placeholder="Lý do (bắt buộc): ví dụ sao kê Vietcombank ghi đã chuyển 1.000.000 lúc 07:50" className="w-full rounded-lg border border-slate-300 bg-white p-2" />
      <div className="flex items-center gap-3">
        <label className="inline-flex h-9 shrink-0 cursor-pointer items-center rounded-lg border border-dashed border-rose-400 bg-white px-3 text-[11px] font-semibold text-rose-800 hover:border-rose-600">
          {file ? 'Đổi ảnh minh chứng' : 'Chọn ảnh minh chứng (bắt buộc)'}
          <input type="file" accept="image/jpeg,image/png,image/webp" onChange={pick} className="sr-only" />
        </label>
        {preview ? (
          <div className="flex min-w-0 items-center gap-2">
            <img src={preview} alt="Ảnh minh chứng đã chọn" className="h-12 w-12 shrink-0 rounded-md border border-slate-200 object-cover" />
            <span className="truncate text-[11px] text-slate-600">{file?.name}</span>
            <button type="button" onClick={() => { setFile(null); setPreview(null) }} className="shrink-0 text-[11px] font-semibold text-rose-700 hover:underline">Bỏ</button>
          </div>
        ) : (
          <span className="text-[11px] text-slate-500">Biên lai / sao kê · JPG, PNG, WebP ≤ 5 MB</span>
        )}
      </div>
      {error && <p role="alert" className="text-[11px] text-red-700">{error}</p>}
      <div className="flex justify-end gap-2">
        <button type="button" disabled={busy} onClick={() => setEditing(false)} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">Huỷ</button>
        <button type="button" disabled={busy} onClick={submit} className="rounded-lg bg-rose-700 px-3 py-1.5 font-bold text-white disabled:opacity-50">{busy ? 'Đang gửi...' : 'Gửi báo sai'}</button>
      </div>
    </div>
  )
}

function ResolveForm({ payment, onResolve }) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const decide = async (accept) => {
    if (!accept && !note.trim()) { setError('Giữ nguyên cần ghi lý do để nhà xe đối chiếu.'); return }
    setBusy(true); setError('')
    try { await onResolve(payment.id, accept, note.trim()) } catch (err) { setError(err.message || 'Chưa lưu được') } finally { setBusy(false) }
  }
  return (
    <div className="space-y-2 rounded-lg border border-rose-200 bg-rose-50/60 p-3">
      <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} maxLength={500}
        placeholder="Kết quả đối chiếu sao kê (bắt buộc nếu giữ nguyên)" className="w-full rounded-lg border border-slate-300 bg-white p-2" />
      {error && <p role="alert" className="text-[11px] text-red-700">{error}</p>}
      <div className="flex flex-wrap justify-end gap-2">
        <button type="button" disabled={busy} onClick={() => decide(false)} className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 font-semibold">Giữ nguyên {formatCurrency(payment.confirmedAmount)}</button>
        <button type="button" disabled={busy} onClick={() => decide(true)} className="rounded-lg bg-emerald-700 px-3 py-1.5 font-bold text-white disabled:opacity-50">Chấp nhận {formatCurrency(payment.claimedAmount)}</button>
      </div>
    </div>
  )
}

function PaymentRow({ payment, loadProof, defaultOpen, onDispute, onResolve }) {
  const [open, setOpen] = useState(defaultOpen || payment.disputeStatus === 'OPEN')
  const dispute = payment.disputeStatus ? DISPUTE_STATUS[payment.disputeStatus] : null
  const shortfall = payment.status === 'CONFIRMED' && payment.declaredAmount != null && payment.confirmedAmount != null
    ? Number(payment.declaredAmount) - Number(payment.confirmedAmount) : 0
  const meta = COMMISSION_PAYMENT_STATUS[payment.status] || COMMISSION_PAYMENT_STATUS.REPORTED
  return (
    <li className="rounded-lg border border-slate-200 bg-white">
      <button type="button" onClick={() => setOpen((v) => !v)} aria-expanded={open}
        className="flex w-full flex-wrap items-center justify-between gap-2 px-3 py-2.5 text-left text-xs">
        <span>
          <strong className="font-mono text-slate-900">
            {formatCurrency(payment.status === 'CONFIRMED' ? payment.confirmedAmount : payment.declaredAmount)}
          </strong>
          <span className="ml-2 text-slate-500">{formatBookingWhen(payment.reportedAt)}</span>
        </span>
        <span className="flex items-center gap-2">
          <span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold ${meta.cls}`}>{meta.label}</span>
          {payment.disputeStatus === 'OPEN' && <span className="inline-flex rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-800">Đang khiếu nại</span>}
          <span className="font-semibold text-brand-700">{open ? 'Thu gọn' : 'Xem thêm'}</span>
        </span>
      </button>
      {open && (
        <div className="space-y-2 border-t border-slate-100 px-3 py-3 text-xs text-slate-700">
          <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-2">
            <div><dt className="text-slate-500">Nhà xe khai đã chuyển</dt><dd className="font-mono font-semibold">{formatCurrency(payment.declaredAmount)}</dd></div>
            {payment.status === 'CONFIRMED' && <div><dt className="text-slate-500">Admin xác nhận thực nhận</dt><dd className="font-mono font-semibold">{formatCurrency(payment.confirmedAmount)}</dd></div>}
            <div><dt className="text-slate-500">Mã giao dịch</dt><dd className="font-mono">{payment.paymentReference || 'Không có'}</dd></div>
            {payment.decidedAt && <div><dt className="text-slate-500">Admin xử lý lúc</dt><dd>{formatBookingWhen(payment.decidedAt)}</dd></div>}
          </dl>
          {payment.note && <p>Ghi chú nhà xe: {payment.note}</p>}
          {payment.adminNote && <p>Ghi chú admin: {payment.adminNote}</p>}
          {payment.hasProof ? <ProofImage paymentId={payment.id} loadProof={loadProof} /> : <p className="text-slate-500">Không đính kèm ảnh biên lai.</p>}
          {shortfall > 0 && !payment.disputeStatus && (
            <p className="rounded-lg bg-amber-50 p-2 text-amber-800">Admin xác nhận ít hơn số nhà xe khai {formatCurrency(shortfall)}.</p>
          )}
          {dispute && (
            <div className="space-y-1 rounded-lg border border-slate-200 p-2">
              <span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold ${dispute.cls}`}>{dispute.label}</span>
              <p>Nhà xe khai thực chuyển <strong className="font-mono">{formatCurrency(payment.claimedAmount)}</strong>{payment.disputedAt ? ` · ${formatBookingWhen(payment.disputedAt)}` : ''}</p>
              {payment.disputeNote && <p>Lý do: {payment.disputeNote}</p>}
              {payment.hasDisputeProof && <ProofImage paymentId={payment.id} loadProof={loadProof} dispute />}
              {payment.disputeResolution && <p>Admin trả lời: {payment.disputeResolution}{payment.disputeResolvedAt ? ` · ${formatBookingWhen(payment.disputeResolvedAt)}` : ''}</p>}
            </div>
          )}
          {onDispute && payment.status === 'CONFIRMED' && payment.disputeStatus !== 'OPEN' && <DisputeForm payment={payment} onDispute={onDispute} />}
          {onResolve && payment.disputeStatus === 'OPEN' && <ResolveForm payment={payment} onResolve={onResolve} />}
        </div>
      )}
    </li>
  )
}

/** Lịch sử từng lần nộp hoa hồng: số tiền khai, mã giao dịch, ảnh biên lai, kết quả admin. */
export default function CommissionPaymentHistory({ payments, loadProof, expandLatest = false, onDispute, onResolve }) {
  if (!payments?.length) return <p className="text-xs text-slate-500">Chưa có lần nộp nào.</p>
  return (
    <ul className="space-y-2">
      {payments.map((payment, index) => (
        <PaymentRow key={payment.id} payment={payment} loadProof={loadProof} defaultOpen={expandLatest && index === 0}
          onDispute={onDispute} onResolve={onResolve} />
      ))}
    </ul>
  )
}
