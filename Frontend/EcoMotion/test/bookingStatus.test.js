import test from 'node:test'
import assert from 'node:assert/strict'
import {
  BOOKING_FILTERS,
  BOOKING_STATUS_META,
  bookingMatchesFilter,
  bookingStatusMeta,
  canShowDepositCTA,
  canShowFinalPaymentCTA,
  canShowOperationalRisk,
  cardAction,
  listPaymentLabel,
} from '../src/utils/bookingStatus.js'
import {
  formatCountdown,
  isPaymentRequestExpired,
  remainingMs,
  countdownUrgency,
} from '../src/utils/paymentDisplay.js'

const statuses = ['PENDING', 'REJECTED', 'ACCEPTED', 'CONFIRMED', 'DRIVER_ASSIGNED',
  'AWAITING_DEPOSIT', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION',
  'IN_PROGRESS', 'AWAITING_END_CONFIRMATION', 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
  'AWAITING_FINAL_PAYMENT', 'COMPLETED', 'CANCELLATION_REVIEW', 'CANCELLED']

test('all backend booking states have a trip label and style', () => {
  assert.deepEqual(Object.keys(BOOKING_STATUS_META).sort(), [...statuses].sort())
  for (const status of statuses) {
    const meta = bookingStatusMeta(status)
    assert.ok(meta.label)
    assert.ok(meta.tripLabel)
    assert.ok(meta.badgeClass)
    assert.equal(meta.action, undefined)
  }
})

test('list tabs are action-oriented and do not call deposit confirmed', () => {
  assert.deepEqual(BOOKING_FILTERS.map((filter) => filter.id), [
    'ALL', 'ACTION_NEEDED', 'UPCOMING', 'ACTIVE', 'ENDED',
  ])
  assert.equal(bookingStatusMeta('PENDING').label, 'Chờ chủ xe xác nhận')
  assert.equal(bookingStatusMeta('AWAITING_DEPOSIT').label, 'Chờ thanh toán cọc')
  assert.equal(bookingStatusMeta('ACCEPTED').label, 'Chờ thanh toán cọc')
  assert.notEqual(bookingStatusMeta('AWAITING_DEPOSIT').label, 'Đã xác nhận')
  assert.equal(bookingStatusMeta('CONFIRMED').label, 'Đã xác nhận')
  assert.equal(bookingStatusMeta('CONFIRMED').tripLabel, 'Sắp khởi hành')
  assert.equal(bookingStatusMeta('IN_PROGRESS').label, 'Đang thực hiện')
})

test('status groups match upcoming, active and ended tabs only', () => {
  assert.equal(bookingMatchesFilter('PENDING', 'ACTION_NEEDED'), false)
  assert.equal(bookingMatchesFilter('AWAITING_DEPOSIT', 'UPCOMING'), false)
  assert.equal(bookingMatchesFilter('CONFIRMED', 'UPCOMING'), true)
  assert.equal(bookingMatchesFilter('DRIVER_ARRIVED', 'ACTIVE'), true)
  assert.equal(bookingMatchesFilter('IN_PROGRESS', 'ACTIVE'), true)
  assert.equal(bookingMatchesFilter('COMPLETED', 'ENDED'), true)
  assert.equal(bookingMatchesFilter('CANCELLED', 'ENDED'), true)
})

test('payment labels cover deposit hold and settlement only', () => {
  assert.equal(listPaymentLabel('AWAITING_DEPOSIT'), 'Chờ thanh toán cọc')
  assert.equal(listPaymentLabel('AWAITING_DEPOSIT', { holdExpired: true }), 'Hết hạn giữ chỗ')
  assert.equal(listPaymentLabel('CONFIRMED'), null)
  assert.equal(listPaymentLabel('AWAITING_FINAL_PAYMENT'), 'Chờ thanh toán quyết toán')
})

test('card CTAs follow actionKind and never infer from status', () => {
  assert.equal(canShowDepositCTA('PAY_DEPOSIT'), true)
  assert.equal(canShowDepositCTA('NONE'), false)
  assert.equal(canShowFinalPaymentCTA('PAY_SETTLEMENT'), true)
  assert.equal(canShowFinalPaymentCTA('PAY_DEPOSIT'), false)
  assert.equal(cardAction('NONE'), null)
  assert.equal(cardAction('PAY_DEPOSIT').label, 'Thanh toán cọc')
  assert.ok(bookingStatusMeta('UNKNOWN').label)
})

test('risk copy is hidden for pending and empty messages', () => {
  assert.equal(canShowOperationalRisk('PENDING', true, 'Xe trễ'), false)
  assert.equal(canShowOperationalRisk('CONFIRMED', true, ''), false)
  assert.equal(canShowOperationalRisk('CONFIRMED', true, 'Xe có thể về trễ'), true)
  assert.equal(canShowOperationalRisk('CONFIRMED', false, 'Xe có thể về trễ'), false)
})

test('payment expiry changes exactly at the server deadline', () => {
  const request = { expiresAt: '2026-10-21T08:00:00.000Z' }
  assert.equal(isPaymentRequestExpired(request, Date.parse('2026-10-21T07:59:59.999Z')), false)
  assert.equal(isPaymentRequestExpired(request, Date.parse('2026-10-21T08:00:00.000Z')), true)
  assert.equal(isPaymentRequestExpired({ expiresAt: null }, Date.now()), false)
})

test('hold countdown formats remaining time and urgency', () => {
  const deadline = '2026-10-21T08:15:00.000Z'
  assert.equal(remainingMs(deadline, Date.parse('2026-10-21T08:00:00.000Z')), 15 * 60 * 1000)
  assert.equal(formatCountdown(15 * 60 * 1000), '15:00')
  assert.equal(formatCountdown(2 * 60 * 1000), '02:00')
  assert.equal(formatCountdown(-1), '00:00')
  assert.equal(countdownUrgency(3 * 60 * 1000), 'ok')
  assert.equal(countdownUrgency(90 * 1000), 'urgent')
  assert.equal(countdownUrgency(0), 'expired')
})
