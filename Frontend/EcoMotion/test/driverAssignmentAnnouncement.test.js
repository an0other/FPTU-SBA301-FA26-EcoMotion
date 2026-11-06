import test from 'node:test'
import assert from 'node:assert/strict'
import { assignmentSnapshot, shouldAnnounceDriverAssigned } from '../src/utils/driverAssignmentAnnouncement.js'

const snap = (driverId, bookingCode = 'EM-1') => ({ bookingCode, driverId })

test('first load never announces, even when a driver is already assigned', () => {
  assert.equal(shouldAnnounceDriverAssigned(null, snap(7)), false)
})

test('a new driver on the same booking announces once', () => {
  assert.equal(shouldAnnounceDriverAssigned(snap(null), snap(7)), true)
  assert.equal(shouldAnnounceDriverAssigned(snap(7), snap(8)), true)
})

test('repeated refetches with the same driver (SSE reconnect or polling) do not announce', () => {
  assert.equal(shouldAnnounceDriverAssigned(snap(7), snap(7)), false)
})

test('losing the driver does not announce an assignment', () => {
  assert.equal(shouldAnnounceDriverAssigned(snap(7), snap(null)), false)
})

test('a different booking never triggers the announcement', () => {
  assert.equal(shouldAnnounceDriverAssigned(snap(7, 'EM-1'), snap(8, 'EM-2')), false)
})

test('assignmentSnapshot uses confirmed booking data and tolerates missing fields', () => {
  assert.deepEqual(assignmentSnapshot({ bookingCode: 'EM-1', driverId: 7, status: 'DRIVER_ASSIGNED' }), { bookingCode: 'EM-1', driverId: 7 })
  assert.deepEqual(assignmentSnapshot({ bookingCode: 'EM-1' }), { bookingCode: 'EM-1', driverId: null })
  assert.equal(assignmentSnapshot(null), null)
})
