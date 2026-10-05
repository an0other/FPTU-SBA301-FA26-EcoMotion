import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'

export const ChoosePlanStep = ({
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
  onNext,
}) => {
  const selected = plans.find((plan) => plan.id === selectedPlanId) ?? plans[0]
  const selectedCopy = planLabel(selected?.planType)

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <nav className="flex items-center gap-2 text-xs sm:text-sm text-[#64748B]">
        <span>Trang chủ</span>
        <span className="text-xs">&gt;</span>
        <span>Khám phá xe</span>
        <span className="text-xs">&gt;</span>
        <span>{vehicleName}</span>
        <span className="text-xs">&gt;</span>
        <span className="font-medium text-[#14221A]">Gửi yêu cầu thuê</span>
      </nav>

      <div className="w-full bg-white flex flex-col md:flex-row items-center justify-between p-5 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
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

      <div className="w-full py-2">
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

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {plans.map((plan) => {
          const copy = planLabel(plan.planType)
          const active = plan.id === selected?.id
          return (
            <button
              key={plan.id}
              type="button"
              onClick={() => onSelectPlan(plan.id)}
              className={`relative flex flex-col justify-between p-5 text-left cursor-pointer transition-all duration-180 rounded-[16px] border select-none ${
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

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
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

        <div className="bg-white p-5 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <label className="block text-sm font-bold text-[#14221A]">Thời điểm bắt đầu</label>
          <p className="text-xs text-gray-500 mt-0.5">Chọn ngày và giờ chính xác để tài xế phục vụ đúng hẹn</p>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div>
              <span className="block text-xs text-gray-500 mb-1 font-medium">Ngày nhận xe</span>
              <input
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
                className="w-full text-sm font-medium px-3 py-2 bg-white rounded-[12px] border border-[#E6EBE8] outline-none focus:ring-2 focus:ring-[#15803D]"
              />
            </div>
            <div>
              <span className="block text-xs text-gray-500 mb-1 font-medium">Giờ nhận xe</span>
              <input
                type="time"
                value={startTime}
                onChange={(event) => setStartTime(event.target.value)}
                className="w-full text-sm font-medium px-3 py-2 bg-white rounded-[12px] border border-[#E6EBE8] outline-none focus:ring-2 focus:ring-[#15803D]"
              />
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

      <div className="flex justify-end pt-2 pb-6">
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
