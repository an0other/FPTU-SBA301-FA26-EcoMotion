import { yearlyDate, yearlyMoney } from '../../utils/yearlyRental'

export default function YearlyObligationsSummary({ view, payments = [], refunds = [], onOpen }) {
  const outstanding = payments.filter(p => !['CONFIRMED', 'VOIDED', 'EXPIRED'].includes(p.status))
  const next = outstanding.find(p => p.purpose === 'ADJUSTMENT') || outstanding.find(p => p.overdue) || outstanding[0]
  const deposit = payments.filter(p => p.purpose === 'SECURITY_DEPOSIT').reduce((sum, p) => sum + Number(p.paidAmount || 0), 0)
  const openReports = view.incidents.filter(i => i.status !== 'CLOSED').length
  const separateRefunds = refunds.filter(f => f.status !== 'REJECTED' && f.status !== 'CANCELLED')
  return <section aria-label="Tóm tắt nghĩa vụ hợp đồng" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
    <div className="rounded-xl border bg-white p-4"><p className="text-sm text-slate-500">Cọc đã ghi nhận riêng</p><p className="mt-2 text-lg font-bold">{yearlyMoney(deposit)}</p><p className="mt-1 text-xs text-slate-500">Không cộng vào tiền thuê đã thanh toán.</p></div>
    <button type="button" onClick={() => onOpen('payments')} className="rounded-xl border bg-white p-4 text-left hover:border-emerald-600"><p className="text-sm text-slate-500">{next ? 'Khoản chưa hoàn tất' : 'Thanh toán nghĩa vụ'}</p><p className="mt-2 text-lg font-bold">{next ? yearlyMoney(next.remainingAmount ?? Math.max(0, Number(next.amount) - Number(next.paidAmount || 0))) : 'Đã đối soát đủ'}</p><p className="mt-1 text-xs text-slate-500">{next ? (next.installmentNumber > 0 ? 'Tháng ' + next.installmentNumber : next.purpose === 'ADJUSTMENT' ? 'Bổ sung quyết toán' : 'Tiền cọc') + ' · ' + (next.dueOn ? yearlyDate(next.dueOn) : 'Trước bàn giao') : 'Xem chứng từ và các khoản hoàn riêng →'}</p></button>
    <button type="button" onClick={() => onOpen('incidents')} className="rounded-xl border bg-white p-4 text-left hover:border-emerald-600"><p className="text-sm text-slate-500">Sự cố đang xử lý</p><p className="mt-2 text-lg font-bold">{openReports} hồ sơ</p><p className="mt-1 text-xs text-slate-500">Xem trách nhiệm, phương án và giao nhận xe thay thế →</p></button>
    <button type="button" onClick={() => onOpen('return')} className="rounded-xl border bg-white p-4 text-left hover:border-emerald-600"><p className="text-sm text-slate-500">Quyết toán và hoàn tiền</p><p className="mt-2 font-bold">{view.stage === 'COMPLETED' ? 'Đã hoàn tất' : ['SETTLEMENT_PROPOSED', 'SETTLEMENT_ACCEPTED', 'REFUND_TRANSFER_RECORDED', 'REFUND_RECEIVED'].includes(view.stage) ? 'Đã có bảng quyết toán' : 'Chưa chốt quyết toán'}</p><p className="mt-1 text-xs text-slate-500">{separateRefunds.length ? separateRefunds.length + ' hồ sơ hoàn riêng tại mục Thanh toán. ' : ''}Cọc, tiền thuê hoàn và trạng thái nhận tiền được theo dõi riêng.</p></button>
  </section>
}
