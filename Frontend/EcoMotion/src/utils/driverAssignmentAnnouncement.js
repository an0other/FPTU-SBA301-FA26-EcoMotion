// Quyết định có hiện toast "Tài xế đã được phân công" hay không, dựa trên dữ liệu booking đã xác nhận (driverId),
// không dựa trên payload của event. Chỉ báo khi driverId đổi sang một tài xế khác trong CÙNG một đơn.
// Lần tải đầu (previous = null), refetch lặp lại do SSE reconnect hoặc polling với cùng driverId đều không báo.

export function shouldAnnounceDriverAssigned(previous, next) {
  if (!previous || !next) return false
  if (previous.bookingCode !== next.bookingCode) return false
  if (next.driverId == null) return false
  return next.driverId !== previous.driverId
}

export function assignmentSnapshot(booking) {
  if (!booking || !booking.bookingCode) return null
  return { bookingCode: booking.bookingCode, driverId: booking.driverId ?? null }
}
