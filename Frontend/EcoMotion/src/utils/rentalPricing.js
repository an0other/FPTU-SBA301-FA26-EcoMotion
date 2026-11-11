import { parseVietnamDateTime } from './bookingDateTime.js'

export const MIN_RENTAL_DURATION_HOURS = 4

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
export function minReturnRentalInstant(start) {
  const pickup = parseRentalTime(start)
  if (!pickup) return null
  return new Date(pickup.getTime() + MIN_RENTAL_DURATION_HOURS * 3600000)
}

export function rentalTimeError(start, end) {
  const pickup = parseRentalTime(start)
  const returned = parseRentalTime(end)
  if (!pickup || !returned) return ''
  const minReturn = minReturnRentalInstant(start)
  if (minReturn && returned < minReturn) {
    return `Thời gian trả xe phải sau giờ nhận xe ít nhất ${MIN_RENTAL_DURATION_HOURS} giờ.`
  }
  return ''
}

export const MIN_BOOKING_LEAD_HOURS = 3

export function rentalStartTimeConstraints(now = Date.now()) {
  const earliest = new Date(Math.ceil((now + MIN_BOOKING_LEAD_HOURS * 3600000) / 3600000) * 3600000)
  const minInput = rentalTimeInput(earliest.toISOString())
  const minDate = minInput.slice(0, 10)
  const minHourOnMinDate = minInput.slice(11, 16)
  return {
    minDate,
    minHourForDate: (dateYmd) => (dateYmd === minDate ? minHourOnMinDate : null),
  }
}

export function rentalEndTimeConstraints(start) {
  const minReturn = minReturnRentalInstant(start)
  if (!minReturn) return { minDate: null, minHourForDate: () => null }
  const minInput = rentalTimeInput(minReturn.toISOString())
  const minDate = minInput.slice(0, 10)
  const minHourOnMinDate = minInput.slice(11, 16)
  return {
    minDate,
    minHourForDate: (dateYmd) => (dateYmd === minDate ? minHourOnMinDate : null),
  }
}

export function rentalSelection(vehicleId, start, end) {
  const pickup = parseRentalTime(start)
  const requestedEnd = parseRentalTime(end)
  const minReturn = minReturnRentalInstant(start)
  if (!vehicleId || !pickup || !requestedEnd || !minReturn || requestedEnd < minReturn) return null
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
