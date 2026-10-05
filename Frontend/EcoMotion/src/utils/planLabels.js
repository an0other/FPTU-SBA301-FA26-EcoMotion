export const PLAN_LABELS = {
  HOURLY: {
    title: 'Gói giờ',
    detail: '1 slot = 1 giờ.',
    footer: 'Đã gồm tài xế',
    unit: 'giờ',
  },
  DAILY: {
    title: 'Gói ngày',
    detail: '1 slot = 24 giờ.',
    footer: 'Đã gồm tài xế',
    unit: 'ngày',
  },
  MONTHLY: {
    title: 'Gói tháng',
    detail: '1 slot = 30 ngày.',
    footer: 'Đã gồm tài xế',
    unit: 'tháng',
  },
  YEARLY: {
    title: 'Gói năm',
    detail: '1 slot = 12 tháng.',
    footer: 'Tự lái theo hợp đồng, kỳ 3–5 năm',
    unit: 'năm',
  },
}

export function planLabel(planType) {
  return PLAN_LABELS[planType] ?? PLAN_LABELS.DAILY
}
