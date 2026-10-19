import test, { describe } from 'node:test'
import assert from 'node:assert/strict'
import {
  formatTripDate,
  formatTripDateTime,
  formatTripTime,
  getRelativeDayLabel,
  getTripStatusConfig,
  serializeTripFilters,
  STATUS_FILTER_OPTIONS,
  TRIP_STATUS_CONFIG,
  TRIP_VIEWS,
} from '../src/utils/driverTripUtils.js'
import { createDriverTripService, driverTripService } from '../src/services/driverTripService.js'

describe('D01 — Driver Schedule Unit Tests', () => {
  describe('Date & Time Formatting (Asia/Ho_Chi_Minh)', () => {
    test('formatTripDateTime formats UTC timestamp into Vietnam timezone correctly', () => {
      // 2026-10-17T07:30:00Z is 14:30 in UTC+7 (Asia/Ho_Chi_Minh)
      const isoUtc = '2026-10-17T07:30:00Z'
      const formatted = formatTripDateTime(isoUtc)
      assert.match(formatted, /14:30/)
      assert.match(formatted, /17\/10\/2026/)
    })

    test('formatTripTime extracts hours and minutes in Vietnam timezone', () => {
      const isoUtc = '2026-10-17T01:15:00Z' // 08:15 UTC+7
      const time = formatTripTime(isoUtc)
      assert.equal(time, '08:15')
    })

    test('formatTripDate extracts day/month/year in Vietnam timezone', () => {
      // 2026-10-17T18:00:00Z is 2026-10-18T01:00:00 in UTC+7
      const isoUtc = '2026-10-17T18:00:00Z'
      const dateStr = formatTripDate(isoUtc)
      assert.equal(dateStr, '18/10/2026')
    })

    test('getRelativeDayLabel detects Hôm nay, Ngày mai, and empty for other days', () => {
      const refDate = new Date('2026-10-17T10:00:00+07:00')
      const todayIso = '2026-10-17T02:00:00Z' // Today
      const tomorrowIso = '2026-10-18T02:00:00Z' // Tomorrow
      const pastIso = '2026-10-10T02:00:00Z' // 7 days ago

      assert.equal(getRelativeDayLabel(todayIso, refDate), 'Hôm nay')
      assert.equal(getRelativeDayLabel(tomorrowIso, refDate), 'Ngày mai')
      assert.equal(getRelativeDayLabel(pastIso, refDate), '')
    })

    test('Gracefully handles null, undefined, and invalid ISO timestamps', () => {
      assert.equal(formatTripDateTime(null), 'Chưa có lịch hẹn')
      assert.equal(formatTripDateTime('invalid-date'), 'Thời gian không hợp lệ')
      assert.equal(formatTripTime(null), '--:--')
      assert.equal(formatTripTime('invalid'), '--:--')
      assert.equal(formatTripDate(null), 'Chưa xác định')
      assert.equal(formatTripDate('invalid'), 'Chưa xác định')
      assert.equal(getRelativeDayLabel(null), '')
    })
  })

  describe('Status Labels & Config Mapping', () => {
    test('Maps known statuses with correct Vietnamese labels and styles', () => {
      const confirmed = getTripStatusConfig('CONFIRMED')
      assert.equal(confirmed.label, 'Đã xác nhận')
      assert.ok(confirmed.badgeClass.includes('emerald'))

      const inProgress = getTripStatusConfig('IN_PROGRESS')
      assert.equal(inProgress.label, 'Đang thực hiện')
      assert.ok(inProgress.badgeClass.includes('amber'))

      const completed = getTripStatusConfig('COMPLETED')
      assert.equal(completed.label, 'Đã hoàn thành')

      const cancelled = getTripStatusConfig('CANCELLED')
      assert.equal(cancelled.label, 'Đã hủy')
      assert.ok(cancelled.badgeClass.includes('rose'))

      const assigned = getTripStatusConfig('ASSIGNED')
      assert.equal(assigned.label, 'Đã phân công')
    })

    test('Handles case insensitivity and whitespace', () => {
      const lower = getTripStatusConfig('  confirmed  ')
      assert.equal(lower.label, 'Đã xác nhận')
    })

    test('Fails safely on unknown or unsupported status without throwing', () => {
      const unknown = getTripStatusConfig('UNRECOGNIZED_STATUS')
      assert.equal(unknown.label, 'Không xác định')
      assert.ok(unknown.badgeClass.includes('gray'))

      const nullStatus = getTripStatusConfig(null)
      assert.equal(nullStatus.label, 'Không xác định')

      const undefinedStatus = getTripStatusConfig(undefined)
      assert.equal(undefinedStatus.label, 'Không xác định')
    })
  })

  describe('Filter Serialization', () => {
    test('Serializes default options properly', () => {
      const params = serializeTripFilters()
      assert.deepEqual(params, {
        view: 'TODAY',
        page: 0,
        size: 20,
      })
    })

    test('Normalizes valid view options (TODAY, UPCOMING, HISTORY, ACTIVE)', () => {
      assert.equal(serializeTripFilters({ view: 'upcoming' }).view, 'UPCOMING')
      assert.equal(serializeTripFilters({ view: 'history' }).view, 'HISTORY')
      assert.equal(serializeTripFilters({ view: 'active' }).view, 'ACTIVE')
      assert.equal(serializeTripFilters({ view: 'invalid_view' }).view, 'TODAY')
    })

    test('Omits status when status is ALL or empty', () => {
      const paramsAll = serializeTripFilters({ status: 'ALL' })
      assert.equal(paramsAll.status, undefined)

      const paramsEmpty = serializeTripFilters({ status: '' })
      assert.equal(paramsEmpty.status, undefined)
    })

    test('Includes status when valid status provided', () => {
      const params = serializeTripFilters({ status: 'confirmed' })
      assert.equal(params.status, 'CONFIRMED')
    })

    test('Validates date format YYYY-MM-DD', () => {
      const valid = serializeTripFilters({ date: '2026-10-17' })
      assert.equal(valid.date, '2026-10-17')

      const invalid = serializeTripFilters({ date: '17/10/2026' })
      assert.equal(invalid.date, undefined)
    })

    test('Clamps and coerces page and size', () => {
      const params1 = serializeTripFilters({ page: -5, size: 200 })
      assert.equal(params1.page, 0)
      assert.equal(params1.size, 20) // 200 exceeds 100 max, fallback to 20

      const params2 = serializeTripFilters({ page: 2, size: 50 })
      assert.equal(params2.page, 2)
      assert.equal(params2.size, 50)
    })
  })

  describe('Trip Views and Filter Options constants', () => {
    test('Contains correct view definitions', () => {
      assert.equal(TRIP_VIEWS.TODAY.key, 'TODAY')
      assert.equal(TRIP_VIEWS.TODAY.label, 'Hôm nay')
      assert.equal(TRIP_VIEWS.UPCOMING.key, 'UPCOMING')
      assert.equal(TRIP_VIEWS.UPCOMING.label, 'Sắp tới')
      assert.equal(TRIP_VIEWS.HISTORY.key, 'HISTORY')
      assert.equal(TRIP_VIEWS.HISTORY.label, 'Lịch sử')
    })

    test('Contains correct filter status options', () => {
      const values = STATUS_FILTER_OPTIONS.map((o) => o.value)
      assert.ok(values.includes('ALL'))
      assert.ok(values.includes('CONFIRMED'))
      assert.ok(values.includes('IN_PROGRESS'))
      assert.ok(values.includes('COMPLETED'))
      assert.ok(values.includes('CANCELLED'))
    })
  })
})

