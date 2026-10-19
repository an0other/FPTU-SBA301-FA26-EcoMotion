import { useState } from 'react';
import { Link } from 'react-router-dom';


export const ConfirmRequestStep = ({
  vehicleId,
  vehicleName,
  plateNumber,
  vehicleImage,
  pickupAddress,
  passengerCount,
  planTitle,
  durationLabel,
  formattedStart,
  projectedEnd,
  priceNote,
  depositNote,
  vehicleYear,
  totalAmount,
  depositAmount,
  planType,
  onBack,
  onSubmit,
}) => {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = () => {
    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      onSubmit();
    }, 600);
  };

  return (
    <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-4 space-y-4">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs sm:text-sm text-[#64748B]">
        <Link to="/" className="hover:text-[#15803D] transition-colors">Trang chủ</Link>
        <span className="text-xs">&gt;</span>
        <Link to={`/vehicles/${vehicleId}`} className="hover:text-[#15803D] transition-colors">{vehicleName}</Link>
        <span className="text-xs">&gt;</span>
        <span className="font-semibold text-[#14221A]">Gửi yêu cầu đặt xe</span>
      </nav>

      {/* Stepper 3 Bước */}
      <div className="bg-white rounded-[16px] p-4 sm:p-6 shadow-[0_8px_24px_rgba(20,34,26,0.06)] border border-[#E6EBE8]">
        <div className="relative flex items-center justify-between">
          <div className="absolute left-[15%] right-[50%] top-4 h-[2px] bg-[#15803D] z-0"></div>
          <div className="absolute left-[50%] right-[15%] top-4 h-[2px] bg-[#15803D] z-0"></div>

          {/* Step 1 */}
          <div className="relative z-10 flex flex-col items-center text-center w-1/3 cursor-pointer" onClick={onBack}>
            <div className="w-8 h-8 rounded-full bg-[#15803D] text-white flex items-center justify-center shadow-sm">
              <span className="material-symbols-outlined text-[18px]">check</span>
            </div>
            <span className="mt-1 font-semibold text-xs sm:text-sm text-[#14221A]">Chọn gói & Thời gian</span>
            <span className="text-[11px] text-gray-500 hidden sm:inline-block">Đã hoàn thành</span>
          </div>

          {/* Step 2 */}
          <div className="relative z-10 flex flex-col items-center text-center w-1/3 cursor-pointer" onClick={onBack}>
            <div className="w-8 h-8 rounded-full bg-[#15803D] text-white flex items-center justify-center shadow-sm">
              <span className="material-symbols-outlined text-[18px]">check</span>
            </div>
            <span className="mt-1 font-semibold text-xs sm:text-sm text-[#14221A]">Điểm đón & Khách</span>
            <span className="text-[11px] text-gray-500 hidden sm:inline-block">Đã hoàn thành</span>
          </div>

          {/* Step 3 Active */}
          <div className="relative z-10 flex flex-col items-center text-center w-1/3">
            <div className="w-8 h-8 rounded-full bg-[#15803D] text-white flex items-center justify-center shadow-[0_0_0_4px_#E8F6EE] font-bold text-sm">
              3
            </div>
            <span className="mt-1 font-bold text-xs sm:text-sm text-[#15803D]">Xác nhận & Gửi</span>
            <span className="text-[11px] text-[#15803D] font-semibold hidden sm:inline-block">Đang thực hiện</span>
          </div>
        </div>
      </div>

      {/* Section Header */}
      <div className="space-y-1">
        <span className="inline-block px-2.5 py-0.5 bg-[#E8F6EE] text-[#15803D] rounded-full text-xs uppercase tracking-wider font-semibold">
          Bước cuối
        </span>
        <h1 className="text-2xl font-bold text-[#14221A] tracking-tight">3. Xác nhận yêu cầu</h1>
        <p className="text-sm text-gray-600">
          Vui lòng kiểm tra lại thông tin lịch trình trước khi gửi đến chủ xe
        </p>
      </div>

      {/* Card Tóm Tắt & Chi Tiết Lịch Trình */}
      <div className="bg-white rounded-[16px] p-6 shadow-[0_8px_24px_rgba(20,34,26,0.06)] border border-[#E6EBE8] space-y-4">
        {/* Card Header: Car Profile */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 bg-[#F7F8F6] p-4 rounded-[12px] border border-[#E6EBE8]">
          <div className="flex items-center gap-4">
            <div className="w-28 h-20 sm:w-36 sm:h-24 rounded-[12px] overflow-hidden shrink-0 bg-gray-100">
              <img
                className="w-full h-full object-cover"
                alt={vehicleName}
                src={vehicleImage}
              />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-lg font-bold text-[#14221A]">{vehicleName}</h2>
                <span className="text-xs text-gray-500">({vehicleYear})</span>
              </div>
              <p className="text-xs text-gray-600 mt-1 flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px] text-gray-400">badge</span>
                Biển số xe: <span className="font-semibold text-[#14221A]">{plateNumber}</span>
              </p>
            </div>
          </div>
          <div className="self-start sm:self-center">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#E8F6EE] text-[#15803D] text-xs font-semibold shadow-xs">
              <span className="material-symbols-outlined text-[18px]">verified_user</span>
              Đã bao gồm tài xế
            </span>
          </div>
        </div>

        {/* Bento Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Slot & Gói */}
          <div className="bg-[#F7F8F6] p-4 rounded-[12px] border border-[#E6EBE8]">
            <div className="flex items-center gap-1.5 text-gray-500 text-xs font-semibold mb-1">
              <span className="material-symbols-outlined text-[#15803D] text-[18px]">calendar_view_day</span>
              Gói dịch vụ đã chọn
            </div>
            <div className="text-base font-bold text-[#14221A]">{planTitle}</div>
            <div className="mt-2 inline-block bg-white px-3 py-1 rounded-lg text-[#15803D] font-semibold text-xs border border-[#E6EBE8]">
              {durationLabel}
            </div>
          </div>

          {/* Số khách */}
          <div className="bg-[#F7F8F6] p-4 rounded-[12px] border border-[#E6EBE8]">
            <div className="flex items-center gap-1.5 text-gray-500 text-xs font-semibold mb-1">
              <span className="material-symbols-outlined text-[#15803D] text-[18px]">group</span>
              Số lượng hành khách
            </div>
            <div className="text-base font-bold text-[#14221A]">{passengerCount} hành khách lớn</div>
            <p className="text-xs text-gray-500 mt-2">Khoang xe cao cấp rộng rãi, điều hòa độc lập</p>
          </div>

          {/* Khoảng thời gian */}
          <div className="bg-[#F7F8F6] p-4 rounded-[12px] border border-[#E6EBE8] md:col-span-2">
            <div className="flex items-center gap-1.5 text-gray-500 text-xs font-semibold mb-2">
              <span className="material-symbols-outlined text-[#15803D] text-[18px]">schedule</span>
              Khoảng thời gian thuê xe
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center gap-3 bg-white p-3 rounded-lg border border-[#E6EBE8]">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-[#15803D]"></span>
                <span className="text-sm font-bold text-[#14221A]">{formattedStart}</span>
              </div>
              <div className="hidden sm:flex items-center text-[#15803D]">
                <span className="material-symbols-outlined text-[18px]">east</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-600"></span>
                <span className="text-sm font-bold text-[#14221A]">{projectedEnd}</span>
              </div>
            </div>
          </div>

          {/* Địa chỉ đón */}
          <div className="bg-[#F7F8F6] p-4 rounded-[12px] border border-[#E6EBE8] md:col-span-2">
            <div className="flex items-center gap-1.5 text-gray-500 text-xs font-semibold mb-1">
              <span className="material-symbols-outlined text-[#15803D] text-[18px]">pin_drop</span>
              Điểm đón duy nhất
            </div>
            <div className="flex items-start gap-2 bg-white p-3 rounded-lg border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[#15803D] text-[20px] shrink-0 mt-0.5">location_on</span>
              <div>
                <p className="text-sm font-bold text-[#14221A]">{pickupAddress}</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  Tài xế chuyên trách sẽ đón đúng giờ và liên hệ trước 30 phút xuất phát
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Financial Breakdown Card */}
      <div className="bg-white rounded-[16px] p-6 shadow-[0_8px_24px_rgba(20,34,26,0.06)] border border-[#E6EBE8] space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2 pb-2">
          <h3 className="text-base font-bold text-[#14221A] flex items-center gap-2">
            <span className="material-symbols-outlined text-[#15803D]">receipt_long</span>
            Hạch toán tài chính minh bạch
          </h3>
          <span className="bg-[#EFF4FF] text-[#3D4A42] px-3 py-1 rounded-full text-xs font-semibold">
            Chưa phát sinh thanh toán
          </span>
        </div>

        <div className="space-y-3">
          {/* Tiền thuê */}
          <div className="p-4 bg-[#F7F8F6] rounded-[12px] border border-[#E6EBE8]">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
              <div>
                <span className="text-sm font-bold text-[#14221A]">Tiền thuê dự kiến</span>
                <span className="text-xs text-gray-500 ml-2">({priceNote})</span>
              </div>
              <div className="text-xl font-bold text-[#14221A]">
                {totalAmount.toLocaleString('vi-VN')} đ
              </div>
            </div>
            <div className="mt-2 flex items-center gap-1.5 text-xs text-gray-600">
              <span className="material-symbols-outlined text-gray-400 text-[16px]">info</span>
              <span>Đây là khoản tiền thuê, <strong className="text-[#14221A]">chưa thu lúc này</strong>.</span>
            </div>
          </div>

          {/* Cọc */}
          <div className="p-4 bg-[#E8F6EE]/50 rounded-[12px] border border-[#15803D]/20">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
              <div>
                <span className="text-sm font-bold text-[#14221A]">Cọc dự kiến (thu sau khi chủ xe chấp nhận)</span>
                <span className="text-xs text-gray-500 ml-2">({depositNote})</span>
              </div>
              <div className="text-xl font-bold text-[#15803D]">
                {depositAmount.toLocaleString('vi-VN')} đ
              </div>
            </div>
            <div className="mt-2 p-2.5 bg-white rounded-lg border border-[#E6EBE8] flex items-start gap-2 text-xs">
              <span className="material-symbols-outlined text-[#15803D] text-[18px] shrink-0 mt-0.5">verified</span>
              <p className="text-gray-700">
                <strong className="text-[#15803D]">Chưa thanh toán lúc này.</strong> Bạn chỉ thanh toán cọc sau khi chủ xe xác nhận đồng ý chuyến đi.
              </p>
            </div>
          </div>

          {/* Yearly package note - only show for YEARLY plan */}
          {planType === 'YEARLY' && (
          <div className="p-3 bg-gray-50 rounded-[12px] border border-[#E6EBE8] flex items-start gap-2 text-xs text-gray-600">
            <span className="material-symbols-outlined text-gray-400 text-[18px] shrink-0 mt-0.5">description</span>
            <p>
              Gói năm: Trả trước theo hợp đồng và liên kết Khai GPLX và hợp đồng (không mở form upload tại đây).
            </p>
          </div>
          )}
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex flex-col-reverse sm:flex-row items-center justify-between gap-4 pt-2">
        <button
          onClick={onBack}
          className="w-full sm:w-auto px-6 py-3 rounded-[12px] bg-white border border-[#E6EBE8] hover:bg-gray-50 text-[#14221A] font-semibold text-sm flex items-center justify-center gap-2 transition-all duration-180 cursor-pointer"
          type="button"
        >
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          <span>Quay lại chỉnh sửa</span>
        </button>

        <button
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="w-full sm:w-auto min-w-[220px] px-7 py-3 rounded-[12px] bg-[#15803D] hover:bg-[#166534] text-white font-semibold text-sm flex items-center justify-center gap-2 shadow-md active:scale-[0.98] transition-all duration-180 cursor-pointer disabled:opacity-70"
          type="button"
        >
          {isSubmitting ? (
            <>
              <span className="material-symbols-outlined text-[18px] animate-spin">progress_activity</span>
              <span>Đang gửi...</span>
            </>
          ) : (
            <>
              <span>Gửi yêu cầu đặt xe</span>
              <span className="material-symbols-outlined text-[18px]">send</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
};
