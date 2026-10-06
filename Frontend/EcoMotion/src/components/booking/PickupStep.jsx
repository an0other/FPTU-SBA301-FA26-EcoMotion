import { useState } from 'react';


export const PickupStep = ({
  vehicleName,
  plateNumber,
  vehicleImage,
  slots,
  planSummary,
  priceLabel,
  pickupAddress,
  setPickupAddress,
  passengerCount,
  setPassengerCount,
  maxPassengers,
  onBack,
  onNext,
}) => {
  const [errorMsg, setErrorMsg] = useState(null);

  const handleNextClick = () => {
    if (!pickupAddress.trim()) {
      setErrorMsg('Vui lòng nhập điểm đón cụ thể để tiếp tục đặt xe.');
      return;
    }
    setErrorMsg(null);
    onNext();
  };

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs sm:text-sm text-[#64748B]">
        <span onClick={onBack} className="hover:text-[#15803D] cursor-pointer transition-colors">
          Trang chủ
        </span>
        <span className="text-xs">&gt;</span>
        <span onClick={onBack} className="hover:text-[#15803D] cursor-pointer transition-colors">
          Khám phá xe
        </span>
        <span className="text-xs">&gt;</span>
        <span className="hover:text-[#15803D] transition-colors">{vehicleName}</span>
        <span className="text-xs">&gt;</span>
        <span className="font-semibold text-[#14221A]">Gửi yêu cầu thuê</span>
      </nav>

      {/* Vehicle Summary Header Card */}
      <section className="w-full bg-white rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)] border border-[#E6EBE8] p-4 sm:p-6 transition-all duration-180 hover:-translate-y-0.5">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-4 min-w-0">
            <div className="w-24 h-20 sm:w-28 sm:h-20 rounded-[12px] overflow-hidden bg-[#F7F8F6] shrink-0">
              <img
                className="w-full h-full object-cover"
                alt={vehicleName}
                src={vehicleImage}
              />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap mb-1">
                <h2 className="text-lg font-bold text-[#14221A] truncate">{vehicleName} (2024)</h2>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full bg-[#EFF4FF] text-[#3D4A42] text-xs font-semibold">
                  {plateNumber}
                </span>
              </div>
              <div className="flex items-center gap-1.5 text-xs sm:text-sm text-[#3D4A42]">
                <span className="material-symbols-outlined text-[18px] text-[#004F35]">schedule</span>
                <span className="truncate">
                  Gói đã chọn:{' '}
                  <strong className="text-[#14221A]">
                    {planSummary || `${slots} slot`}
                  </strong>
                </span>
              </div>
            </div>
          </div>
          <div className="flex sm:flex-col items-baseline sm:items-end justify-between w-full sm:w-auto pt-2 sm:pt-0 border-t sm:border-t-0 border-[#E6EBE8]">
            <span className="text-xs text-[#3D4A42]">Đơn giá niêm yết</span>
            <div className="text-right">
              <span className="text-2xl font-bold text-[#004F35]">{priceLabel}</span>
              <span className="text-xs text-[#3D4A42] font-medium"> / slot</span>
            </div>
          </div>
        </div>
      </section>

      {/* Stepper 3 Bước */}
      <section className="w-full max-w-3xl mx-auto my-6">
        <div className="relative flex items-center justify-between">
          <div className="absolute left-8 right-8 top-1/2 -translate-y-1/2 h-[3px] bg-[#BEC9C1] -z-0"></div>
          <div className="absolute left-8 w-1/2 top-1/2 -translate-y-1/2 h-[3px] bg-[#004F35] -z-0"></div>

          {/* Step 1 Check */}
          <div className="flex flex-col items-center gap-2 relative z-10 bg-[#F7F8F6] px-2 cursor-pointer" onClick={onBack}>
            <div className="w-9 h-9 rounded-full bg-[#004F35] flex items-center justify-center text-white shadow-sm ring-4 ring-[#F7F8F6]">
              <span className="material-symbols-outlined text-[20px]">check</span>
            </div>
            <span className="text-xs sm:text-sm text-[#14221A] font-medium hidden sm:inline-block">Chọn gói & Thời gian</span>
          </div>

          {/* Step 2 Active */}
          <div className="flex flex-col items-center gap-2 relative z-10 bg-[#F7F8F6] px-2">
            <div className="w-9 h-9 rounded-full bg-[#004F35] flex items-center justify-center text-white font-bold text-sm shadow-md ring-4 ring-[#85F8C4]">
              2
            </div>
            <span className="text-xs sm:text-sm text-[#004F35] font-bold">Điểm đón & Khách</span>
          </div>

          {/* Step 3 Pending */}
          <div className="flex flex-col items-center gap-2 relative z-10 bg-[#F7F8F6] px-2">
            <div className="w-9 h-9 rounded-full bg-white ring-2 ring-[#BEC9C1] flex items-center justify-center text-[#565E74] font-semibold text-sm">
              3
            </div>
            <span className="text-xs sm:text-sm text-[#565E74] font-normal hidden sm:inline-block">Xác nhận & Gửi</span>
          </div>
        </div>
      </section>

      {/* Main Form Card Container */}
      <div className="max-w-3xl mx-auto w-full">
        <div className="mb-4">
          <h1 className="text-2xl font-bold text-[#14221A] tracking-tight">2. Điểm đón và số khách</h1>
          <p className="text-sm text-gray-600 mt-1">Cung cấp vị trí đón thuận tiện để tài xế chuyên trách phục vụ chu đáo</p>
        </div>

        <div className="bg-white rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)] p-6 sm:p-8 space-y-6 border border-[#E6EBE8]">
          {/* Field 1: Điểm đón */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-semibold text-[#14221A] flex items-center gap-1" htmlFor="pickup-address">
                Điểm đón <span className="text-red-600 font-bold">*</span>
              </label>
              <span className="text-xs text-[#565E74]">{pickupAddress.length}/500 ký tự</span>
            </div>
            <div className="relative">
              <div className="absolute left-3.5 top-3.5 text-[#004F35] pointer-events-none">
                <span className="material-symbols-outlined text-[20px]">location_on</span>
              </div>
              <textarea
                id="pickup-address"
                rows={2}
                maxLength={500}
                value={pickupAddress}
                onChange={(e) => {
                  setPickupAddress(e.target.value);
                  if (e.target.value.trim()) setErrorMsg(null);
                }}
                placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố"
                className={`w-full pl-10 pr-4 py-3 rounded-[12px] bg-white text-[#14221A] text-sm shadow-xs outline-none transition-all duration-180 resize-none border ${
                  errorMsg ? 'border-red-500 bg-red-50/20' : 'border-[#BEC9C1] focus:ring-2 focus:ring-[#004F35]'
                }`}
              />
            </div>

            {errorMsg ? (
              <p className="text-xs font-medium text-red-600 flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px]">error</span>
                {errorMsg}
              </p>
            ) : (
              <div className="flex items-start gap-1.5 pt-0.5 text-xs text-gray-500">
                <span className="material-symbols-outlined text-[16px] text-gray-400 shrink-0">info</span>
                <span>Vui lòng nhập chính xác số nhà để tài xế liên hệ đúng giờ hẹn</span>
              </div>
            )}

            {/* Static Map Preview */}
            <div className="mt-3 pt-1">
              <div className="relative w-full h-44 rounded-[12px] overflow-hidden shadow-xs bg-[#E5EEFF]">
                <div
                  className="w-full h-full bg-cover bg-center"
                  style={{
                    backgroundImage: `url('https://lh3.googleusercontent.com/aida-public/AB6AXuCczHXg08KoFcghzMO9YWbUr9POqAkJYOo53snZZ4msyzgdo1d7tyPg3IGjz4k639y6SjBSbd4DkWW2yzppqiH4GwfqvfzEeVVnJXeoLHxgc6KpRy4fNZEnMEw09qMszetf7hdX8RTFzLYD-AQmoYhF_C8Qnti_ftnIZRwYlGnizHB1sDR_L_yDI4vReixXbsSNhp_fKN_OfBblqx6fTVohMssDQwaB1YsOpg5mdYFqVhft765uOsVSJw')`,
                  }}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent pointer-events-none"></div>
                <div className="absolute bottom-3 left-3 flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/90 backdrop-blur-md shadow-xs">
                  <span className="material-symbols-outlined text-[15px] text-[#004F35]">pin_drop</span>
                  <span className="text-xs text-[#14221A] font-semibold tracking-wide">Minh họa khu vực đón</span>
                </div>
              </div>
            </div>
          </div>

          {/* Field 2: Số khách */}
          <div className="space-y-2 pt-2">
            <label className="text-sm font-semibold text-[#14221A] flex items-center gap-1">
              Số khách <span className="text-red-600 font-bold">*</span>
            </label>
            <div className="flex items-center gap-4 flex-wrap">
              <div className="inline-flex items-center rounded-[12px] bg-[#EFF4FF] p-1 shadow-xs border border-[#BEC9C1]">
                <button
                  type="button"
                  onClick={() => setPassengerCount(Math.max(1, passengerCount - 1))}
                  disabled={passengerCount <= 1}
                  className="w-10 h-10 rounded-[8px] flex items-center justify-center text-[#14221A] bg-white hover:bg-[#DAE2FC]/50 active:scale-[0.98] transition-all duration-180 disabled:opacity-40 disabled:pointer-events-none shadow-xs cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[20px]">remove</span>
                </button>
                <span className="w-14 text-center text-lg font-bold text-[#14221A] select-none">
                  {passengerCount}
                </span>
                <button
                  type="button"
                  onClick={() => setPassengerCount(Math.min(maxPassengers, passengerCount + 1))}
                  disabled={passengerCount >= maxPassengers}
                  className="w-10 h-10 rounded-[8px] flex items-center justify-center text-[#14221A] bg-white hover:bg-[#DAE2FC]/50 active:scale-[0.98] transition-all duration-180 disabled:opacity-40 disabled:pointer-events-none shadow-xs cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[20px]">add</span>
                </button>
              </div>
              <div className="flex items-center gap-2 text-xs text-gray-600 font-medium">
                <span className="material-symbols-outlined text-[18px] text-[#004F35]">groups</span>
                <span>Tối đa {maxPassengers} hành khách cho dòng vehicle này</span>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Navigation Buttons */}
        <div className="mt-6 flex items-center justify-between gap-4">
          <button
            onClick={onBack}
            className="inline-flex items-center justify-center gap-1.5 px-6 py-3 rounded-[12px] bg-white text-[#14221A] font-semibold border border-[#E6EBE8] hover:bg-gray-50 active:scale-[0.98] transition-all duration-180 cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
            <span>Quay lại</span>
          </button>
          <button
            onClick={handleNextClick}
            className="inline-flex items-center justify-center gap-1.5 px-7 py-3 rounded-[12px] bg-[#15803D] text-white font-semibold shadow-md hover:bg-[#166534] active:scale-[0.98] transition-all duration-180 cursor-pointer"
            type="button"
          >
            <span>Tiếp tục: Xác nhận</span>
            <span className="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </div>
      </div>
    </div>
  );
};
