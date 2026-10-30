/**
 * Booking Workflow Steps & Progress Evaluation Engine
 * Handles business logic, status mapping, and step states for Booking detail pages (Provider & Renter).
 */

export const STEP_STATUS = {
  COMPLETED: 'completed',
  CURRENT: 'current',
  PENDING: 'pending',
  WARNING: 'warning',
  FAILED: 'failed',
}

const TERMINAL_STATUSES = new Set(['CANCELLED', 'REJECTED', 'CANCELLATION_REVIEW'])

/**
 * Checks whether a booking is in a cancelled / terminal state
 */
export function isTerminalBooking(bookingStatus) {
  return TERMINAL_STATUSES.has(bookingStatus)
}

/**
 * Checks for anomaly between pickup time and creation time
 */
export function checkPickupTimeAnomaly(pickupTime, createdAt) {
  if (!pickupTime) return null
  const pickupDate = new Date(pickupTime)
  if (Number.isNaN(pickupDate.getTime())) return null

  const createdDate = createdAt ? new Date(createdAt) : null
  if (createdDate && !Number.isNaN(createdDate.getTime()) && pickupDate.getTime() < createdDate.getTime()) {
    return {
      type: 'CREATED_AFTER_PICKUP',
      message: 'Thời gian nhận xe đặt trước thời điểm tạo đơn.',
      pickupTime,
      createdAt,
    }
  }

  return null
}

/**
 * Checks if pickup time has already passed while booking is still in preparation
 */
export function isOverduePickup(pickupTime, bookingStatus, now = Date.now()) {
  if (!pickupTime) return false
  const pickupDate = new Date(pickupTime)
  if (Number.isNaN(pickupDate.getTime())) return false

  const prepStatuses = new Set([
    'PENDING',
    'AWAITING_DEPOSIT',
    'ACCEPTED',
    'CONFIRMED',
    'DRIVER_ASSIGNED',
    'DRIVER_CONFIRMED',
  ])

  return prepStatuses.has(bookingStatus) && pickupDate.getTime() < now
}

/**
 * Extracts only non-zero financial metrics to avoid redundant 0đ displays
 */
export function getActiveFinancialMetrics(workflow) {
  if (!workflow) return []
  const metrics = []
  const paid = Number(workflow.confirmedPaid || 0)
  const refunded = Number(workflow.refundedAmount || 0)
  const pendingRefund = Number(workflow.pendingRefundAmount || 0)
  const remaining = Number(workflow.remainingAmount || 0)
  const isClosed = isTerminalBooking(workflow.bookingStatus)

  if (paid > 0) {
    metrics.push({
      id: 'confirmedPaid',
      label: 'Đã nhận xác nhận',
      amount: paid,
      badgeClass: 'text-emerald-700 bg-emerald-50 border-emerald-200',
    })
  }
  if (refunded > 0) {
    metrics.push({
      id: 'refundedAmount',
      label: 'Đã hoàn tiền',
      amount: refunded,
      badgeClass: 'text-slate-700 bg-slate-100 border-slate-200',
    })
  }
  if (pendingRefund > 0) {
    metrics.push({
      id: 'pendingRefundAmount',
      label: 'Đang xử lý hoàn',
      amount: pendingRefund,
      badgeClass: 'text-amber-800 bg-amber-50 border-amber-200 font-bold',
    })
  }
  if (remaining > 0 && !isClosed) {
    metrics.push({
      id: 'remainingAmount',
      label: 'Cần quyết toán thêm',
      amount: remaining,
      badgeClass: 'text-purple-800 bg-purple-50 border-purple-200 font-bold',
    })
  }

  return metrics
}

/**
 * Evaluates operational workflow steps for an ACTIVE booking (not cancelled)
 */
