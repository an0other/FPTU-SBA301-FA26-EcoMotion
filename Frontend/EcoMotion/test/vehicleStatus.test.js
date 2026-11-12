import test from 'node:test'
import assert from 'node:assert/strict'
import {
  VEHICLE_STATUS_BADGES,
  VEHICLE_STATUS_FILTERS,
  VEHICLE_STATUS_LABELS,
} from '../src/utils/vehicleStatus.js'

test('vehicle status filters match backend VehicleStatus enum and exclude BLOCKED', () => {
  assert.deepEqual(VEHICLE_STATUS_FILTERS, [
    'ALL',
    'PENDING',
    'APPROVED',
    'REJECTED',
    'HIDDEN',
  ])
  assert.equal(VEHICLE_STATUS_FILTERS.includes('BLOCKED'), false)
  for (const status of VEHICLE_STATUS_FILTERS) {
    assert.ok(VEHICLE_STATUS_LABELS[status], `Missing label for ${status}`)
  }
})

test('vehicle status badges cover every vehicle and document status', () => {
  for (const status of ['PENDING', 'APPROVED', 'REJECTED', 'HIDDEN', 'VERIFIED', 'EXPIRED']) {
    assert.ok(VEHICLE_STATUS_BADGES[status], `Missing badge style for ${status}`)
  }
})

