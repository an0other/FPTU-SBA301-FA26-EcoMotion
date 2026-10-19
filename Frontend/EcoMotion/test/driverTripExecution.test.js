import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import {
  deriveExecutionStatus,
  getExecutionStatusConfig,
  isLocationStale,
} from '../src/utils/driverTripUtils.js'
import { createDriverTripService } from '../src/services/driverTripService.js'
import { providerBookingService } from '../src/services/providerBookingService.js'

describe('D02 — Driver Trip Execution & State Machine', () => {
  describe('deriveExecutionStatus', () => {
    test('Derives ASSIGNED when driver is assigned but unconfirmed', () => {
      const trip = {
        status: 'ACCEPTED',
        driverConfirmedAt: null,
        actualArrivedAt: null,
        actualStartAt: null,
        actualEndAt: null,
      }
      assert.equal(deriveExecutionStatus(trip), 'ASSIGNED')
    })

    test('Derives CONFIRMED when driverConfirmedAt is set', () => {
      const trip = {
        status: 'ACCEPTED',
        driverConfirmedAt: '2026-10-17T09:00:00Z',
        actualArrivedAt: null,
        actualStartAt: null,
        actualEndAt: null,
      }
      assert.equal(deriveExecutionStatus(trip), 'CONFIRMED')
    })

    test('Derives ARRIVED when actualArrivedAt is set', () => {
      const trip = {
        status: 'ACCEPTED',
        driverConfirmedAt: '2026-10-17T09:00:00Z',
        actualArrivedAt: '2026-10-17T09:15:00Z',
        actualStartAt: null,
        actualEndAt: null,
      }
      assert.equal(deriveExecutionStatus(trip), 'ARRIVED')
    })

    test('Derives IN_PROGRESS when status or actualStartAt is active', () => {
      const trip = {
        status: 'IN_PROGRESS',
        actualStartAt: '2026-10-17T09:20:00Z',
      }
      assert.equal(deriveExecutionStatus(trip), 'IN_PROGRESS')
    })

    test('Derives COMPLETED when status is COMPLETED', () => {
      const trip = {
        status: 'COMPLETED',
        actualEndAt: '2026-10-17T11:00:00Z',
      }
      assert.equal(deriveExecutionStatus(trip), 'COMPLETED')
    })

    test('Derives CANCELLED when status is CANCELLED', () => {
      const trip = {
        status: 'CANCELLED',
      }
      assert.equal(deriveExecutionStatus(trip), 'CANCELLED')
    })

    test('Prefers explicit backend executionStatus if provided', () => {
      const trip = {
        executionStatus: 'ARRIVED',
        status: 'ACCEPTED',
      }
      assert.equal(deriveExecutionStatus(trip), 'ARRIVED')
    })
  })

  describe('getExecutionStatusConfig Action Mapping', () => {
    test('ASSIGNED renders "Xác nhận nhận chuyến" with actionType confirm', () => {
      const config = getExecutionStatusConfig('ASSIGNED')
      assert.equal(config.actionLabel, 'Xác nhận nhận chuyến')
      assert.equal(config.actionType, 'confirm')
    })

    test('CONFIRMED renders "Đã đến điểm đón" with actionType arrive', () => {
      const config = getExecutionStatusConfig('CONFIRMED')
      assert.equal(config.actionLabel, 'Đã đến điểm đón')
      assert.equal(config.actionType, 'arrive')
    })

    test('ARRIVED renders "Bắt đầu chuyến" with actionType start', () => {
      const config = getExecutionStatusConfig('ARRIVED')
      assert.equal(config.actionLabel, 'Bắt đầu chuyến')
      assert.equal(config.actionType, 'start')
    })

    test('IN_PROGRESS renders "Kết thúc chuyến" with actionType finish', () => {
      const config = getExecutionStatusConfig('IN_PROGRESS')
      assert.equal(config.actionLabel, 'Kết thúc chuyến')
      assert.equal(config.actionType, 'finish')
    })

    test('COMPLETED and CANCELLED have no action buttons', () => {
      const completed = getExecutionStatusConfig('COMPLETED')
      assert.equal(completed.actionLabel, null)

      const cancelled = getExecutionStatusConfig('CANCELLED')
      assert.equal(cancelled.actionLabel, null)
    })
  })

  describe('isLocationStale Staleness Evaluation', () => {
    test('Marks null or empty timestamp as stale', () => {
      assert.equal(isLocationStale(null), true)
      assert.equal(isLocationStale(undefined), true)
    })

    test('Recent timestamp within 60 seconds is not stale', () => {
      const recent = new Date(Date.now() - 10000).toISOString()
      assert.equal(isLocationStale(recent, 60), false)
    })

    test('Timestamp older than 60 seconds is stale', () => {
      const old = new Date(Date.now() - 120000).toISOString()
      assert.equal(isLocationStale(old, 60), true)
    })
  })
})

describe('D02 / O08 — Service API Contract Tests', () => {
  test('driverTripService action methods call proper API endpoints', async () => {
    const calls = []
    const mockClient = {
      async post(url, body) {
        calls.push({ method: 'POST', url, body })
        return { data: { code: 'SUCCESS', data: { status: 'OK' } } }
      },
      async get(url, config) {
        calls.push({ method: 'GET', url, config })
        return { data: { code: 'SUCCESS', data: { tracking: true } } }
      },
    }

    const service = createDriverTripService(mockClient)

    await service.confirmTrip('BK-100')
    assert.equal(calls[0].url, '/v1/driver/trips/BK-100/confirm')

    await service.arriveTrip('BK-100')
    assert.equal(calls[1].url, '/v1/driver/trips/BK-100/arrive')

    await service.startTrip('BK-100')
    assert.equal(calls[2].url, '/v1/driver/trips/BK-100/start')

    await service.finishTrip('BK-100')
    assert.equal(calls[3].url, '/v1/driver/trips/BK-100/finish')

    await service.sendLocation('BK-100', { latitude: 10.5, longitude: 106.5 })
    assert.equal(calls[4].url, '/v1/driver/trips/BK-100/locations')
    assert.deepEqual(calls[4].body, { latitude: 10.5, longitude: 106.5 })

    await service.getTripTracking('BK-100')
    assert.equal(calls[5].url, '/v1/driver/trips/BK-100/tracking')
  })

  test('providerBookingService has tracking and location methods', () => {
    assert.equal(typeof providerBookingService.getTripTracking, 'function')
    assert.equal(typeof providerBookingService.getTripLocation, 'function')
  })
})
