import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_TREND_WINDOW_SIZE,
  getTrendMonthWindow,
  stepTrendOffset,
} from '../src/utils/adminTrendWindow.js'

const sampleMonths = Array.from({ length: 11 }, (_, idx) => ({
  key: `2026-${String(idx + 1).padStart(2, '0')}`,
  label: `Tháng ${String(idx + 1).padStart(2, '0')}/2026`,
}))

test('defaults to the 5 most recent months when series has more than 5 items', () => {
  const win = getTrendMonthWindow(sampleMonths, 0, DEFAULT_TREND_WINDOW_SIZE)
  assert.deepEqual(
    win.visibleMonths.map((m) => m.key),
    ['2026-07', '2026-08', '2026-09', '2026-10', '2026-11']
  )
  assert.equal(win.canShiftOlder, true)
  assert.equal(win.canShiftNewer, false)
})

test('shifts smoothly toward older months (<) and back toward newer months (>)', () => {
  let offset = 0
  offset = stepTrendOffset(offset, 1, sampleMonths.length)
  assert.equal(offset, 1)
  let win = getTrendMonthWindow(sampleMonths, offset)
  assert.deepEqual(
    win.visibleMonths.map((m) => m.key),
    ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10']
  )
  assert.equal(win.canShiftOlder, true)
  assert.equal(win.canShiftNewer, true)

  offset = stepTrendOffset(offset, 10, sampleMonths.length)
  win = getTrendMonthWindow(sampleMonths, offset)
  assert.deepEqual(
    win.visibleMonths.map((m) => m.key),
    ['2026-01', '2026-02', '2026-03', '2026-04', '2026-05']
  )
  assert.equal(win.canShiftOlder, false)
  assert.equal(win.canShiftNewer, true)

  offset = stepTrendOffset(offset, -1, sampleMonths.length)
  win = getTrendMonthWindow(sampleMonths, offset)
  assert.deepEqual(
    win.visibleMonths.map((m) => m.key),
    ['2026-02', '2026-03', '2026-04', '2026-05', '2026-06']
  )
})

test('clamps stale offset immediately when month list shrinks', () => {
  const shrunk = sampleMonths.slice(0, 7)
  const nextOffset = stepTrendOffset(6, -1, shrunk.length)
  assert.equal(nextOffset, 1)
})

test('shows all items without navigation when series has at most 5 months or is empty', () => {
  const shortWin = getTrendMonthWindow(sampleMonths.slice(0, 3), 2)
  assert.deepEqual(
    shortWin.visibleMonths.map((m) => m.key),
    ['2026-01', '2026-02', '2026-03']
  )
  assert.equal(shortWin.canShiftOlder, false)
  assert.equal(shortWin.canShiftNewer, false)

  const emptyWin = getTrendMonthWindow([], 0)
  assert.deepEqual(emptyWin.visibleMonths, [])
  assert.equal(emptyWin.canShiftOlder, false)
  assert.equal(emptyWin.canShiftNewer, false)
})

