export const canReviewRefund = status => ['REQUESTED', 'UNDER_REVIEW', 'FAILED'].includes(status)

export function refundReviewPayload(action, reason = '') {
  if (!['APPROVE', 'REJECT', 'CONTINUE', 'MANUAL'].includes(action)) throw new Error('Hành động duyệt không hợp lệ.')
  const note = reason.trim()
  if (action === 'REJECT' && (!note || note.length > 500)) throw new Error('Lý do từ chối phải từ 1 đến 500 ký tự.')
  return action === 'APPROVE' ? { action } : { action, rejectionReason: note }
}

export function incidentResolutionPayload(note) {
  const resolutionNote = note.trim()
  if (!resolutionNote || resolutionNote.length > 1000) throw new Error('Ghi chú xử lý phải từ 1 đến 1000 ký tự.')
  return { resolutionNote, status: 'RESOLVED' }
}

export function chargeQuote(booking, type, quantity, enteredAmount) {
  if (type === 'OVERTIME') {
    const unitPrice = Number(booking.slotPrice)
    const appliedRate = 1 + Number(booking.overtimeSurchargeRatio || 0)
    return { unitPrice, appliedRate, amount: Math.round(unitPrice * appliedRate * Number(quantity) * 100) / 100 }
  }
  if (type === 'TOLL' || type === 'PARKING' || type === 'OTHER') {
    const amount = Math.round(Number(enteredAmount) * 100) / 100
    return { unitPrice: amount, appliedRate: 1, amount }
  }
  throw new Error('Loại phụ phí không hợp lệ.')
}
