import test from 'node:test'
import assert from 'node:assert/strict'

// Helper mirroring ProviderSchedulePage date filter ISO conversion
const buildDateFilterIso = (startDateStr, endDateStr) => {
  const result = {}
  if (startDateStr) {
    result.fromDate = new Date(`${startDateStr}T00:00:00.000`).toISOString()
  }
  if (endDateStr) {
    result.toDate = new Date(`${endDateStr}T23:59:59.999`).toISOString()
  }
  return result
}

// Helper mirroring ProviderSchedulePage live conflict check logic
const checkLiveConflict = ({ formStartAt, formEndAt, vehicleBusySchedules, maintenanceBufferMinutes = 60 }) => {
  if (!formStartAt || !formEndAt || !vehicleBusySchedules || vehicleBusySchedules.length === 0) return null
  const startMs = new Date(formStartAt).getTime()
  const endMs = new Date(formEndAt).getTime()
  if (isNaN(startMs) || isNaN(endMs) || endMs <= startMs) return null

  const bufferMs = maintenanceBufferMinutes * 60000

  for (const s of vehicleBusySchedules) {
    const sStart = new Date(s.startAt).getTime()
    const sEnd = new Date(s.endAt).getTime()
    if (s.type === 'BOOKED') {
      const conflict = startMs < sEnd + bufferMs && endMs > sStart - bufferMs
      if (conflict) {
        return {
          type: 'BOOKED',
          bookingCode: s.bookingCode || 'BOOKED',
          message: `Khung giờ chọn đang trùng hoặc lấn vào khoảng đệm của đơn khách #${s.bookingCode || 'BOOKED'}`
        }
      }
    } else {
      const conflict = startMs < sEnd && endMs > sStart
      if (conflict) {
        return {
          type: s.type,
          message: `Khung giờ chọn đang trùng với lịch ${s.type === 'MAINTENANCE' ? 'bảo dưỡng' : 'chặn bận'} hiện có`
        }
      }
    }
  }
  return null
}

// Helper mirroring displayedSchedules filtering by status
const filterDisplayedSchedules = (schedules, selectedStatus, nowTime) => {
  if (!selectedStatus) return schedules
  if (selectedStatus === 'ACTIVE') {
    return schedules.filter((item) => item.status === 'ACTIVE' && new Date(item.endAt).getTime() > nowTime)
  }
  if (selectedStatus === 'HELD') {
    return schedules.filter((item) => item.status === 'HELD' && new Date(item.endAt).getTime() > nowTime)
  }
  if (selectedStatus === 'EXPIRED') {
    return schedules.filter((item) => (item.status === 'ACTIVE' || item.status === 'HELD') && new Date(item.endAt).getTime() <= nowTime)
  }
  if (selectedStatus === 'CANCELLED') {
    return schedules.filter((item) => item.status === 'CANCELLED')
  }
  return schedules
}

test('buildDateFilterIso produces full day boundaries without date-only UTC truncation', () => {
  const boundaries = buildDateFilterIso('2026-11-10', '2026-11-10')
  const from = new Date(boundaries.fromDate)
  const to = new Date(boundaries.toDate)

  // From date must be earlier than to date on the same calendar day
  assert.ok(to.getTime() > from.getTime())
  // Difference should be 23 hours, 59 minutes, 59 seconds and 999 ms
  const diffMs = to.getTime() - from.getTime()
  assert.equal(diffMs, 86399999)
})

test('live conflict check flags overlap with BOOKED schedule within buffer window', () => {
  const busyList = [
    {
      id: 101,
      type: 'BOOKED',
      bookingCode: 'BK-202611-001',
      startAt: '2026-11-10T10:00:00Z',
      endAt: '2026-11-10T14:00:00Z',
    }
  ]

  // Trying to block 14:30 to 16:00 when buffer is 60 mins (buffer ends at 15:00)
  const conflict = checkLiveConflict({
    formStartAt: '2026-11-10T14:30:00Z',
    formEndAt: '2026-11-10T16:00:00Z',
    vehicleBusySchedules: busyList,
    maintenanceBufferMinutes: 60,
  })

  assert.ok(conflict !== null)
  assert.equal(conflict.type, 'BOOKED')
  assert.equal(conflict.bookingCode, 'BK-202611-001')
})

test('live conflict check allows block schedule after BOOKED schedule buffer has passed', () => {
  const busyList = [
    {
      id: 101,
      type: 'BOOKED',
      bookingCode: 'BK-202611-001',
      startAt: '2026-11-10T10:00:00Z',
      endAt: '2026-11-10T14:00:00Z',
    }
  ]

  // Start at 15:00 (exact buffer boundary end)
  const conflict = checkLiveConflict({
    formStartAt: '2026-11-10T15:00:00Z',
    formEndAt: '2026-11-10T17:00:00Z',
    vehicleBusySchedules: busyList,
    maintenanceBufferMinutes: 60,
  })

  assert.equal(conflict, null)
})

test('live conflict check permits contiguous back-to-back internal maintenance blocks', () => {
  const busyList = [
    {
      id: 201,
      type: 'MAINTENANCE',
      startAt: '2026-11-10T08:00:00Z',
      endAt: '2026-11-10T10:00:00Z',
    }
  ]

  // Consecutive internal block: 10:00 to 12:00
  const contiguous = checkLiveConflict({
    formStartAt: '2026-11-10T10:00:00Z',
    formEndAt: '2026-11-10T12:00:00Z',
    vehicleBusySchedules: busyList,
    maintenanceBufferMinutes: 60,
  })
  assert.equal(contiguous, null)

  // Direct overlapping block: 09:30 to 11:30
  const overlapping = checkLiveConflict({
    formStartAt: '2026-11-10T09:30:00Z',
    formEndAt: '2026-11-10T11:30:00Z',
    vehicleBusySchedules: busyList,
    maintenanceBufferMinutes: 60,
  })
  assert.ok(overlapping !== null)
  assert.equal(overlapping.type, 'MAINTENANCE')
})

test('filterDisplayedSchedules classifies ACTIVE, HELD, EXPIRED and CANCELLED correctly', () => {
  const now = new Date('2026-11-10T12:00:00Z').getTime()
  const schedules = [
    { id: 1, status: 'ACTIVE', endAt: '2026-11-10T14:00:00Z' }, // future ACTIVE
    { id: 2, status: 'ACTIVE', endAt: '2026-11-10T10:00:00Z' }, // past ACTIVE -> EXPIRED
    { id: 3, status: 'HELD', endAt: '2026-11-10T15:00:00Z' },   // future HELD
    { id: 4, status: 'HELD', endAt: '2026-11-10T09:00:00Z' },   // past HELD -> EXPIRED
    { id: 5, status: 'CANCELLED', endAt: '2026-11-10T18:00:00Z' },
  ]

  const active = filterDisplayedSchedules(schedules, 'ACTIVE', now)
  assert.deepEqual(active.map((s) => s.id), [1])

  const held = filterDisplayedSchedules(schedules, 'HELD', now)
  assert.deepEqual(held.map((s) => s.id), [3])

  const expired = filterDisplayedSchedules(schedules, 'EXPIRED', now)
  assert.deepEqual(expired.map((s) => s.id), [2, 4])

  const cancelled = filterDisplayedSchedules(schedules, 'CANCELLED', now)
  assert.deepEqual(cancelled.map((s) => s.id), [5])
})

