import test from 'node:test'
import assert from 'node:assert/strict'
import { formatCountdown, tripEndWindow } from '../src/utils/tripEndWindow.js'

const reportedAt = '2026-11-09T12:25:00Z'
const deadline = '2026-11-10T00:25:00Z'

test('không có lần báo kết thúc thì không hiện banner', () => {
  assert.equal(tripEndWindow(null).phase, 'NONE')
  assert.equal(tripEndWindow({ endConfirmations: [] }).phase, 'NONE')
})

test('WAITING trong hạn: đếm ngược tới deadline 12 giờ', () => {
  const view = tripEndWindow({ endConfirmations: [{ id: 1, state: 'WAITING', reportedAt, deadline }] },
    Date.parse('2026-11-09T12:25:30Z'))
  assert.equal(view.phase, 'WAITING')
  assert.equal(formatCountdown(view.secondsLeft), '11:59:30')
})

test('WAITING quá hạn chờ job tự chốt; PAUSED là đang khiếu nại; lấy bản ghi mới nhất', () => {
  assert.equal(tripEndWindow({ endConfirmations: [{ id: 1, state: 'WAITING', reportedAt, deadline }] },
    Date.parse('2026-11-10T01:00:00Z')).phase, 'EXPIRING')
  assert.equal(tripEndWindow({ endConfirmations: [{ id: 2, state: 'PAUSED', reportedAt, deadline }] }).phase, 'DISPUTED')
  const latest = tripEndWindow({ endConfirmations: [
    { id: 3, state: 'CONFIRMED', confirmationMethod: 'RENTER', reportedAt, deadline },
    { id: 2, state: 'CANCELLED', reportedAt, deadline },
  ] })
  assert.equal(latest.phase, 'CONFIRMED')
  assert.equal(latest.confirmationMethod, 'RENTER')
})
