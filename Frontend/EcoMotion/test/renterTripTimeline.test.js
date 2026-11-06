import test from 'node:test'
import assert from 'node:assert/strict'
import { evaluateActiveWorkflowSteps } from '../src/utils/bookingWorkflowSteps.js'

const paidWorkflow = (bookingStatus, tripStatus) => ({
  bookingStatus,
  paymentStatus: 'DEPOSIT_PAID',
  driverStatus: 'ACCEPTED',
  tripStatus,
  confirmedPaid: 1000,
})

const tripStep = (steps) => steps.find((s) => s.id === 'TRIP')

test('in-progress trip shows driver actual start time from backend, not a renter confirmation', () => {
  const booking = { status: 'IN_PROGRESS', actualStartAt: '2026-11-07T02:00:00Z', driverName: 'An' }
  const step = tripStep(evaluateActiveWorkflowSteps(paidWorkflow('IN_PROGRESS', 'IN_PROGRESS'), booking))
  assert.equal(step.state, 'current')
  assert.equal(step.description, 'Đang di chuyển')
  assert.equal(step.at, '2026-11-07T02:00:00Z')
})

test('ended trip is completed and shows actual end time', () => {
  const booking = { status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', actualStartAt: '2026-11-07T02:00:00Z', actualEndAt: '2026-11-07T04:00:00Z', driverName: 'An' }
  const step = tripStep(evaluateActiveWorkflowSteps(paidWorkflow('AWAITING_EXTRA_CHARGE_CONFIRMATION', 'ENDED'), booking))
  assert.equal(step.state, 'completed')
  assert.equal(step.at, '2026-11-07T04:00:00Z')
})

test('legacy awaiting-confirmation statuses never ask the renter to confirm', () => {
  for (const status of ['AWAITING_START_CONFIRMATION', 'AWAITING_END_CONFIRMATION']) {
    const steps = evaluateActiveWorkflowSteps(paidWorkflow(status, 'NOT_STARTED'), { status, driverName: 'An' })
    const text = steps.map((s) => s.description).join(' | ')
    assert.doesNotMatch(text, /xác nhận bắt đầu|xác nhận kết thúc/i)
  }
})

test('upcoming trip has no timestamp before the driver starts', () => {
  const step = tripStep(evaluateActiveWorkflowSteps(paidWorkflow('CONFIRMED', 'NOT_STARTED'), { status: 'CONFIRMED', driverName: 'An' }))
  assert.equal(step.at, null)
})
