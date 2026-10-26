const VIETNAM_TIMEZONE = 'Asia/Ho_Chi_Minh'

export const TRIP_STATUS_CONFIG = {
  ASSIGNED: {
    label: 'Đã phân công',
    badgeClass: 'bg-teal-50 text-teal-700 border-teal-200',
    dotClass: 'bg-teal-500',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    dotClass: 'bg-emerald-500',
  },
  IN_PROGRESS: {
    label: 'Đang thực hiện',
    badgeClass: 'bg-amber-50 text-amber-700 border-amber-200 animate-pulse',
    dotClass: 'bg-amber-500',
  },
  AWAITING_START_CONFIRMATION: {
    label: 'Đang chờ khách xác nhận bắt đầu',
    badgeClass: 'bg-cyan-50 text-cyan-700 border-cyan-200',
    dotClass: 'bg-cyan-500 animate-pulse',
  },
  AWAITING_END_CONFIRMATION: {
    label: 'Đang chờ khách xác nhận kết thúc',
    badgeClass: 'bg-orange-50 text-orange-700 border-orange-200',
    dotClass: 'bg-orange-500 animate-pulse',
  },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: {
    label: 'Chờ nhà cung cấp chốt phụ phí',
    badgeClass: 'bg-purple-50 text-purple-700 border-purple-200',
    dotClass: 'bg-purple-500',
  },
  DRIVER_REPLACEMENT_REQUIRED: {
    label: 'Đang chờ đổi tài xế',
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500 animate-pulse',
  },
  COMPLETED: {
    label: 'Đã hoàn thành',
    badgeClass: 'bg-slate-100 text-slate-700 border-slate-200',
    dotClass: 'bg-slate-400',
  },
  CANCELLED: {
    label: 'Đã hủy',
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500',
  },
  REJECTED: {
    label: 'Đã từ chối',
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500',
  },
}

export const TRIP_EXECUTION_CONFIG = {
  ASSIGNED: {
    key: 'ASSIGNED',
    label: 'Chờ tài xế xác nhận',
    actionLabel: 'Xác nhận nhận chuyến',
    actionType: 'confirm',
    confirmTitle: 'Xác nhận tiếp nhận chuyến đi',
    confirmMessage: 'Bạn có chắc chắn muốn xác nhận nhận chuyến này và bắt đầu chuẩn bị di chuyển đến điểm đón?',
    badgeClass: 'bg-amber-50 text-amber-700 border-amber-200',
    dotClass: 'bg-amber-500',
  },
  CONFIRMED: {
    key: 'CONFIRMED',
    label: 'Đã nhận chuyến - Đang đến đón',
    actionLabel: 'Đã đến điểm đón',
    actionType: 'arrive',
    confirmTitle: 'Xác nhận đã đến điểm đón',
    confirmMessage: 'Bạn đã có mặt tại địa chỉ đón khách và sẵn sàng đón khách?',
    badgeClass: 'bg-blue-50 text-blue-700 border-blue-200',
    dotClass: 'bg-blue-500',
  },
  ARRIVED: {
    key: 'ARRIVED',
    label: 'Đã đến điểm đón',
    actionLabel: 'Bắt đầu chuyến',
    actionType: 'start',
    confirmTitle: 'Bắt đầu chuyến đi',
    confirmMessage: 'Khách đã lên xe an toàn và bạn bắt đầu di chuyển theo hành trình?',
    badgeClass: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    dotClass: 'bg-indigo-500',
  },
  IN_PROGRESS: {
    key: 'IN_PROGRESS',
    label: 'Đang trong chuyến đi',
    actionLabel: 'Kết thúc chuyến',
    actionType: 'finish',
    confirmTitle: 'Xác nhận hoàn thành chuyến đi',
    confirmMessage: 'Khách đã xuống xe an toàn tại điểm đến. Bạn có muốn kết thúc chuyến đi này?',
    badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200 animate-pulse',
    dotClass: 'bg-emerald-500',
  },
  AWAITING_START_CONFIRMATION: {
    key: 'AWAITING_START_CONFIRMATION',
    label: 'Bạn đã xác nhận, đang chờ khách bắt đầu chuyến',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-cyan-50 text-cyan-700 border-cyan-200',
    dotClass: 'bg-cyan-500 animate-pulse',
  },
  AWAITING_END_CONFIRMATION: {
    key: 'AWAITING_END_CONFIRMATION',
    label: 'Bạn đã kết thúc, đang chờ khách xác nhận',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-orange-50 text-orange-700 border-orange-200',
    dotClass: 'bg-orange-500 animate-pulse',
  },
  AWAITING_EXTRA_CHARGE_CONFIRMATION: {
    key: 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
    label: 'Đang chờ nhà cung cấp chốt phụ phí',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-purple-50 text-purple-700 border-purple-200',
    dotClass: 'bg-purple-500',
  },
  DRIVER_REPLACEMENT_REQUIRED: {
    key: 'DRIVER_REPLACEMENT_REQUIRED',
    label: 'Đã báo sự cố, đang chờ nhà cung cấp đổi tài xế',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500 animate-pulse',
  },
  COMPLETED: {
    key: 'COMPLETED',
    label: 'Đã hoàn thành',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-slate-100 text-slate-700 border-slate-200',
    dotClass: 'bg-slate-400',
  },
  CANCELLED: {
    key: 'CANCELLED',
    label: 'Đã hủy',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500',
  },
  REJECTED: {
    key: 'REJECTED',
    label: 'Đã từ chối',
    actionLabel: null,
    actionType: null,
    badgeClass: 'bg-rose-50 text-rose-700 border-rose-200',
    dotClass: 'bg-rose-500',
  },
}

