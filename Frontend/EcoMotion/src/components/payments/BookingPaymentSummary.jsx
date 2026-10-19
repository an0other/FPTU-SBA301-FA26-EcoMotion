import { formatPaymentMoney } from '../../utils/paymentDisplay'

function SummaryItem({ label, value, highlighted = false }) {
  return (
    <div
      className={`rounded-xl border p-4 ${
        highlighted
          ? 'border-brand-200 bg-brand-50'
          : 'border-slate-200 bg-white'
      }`}
    >
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd
        className={`mt-2 text-xl font-bold tabular-nums ${
          highlighted ? 'text-brand-800' : 'text-slate-900'
        }`}
      >
        {value == null ? 'Chưa xác định' : formatPaymentMoney(value)}
      </dd>
    </div>
  )
}

export default function BookingPaymentSummary({ summary, planType }) {
  if (!summary) {
    return (
      <p className="text-sm text-slate-500">
        Chưa có thông tin tổng hợp thanh toán.
      </p>
    )
  }

  const yearly = planType === 'YEARLY'
  const provisional = summary.provisional
  const hasRefund = Number(summary.refundedAmount) > 0
  const hasCredit = Number(summary.creditAmount) > 0

  return (
    <div className="space-y-4">
      <dl className="grid gap-3 sm:grid-cols-3">
        <SummaryItem
          label={
            yearly
              ? 'Chi phí đã ghi nhận'
              : provisional
                ? 'Tổng chi phí dự tính'
                : 'Tổng chi phí đã ghi nhận'
          }
          value={summary.recordedCost}
        />

        <SummaryItem
          label="Đã thanh toán thành công"
          value={summary.paidAmount}
        />

        <SummaryItem
          label={
            yearly
              ? 'Khoản trả trước theo hợp đồng'
              : provisional
                ? 'Còn lại dự tính'
                : 'Còn lại theo ghi nhận'
          }
          value={yearly ? summary.upfrontRequired : summary.remainingAmount}
          highlighted
        />
      </dl>

      {hasRefund && (
        <dl className="grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Đã hoàn tiền</dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {formatPaymentMoney(summary.refundedAmount)}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Đã trả sau khi trừ hoàn tiền</dt>
            <dd className="mt-1 font-semibold text-slate-900">
              {formatPaymentMoney(summary.netPaidAmount)}
            </dd>
          </div>
        </dl>
      )}

      {hasCredit && (
        <p className="rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
          Tiền đã trả vượt chi phí đang ghi nhận:{' '}
          <strong>{formatPaymentMoney(summary.creditAmount)}</strong>.
          Khoản này chưa đồng nghĩa đã được duyệt hoàn tiền.
        </p>
      )}

      {summary.balanceNote && (
        <p className="text-xs leading-relaxed text-slate-500">
          {summary.balanceNote}
        </p>
      )}
    </div>
  )
}