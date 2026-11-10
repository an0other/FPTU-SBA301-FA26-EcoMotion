import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  visibleProposal, validateRescheduleInput, minRescheduleInput, RESCHEDULE_LEAD_MESSAGE,
} from '../src/utils/bookingWorkflow.js'

// 2026-11-10T00:00:00Z = 07:00 giờ Việt Nam
const NOW = Date.parse('2026-11-10T00:00:00Z')

test('B4: renter never sees withdrawn or expired proposals; provider keeps the history', () => {
  for (const status of ['WITHDRAWN', 'EXPIRED']) {
    assert.equal(visibleProposal({ id: 'p', status }, { provider: false }), null)
    assert.deepEqual(visibleProposal({ id: 'p', status }, { provider: true }), { id: 'p', status })
  }
  for (const status of ['PENDING', 'ACCEPTED', 'REJECTED']) {
    assert.deepEqual(visibleProposal({ id: 'p', status }, { provider: false }), { id: 'p', status })
  }
  assert.equal(visibleProposal(null, { provider: false }), null)
  assert.equal(visibleProposal(undefined, { provider: true }), null)
})

test('B6: reschedule pickup under 1 hour is rejected with the Vietnamese message', () => {
  const r = validateRescheduleInput('2026-11-10T07:30', NOW)
  assert.equal(r.valid, false)
  assert.equal(r.message, 'Giờ nhận mới phải sau hiện tại ít nhất 1 giờ.')
  assert.equal(r.message, RESCHEDULE_LEAD_MESSAGE)
})

test('B6: exactly 1 hour is rejected like the backend (must be strictly after now + 1h); one minute later is accepted', () => {
  assert.equal(validateRescheduleInput('2026-11-10T08:00', NOW).valid, false)
  const ok = validateRescheduleInput('2026-11-10T08:01', NOW)
  assert.equal(ok.valid, true)
  assert.equal(ok.instant, '2026-11-10T01:01:00.000Z')
})

test('B6: past and far-future values', () => {
  assert.equal(validateRescheduleInput('2026-11-09T07:00', NOW).valid, false)
  assert.equal(validateRescheduleInput('2026-11-12T07:00', NOW).valid, true)
})

test('B6: empty and malformed input is invalid without the lead-time message', () => {
  assert.deepEqual(validateRescheduleInput('', NOW), { valid: false, instant: null, message: '' })
  assert.equal(validateRescheduleInput('2026-02-31T09:30', NOW).valid, false)
  assert.equal(validateRescheduleInput('2026-02-31T09:30', NOW).message, 'Giờ chưa hợp lệ.')
})

test('B6: min attribute is in Vietnam time and is itself always accepted', () => {
  assert.equal(minRescheduleInput(NOW), '2026-11-10T08:01')
  for (const ms of [NOW, NOW + 30000, NOW + 59999, NOW + 123456]) {
    assert.equal(validateRescheduleInput(minRescheduleInput(ms), ms).valid, true)
  }
})

test('B6: a value valid when the form opened becomes invalid if submit happens later', () => {
  const chosen = '2026-11-10T08:30'
  assert.equal(validateRescheduleInput(chosen, NOW).valid, true)
  assert.equal(validateRescheduleInput(chosen, NOW + 31 * 60 * 1000).valid, false)
})