const UNKNOWN_STATUS_CONFIG = {
  label: 'Không xác định',
  badgeClass: 'bg-gray-100 text-gray-600 border-gray-200',
  dotClass: 'bg-gray-400',
}

export function deriveExecutionStatus(trip) {
  if (!trip) return 'UNKNOWN'
  if (trip.executionStatus) {
    return trip.executionStatus.toUpperCase()
  }
  const status = (trip.status || '').toUpperCase()
  if (['COMPLETED', 'CANCELLED', 'REJECTED', 'IN_PROGRESS', 'AWAITING_START_CONFIRMATION', 'AWAITING_END_CONFIRMATION', 'AWAITING_EXTRA_CHARGE_CONFIRMATION', 'DRIVER_REPLACEMENT_REQUIRED'].includes(status)) {
    return status
  }
  if (trip.actualStartAt) return 'IN_PROGRESS'
  if (trip.actualArrivedAt) return 'ARRIVED'
  if (trip.driverConfirmedAt) return 'CONFIRMED'
  return 'ASSIGNED'
}

export function getTripStatusConfig(status) {
  if (!status || typeof status !== 'string') {
    return UNKNOWN_STATUS_CONFIG
  }
  const normalized = status.toUpperCase().trim()
  return TRIP_STATUS_CONFIG[normalized] || UNKNOWN_STATUS_CONFIG
}

export function getExecutionStatusConfig(tripOrStatus) {
  const statusKey = typeof tripOrStatus === 'string'
    ? tripOrStatus.toUpperCase().trim()
    : deriveExecutionStatus(tripOrStatus)
  return TRIP_EXECUTION_CONFIG[statusKey] || UNKNOWN_STATUS_CONFIG
}

export function isLocationStale(receivedAt, maxAgeSeconds = 60) {
  if (!receivedAt) return true
  try {
    const timestamp = new Date(receivedAt).getTime()
    if (isNaN(timestamp)) return true
    return Date.now() - timestamp > maxAgeSeconds * 1000
  } catch {
    return true
  }
}

export const TRIP_VIEWS = {
  TODAY: { key: 'TODAY', label: 'Hôm nay' },
  UPCOMING: { key: 'UPCOMING', label: 'Sắp tới' },
  HISTORY: { key: 'HISTORY', label: 'Lịch sử' },
}

export const STATUS_FILTER_OPTIONS = [
  { value: 'ALL', label: 'Tất cả trạng thái' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'IN_PROGRESS', label: 'Đang thực hiện' },
  { value: 'COMPLETED', label: 'Đã hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
]

export function formatTripDateTime(isoString) {
  if (!isoString) return 'Chưa có lịch hẹn'
  try {
    const date = new Date(isoString)
    if (isNaN(date.getTime())) return 'Thời gian không hợp lệ'

    const timeFormatter = new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    })

    const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      weekday: 'short',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })

    return `${timeFormatter.format(date)} - ${dateFormatter.format(date)}`
  } catch {
    return 'Thời gian không hợp lệ'
  }
}

export function formatTripTime(isoString) {
  if (!isoString) return '--:--'
  try {
    const date = new Date(isoString)
    if (isNaN(date.getTime())) return '--:--'
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(date)
  } catch {
    return '--:--'
  }
}

export function formatTripDate(isoString) {
  if (!isoString) return 'Chưa xác định'
  try {
    const date = new Date(isoString)
    if (isNaN(date.getTime())) return 'Chưa xác định'
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: VIETNAM_TIMEZONE,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date)
  } catch {
    return 'Chưa xác định'
  }
}

export function getRelativeDayLabel(isoString, referenceDate = new Date()) {
  if (!isoString) return ''
  try {
    const date = new Date(isoString)
    if (isNaN(date.getTime())) return ''

    const toYMD = (d) => {
      const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: VIETNAM_TIMEZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(d)
      return parts
    }

    const tripYMD = toYMD(date)
    const refYMD = toYMD(referenceDate)

    if (tripYMD === refYMD) return 'Hôm nay'

    const refDateObj = new Date(refYMD)
    const tripDateObj = new Date(tripYMD)
    const diffDays = Math.round((tripDateObj - refDateObj) / (1000 * 60 * 60 * 24))

    if (diffDays === 1) return 'Ngày mai'
    if (diffDays === -1) return 'Hôm qua'
    return ''
  } catch {
    return ''
  }
}

export function serializeTripFilters({ view = 'TODAY', status, date, page = 0, size = 20, sort } = {}) {
  const params = {}

  if (view && ['TODAY', 'UPCOMING', 'HISTORY', 'ACTIVE'].includes(view.toUpperCase())) {
    params.view = view.toUpperCase()
  } else {
    params.view = 'TODAY'
  }

  if (status && status !== 'ALL' && typeof status === 'string' && status.trim()) {
    params.status = status.toUpperCase().trim()
  }

  if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    params.date = date
  }

  const pageNum = Number.isInteger(page) && page >= 0 ? page : 0
  params.page = pageNum

  const sizeNum = Number.isInteger(size) && size > 0 && size <= 100 ? size : 20
  params.size = sizeNum

  if (sort && typeof sort === 'string') {
    params.sort = sort
  }

  return params
}
