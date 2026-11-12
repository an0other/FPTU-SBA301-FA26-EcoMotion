import { yearlyDate, yearlyMoney } from '../../utils/yearlyRental'

export default function YearlyReturnAssessment({ assessment: a, evidence, adminResolved = false, payments = null }) {
  if (!a) return null
  const m = a.money
  const agreed = ['SETTLEMENT_ACCEPTED','REFUND_TRANSFER_RECORDED','REFUND_RECEIVED','COMPLETED'].includes(a.stage)
  const adjustments = payments?.filter(p => p.purpose === 'ADJUSTMENT' && !['VOIDED', 'EXPIRED'].includes(p.status))
  const adjustmentPaid = adjustments?.reduce((sum, p) => sum + Number(p.paidAmount || 0), 0) || 0
  const adjustmentRemaining = adjustments?.length ? adjustments.reduce((sum, p) => sum + Math.max(0, Number(p.remainingAmount ?? Number(p.amount) - Number(p.paidAmount || 0))), 0) : Number(m.balanceDue || 0)
  return <section className="space-y-4 rounded-xl border border-emerald-200 bg-white p-5">
    <h3 className="text-lg font-bold">{agreed ? (adminResolved ? 'Bảng quyết toán theo phán quyết Admin' : 'Bảng quyết toán đã thống nhất') : a.stage === 'SETTLEMENT_PROPOSED' ? 'Bảng quyết toán provider đề xuất' : 'Đối chiếu dự tính theo điều khoản đã chốt'}</h3>
    <p className="text-sm">{adminResolved ? 'Admin đã phân xử ngày trả xe và quyết toán; đây không phải xác nhận trực tiếp của người thuê.' : a.returnConfirmed ? 'Hai bên đã xác nhận trả xe.' : 'Biên bản đang chờ người thuê xác nhận; số liệu dưới đây chưa phải quyết toán đã chốt.'} Phiên bản {a.version}.</p>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt>Nhận thực tế / hạn trả</dt><dd>{yearlyDate(a.startedOn)} — {yearlyDate(a.dueOn)}</dd></div>
      <div><dt>Ngày trả ghi nhận</dt><dd>{yearlyDate(a.returnedOn)}</dd></div>
      <div><dt>Tháng đã bắt đầu / tháng tính tiền</dt><dd>{a.usedMonths} / {a.chargedMonths}</dd></div>
    </dl>
    <h4 className="font-semibold">Km từng xe — cộng quãng đường, không trừ đồng hồ khác xe</h4>
    <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Xe / thời gian</th><th className="p-2">Km nhận</th><th className="p-2">Km kết thúc</th><th className="p-2">Đã đi</th></tr></thead><tbody>{a.legs.map((leg, i) => <tr key={i} className="border-t"><td className="p-2">{leg.vehicleName}<span className="block text-xs text-slate-500">{yearlyDate(leg.from)} — {yearlyDate(leg.to)}</span></td><td className="p-2">{leg.initialKm}</td><td className="p-2">{leg.finalKm}</td><td className="p-2 font-semibold">{leg.usedKm} km</td></tr>)}</tbody></table></div>
    <div className="rounded-lg bg-slate-50 p-4 text-sm">
      <p>Tổng đã đi: <strong>{a.totalKm} km</strong>. Hạn mức năm: {a.annualKm} km.</p>
      <p>Hạn mức áp dụng: {a.annualKm} × {a.allowanceMonths}/12, làm tròn xuống = <strong>{a.allowedKm} km</strong>.</p>
      <p>Vượt: max(0, {a.totalKm} − {a.allowedKm}) = {a.excessKm} km × {yearlyMoney(a.excessKmRate)} = <strong>{yearlyMoney(m.excessKmFee)}</strong>.</p>
      <p>Trễ thực tế: {a.calendarLateDays} ngày; ân hạn {a.graceDays} ngày; tính phí {a.chargedLateDays} ngày × {yearlyMoney(a.lateDailyRate)} = <strong>{yearlyMoney(m.lateFee)}</strong>.</p>
    </div>
    <dl className="grid gap-3 text-sm sm:grid-cols-2">
      {[['Tiền thuê phải trả',m.rentDue],['Phí kết thúc sớm',m.earlyFee],['Phí trễ',m.lateFee],['Phí vượt km',m.excessKmFee],['Chi phí bổ sung có bằng chứng',m.otherCharges],['Giảm trừ đổi xe',m.vehicleChangeDiscount],[agreed ? 'Tiền thuê đã nhận khi chốt' : 'Tiền thuê đã nhận',m.rentPaid],[agreed ? 'Cọc đưa vào quyết toán' : 'Cọc đang giữ',m.depositHeld],['Dùng cọc bù nghĩa vụ',m.depositDeduction],[agreed ? 'Nghĩa vụ bổ sung khi chốt' : 'Cần thanh toán bổ sung',m.balanceDue],['Tiền thuê cần hoàn',m.rentRefund],['Cọc cần hoàn',m.depositRefund]].map(([label,value]) => <div key={label}><dt className="text-slate-500">{label}</dt><dd className="font-semibold">{yearlyMoney(value)}</dd></div>)}
    </dl>
    {agreed && <p className="rounded-lg bg-blue-50 p-3 text-sm">Bảng trên lưu số tiền tại thời điểm chốt, không phải yêu cầu chuyển lại tiền. Trạng thái thu tiền hiện tại xem tại Thanh toán; trạng thái hoàn tiền xem ở bước 3.</p>}
    {agreed && payments && <dl className="grid gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm sm:grid-cols-2"><div><dt>Khoản bổ sung đã đối soát hiện tại</dt><dd className="font-bold">{yearlyMoney(adjustmentPaid)}</dd></div><div><dt>Khoản bổ sung còn chưa đối soát</dt><dd className="font-bold">{yearlyMoney(adjustmentRemaining)}</dd></div></dl>}
    {m.note && <p className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm">Ghi chú quyết toán: {m.note}</p>}
    {!!a.charges.length && <div className="space-y-3"><h4 className="font-semibold">Chi tiết chi phí bổ sung</h4>{a.charges.map((item,i) => <div key={i} className="rounded-lg border p-3 text-sm"><p>{item.description} · <strong>{yearlyMoney(item.amount)}</strong></p>{item.incidentId && <p>Liên quan report #{item.incidentId}</p>}{evidence?.(item.evidenceId)}</div>)}</div>}
    <p className="text-xs text-slate-500">Phí vượt km, trễ và trả sớm đã tính ở trên. Không nhập lại vào chi phí bổ sung. Tháng phí trả sớm không tăng hạn mức km; thanh toán đủ hợp đồng áp dụng hạn mức năm.</p>
  </section>
}
