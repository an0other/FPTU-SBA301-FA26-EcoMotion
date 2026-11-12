import apiClient from '../../services/apiClient'
import { openDispute, uploadDisputeEvidence } from '../../services/bookingService'
import ActionForm from './YearlyActionForm'
import { yearlyMoney } from '../../utils/yearlyRental'

export default function YearlyPaymentFeedback({ request, canReport, busy, run, onDone }) {
  if (!(Number(request.paidAmount) > 0)) return null
  return <section className="space-y-3 rounded-lg border border-blue-200 p-4">
    <h4 className="font-semibold">Đối chiếu số provider đã ghi nhận</h4>
    <p className="text-sm">Đã áp vào khoản này: {yearlyMoney(request.paidAmount)}. Nếu có tiền chuyển dư, xem riêng khoản hoàn; không coi số đã áp dụng là toàn bộ tiền ngân hàng nhận.</p>
    {request.renterAcknowledgedAt ? <p className="text-sm text-emerald-800">Bạn đã xác nhận số ghi nhận này. Khi provider ghi nhận thêm giao dịch, cần đối chiếu lại số mới.</p> : <ActionForm title="Số provider ghi nhận phù hợp với giao dịch của tôi" submitLabel="Đồng ý số đã ghi nhận" busy={busy} confirm onSubmit={async () => { const ok = await run(() => apiClient.post('/payment-requests/' + request.id + '/acknowledge'), 'Đã xác nhận số ghi nhận. Không thay đổi số tiền hoặc tự hoàn tất hợp đồng.'); if (ok) onDone(); return ok }} />}
    {canReport && <details><summary className="cursor-pointer font-semibold text-amber-900">Số tiền không đúng? Báo sai lệch</summary><ActionForm title="Yêu cầu Admin đối chiếu giao dịch" busy={busy} confirm fields={[
      { name: 'amount', label: 'Tổng số tiền thực tế đã chuyển cho riêng khoản này (đ)', type: 'money', min: 1000, max: 1000000000 },
      { name: 'mismatchType', label: 'Loại sai lệch', type: 'select', options: [['UNDER_RECORDED','Provider ghi thiếu tiền'],['OVERPAID','Tôi chuyển dư'],['OTHER','Sai lệch khác']] },
      { name: 'note', label: 'Nội dung cần đối chiếu', type: 'textarea' },
      { name: 'file', label: 'Ảnh giao dịch / sao kê', type: 'file' },
    ]} onSubmit={async d => { const ok = await run(async () => {
      const url = await uploadDisputeEvidence(request.bookingCode,d.get('file'))
      await openDispute(request.bookingCode,{ category:'PAYMENT_MISMATCH',paymentRequestId:request.id,claimedAmount:Number(d.get('amount')),mismatchType:d.get('mismatchType'),description:d.get('note'),evidenceUrls:[url] })
    }, 'Đã gửi sai lệch để Admin đối chiếu. Tiền đã ghi nhận được giữ nguyên, chưa tự tăng hoặc giảm.'); if (ok) onDone(); return ok }} /></details>}
  </section>
}
