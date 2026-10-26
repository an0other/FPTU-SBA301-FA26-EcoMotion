export const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh'

export function parseVietnamDateTime(date, time = '00:00') {
  if (!date) return null
  const clock = time && String(time).length >= 4 ? time : '00:00'
  const parsed = new Date(`${date}T${clock}:00+07:00`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

export function formatBookingWhen(value) {
  if (value == null || value === '') return ''
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: VN_TIME_ZONE,
  }).formatToParts(date)
  const get = (type) => parts.find((part) => part.type === type)?.value ?? ''
  return `${get('weekday')}, ${get('day')}/${get('month')}/${get('year')} · ${get('hour')}:${get('minute')}`
}

export function vietnamTodayYmd() {
  const vn = new Date(new Date().toLocaleString('en-US', { timeZone: VN_TIME_ZONE }))
  return `${vn.getFullYear()}-${String(vn.getMonth() + 1).padStart(2, '0')}-${String(vn.getDate()).padStart(2, '0')}`
}
