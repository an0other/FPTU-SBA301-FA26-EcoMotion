export const yearlyInputClass = 'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 disabled:bg-slate-100'
export const yearlyButtonClass = 'rounded-lg bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50'
export const yearlyDate = (value) => value ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'medium' }).format(new Date(value.length === 10 ? value + 'T12:00:00+07:00' : value)) : 'Chưa ghi nhận'
export const yearlyToday = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })
export const yearlyUuid = () => globalThis.crypto?.randomUUID?.() || 'yearly-' + Date.now() + '-' + Math.random().toString(36).slice(2)
export const yearlyMoney = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0))


export function yearlyReservationEndForStart(value) {
  const timestamp = new Date(value).getTime()
  if (!Number.isFinite(timestamp)) return null
  const local = new Date(timestamp + 7 * 3600000)
  const year = local.getUTCFullYear() + 1, month = local.getUTCMonth()
  const day = Math.min(local.getUTCDate(), new Date(Date.UTC(year,month+1,0)).getUTCDate())
  return Date.UTC(year,month,day+1) - 7 * 3600000
}
export function yearlyPickupAvailable(value, slots) {
  const from=new Date(value).getTime(),to=yearlyReservationEndForStart(value)
  if (!Number.isFinite(from) || to===null || !Array.isArray(slots)) return false
  return slots.every(slot=>{
    const begin=new Date(slot.startAt).getTime(),end=new Date(slot.endAt).getTime()
    return Number.isFinite(begin) && Number.isFinite(end) && begin<end && (end<=from || begin>=to)
  })
}
export function yearlyFirstAvailablePickup(day,minimum,maximum,slots) {
  const start=new Date(`${day}T00:00:00+07:00`).getTime(),min=new Date(minimum).getTime(),max=new Date(maximum).getTime()
  if (![start,min,max].every(Number.isFinite) || !Array.isArray(slots)) return null
  const earliest=Math.ceil(Math.max(start,min)/60000)*60000,latest=Math.min(start+86400000-1,max)
  const candidates=[earliest,...slots.map(slot=>Math.ceil(new Date(slot.endAt).getTime()/60000)*60000)]
    .filter(value=>Number.isFinite(value) && value>=earliest && value<=latest).sort((a,b)=>a-b)
  return candidates.find(value=>yearlyPickupAvailable(value,slots)) ?? null
}

const stages = { TERMS_DRAFT: 'Chờ chốt điều khoản', TERMS_ACCEPTED: 'Chuẩn bị hợp đồng và bàn giao', ACTIVE: 'Đang thuê', RETURN_REQUESTED: 'Đã yêu cầu trả xe', RETURN_INSPECTED: 'Chờ xác nhận kiểm định', RETURNED: 'Đã nhận lại xe', SETTLEMENT_PROPOSED: 'Chờ đồng ý quyết toán', SETTLEMENT_ACCEPTED: 'Đã chốt quyết toán', REFUND_TRANSFER_RECORDED: 'Chờ xác nhận tiền hoàn', REFUND_RECEIVED: 'Đã nhận tiền hoàn', COMPLETED: 'Hoàn tất' }
export function yearlyRequestState(bookingStatus, stage) {
  const terminal = ['CANCELLED','CANCELLATION_REVIEW','REJECTED','EXPIRED'].includes(bookingStatus)
  const pending = bookingStatus === 'PENDING'
  const label = terminal ? ({CANCELLED:'Đơn thuê đã hủy',CANCELLATION_REVIEW:'Đơn đã hủy · đang đối soát',REJECTED:'Yêu cầu đã bị từ chối',EXPIRED:'Yêu cầu đã hết hạn'})[bookingStatus]
    : pending ? 'Chờ Provider chấp nhận yêu cầu' : stages[stage] || stage || 'Đang tải trạng thái'
  return {terminal,pending,label}
}

export function parseYearlyVnd(value) {
  const raw = String(value ?? '').trim()
  if (!/^\d+$/.test(raw) && !/^\d{1,3}(?:\.\d{3})+$/.test(raw)
      && !/^\d{1,3}(?:,\d{3})+$/.test(raw) && !/^\d{1,3}(?: \d{3})+$/.test(raw)) return null
  const amount = Number(raw.replace(/[., ]/g, ''))
  return Number.isSafeInteger(amount) && amount <= 999999999999 ? amount : null
}
