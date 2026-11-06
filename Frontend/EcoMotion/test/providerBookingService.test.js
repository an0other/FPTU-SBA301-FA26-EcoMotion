import test from 'node:test'
import assert from 'node:assert/strict'
import apiClient from '../src/services/apiClient.js'
import { providerBookingService } from '../src/services/providerBookingService.js'

// Thay apiClient.get tạm thời để ghi lại mọi URL được gọi; khôi phục sau mỗi test.
async function withRecordedGet(respond, run) {
  const original = apiClient.get
  const calls = []
  apiClient.get = async (url, config) => {
    calls.push(url)
    return respond(url, config)
  }
  try {
    return await run(calls)
  } finally {
    apiClient.get = original
  }
}

test('getAvailableDrivers calls only assign-candidates for the booking code and maps driverId to id', async () => {
  const candidates = [
    { driverId: 7, fullName: 'Tài xế A', eligible: true },
    { driverId: 8, fullName: 'Tài xế B', eligible: false, ineligibilityReason: 'Trùng lịch' },
  ]
  await withRecordedGet(() => ({ data: { data: candidates } }), async (calls) => {
    const drivers = await providerBookingService.getAvailableDrivers('BK-001')

    assert.deepEqual(calls, ['/provider/bookings/BK-001/assign-candidates'])
    assert.equal(drivers.length, 2)
    assert.equal(drivers[0].id, 7)
    assert.equal(drivers[0].eligible, true)
    assert.equal(drivers[1].id, 8)
    assert.equal(drivers[1].eligible, false)
  })
})

test('getAvailableDrivers returns an empty list when no driver is available for the booking', async () => {
  await withRecordedGet(() => ({ data: { data: [] } }), async (calls) => {
    const drivers = await providerBookingService.getAvailableDrivers('BK-002')

    assert.deepEqual(drivers, [])
    assert.deepEqual(calls, ['/provider/bookings/BK-002/assign-candidates'])
  })
})

test('getAvailableDrivers trims the booking code before building the URL', async () => {
  await withRecordedGet(() => ({ data: { data: [] } }), async (calls) => {
    await providerBookingService.getAvailableDrivers('  BK-003  ')

    assert.deepEqual(calls, ['/provider/bookings/BK-003/assign-candidates'])
  })
})

test('getAvailableDrivers rejects a missing or blank code without sending any request', async () => {
  for (const bad of [undefined, null, '', '   ']) {
    await withRecordedGet(() => {
      throw new Error('không được gọi API khi thiếu mã đơn')
    }, async (calls) => {
      await assert.rejects(
        () => providerBookingService.getAvailableDrivers(bad),
        /Thiếu mã đơn thuê/,
      )
      assert.equal(calls.length, 0, `không được gửi request khi mã là ${JSON.stringify(bad)}`)
    })
  }
})
