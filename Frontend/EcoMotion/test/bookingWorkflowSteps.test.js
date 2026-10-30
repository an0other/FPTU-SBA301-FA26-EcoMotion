import test from 'node:test'
import assert from 'node:assert/strict'
import {
  STEP_STATUS,
  isTerminalBooking,
  checkPickupTimeAnomaly,
  isOverduePickup,
  getActiveFinancialMetrics,
  evaluateActiveWorkflowSteps,
  evaluateRefundWorkflow,
} from '../src/utils/bookingWorkflowSteps.js'

test('1. Chờ xác nhận: approval is current, subsequent steps are pending', () => {
  const steps = evaluateActiveWorkflowSteps(
    { bookingStatus: 'PENDING', paymentStatus: 'UNPAID', confirmedPaid: 0 },
    { status: 'PENDING' }
  )
  assert.equal(steps[0].id, 'APPROVAL')
  assert.equal(steps[0].state, STEP_STATUS.CURRENT)
  assert.equal(steps[1].id, 'DEPOSIT')
  assert.equal(steps[1].state, STEP_STATUS.PENDING)
  assert.equal(steps[2].id, 'DISPATCH')
  assert.equal(steps[2].state, STEP_STATUS.PENDING)
})

test('2. Chờ thanh toán cọc: approval completed, deposit current, driver pending on deposit', () => {
  const steps = evaluateActiveWorkflowSteps(
    { bookingStatus: 'AWAITING_DEPOSIT', paymentStatus: 'AWAITING_DEPOSIT', confirmedPaid: 0 },
    { status: 'AWAITING_DEPOSIT' }
  )
  assert.equal(steps[0].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[1].state, STEP_STATUS.CURRENT)
  assert.equal(steps[2].state, STEP_STATUS.PENDING) // Driver depends on deposit!
  assert.match(steps[2].description, /Chờ đặt cọc/)
})

test('3. Đã xác nhận cọc & Chờ phân công tài xế: deposit completed, driver current', () => {
  const steps = evaluateActiveWorkflowSteps(
    { bookingStatus: 'CONFIRMED', paymentStatus: 'PARTIALLY_PAID', confirmedPaid: 500000, needsDriver: true, driverStatus: 'NOT_ASSIGNED' },
    { status: 'CONFIRMED' }
  )
  assert.equal(steps[0].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[1].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[2].state, STEP_STATUS.CURRENT)
  assert.match(steps[2].description, /Chưa có tài xế/)
  assert.equal(steps[3].state, STEP_STATUS.PENDING)
})

test('4. Đã phân công tài xế (chờ phản hồi & tài xế đã xác nhận)', () => {
  // Case A: Đã gán tài xế, chờ tài xế confirm
  const stepsWaiting = evaluateActiveWorkflowSteps(
    { bookingStatus: 'DRIVER_ASSIGNED', driverStatus: 'PENDING_RESPONSE', confirmedPaid: 500000 },
    { status: 'DRIVER_ASSIGNED', driverName: 'Nguyễn Văn A' }
  )
  assert.equal(stepsWaiting[2].state, STEP_STATUS.CURRENT)
  assert.match(stepsWaiting[2].description, /Chờ tài xế phản hồi/)

  // Case B: Tài xế đã xác nhận
  const stepsConfirmed = evaluateActiveWorkflowSteps(
    { bookingStatus: 'DRIVER_CONFIRMED', driverStatus: 'ACCEPTED', confirmedPaid: 500000 },
    { status: 'DRIVER_CONFIRMED', driverName: 'Nguyễn Văn A', driverConfirmedAt: '2026-10-30T10:00:00Z' }
  )
  assert.equal(stepsConfirmed[2].state, STEP_STATUS.COMPLETED)
  assert.match(stepsConfirmed[2].description, /Nguyễn Văn A/)
})

test('5. Chuyến đi đang thực hiện', () => {
  const steps = evaluateActiveWorkflowSteps(
    { bookingStatus: 'IN_PROGRESS', tripStatus: 'IN_PROGRESS', confirmedPaid: 500000 },
    { status: 'IN_PROGRESS', driverName: 'Nguyễn Văn A', actualStartAt: '2026-10-30T10:00:00Z' }
  )
  assert.equal(steps[0].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[1].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[2].state, STEP_STATUS.COMPLETED)
  assert.equal(steps[3].state, STEP_STATUS.CURRENT)
  assert.match(steps[3].description, /Đang di chuyển/)
  assert.equal(steps[4].state, STEP_STATUS.PENDING)
})

