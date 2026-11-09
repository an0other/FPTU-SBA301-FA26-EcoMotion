export const yearlyInputClass = 'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 disabled:bg-slate-100'
export const yearlyButtonClass = 'rounded-lg bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50'
export const yearlyDate = (value) => value ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'medium' }).format(new Date(value.length === 10 ? value + 'T12:00:00+07:00' : value)) : 'Chưa ghi nhận'
export const yearlyToday = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })
export const yearlyUuid = () => globalThis.crypto?.randomUUID?.() || 'yearly-' + Date.now() + '-' + Math.random().toString(36).slice(2)
export const yearlyMoney = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0))

