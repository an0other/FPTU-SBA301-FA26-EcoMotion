import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'

const categories = { CHARGE: 'Khiếu nại phụ phí', YEARLY_SETTLEMENT: 'Quyết toán năm', YEARLY_CANCELLATION: 'Hủy trước nhận xe', DEPOSIT_REFUND: 'Khiếu nại hoàn cọc', SERVICE_QUALITY: 'Chất lượng dịch vụ', TRIP_NOT_CONFIRMED: 'Giờ kết thúc chuyến không đúng', PAYMENT_MISMATCH: 'Sai lệch số tiền thanh toán' }
const statuses = { OPEN: 'Đã gửi, chờ thụ lý', IN_REVIEW: 'Đang xem xét', RESOLVED: 'Đã phân xử', REJECTED: 'Đã bác bỏ khiếu nại' }
const finances = { AWAITING_REFUND: 'Đã phân xử, chờ hoàn tiền', AWAITING_RECEIPT: 'Nhà xe đã chuyển hoàn, chờ xác nhận nhận tiền', RECONCILIATION_REQUIRED: 'Khoản hoàn cần đối soát', COMPLETED: 'Khoản hoàn đã hoàn tất' }
const decisions = { WAIVE_CHARGE: 'Miễn khoản phụ phí', KEEP_CHARGE: 'Giữ nguyên khoản phụ phí' }

export default function DisputeHistory({ disputes = [] }) {
  if (!disputes.length) return null
  return <section id="booking-disputes" aria-label="Lịch sử khiếu nại" className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
    <h2 className="text-base font-semibold text-slate-900">Khiếu nại và kết quả xử lý</h2>
    {disputes.map(dispute => <article key={dispute.id} className="space-y-2 border-t border-slate-200 pt-4 text-sm text-slate-700">
      <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold text-slate-900">#{dispute.id} · {categories[dispute.category] || dispute.category}</h3><span>{statuses[dispute.status] || dispute.status}</span></div>
      <p className="whitespace-pre-wrap break-words">{dispute.description || 'Chưa ghi nhận nội dung'}</p>
      {dispute.chargeDescription && <p>Khoản phí: {dispute.chargeDescription}</p>}
      <p>Thời gian gửi: {formatBookingWhen(dispute.createdAt) || 'Chưa ghi nhận'}</p>
      {dispute.evidenceUrls?.length > 0 && <div className="flex flex-wrap gap-3">{dispute.evidenceUrls.map((url, index) => /^(https?:\/\/|\/api\/files\/)/.test(url) && <a key={url} href={url} target="_blank" rel="noopener noreferrer" className="text-emerald-800 underline">Bằng chứng {index + 1}</a>)}</div>}
      <p className="whitespace-pre-wrap break-words">Phản hồi / kết luận: {dispute.resolution || 'Chưa ghi nhận'}</p>
      <p>Người xử lý: {dispute.handledByName || 'Chưa ghi nhận'}</p>
      <p>Thời điểm phân xử: {formatBookingWhen(dispute.resolvedAt) || 'Chưa ghi nhận'}</p>
      {dispute.decision && <p>Quyết định: {decisions[dispute.decision] || dispute.decision}</p>}
      {dispute.refundAwardTotal != null && <p className="font-semibold">Tổng mức hoàn: {formatCurrency(dispute.refundAwardTotal)}</p>}
      {finances[dispute.financialStatus] && <p className="font-semibold text-emerald-900">{finances[dispute.financialStatus]}</p>}
      {dispute.refundRequestIds?.length > 0 && <p>Yêu cầu hoàn liên quan: {dispute.refundRequestIds.map(id => `#${id}`).join(', ')}. Xem tiến trình và biên lai trong danh sách hoàn tiền của đơn.</p>}
    </article>)}
  </section>
}
