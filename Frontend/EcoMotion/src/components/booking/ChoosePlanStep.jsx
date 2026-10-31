import { useId } from 'react'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatSlotPrice, slotDurationLabel } from '../../utils/planLabels'
import { formatBookingWhen, parseVietnamDateTime } from '../../utils/bookingDateTime'
import { rentalDurationLabel } from '../../utils/rentalPricing'

export function ChoosePlanStep({ plans, start, end, onStart, onEnd, quote, pending, error,
  selected, requestedEnd, onSelect, onWindow, onNext, onYearly, timeError }) {
  const lowestPrice = Math.min(...(quote?.options ?? []).map(option => Number(option.rentalAmount)))
  const [startDate, startHour] = start.split('T')
  const pickupTime = parseVietnamDateTime(startDate, startHour)
  const inputClass = 'mt-2 w-full rounded-xl border border-[#D9DFD9] bg-white px-4 py-3 focus-visible:outline-2 focus-visible:outline-brand-700'
  return <div className="space-y-6">
    <div><h1 className="text-2xl font-bold text-[#14221A]">1. Chọn thời gian thuê</h1>
      <p className="mt-2 text-sm text-[#14221A]/75">Chọn giờ tròn, đặt trước ít nhất 3 giờ. Chúng tôi tìm cách ghép gói có giá tốt nhất cho bạn.</p></div>
    <section className="rounded-2xl border border-[#D9DFD9] bg-white p-5">
      <h2 className="font-semibold">Bảng giá tham khảo</h2>
      <ul className="mt-3 flex flex-wrap gap-4 text-sm">{plans.filter(p => p.planType !== 'YEARLY').map(p =>
        <li key={p.id}>{formatSlotPrice(p.slotPrice, p.slotLengthMinutes)}</li>)}</ul>
      {plans.filter(p => p.planType === 'YEARLY').map(p => <button key={p.id} type="button" onClick={() => onYearly(p.id)}
        className="mt-4 text-sm font-semibold text-brand-700 underline focus-visible:outline-2">Xem hợp đồng thuê năm</button>)}
    </section>
    <div className="grid gap-4 sm:grid-cols-2">
      <RentalTimeInput legend="Giờ nhận xe (giờ Việt Nam)" dateLabel="Ngày nhận xe" hourLabel="Giờ nhận xe"
        value={start} onChange={onStart} inputClass={inputClass} />
      <RentalTimeInput legend="Giờ trả mong muốn (giờ Việt Nam)" dateLabel="Ngày trả xe" hourLabel="Giờ trả xe"
        value={end} onChange={onEnd} inputClass={inputClass} invalid={Boolean(timeError)} />
    </div>
    <div aria-live="polite" aria-busy={pending}>
      {pending ? <p role="status">Đang kiểm tra lịch xe và tìm giá tốt nhất…</p> : null}
      {error ? <p id="rental-time-error" role="alert" className="text-red-700">{error}</p> : null}
      {!pending && quote?.reason ? <p className="text-amber-800">{quote.reason}</p> : null}
      {!pending && quote?.occupiedWindows?.length > 0 ? <section className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-4" aria-label="Các khoảng xe bận">
        <h2 className="text-sm font-semibold text-amber-950">Xe không thể đặt trong các khoảng sau</h2>
        <ul className="mt-2 space-y-2 text-sm text-amber-950">{quote.occupiedWindows.map((window, index) =>
          <li key={index}><span className="block">Từ {formatBookingWhen(window.startAt)}</span>
            <span className="block">Đến {formatBookingWhen(window.endAt)}</span></li>)}</ul>
        <p className="mt-3 text-xs text-amber-950">Các khoảng này đã bao gồm thời gian chuẩn bị xe. Vui lòng chọn thời gian khác.</p>
      </section> : null}
      {!pending && quote?.availableWindows?.length > 0 ? <section className="mt-4 space-y-3">
        <h2 className="font-semibold">Các khoảng xe trống</h2>
        <p className="text-sm">Tìm đến {formatBookingWhen(quote.searchTo)}. Chọn một khoảng để lấy báo giá mới.</p>
        {quote.availableWindows.map(w => <button key={w.startAt} type="button" onClick={() => onWindow(w)}
          className="block rounded-xl border border-[#D9DFD9] px-4 py-3 text-left text-sm focus-visible:outline-2">
          {formatBookingWhen(w.startAt)} → {formatBookingWhen(w.endAt)}</button>)}
      </section> : null}
      {!pending && quote && !quote.available && !quote.availableWindows.length ? <p className="mt-3 text-sm">Không tìm thấy khoảng đủ dài trong 30 ngày tiếp theo. Hãy đổi ngày hoặc thời lượng.</p> : null}
    </div>
    {quote?.options?.length > 0 && !pending ? <section className="space-y-3" aria-label="Phương án giá đề xuất">
      <h2 className="font-semibold">Phương án giá đề xuất</h2>
      {quote.options.map(option => <label key={option.plannedEndAt}
        className={`flex cursor-pointer items-start gap-3 rounded-2xl border p-4 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-brand-700 ${selected?.plannedEndAt === option.plannedEndAt ? 'border-brand-700 bg-brand-50/40' : 'border-[#D9DFD9] bg-white hover:border-brand-500'}`}>
        <input type="radio" name="rental-option" checked={selected?.plannedEndAt === option.plannedEndAt}
          onChange={() => onSelect(option)} className="mt-1 accent-brand-700" />
        <span className="block min-w-0 flex-1">
          <span className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="font-semibold text-[#14221A]">Bắt đầu: {formatBookingWhen(pickupTime)}</span>
            {Number(option.rentalAmount) === lowestPrice ? <span className="rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-semibold text-brand-800">Tối ưu</span> : null}
          </span>
          <span className="mt-1 block text-sm text-[#14221A]">Kết thúc: <strong>{formatBookingWhen(option.plannedEndAt)}</strong></span>
          <span className="mt-2 block text-sm text-[#3D4A42]">Tổng thời lượng: <strong>{rentalDurationLabel(option.coveredHours)}</strong> ({option.coveredHours} giờ)</span>
          {requestedEnd && new Date(option.plannedEndAt) > new Date(requestedEnd) ? <span className="mt-1 block text-xs text-brand-800">Thêm {rentalDurationLabel((new Date(option.plannedEndAt) - new Date(requestedEnd)) / 3600000)} sử dụng so với yêu cầu, đã gồm trong giá.</span> : null}
          <span className="mt-3 block border-t border-[#D9DFD9] pt-3 text-sm text-[#3D4A42]">
            {option.items.map(item => <span key={item.planId} className="mt-1 flex flex-wrap justify-between gap-x-3 gap-y-1">
              <span>{item.quantity} gói {slotDurationLabel(item.slotLengthMinutes)}</span>
              <span className="tabular-nums">{item.quantity} × {formatCurrency(item.unitPrice)} = {formatCurrency(Number(item.unitPrice) * item.quantity)}</span>
            </span>)}
            <span className="mt-2 flex flex-wrap items-center justify-between gap-2 font-semibold text-[#14221A]"><span>Tổng tiền thuê</span><span className="text-xl tabular-nums">{formatCurrency(option.rentalAmount)}</span></span>
          </span>
          <span className="mt-3 flex flex-wrap justify-between gap-2 text-sm text-[#3D4A42]"><span>Cọc giữ chỗ</span><strong className="tabular-nums">{formatCurrency(option.depositAmount)}</strong></span>
        </span>
      </label>)}
      <p className="text-sm text-[#3D4A42]">Tổng tiền thuê chưa gồm phí đón và phụ phí phát sinh. Xe được giữ đến giờ trả của phương án bạn chọn.</p>
    </section> : null}
    <p className="text-sm text-[#14221A]/75">Chọn cách thuê phù hợp, kiểm tra giờ trả xe và tổng tiền trước khi tiếp tục.</p>
    <button type="button" disabled={!selected || pending || Boolean(error)} onClick={onNext}
      className="rounded-xl bg-brand-700 px-6 py-3 font-semibold text-white focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50">Tiếp tục chọn điểm đón</button>
  </div>
}

