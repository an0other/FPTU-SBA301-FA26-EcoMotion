// Phân loại dữ liệu trạng thái Driver (DriverStatusResponse) thành khung hiển thị dashboard.
// Dữ liệu nguồn: activeTrip (chuyến đang thực hiện, kể cả chờ xác nhận bắt đầu/kết thúc) và
// nextTrip (chuyến kế tiếp: đã xác nhận, hoặc yêu cầu DRIVER_ASSIGNED còn hạn phản hồi).
// Không có logic ở đây đoán trạng thái; mọi quyết định dựa trên status và timestamp backend trả về.

export const DASHBOARD_MODE = Object.freeze({
  IN_TRIP: 'IN_TRIP',
  PENDING_CONFIRMATION: 'PENDING_CONFIRMATION',
  UPCOMING_CONFIRMED: 'UPCOMING_CONFIRMED',
  EMPTY: 'EMPTY',
})

// Các trạng thái chuyến đã bắt đầu theo nghiệp vụ (xem TripTransitions): không còn là yêu cầu chờ xác nhận.
export const IN_TRIP_STATUSES = Object.freeze([
  'AWAITING_START_CONFIRMATION',
  'IN_PROGRESS',
  'AWAITING_END_CONFIRMATION',
])

// Trạng thái kết thúc: không được hiển thị như chuyến còn có thể xác nhận hoặc đang chạy.
const TERMINAL_STATUSES = Object.freeze(['COMPLETED', 'CANCELLED', 'REJECTED', 'CANCELLATION_REVIEW'])

const IN_TRIP_LABELS = {
  AWAITING_START_CONFIRMATION: 'Chờ xác nhận bắt đầu',
  IN_PROGRESS: 'Đang diễn ra',
  AWAITING_END_CONFIRMATION: 'Chờ xác nhận kết thúc',
}

export function inTripStatusLabel(status) {
  return IN_TRIP_LABELS[status] || 'Đang thực hiện'
}

// Số giây còn lại tới hạn phản hồi; null nếu không có hạn. Không hardcode thời lượng.
export function countdownSeconds(expiresAt, nowMs) {
  if (!expiresAt) return null
  const expiresMs = Date.parse(expiresAt)
  if (Number.isNaN(expiresMs)) return null
  return Math.max(0, Math.floor((expiresMs - nowMs) / 1000))
}

export function formatCountdown(totalSeconds) {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

/**
 * @returns {{ mode: string, trip: object|null, showAvailability: boolean, countdown: number|null }}
 */
export function buildDriverDashboardView(status, nowMs) {
  const empty = { mode: DASHBOARD_MODE.EMPTY, trip: null, showAvailability: true, countdown: null }
  if (!status) return empty

  const active = status.activeTrip
  if (active && IN_TRIP_STATUSES.includes(active.status) && !TERMINAL_STATUSES.includes(active.status)) {
    return { mode: DASHBOARD_MODE.IN_TRIP, trip: active, showAvailability: false, countdown: null }
  }

  const next = status.nextTrip
  if (!next || TERMINAL_STATUSES.includes(next.status)) return empty

  if (next.status === 'DRIVER_ASSIGNED') {
    const countdown = countdownSeconds(next.assignmentExpiresAt, nowMs)
    // Hết hạn (hoặc thiếu hạn): không hiển thị nút xác nhận và không đếm ngược.
    if (countdown === null || countdown <= 0) return empty
    return { mode: DASHBOARD_MODE.PENDING_CONFIRMATION, trip: next, showAvailability: true, countdown }
  }

  // CONFIRMED, DRIVER_CONFIRMED, DRIVER_ARRIVED: đã xác nhận nhận chuyến, chưa bắt đầu.
  return { mode: DASHBOARD_MODE.UPCOMING_CONFIRMED, trip: next, showAvailability: true, countdown: null }
}

// Nút xác nhận chỉ đi cùng yêu cầu còn hiệu lực.
export function canConfirmAssignment(view) {
  return view.mode === DASHBOARD_MODE.PENDING_CONFIRMATION && view.countdown > 0
}
