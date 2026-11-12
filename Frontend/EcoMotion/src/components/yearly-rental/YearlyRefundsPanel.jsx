import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RefundAccountModal } from '../booking/RefundAccountModal'
import RefundReceiptTimeline from '../booking/RefundReceiptTimeline'
import { RefundProofModal } from '../payments/RefundProofModal'
import { yearlyDate, yearlyMoney, yearlyButtonClass } from '../../utils/yearlyRental'

const statuses = { REQUESTED: 'Chờ xét duyệt', UNDER_REVIEW: 'Cần đối soát', APPROVED: 'Đã duyệt', PROCESSING: 'Chờ thực hiện hoàn', REFUNDED: 'Đã ghi nhận chuyển hoàn', FAILED: 'Hoàn chưa thành công', PENDING_RECONCILIATION: 'Đang xác minh giao dịch hoàn', REJECTED: 'Đã đóng / từ chối yêu cầu' }
export default function YearlyRefundsPanel({ role, code, refunds, busy, run }) {
  const [accountOpen, setAccountOpen] = useState(false)
  const [proofRefund, setProofRefund] = useState(null)
  const provider = role === 'provider'
  const manual = refunds.filter(r => r.refundMethod !== 'VNPAY' && !['REFUNDED','REJECTED'].includes(r.status))
  const refresh = () => run(async () => {}, 'Đã cập nhật khoản hoàn riêng.')
  return <section className="space-y-4 rounded-xl border border-blue-200 bg-white p-5">
    <h3 className="text-lg font-bold">Hoàn tiền riêng: chuyển dư / đối soát</h3>
    <p className="text-sm text-slate-600">Đây là các khoản hoàn qua hệ thống tài chính chung. Không cộng lại vào tiền thuê hoặc tiền cọc cần hoàn ở quyết toán năm. Chưa hỗ trợ cấn trừ tiền dư sang kỳ thuê.</p>
    {!refunds.length && <p className="text-sm text-slate-500">Chưa có khoản hoàn riêng. Khoản chuyển dư chỉ xuất hiện sau khi provider ghi nhận tiền thực nhận, không dựa vào số renter khai.</p>}
    {!provider && manual.length > 0 && <button disabled={busy} className={yearlyButtonClass} onClick={() => setAccountOpen(true)}>Bổ sung / kiểm tra tài khoản nhận hoàn</button>}
    {refunds.map(refund => <article key={refund.id} className="space-y-3 rounded-lg border p-4">
      <h4 className="font-semibold">#{refund.id} · {refund.excess ? 'Hoàn tiền chuyển dư' : 'Khoản hoàn riêng'} · {yearlyMoney(refund.amount)}</h4>
      <p className="text-sm">{statuses[refund.status] || refund.status} · Tạo ngày {yearlyDate(refund.createdAt)}</p>
      <p className="whitespace-pre-wrap text-sm">{refund.reason}</p>
      {refund.refundMethod !== 'VNPAY' && <p className="rounded-lg bg-slate-50 p-3 text-sm">{refund.recipientAccountNumber ? 'Tài khoản nhận: ' + refund.recipientBankName + ' · ' + refund.recipientAccountHolder + ' · ' + refund.recipientAccountNumber : 'Chưa có tài khoản nhận hoàn. Renter cần bổ sung trước khi provider chuyển tiền.'}</p>}
      {refund.status === 'REFUNDED' && refund.receiptStatus === 'WAITING' && <p className="text-sm font-semibold text-amber-800">Provider đã gửi chứng từ; vẫn đang chờ renter kiểm tra tiền thực nhận.</p>}
      {provider && refund.status === 'PROCESSING' && refund.refundMethod !== 'VNPAY' && refund.paymentTransactionId && <button disabled={busy || !refund.recipientAccountNumber} className={yearlyButtonClass} onClick={() => setProofRefund(refund)}>Đã chuyển hoàn — gửi biên lai</button>}
      {provider && ['REQUESTED','UNDER_REVIEW','FAILED','APPROVED'].includes(refund.status) && <Link className="block text-sm font-semibold text-emerald-800 underline" to="/provider/finance?tab=refunds">Mở tài chính để xét duyệt / tiếp tục đối soát khoản #{refund.id}</Link>}
      {refund.receiptStatus === 'REPORTED_NOT_RECEIVED' && <p className="text-sm text-amber-800">Đang có phản hồi chưa nhận tiền. Cần đối soát khoản đã chuyển trước khi gửi lại; không tự chuyển thêm một lần nữa.</p>}
      <RefundReceiptTimeline refund={refund} provider={provider} onRefresh={refresh} />
    </article>)}
    {accountOpen && <RefundAccountModal bookingCode={code} refunds={manual} onClose={() => setAccountOpen(false)} onSaved={refresh} />}
    {proofRefund && <RefundProofModal refund={proofRefund} onClose={() => setProofRefund(null)} onSuccess={refresh} />}
  </section>
}