function RentalTimeInput({ legend, dateLabel, hourLabel, value, onChange, inputClass, invalid = false }) {
  const id = useId()
  const date = value.slice(0, 10)
  const hour = value.slice(11, 16)
  return <fieldset className="min-w-0">
    <legend className="text-sm font-semibold">{legend}</legend>
    <div className="grid grid-cols-[minmax(0,1fr)_7rem] gap-3">
      <div className="min-w-0 text-sm"><label htmlFor={id + '-date'} className="sr-only">{dateLabel}</label>
        <input id={id + '-date'} type="date" required value={date} onChange={e => onChange(e.target.value ? e.target.value + 'T' + hour : '')}
          aria-invalid={invalid} aria-describedby={invalid ? 'rental-time-error' : undefined} className={inputClass} /></div>
      <div className="text-sm"><label htmlFor={id + '-hour'} className="sr-only">{hourLabel}</label>
        <select id={id + '-hour'} required disabled={!date} value={hour} onChange={e => onChange(date + 'T' + e.target.value)}
          aria-invalid={invalid} aria-describedby={invalid ? 'rental-time-error' : undefined} className={inputClass}>
          <option value="">Chọn giờ</option>
          {Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0') + ':00').map(time =>
            <option key={time} value={time}>{time}</option>)}
        </select></div>
    </div>
  </fieldset>
}
