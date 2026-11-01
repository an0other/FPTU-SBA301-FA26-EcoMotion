export const canReviewRefund = status => ['REQUESTED', 'UNDER_REVIEW'].includes(status)

export function refundReviewPayload(action, reason = '') {
  if (!['APPROVE', 'REJECT'].includes(action)) throw new Error('Hành động duyệt không hợp lệ.')
  const note = reason.trim()
  if (action === 'REJECT' && (!note || note.length > 500)) throw new Error('Lý do từ chối phải từ 1 đến 500 ký tự.')
  return action === 'APPROVE' ? { action } : { action, rejectionReason: note }
}

export function incidentResolutionPayload(note) {
  const resolutionNote = note.trim()
  if (!resolutionNote || resolutionNote.length > 1000) throw new Error('Ghi chú xử lý phải từ 1 đến 1000 ký tự.')
  return { resolutionNote, status: 'RESOLVED' }
}

export function chargeQuote(booking, type, quantity) {
  const slot = Number(booking.slotPrice)
  const config = {
    OVERTIME: [slot, 1 + Number(booking.overtimeSurchargeRatio || 0)],
    WAITING: [Number(booking.waitingRate), 1],
    OUT_OF_RADIUS: [Number(booking.outOfRadiusRate), 1],
    WEEKEND: [slot, Number(booking.weekendSurchargeRatio)],
  }[type]
  if (!config) throw new Error('Loại phụ phí không hợp lệ.')
  const [unitPrice, appliedRate] = config
  return { unitPrice, appliedRate, amount: Math.round(unitPrice * appliedRate * Number(quantity) * 100) / 100 }
}
