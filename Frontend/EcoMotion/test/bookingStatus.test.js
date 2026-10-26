import test from 'node:test'
import assert from 'node:assert/strict'
import { BOOKING_FILTERS, BOOKING_STATUS_META, bookingMatchesFilter, bookingStatusMeta } from '../src/utils/bookingStatus.js'
import {
  formatCountdown,
  isPaymentRequestExpired,
  remainingMs,
  countdownUrgency,
} from '../src/utils/paymentDisplay.js'

const statuses = ['PENDING', 'REJECTED', 'ACCEPTED', 'CONFIRMED', 'DRIVER_ASSIGNED',
  'AWAITING_DEPOSIT', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION',
  'IN_PROGRESS', 'AWAITING_END_CONFIRMATION', 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
  'AWAITING_FINAL_PAYMENT', 'COMPLETED', 'CANCELLATION_REVIEW', 'CANCELLED']

test('all backend booking states have one label, style and exclusive filter group', () => {
  const groupIds = BOOKING_FILTERS.filter((filter) => filter.id !== 'ALL').map((filter) => filter.id)
  assert.deepEqual(Object.keys(BOOKING_STATUS_META).sort(), [...statuses].sort())
  for (const status of statuses) {
    const meta = bookingStatusMeta(status)
    assert.ok(meta.label)
    assert.ok(meta.badgeClass)
    assert.equal(groupIds.filter((group) => bookingMatchesFilter(status, group)).length, 1)
  }
})

test('payment actions exist only for deposit and final-payment states', () => {
  const withAction = statuses.filter((status) => bookingStatusMeta(status).action)
  assert.deepEqual(withAction, ['ACCEPTED', 'AWAITING_DEPOSIT', 'AWAITING_FINAL_PAYMENT'])
  assert.ok(bookingStatusMeta('UNKNOWN').label)
})

test('payment expiry changes exactly at the server deadline', () => {
  const request = { expiresAt: '2026-10-21T08:00:00.000Z' }
  assert.equal(isPaymentRequestExpired(request, Date.parse('2026-10-21T07:59:59.999Z')), false)
  assert.equal(isPaymentRequestExpired(request, Date.parse('2026-10-21T08:00:00.000Z')), true)
  assert.equal(isPaymentRequestExpired({ expiresAt: null }, Date.now()), false)
})

test('hold countdown formats remaining time and urgency', () => {
  const deadline = '2026-10-21T08:15:00.000Z'
  assert.equal(remainingMs(deadline, Date.parse('2026-10-21T08:00:00.000Z')), 15 * 60 * 1000)
  assert.equal(formatCountdown(15 * 60 * 1000), '15:00')
  assert.equal(formatCountdown(2 * 60 * 1000), '02:00')
  assert.equal(formatCountdown(-1), '00:00')
  assert.equal(countdownUrgency(3 * 60 * 1000), 'ok')
  assert.equal(countdownUrgency(90 * 1000), 'urgent')
  assert.equal(countdownUrgency(0), 'expired')
})
