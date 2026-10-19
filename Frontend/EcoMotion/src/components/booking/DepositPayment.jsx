import { useState } from 'react';


export const DepositPayment = ({
  booking,
  onPay,
  onViewDetail,
  onGoToMyBookings,
  planSummary,
  payTitle = 'Cọc giữ chỗ',
  ratioText,
  payHint = 'Đây là tiền cọc giữ chỗ, không phải cả tiền thuê',
  successText,
  amountLabel = 'Số tiền cọc:',
}) => {
  const [paymentMethod, setPaymentMethod] = useState('card');
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState('');

  const handleCheckout = async () => {
    setIsProcessing(true);
    setError('');
    try {
      await onPay(paymentMethod);
      setShowSuccessModal(true);
    } catch (err) {
      setError(err?.response?.data?.message || 'Không thanh toán được');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="w-full max-w-[1240px] mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-xs text-[#14221A]/60 font-normal">
        <button onClick={onGoToMyBookings} className="hover:text-[#15803D] transition-colors flex items-center gap-1 cursor-pointer">
          <span className="material-symbols-outlined text-[15px]">home</span> Trang chủ
        </button>
        <span className="material-symbols-outlined text-[13px] text-gray-400">chevron_right</span>
        <button onClick={onGoToMyBookings} className="hover:text-[#15803D] transition-colors cursor-pointer">
          Đơn của tôi
        </button>
        <span className="material-symbols-outlined text-[13px] text-gray-400">chevron_right</span>
        <span className="font-medium text-[#14221A]">Thanh toán cọc giữ chỗ</span>
      </nav>

      {/* Header Banner: Order ID & Status */}
      <div className="bg-white rounded-2xl border border-[#E6EBE8] p-4 sm:p-5 shadow-[0_8px_24px_rgba(20,34,26,0.06)] flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-xs uppercase tracking-wider font-semibold text-gray-500">Mã đơn:</span>
          <span className="font-mono font-bold text-lg text-[#15803D] tracking-wide">{booking.id}</span>
        </div>
        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#E8F6EE] text-[#15803D] text-xs font-semibold">
          <span className="w-2 h-2 rounded-full bg-[#15803D]"></span>
          Đã chấp nhận
        </div>
      </div>

      {/* 2 Column Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Trip Info */}
        <section className="lg:col-span-7 bg-white rounded-2xl border border-[#E6EBE8] p-6 shadow-[0_8px_24px_rgba(20,34,26,0.06)] space-y-6">
          <div className="flex items-center gap-2.5 pb-4 border-b border-[#E6EBE8]">
            <span className="material-symbols-outlined text-[#15803D] text-[22px]">directions_car</span>
            <h2 className="text-base font-bold text-[#14221A]">Thông tin chuyến đi</h2>
          </div>

          {/* Vehicle photo & title */}
          <div className="grid grid-cols-1 sm:grid-cols-12 gap-5">
            <div className="sm:col-span-5 relative overflow-hidden rounded-xl bg-gray-50 aspect-[16/11]">
              <img
                alt={booking.vehicle.name}
                className="w-full h-full object-cover rounded-xl"
                src={booking.vehicle.image}
              />
              <div className="absolute bottom-2 left-2 bg-white/95 px-2 py-0.5 rounded text-[11px] font-mono font-bold text-[#14221A] shadow-xs border border-[#E6EBE8]">
                {booking.vehicle.plateNumber}
              </div>
            </div>
            <div className="sm:col-span-7 flex flex-col justify-center gap-1.5">
              <span className="text-xs font-semibold text-[#15803D] uppercase">Thuần điện 100%</span>
              <h3 className="text-lg font-bold text-[#14221A] leading-snug">{booking.vehicle.name} ({booking.vehicle.year})</h3>
              <p className="text-xs text-gray-600 leading-relaxed">
                Nội thất da cao cấp, vận hành êm ái, hệ thống trợ lái thông minh và an toàn tối đa cho mọi hành trình.
              </p>
            </div>
          </div>

          {/* Booking details list */}
          <div className="space-y-3 pt-2">
            <div className="flex items-start gap-3 p-3.5 rounded-xl bg-[#F7F8F6] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[#15803D] text-[20px] shrink-0 mt-0.5">category</span>
              <div>
                <div className="text-xs font-semibold text-gray-500">Gói thuê</div>
                <div className="text-sm font-semibold text-[#14221A] mt-0.5">
                  {planSummary}
                </div>
              </div>
            </div>

            <div className="flex items-start gap-3 p-3.5 rounded-xl bg-[#F7F8F6] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[#15803D] text-[20px] shrink-0 mt-0.5">calendar_month</span>
              <div>
                <div className="text-xs font-semibold text-gray-500">Khoảng thời gian</div>
                <div className="text-sm font-semibold text-[#14221A] mt-0.5">
                  {booking.formattedStart} → {booking.formattedEnd}
                </div>
              </div>
            </div>

            <div className="flex items-start gap-3 p-3.5 rounded-xl bg-[#F7F8F6] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[#15803D] text-[20px] shrink-0 mt-0.5">pin_drop</span>
              <div>
                <div className="text-xs font-semibold text-gray-500">Địa chỉ đón duy nhất</div>
                <div className="text-sm font-semibold text-[#14221A] mt-0.5">{booking.pickupAddress}</div>
              </div>
            </div>

            <div className="flex items-start gap-3 p-3.5 rounded-xl bg-[#F7F8F6] border border-[#E6EBE8]">
              <span className="material-symbols-outlined text-[#15803D] text-[20px] shrink-0 mt-0.5">group</span>
              <div>
                <div className="text-xs font-semibold text-gray-500">Số khách</div>
                <div className="text-sm font-semibold text-[#14221A] mt-0.5">{booking.passengerCount} hành khách lớn</div>
              </div>
            </div>
          </div>
        </section>

        {/* Right Column: Deposit Payment Card */}
        <section className="lg:col-span-5 bg-white rounded-2xl border border-[#E6EBE8] p-6 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
          <h2 className="text-lg font-bold text-[#14221A] pb-4 border-b border-[#E6EBE8] mb-4">
            {payTitle}
          </h2>

          <div className="space-y-2 mb-4 text-sm">
            <div className="flex justify-between items-center text-gray-600">
              <span>Tiền thuê:</span>
              <span className="font-medium text-[#14221A]">{booking.totalAmount.toLocaleString('vi-VN')} đ</span>
            </div>
            {ratioText ? (
              <div className="flex justify-between items-center text-gray-600">
                <span>Tỷ lệ cọc đã chốt:</span>
                <span className="font-medium text-[#14221A]">{ratioText}</span>
              </div>
            ) : null}
          </div>

          <div className="p-4 rounded-xl bg-[#E8F6EE] border border-[#15803D]/20 mb-5">
            <div className="text-xs font-semibold text-gray-600 mb-1">Cần thanh toán:</div>
            <div className="text-2xl font-bold text-[#15803D] leading-none">
              {booking.depositAmount.toLocaleString('vi-VN')} đ
            </div>
            <p className="text-xs text-gray-600 mt-2 italic">
              {payHint}
            </p>
          </div>

          {/* Payment Method Selector */}
          <div className="mb-6">
            <div className="text-xs font-semibold text-gray-500 mb-2.5 uppercase tracking-wide">
              PHƯƠNG THỨC THANH TOÁN
            </div>
            <div className="flex flex-col gap-2.5">
              {/* Option 1: Card */}
              <label
                onClick={() => setPaymentMethod('card')}
                className={`h-14 px-4 rounded-xl border flex items-center justify-between cursor-pointer transition-all duration-180 ${
                  paymentMethod === 'card'
                    ? 'bg-[#E8F6EE] border-[#15803D]'
                    : 'bg-white border-[#E6EBE8] hover:border-gray-300'
                }`}
              >
                <div className="flex items-center gap-3">
                  <input
                    type="radio"
                    name="payment_method"
                    value="card"
                    checked={paymentMethod === 'card'}
                    onChange={() => setPaymentMethod('card')}
                    className="w-4 h-4 text-[#15803D] accent-[#15803D] cursor-pointer"
                  />
                  <span className="text-sm font-medium text-[#14221A]">Thẻ (VNPay, mock)</span>
                </div>
                <span className={`material-symbols-outlined text-[20px] ${paymentMethod === 'card' ? 'text-[#15803D]' : 'text-gray-400'}`}>
                  credit_card
                </span>
              </label>

              {/* Option 2: Wallet */}
              <label
                onClick={() => setPaymentMethod('wallet')}
                className={`h-14 px-4 rounded-xl border flex items-center justify-between cursor-pointer transition-all duration-180 ${
                  paymentMethod === 'wallet'
                    ? 'bg-[#E8F6EE] border-[#15803D]'
                    : 'bg-white border-[#E6EBE8] hover:border-gray-300'
                }`}
              >
                <div className="flex items-center gap-3">
                  <input
                    type="radio"
                    name="payment_method"
                    value="wallet"
                    checked={paymentMethod === 'wallet'}
                    onChange={() => setPaymentMethod('wallet')}
                    className="w-4 h-4 text-[#15803D] accent-[#15803D] cursor-pointer"
                  />
                  <span className="text-sm font-medium text-[#14221A]">Ví (MoMo, mock)</span>
                </div>
                <span className={`material-symbols-outlined text-[20px] ${paymentMethod === 'wallet' ? 'text-[#15803D]' : 'text-gray-400'}`}>
                  account_balance_wallet
                </span>
              </label>

              {/* Option 3: Cash */}
              <label
                onClick={() => setPaymentMethod('cash')}
                className={`h-14 px-4 rounded-xl border flex items-center justify-between cursor-pointer transition-all duration-180 ${
                  paymentMethod === 'cash'
                    ? 'bg-[#E8F6EE] border-[#15803D]'
                    : 'bg-white border-[#E6EBE8] hover:border-gray-300'
                }`}
              >
                <div className="flex items-center gap-3">
                  <input
                    type="radio"
                    name="payment_method"
                    value="cash"
                    checked={paymentMethod === 'cash'}
                    onChange={() => setPaymentMethod('cash')}
                    className="w-4 h-4 text-[#15803D] accent-[#15803D] cursor-pointer"
                  />
                  <span className="text-sm font-medium text-[#14221A]">Tiền mặt</span>
                </div>
                <span className={`material-symbols-outlined text-[20px] ${paymentMethod === 'cash' ? 'text-[#15803D]' : 'text-gray-400'}`}>
                  payments
                </span>
              </label>
            </div>
          </div>

          {/* Checkout Button */}
          <button
            onClick={handleCheckout}
            disabled={isProcessing}
            className="w-full py-3.5 px-4 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-semibold text-sm transition-all duration-180 active:scale-[0.98] cursor-pointer shadow-xs flex items-center justify-center gap-2 disabled:opacity-70"
            type="button"
          >
            {isProcessing ? (
              <>
                <span className="material-symbols-outlined text-[18px] animate-spin">progress_activity</span>
                <span>Đang xử lý thanh toán...</span>
              </>
            ) : (
              <>
                <span className="material-symbols-outlined text-[18px]">lock</span>
                <span>Thanh toán</span>
              </>
            )}
          </button>

          {error ? <p className="mt-3 text-xs text-[#B42318]">{error}</p> : null}
          <p className="text-[11px] text-center text-gray-500 mt-3">
            Giao dịch được bảo mật và lịch xe sẽ được khóa tự động sau khi thanh toán.
          </p>
        </section>
      </div>

      {/* Success Modal */}
      {showSuccessModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl border border-[#E6EBE8] p-6 sm:p-8 max-w-md w-full shadow-2xl text-center animate-in zoom-in duration-200">
            <div className="w-14 h-14 rounded-full bg-[#E8F6EE] text-[#15803D] flex items-center justify-center mx-auto mb-4">
              <span className="material-symbols-outlined text-[32px]">check_circle</span>
            </div>

            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#E8F6EE] text-[#15803D] text-xs font-semibold mb-3">
              <span className="w-1.5 h-1.5 rounded-full bg-[#15803D]"></span>
              Đã xác nhận
            </div>

            <h3 className="text-xl font-bold text-[#14221A] mb-1.5">Lịch xe đã được khóa</h3>
            <p className="text-xs text-gray-600 leading-relaxed mb-6">
              {successText}
            </p>

            <div className="bg-[#F7F8F6] rounded-xl p-3.5 border border-[#E6EBE8] mb-6 text-left text-xs space-y-1.5">
              <div className="flex justify-between text-gray-600">
                <span>{amountLabel}</span>
                <span className="font-bold text-[#15803D]">
                  {booking.depositAmount.toLocaleString('vi-VN')} đ
                </span>
              </div>
              <div className="flex justify-between text-gray-600">
                <span>Điểm đón:</span>
                <span className="font-medium text-[#14221A] truncate ml-2">
                  {booking.pickupAddress}
                </span>
              </div>
            </div>

            <button
              onClick={() => {
                setShowSuccessModal(false);
                onViewDetail();
              }}
              className="w-full py-3 px-4 rounded-xl bg-[#15803D] hover:bg-[#166534] text-white font-semibold text-sm transition-all duration-180 active:scale-[0.98] cursor-pointer"
              type="button"
            >
              Xem chi tiết đơn
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
