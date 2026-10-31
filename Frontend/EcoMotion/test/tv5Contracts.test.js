import test from 'node:test'
import assert from 'node:assert/strict'
import { canReviewRefund, refundReviewPayload, incidentResolutionPayload, chargeQuote } from '../src/utils/tv5Contracts.js'

test('refund review accepts backend review states and sends the required contract', () => {
  assert.equal(canReviewRefund('REQUESTED'), true)
  assert.equal(canReviewRefund('UNDER_REVIEW'), true)
  assert.equal(canReviewRefund('PENDING'), false)
  assert.equal(canReviewRefund('REFUNDED'), false)
  assert.deepEqual(refundReviewPayload('APPROVE', 'ignored'), { action: 'APPROVE' })
  assert.deepEqual(refundReviewPayload('REJECT', '  Thiếu chứng từ  '), { action: 'REJECT', rejectionReason: 'Thiếu chứng từ' })
  assert.throws(() => refundReviewPayload('REJECT', '  '))
  assert.throws(() => refundReviewPayload('REJECT', 'x'.repeat(501)))
})

test('incident resolution uses the exact DTO fields and validates the note', () => {
  assert.deepEqual(incidentResolutionPayload('  Đã xử lý  '), { resolutionNote: 'Đã xử lý', status: 'RESOLVED' })
  assert.throws(() => incidentResolutionPayload(' '))
  assert.throws(() => incidentResolutionPayload('x'.repeat(1001)))
})

test('charge preview uses overtime policy and entered money for toll, parking and other', () => {
  const policy = { slotPrice: 100000, overtimeSurchargeRatio: 0.2 }
  assert.equal(chargeQuote(policy, 'OVERTIME', 2).amount, 240000)
  assert.equal(chargeQuote(policy, 'PARKING', 1, 20000).amount, 20000)
  assert.equal(chargeQuote(policy, 'TOLL', 1, 15000).amount, 15000)
  assert.throws(() => chargeQuote(policy, 'WEEKEND', 2))
  assert.throws(() => chargeQuote(policy, 'WAITING', 3))
})
