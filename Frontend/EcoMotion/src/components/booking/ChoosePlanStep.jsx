import { useMemo, useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'

export const ChoosePlanStep = ({
  vehicleId,
  vehicleName,
  plateNumber,
  vehicleType,
  vehicleImage,
  plans,
  selectedPlanId,
  onSelectPlan,
  slots,
  setSlots,
  startDate,
  setStartDate,
  startTime,
  setStartTime,
  projectedEnd,
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

  // Min date = today (Vietnam timezone)
  const today = useMemo(() => {
    const now = new Date()
    const vn = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Ho_Chi_Minh' }))
    return vn.getFullYear() + '-' + String(vn.getMonth() + 1).padStart(2, '0') + '-' + String(vn.getDate()).padStart(2, '0')
  }, [])

  // Generate available time slots (hourly), filtering out past hours if today
  const timeSlots = useMemo(() => {
    const all = []
    for (let h = 0; h < 24; h++) {
      const value = String(h).padStart(2, '0') + ':00'
      const label = h === 0 ? '12:00 SA' : h < 12 ? `${h}:00 SA` : h === 12 ? '12:00 PM' : `${h - 12}:00 PM`
      all.push({ value, label })
    }
    if (startDate === today) {
      const now = new Date()
      const vn = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Ho_Chi_Minh' }))
      let minHour = vn.getHours()
      if (vn.getMinutes() > 0) minHour += 1
      return all.filter((s) => {
        const h = parseInt(s.value.split(':')[0], 10)
        return h >= minHour
      })
    }
    return all
  }, [startDate, today])

  const [timeOpen, setTimeOpen] = useState(false)
  const timeRef = useRef(null)

  useEffect(() => {
    function handleClickOutside(e) {
      if (timeRef.current && !timeRef.current.contains(e.target)) setTimeOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Format selected time for display
  const selectedTimeLabel = useMemo(() => {
    if (!startTime) return ''
    const h = parseInt(startTime.split(':')[0], 10)
    if (h === 0) return '12:00 SA'
    if (h < 12) return `${h}:00 SA`
    if (h === 12) return '12:00 PM'
    return `${h - 12}:00 PM`
  }, [startTime])

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 space-y-4">
      <nav className="flex items-center gap-2 text-xs sm:text-sm text-[#64748B]">
        <Link to="/" className="hover:text-[#15803D] transition-colors">Trang chủ</Link>
        <span className="text-xs">&gt;</span>
        <Link to={`/vehicles/${vehicleId}`} className="hover:text-[#15803D] transition-colors">{vehicleName}</Link>
        <span className="text-xs">&gt;</span>
        <span className="font-medium text-[#14221A]">Gửi yêu cầu đặt xe</span>
      </nav>

      <div className="w-full bg-white flex flex-col md:flex-row items-center justify-between p-4 rounded-[14px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
        <div className="flex items-center gap-5 w-full md:w-auto">
          <div className="w-24 h-16 sm:w-28 sm:h-20 flex-shrink-0 rounded-[12px] overflow-hidden bg-[#F7F8F6]">
            {vehicleImage ? (
              <img className="w-full h-full object-cover" alt={vehicleName} src={vehicleImage} />
            ) : null}
          </div>
          <div className="flex flex-col">
            <div className="text-lg font-bold tracking-tight text-[#14221A]">{vehicleName}</div>
            <div className="flex items-center gap-2 mt-1 flex-wrap">
              <span className="inline-flex items-center px-2.5 py-0.5 text-xs font-semibold uppercase rounded-full bg-[#F7F8F6] text-[#14221A] border border-[#E6EBE8]">
                Biển số: {plateNumber}
              </span>
              <span className="text-xs text-gray-500 font-normal">{vehicleType}</span>
            </div>
          </div>
        </div>
        <div className="mt-4 md:mt-0 flex flex-col items-start md:items-end w-full md:w-auto pt-3 md:pt-0 border-t md:border-t-0 border-[#E6EBE8]">
          <span className="text-xs text-gray-500">Đơn giá đã chọn</span>
          <div className="flex items-baseline gap-1 mt-0.5">
            <span className="text-2xl font-bold text-[#15803D]">
              {formatCurrency(selected?.slotPrice)}
            </span>
            <span className="text-xs text-gray-600 font-medium">/ slot</span>
          </div>
        </div>
      </div>

      <div className="w-full py-1">
        <div className="flex items-center justify-between max-w-2xl mx-auto relative">
          <div className="absolute left-0 right-0 top-1/2 -translate-y-1/2 h-0.5 bg-[#E6EBE8] z-0"></div>
          <StepDot active label="Chọn gói & Thời gian" number="1" />
          <StepDot label="Điểm đón & Khách" number="2" />
          <StepDot label="Xác nhận & Gửi" number="3" />
        </div>
      </div>

      <div className="space-y-1">
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-[#14221A]">
          1. Chọn gói dịch vụ & Thời gian thuê
        </h1>
        <p className="text-sm text-gray-600">
          Chọn gói định giá phù hợp. Hệ thống chỉ cho phép đặt các slot liên tiếp nhau.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {plans.map((plan) => {
          const copy = planLabel(plan.planType)
          const active = plan.id === selected?.id
          return (
            <button
              key={plan.id}
              type="button"
              onClick={() => onSelectPlan(plan.id)}
              className={`relative flex flex-col justify-between p-4 text-left cursor-pointer transition-all duration-180 rounded-[16px] border select-none ${
                active
                  ? 'bg-[#E8F6EE] border-2 border-[#15803D] shadow-[0_8px_24px_rgba(20,34,26,0.06)]'
                  : 'bg-white border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)] hover:-translate-y-0.5'
              }`}
            >
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className={`text-xs font-bold uppercase tracking-wider ${active ? 'text-[#15803D]' : 'text-[#64748B]'}`}>
                    {copy.title}
                  </span>
                  <div className={`w-5 h-5 rounded-full flex items-center justify-center ${active ? 'bg-[#15803D] p-1' : 'border border-[#CBD5E1]'}`}>
                    {active ? <div className="w-2 h-2 rounded-full bg-white"></div> : null}
                  </div>
                </div>
                <div className="flex items-baseline gap-1">
                  <span className="text-xl font-bold text-[#14221A]">{formatCurrency(plan.slotPrice)}</span>
                  <span className="text-xs text-gray-500 font-medium">/ slot</span>
                </div>
                <p className="text-xs text-gray-600 mt-2.5 leading-relaxed">{copy.detail}</p>
              </div>
              <div className={`mt-4 pt-3 border-t text-xs font-medium ${active ? 'border-[#15803D]/20 text-[#15803D]' : 'border-[#E6EBE8] text-gray-500'}`}>
                {copy.footer}
              </div>
            </button>
          )
        })}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white p-5 flex flex-col justify-between rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <div>
            <label className="block text-sm font-bold text-[#14221A]">Số lượng slot liên tiếp</label>
            <p className="text-xs text-gray-500 mt-0.5">Hệ thống chỉ đặt các slot liên tiếp nhau, không tách rời</p>
          </div>
          <div className="mt-4 flex items-center justify-between p-2.5 bg-gray-50 rounded-[12px] border border-[#E6EBE8]">
            <span className="text-sm font-medium px-2 text-[#14221A]">
              {selectedCopy.unit} thuê ({slots} slot liên tiếp)
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setSlots(Math.max(1, slots - 1))}
                disabled={slots <= 1}
                className="w-9 h-9 flex items-center justify-center font-bold text-lg rounded-[12px] border border-[#E6EBE8] bg-white disabled:opacity-40 cursor-pointer"
                type="button"
              >
                −
              </button>
              <span className="w-8 text-center font-bold text-base text-[#14221A]">{slots}</span>
              <button
                onClick={() => setSlots(slots + 1)}
                className="w-9 h-9 flex items-center justify-center font-bold text-lg rounded-[12px] border border-[#E6EBE8] bg-white cursor-pointer"
                type="button"
              >
                +
              </button>
            </div>
          </div>
        </div>

        <div className="bg-white p-4 rounded-[14px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <label className="block text-sm font-bold text-[#14221A]">Thời điểm bắt đầu</label>
          <p className="text-xs text-gray-500 mt-0.5">
            {yearly ? 'Chọn ngày và giờ nhận xe theo hợp đồng' : 'Chọn ngày và giờ chính xác để tài xế phục vụ đúng hẹn'}
          </p>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div>
              <span className="block text-xs text-gray-500 mb-1 font-medium">Ngày nhận xe</span>
              <input
                type="date"
                value={startDate}
                min={today}
                onChange={(event) => {
                  const val = event.target.value
                  setStartDate(val)
                  setFieldErrors((prev) => { const next = { ...prev }; delete next.startDate; return next })
                  // If switching to today, clear time if it's now in the past
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
                className={`w-full text-sm font-medium px-3 py-2 bg-white rounded-[12px] border outline-none focus:ring-2 focus:ring-[#15803D] ${fieldErrors?.startDate ? 'border-[#B42318]' : 'border-[#E6EBE8]'}`}
              />
              {fieldErrors?.startDate && <p className="text-xs text-[#B42318] mt-1">{fieldErrors.startDate}</p>}
            </div>
            <div ref={timeRef} className="relative">
              <span className="block text-xs text-gray-500 mb-1 font-medium">Giờ nhận xe</span>
              <button
                type="button"
                onClick={() => setTimeOpen(!timeOpen)}
                className={`w-full text-left text-sm font-medium px-3 py-2 bg-white rounded-[12px] border outline-none focus:ring-2 focus:ring-[#15803D] flex items-center justify-between ${fieldErrors?.startTime ? 'border-[#B42318]' : 'border-[#E6EBE8]'}`}
              >
                <span className={startTime ? 'text-[#14221A]' : 'text-gray-400'}>{selectedTimeLabel || 'Chọn giờ'}</span>
                <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              </button>
              {timeOpen && (
                <div className="absolute z-20 mt-1 w-full max-h-48 overflow-y-auto rounded-[12px] border border-[#E6EBE8] bg-white shadow-lg">
                  {timeSlots.length === 0 ? (
                    <div className="px-3 py-2 text-xs text-gray-400">Không còn khung giờ khả dụng hôm nay</div>
                  ) : timeSlots.map((slot) => (
                    <button
                      key={slot.value}
                      type="button"
                      onClick={() => {
                        setStartTime(slot.value)
                        setTimeOpen(false)
                        setFieldErrors((prev) => { const next = { ...prev }; delete next.startTime; return next })
                      }}
                      className={`w-full text-left px-3 py-2 text-sm hover:bg-[#E8F6EE] transition-colors cursor-pointer ${startTime === slot.value ? 'bg-[#E8F6EE] font-semibold text-[#15803D]' : 'text-[#14221A]'}`}
                    >
                      {slot.label}
                    </button>
                  ))}
                </div>
              )}
              {fieldErrors?.startTime && <p className="text-xs text-[#B42318] mt-1">{fieldErrors.startTime}</p>}
            </div>
          </div>
        </div>
      </div>

      <div className="w-full p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 rounded-[12px] bg-[#E8F6EE]">
        <span className="text-sm font-medium text-[#14221A]">
          Thời điểm kết thúc dự kiến: <strong>{projectedEnd}</strong>
        </span>
        <div className="flex-shrink-0 px-3.5 py-1 text-xs font-semibold text-white rounded-full bg-[#15803D]">
          Xe còn chỗ trong khung giờ này
        </div>
      </div>

      {selected && slots > 0 && (
        <div className="w-full rounded-[14px] border border-[#E6EBE8] bg-white p-4 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <h3 className="text-sm font-bold text-[#14221A] mb-3">Chi phí ước tính</h3>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-600">
                {formatCurrency(selected.slotPrice)} × {slots} slot
              </span>
              <span className="font-semibold text-[#14221A]">{formatCurrency(totalAmount)}</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-600">
                {yearly
                  ? 'Trả trước (theo hợp đồng)'
                  : `Tiền cọc (${Math.round(depositRatio * 100)}%)`}
              </span>
              <span className="font-semibold text-[#15803D]">{formatCurrency(depositAmount)}</span>
            </div>
            <div className="border-t border-[#E6EBE8] pt-2 mt-1 flex items-center justify-between">
              <span className="text-sm font-bold text-[#14221A]">Tổng tiền thuê</span>
              <span className="text-lg font-bold text-[#15803D]">{formatCurrency(totalAmount)}</span>
            </div>
          </div>
        </div>
      )}

      <div className="flex justify-end pt-2 pb-4">
        <button
          onClick={onNext}
          disabled={!selected}
          className="inline-flex items-center gap-2 text-white font-medium px-6 py-3 rounded-[12px] bg-[#15803D] hover:bg-emerald-800 active:scale-[0.98] cursor-pointer disabled:opacity-40"
          type="button"
        >
          Tiếp tục: Điểm đón
        </button>
      </div>
    </div>
  )
}

function StepDot({ number, label, active = false }) {
  return (
    <div className="relative z-10 flex flex-col items-center gap-2">
      <div className={`w-9 h-9 flex items-center justify-center font-semibold text-sm rounded-full ${active ? 'bg-[#15803D] text-white' : 'bg-white border border-[#E6EBE8] text-[#64748B]'}`}>
        {number}
      </div>
      <span className={`text-xs sm:text-sm whitespace-nowrap ${active ? 'font-semibold text-[#15803D]' : 'font-medium text-gray-500'}`}>
        {label}
      </span>
    </div>
  )
}
