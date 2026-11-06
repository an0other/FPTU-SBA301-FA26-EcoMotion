// Trạng thái chỉ đọc của chuyến đi trên trang Provider.
// Bắt đầu/kết thúc chuyến do Tài xế và Khách thuê cùng xác nhận; Provider không thao tác.
// Mốc xác nhận từng bên lấy từ `booking.trip` (ProviderBookingDetailResponse.TripSummary),
// không suy diễn từ trạng thái.

const TITLES = {
  DRIVER_ARRIVED: 'Tài xế đã đến điểm đón.',
  AWAITING_START_CONFIRMATION: 'Đang chờ hoàn tất xác nhận bắt đầu.',
  IN_PROGRESS: 'Chuyến đi đang diễn ra.',
  AWAITING_END_CONFIRMATION: 'Đang chờ hoàn tất xác nhận kết thúc.',
}

// Tên trường mốc xác nhận của từng bên, theo trạng thái cần hiển thị.
const PARTY_FIELDS = {
  AWAITING_START_CONFIRMATION: { driver: 'driverStartConfirmedAt', renter: 'renterStartConfirmedAt' },
  AWAITING_END_CONFIRMATION: { driver: 'driverEndConfirmedAt', renter: 'renterEndConfirmedAt' },
}

/**
 * @returns {{ title: string, parties: Array<{label: string, confirmedAt: string|null}> | null } | null}
 *   null khi trạng thái không thuộc nhóm hiển thị.
 *   parties = null khi backend không trả mốc xác nhận của chuyến (không đoán thay).
 */
export function getTripConfirmationView(booking) {
  const title = TITLES[booking?.status]
  if (!title) return null

  const fields = PARTY_FIELDS[booking.status]
  const trip = booking.trip
  let parties = null
  if (fields && trip && typeof trip === 'object' && fields.driver in trip && fields.renter in trip) {
    parties = [
      { label: 'Tài xế', confirmedAt: trip[fields.driver] ?? null },
      { label: 'Khách thuê', confirmedAt: trip[fields.renter] ?? null },
    ]
  }
  return { title, parties }
}

export const TRIP_CONFIRMATION_STATUSES = Object.freeze(Object.keys(TITLES))
