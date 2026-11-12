import { useEffect, useRef, useState } from 'react'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { COMMISSION_PAYMENT_STATUS } from '../../utils/commissionDisplay'

function ProofImage({ paymentId, loadProof }) {
  const [state, setState] = useState({ url: null, error: false })
  const loader = useRef(loadProof)
  useEffect(() => {
    let live = true
    let objectUrl
    loader.current(paymentId).then((blob) => {
      if (!live) return
      objectUrl = URL.createObjectURL(blob)
      setState({ url: objectUrl, error: false })
    }).catch(() => { if (live) setState({ url: null, error: true }) })
    return () => { live = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [paymentId])
  if (state.error) return <p role="alert" className="text-[11px] text-red-700">Không tải được ảnh biên lai.</p>
  if (!state.url) return <p role="status" className="text-[11px] text-slate-500">Đang tải ảnh…</p>
  return (
    <a href={state.url} target="_blank" rel="noreferrer" className="block w-fit">
      <img src={state.url} alt="Ảnh biên lai chuyển khoản hoa hồng" className="max-h-72 rounded-lg border border-slate-200 bg-white object-contain" />
      <span className="text-[11px] font-semibold text-brand-700 underline">Mở ảnh đầy đủ</span>
    </a>
  )
}

function PaymentRow({ payment, loadProof, defaultOpen }) {
  const [open, setOpen] = useState(defaultOpen)
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
        </div>
      )}
    </li>
  )
}

/** Lịch sử từng lần nộp hoa hồng: số tiền khai, mã giao dịch, ảnh biên lai, kết quả admin. */
export default function CommissionPaymentHistory({ payments, loadProof, expandLatest = false }) {
  if (!payments?.length) return <p className="text-xs text-slate-500">Chưa có lần nộp nào.</p>
  return (
    <ul className="space-y-2">
      {payments.map((payment, index) => (
        <PaymentRow key={payment.id} payment={payment} loadProof={loadProof} defaultOpen={expandLatest && index === 0} />
      ))}
    </ul>
  )
}