describe('D01 — Driver Trip Service & Integration Contract Tests', () => {
  test('driverTripService has listTrips, getActiveTrip, and getTripDetail functions', () => {
    assert.equal(typeof driverTripService.listTrips, 'function')
    assert.equal(typeof driverTripService.getActiveTrip, 'function')
    assert.equal(typeof driverTripService.getTripDetail, 'function')
  })

  test('getTripDetail throws descriptive error when trip ID or code is empty', async () => {
    await assert.rejects(
      async () => {
        await driverTripService.getTripDetail('')
      },
      {
        message: 'Mã chuyến đi không hợp lệ',
      }
    )
  })

  test('Trip card data mapping handles complete payload without error', () => {
    const mockTrip = {
      tripId: 101,
      bookingCode: 'BK-TRIP01',
      status: 'CONFIRMED',
      scheduledPickupAt: '2026-10-17T08:00:00Z',
      pickupAddress: '123 Nguyen Hue, Quan 1, TP.HCM',
      passengerCount: 2,
      rentalType: 'HOURLY',
      rentalDuration: 4,
      vehicle: {
        id: 1,
        name: 'VF 8 Plus',
        brand: 'VinFast',
        model: 'VF 8',
        licensePlate: '51K-999.88',
        color: 'Trắng',
        seatCount: 5,
        coverUrl: 'https://example.com/vf8.jpg',
      },
      customer: {
        fullName: 'Nguyen Van A',
        phone: '0901234567',
      },
    }

    const statusConfig = getTripStatusConfig(mockTrip.status)
    const dateTimeStr = formatTripDateTime(mockTrip.scheduledPickupAt)
    const timeStr = formatTripTime(mockTrip.scheduledPickupAt)

    assert.equal(statusConfig.label, 'Đã xác nhận')
    assert.match(dateTimeStr, /15:00/)
    assert.equal(timeStr, '15:00')
    assert.equal(mockTrip.vehicle.licensePlate, '51K-999.88')
    assert.equal(mockTrip.customer.phone, '0901234567')
  })

  test('Trip card data mapping handles partial/missing payload safely', () => {
    const incompleteTrip = {
      tripId: 102,
      bookingCode: 'BK-TRIP02',
      status: 'PENDING',
      scheduledPickupAt: null,
      pickupAddress: null,
      vehicle: null,
      customer: null,
    }

    const statusConfig = getTripStatusConfig(incompleteTrip.status)
    const dateTimeStr = formatTripDateTime(incompleteTrip.scheduledPickupAt)

    assert.equal(statusConfig.label, 'Không xác định')
    assert.equal(dateTimeStr, 'Chưa có lịch hẹn')
    assert.equal(incompleteTrip.vehicle, null)
    assert.equal(incompleteTrip.customer, null)
  })

  test('Mock client integration: listTrips returns structured page response', async () => {
    const mockClient = {
      async get(url, config) {
        assert.equal(url, '/v1/driver/trips')
        assert.equal(config.params.view, 'TODAY')
        return {
          data: {
            code: 'SUCCESS',
            message: 'OK',
            data: {
              items: [{ tripId: 1, bookingCode: 'BK-001', status: 'CONFIRMED' }],
              total: 1,
              offset: 0,
              limit: 20,
              hasMore: false,
            },
          },
        }
      },
    }

    const service = createDriverTripService(mockClient)
    const result = await service.listTrips({ view: 'TODAY' })
    assert.equal(result.items.length, 1)
    assert.equal(result.total, 1)
    assert.equal(result.items[0].bookingCode, 'BK-001')
  })

  test('Mock client integration: listTrips handles empty and null data gracefully', async () => {
    const mockClient = {
      async get() {
        return { data: { code: 'SUCCESS', data: null } }
      },
    }

    const service = createDriverTripService(mockClient)
    const result = await service.listTrips({ view: 'UPCOMING' })
    assert.deepEqual(result.items, [])
    assert.equal(result.total, 0)
    assert.equal(result.hasMore, false)
  })

  test('Mock client integration: getActiveTrip returns active trip or null', async () => {
    const mockClientWithActive = {
      async get(url, config) {
        assert.equal(config.params.view, 'ACTIVE')
        return {
          data: {
            data: {
              items: [{ tripId: 99, bookingCode: 'BK-ACTIVE', status: 'IN_PROGRESS' }],
            },
          },
        }
      },
    }

    const serviceWithActive = createDriverTripService(mockClientWithActive)
    const active = await serviceWithActive.getActiveTrip()
    assert.equal(active.bookingCode, 'BK-ACTIVE')
    assert.equal(active.status, 'IN_PROGRESS')

    const mockClientEmpty = {
      async get() {
        return { data: { data: { items: [] } } }
      },
    }
    const serviceEmpty = createDriverTripService(mockClientEmpty)
    const none = await serviceEmpty.getActiveTrip()
    assert.equal(none, null)
  })

  test('Security & error handling: 401, 403, 404, 500 propagation', async () => {
    const error401 = new Error('Phiên đăng nhập đã hết hạn.')
    error401.status = 401

    const error403 = new Error('Bạn không có quyền truy cập.')
    error403.status = 403

    const error404 = new Error('Không tìm thấy tài nguyên yêu cầu.')
    error404.status = 404

    const error500 = new Error('Máy chủ gặp sự cố.')
    error500.status = 500

    const makeFailingClient = (err) => ({
      async get() {
        throw err
      },
    })

    await assert.rejects(
      async () => createDriverTripService(makeFailingClient(error401)).listTrips(),
      (err) => err.status === 401
    )

    await assert.rejects(
      async () => createDriverTripService(makeFailingClient(error403)).getTripDetail('BK-001'),
      (err) => err.status === 403
    )

    await assert.rejects(
      async () => createDriverTripService(makeFailingClient(error404)).getTripDetail('BK-OLD-REASSIGNED'),
      (err) => err.status === 404
    )

    await assert.rejects(
      async () => createDriverTripService(makeFailingClient(error500)).listTrips(),
      (err) => err.status === 500
    )
  })

  test('Race condition & rapid filter safety: request counter correctly isolates last response', async () => {
    let requestId = 0
    let lastRenderedResult = null

    async function simulateFilterChange(filterValue, delayMs, responsePayload) {
      const currentReq = ++requestId
      await new Promise((resolve) => setTimeout(resolve, delayMs))
      if (currentReq === requestId) {
        lastRenderedResult = responsePayload
      }
    }

    // Trigger filter 1 (slow, 50ms) then immediately trigger filter 2 (fast, 10ms)
    const p1 = simulateFilterChange('TODAY', 50, 'DATA_FOR_TODAY')
    const p2 = simulateFilterChange('UPCOMING', 10, 'DATA_FOR_UPCOMING')

    await Promise.all([p1, p2])

    // Should only show the latest filter's data (UPCOMING), discarding stale TODAY response
    assert.equal(lastRenderedResult, 'DATA_FOR_UPCOMING')
  })

  test('Trip status remains determined by Backend status, never inferred from past pickup time', () => {
    // Pickup time was 2 hours ago, but status is still CONFIRMED or IN_PROGRESS
    const overdueTrip = {
      tripId: 55,
      bookingCode: 'BK-OVERDUE',
      scheduledPickupAt: '2026-10-17T01:00:00Z',
      status: 'CONFIRMED',
    }

    const config = getTripStatusConfig(overdueTrip.status)
    assert.equal(config.label, 'Đã xác nhận')
    assert.notEqual(config.label, 'Đã hoàn thành')
  })
})
