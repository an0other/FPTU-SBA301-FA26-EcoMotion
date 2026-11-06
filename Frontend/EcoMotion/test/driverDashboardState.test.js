import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DASHBOARD_MODE,
  buildDriverDashboardView,
  canConfirmAssignment,
  countdownSeconds,
  formatCountdown,
  inTripStatusLabel,
} from '../src/utils/driverDashboardState.js'

const NOW = Date.parse('2026-10-28T10:00:00Z')
const iso = (ms) => new Date(ms).toISOString()
const trip = (bookingCode, status, extra = {}) => ({ bookingCode, status, vehicleName: 'Vinfast', pickupAddress: 'Q1', pickupTime: iso(NOW + 3600_000), ...extra })
const status = (activeTrip, nextTrip, op = 'AVAILABLE') => ({ activityStatus: 'AVAILABLE', operationalStatus: op, activeTrip, nextTrip })

test('no trip at all: empty state and availability selector is visible', () => {
  const view = buildDriverDashboardView(status(null, null), NOW)
  assert.equal(view.mode, DASHBOARD_MODE.EMPTY)
  assert.equal(view.showAvailability, true)
})

test('null or missing status never throws and shows the empty state', () => {
  assert.equal(buildDriverDashboardView(null, NOW).mode, DASHBOARD_MODE.EMPTY)
  assert.equal(buildDriverDashboardView({}, NOW).mode, DASHBOARD_MODE.EMPTY)
})

test('a pending assignment shows a countdown from the real deadline and a confirm action', () => {
  const deadline = iso(NOW + 420_000)
  const view = buildDriverDashboardView(status(null, trip('B1', 'DRIVER_ASSIGNED', { assignmentExpiresAt: deadline })), NOW)
  assert.equal(view.mode, DASHBOARD_MODE.PENDING_CONFIRMATION)
  assert.equal(view.countdown, 420)
  assert.equal(canConfirmAssignment(view), true)
  assert.equal(view.showAvailability, true)
})

test('an expired pending assignment shows no countdown and no confirm action', () => {
  const view = buildDriverDashboardView(status(null, trip('B2', 'DRIVER_ASSIGNED', { assignmentExpiresAt: iso(NOW - 1000) })), NOW)
  assert.equal(view.mode, DASHBOARD_MODE.EMPTY)
  assert.equal(canConfirmAssignment(view), false)
})

test('a pending assignment without any deadline is not treated as confirmable', () => {
  const view = buildDriverDashboardView(status(null, trip('B3', 'DRIVER_ASSIGNED', { assignmentExpiresAt: null })), NOW)
  assert.equal(view.mode, DASHBOARD_MODE.EMPTY)
  assert.equal(canConfirmAssignment(view), false)
})

test('a confirmed upcoming trip has no countdown and no confirm action', () => {
  for (const st of ['CONFIRMED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED']) {
    const view = buildDriverDashboardView(status(null, trip('B4', st)), NOW)
    assert.equal(view.mode, DASHBOARD_MODE.UPCOMING_CONFIRMED, st)
    assert.equal(view.countdown, null, st)
    assert.equal(canConfirmAssignment(view), false, st)
  }
})

test('a trip that has started is shown as in-trip and hides the availability selector', () => {
  for (const st of ['AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION']) {
    const view = buildDriverDashboardView(status(trip('R1', st), null, 'ON_TRIP'), NOW)
    assert.equal(view.mode, DASHBOARD_MODE.IN_TRIP, st)
    assert.equal(view.showAvailability, false, st)
    assert.equal(canConfirmAssignment(view), false, st)
  }
})

test('an in-trip booking never leaks as a pending request or an upcoming trip', () => {
  // Driver đã bắt đầu chuyến R1 nhưng backend vẫn trả một yêu cầu cũ R1 trong nextTrip: chỉ hiển thị chuyến đang thực hiện.
  const view = buildDriverDashboardView(
    status(trip('R1', 'IN_PROGRESS'), trip('R1', 'DRIVER_ASSIGNED', { assignmentExpiresAt: iso(NOW + 60_000) })),
    NOW,
  )
  assert.equal(view.mode, DASHBOARD_MODE.IN_TRIP)
  assert.equal(view.trip.bookingCode, 'R1')
  assert.equal(canConfirmAssignment(view), false)
})

test('a terminal next trip (completed, cancelled, rejected) is never shown as actionable', () => {
  for (const st of ['COMPLETED', 'CANCELLED', 'REJECTED', 'CANCELLATION_REVIEW']) {
    const view = buildDriverDashboardView(status(null, trip('T1', st)), NOW)
    assert.equal(view.mode, DASHBOARD_MODE.EMPTY, st)
    assert.equal(canConfirmAssignment(view), false, st)
  }
})

test('after the trip ends, the availability selector is shown again', () => {
  const during = buildDriverDashboardView(status(trip('R2', 'IN_PROGRESS'), null, 'ON_TRIP'), NOW)
  const after = buildDriverDashboardView(status(null, null, 'AVAILABLE'), NOW)
  assert.equal(during.showAvailability, false)
  assert.equal(after.showAvailability, true)
})

test('reloading the same backend data yields the same view (no dependence on local state)', () => {
  const data = status(trip('R3', 'AWAITING_START_CONFIRMATION'), trip('N1', 'CONFIRMED'), 'ON_TRIP')
  assert.deepEqual(buildDriverDashboardView(data, NOW), buildDriverDashboardView(JSON.parse(JSON.stringify(data)), NOW))
})

test('countdown is derived from the deadline and clamps at zero', () => {
  assert.equal(countdownSeconds(iso(NOW + 90_000), NOW), 90)
  assert.equal(countdownSeconds(iso(NOW - 5_000), NOW), 0)
  assert.equal(countdownSeconds(null, NOW), null)
  assert.equal(countdownSeconds('not-a-date', NOW), null)
})

test('formatCountdown renders mm:ss', () => {
  assert.equal(formatCountdown(0), '00:00')
  assert.equal(formatCountdown(65), '01:05')
  assert.equal(formatCountdown(900), '15:00')
})

test('status labels for in-trip stages are Vietnamese and never claim a pending request', () => {
  assert.equal(inTripStatusLabel('AWAITING_START_CONFIRMATION'), 'Chờ xác nhận bắt đầu')
  assert.equal(inTripStatusLabel('IN_PROGRESS'), 'Đang diễn ra')
  assert.equal(inTripStatusLabel('AWAITING_END_CONFIRMATION'), 'Chờ xác nhận kết thúc')
  assert.equal(inTripStatusLabel('UNKNOWN'), 'Đang thực hiện')
})
