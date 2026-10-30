import test from 'node:test'
import assert from 'node:assert/strict'
import { createDriverStatusService } from '../src/services/driverStatusService.js'
import { DRIVER_STATUS_LABELS } from '../src/utils/driverStatus.js'
test('status reads the authenticated driver endpoint and preserves server projection', async () => {
  const projection = { activityStatus: 'AVAILABLE', operationalStatus: 'SCHEDULED', nextTrip: { bookingCode: 'test' } }
  const service = createDriverStatusService({ get: async path => {
    assert.equal(path, '/v1/driver/status')
    return { data: { data: projection } }
  } })
  assert.equal(await service.getStatus(), projection)
})
test('readiness update sends no driver identity and propagates conflicts', async () => {
  const conflict = new Error('ON_TRIP')
  const service = createDriverStatusService({ put: async (path, body) => {
    assert.equal(path, '/v1/driver/status')
    assert.deepEqual(body, { activityStatus: 'AVAILABLE' })
    throw conflict
  } })
  await assert.rejects(service.updateStatus('AVAILABLE'), error => error === conflict)
})
test('all five operational states have distinct display labels', () => {
  assert.equal(Object.keys(DRIVER_STATUS_LABELS).length, 5)
  assert.equal(new Set(Object.values(DRIVER_STATUS_LABELS)).size, 5)
})
