import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'

const categories = { YEARLY_REFUND: 'Chưa nhận/nhận thiếu tiền hoàn năm', YEARLY_INCIDENT: 'Sự cố năm cần Admin xem xét', CHARGE: 'Khiếu nại phụ phí', YEARLY_SETTLEMENT: 'Quyết toán năm', YEARLY_CANCELLATION: 'Hủy trước nhận xe', DEPOSIT_REFUND: 'Khiếu nại hoàn cọc', SERVICE_QUALITY: 'Chất lượng dịch vụ', TRIP_NOT_CONFIRMED: 'Giờ kết thúc chuyến không đúng', PAYMENT_MISMATCH: 'Sai lệch số tiền thanh toán', OTHER: 'Khác' }
const statuses = {
  OPEN: { label: 'Chờ thụ lý', cls: 'border-amber-200 bg-amber-50 text-amber-800' },
  IN_REVIEW: { label: 'Đang xem xét', cls: 'border-sky-200 bg-sky-50 text-sky-800' },
  RESOLVED: { label: 'Đã phân xử', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  REJECTED: { label: 'Đã bác bỏ', cls: 'border-slate-200 bg-slate-100 text-slate-700' },
}
const finances = {
  AWAITING_REFUND: { label: 'Chờ nhà xe hoàn tiền', cls: 'text-amber-800' },
  AWAITING_RECEIPT: { label: 'Nhà xe đã chuyển hoàn — chờ bạn xác nhận', cls: 'text-sky-800' },
  RECONCILIATION_REQUIRED: { label: 'Khoản hoàn cần đối soát', cls: 'text-rose-800' },
  COMPLETED: { label: 'Khoản hoàn đã hoàn tất', cls: 'text-emerald-800' },
}
const decisions = { WAIVE_CHARGE: 'Miễn khoản phụ phí', KEEP_CHARGE: 'Giữ nguyên khoản phụ phí', PARTIAL_WAIVE: 'Giảm một phần phụ phí' }

function amountLabel(dispute) {
  if (dispute.decision === 'PARTIAL_WAIVE') return 'Số tiền phụ phí được giảm'
  if (dispute.category === 'PAYMENT_MISMATCH') return 'Số tiền bạn thực chuyển (đã đối chiếu)'
  if (dispute.category === 'DEPOSIT_REFUND') return 'Tổng tiền cọc được hoàn'
  if (dispute.category === 'SERVICE_QUALITY' || dispute.category === 'OTHER') return 'Bồi hoàn cho bạn'
  return 'Số tiền theo phán quyết'
}

export default function DisputeHistory({ disputes = [] }) {
  if (!disputes.length) return null
  return (
    <section id="booking-disputes" aria-label="Lịch sử khiếu nại" className="rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-bold text-slate-900">Khiếu nại và kết quả xử lý</h2>
      <ul className="mt-3 space-y-3">
        {disputes.map((dispute) => {
          const status = statuses[dispute.status] || { label: dispute.status, cls: 'border-slate-200 bg-slate-50 text-slate-700' }
          const finance = finances[dispute.financialStatus]
          const decided = ['RESOLVED', 'REJECTED'].includes(dispute.status)
          const evidence = (dispute.evidenceUrls || []).filter((url) => /^(https?:\/\/|\/api\/files\/)/.test(url))
          return (
            <li key={dispute.id} className="rounded-xl border border-slate-200 p-4 text-sm text-slate-700">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <h3 className="font-semibold text-slate-900">{categories[dispute.category] || dispute.category}</h3>
                  <p className="text-xs text-slate-500">#{dispute.id} · gửi {formatBookingWhen(dispute.createdAt) || '—'}</p>
                </div>
                <span className={`inline-flex shrink-0 rounded-full border px-2.5 py-0.5 text-[11px] font-bold ${status.cls}`}>{status.label}</span>
              </div>

              {dispute.chargeDescription && <p className="mt-2 text-xs text-slate-600">Khoản phí: <strong>{dispute.chargeDescription}</strong></p>}
              {dispute.description && (
                <p className="mt-2 whitespace-pre-wrap break-words border-l-2 border-slate-200 pl-3 text-slate-600">{dispute.description}</p>
              )}
              {evidence.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-2">
                  {evidence.map((url, index) => (
                    <a key={url} href={url} target="_blank" rel="noopener noreferrer"
                      className="rounded-full border border-slate-200 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-800 hover:bg-emerald-50">
                      Bằng chứng {index + 1}
                    </a>
                  ))}
                </div>
              )}

              {decided ? (
                <div className="mt-3 space-y-1.5 rounded-lg bg-slate-50 p-3">
                  {dispute.decision && <p className="font-semibold text-slate-900">{decisions[dispute.decision] || dispute.decision}</p>}
                  {dispute.resolution && <p className="whitespace-pre-wrap break-words">{dispute.resolution}</p>}
                  {dispute.refundAwardTotal != null && (
                    <p className="flex flex-wrap items-baseline justify-between gap-2">
                      <span className="text-xs text-slate-500">{amountLabel(dispute)}</span>
                      <strong className="font-mono text-slate-900">{formatCurrency(dispute.refundAwardTotal)}</strong>
                    </p>
                  )}
                  {finance && (
                    <p className={`text-xs font-semibold ${finance.cls}`}>
                      {finance.label}
                      {dispute.refundRequestIds?.length > 0 && <span className="font-normal text-slate-500"> · khoản hoàn {dispute.refundRequestIds.map((id) => `#${id}`).join(', ')}</span>}
                    </p>
                  )}
                  <p className="text-[11px] text-slate-500">
                    {dispute.handledByName || 'Admin'} · {formatBookingWhen(dispute.resolvedAt) || '—'}
                  </p>
                </div>
              ) : (
                <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  {dispute.status === 'IN_REVIEW' && dispute.resolution ? dispute.resolution : 'Quản trị viên sẽ xem xét và phản hồi tại đây.'}
                </p>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
