import test from 'node:test'
import assert from 'node:assert/strict'
import { buildPaymentCategorySummaries } from '../src/utils/paymentCategorySummary.js'

test('each category is taken from paymentType, never from description or notes', () => {
  const [deposit, final, monthly] = buildPaymentCategorySummaries([
    { id: 1, paymentType: 'DEPOSIT', amount: 300000, paidAmount: 300000, remainingAmount: 0, status: 'CONFIRMED', issueNote: 'Trả góp tháng 3 - quyết toán' },
  ])
  assert.equal(deposit.hasRequest, true)
  assert.equal(final.hasRequest, false)
  assert.equal(monthly.hasRequest, false)
})

test('backend-computed amounts are passed through unchanged', () => {
  const [, final] = buildPaymentCategorySummaries([
    { id: 2, paymentType: 'FINAL_BALANCE', amount: 3300000, paidAmount: 330000, remainingAmount: 2970000, status: 'PARTIALLY_PAID' },
  ])
  assert.equal(final.amount, 3300000)
  assert.equal(final.paidAmount, 330000)
  assert.equal(final.remainingAmount, 2970000)
  assert.equal(final.status, 'PARTIALLY_PAID')
})

test('duplicate requests of one type are flagged as anomaly and not guessed', () => {
  const [, final] = buildPaymentCategorySummaries([
    { id: 3, paymentType: 'FINAL_BALANCE', amount: 1 },
    { id: 4, paymentType: 'FINAL_BALANCE', amount: 2 },
  ])
  assert.equal(final.hasRequest, false)
  assert.equal(final.anomaly, true)
})

test('evidences belong to their request and keep their own review status and timestamps', () => {
  const summaries = buildPaymentCategorySummaries([
    {
      id: 5,
      paymentType: 'MONTHLY_INSTALLMENT',
      amount: 100,
      evidences: [{ id: 9, proofImageUrl: 'https://x/y.jpg', transferredAmount: 100, status: 'REJECTED', createdAt: 'a', reviewedAt: 'b' }],
    },
  ])
  const monthly = summaries.find((s) => s.type === 'MONTHLY_INSTALLMENT')
  assert.equal(monthly.evidences.length, 1)
  assert.equal(monthly.evidences[0].status, 'REJECTED')
  assert.equal(monthly.evidences[0].reviewedAt, 'b')
})

test('missing backend fields are declared, not invented', () => {
  const [deposit] = buildPaymentCategorySummaries([{ id: 6, paymentType: 'DEPOSIT', amount: 1 }])
  assert.ok(deposit.missingFields.includes('installmentPeriodNo'))
  assert.equal(deposit.installmentPeriodNo, undefined)
  assert.equal(deposit.installmentDueDate, undefined)
})
