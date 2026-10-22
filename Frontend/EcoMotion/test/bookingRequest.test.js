import test from 'node:test'
import assert from 'node:assert/strict'
import { availabilityWindow, buildBookingPayload, hasValidPickup, overlapsOccupied } from '../src/utils/bookingAvailability.js'

test('availability window starts at Vietnam midnight and covers projected end', () => {
  const window = availabilityWindow('2026-10-20T03:00:00Z', '2026-10-22T03:00:00Z')
  assert.equal(window.from, '2026-10-19T17:00:00.000Z')
  assert.equal(window.to, '2026-10-22T03:00:00.000Z')
})

test('occupied slot exact edges do not overlap but real intersections do', () => {
  const slots = [{ startAt: '2026-10-20T03:00:00Z', endAt: '2026-10-20T05:00:00Z' }]
  assert.equal(overlapsOccupied('2026-10-20T01:00:00Z', '2026-10-20T03:00:00Z', slots), false)
  assert.equal(overlapsOccupied('2026-10-20T05:00:00Z', '2026-10-20T06:00:00Z', slots), false)
  assert.equal(overlapsOccupied('2026-10-20T04:00:00Z', '2026-10-20T06:00:00Z', slots), true)
})

test('pickup validation and create payload retain exact coordinates and note', () => {
  const pickup = { address: 'Bến xe Miền Đông mới', latitude: 10.8791, longitude: 106.8146 }
  assert.equal(hasValidPickup(pickup), true)
  assert.equal(hasValidPickup({ address: 'Chỉ có địa chỉ', latitude: null, longitude: null }), false)
  assert.equal(hasValidPickup({ address: 'Thiếu kinh độ', latitude: 10.8791, longitude: null }), false)
  assert.equal(hasValidPickup({ address: 'x', latitude: 91, longitude: 0 }), false)
  const payload = buildBookingPayload({ vehicleId: 1, planId: 2, slotCount: 1,
    pickupTime: '2027-01-01T01:00:00Z', pickup, passengerCount: 2, driverNote: 'Có trẻ nhỏ' })
  assert.equal(payload.pickupLatitude, 10.8791)
  assert.equal(payload.pickupLongitude, 106.8146)
  assert.equal(payload.driverNote, 'Có trẻ nhỏ')
})
