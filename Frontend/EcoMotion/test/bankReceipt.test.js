import test from 'node:test'
import assert from 'node:assert/strict'
import { bankReceiptPayload } from '../src/utils/bankReceipt.js'
const form = { receivedAmount: '40000', bankTransactionRef: ' BANK-1 ', bankStatementReference: ' STMT-1 ',
  bankReceiptVerified: true, receivedAt: '2026-11-08T01:00:00Z', note: ' Verified bank credit ' }
const now = Date.parse('2026-11-08T02:00:00Z')
test('bank exception carries actual receipt and statement attestation without requesting allocation or refund', () => {
  const payload = bankReceiptPayload(form, 'stable-key', now)
  assert.equal(payload.receivedAmount, '40000')
  assert.equal(payload.bankTransactionRef, 'BANK-1')
  assert.equal(payload.bankStatementReference, 'STMT-1')
  assert.equal(payload.reconciliationId, 'stable-key')
  assert.equal('appliedAmount' in payload, false)
  assert.equal('refundAmount' in payload, false)
  assert.deepEqual(bankReceiptPayload(form, 'stable-key', now), payload)
})
test('bank exception blocks unverified, missing or future receipt evidence', () => {
  for (const bad of [{ bankReceiptVerified: false }, { bankStatementReference: ' ' }, { bankTransactionRef: '' },
    { receivedAt: '' }, { receivedAt: '2026-11-09T00:00:00Z' }])
    assert.throws(() => bankReceiptPayload({ ...form, ...bad }, 'key', now))
})
test('bank exception validates positive bounded money and precision', () => {
  for (const amount of ['0', '-1', 'NaN', 'Infinity', '1.001', '1000000000000', '1e5'])
    assert.throws(() => bankReceiptPayload({ ...form, receivedAmount: amount }, 'key', now))
  assert.equal(bankReceiptPayload({ ...form, receivedAmount: '10000.01' }, 'key', now).receivedAmount, '10000.01')
})
