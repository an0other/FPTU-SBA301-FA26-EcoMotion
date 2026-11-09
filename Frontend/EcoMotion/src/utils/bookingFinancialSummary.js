import { formatCurrency } from './formatCurrency.js'

const CLOSED = new Set(['CANCELLED', 'REJECTED', 'COMPLETED'])
const COST_CHARGE_TYPES = new Set(['BASE_RENTAL', 'DEPOSIT'])

function isAwaitingDeposit(status, paymentStatus) {
  if (['DEPOSIT_PAID', 'FULLY_PAID', 'PAID', 'PARTIALLY_PAID'].includes(paymentStatus)) return false
  return ['AWAITING_DEPOSIT', 'ACCEPTED'].includes(status) || paymentStatus === 'AWAITING_DEPOSIT'
}

function surchargeItemsFromCharges(charges) {
  if (!charges?.length) return []
  return charges.filter(
    (charge) =>
      !charge.isWaived
      && !COST_CHARGE_TYPES.has(charge.chargeType)
      && Number(charge.amount) > 0,
  )
}

/**
 * Dòng hiển thị tài chính — tránh liệt kê cọc + đã trả + còn phải trả như ba khoản cộng dồn.
 */
export function computeBookingFinancialLines({
  totalAmount,
  pickupFee,
  depositAmount,
  confirmedPaid,
  remainingAmount,
  status,
  paymentStatus,
  charges,
}) {
  const rent = Number(totalAmount) || 0
  // Phí đón ngoài bán kính đã là một dòng charge OUT_OF_RADIUS; không cộng pickupFee lần nữa.
  const pickupFeeInCharges = Boolean(charges?.some((charge) => charge.chargeType === 'OUT_OF_RADIUS'))
  const fee = pickupFeeInCharges ? 0 : Number(pickupFee) || 0
  const surcharges = surchargeItemsFromCharges(charges)
  const surchargeTotal = surcharges.reduce((sum, item) => sum + Number(item.amount), 0)
  const grand = rent + fee + surchargeTotal
  const deposit = Number(depositAmount) || 0
  const paid = Number(confirmedPaid) || 0
  const remaining = remainingAmount != null && !Number.isNaN(Number(remainingAmount))
    ? Number(remainingAmount)
    : null
  const closed = CLOSED.has(status)
  const awaitingDeposit = !closed && isAwaitingDeposit(status, paymentStatus) && paid <= 0
  const fullyPaid = paymentStatus === 'FULLY_PAID'
    || (closed && paid > 0 && (remaining == null || remaining <= 0))

  const lines = []

  if (rent > 0) {
    lines.push({ key: 'rent', section: 'cost', label: 'Tiền thuê', amount: rent })
  }
  if (fee > 0) {
    lines.push({ key: 'fee', section: 'cost', label: 'Phí đón', amount: fee })
  }
  surcharges.forEach((charge) => {
    lines.push({
      key: `surcharge-${charge.chargeType}-${charge.chargeId ?? charge.label}`,
      section: 'cost',
      label: charge.label || 'Phụ phí',
      amount: Number(charge.amount),
      note: charge.description || null,
    })
  })
  if (grand > 0) {
    lines.push({
      key: 'grand',
      section: 'cost',
      label: closed && fullyPaid ? 'Tổng phải trả' : 'Tổng chi phí dự kiến',
      amount: grand,
      emphasize: true,
      note: surchargeTotal > 0
        ? 'Tiền thuê, phí đón và phụ phí phát sinh'
        : 'Tiền thuê và phí đón (nếu có)',
    })
  }

  if (closed && paid <= 0 && grand <= 0) return lines

  if (fullyPaid && paid > 0) {
    const depositPaid = deposit > 0 ? Math.min(deposit, paid) : 0
    const settlementPaid = Math.max(0, paid - depositPaid)
    if (depositPaid > 0) {
      lines.push({
        key: 'depositPaid',
        section: 'payment',
        label: 'Cọc đã thanh toán',
        amount: depositPaid,
        note: 'Đã trừ vào tổng phải trả',
      })
    }
    if (settlementPaid > 0) {
      lines.push({
        key: 'settlementPaid',
        section: 'payment',
        label: 'Quyết toán chuyến đã trả',
        amount: settlementPaid,
        note: surchargeTotal > 0
          ? 'Gồm tiền thuê còn lại và phụ phí'
          : 'Phần còn lại sau cọc',
      })
    }
    lines.push({
      key: 'paymentComplete',
      section: 'payment',
      variant: 'completeHighlight',
      label: 'Tổng đã thanh toán',
      amount: paid,
      breakdown: [
        ...(depositPaid > 0 ? [{ label: 'Cọc', amount: depositPaid }] : []),
        ...(settlementPaid > 0 ? [{ label: 'Quyết toán', amount: settlementPaid }] : []),
      ],
    })
    return lines
  }

  const depositOnlyPaid = deposit > 0 && paid > 0 && paid <= deposit + 0.5
  const depositPortionPaid = deposit > 0 && paid > 0 ? Math.min(paid, deposit) : 0

  if (depositPortionPaid > 0 && (paymentStatus === 'DEPOSIT_PAID' || depositOnlyPaid)) {
    lines.push({
      key: 'depositPaid',
      section: 'payment',
      label: 'Cọc đã thanh toán',
      amount: depositPortionPaid,
      note: 'Đã trừ vào tổng chi phí — chưa gồm quyết toán cuối chuyến',
    })
  } else if (paid > 0) {
    const isDepositOnly = deposit > 0 && paid <= deposit
    lines.push({
      key: 'paid',
      section: 'payment',
      label: isDepositOnly ? 'Đã thanh toán (cọc giữ chỗ)' : 'Đã thanh toán',
      amount: paid,
      note: isDepositOnly ? 'Khoản này trừ vào tổng chi phí — không phải phí cộng thêm' : 'Đã trừ vào tổng chi phí',
    })
  } else if (deposit > 0 && awaitingDeposit && !closed) {
    lines.push({
      key: 'depositDue',
      section: 'payment',
      label: 'Cọc cần trả ngay',
      amount: deposit,
      note: 'Trả trước để giữ chỗ; được trừ vào tổng, không cộng thêm ngoài tổng chi phí',
    })
    const afterDeposit = Math.max(0, grand - deposit)
    if (afterDeposit > 0) {
      lines.push({
        key: 'afterDeposit',
        section: 'payment',
        label: 'Còn lại dự kiến (sau cọc)',
        amount: afterDeposit,
        note: 'Thanh toán khi quyết toán chuyến',
      })
    }
  }

  const remainingIsFullTotal = grand > 0 && remaining != null && paid <= 0 && Math.abs(remaining - grand) < 1
  const skipRemaining = awaitingDeposit && (remainingIsFullTotal || (deposit > 0 && remaining != null && Math.abs(remaining - (grand - deposit)) < 1))

  if (!closed && remaining != null && remaining > 0 && !skipRemaining) {
    lines.push({
      key: 'remaining',
      section: 'payment',
      label: paid > 0 ? 'Còn phải trả khi quyết toán' : 'Còn phải trả',
      amount: remaining,
      note: paid > 0 ? 'Tổng chi phí dự kiến trừ phần đã thanh toán (gồm cọc)' : null,
      emphasize: true,
    })
  } else if (!closed && paid > 0 && grand > paid && remaining == null) {
    lines.push({
      key: 'remainingCalc',
      section: 'payment',
      label: 'Còn phải trả khi quyết toán',
      amount: grand - paid,
      note: 'Tổng chi phí dự kiến trừ phần đã thanh toán (gồm cọc)',
      emphasize: true,
    })
  }

  return lines
}

export function formatFinancialLineAmount(amount) {
  return formatCurrency(amount)
}
