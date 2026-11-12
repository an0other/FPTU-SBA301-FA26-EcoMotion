import { useState } from 'react'
import { yearlyFirstAvailablePickup, yearlyPickupAvailable } from '../../utils/yearlyRental'

const localValue = time => new Date(time + 7 * 3600000).toISOString().slice(0,16)
export default function YearlyPickupCalendar({ value, minimum, maximum, slots, loading, error, onChange, onRefresh }) {
  const selectedDay=value.slice(0,10), selectedTime=value.slice(11,16)
  const [month,setMonth]=useState(()=>selectedDay.slice(0,7))
  const first=new Date(`${month}-01T00:00:00Z`)
  const offset=(first.getUTCDay()+6)%7
  const days=Array.from({length:42},(_,index)=>new Date(Date.UTC(first.getUTCFullYear(),first.getUTCMonth(),index-offset+1)).toISOString().slice(0,10))
  const minMonth=minimum.slice(0,7),maxMonth=maximum?.slice(0,7)
  function changeMonth(delta) {setMonth(new Date(Date.UTC(first.getUTCFullYear(),first.getUTCMonth()+delta,1)).toISOString().slice(0,7))}
  const ready=!loading && !error && !!maximum && Array.isArray(slots)
  const choices=new Set(Array.from({length:96},(_,index)=>`${String(Math.floor(index/4)).padStart(2,'0')}:${String(index%4*15).padStart(2,'0')}`))
  choices.add(selectedTime)
  const earliest=ready?yearlyFirstAvailablePickup(selectedDay,`${minimum}:00+07:00`,maximum,slots):null
  if(earliest!==null)choices.add(localValue(earliest).slice(11,16))
  const maxTime=maximum?new Date(maximum).getTime():NaN,minTime=new Date(`${minimum}:00+07:00`).getTime()
  function allowed(time) {const date=new Date(`${selectedDay}T${time}:00+07:00`);return ready && date.getTime()>=minTime && date.getTime()<=maxTime && yearlyPickupAvailable(date,slots)}
  return <div className="space-y-3 rounded-xl border border-slate-200 p-4" aria-label="Lịch nhận xe tự lái">
    <div className="flex items-center justify-between gap-3"><button type="button" aria-label="Tháng trước" disabled={month<=minMonth} onClick={()=>changeMonth(-1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">←</button><strong>Tháng {month.slice(5)} / {month.slice(0,4)}</strong><button type="button" aria-label="Tháng sau" disabled={!maxMonth || month>=maxMonth} onClick={()=>changeMonth(1)} className="rounded-lg border px-3 py-2 disabled:opacity-40">→</button></div>
    <div className="grid grid-cols-7 gap-1 text-center text-xs">{['T2','T3','T4','T5','T6','T7','CN'].map(day=><span key={day} className="p-2 font-semibold">{day}</span>)}{days.map(day=>{
      const firstAvailable=ready && day.slice(0,7)===month?yearlyFirstAvailablePickup(day,`${minimum}:00+07:00`,maximum,slots):null
      const disabled=firstAvailable===null
      return <button key={day} type="button" disabled={disabled} aria-pressed={day===selectedDay} title={disabled?'Không có thời gian nhận hợp lệ cho kỳ thuê 12 tháng':'Có thời gian nhận phù hợp'} aria-label={`Nhận xe ngày ${day}${disabled?' — không khả dụng':''}`}
        className={`min-h-10 rounded-lg border p-2 ${day===selectedDay?'border-emerald-600 bg-emerald-50 font-bold text-emerald-900':'border-slate-200'} disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400`}
        onClick={()=>{const preferred=new Date(`${day}T${selectedTime}:00+07:00`).getTime();onChange(localValue(preferred>=minTime && preferred<=maxTime && yearlyPickupAvailable(preferred,slots)?preferred:firstAvailable))}}>{Number(day.slice(8))}</button>
    })}</div>
    <label className="block text-sm font-medium">Giờ nhận ngày {selectedDay}<select className="mt-1 w-full rounded-lg border p-2" value={selectedTime} disabled={!ready} onChange={event=>onChange(`${selectedDay}T${event.target.value}`)}>{[...choices].sort().map(time=><option key={time} value={time} disabled={!allowed(time)}>{time}{allowed(time)?'':' — không khả dụng'}</option>)}</select></label>
    <p className="text-xs text-slate-600">Ngày bị mờ không có khoảng trống đủ 12 tháng theo lịch hiện tại, giới hạn đặt trước và thời gian chuẩn bị xe. Lịch được kiểm tra lại khi gửi.</p>
    {loading && <p role="status" className="text-sm text-blue-800">Đang tải lịch xe…</p>}{error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    <button type="button" className="text-sm font-semibold text-emerald-800 underline" onClick={onRefresh}>Cập nhật lịch xe</button>
  </div>
}
