export function formatCurrency(amount) {
  if (amount === null || amount === undefined || amount === '') {
    return '—'
  }
  const value = Number(amount)
  if (Number.isNaN(value)) {
    return '—'
  }
  return `${new Intl.NumberFormat('vi-VN').format(value)} đ`
}

export const PLAN_LABELS = {
  HOURLY: 'Giờ',
  DAILY: 'Ngày',
  MONTHLY: 'Tháng',
  YEARLY: 'Năm',
}

export const FUEL_LABELS = {
  EV: 'Điện',
  GASOLINE: 'Xăng',
  DIESEL: 'Dầu',
  HYBRID: 'Hybrid',
}

export const TRANSMISSION_LABELS = {
  AUTOMATIC: 'Tự động',
  MANUAL: 'Số sàn',
}
