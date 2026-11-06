import test from 'node:test'
import assert from 'node:assert/strict'
import { getTripPhase, groupTripsByPhase, vietnamDateOffset } from '../src/utils/driverTripPhase.js'
import { formatTripDateTime, getRelativeDayLabel } from '../src/utils/driverTripUtils.js'

const trip = (bookingCode, executionStatus, extra = {}) => ({ bookingCode, executionStatus, status: executionStatus, ...extra })

test('phase follows backend execution status, never pickup time', () => {
  assert.equal(getTripPhase(trip('A', 'IN_PROGRESS', { scheduledPickupAt: '2020-01-01T00:00:00Z' })), 'IN_PROGRESS')
  assert.equal(getTripPhase(trip('B', 'ARRIVED')), 'IN_PROGRESS')
  assert.equal(getTripPhase(trip('C', 'ASSIGNED')), 'UPCOMING')
  assert.equal(getTripPhase(trip('D', 'CONFIRMED')), 'UPCOMING')
  assert.equal(getTripPhase(trip('E', 'AWAITING_EXTRA_CHARGE_CONFIRMATION')), 'COMPLETED')
  assert.equal(getTripPhase(trip('F', 'CANCELLED')), 'COMPLETED')
})

test('IN_PROGRESS trip is never placed under upcoming, even if it appears in an upcoming list', () => {
  const groups = groupTripsByPhase([trip('A', 'IN_PROGRESS'), trip('C', 'ASSIGNED')])
  assert.deepEqual(groups.UPCOMING.map((t) => t.bookingCode), ['C'])
  assert.deepEqual(groups.IN_PROGRESS.map((t) => t.bookingCode), ['A'])
})

test('duplicate booking rows are shown once', () => {
  const groups = groupTripsByPhase([trip('A', 'IN_PROGRESS'), trip('A', 'IN_PROGRESS'), trip('C', 'ASSIGNED'), trip('C', 'ASSIGNED')])
  assert.equal(groups.IN_PROGRESS.length + groups.UPCOMING.length + groups.COMPLETED.length, 2)
})

test('trip shown in the active banner is excluded from the list', () => {
  const groups = groupTripsByPhase([trip('A', 'IN_PROGRESS'), trip('B', 'ARRIVED')], { excludeBookingCodes: ['A'] })
  assert.deepEqual(groups.IN_PROGRESS.map((t) => t.bookingCode), ['B'])
})

test('timezone: a trip at 00:30 Vietnam time on 7 Nov is "today" for Vietnam, regardless of UTC date', () => {
  // 2026-11-06T17:30Z = 2026-11-07 00:30 in Asia/Ho_Chi_Minh (UTC+7)
  const tripAt = '2026-11-06T17:30:00Z'
  const reference = new Date('2026-11-07T02:00:00+07:00')
  assert.equal(getRelativeDayLabel(tripAt, reference), 'Hôm nay')
  assert.match(formatTripDateTime(tripAt), /^00:30 - .*07\/11\/2026$/)
})

test('vietnamDateOffset uses Vietnam calendar day, not the UTC day', () => {
  // 2026-11-06T18:30Z = 2026-11-07 01:30 in Vietnam
  const now = new Date('2026-11-06T18:30:00Z')
  assert.equal(vietnamDateOffset(0, now), '2026-11-07')
  assert.equal(vietnamDateOffset(1, now), '2026-11-08')
  // month boundary
  assert.equal(vietnamDateOffset(1, new Date('2026-11-30T18:00:00Z')), '2026-12-02')
})
