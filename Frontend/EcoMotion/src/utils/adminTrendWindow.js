export const DEFAULT_TREND_WINDOW_SIZE = 5

/**
 * Slices a chronological (oldest-to-newest) month list into a single-row window,
 * anchored at the newest months when offsetFromEnd === 0.
 */
export function getTrendMonthWindow(months, offsetFromEnd = 0, windowSize = DEFAULT_TREND_WINDOW_SIZE) {
  const list = Array.isArray(months) ? months : []
  const total = list.length
  const safeSize = Math.max(1, Number.isFinite(windowSize) ? Math.floor(windowSize) : DEFAULT_TREND_WINDOW_SIZE)
  const maxOffset = Math.max(0, total - safeSize)
  const numericOffset = Number.isFinite(offsetFromEnd) ? Math.floor(offsetFromEnd) : 0
  const safeOffset = Math.min(Math.max(0, numericOffset), maxOffset)
  const endIndex = total - safeOffset
  const startIndex = Math.max(0, endIndex - safeSize)

  return {
    visibleMonths: total > 0 ? list.slice(startIndex, endIndex) : [],
    safeOffset,
    maxOffset,
    canShiftOlder: safeOffset < maxOffset,
    canShiftNewer: safeOffset > 0,
  }
}

/**
 * Steps the end-anchored window offset (+1 moves toward older months,
 * -1 moves toward newer months) while clamping within valid bounds.
 */
export function stepTrendOffset(currentOffset, delta, totalMonths, windowSize = DEFAULT_TREND_WINDOW_SIZE) {
  const total = Math.max(0, Number.isFinite(totalMonths) ? Math.floor(totalMonths) : 0)
  const safeSize = Math.max(1, Number.isFinite(windowSize) ? Math.floor(windowSize) : DEFAULT_TREND_WINDOW_SIZE)
  const maxOffset = Math.max(0, total - safeSize)
  const baseOffset = Math.min(Math.max(0, Number.isFinite(currentOffset) ? Math.floor(currentOffset) : 0), maxOffset)
  const numericDelta = Number.isFinite(delta) ? Math.trunc(delta) : 0
  return Math.min(Math.max(0, baseOffset + numericDelta), maxOffset)
}

