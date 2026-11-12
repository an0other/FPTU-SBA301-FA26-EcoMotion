/**
 * Formats an average rating + count for Admin detail modals.
 * Returns null when there are no reviews yet so callers can display "Chưa có".
 */
export function formatAdminRating(ratingAvg, ratingCount, unit = 'lượt') {
  const count = Number(ratingCount ?? 0)
  if (!Number.isFinite(count) || count <= 0) {
    return null
  }
  const avg = Number(ratingAvg ?? 0)
  const formattedAvg = Number.isFinite(avg) && avg > 0 ? avg.toFixed(1) : '0.0'
  return `★ ${formattedAvg} (${count} ${unit})`
}

