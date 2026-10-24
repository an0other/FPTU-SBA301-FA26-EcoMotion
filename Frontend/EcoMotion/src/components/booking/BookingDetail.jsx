import { useState } from 'react';
import { readErrorMessage } from '../../services/apiClient';
import { CancelBookingModal } from './CancelBookingModal';

const DISPUTE_LABELS = {
  CHARGE: 'Phụ phí / khoản phí',
  SERVICE_QUALITY: 'Chất lượng dịch vụ / tài xế',
  DEPOSIT_REFUND: 'Hoàn cọc',
  TRIP_NOT_CONFIRMED: 'Chuyến chưa được xác nhận kết thúc',
};

export const BookingDetail = ({
  booking,
  onGoBack,
  onGoToCarPage,
  onPayDeposit,
  onCancelBooking,
  onCompleteBooking,
  onOpenDispute,
}) => {
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [complaintCategory, setComplaintCategory] = useState('');
  const [complaintCharge, setComplaintCharge] = useState('');
  const [actionError, setActionError] = useState('');
  const [disputeNote, setDisputeNote] = useState('');

  const handleComplaintSubmit = async (e) => {
    e.preventDefault();
    setActionError('');
    try {
      await onOpenDispute({
        category: complaintCategory,
        chargeType: complaintCategory === 'CHARGE' ? complaintCharge : undefined,
      });
      setDisputeNote('Đã gửi khiếu nại. Admin sẽ xem xét.');
      setComplaintCategory('');
      setComplaintCharge('');
    } catch (err) {
      setActionError(readErrorMessage(err));
    }
  };

  return (
    <div className="w-full max-w-[1120px] mx-auto px-4 py-8 space-y-6">
      {/* Header Row */}
      <div className="flex flex-col gap-2">
        <div className="text-sm font-medium text-[#6D7A72] flex items-center gap-1.5">
          <button onClick={onGoBack} className="hover:text-[#14221A] transition-colors cursor-pointer">
            Đơn của tôi
          </button>
          <span>/</span>
          <span className="text-[#14221A] font-medium">Chi tiết đơn</span>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <h1 className="font-mono font-bold text-2xl sm:text-[28px] text-[#14221A] tracking-tight">
              {booking.id}
            </h1>

            {booking.status === 'IN_PROGRESS' && (
              <span className="px-3 py-1 text-xs font-semibold rounded-full inline-flex items-center gap-1.5 bg-[#E8F6EE] text-[#15803D]">
                <span className="w-2 h-2 rounded-full bg-[#15803D]"></span>
                Đang chạy
              </span>
            )}

            {booking.status === 'REJECTED' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-[#FEE4E2] text-[#B42318]">
                Từ chối
              </span>
            )}

            {booking.status === 'CONFIRMED' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-[#E8F6EE] text-[#15803D]">
                Đã xác nhận
              </span>
            )}

            {booking.status === 'ACCEPTED' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-[#E8F6EE] text-[#15803D]">
                Đã chấp nhận
              </span>
            )}

            {booking.status === 'PENDING' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-900">
                Chờ duyệt
              </span>
            )}

            {booking.status === 'CANCELLED' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800">
                Đã huỷ
              </span>
            )}

            {booking.status === 'COMPLETED' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-teal-100 text-teal-800">
                Hoàn thành
              </span>
            )}

            {booking.status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-orange-100 text-orange-800">
                Chờ chốt phụ phí
              </span>
            )}

            {booking.status === 'AWAITING_FINAL_PAYMENT' && (
              <span className="px-3 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800">
                Chờ thanh toán quyết toán
              </span>
            )}
          </div>

          <div className="text-xs sm:text-sm text-gray-600">
            Thời gian gửi: <span className="font-medium text-[#14221A]">{booking.createdAt}</span>
          </div>
        </div>
        {actionError && <p className="text-sm text-[#B42318]">{actionError}</p>}
      </div>

      {/* Main 2-Column Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
        {/* Left Column (7 cols) */}
        <div className="lg:col-span-7 flex flex-col gap-5">
          {/* REJECTED SCREEN HERO BANNER */}
          {booking.status === 'REJECTED' && (
            <div className="rounded-[16px] bg-[#FEF3F2] border border-[#F8D0CB] p-5 shadow-[0_8px_24px_rgba(20,34,26,0.06)] flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="flex items-center gap-4 w-full sm:w-auto">
                <div className="w-12 h-12 rounded-full bg-[#FEE4E2] flex items-center justify-center shrink-0 text-[#B42318]">
                  <span className="material-symbols-outlined text-[26px]">warning</span>
                </div>
                <div>
                  <h2 className="text-base font-bold text-[#14221A]">Chủ xe từ chối</h2>
                  <p className="text-sm text-[#14221A]/80 mt-0.5">
                    {booking.rejectionReason || 'Không có lý do. Hãy đặt lại theo giá hiện hành.'}
                  </p>
                </div>
              </div>
              <button
                onClick={onGoToCarPage}
                className="inline-flex items-center justify-center px-5 py-2.5 rounded-[12px] bg-[#15803D] hover:bg-[#166534] text-white text-sm font-semibold transition-colors shrink-0 cursor-pointer"
                type="button"
              >
                Về trang xe
              </button>
            </div>
          )}

          {/* IN_PROGRESS HERO GREEN BANNER */}
          {booking.status === 'PENDING' && (
            <div className="p-5 bg-white border border-[#E6EBE8] rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-[#14221A]">Đơn đang chờ chủ xe</h2>
                <p className="text-xs text-gray-600 mt-1">Chưa thu cọc. Bạn có thể huỷ yêu cầu.</p>
              </div>
              <button
                onClick={() => setIsCancelModalOpen(true)}
                type="button"
                className="px-4 py-2 bg-white text-[#B42318] border border-[#B42318] hover:bg-rose-50 text-xs font-semibold rounded-[12px] transition-colors cursor-pointer whitespace-nowrap"
              >
                Huỷ đơn
              </button>
            </div>
          )}

          {['IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(booking.status) && (
            <div className="p-5 text-white bg-[#15803D] rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex flex-col gap-1">
                <h2 className="text-base font-bold text-white">
                  {booking.status === 'IN_PROGRESS' ? 'Chuyến đang diễn ra' : 'Tài xế đã kết thúc chuyến'}
                </h2>
                <p className="text-xs sm:text-sm text-white/90">
                  {booking.actualStartTime
                    ? `Bắt đầu thực tế: ${booking.actualStartTime}`
                    : 'Chưa có giờ bắt đầu thực tế'}
                </p>
              </div>
              {booking.canConfirmEnd ? (
                <button
                  onClick={async () => {
                    setActionError('');
                    try {
                      await onCompleteBooking(booking.id);
                    } catch (err) {
                      setActionError(readErrorMessage(err));
                    }
                  }}
                  type="button"
                  className="px-5 py-2.5 bg-white text-[#15803D] font-bold text-sm rounded-[12px] shadow-xs hover:bg-emerald-50 transition-all cursor-pointer active:scale-95 shrink-0"
                >
                  Xác nhận kết thúc chuyến
                </button>
              ) : (
                <p className="text-sm font-medium text-white">Đang chờ phía nhà cung cấp xác nhận</p>
              )}
            </div>
          )}

          {booking.status === 'AWAITING_FINAL_PAYMENT' && (
            <div className="p-5 bg-purple-50 border border-purple-200 rounded-[16px] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-purple-900">Đã chốt quyết toán</h2>
                <p className="text-xs text-purple-700 mt-1">Vui lòng thanh toán số dư qua cổng thanh toán VNPAY để hoàn tất đơn thuê.</p>
              </div>
              <button onClick={() => onPayDeposit(booking)} type="button" className="px-6 py-2.5 bg-purple-700 hover:bg-purple-800 text-white font-semibold text-sm rounded-[12px]">
                Thanh toán số dư
              </button>
            </div>
          )}

          {/* ACCEPTED CALL TO ACTION BANNER */}
          {booking.status === 'ACCEPTED' && (
            <div className="p-5 bg-[#E8F6EE] border border-[#15803D]/30 rounded-[16px] shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-[#15803D]">Chủ xe đã chấp nhận đơn!</h2>
                <p className="text-xs text-gray-600 mt-1">
                  {booking.upfront
                    ? 'Vui lòng thanh toán khoản trả trước để khóa lịch xe.'
                    : 'Vui lòng hoàn tất thanh toán cọc giữ chỗ để khóa lịch xe.'}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setIsCancelModalOpen(true)}
                  type="button"
                  className="px-4 py-2 bg-white text-[#B42318] border border-[#B42318] hover:bg-rose-50 text-xs font-semibold rounded-[12px] transition-colors cursor-pointer whitespace-nowrap"
                >
                  Huỷ đơn
                </button>
                <button
                  onClick={() => onPayDeposit(booking)}
                  type="button"
                  className="px-6 py-2.5 bg-[#15803D] hover:bg-[#166534] text-white font-semibold text-sm rounded-[12px] shadow-sm transition-all cursor-pointer whitespace-nowrap"
                >
                  {booking.upfront ? 'Thanh toán trả trước' : 'Thanh toán giữ chỗ ngay'}
                </button>
              </div>
            </div>
          )}

          {/* CONFIRMED HERO BANNER WITH CANCEL BUTTON */}
          {booking.status === 'CONFIRMED' && (
            <div className="p-5 bg-white border border-[#E6EBE8] rounded-[16px] shadow-[0_8px_24px_rgba(20,34,26,0.06)] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-[#14221A]">Chuyến xe đã được xác nhận</h2>
                <p className="text-xs text-gray-600 mt-1">
                  {booking.upfront
                    ? 'Lịch xe đã khóa. Bạn tự lái theo hợp đồng.'
                    : `Lịch xe đã khóa thành công. ${booking.driver?.name ? `Tài xế ${booking.driver.name} sẽ đón bạn đúng giờ.` : 'Tài xế sẽ được phân công trước giờ đón.'}`}
                </p>
              </div>
              <button
                onClick={() => setIsCancelModalOpen(true)}
                type="button"
                className="px-4 py-2 bg-white text-[#B42318] border border-[#B42318] hover:bg-rose-50 text-xs font-semibold rounded-[12px] transition-colors cursor-pointer whitespace-nowrap"
              >
                Huỷ đơn
              </button>
            </div>
          )}

          {/* CARD: VEHICLE INFO */}
          <div className="bg-white p-5 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
            <div className="flex flex-col sm:flex-row gap-5 items-center">
              <div className="w-full sm:w-[40%] aspect-[4/3] rounded-[12px] overflow-hidden bg-[#F7F8F6] shrink-0">
                <img
                  className="w-full h-full object-cover"
                  alt={booking.vehicle.name}
                  src={booking.vehicle.image}
                />
              </div>
              <div className="w-full sm:flex-1 flex flex-col justify-center">
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="text-lg font-bold text-[#14221A]">{booking.vehicle.name}</h3>
                </div>
                <p className="font-mono text-xs text-gray-500 mb-4 tracking-wider">
                  Biển số: {booking.vehicle.plateNumber}
                </p>
                <div className="flex flex-wrap gap-2">
                  <span className="px-3 py-1 rounded-full text-xs font-medium bg-[#F3F4F6] text-[#14221A]">
                    {booking.planTitle}
                  </span>
                  <span className="px-3 py-1 rounded-full text-xs font-medium bg-[#F3F4F6] text-[#14221A]">
                    {booking.slots} slot
                  </span>
                  <span className="px-3 py-1 rounded-full text-xs font-semibold bg-[#E8F6EE] text-[#15803D]">
                    {booking.totalAmount.toLocaleString('vi-VN')} đ
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* CARD: PICKUP DETAILS */}
          <div className="bg-white p-5 flex flex-col gap-4 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
            <h3 className="text-sm font-bold text-[#14221A] uppercase tracking-wider text-gray-500">
              Điểm đón
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {/* Address */}
              <div className="flex items-start gap-2.5 p-3 rounded-xl bg-[#F7F8F6]">
                <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 bg-[#E8F6EE] text-[#15803D]">
                  <span className="material-symbols-outlined text-[18px]">pin_drop</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-[#6D7A72]">
                    Địa chỉ đón
                  </span>
                  <span className="text-[13px] font-medium text-[#14221A] leading-snug mt-0.5">
                    {booking.pickupAddress}
                  </span>
                </div>
              </div>

              {/* Time */}
              <div className="flex items-start gap-2.5 p-3 rounded-xl bg-[#F7F8F6]">
                <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 bg-[#E8F6EE] text-[#15803D]">
                  <span className="material-symbols-outlined text-[18px]">schedule</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-[#6D7A72]">
                    Giờ hẹn đón
                  </span>
                  <span className="text-[13px] font-medium text-[#14221A] leading-snug mt-0.5">
                    {booking.formattedStart}
                  </span>
                </div>
              </div>

              {/* Passengers */}
              <div className="flex items-start gap-2.5 p-3 rounded-xl bg-[#F7F8F6]">
                <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 bg-[#E8F6EE] text-[#15803D]">
                  <span className="material-symbols-outlined text-[18px]">group</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="text-[11px] font-semibold uppercase tracking-wider text-[#6D7A72]">
                    Số lượng
                  </span>
                  <span className="text-[13px] font-medium text-[#14221A] leading-snug mt-0.5">
                    {booking.passengerCount} khách
                  </span>
                </div>
              </div>
            </div>

            <div className="pt-2 border-t border-[#E6EBE8] text-xs font-medium text-[#6D7A72]">
              Kết thúc dự kiến: {booking.formattedEnd}
            </div>
          </div>

          {/* CARD: DRIVER (if assigned) */}
          {booking.driver && (
            <div className="bg-white p-5 flex flex-col gap-3 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
              <h3 className="text-sm font-bold text-[#14221A]">Tài xế chuyên trách</h3>
              <div className="flex items-center gap-4">
                <img
                  className="w-12 h-12 rounded-full object-cover shrink-0 ring-1 ring-[#E6EBE8]"
                  alt={booking.driver.name}
                  src={booking.driver.avatar}
                />
                <div className="flex flex-col">
                  <span className="text-base font-bold text-[#14221A]">{booking.driver.name}</span>
                  <span className="text-xs font-mono text-[#6D7A72] mt-0.5">{booking.driver.phone}</span>
                  <span className="text-xs text-[#6D7A72] mt-0.5">
                    {Number(booking.driver.ratingAvg).toFixed(1)} / 5 ({booking.driver.ratingCount} đánh giá)
                  </span>
                </div>
                <div className="ml-auto">
                  <a
                    href={`tel:${booking.driver.phone}`}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#E8F6EE] text-[#15803D] text-xs font-semibold hover:bg-[#15803D] hover:text-white transition-colors"
                  >
                    <span className="material-symbols-outlined text-[16px]">call</span>
                    Gọi tài xế
                  </a>
                </div>
              </div>
            </div>
          )}

          {/* CARD: FEES TABLE */}
          <div className="bg-white p-5 flex flex-col gap-3 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
            <h3 className="text-sm font-bold text-[#14221A]">Khoản phí</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E6EBE8] text-[#6D7A72]">
                    <th className="py-2.5 px-3 font-semibold">Loại</th>
                    <th className="py-2.5 px-3 font-semibold">Mô tả</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Cách tính</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E6EBE8]">
                  {booking.charges.map((charge) => (
                    <tr key={`${charge.chargeType}-${charge.description}`}>
                      <td className="py-3 px-3 font-medium text-[#14221A]">{charge.label}</td>
                      <td className="py-3 px-3 text-[#6D7A72]">{charge.description}</td>
                      <td className="py-3 px-3 text-right font-mono text-[#6D7A72]">{charge.formula}</td>
                      <td className="py-3 px-3 text-right font-bold font-mono text-[#14221A]">
                        {charge.amount.toLocaleString('vi-VN')} đ
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {booking.disputeCategories.length > 0 && (
            <div className="bg-white p-5 flex flex-col gap-4 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
              <h3 className="text-sm font-bold text-[#14221A]">Khiếu nại</h3>
              <form onSubmit={handleComplaintSubmit} className="flex flex-col gap-3">
                <div className="flex flex-col gap-1.5">
                  <label className="text-xs font-semibold text-[#14221A]">Loại khiếu nại</label>
                  <select
                    value={complaintCategory}
                    onChange={(e) => setComplaintCategory(e.target.value)}
                    required
                    className="w-full h-11 px-3.5 rounded-xl text-sm bg-[#F7F8F6] border border-[#E6EBE8] outline-none cursor-pointer"
                  >
                    <option value="" disabled>-- Chọn loại khiếu nại --</option>
                    {booking.disputeCategories.map((category) => (
                      <option key={category} value={category}>{DISPUTE_LABELS[category]}</option>
                    ))}
                  </select>
                </div>

                {complaintCategory === 'CHARGE' && (
                  <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-semibold text-[#14221A]">Khoản phí</label>
                    <select
                      value={complaintCharge}
                      onChange={(e) => setComplaintCharge(e.target.value)}
                      required
                      className="w-full h-11 px-3.5 rounded-xl text-sm bg-[#F7F8F6] border border-[#E6EBE8] outline-none cursor-pointer"
                    >
                      <option value="" disabled>-- Chọn khoản --</option>
                      {booking.charges.map((charge) => (
                        <option key={charge.chargeType} value={charge.chargeType}>{charge.label}</option>
                      ))}
                    </select>
                  </div>
                )}

                {disputeNote && <p className="text-sm text-[#15803D]">{disputeNote}</p>}
                {actionError && <p className="text-sm text-[#B42318]">{actionError}</p>}

                <div className="flex justify-end pt-1">
                  <button
                    type="submit"
                    className="px-5 py-2 font-semibold text-xs border border-[#B42318] text-[#B42318] rounded-xl hover:bg-rose-50 transition-colors cursor-pointer active:scale-95"
                  >
                    Gửi khiếu nại
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>

        {/* Right Column (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-5 sticky top-24">
          {/* Timeline Card */}
          <div className="bg-white p-5 flex flex-col gap-4 rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
            <h3 className="text-sm font-bold text-[#14221A]">Tiến trình đơn</h3>

            <div className="flex flex-col gap-5 relative pl-6">
              {booking.timeline.map((item, idx) => (
                <div key={idx} className="relative flex items-start gap-3">
                  <div
                    className={`absolute -left-[23px] top-1.5 w-3 h-3 rounded-full shrink-0 ring-2 ${
                      item.isFailed
                        ? 'bg-[#B42318] ring-rose-100'
                        : item.completed
                        ? 'bg-[#15803D] ring-[#E8F6EE]'
                        : 'bg-gray-300 ring-gray-100'
                    }`}
                  />
                  <div className="flex flex-col">
                    <span
                      className={`text-xs sm:text-sm font-bold ${
                        item.isFailed ? 'text-[#B42318]' : 'text-[#14221A]'
                      }`}
                    >
                      {item.title}
                    </span>
                    <span className="font-mono text-[11px] text-gray-500 mt-0.5">
                      {item.timestamp}
                    </span>
                    {item.description && (
                      <p className="text-xs text-gray-600 mt-1">{item.description}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Navigation Card for Rejected/General */}
          {booking.status === 'REJECTED' && (
            <div className="rounded-[16px] bg-[#F7F8F6] border border-[#E6EBE8] p-5 shadow-xs flex flex-col gap-3">
              <p className="text-xs text-gray-700 leading-relaxed">
                Bạn có thể chọn lại xe đang mở bán.
              </p>
              <button
                onClick={onGoToCarPage}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-[#15803D] hover:underline cursor-pointer text-left"
              >
                Khám phá xe →
              </button>
            </div>
          )}

          {/* Vehicle Photo Showcase */}
          <div className="bg-white overflow-hidden flex flex-col rounded-[16px] border border-[#E6EBE8] shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
            <img
              className="w-full h-48 object-cover"
              alt={booking.vehicle.name}
              src={booking.vehicle.image}
            />
            <div className="p-4 flex items-center justify-between gap-3">
              <span className="font-bold text-sm text-[#14221A]">{booking.vehicle.name}</span>
              <span className="font-mono font-bold text-xs px-2.5 py-1 rounded-lg bg-[#F7F8F6] border border-[#E6EBE8] text-[#14221A]">
                {booking.vehicle.plateNumber}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Cancel Confirmation Dialog Modal */}
      <CancelBookingModal
        bookingId={booking.id}
        isOpen={isCancelModalOpen}
        onClose={() => setIsCancelModalOpen(false)}
        refundExplanation={booking.refundExplanation}
        onConfirmCancel={async () => {
          setActionError('');
          try {
            await onCancelBooking(booking.id);
            setIsCancelModalOpen(false);
          } catch (err) {
            setActionError(readErrorMessage(err));
          }
        }}
      />
    </div>
  );
};
