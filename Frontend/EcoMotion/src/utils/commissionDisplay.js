export const COMMISSION_STATUS = {
  ISSUED: { label: 'Chờ nộp', cls: 'border-blue-200 bg-blue-50 text-blue-800' },
  PAYMENT_REPORTED: { label: 'Đã báo chuyển — chờ admin xác nhận', cls: 'border-amber-200 bg-amber-50 text-amber-800' },
  PAID: { label: 'Đã nộp đủ', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  OVERDUE: { label: 'Quá hạn', cls: 'border-rose-200 bg-rose-50 text-rose-800' },
}

/** Trạng thái thanh toán dễ hiểu: chưa nộp / nộp thiếu / đã báo chuyển / đủ / dư. */
export function commissionPaymentState(statement, formatMoney = (v) => `${Number(v || 0).toLocaleString('vi-VN')} đ`) {
  switch (statement?.paymentState) {
    case 'PARTIAL':
      return { label: `Nộp thiếu — còn ${formatMoney(statement.remainingAmount)}`, cls: 'border-amber-300 bg-amber-50 text-amber-800' }
    case 'REPORTED':
      return { label: 'Đã báo chuyển — chờ admin xác nhận', cls: 'border-sky-200 bg-sky-50 text-sky-800' }
    case 'PAID':
      return { label: 'Đã nộp đủ', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' }
    case 'OVERPAID':
      return { label: `Nộp dư ${formatMoney(statement.overpaidAmount)} — cấn trừ kỳ sau`, cls: 'border-teal-200 bg-teal-50 text-teal-800' }
    case 'UNPAID':
      return { label: 'Chưa nộp', cls: 'border-rose-200 bg-rose-50 text-rose-800' }
    default:
      return commissionStatus(statement?.status)
  }
}

export const COMMISSION_PAYMENT_STATUS = {
  REPORTED: { label: 'Chờ admin xác nhận', cls: 'border-sky-200 bg-sky-50 text-sky-800' },
  CONFIRMED: { label: 'Admin đã xác nhận', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  REJECTED: { label: 'Admin chưa thấy tiền', cls: 'border-rose-200 bg-rose-50 text-rose-800' },
}

export function commissionStatus(status) {
  return COMMISSION_STATUS[status] || { label: 'Kỳ đang chạy', cls: 'border-slate-200 bg-slate-50 text-slate-700' }
}

export function periodLabel(periodMonth) {
  if (!periodMonth) return ''
  const [year, month] = periodMonth.split('-')
  return `Tháng ${Number(month)}/${year}`
}

export function canPayCommission(statement) {
  return Boolean(statement?.id) && ['ISSUED', 'OVERDUE'].includes(statement.status) && Number(statement.remainingAmount) > 0
}

export function dueText(statement, now = Date.now()) {
  if (!statement?.dueAt || statement.status === 'PAID') return ''
  const due = new Date(statement.dueAt).getTime()
  const days = Math.ceil((due - now) / 86400000)
  if (days > 1) return `Còn ${days} ngày đến hạn`
  if (days === 1) return 'Hạn nộp trong hôm nay/ngày mai'
  if (statement.overdueDays > 0) return `Quá hạn ${statement.overdueDays} ngày`
  return 'Đã đến hạn'
}

/**
 * Cảnh báo hoa hồng cho nhà xe (dùng ở tab Hoa hồng và trang Tổng quan), hoặc null nếu không có gì cần làm.
 * Phân biệt rõ số ngày đã quá hạn với số ngày ân hạn trước khi bị khoá.
 */
export function commissionAlert(overview, formatMoney = (v) => `${Number(v || 0).toLocaleString('vi-VN')} đ`, formatWhen = (v) => new Date(v).toLocaleDateString('vi-VN')) {
  if (!overview) return null
  const statements = overview.statements || []
  const grace = overview.graceDays ?? 3
  const unpaid = statements.filter((s) => ['ISSUED', 'OVERDUE'].includes(s.status) && Number(s.remainingAmount) > 0)
  const outstanding = formatMoney(overview.outstandingAmount)
  const paidNote = (s) => (Number(s.paidAmount) > 0 ? ` (đã nộp ${formatMoney(s.paidAmount)}, còn thiếu ${formatMoney(s.remainingAmount)})` : ' (chưa nộp)')
  const worst = unpaid.reduce((a, s) => (!a || Number(s.overdueDays) > Number(a.overdueDays) ? s : a), null)
  if (overview.bookingBlocked && worst) {
    return {
      tone: 'blocked',
      title: 'Đang tạm ngưng nhận đơn mới',
      text: `Bảng kê ${periodLabel(worst.periodMonth)}${paidNote(worst)} đã quá hạn ${worst.overdueDays} ngày (quá ${grace} ngày ân hạn thì bị khoá). Còn phải nộp ${outstanding}. Xe của bạn tạm ẩn với khách. Nộp hoa hồng và bấm「Tôi đã chuyển khoản」để mở lại trong lúc admin đối soát — nếu admin không thấy tiền, khoá lại ngay.`,
    }
  }
  if (worst && worst.status === 'OVERDUE') {
    const left = Math.max(0, grace - Number(worst.overdueDays))
    return {
      tone: 'overdue',
      title: `Hoa hồng ${periodLabel(worst.periodMonth)} đã quá hạn ${worst.overdueDays} ngày`,
      text: `Còn phải nộp ${outstanding}${paidNote(worst)}. ${left > 0 ? `Còn ${left} ngày nữa sẽ bị tạm ngưng nhận đơn mới.` : 'Sắp bị tạm ngưng nhận đơn mới.'}`,
    }
  }
  if (worst) {
    return {
      tone: 'due',
      title: `Hoa hồng ${periodLabel(worst.periodMonth)} chờ nộp`,
      text: `Còn phải nộp ${outstanding}, hạn ${formatWhen(worst.dueAt)}.`,
    }
  }
  const reported = statements.find((s) => s.status === 'PAYMENT_REPORTED')
  if (reported) {
    return {
      tone: 'reported',
      title: `Đã báo chuyển hoa hồng ${periodLabel(reported.periodMonth)}`,
      text: 'Đang chờ admin đối soát sao kê và xác nhận đã nhận tiền.',
    }
  }
  return null
}
