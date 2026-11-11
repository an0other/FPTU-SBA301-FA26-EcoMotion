import test from 'node:test'
import assert from 'node:assert/strict'
import { canPayCommission, dueText, periodLabel } from '../src/utils/commissionDisplay.js'

test('period label and payability of a commission statement', () => {
  assert.equal(periodLabel('2026-10'), 'Tháng 10/2026')
  assert.equal(canPayCommission({ id: 1, status: 'OVERDUE', remainingAmount: 400000 }), true)
  assert.equal(canPayCommission({ id: 1, status: 'PAYMENT_REPORTED', remainingAmount: 400000 }), false)
  assert.equal(canPayCommission({ id: null, status: 'ISSUED', remainingAmount: 1 }), false, 'kỳ đang chạy chưa nộp được')
})

test('due text counts down and reports overdue days', () => {
  const now = Date.parse('2026-11-01T00:00:00Z')
  assert.equal(dueText({ status: 'ISSUED', dueAt: '2026-11-05T10:00:00Z' }, now), 'Còn 5 ngày đến hạn')
  assert.equal(dueText({ status: 'OVERDUE', dueAt: '2026-10-05T10:00:00Z', overdueDays: 27 }, now), 'Quá hạn 27 ngày')
  assert.equal(dueText({ status: 'PAID', dueAt: '2026-10-05T10:00:00Z' }, now), '')
})
