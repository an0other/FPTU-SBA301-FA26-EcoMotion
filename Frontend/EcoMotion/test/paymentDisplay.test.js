import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatChargeDescription,
  formatChargeQuantity,
} from '../src/utils/paymentDisplay.js'

test('yearly rent quantity is described as monthly installments rather than slots', () => {
  assert.equal(
    formatChargeQuantity({ chargeType: 'BASE_RENTAL', quantity: 12, planType: 'YEARLY' }),
    '12 kỳ (tháng)',
  )
})

test('other rental quantities use renter-facing wording', () => {
  assert.equal(
    formatChargeQuantity({ chargeType: 'BASE_RENTAL', quantity: 2, planType: 'DAILY' }),
    '2 lượt thuê',
  )
})

test('legacy seeded yearly description is displayed in Vietnamese with installment context', () => {
  assert.match(
    formatChargeDescription(
      { description: 'Yearly rental fee, paid in 12 monthly installments (demo seed)' },
      'YEARLY',
    ),
    /12 kỳ hằng tháng/,
  )
})
