import test from 'node:test'
import assert from 'node:assert/strict'
import { computeBookingFinancialLines } from '../src/utils/bookingFinancialSummary.js'

test('awaiting deposit shows cọc and after-deposit, not full remaining as duplicate total', () => {
  const lines = computeBookingFinancialLines({
    totalAmount: 1000000,
    pickupFee: 150000,
    depositAmount: 300000,
    confirmedPaid: 0,
    remainingAmount: 1150000,
    status: 'AWAITING_DEPOSIT',
    paymentStatus: 'UNPAID',
  })
  const keys = lines.map((line) => line.key)
  assert.ok(keys.includes('grand'))
  assert.ok(keys.includes('depositDue'))
  assert.ok(keys.includes('afterDeposit'))
  assert.equal(keys.filter((k) => k === 'remaining').length, 0)
  assert.equal(lines.find((l) => l.key === 'afterDeposit').amount, 850000)
})

test('after deposit paid shows cọc line and settlement remaining highlight', () => {
  const lines = computeBookingFinancialLines({
    totalAmount: 2100000,
    pickupFee: 150000,
    depositAmount: 630000,
    confirmedPaid: 630000,
    remainingAmount: 1620000,
    status: 'CONFIRMED',
    paymentStatus: 'DEPOSIT_PAID',
  })
  const keys = lines.map((line) => line.key)
  assert.ok(keys.includes('depositPaid'))
  assert.equal(keys.includes('paid'), false)
  assert.equal(keys.includes('depositDue'), false)
  assert.ok(keys.includes('remaining'))
})

test('completed booking shows phụ phí, cọc, quyết toán and large complete highlight', () => {
  const lines = computeBookingFinancialLines({
    totalAmount: 1100000,
    pickupFee: 0,
    depositAmount: 330000,
    confirmedPaid: 1175000,
    remainingAmount: 0,
    status: 'COMPLETED',
    paymentStatus: 'FULLY_PAID',
    charges: [
      {
        chargeId: 1,
        chargeType: 'OUT_OF_RADIUS',
        label: 'Phí đón ngoài phạm vi',
        amount: 75000,
        isWaived: false,
      },
    ],
  })
  const keys = lines.map((line) => line.key)
  assert.ok(keys.some((k) => k.startsWith('surcharge-')))
  assert.equal(lines.find((l) => l.key === 'grand').amount, 1175000)
  assert.ok(keys.includes('depositPaid'))
  assert.ok(keys.includes('settlementPaid'))
  assert.ok(keys.includes('paymentComplete'))
  assert.equal(lines.find((l) => l.key === 'depositPaid').amount, 330000)
  assert.equal(lines.find((l) => l.key === 'settlementPaid').amount, 845000)
})

test('không cộng phí đón hai lần khi đã có dòng charge OUT_OF_RADIUS (EM-2026-000043)', () => {
  const charges = [
    { chargeType: 'BASE_RENTAL', amount: 1460000, isWaived: false },
    { chargeType: 'OUT_OF_RADIUS', amount: 120000, isWaived: false, label: 'Phí đón ngoài phạm vi' },
  ]
  const awaiting = computeBookingFinancialLines({
    totalAmount: 1460000, pickupFee: 120000, depositAmount: 438000, confirmedPaid: 0,
    remainingAmount: 1580000, status: 'AWAITING_DEPOSIT', paymentStatus: 'UNPAID', charges,
  })
  assert.equal(awaiting.find((l) => l.key === 'grand').amount, 1580000)
  assert.equal(awaiting.some((l) => l.key === 'fee'), false)
  assert.equal(awaiting.find((l) => l.key === 'afterDeposit').amount, 1142000)
  assert.equal(awaiting.some((l) => l.key === 'remaining'), false, 'trang cọc không hiện khối quyết toán mâu thuẫn')

  const paid = computeBookingFinancialLines({
    totalAmount: 1460000, pickupFee: 120000, depositAmount: 438000, confirmedPaid: 438000,
    remainingAmount: 1142000, status: 'CONFIRMED', paymentStatus: 'DEPOSIT_PAID', charges,
  })
  assert.equal(paid.find((l) => l.key === 'grand').amount, 1580000)
  assert.equal(paid.find((l) => l.key === 'remaining').amount, 1142000)
})

test('estimatedGrandTotal: tổng gồm phí đón và phụ phí chưa miễn, nhận cả waived lẫn isWaived', async () => {
  const { estimatedGrandTotal } = await import('../src/utils/bookingFinancialSummary.js')
  // Trang "đã gửi": chưa có charge phí đón, dùng pickupFee
  assert.equal(estimatedGrandTotal({ totalAmount: 1460000, pickupFee: 90000, charges: [] }), 1550000)
  // Nhà xe: charge OUT_OF_RADIUS đã có, không cộng pickupFee lần nữa; khoản đã miễn (waived) bị loại
  assert.equal(estimatedGrandTotal({
    totalAmount: 1460000, pickupFee: 90000,
    charges: [
      { chargeType: 'BASE_RENTAL', amount: 1460000 },
      { chargeType: 'OUT_OF_RADIUS', amount: 90000, waived: false },
      { chargeType: 'OVERTIME', amount: 1320000, waived: true },
    ],
  }), 1550000)
})
