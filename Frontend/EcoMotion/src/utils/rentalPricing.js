import { parseVietnamDateTime } from './bookingDateTime.js'

export function rentalDurationLabel(hours) {
  const days = Math.floor(hours / 24)
  const rest = hours % 24
  return [days ? days + ' ngày' : '', rest ? rest + ' giờ' : ''].filter(Boolean).join(' ')
}
export function pricingDescription(items = []) {
  return items.map(item => item.quantity + ' × gói ' + rentalDurationLabel(item.slotLengthMinutes / 60)).join(' + ')
}
export function parseRentalTime(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:00$/.test(value)) return null
  const [date, time] = value.split('T')
  return parseVietnamDateTime(date, time)
}
export function rentalTimeError(start, end) {
  const pickup = parseRentalTime(start)
  const returned = parseRentalTime(end)
  return pickup && returned && returned <= pickup ? 'Thời gian trả xe phải sau thời gian nhận xe.' : ''
}
export function rentalSelection(vehicleId, start, end) {
  const pickup = parseRentalTime(start)
  const requestedEnd = parseRentalTime(end)
  if (!vehicleId || !pickup || !requestedEnd || requestedEnd <= pickup) return null
  return { vehicleId, pickupTime: pickup.toISOString(), requestedEndAt: requestedEnd.toISOString() }
}
export function rentalPayload(vehicleId, request, option, pickup, passengerCount, driverNote) {
  return { vehicleId, pickupTime: request.pickupTime, plannedEndAt: option.plannedEndAt,
    pricingItems: option.items.map(({ planId, quantity }) => ({ planId, quantity })),
    expectedRentalAmount: option.rentalAmount,
    pickupAddress: pickup.address.trim(), pickupLatitude: Number(pickup.latitude),
    pickupLongitude: Number(pickup.longitude), passengerCount, driverNote: driverNote?.trim() || undefined }
}
export function rentalTimeInput(instant) {
  return new Date(new Date(instant).getTime() + 7 * 3600000).toISOString().slice(0, 16)
}
