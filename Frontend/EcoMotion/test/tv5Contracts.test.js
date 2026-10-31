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

test('charge preview follows each snapshot formula rather than a freely entered amount', () => {
  const policy = { slotPrice: 100000, overtimeSurchargeRatio: 0.2, waitingRate: 3000, outOfRadiusRate: 10000, weekendSurchargeRatio: 0.1 }
  assert.equal(chargeQuote(policy, 'OVERTIME', 2).amount, 240000)
  assert.equal(chargeQuote(policy, 'WAITING', 3).amount, 9000)
  assert.equal(chargeQuote(policy, 'OUT_OF_RADIUS', 3).amount, 30000)
  assert.equal(chargeQuote(policy, 'WEEKEND', 2).amount, 20000)
})
