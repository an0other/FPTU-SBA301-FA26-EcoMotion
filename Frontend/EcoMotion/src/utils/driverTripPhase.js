import { deriveExecutionStatus } from './driverTripUtils.js'

// Nhóm hiển thị của lịch Tài xế. Nguồn sự thật là trạng thái booking do backend trả về,
// không suy đoán theo giờ đón: chuyến IN_PROGRESS không bao giờ rơi vào nhóm "Sắp tới".
export const TRIP_PHASES = {
  IN_PROGRESS: { key: 'IN_PROGRESS', label: 'Đang diễn ra' },
  UPCOMING: { key: 'UPCOMING', label: 'Sắp tới' },
  COMPLETED: { key: 'COMPLETED', label: 'Đã hoàn thành' },
}

const PHASE_BY_EXECUTION_STATUS = {
  ARRIVED: 'IN_PROGRESS',
  IN_PROGRESS: 'IN_PROGRESS',
  // Trạng thái legacy (trước khi Driver độc quyền bắt đầu/kết thúc): chuyến vẫn đang mở.
  AWAITING_START_CONFIRMATION: 'IN_PROGRESS',
  AWAITING_END_CONFIRMATION: 'IN_PROGRESS',
  AWAITING_EXTRA_CHARGE_CONFIRMATION: 'COMPLETED',
  AWAITING_FINAL_PAYMENT: 'COMPLETED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'COMPLETED',
  REJECTED: 'COMPLETED',
}

export function getTripPhase(trip) {
  return PHASE_BY_EXECUTION_STATUS[deriveExecutionStatus(trip)] ?? 'UPCOMING'
}

/**
 * Gộp chuyến theo nhóm. Mỗi bookingCode chỉ xuất hiện một lần (giữ lần đầu gặp),
 * và các bookingCode trong excludeBookingCodes (ví dụ chuyến đã hiện ở banner) bị bỏ qua.
 */
export function groupTripsByPhase(trips = [], { excludeBookingCodes = [] } = {}) {
  const groups = { IN_PROGRESS: [], UPCOMING: [], COMPLETED: [] }
  const seen = new Set(excludeBookingCodes.filter(Boolean))
  for (const trip of trips) {
    if (!trip) continue
    const code = trip.bookingCode || (trip.tripId != null ? String(trip.tripId) : '')
    if (code) {
      if (seen.has(code)) continue
      seen.add(code)
    }
    groups[getTripPhase(trip)].push(trip)
  }
  return groups
}

/**
 * Ngày YYYY-MM-DD theo giờ Việt Nam, cộng thêm `days` ngày.
 * Không dùng ngày UTC: lúc 00:30 giờ Việt Nam, UTC vẫn là ngày hôm trước.
 */
export function vietnamDateOffset(days = 0, now = new Date()) {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
  const [year, month, day] = today.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}