test('6. Hoàn thành chuyến đi, chờ quyết toán: trip completed, settlement current', () => {
  const steps = evaluateActiveWorkflowSteps(
    {
      bookingStatus: 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
      tripStatus: 'ENDED',
      settlementStatus: 'REVIEWING_CHARGES',
      confirmedPaid: 500000,
      remainingAmount: 150000,
    },
    { status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', actualEndAt: '2026-10-30T12:00:00Z' }
  )
  assert.equal(steps[3].state, STEP_STATUS.COMPLETED)
  assert.match(steps[3].description, /Đã hoàn thành chuyến/)
  assert.equal(steps[4].state, STEP_STATUS.CURRENT)
  assert.match(steps[4].description, /Chờ chốt phụ phí/)
})

test('7. Đã hoàn tất: All 5 steps completed', () => {
  const steps = evaluateActiveWorkflowSteps(
    { bookingStatus: 'COMPLETED', tripStatus: 'ENDED', settlementStatus: 'SETTLED', confirmedPaid: 650000, remainingAmount: 0 },
    { status: 'COMPLETED', actualEndAt: '2026-10-30T12:00:00Z' }
  )
  for (const step of steps) {
    assert.equal(step.state, STEP_STATUS.COMPLETED)
  }
})

test('8. Đã hủy trước thanh toán: Không phát sinh nghĩa vụ tài chính', () => {
  assert.equal(isTerminalBooking('CANCELLED'), true)
  assert.equal(isTerminalBooking('REJECTED'), true)
  assert.equal(isTerminalBooking('CANCELLATION_REVIEW'), true)
  assert.equal(isTerminalBooking('CONFIRMED'), false)

  const result = evaluateRefundWorkflow(
    { bookingStatus: 'CANCELLED', paymentStatus: 'UNPAID', confirmedPaid: 0, refundedAmount: 0, pendingRefundAmount: 0 },
    { status: 'CANCELLED', cancelReason: 'Khách đổi kế hoạch' }
  )
  assert.equal(result.hasFinancialObligation, false)
})

test('9. Đã hủy sau thanh toán, chờ hoàn tiền: refund stepper with active processing', () => {
  const result = evaluateRefundWorkflow(
    {
      bookingStatus: 'CANCELLED',
      paymentStatus: 'REFUND_PENDING',
      confirmedPaid: 500000,
      refundedAmount: 0,
      pendingRefundAmount: 500000,
    },
    { status: 'CANCELLED' }
  )
  assert.equal(result.hasFinancialObligation, true)
  assert.equal(result.isFullyRefunded, false)
  assert.equal(result.steps.length, 3)
  assert.equal(result.steps[0].state, STEP_STATUS.COMPLETED)
  assert.equal(result.steps[1].state, STEP_STATUS.CURRENT)
  assert.match(result.steps[1].description, /Đang xử lý chuyển khoản/)
  assert.equal(result.steps[2].state, STEP_STATUS.PENDING)
})

test('10. Hoàn tiền thất bại: Shows failed error state on processing step', () => {
  const result = evaluateRefundWorkflow(
    {
      bookingStatus: 'CANCELLED',
      paymentStatus: 'FAILED',
      confirmedPaid: 500000,
      refundedAmount: 0,
      pendingRefundAmount: 500000,
    },
    { status: 'CANCELLED' }
  )
  assert.equal(result.hasFailedRefund, true)
  assert.equal(result.steps[1].state, STEP_STATUS.FAILED)
  assert.match(result.steps[1].description, /thất bại/)
})

test('11. Anomaly detection: Pickup time before creation time', () => {
  const anomaly = checkPickupTimeAnomaly('2026-10-30T08:00:00Z', '2026-10-30T09:00:00Z')
  assert.ok(anomaly)
  assert.equal(anomaly.type, 'CREATED_AFTER_PICKUP')

  const valid = checkPickupTimeAnomaly('2026-10-30T10:00:00Z', '2026-10-30T09:00:00Z')
  assert.equal(valid, null)
})

test('12. Overdue pickup detection during preparation', () => {
  const now = Date.parse('2026-10-30T10:00:00Z')
  assert.equal(isOverduePickup('2026-10-30T09:00:00Z', 'CONFIRMED', now), true)
  assert.equal(isOverduePickup('2026-10-30T11:00:00Z', 'CONFIRMED', now), false)
  // When already in progress, not overdue in preparation
  assert.equal(isOverduePickup('2026-10-30T09:00:00Z', 'IN_PROGRESS', now), false)
})

test('13. Non-zero financial metrics avoids redundant 0đ', () => {
  // Empty when all 0
  assert.deepEqual(
    getActiveFinancialMetrics({ confirmedPaid: 0, refundedAmount: 0, pendingRefundAmount: 0, remainingAmount: 0 }),
    []
  )

  // Only populated metrics returned
  const metrics = getActiveFinancialMetrics({
    confirmedPaid: 500000,
    refundedAmount: 0,
    pendingRefundAmount: 200000,
    remainingAmount: 0,
    bookingStatus: 'CANCELLED',
  })
  assert.equal(metrics.length, 2)
  assert.equal(metrics[0].id, 'confirmedPaid')
  assert.equal(metrics[1].id, 'pendingRefundAmount')
})
