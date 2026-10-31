import test from 'node:test'
import assert from 'node:assert/strict'
import { rentalSelection, rentalPayload, parseRentalTime, rentalTimeInput, rentalTimeError } from '../src/utils/rentalPricing.js'

test('requests whole hours in Vietnam and preserves the desired interval across midnight', () => {
  assert.equal(parseRentalTime('2030-11-01T10:30'), null)
  assert.equal(rentalSelection(3, '2030-11-01T10:00', '2030-11-01T09:00'), null)
  const request = rentalSelection(3, '2030-11-01T23:00', '2030-11-02T01:00')
  assert.equal(request.pickupTime, '2030-11-01T16:00:00.000Z')
  assert.equal(request.requestedEndAt, '2030-11-01T18:00:00.000Z')
  assert.equal(rentalTimeInput(request.requestedEndAt), '2030-11-02T01:00')
})
test('checkout sends the chosen coverage end and strips client prices from the package selection', () => {
  const request = { pickupTime: '2030-11-01T03:00:00Z', requestedEndAt: '2030-11-04T05:00:00Z' }
  const option = { plannedEndAt: '2030-11-05T03:00:00Z', rentalAmount: 4000000,
    items: [{ planId: 2, quantity: 4, unitPrice: 1000000 }] }
  const body = rentalPayload(3, request, option, { address: ' Điểm đón ', latitude: 10, longitude: 106 }, 3, ' Gọi trước ')
  assert.equal(body.plannedEndAt, option.plannedEndAt)
  assert.equal(body.expectedRentalAmount, 4000000)
  assert.deepEqual(body.pricingItems, [{ planId: 2, quantity: 4 }])
  assert.equal(body.pickupAddress, 'Điểm đón')
  assert.equal(body.driverNote, 'Gọi trước')
  assert.equal(body.slotCount, undefined)
})

test('reports return-before-pickup separately and waits for complete date/hour selections', () => {
  const message = 'Thời gian trả xe phải sau thời gian nhận xe.'
  assert.equal(rentalTimeError('2030-11-04T13:00', '2030-11-04T12:00'), message)
  assert.equal(rentalTimeError('2030-11-04T12:00', '2030-11-04T12:00'), message)
  assert.equal(rentalTimeError('2030-11-04T12:00', '2030-11-05T00:00'), '')
  assert.equal(rentalTimeError('2030-11-04T', '2030-11-05T00:00'), '')
  assert.equal(rentalTimeError('', ''), '')
})
