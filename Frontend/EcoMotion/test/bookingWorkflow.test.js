import { test } from 'node:test'
import assert from 'node:assert/strict'
import { vietnamInputToInstant } from '../src/utils/bookingWorkflow.js'
import { serializeTripFilters, rentalTypeLabel, deriveExecutionStatus } from '../src/utils/driverTripUtils.js'

test('reschedule input explicitly uses Vietnam time independent of browser timezone', () => {
  assert.equal(vietnamInputToInstant('2026-11-03T09:30'), '2026-11-03T02:30:00.000Z')
  assert.throws(() => vietnamInputToInstant('2026-11-03'))
  assert.throws(() => vietnamInputToInstant('2026-13-03T09:30'))
  assert.throws(() => vietnamInputToInstant('2026-02-31T09:30'))
})
test('driver request queue preserves REQUESTS and hourly plan labels', () => {
  assert.equal(serializeTripFilters({ view: 'REQUESTS' }).view, 'REQUESTS')
  assert.equal(rentalTypeLabel('HOURLY'), 'Theo giờ')
  assert.equal(rentalTypeLabel('MONTHLY'), 'Theo tháng')
  assert.equal(rentalTypeLabel(null), 'Gói thuê đã chọn')
  assert.equal(deriveExecutionStatus({ status: 'AWAITING_FINAL_PAYMENT' }), 'AWAITING_FINAL_PAYMENT')
})
