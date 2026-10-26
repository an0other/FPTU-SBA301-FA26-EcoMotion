import { formatCurrency } from './formatCurrency.js'

export const PLAN_LABELS = {
  HOURLY: {
    title: 'Gói giờ',
    footer: 'Đã gồm tài xế',
    unit: 'giờ',
  },
  DAILY: {
    title: 'Gói ngày',
    footer: 'Đã gồm tài xế',
    unit: 'ngày',
  },
  MONTHLY: {
    title: 'Gói tháng',
    footer: 'Đã gồm tài xế',
    unit: 'tháng',
  },
  YEARLY: {
    title: 'Gói năm',
    footer: 'Hợp đồng năm · quy trình riêng',
    unit: 'năm',
  },
}

export function planLabel(planType) {
  return PLAN_LABELS[planType] ?? PLAN_LABELS.DAILY
}

/**
 * Convert slotLengthMinutes to a human-readable Vietnamese string.
 * Examples: 60 → "1 giờ", 240 → "4 giờ", 1440 → "1 ngày", 43200 → "30 ngày"
 */
export function slotDurationLabel(minutes) {
  const m = Number(minutes)
  if (!Number.isFinite(m) || m <= 0) return null
  if (m % 43200 === 0) {
    const months = m / 43200
    return `${months} tháng`
  }
  if (m % 1440 === 0) {
    const days = m / 1440
    return `${days} ngày`
  }
  if (m % 60 === 0) {
    const hours = m / 60
    return `${hours} giờ`
  }
  return `${m} phút`
}

export function formatSlotPrice(amount, slotLengthMinutes) {
  const money = formatCurrency(amount)
  if (money === '—') return money
  const duration = slotDurationLabel(slotLengthMinutes)
  return duration ? `${money} / ${duration}` : money
}

export const PLAN_PRICE_SUFFIX = {
  HOURLY: '/giờ',
  DAILY: '/ngày',
  MONTHLY: '/tháng',
  YEARLY: '/năm',
}

export function rentalSpanLabel(slotLengthMinutes, slots) {
  const count = Number(slots)
  const minutes = Number(slotLengthMinutes)
  if (!Number.isFinite(minutes) || minutes <= 0 || !Number.isFinite(count) || count <= 0) {
    return ''
  }
  return slotDurationLabel(minutes * count) ?? ''
}
