import test from 'node:test'
import assert from 'node:assert/strict'
import { buildDisputeResolution, disputeVerdictLabel } from '../src/utils/disputeVerdict.js'
import { waivedChargeLabel } from '../src/utils/paymentDisplay.js'

test('trip-end verdicts map to a decisive status and action', () => {
  const accept = buildDisputeResolution('TRIP_NOT_CONFIRMED', { verdict: 'ACCEPT_RENTER_TIME', correctedEndAt: '2026-11-10T11:00', resolution: 'GPS' })
  assert.equal(accept.status, 'RESOLVED')
  assert.equal(accept.tripEndAction, 'ACCEPT_RENTER_TIME')
  assert.ok(accept.correctedEndAt.endsWith('Z'))
  assert.ok(buildDisputeResolution('TRIP_NOT_CONFIRMED', { verdict: 'ACCEPT_RENTER_TIME', resolution: 'x' }).error)
  assert.deepEqual(buildDisputeResolution('TRIP_NOT_CONFIRMED', { verdict: 'KEEP_DRIVER_TIME', resolution: 'x' }),
    { status: 'REJECTED', resolution: 'x', tripEndAction: 'KEEP_DRIVER_TIME' })
  assert.deepEqual(buildDisputeResolution('TRIP_NOT_CONFIRMED', { verdict: 'OTHER', resolution: 'chờ log' }),
    { status: 'IN_REVIEW', resolution: 'chờ log' })
})

test('charge verdicts include partial reduction and "other"', () => {
  assert.equal(buildDisputeResolution('CHARGE', { verdict: 'PARTIAL_WAIVE', refundAwardTotal: '20000', resolution: 'x' }).refundAwardTotal, 20000)
  assert.ok(buildDisputeResolution('CHARGE', { verdict: 'PARTIAL_WAIVE', refundAwardTotal: '', resolution: 'x' }).error)
  assert.equal(buildDisputeResolution('CHARGE', { verdict: 'OTHER', resolution: 'x' }).status, 'IN_REVIEW')
  assert.ok(buildDisputeResolution('CHARGE', { verdict: 'KEEP_CHARGE', resolution: '  ' }).error)
})

test('final verdict label states the outcome', () => {
  assert.equal(disputeVerdictLabel({ status: 'RESOLVED', category: 'TRIP_NOT_CONFIRMED', tripEndAction: 'ACCEPT_RENTER_TIME' }), 'Đã chốt: theo giờ khách')
  assert.equal(disputeVerdictLabel({ status: 'REJECTED', category: 'TRIP_NOT_CONFIRMED', tripEndAction: 'KEEP_DRIVER_TIME' }), 'Đã chốt: giữ giờ tài xế báo')
  assert.equal(disputeVerdictLabel({ status: 'IN_REVIEW', category: 'CHARGE' }), null)
  assert.match(disputeVerdictLabel({ status: 'RESOLVED', category: 'CHARGE', decision: 'PARTIAL_WAIVE', refundAwardTotal: 20000 }), /giảm/)
})

test('waived charge label tells who waived it', () => {
  assert.equal(waivedChargeLabel('PROVIDER_WITHDRAWN'), 'Nhà xe đã rút')
  assert.equal(waivedChargeLabel('ADMIN_DISPUTE'), 'Admin miễn (khiếu nại)')
})
