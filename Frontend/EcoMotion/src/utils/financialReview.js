export const refundOutcomes = ['HOLD', 'TRANSFER_VERIFIED', 'FAILED_VERIFIED', 'WRONG_RECIPIENT']

export function financialReviewNote(entry) {
  const text = entry.payload || ''
  return text.match(/note=(.*?)(?:, evidence|, transaction|\]$)/s)?.[1] || 'Đã ghi nhận thay đổi để đối chiếu.'
}

export function financialReviewLabel(outcome) {
  return ({ HOLD: 'Tiếp tục đối soát', TRANSFER_VERIFIED: 'Đã xác minh chuyển tiền', FAILED_VERIFIED: 'Đã xác minh không chuyển hoặc thu hồi tiền', WRONG_RECIPIENT: 'Đang xử lý chuyển nhầm', DISPUTE: 'Người thuê yêu cầu xem xét', PROVIDER_REPLY: 'Nhà xe phản hồi', SETTLEMENT_RULING: 'Đã phân xử quyết toán', CANCELLATION_RULING: 'Đã phân xử hủy hợp đồng' })[outcome] || 'Cập nhật hồ sơ'
}

export function reconciliationPayload(refund, form, requestId) {
  if (!refund || !Number.isInteger(refund.version) || refund.version < 0) throw new Error('Cần tải lại phiên bản khoản hoàn.')
  if (!refundOutcomes.includes(form.outcome)) throw new Error('Kết quả đối soát không hợp lệ.')
  for (const key of ['note', 'evidenceReference', 'transactionReference']) {
    if (typeof form[key] !== 'string' || !form[key].trim()) throw new Error('Cần kết luận và bằng chứng đối soát.')
  }
  if (!requestId) throw new Error('Thiếu mã yêu cầu.')
  return { version: refund.version, requestId, outcome: form.outcome, note: form.note.trim(), evidenceReference: form.evidenceReference.trim(), transactionReference: form.transactionReference.trim() }
}

export function yearlyRulingPayload(dispute, workflow, form, requestId) {
  if (!requestId || !form.note?.trim()) throw new Error('Cần mã yêu cầu và kết luận.')
  const body = { ...form, requestId, disputeId: dispute.id }
  const money = key => {
    const raw = form[key]
    const value = Number(raw)
    if (raw == null || String(raw).trim() === '' || !Number.isFinite(value) || value < 0) throw new Error('Số tiền không hợp lệ.')
    return value
  }
  if (dispute.category === 'YEARLY_CANCELLATION') {
    if (!form.evidenceReference?.trim()) throw new Error('Cần chứng cứ theo hợp đồng.')
    body.refundAmount = money('refundAmount')
  } else {
    if (!Number.isInteger(workflow.version) || workflow.version < 0 || !form.returnedOn || !Number.isInteger(Number(form.finalKm)) || Number(form.finalKm) < 0
      || !Number.isInteger(Number(form.evidenceId)) || Number(form.evidenceId) < 1) throw new Error('Cần phiên bản, ngày trả, số km và bằng chứng hợp lệ.')
    Object.assign(body, { version: workflow.version, finalKm: Number(form.finalKm), evidenceId: Number(form.evidenceId), otherCharges: money('otherCharges'), depositDeduction: money('depositDeduction') })
  }
  return body
}
