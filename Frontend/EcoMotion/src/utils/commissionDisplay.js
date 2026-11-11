/** Hiển thị trạng thái bảng kê hoa hồng tháng. */
export const COMMISSION_STATUS = {
  ISSUED: { label: 'Chờ nộp', cls: 'border-blue-200 bg-blue-50 text-blue-800' },
  PAYMENT_REPORTED: { label: 'Đã báo chuyển — chờ admin xác nhận', cls: 'border-amber-200 bg-amber-50 text-amber-800' },
  PAID: { label: 'Đã nộp đủ', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  OVERDUE: { label: 'Quá hạn', cls: 'border-rose-200 bg-rose-50 text-rose-800' },
}

export function commissionStatus(status) {
  return COMMISSION_STATUS[status] || { label: 'Kỳ đang chạy', cls: 'border-slate-200 bg-slate-50 text-slate-700' }
}

/** "Tháng 10/2026" từ "2026-10". */
export function periodLabel(periodMonth) {
  if (!periodMonth) return ''
  const [year, month] = periodMonth.split('-')
  return `Tháng ${Number(month)}/${year}`
}

/** Nhà xe còn được nộp hoa hồng cho bảng kê này (chưa nộp đủ và chưa báo chuyển). */
export function canPayCommission(statement) {
  return Boolean(statement?.id) && ['ISSUED', 'OVERDUE'].includes(statement.status) && Number(statement.remainingAmount) > 0
}

/** Mô tả hạn nộp theo thời điểm hiện tại. */
export function dueText(statement, now = Date.now()) {
  if (!statement?.dueAt || statement.status === 'PAID') return ''
  const due = new Date(statement.dueAt).getTime()
  const days = Math.ceil((due - now) / 86400000)
  if (days > 1) return `Còn ${days} ngày đến hạn`
  if (days === 1) return 'Hạn nộp trong hôm nay/ngày mai'
  if (statement.overdueDays > 0) return `Quá hạn ${statement.overdueDays} ngày`
  return 'Đã đến hạn'
}
