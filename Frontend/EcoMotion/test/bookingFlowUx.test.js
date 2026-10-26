import test from 'node:test'
import assert from 'node:assert/strict'
import { formatSlotPrice, planLabel, rentalSpanLabel, slotDurationLabel } from '../src/utils/planLabels.js'
import { formatBookingWhen, parseVietnamDateTime } from '../src/utils/bookingDateTime.js'
import { DEPOSIT_HOLD_COPY } from '../src/utils/bookingCopy.js'

test('formatSlotPrice uses real slot duration, never a bare /slot', () => {
  assert.equal(formatSlotPrice(150000, 240), '150.000 đ / 4 giờ')
  assert.equal(formatSlotPrice(500000, 1440), '500.000 đ / 1 ngày')
  assert.equal(formatSlotPrice(12_000_000, 43200), '12.000.000 đ / 1 tháng')
  assert.equal(slotDurationLabel(60), '1 giờ')
  assert.equal(rentalSpanLabel(240, 2), '8 giờ')
})

test('Vietnam parser stays on the civil date across midnight, month end, and year end', () => {
  const midnightStart = parseVietnamDateTime('2026-10-26', '00:00')
  assert.equal(midnightStart.toISOString(), '2026-10-25T17:00:00.000Z')
  assert.match(formatBookingWhen(midnightStart), /26\/10\/2026/)
  assert.match(formatBookingWhen(midnightStart), /00:00/)

  const late = parseVietnamDateTime('2026-10-26', '23:00')
  const nextDay = new Date(late.getTime() + 2 * 60 * 60 * 1000)
  assert.match(formatBookingWhen(nextDay), /27\/10\/2026/)
  assert.match(formatBookingWhen(nextDay), /01:00/)

  const monthEnd = parseVietnamDateTime('2026-10-31', '23:00')
  const november = new Date(monthEnd.getTime() + 2 * 60 * 60 * 1000)
  assert.match(formatBookingWhen(november), /01\/11\/2026/)

  const yearEnd = parseVietnamDateTime('2026-12-31', '23:00')
  const nextYear = new Date(yearEnd.getTime() + 2 * 60 * 60 * 1000)
  assert.match(formatBookingWhen(nextYear), /01\/01\/2027/)
})

test('YEARLY copy is a separate contract, not chauffeur hire', () => {
  assert.equal(planLabel('YEARLY').footer, 'Hợp đồng năm · quy trình riêng')
  assert.equal(planLabel('DAILY').footer, 'Đã gồm tài xế')
})

test('hold copy distinguishes submit from CONFIRMED and names both payment channels', () => {
  assert.match(DEPOSIT_HOLD_COPY.submitHint, /15 phút/)
  assert.match(DEPOSIT_HOLD_COPY.submitHint, /chưa phải chuyến đã xác nhận/i)
  assert.match(DEPOSIT_HOLD_COPY.confirmedWhen, /VNPAY/)
  assert.match(DEPOSIT_HOLD_COPY.confirmedWhen, /VietQR/)
  assert.match(DEPOSIT_HOLD_COPY.confirmedWhen, /đối soát/)
})
