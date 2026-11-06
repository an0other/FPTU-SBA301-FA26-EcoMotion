import test from 'node:test'
import assert from 'node:assert/strict'
import { chargeRequestIdentity } from '../src/utils/chargeRequestIdentity.js'

test('a retry retains its key; changing the charge or booking starts another request', () => {
  let counter = 0
  const createId = () => `key-${++counter}`
  const payload = { chargeType: 'TOLL', amount: 1000, evidenceUrl: 'https://example.test/a' }
  const original = chargeRequestIdentity(null, 'BK1', payload, createId)
  assert.equal(chargeRequestIdentity(original, 'BK1', { ...payload }, createId).id, original.id)
  assert.notEqual(chargeRequestIdentity(original, 'BK1', { ...payload, amount: 2000 }, createId).id, original.id)
  assert.notEqual(chargeRequestIdentity(original, 'BK2', payload, createId).id, original.id)
})
