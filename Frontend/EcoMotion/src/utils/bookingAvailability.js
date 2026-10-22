const VN_OFFSET_MS = 7 * 60 * 60 * 1000

export function availabilityWindow(start, end) {
  const startDate = new Date(start)
  const endDate = new Date(end)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime()) || endDate <= startDate) {
    throw new Error('Khoảng thời gian thuê không hợp lệ')
  }
  const vn = new Date(startDate.getTime() + VN_OFFSET_MS)
  const midnightUtc = Date.UTC(vn.getUTCFullYear(), vn.getUTCMonth(), vn.getUTCDate()) - VN_OFFSET_MS
  const nextMidnight = midnightUtc + 24 * 60 * 60 * 1000
  return {
    from: new Date(midnightUtc).toISOString(),
    to: new Date(Math.max(nextMidnight, endDate.getTime())).toISOString(),
  }
}

export function overlapsOccupied(start, end, slots = []) {
  const startMs = new Date(start).getTime()
  const endMs = new Date(end).getTime()
  return slots.some((slot) => startMs < new Date(slot.endAt).getTime()
    && endMs > new Date(slot.startAt).getTime())
}

export function hasValidPickup(pickup) {
  if (pickup?.latitude == null || pickup?.longitude == null
    || pickup.latitude === '' || pickup.longitude === '') return false
  const latitude = Number(pickup?.latitude)
  const longitude = Number(pickup?.longitude)
  return Boolean(pickup?.address?.trim())
    && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
}

export function buildBookingPayload({ vehicleId, planId, slotCount, pickupTime,
  pickup, passengerCount, driverNote }) {
  return {
    vehicleId,
    planId,
    slotCount,
    pickupTime,
    pickupAddress: pickup.address.trim(),
    pickupLatitude: Number(pickup.latitude),
    pickupLongitude: Number(pickup.longitude),
    passengerCount,
    driverNote: driverNote?.trim() || undefined,
  }
}