export function evaluateActiveWorkflowSteps(workflow = {}, booking = {}) {
  const status = workflow.bookingStatus || booking.status || 'PENDING'
  const paymentStatus = workflow.paymentStatus || booking.paymentStatus || 'UNPAID'
  const driverStatus = workflow.driverStatus || 'NOT_ASSIGNED'
  const tripStatus = workflow.tripStatus || 'NOT_STARTED'
  const settlementStatus = workflow.settlementStatus || 'NOT_READY'
  const confirmedPaid = Number(workflow.confirmedPaid || 0)

  // 1. APPROVAL STEP
  let approvalState = STEP_STATUS.COMPLETED
  let approvalDescription = 'Đã duyệt đơn'

  if (status === 'PENDING') {
    approvalState = STEP_STATUS.CURRENT
    approvalDescription = 'Chờ nhà xe xác nhận'
  }

  // 2. DEPOSIT STEP
  let depositState = STEP_STATUS.PENDING
  let depositDescription = 'Chờ duyệt đơn'

  const hasPaidDeposit = confirmedPaid > 0 || [
    'CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED',
    'AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION',
    'AWAITING_EXTRA_CHARGE_CONFIRMATION', 'AWAITING_FINAL_PAYMENT', 'COMPLETED',
  ].includes(status)

  if (hasPaidDeposit) {
    depositState = STEP_STATUS.COMPLETED
    depositDescription = 'Đã thanh toán cọc'
  } else if (status === 'AWAITING_DEPOSIT' || status === 'ACCEPTED' || paymentStatus === 'AWAITING_DEPOSIT') {
    depositState = STEP_STATUS.CURRENT
    depositDescription = 'Chờ khách cọc'
  } else if (paymentStatus === 'VERIFYING') {
    depositState = STEP_STATUS.CURRENT
    depositDescription = 'Đang xác minh giao dịch'
  }

  // 3. DRIVER DISPATCH STEP (Depends on Deposit confirmation)
  let driverState = STEP_STATUS.PENDING
  let driverDescription = 'Chờ đặt cọc'

  if (depositState === STEP_STATUS.COMPLETED) {
    if (driverStatus === 'ACCEPTED' || Boolean(booking.driverConfirmedAt)) {
      driverState = STEP_STATUS.COMPLETED
      driverDescription = booking.driverName ? `Tài xế: ${booking.driverName}` : 'Tài xế đã nhận chuyến'
    } else if (driverStatus === 'PENDING_RESPONSE' || status === 'DRIVER_ASSIGNED') {
      driverState = STEP_STATUS.CURRENT
      driverDescription = 'Chờ tài xế phản hồi'
    } else if (driverStatus === 'RESPONSE_EXPIRED') {
      driverState = STEP_STATUS.WARNING
      driverDescription = 'Tài xế quá hạn phản hồi'
    } else if (driverStatus === 'REPLACEMENT_REQUIRED' || booking.driverReplacementRequired) {
      driverState = STEP_STATUS.WARNING
      driverDescription = 'Cần đổi tài xế'
    } else if (workflow.needsDriver || driverStatus === 'NOT_ASSIGNED' || !booking.driverName) {
      driverState = STEP_STATUS.CURRENT
      driverDescription = 'Chưa có tài xế'
    } else {
      driverState = STEP_STATUS.COMPLETED
      driverDescription = 'Đã phân công'
    }
  }

  // If trip already underway or finished, driver step is marked completed
  if (['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION', 'AWAITING_EXTRA_CHARGE_CONFIRMATION', 'AWAITING_FINAL_PAYMENT', 'COMPLETED'].includes(status)
      || tripStatus === 'IN_PROGRESS' || tripStatus === 'ENDED') {
    driverState = STEP_STATUS.COMPLETED
    if (booking.driverName) {
      driverDescription = `Tài xế: ${booking.driverName}`
    }
  }

  // 4. TRIP EXECUTION STEP
  let tripState = STEP_STATUS.PENDING
  let tripDescription = 'Chờ tài xế đón'

  const isTripEnded = tripStatus === 'ENDED'
    || Boolean(booking.actualEndAt)
    || ['AWAITING_EXTRA_CHARGE_CONFIRMATION', 'AWAITING_FINAL_PAYMENT', 'COMPLETED'].includes(status)

  if (isTripEnded) {
    tripState = STEP_STATUS.COMPLETED
    tripDescription = 'Đã hoàn thành chuyến'
  } else if (status === 'IN_PROGRESS' || tripStatus === 'IN_PROGRESS' || Boolean(booking.actualStartAt)) {
    tripState = STEP_STATUS.CURRENT
    tripDescription = 'Đang di chuyển'
  } else if (status === 'AWAITING_START_CONFIRMATION') {
    tripState = STEP_STATUS.CURRENT
    tripDescription = 'Chờ xác nhận bắt đầu'
  } else if (status === 'DRIVER_ARRIVED') {
    tripState = STEP_STATUS.CURRENT
    tripDescription = 'Tài xế đã đến điểm đón'
  } else if (status === 'AWAITING_END_CONFIRMATION') {
    tripState = STEP_STATUS.CURRENT
    tripDescription = 'Chờ xác nhận kết thúc'
  } else if (driverState === STEP_STATUS.COMPLETED) {
    tripState = STEP_STATUS.PENDING
    tripDescription = 'Sẵn sàng khởi hành'
  }

  // 5. SETTLEMENT STEP
  let settlementState = STEP_STATUS.PENDING
  let settlementDescription = 'Chờ hoàn thành chuyến'

  if (status === 'COMPLETED' || settlementStatus === 'SETTLED') {
    settlementState = STEP_STATUS.COMPLETED
    settlementDescription = 'Đã tất toán'
  } else if (tripState === STEP_STATUS.COMPLETED) {
    settlementState = STEP_STATUS.CURRENT
    if (settlementStatus === 'DISPUTED') {
      settlementState = STEP_STATUS.WARNING
      settlementDescription = 'Đang xử lý tranh chấp phụ phí'
    } else if (status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' || settlementStatus === 'REVIEWING_CHARGES') {
      settlementDescription = 'Chờ chốt phụ phí'
    } else if (status === 'AWAITING_FINAL_PAYMENT' || settlementStatus === 'AWAITING_FINAL_PAYMENT') {
      settlementDescription = 'Chờ thanh toán quyết toán'
    } else {
      settlementDescription = 'Chờ quyết toán'
    }
  }

  return [
    {
      id: 'APPROVAL',
      label: 'Duyệt đơn',
      state: approvalState,
      description: approvalDescription,
      icon: 'fact_check',
    },
    {
      id: 'DEPOSIT',
      label: 'Đặt cọc',
      state: depositState,
      description: depositDescription,
      icon: 'payments',
    },
    {
      id: 'DISPATCH',
      label: 'Tài xế',
      state: driverState,
      description: driverDescription,
      icon: 'person_pin_circle',
    },
    {
      id: 'TRIP',
      label: 'Chuyến đi',
      state: tripState,
      description: tripDescription,
      icon: 'directions_car',
    },
    {
      id: 'SETTLEMENT',
      label: 'Quyết toán',
      state: settlementState,
      description: settlementDescription,
      icon: 'receipt_long',
    },
  ]
}

