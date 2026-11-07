import test from 'node:test'
import assert from 'node:assert/strict'
import { reconciliationPayload, yearlyRulingPayload } from '../src/utils/financialReview.js'

const proof = { outcome: 'HOLD', note: ' Bank trace ', evidenceReference: ' proof ', transactionReference: ' original-ref ' }
test('reconciliation carries optimistic version and stable request key without asserting payment', () => {
  const result = reconciliationPayload({ version: 3 }, proof, 'same-request')
  assert.deepEqual(result, { version: 3, requestId: 'same-request', outcome: 'HOLD', note: 'Bank trace', evidenceReference: 'proof', transactionReference: 'original-ref' })
  assert.deepEqual(reconciliationPayload({ version: 3 }, proof, 'same-request'), result)
  assert.equal('amount' in result, false)
})
test('reconciliation blocks missing proof, stale data and invented resolution codes', () => {
  assert.throws(() => reconciliationPayload({}, proof, 'a'))
  assert.throws(() => reconciliationPayload({ version: 0 }, { ...proof, evidenceReference: '' }, 'a'))
  assert.throws(() => reconciliationPayload({ version: 0 }, { ...proof, outcome: 'AUTO_REFUND_AGAIN' }, 'a'))
})
test('pre-handover cancellation ruling can retain money only through explicit zero award with evidence', () => {
  const result = yearlyRulingPayload({ id: 8, category: 'YEARLY_CANCELLATION' }, {}, { refundAmount: '0', note: 'Contract reviewed', evidenceReference: 'contract' }, 'request')
  assert.equal(result.refundAmount, 0)
  assert.equal(result.disputeId, 8)
  assert.throws(() => yearlyRulingPayload({ id: 8, category: 'YEARLY_CANCELLATION' }, {}, { refundAmount: '-1', note: 'x', evidenceReference: 'x' }, 'r'))
})
test('settlement ruling carries corrected inputs rather than an unverified paid amount', () => {
  const result = yearlyRulingPayload({ id: 8, category: 'YEARLY_SETTLEMENT' }, { version: 2 }, { returnedOn: '2026-11-08', finalKm: '140', evidenceId: '7', otherCharges: '0', depositDeduction: '100000', note: 'Verified' }, 'request')
  assert.equal(result.version, 2)
  assert.equal(result.depositDeduction, 100000)
  assert.equal(result.evidenceId, 7)
  assert.equal('paidAmount' in result, false)
})
test('settlement cannot submit nonfinite money or fractional kilometer reading', () => {
  const form = { returnedOn: '2026-11-08', finalKm: '1.5', evidenceId: '7', otherCharges: '0', depositDeduction: '0', note: 'Verified' }
  assert.throws(() => yearlyRulingPayload({ id: 1 }, { version: 0 }, form, 'a'))
  assert.throws(() => yearlyRulingPayload({ id: 1 }, { version: 0 }, { ...form, finalKm: '10', otherCharges: 'Infinity' }, 'a'))
})
