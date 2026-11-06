import test from 'node:test'
import assert from 'node:assert/strict'
import { getTripConfirmationView, TRIP_CONFIRMATION_STATUSES } from '../src/utils/tripConfirmationStatus.js'

const AT = '2026-11-06T10:00:00Z'

test('four read-only trip statuses have the agreed Vietnamese titles', () => {
  assert.deepEqual(TRIP_CONFIRMATION_STATUSES, [
    'DRIVER_ARRIVED',
    'AWAITING_START_CONFIRMATION',
    'IN_PROGRESS',
    'AWAITING_END_CONFIRMATION',
  ])
  assert.equal(getTripConfirmationView({ status: 'DRIVER_ARRIVED' }).title, 'Tài xế đã đến điểm đón.')
  assert.equal(getTripConfirmationView({ status: 'AWAITING_START_CONFIRMATION' }).title, 'Đang chờ hoàn tất xác nhận bắt đầu.')
  assert.equal(getTripConfirmationView({ status: 'IN_PROGRESS' }).title, 'Chuyến đi đang diễn ra.')
  assert.equal(getTripConfirmationView({ status: 'AWAITING_END_CONFIRMATION' }).title, 'Đang chờ hoàn tất xác nhận kết thúc.')
})

test('DRIVER_ARRIVED and IN_PROGRESS show no per-party lines', () => {
  const trip = { driverStartConfirmedAt: AT, renterStartConfirmedAt: AT, driverEndConfirmedAt: null, renterEndConfirmedAt: null }
  assert.equal(getTripConfirmationView({ status: 'DRIVER_ARRIVED', trip }).parties, null)
  assert.equal(getTripConfirmationView({ status: 'IN_PROGRESS', trip }).parties, null)
})

test('AWAITING_START_CONFIRMATION shows each party from its own real timestamp', () => {
  const view = getTripConfirmationView({
    status: 'AWAITING_START_CONFIRMATION',
    trip: { driverStartConfirmedAt: AT, renterStartConfirmedAt: null },
  })
  assert.deepEqual(view.parties, [
    { label: 'Tài xế', confirmedAt: AT },
    { label: 'Khách thuê', confirmedAt: null },
  ])
})

test('AWAITING_END_CONFIRMATION reads end timestamps, not start ones', () => {
  const view = getTripConfirmationView({
    status: 'AWAITING_END_CONFIRMATION',
    trip: { driverStartConfirmedAt: AT, renterStartConfirmedAt: AT, driverEndConfirmedAt: null, renterEndConfirmedAt: AT },
  })
  assert.deepEqual(view.parties, [
    { label: 'Tài xế', confirmedAt: null },
    { label: 'Khách thuê', confirmedAt: AT },
  ])
})

test('does not infer a renter confirmation from providerConfirmedAt or booking status', () => {
  // Tài xế kết thúc làm backend tự gán providerConfirmedAt; điều đó không có nghĩa khách đã xác nhận.
  const view = getTripConfirmationView({
    status: 'AWAITING_END_CONFIRMATION',
    providerConfirmedAt: AT,
    renterConfirmedAt: null,
    trip: { driverEndConfirmedAt: AT, renterEndConfirmedAt: null, driverStartConfirmedAt: AT, renterStartConfirmedAt: AT },
  })
  assert.deepEqual(view.parties, [
    { label: 'Tài xế', confirmedAt: AT },
    { label: 'Khách thuê', confirmedAt: null },
  ])
})

test('missing trip data hides party lines instead of guessing "chưa xác nhận"', () => {
  assert.equal(getTripConfirmationView({ status: 'AWAITING_START_CONFIRMATION' }).parties, null)
  assert.equal(getTripConfirmationView({ status: 'AWAITING_END_CONFIRMATION', trip: null }).parties, null)
  // Backend cũ có thể thiếu một trường mốc: không hiển thị một bên nào.
  assert.equal(
    getTripConfirmationView({ status: 'AWAITING_START_CONFIRMATION', trip: { driverStartConfirmedAt: AT } }).parties,
    null,
  )
})

test('statuses outside the four trip states return null', () => {
  for (const status of ['DRIVER_CONFIRMED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', undefined]) {
    assert.equal(getTripConfirmationView({ status }), null, status)
  }
  assert.equal(getTripConfirmationView(null), null)
})
