import { bookingStatusMeta, paymentStatusLabel } from '../../utils/bookingStatus'
import { computeBookingFinancialLines, formatFinancialLineAmount } from '../../utils/bookingFinancialSummary'

const SETTLEMENT_LINE_KEYS = new Set(['remaining', 'remainingCalc'])
const HIGHLIGHT_LINE_KEYS = new Set([...SETTLEMENT_LINE_KEYS, 'paymentComplete'])

function SummaryRow({ label, children, emphasize = false, note = null }) {
  return (
    <div className={`${emphasize ? 'border-t border-[#E6EBE8] pt-3' : ''}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
        <dt className="text-[11px] font-semibold uppercase tracking-wider text-[#6D7A72]">{label}</dt>
        <dd className={`text-right tabular-nums ${emphasize ? 'text-base font-bold text-brand-800' : 'text-sm font-semibold text-[#14221A]'}`}>
          {children}
        </dd>
      </div>
      {note ? <p className="mt-0.5 text-[10px] leading-snug text-[#14221A]/50">{note}</p> : null}
    </div>
  )
}

function SettlementDueHighlight({ line }) {
  return (
    <div
      className="mt-3 rounded-xl border-2 border-brand-600 bg-white px-4 py-4 shadow-sm"
      role="status"
      aria-live="polite"
    >
      <p className="text-xs font-bold uppercase tracking-wide text-brand-900">
        Cần thanh toán khi quyết toán chuyến
      </p>
      <p className="mt-2 text-2xl font-extrabold tabular-nums tracking-tight text-brand-800 sm:text-3xl">
        {formatFinancialLineAmount(line.amount)}
      </p>
      <p className="mt-2 text-xs leading-relaxed text-[#14221A]/65">
        {line.note || 'Tổng chi phí dự kiến trừ phần đã thanh toán (gồm cọc). Đây là số tiền bạn cần trả khi kết thúc thuê.'}
      </p>
    </div>
  )
}

function PaymentCompleteHighlight({ line }) {
  return (
    <div
      className="mt-3 rounded-xl border-2 border-emerald-600 bg-white px-4 py-4 shadow-sm"
      role="status"
      aria-live="polite"
    >
      <p className="text-xs font-bold uppercase tracking-wide text-emerald-900">
        Đã quyết toán xong
      </p>
      <p className="mt-2 text-2xl font-extrabold tabular-nums tracking-tight text-emerald-800 sm:text-3xl">
        {formatFinancialLineAmount(line.amount)}
      </p>
      {line.breakdown?.length > 0 ? (
        <ul className="mt-3 space-y-1.5 border-t border-emerald-100 pt-3 text-xs text-[#14221A]/75">
          {line.breakdown.map((row) => (
            <li key={row.label} className="flex justify-between gap-3 tabular-nums">
              <span>{row.label}</span>
              <span className="font-semibold text-[#14221A]">{formatFinancialLineAmount(row.amount)}</span>
            </li>
          ))}
        </ul>
      ) : null}
      <p className="mt-2 text-xs leading-relaxed text-[#14221A]/65">
        Gồm cọc giữ chỗ và quyết toán cuối chuyến (kể cả phụ phí nếu có).
      </p>
    </div>
  )
}

/**
 * Khối tóm tắt trạng thái đơn, lịch thuê và số tiền — dùng trên chi tiết đơn, thanh toán, submitted.
 */
export function BookingOrderSummaryCard({
  bookingCode,
  status,
  paymentStatus,
  formattedStart,
  formattedEnd,
  durationLabel,
  totalAmount,
  depositAmount,
  confirmedPaid,
  remainingAmount,
  pickupFee,
  charges,
  showStatusBadges = true,
  // Khi trang đã có bảng "Khoản phí" bóc tách chi tiết: chỉ giữ dòng tổng, tránh lặp lại từng khoản.
  totalOnly = false,
  className = '',
}) {
  const statusMeta = bookingStatusMeta(status)
  const paymentLabel = paymentStatus ? paymentStatusLabel(paymentStatus) : null
  const financialLines = computeBookingFinancialLines({
    totalAmount,
    pickupFee,
    depositAmount,
    confirmedPaid,
    remainingAmount,
    status,
    paymentStatus,
    charges,
  })
  const costLines = financialLines.filter((line) => line.section === 'cost' && (!totalOnly || line.key === 'grand'))
  const paymentLines = financialLines.filter((line) => line.section === 'payment')
  const settlementLine = paymentLines.find((line) => SETTLEMENT_LINE_KEYS.has(line.key))
  const completeLine = paymentLines.find((line) => line.key === 'paymentComplete')
  const regularPaymentLines = paymentLines.filter((line) => !HIGHLIGHT_LINE_KEYS.has(line.key))

  return (
    <section
      aria-label="Tóm tắt đơn thuê"
      className={`rounded-[16px] border border-[#E6EBE8] bg-white p-5 shadow-[0_8px_24px_rgba(20,34,26,0.06)] ${className}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[#14221A]">Tóm tắt đơn thuê</h2>
          {bookingCode ? (
            <p className="mt-0.5 font-mono text-xs font-semibold text-[#14221A]/70">{bookingCode}</p>
          ) : null}
        </div>
        {showStatusBadges ? (
          <div className="flex flex-wrap justify-end gap-1.5">
            <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${statusMeta.badgeClass}`}>
              {statusMeta.label}
            </span>
            {paymentLabel ? (
              <span className="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-800">
                {paymentLabel}
              </span>
            ) : null}
          </div>
        ) : null}
      </div>

      <dl className="mt-4 space-y-2.5">
        <SummaryRow label="Bắt đầu dự kiến">{formattedStart || '—'}</SummaryRow>
        <SummaryRow label="Kết thúc dự kiến">{formattedEnd || '—'}</SummaryRow>
        {durationLabel ? <SummaryRow label="Thời lượng thuê">{durationLabel}</SummaryRow> : null}
      </dl>

      {costLines.length > 0 ? (
        <div className="mt-4 space-y-2.5 rounded-xl bg-[#F7F8F6] p-3.5">
          <p className="text-[10px] font-bold uppercase tracking-wider text-[#6D7A72]">Chi phí dự kiến</p>
          {totalOnly ? <p className="text-[11px] text-[#6D7A72]">Chi tiết từng khoản xem ở bảng “Khoản phí”.</p> : null}
          {costLines.map((line) => (
            <SummaryRow
              key={line.key}
              label={line.label}
              emphasize={line.emphasize}
              note={line.note}
            >
              {formatFinancialLineAmount(line.amount)}
            </SummaryRow>
          ))}
        </div>
      ) : null}

      {paymentLines.length > 0 ? (
        <div className="mt-3 space-y-2.5 rounded-xl border border-brand-100 bg-brand-50/30 p-3.5">
          <p className="text-[10px] font-bold uppercase tracking-wider text-brand-900/80">Thanh toán</p>
          {regularPaymentLines.map((line) => (
            <SummaryRow
              key={line.key}
              label={line.label}
              emphasize={line.emphasize}
              note={line.note}
            >
              {formatFinancialLineAmount(line.amount)}
            </SummaryRow>
          ))}
          {settlementLine ? <SettlementDueHighlight line={settlementLine} /> : null}
          {completeLine ? <PaymentCompleteHighlight line={completeLine} /> : null}
        </div>
      ) : null}
    </section>
  )
}
