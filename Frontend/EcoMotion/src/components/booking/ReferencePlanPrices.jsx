import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel, slotDurationLabel } from '../../utils/planLabels'

export default function ReferencePlanPrices({ plans, onYearly }) {
  const sortedPlans = [...plans].sort((a, b) => Number(a.slotLengthMinutes) - Number(b.slotLengthMinutes))

  return <section aria-label="Bảng giá tham khảo" className="min-w-0 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6">
    <h2 className="text-lg font-bold text-ink">Bảng giá tham khảo</h2>
    <p className="mt-1 text-sm leading-relaxed text-slate-600">
      Đơn giá từng gói để bạn dễ so sánh.
    </p>
    {sortedPlans.length ? <ul tabIndex={0} aria-label="Các gói thuê, cuộn ngang để xem thêm"
      className="mt-4 flex snap-x snap-proximity gap-3 overflow-x-auto rounded-lg p-1 pb-3 focus-visible:outline-2 focus-visible:outline-brand-700">
      {sortedPlans.map(plan => {
        const yearly = plan.planType === 'YEARLY'
        const content = <>
          <span className="min-w-0">
            <span className="block font-semibold text-ink">{planLabel(plan.planType).title}</span>
            <span className="mt-1 block text-xs text-slate-600">{yearly ? 'Hợp đồng riêng' : `${slotDurationLabel(plan.slotLengthMinutes) ?? 'Chưa có thời lượng'} / gói`}</span>
          </span>
          <span className="min-w-0 border-t border-slate-200 pt-3">
            <span className="block text-lg font-bold tabular-nums text-ink">{formatCurrency(plan.slotPrice)}</span>
            <span className="mt-1 block text-xs text-slate-600">
              {yearly ? 'Giá mỗi gói năm' : 'Giá mỗi gói'}
            </span>
          </span>
        </>
        const rowClass = 'flex h-full w-full flex-col justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 text-left text-sm'
        return <li key={plan.id} className="flex min-w-40 flex-1 shrink-0 basis-40 snap-start flex-col">
          <div className={rowClass}>{content}</div>
          {yearly && onYearly ? <button type="button" onClick={() => onYearly(plan.id)}
            className="mt-1 inline-flex min-h-11 items-center rounded-lg px-3 text-sm font-semibold text-brand-800 underline underline-offset-4 hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-brand-700">
            Xem hợp đồng thuê năm →
          </button> : null}
        </li>
      })}
    </ul> : <p className="mt-4 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">Xe chưa có gói thuê đang mở bán.</p>}
    <p className="mt-4 border-t border-slate-100 pt-4 text-xs leading-relaxed text-slate-600">
      Giá thuê chưa gồm phí đón và phụ phí phát sinh. Với gói giờ, ngày, tháng, chọn thời gian thuê để xem tổng giá ghép gói.
    </p>
  </section>
}
