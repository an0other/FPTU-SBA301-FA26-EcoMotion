const FUEL_LABELS = {
  EV: 'Thuần điện (EV)',
  GASOLINE: 'Xăng',
  DIESEL: 'Dầu',
  HYBRID: 'Hybrid',
}

export const RequestSubmitted = ({
  booking,
  onPayDeposit,
  onGoToMyBookings,
  onGoToCarPage,
}) => {
  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs sm:text-sm text-[#64748B]">
        <span onClick={onGoToCarPage} className="hover:text-[#15803D] cursor-pointer transition-colors">
          Trang chủ
        </span>
        <span className="text-xs">&gt;</span>
        <span onClick={onGoToMyBookings} className="hover:text-[#15803D] cursor-pointer transition-colors">
          Đơn của tôi
        </span>
        <span className="text-xs">&gt;</span>
        <span className="font-semibold text-[#14221A]">Chi tiết đơn {booking.id}</span>
      </nav>

      {/* Main Card Status Center */}
      <div className="w-full max-w-2xl mx-auto bg-white rounded-[16px] p-6 sm:p-10 shadow-[0_8px_24px_rgba(20,34,26,0.06)] border border-[#E6EBE8] relative overflow-hidden">
        {/* Top Decorative Accent */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-[#15803D]"></div>

        {/* Icon Check */}
        <div className="flex justify-center mb-4">
          <div className="w-16 h-16 rounded-full bg-[#E8F6EE] flex items-center justify-center shadow-xs">
            <svg
              className="w-8 h-8 text-[#15803D]"
              fill="none"
              stroke="currentColor"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2.5"
              viewBox="0 0 24 24"
            >
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </div>
        </div>

        {/* Header Title & Order Reference */}
        <div className="text-center space-y-2 mb-6">
          <h1 className="text-xl sm:text-2xl font-bold text-[#14221A] tracking-tight">
            Lịch xe đã được giữ thành công!
          </h1>

          <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
            <span className="font-mono font-bold text-2xl text-[#14221A] tracking-wider bg-[#F7F8F6] px-3 py-1 rounded-lg border border-[#E6EBE8]">
              {booking.id}
            </span>
            <span className="inline-flex items-center px-3.5 py-1 rounded-full text-xs font-semibold bg-[#FEF08A] text-amber-900 shadow-xs">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-600 mr-1.5 animate-pulse"></span>
              Chờ thanh toán cọc
            </span>
          </div>

          <p className="text-sm text-gray-600 max-w-md mx-auto pt-1">
            Hãy thanh toán bằng VietQR trong thời hạn để giữ lịch. <strong className="text-[#14221A]">Số cọc do hệ thống tính theo chính sách của xe.</strong>
          </p>
        </div>

        {/* Compact Reservation Summary Box */}
        <div className="bg-[#F7F8F6] rounded-[12px] p-5 border border-[#E6EBE8] my-6 space-y-3">
          {/* Vehicle Row */}
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-lg bg-white flex items-center justify-center shrink-0 shadow-xs text-[#15803D] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[20px]">electric_car</span>
            </div>
            <div className="min-w-0 flex-1">
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block">
                PHƯƠNG TIỆN
              </span>
              <span className="text-sm font-bold text-[#14221A] block truncate">
                {booking.vehicle.name} ({booking.vehicle.year})
              </span>
              <span className="text-xs text-gray-500 block">
                Biển số xe: {booking.vehicle.plateNumber}
                {booking.vehicle.fuelType ? ` • ${FUEL_LABELS[booking.vehicle.fuelType] ?? booking.vehicle.fuelType}` : ''}
              </span>
            </div>
          </div>

          <div className="h-[1px] bg-[#E6EBE8] w-full"></div>

          {/* Schedule Row */}
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-lg bg-white flex items-center justify-center shrink-0 shadow-xs text-[#15803D] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[20px]">calendar_today</span>
            </div>
            <div className="min-w-0 flex-1">
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block">
                THỜI GIAN ĐÓN
              </span>
              <span className="text-sm font-bold text-[#14221A] block">
                {booking.formattedStart}
              </span>
              <span className="text-xs text-gray-500 block">
                Thời lượng: {booking.slots} slot liên tiếp
                {booking.planType === 'YEARLY' ? ' theo hợp đồng' : ' kèm tài xế'}
              </span>
            </div>
          </div>

          <div className="h-[1px] bg-[#E6EBE8] w-full"></div>

          {/* Pickup Address Row */}
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-lg bg-white flex items-center justify-center shrink-0 shadow-xs text-[#15803D] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[20px]">location_on</span>
            </div>
            <div className="min-w-0 flex-1">
              <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider block">
                ĐIỂM ĐÓN
              </span>
              <span className="text-xs sm:text-sm text-[#14221A] font-medium block">
                {booking.pickupAddress}
              </span>
            </div>
          </div>

          <div className="h-[1px] bg-[#E6EBE8] w-full"></div>

          {/* Deposit Row */}
          <div className="flex items-start gap-3 bg-white p-3.5 rounded-lg border border-[#E6EBE8] shadow-xs">
            <div className="w-9 h-9 rounded-lg bg-[#E8F6EE] flex items-center justify-center shrink-0 text-[#15803D]">
              <span className="material-symbols-outlined text-[20px]">account_balance_wallet</span>
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline justify-between flex-wrap gap-1">
                <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  TIỀN CỌC CẦN THANH TOÁN
                </span>
                <span className="text-base sm:text-lg text-[#15803D] font-bold">
                  {booking.depositAmount.toLocaleString('vi-VN')} đ
                </span>
              </div>
              <p className="text-xs text-gray-600 mt-0.5 flex items-center gap-1">
                <span className="material-symbols-outlined text-[15px] text-[#15803D]">
                  verified_user
                </span>
                Khoản cọc được trừ vào tổng tiền thuê
              </p>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
          <button
            onClick={onPayDeposit}
            className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-3 rounded-[12px] bg-[#15803D] text-white font-semibold text-sm hover:bg-[#166534] transition-colors shadow-xs cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[20px] mr-2">qr_code_2</span>
            Thanh toán cọc VietQR
          </button>
          <button
            onClick={onGoToMyBookings}
            className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-3 rounded-[12px] bg-white text-[#15803D] border border-[#15803D] font-semibold text-sm hover:bg-emerald-50 transition-colors shadow-xs cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[20px] mr-2">receipt_long</span>
            Xem đơn của tôi
          </button>
          <button
            onClick={onGoToCarPage}
            className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-3 rounded-[12px] bg-white text-[#14221A] font-semibold text-sm border border-[#E6EBE8] hover:bg-gray-50 transition-colors shadow-xs cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[20px] mr-2">arrow_back</span>
            Về trang xe
          </button>
        </div>

        {/* Support Note */}
        <div className="mt-6 text-center pt-2">
          <p className="text-xs text-gray-500">
            Cần hỗ trợ chỉnh sửa lộ trình? Hotline miễn cước{' '}
            <a href="tel:19008899" className="font-semibold text-[#15803D] hover:underline">
              1900 8899
            </a>
          </p>
        </div>
      </div>
    </div>
  );
};