/**
 * Evaluates refund workflow steps for a CANCELLED or REVIEW booking
 */
export function evaluateRefundWorkflow(workflow = {}, booking = {}) {
  const confirmedPaid = Number(workflow.confirmedPaid || 0)
  const refundedAmount = Number(workflow.refundedAmount || 0)
  const pendingRefundAmount = Number(workflow.pendingRefundAmount || 0)
  const paymentStatus = workflow.paymentStatus || booking.paymentStatus || 'UNPAID'
  const refunds = workflow.refunds || []

  const hasFinancialObligation = confirmedPaid > 0
    || pendingRefundAmount > 0
    || refundedAmount > 0
    || ['REFUND_PENDING', 'RECONCILIATION_REQUIRED', 'REFUNDED'].includes(paymentStatus)
    || refunds.length > 0

  if (!hasFinancialObligation) {
    return {
      hasFinancialObligation: false,
      reason: booking.rejectionReason || booking.cancelReason || 'Đơn đã hủy/từ chối trước khi phát sinh giao dịch.',
    }
  }

  const hasFailedRefund = paymentStatus === 'FAILED'
    || refunds.some((r) => r.status === 'FAILED')

  const isFullyRefunded = paymentStatus === 'REFUNDED'
    || (refundedAmount > 0 && pendingRefundAmount === 0 && refundedAmount >= confirmedPaid)

  // Refund stepper: 3 steps
  // 1. Ghi nhận hủy / yêu cầu hoàn
  // 2. Xử lý hoàn tiền & Đối soát
  // 3. Hoàn tất hoàn tiền
  let step1State = STEP_STATUS.COMPLETED
  let step2State = STEP_STATUS.PENDING
  let step3State = STEP_STATUS.PENDING

  let step2Desc = 'Chờ xử lý'
  let step3Desc = 'Chưa hoàn tất'

  if (hasFailedRefund) {
    step2State = STEP_STATUS.FAILED
    step2Desc = 'Hoàn tiền thất bại, cần xử lý lại'
  } else if (isFullyRefunded) {
    step2State = STEP_STATUS.COMPLETED
    step2Desc = 'Đã hoàn tất chuyển khoản'
    step3State = STEP_STATUS.COMPLETED
    step3Desc = 'Đã ghi nhận hoàn tiền'
  } else if (pendingRefundAmount > 0 || paymentStatus === 'REFUND_PENDING' || paymentStatus === 'RECONCILIATION_REQUIRED' || refunds.length > 0) {
    step2State = STEP_STATUS.CURRENT
    step2Desc = paymentStatus === 'RECONCILIATION_REQUIRED'
      ? 'Đang đối soát giao dịch'
      : 'Đang xử lý chuyển khoản'
  }

  const steps = [
    {
      id: 'REFUND_REQUESTED',
      label: 'Yêu cầu hoàn tiền',
      state: step1State,
      description: 'Đã ghi nhận hủy & hoàn tiền',
      icon: 'assignment_return',
    },
    {
      id: 'REFUND_PROCESSING',
      label: 'Xử lý hoàn tiền',
      state: step2State,
      description: step2Desc,
      icon: hasFailedRefund ? 'error' : 'currency_exchange',
    },
    {
      id: 'REFUND_COMPLETED',
      label: 'Hoàn tất hoàn tiền',
      state: step3State,
      description: step3Desc,
      icon: 'verified',
    },
  ]

  return {
    hasFinancialObligation: true,
    hasFailedRefund,
    isFullyRefunded,
    steps,
  }
}
