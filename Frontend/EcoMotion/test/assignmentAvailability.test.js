import test from 'node:test'
import assert from 'node:assert/strict'
import { assignmentStatuses, filterAssignmentDrivers } from '../src/utils/assignmentAvailability.js'

const drivers = [
  { driverId: 1, fullName: 'Nguyễn Văn An', eligible: false, availability: { availabilityStatus: 'ON_TRIP' } },
  { driverId: 2, fullName: 'Trần Bình', eligible: true, availability: { availabilityStatus: 'AVAILABLE' } },
  { driverId: 3, fullName: 'Lê Chi', eligible: false, availability: { availabilityStatus: 'OFFLINE' } },
]

test('assignment statuses include an explicit uncertain return with Vietnamese labels and badge colors', () => {
  assert.deepEqual(Object.keys(assignmentStatuses).sort(), ['AVAILABLE', 'OFFLINE', 'ON_TRIP', 'PENDING_ASSIGNMENT', 'SCHEDULE_CONFLICT', 'UNAVAILABLE', 'UNCERTAIN'].sort())
  for (const status of Object.values(assignmentStatuses)) {
    assert.ok(status.label)
    assert.match(status.color, /bg-/)
  }
})

test('all drivers remain visible, eligible drivers sort first without mutating the response', () => {
  assert.deepEqual(filterAssignmentDrivers(drivers, '', 'ALL').map(d => d.driverId), [2, 1, 3])
  assert.equal(drivers[0].driverId, 1)
})

test('eligibility and current status filters use independent backend fields', () => {
  const busyButEligible = { ...drivers[0], eligible: true }
  assert.equal(filterAssignmentDrivers([busyButEligible], '', 'ELIGIBLE').length, 1)
  assert.equal(filterAssignmentDrivers([busyButEligible], '', 'ON_TRIP').length, 1)
  assert.equal(filterAssignmentDrivers(drivers, '', 'OFFLINE')[0].driverId, 3)
})

test('search and status filter combine and unknown status stays unclassified', () => {
  assert.equal(filterAssignmentDrivers(drivers, '  NGUYỄN ', 'ON_TRIP').length, 1)
  assert.equal(filterAssignmentDrivers(drivers, 'Nguyễn', 'ELIGIBLE').length, 0)
  assert.equal(filterAssignmentDrivers([{ fullName: 'A' }], '', 'AVAILABLE').length, 0)
})
