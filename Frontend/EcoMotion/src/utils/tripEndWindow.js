/**
 * Cửa sổ 12 giờ để Renter xác nhận giờ kết thúc do tài xế báo (không chặn luồng phụ phí/quyết toán).
 * Đọc lần báo kết thúc mới nhất trong workflow.endConfirmations (BE trả theo id giảm dần).
 */
export function tripEndWindow(workflow, now = Date.now()) {
  const latest = (workflow?.endConfirmations || [])
    .slice()
    .sort((a, b) => Number(b.id) - Number(a.id))[0]
  if (!latest) return { phase: 'NONE' }
  const deadline = Date.parse(latest.deadline)
  const secondsLeft = Number.isFinite(deadline) ? Math.max(0, Math.floor((deadline - now) / 1000)) : 0
  const base = {
    reportedAt: latest.reportedAt,
    deadline: latest.deadline,
    confirmationMethod: latest.confirmationMethod || null,
    confirmedAt: latest.confirmedAt || null,
    secondsLeft,
  }
  if (latest.state === 'WAITING') return { ...base, phase: secondsLeft > 0 ? 'WAITING' : 'EXPIRING' }
  if (latest.state === 'PAUSED') return { ...base, phase: 'DISPUTED' }
  if (latest.state === 'CONFIRMED') return { ...base, phase: 'CONFIRMED' }
  return { ...base, phase: 'NONE' }
}

export function formatCountdown(totalSeconds) {
  const s = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  return [h, m, sec].map((part) => String(part).padStart(2, '0')).join(':')
}

export const END_CONFIRMATION_METHOD_LABELS = {
  RENTER: 'Khách đã xác nhận',
  PAYMENT: 'Khách đã thanh toán số dư (xem như đồng ý)',
  AUTO: 'Tự chốt sau 12 giờ',
  ADMIN: 'Admin đã phân xử',
  DRIVER: 'Tài xế kết thúc',
}
