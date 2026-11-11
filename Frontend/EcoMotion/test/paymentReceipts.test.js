import test from 'node:test'
import assert from 'node:assert/strict'
import { buildPaymentReceipts, excessRefundViews } from '../src/utils/paymentReceipts.js'

test('receipt sums every confirmed incoming transfer of a request, including the overpaid part', () => {
  const receipts = buildPaymentReceipts([
    { direction: 'IN', status: 'PAID', paymentRequestId: 7, paymentRequestType: 'DEPOSIT', amount: 500000, method: 'BANK_TRANSFER', paidAt: '2026-11-10T03:00:00Z' },
    { direction: 'IN', status: 'PAID', paymentRequestId: 7, paymentRequestType: 'DEPOSIT', amount: 50000, method: 'BANK_TRANSFER', verificationMethod: 'ADMIN_RECONCILED', paidAt: '2026-11-10T05:00:00Z' },
    { direction: 'OUT', status: 'PENDING', paymentRequestId: null, amount: 50000 },
    { direction: 'IN', status: 'PENDING', paymentRequestId: 9, amount: 900000 },
  ])
  assert.equal(receipts.length, 1)
  assert.equal(receipts[0].label, 'Tiền cọc')
  assert.equal(receipts[0].amount, 550000)
  assert.equal(receipts[0].adminAdjusted, true)
  assert.equal(receipts[0].lastPaidAt, '2026-11-10T05:00:00Z')
})

test('excess refund can be offset only before settlement', () => {
  const refund = { id: 1, excess: true, status: 'PROCESSING', amount: 20000 }
  assert.equal(excessRefundViews([refund], 'CONFIRMED')[0].canOffset, true)
  assert.equal(excessRefundViews([refund], 'AWAITING_FINAL_PAYMENT')[0].canOffset, false)
  const offset = excessRefundViews([{ ...refund, status: 'REJECTED', rejectionReason: 'Khách chọn cấn trừ vào quyết toán' }], 'CONFIRMED')[0]
  assert.equal(offset.offsetDone, true)
  assert.equal(offset.statusText, 'Đã cấn trừ vào quyết toán')
  assert.deepEqual(excessRefundViews([{ ...refund, excess: false }], 'CONFIRMED'), [])
})
