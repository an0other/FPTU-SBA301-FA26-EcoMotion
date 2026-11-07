export function bankReceiptPayload(form, reconciliationId, now = Date.now()) {
  const amount = String(form.receivedAmount ?? '').trim()
  const bankTransactionRef = String(form.bankTransactionRef ?? '').trim()
  const bankStatementReference = String(form.bankStatementReference ?? '').trim()
  const receivedAt = new Date(form.receivedAt)
  const note = String(form.note ?? '').trim()
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0)
    throw new Error('Nhập số tiền thực nhận hợp lệ, tối đa 2 chữ số thập phân.')
  if (!bankTransactionRef || bankTransactionRef.length > 100 || !bankStatementReference || bankStatementReference.length > 200)
    throw new Error('Cần mã giao dịch ngân hàng và tham chiếu sao kê tài khoản nhận.')
  if (form.bankReceiptVerified !== true || !Number.isFinite(receivedAt.getTime()) || receivedAt.getTime() > now)
    throw new Error('Cần xác minh sao kê và nhập thời điểm thực nhận hợp lệ.')
  if (!reconciliationId || reconciliationId.length > 64 || note.length > 500)
    throw new Error('Mã yêu cầu hoặc ghi chú không hợp lệ.')
  return { receivedAmount: amount, bankTransactionRef, bankStatementReference,
    bankReceiptVerified: true, reconciliationId, receivedAt: receivedAt.toISOString(), note }
}
