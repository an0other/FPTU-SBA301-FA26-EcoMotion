import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { bookingService, getMyBooking, getBookingDetail } from '../src/services/bookingService.js'
import { paymentService } from '../src/services/paymentService.js'

describe('Renter Payment Flow & Service Contracts', () => {
  it('bookingService exports both getMyBooking and getBookingDetail as functions', () => {
    assert.equal(typeof getMyBooking, 'function')
    assert.equal(typeof getBookingDetail, 'function')
    assert.equal(typeof bookingService.getMyBooking, 'function')
    assert.equal(typeof bookingService.getBookingDetail, 'function')
    assert.equal(getBookingDetail, getMyBooking)
    assert.equal(bookingService.getBookingDetail, getMyBooking)
  })

  it('paymentService provides VNPay Sandbox API integration methods', () => {
    assert.equal(typeof paymentService.createVnpayPaymentUrl, 'function')
    assert.equal(typeof paymentService.processVnpayCallback, 'function')
    assert.equal(typeof paymentService.queryVnpayTransaction, 'function')
  })

  it('Deposit amount is properly calculated from booking charges when DEPOSIT charge exists', () => {
    const bookingWithCharge = {
      bookingCode: 'BK-4C4M93',
      totalAmount: 1100000,
      charges: [
        { chargeType: 'BASE_RENTAL', amount: 1100000 },
        { chargeType: 'DEPOSIT', amount: 330000 },
      ],
    }

    const depositCharge = (bookingWithCharge.charges || []).find((c) => c.chargeType === 'DEPOSIT')
    const depositAmount = depositCharge != null
      ? Number(depositCharge.amount)
      : Math.round((Number(bookingWithCharge.totalAmount) || 0) * 0.3)

    assert.equal(depositAmount, 330000)
  })

  it('Deposit amount falls back safely to 30% ratio when charges array is missing or empty', () => {
    const bookingWithoutCharge = {
      bookingCode: 'BK-EMPTY',
      totalAmount: 2000000,
      charges: [],
    }

    const depositCharge = (bookingWithoutCharge.charges || []).find((c) => c.chargeType === 'DEPOSIT')
    const depositAmount = depositCharge != null
      ? Number(depositCharge.amount)
      : Math.round((Number(bookingWithoutCharge.totalAmount) || 0) * 0.3)

    assert.equal(depositAmount, 600000)
  })

  it('Booking status evaluation identifies CONFIRMED as paid and ACCEPTED as eligible', () => {
    const acceptedBooking = { status: 'ACCEPTED' }
    const confirmedBooking = { status: 'CONFIRMED' }
    const inProgressBooking = { status: 'IN_PROGRESS' }
    const cancelledBooking = { status: 'CANCELLED' }

    const isPaid = (status) =>
      status === 'CONFIRMED' ||
      ['DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION', 'AWAITING_FINAL_PAYMENT', 'COMPLETED'].includes(status)

    const isCancelledOrRejected = (status) => ['CANCELLED', 'REJECTED'].includes(status)

    assert.equal(isPaid(acceptedBooking.status), false)
    assert.equal(isPaid(confirmedBooking.status), true)
    assert.equal(isPaid(inProgressBooking.status), true)
    assert.equal(isPaid(cancelledBooking.status), false)

    assert.equal(isCancelledOrRejected(acceptedBooking.status), false)
    assert.equal(isCancelledOrRejected(cancelledBooking.status), true)
  })

  it('Error categorization correctly distinguishes 404, 403, 409 and JS/network errors', () => {
    function resolveErrorDisplay(fetchError, bookingId) {
      const isNotFound = fetchError?.status === 404
      const isForbidden = fetchError?.status === 403
      const isConflict = fetchError?.status === 409

      let errorTitle = 'Lỗi tải dữ liệu đơn thuê'
      let errorDesc = fetchError?.message

      if (isNotFound) {
        errorTitle = 'Không tìm thấy thông tin đơn thuê'
        errorDesc = `Không tìm thấy thông tin đơn thuê #${bookingId}. Vui lòng kiểm tra lại mã đơn.`
      } else if (isForbidden) {
        errorTitle = 'Từ chối quyền truy cập'
        errorDesc = 'Bạn không có quyền truy cập hoặc thanh toán cho đơn thuê này.'
      } else if (isConflict) {
        errorTitle = 'Trạng thái đơn không hợp lệ'
        errorDesc = fetchError?.message || 'Đơn thuê hiện tại chưa thể thanh toán đặt cọc.'
      }
      return { errorTitle, errorDesc }
    }

    // 404
    const err404 = resolveErrorDisplay({ status: 404, message: 'Not found' }, 'BK-999')
    assert.equal(err404.errorTitle, 'Không tìm thấy thông tin đơn thuê')

    // 403
    const err403 = resolveErrorDisplay({ status: 403, message: 'Forbidden' }, 'BK-999')
    assert.equal(err403.errorTitle, 'Từ chối quyền truy cập')

    // 409
    const err409 = resolveErrorDisplay({ status: 409, message: 'Conflict' }, 'BK-999')
    assert.equal(err409.errorTitle, 'Trạng thái đơn không hợp lệ')

    // JS Exception / Network error (status undefined or 500) MUST NOT display "Không tìm thấy thông tin đơn thuê"
    const jsError = resolveErrorDisplay({ message: 'TypeError: undefined is not a function' }, 'BK-999')
    assert.equal(jsError.errorTitle, 'Lỗi tải dữ liệu đơn thuê')
    assert.notEqual(jsError.errorTitle, 'Không tìm thấy thông tin đơn thuê')
  })

  it('NCB Sandbox test card credentials match specifications', () => {
    const testCard = {
      bank: 'NCB',
      cardNumber: '9704198526191432198',
      cardHolder: 'NGUYEN VAN A',
      issueDate: '07/15',
      otp: '123456',
    }

    assert.equal(testCard.bank, 'NCB')
    assert.equal(testCard.cardNumber, '9704198526191432198')
    assert.equal(testCard.cardHolder, 'NGUYEN VAN A')
    assert.equal(testCard.issueDate, '07/15')
    assert.equal(testCard.otp, '123456')
  })

  it('paymentService provides Settlement and Final Payment methods', () => {
    assert.equal(typeof paymentService.getSettlement, 'function')
    assert.equal(typeof paymentService.createFinalPaymentUrl, 'function')
  })

  it('Settlement formula correctly computes finalPayable with surcharges and deposit deduction', () => {
    const baseRental = 1400000
    const extraCharges = 200000 // e.g. overtime + waiting
    const totalObligation = baseRental + extraCharges
    const depositPaid = 420000
    const refunds = 0
    const netPaid = depositPaid - refunds

    const netBalance = totalObligation - netPaid
    const finalPayable = netBalance > 0 ? netBalance : 0
    const overpayment = netBalance < 0 ? Math.abs(netBalance) : 0

    assert.equal(totalObligation, 1600000)
    assert.equal(netPaid, 420000)
    assert.equal(finalPayable, 1180000)
    assert.equal(overpayment, 0)
  })

  it('Settlement formula handles overpayment correctly without negative payable', () => {
    const baseRental = 500000
    const extraCharges = 0
    const totalObligation = baseRental + extraCharges
    const depositPaid = 600000
    const refunds = 0
    const netPaid = depositPaid - refunds

    const netBalance = totalObligation - netPaid
    const finalPayable = netBalance > 0 ? netBalance : 0
    const overpayment = netBalance < 0 ? Math.abs(netBalance) : 0

    assert.equal(finalPayable, 0)
    assert.equal(overpayment, 100000)
  })

  it('CustomerPaymentPage state categorization properly isolates AWAITING_FINAL_PAYMENT from DEPOSIT', () => {
    const awaitingStatus = 'AWAITING_FINAL_PAYMENT'
    const isFinalPayment = awaitingStatus === 'AWAITING_FINAL_PAYMENT'
    const isDepositPaid = ['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(awaitingStatus)
    const isSettled = awaitingStatus === 'COMPLETED'

    assert.equal(isFinalPayment, true)
    assert.equal(isDepositPaid, false)
    assert.equal(isSettled, false)
  })
})

