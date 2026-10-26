import { useMemo, useState, useRef, useEffect } from 'react'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatSlotPrice, planLabel, slotDurationLabel } from '../../utils/planLabels'
import { parseVietnamDateTime, vietnamTodayYmd } from '../../utils/bookingDateTime'
import { vehicleService } from '../../services/vehicleService'
import { availabilityWindow, overlapsOccupied } from '../../utils/bookingAvailability'

export const ChoosePlanStep = ({
  vehicleId,
  plans,
  selectedPlanId,
  onSelectPlan,
  slots,
  setSlots,
  startDate,
  setStartDate,
  startTime,
  setStartTime,
  formattedStart,
  projectedEnd,
  spanLabel,
  fieldErrors,
  setFieldErrors,
  totalAmount,
  depositAmount,
  depositRatio,
  onNext,
}) => {
  const selected = plans.find((plan) => plan.id === selectedPlanId) ?? plans[0]
  const selectedCopy = planLabel(selected?.planType)
  const yearly = selected?.planType === 'YEARLY'
  const today = useMemo(() => vietnamTodayYmd(), [])

  const [occupiedSlots, setOccupiedSlots] = useState([])
  const [loadingSlots, setLoadingSlots] = useState(false)
  const [availabilityError, setAvailabilityError] = useState('')
  const [availabilityRetry, setAvailabilityRetry] = useState(0)

  const rentalDurationMs = useMemo(() => {
    if (!selected) return 0
    return selected.slotLengthMinutes * slots * 60 * 1000
  }, [selected, slots])

  useEffect(() => {
    if (!startDate || !vehicleId) return undefined
    let cancelled = false
    ;(async () => {
      setLoadingSlots(true)
      setAvailabilityError('')
      try {
        const latestStart = parseVietnamDateTime(startDate, '23:00')
        const dayStart = parseVietnamDateTime(startDate, '00:00')
        const { from, to } = availabilityWindow(
          dayStart,
          new Date(latestStart.getTime() + rentalDurationMs),
        )
        const resp = await vehicleService.getAvailability(vehicleId, from, to)
        if (!cancelled) setOccupiedSlots(resp.occupiedSlots ?? [])
      } catch (err) {
        console.error('[Availability] error', err)
        if (!cancelled) {
          setOccupiedSlots([])
          setAvailabilityError('Chưa kiểm tra được lịch xe. Vui lòng thử lại trước khi tiếp tục.')
        }
      }
      finally { if (!cancelled) setLoadingSlots(false) }
    })()
    return () => { cancelled = true }
  }, [startDate, vehicleId, rentalDurationMs, availabilityRetry])

  const timeSlots = useMemo(() => {
    const all = []
    for (let h = 0; h < 24; h++) {
      const value = String(h).padStart(2, '0') + ':00'
      const label = h === 0 ? '12:00 SA' : h < 12 ? `${h}:00 SA` : h === 12 ? '12:00 PM' : `${h - 12}:00 PM`
      let occupied = false
      if (startDate && occupiedSlots.length > 0 && rentalDurationMs > 0) {
        const rentalStart = parseVietnamDateTime(startDate, value)
        const rentalEnd = new Date(rentalStart.getTime() + rentalDurationMs)
        occupied = occupiedSlots.some((os) => {
          const osStart = new Date(os.startAt)
          const osEnd = new Date(os.endAt)
          return rentalStart < osEnd && rentalEnd > osStart
        })
      }
      all.push({ value, label, occupied })
    }
    let filtered = all
    if (startDate === today) {
      const now = new Date()
      const vn = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Ho_Chi_Minh' }))
      let minHour = vn.getHours()
      if (vn.getMinutes() > 0) minHour += 1
      filtered = filtered.filter((s) => {
        const h = parseInt(s.value.split(':')[0], 10)
        return h >= minHour
      })
    }
    return filtered
  }, [startDate, today, occupiedSlots, rentalDurationMs])

  const [timeOpen, setTimeOpen] = useState(false)
  const timeRef = useRef(null)
  const selectedRange = useMemo(() => {
    if (!startDate || !startTime || !rentalDurationMs) return null
    const start = parseVietnamDateTime(startDate, startTime)
    return { start, end: new Date(start.getTime() + rentalDurationMs) }
  }, [rentalDurationMs, startDate, startTime])
  const selectedRangeOccupied = selectedRange
    ? overlapsOccupied(selectedRange.start, selectedRange.end, occupiedSlots)
    : false

  const handleContinue = () => {
    if (loadingSlots) {
      setAvailabilityError('Đang kiểm tra lịch xe, vui lòng chờ trong giây lát.')
      return
    }
    if (availabilityError) return
    if (selectedRangeOccupied) {
      setFieldErrors((current) => ({ ...current, startTime: 'Khung giờ này vừa có lịch. Vui lòng chọn giờ khác.' }))
      return
    }
    onNext()
  }

  useEffect(() => {
    function handleClickOutside(e) {
      if (timeRef.current && !timeRef.current.contains(e.target)) setTimeOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const selectedTimeLabel = useMemo(() => {
    if (!startTime) return ''
    const h = parseInt(startTime.split(':')[0], 10)
    if (h === 0) return '12:00 SA'
    if (h < 12) return `${h}:00 SA`
    if (h === 12) return '12:00 PM'
    return `${h - 12}:00 PM`
  }, [startTime])

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-bold tracking-tight text-[#14221A] sm:text-2xl">
          1. Chọn gói dịch vụ & Thời gian thuê
        </h1>
        <p className="text-sm text-[#14221A]/70">
          Chọn gói định giá phù hợp. Hệ thống chỉ cho phép đặt các slot liên tiếp nhau.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {plans.map((plan) => {
          const copy = planLabel(plan.planType)
          const active = plan.id === selected?.id
          const isYearly = plan.planType === 'YEARLY'
          return (
            <button
              key={plan.id}
              type="button"
              onClick={() => onSelectPlan(plan.id)}
              className={`relative flex h-full flex-col rounded-2xl border-2 p-4 text-left transition-all select-none ${
                active
                  ? 'border-brand-600 bg-brand-50/60 shadow-sm'
                  : 'border-transparent bg-white shadow-[0_1px_3px_rgba(20,34,26,0.08)] hover:border-[#E6EBE8] hover:shadow-md'
              }`}
            >
              {/* Header: label + radio */}
              <div className="flex items-center justify-between gap-2">
                <span className={`text-[11px] font-bold uppercase tracking-wider ${active ? 'text-brand-700' : 'text-[#14221A]/45'}`}>
                  {copy.title}
                </span>
                <span className={`flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border-2 transition-colors ${
                  active ? 'border-brand-600 bg-brand-600' : 'border-[#CBD5E1]'
                }`}>
                  {active ? <span className="h-1.5 w-1.5 rounded-full bg-white" /> : null}
                </span>
              </div>

              {/* Badge (yearly only) */}
              {isYearly ? (
                <span className="mt-2 inline-flex self-start rounded-full border border-[#E6EBE8] bg-[#F7F8F6] px-2 py-0.5 text-[10px] font-semibold text-[#14221A]/70">
                  Hợp đồng năm
                </span>
              ) : null}

              {/* Price — responsive size to prevent wrapping */}
              <p className={`mt-2.5 font-bold text-[#14221A] ${
                String(plan.slotPrice).length > 8 ? 'text-base' : 'text-lg'
              }`}>
                {formatSlotPrice(plan.slotPrice, plan.slotLengthMinutes)}
              </p>

              {/* Slot description */}
              <p className="mt-1 text-[11px] text-[#14221A]/50">
                1 slot = {slotDurationLabel(plan.slotLengthMinutes) || copy.unit}.
              </p>

              {/* Footer — pushed to bottom */}
              <div className={`mt-auto border-t pt-2.5 text-[11px] font-medium ${
                active ? 'border-brand-200 text-brand-700' : 'border-[#E6EBE8] text-[#14221A]/50'
              }`} style={{ marginTop: 'auto', paddingTop: '0.625rem' }}>
                {copy.footer}
              </div>
            </button>
          )
        })}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="flex flex-col justify-between rounded-[16px] border border-[#E6EBE8] bg-white p-5 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <div>
            <label className="block text-sm font-bold text-[#14221A]">Số lượng slot liên tiếp</label>
            <p className="mt-0.5 text-xs text-[#14221A]/60">Hệ thống chỉ đặt các slot liên tiếp nhau, không tách rời</p>
          </div>
          <div className="mt-4 flex items-center justify-between rounded-[12px] border border-[#E6EBE8] bg-gray-50 p-2.5">
            <span className="px-2 text-sm font-medium text-[#14221A]">
              {spanLabel ? `${spanLabel} (${slots} slot liên tiếp)` : `${selectedCopy.unit} thuê (${slots} slot liên tiếp)`}
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setSlots(Math.max(1, slots - 1))}
                disabled={slots <= 1}
                className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-[12px] border border-[#E6EBE8] bg-white text-lg font-bold disabled:opacity-40"
                type="button"
              >
                −
              </button>
              <span className="w-8 text-center text-base font-bold text-[#14221A]">{slots}</span>
              <button
                onClick={() => setSlots(slots + 1)}
                className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-[12px] border border-[#E6EBE8] bg-white text-lg font-bold"
                type="button"
              >
                +
              </button>
            </div>
          </div>
        </div>

        <div className="rounded-[14px] border border-[#E6EBE8] bg-white p-4 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <label className="block text-sm font-bold text-[#14221A]">Thời điểm bắt đầu</label>
          <p className="mt-0.5 text-xs text-[#14221A]/60">
            {yearly ? 'Chọn ngày và giờ nhận xe theo hợp đồng' : 'Chọn ngày và giờ chính xác để tài xế phục vụ đúng hẹn'}
          </p>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div>
              <span className="mb-1 block text-xs font-medium text-[#14221A]/60">Ngày nhận xe</span>
              <input
                type="date"
                value={startDate}
                min={today}
                onChange={(event) => {
                  const val = event.target.value
                  setStartDate(val)
                  setFieldErrors((prev) => { const next = { ...prev }; delete next.startDate; return next })
                  if (val === today && startTime) {
                    const now = new Date()
                    const vn = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Ho_Chi_Minh' }))
                    let minHour = vn.getHours()
                    if (vn.getMinutes() > 0) minHour += 1
                    const selectedHour = parseInt(startTime.split(':')[0], 10)
                    if (selectedHour < minHour) {
                      setStartTime('')
                    }
                  }
                }}
                className={`w-full rounded-[12px] border bg-white px-3 py-2 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${fieldErrors?.startDate ? 'border-[#B42318]' : 'border-[#E6EBE8]'}`}
              />
              {fieldErrors?.startDate && <p className="mt-1 text-xs text-[#B42318]">{fieldErrors.startDate}</p>}
            </div>
            <div ref={timeRef} className="relative">
              <span className="mb-1 block text-xs font-medium text-[#14221A]/60">Giờ nhận xe</span>
              <button
                type="button"
                onClick={() => setTimeOpen(!timeOpen)}
                className={`flex w-full items-center justify-between rounded-[12px] border bg-white px-3 py-2 text-left text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${fieldErrors?.startTime ? 'border-[#B42318]' : 'border-[#E6EBE8]'}`}
              >
                <span className={startTime ? 'text-[#14221A]' : 'text-gray-400'}>{selectedTimeLabel || 'Chọn giờ'}</span>
                <svg className="h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              </button>
              {timeOpen && (
                <div className="absolute z-20 mt-1 max-h-48 w-full overflow-y-auto rounded-[12px] border border-[#E6EBE8] bg-white shadow-lg">
                  {loadingSlots ? (
                    <div className="px-3 py-2 text-xs text-gray-400">Đang tải lịch...</div>
                  ) : timeSlots.length === 0 ? (
                    <div className="px-3 py-2 text-xs text-gray-400">Không còn khung giờ khả dụng hôm nay</div>
                  ) : timeSlots.map((slot) => (
                    <button
                      key={slot.value}
                      type="button"
                      disabled={slot.occupied}
                      onClick={() => {
                        if (slot.occupied) return
                        setStartTime(slot.value)
                        setTimeOpen(false)
                        setFieldErrors((prev) => { const next = { ...prev }; delete next.startTime; return next })
                      }}
                      className={`w-full px-3 py-2 text-left text-sm transition-colors ${
                        slot.occupied
                          ? 'cursor-not-allowed bg-gray-50 text-gray-300 line-through'
                          : startTime === slot.value
                            ? 'cursor-pointer bg-[#E8F6EE] font-semibold text-[#15803D]'
                            : 'cursor-pointer text-[#14221A] hover:bg-[#E8F6EE]'
                      }`}
                    >
                      {slot.label}{slot.occupied ? ' — Đã có lịch' : ''}
                    </button>
                  ))}
                </div>
              )}
              {fieldErrors?.startTime && <p className="mt-1 text-xs text-[#B42318]">{fieldErrors.startTime}</p>}
            </div>
          </div>
        </div>
      </div>

      <div className="flex w-full flex-col items-start justify-between gap-3 rounded-[12px] bg-[#E8F6EE] p-4 sm:flex-row sm:items-center">
        <div className="text-sm font-medium text-[#14221A]">
          <p>Nhận: <strong>{formattedStart || '—'}</strong></p>
          <p>Trả dự kiến: <strong>{projectedEnd || '—'}</strong></p>
        </div>
        {startDate && startTime ? (
          selectedRangeOccupied
            ? <div className="flex-shrink-0 rounded-full bg-[#B42318] px-3.5 py-1 text-xs font-semibold text-white">Khung giờ này đã có lịch</div>
            : <div className="flex-shrink-0 rounded-full bg-[#15803D] px-3.5 py-1 text-xs font-semibold text-white">Xe còn chỗ trong khung giờ này</div>
        ) : (
          <div className="flex-shrink-0 rounded-full bg-gray-100 px-3.5 py-1 text-xs font-semibold text-gray-500">Chọn ngày và giờ để kiểm tra</div>
        )}
      </div>
      {availabilityError ? (
        <div className="flex flex-col gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 sm:flex-row sm:items-center sm:justify-between">
          <p>{availabilityError}</p>
          <button
            type="button"
            onClick={() => setAvailabilityRetry((value) => value + 1)}
            className="shrink-0 rounded-lg border border-amber-300 bg-white px-3 py-1.5 font-semibold text-amber-900 hover:bg-amber-100"
          >
            Kiểm tra lại lịch
          </button>
        </div>
      ) : null}

      {yearly ? (
        <div className="rounded-xl border border-[#BFDBFE] bg-[#EFF4FF] px-4 py-3 text-sm text-[#14221A]">
          Hợp đồng năm · quy trình riêng. Bước tiếp theo chuyển sang thuê năm, không đi tiếp form thuê có tài xế.
        </div>
      ) : null}

      {selected && slots > 0 && (
        <div className="w-full rounded-[14px] border border-[#E6EBE8] bg-white p-4 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <h3 className="mb-3 text-sm font-bold text-[#14221A]">Chi phí ước tính</h3>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-[#14221A]/70">
                {formatSlotPrice(selected.slotPrice, selected.slotLengthMinutes)} × {slots}
              </span>
              <span className="font-semibold text-[#14221A]">{formatCurrency(totalAmount)}</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-[#14221A]/70">
                {yearly
                  ? 'Trả trước (theo hợp đồng)'
                  : `Tiền cọc (${Math.round(depositRatio * 100)}%)`}
              </span>
              <span className="font-semibold text-[#15803D]">{formatCurrency(depositAmount)}</span>
            </div>
            <div className="mt-1 flex items-center justify-between border-t border-[#E6EBE8] pt-2">
              <span className="text-sm font-bold text-[#14221A]">Tổng tiền thuê</span>
              <span className="text-lg font-bold text-[#15803D]">{formatCurrency(totalAmount)}</span>
            </div>
          </div>
        </div>
      )}

      <div className="flex justify-end pt-2 pb-4">
        <button
          onClick={handleContinue}
          disabled={!selected || loadingSlots || Boolean(availabilityError) || selectedRangeOccupied}
          className="inline-flex cursor-pointer items-center gap-2 rounded-[12px] bg-[#15803D] px-6 py-3 font-medium text-white hover:bg-emerald-800 focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 active:scale-[0.98] disabled:opacity-40"
          type="button"
        >
          {yearly ? 'Tiếp tục sang thuê năm' : 'Tiếp tục: Điểm đón'}
        </button>
      </div>
    </div>
  )
}
