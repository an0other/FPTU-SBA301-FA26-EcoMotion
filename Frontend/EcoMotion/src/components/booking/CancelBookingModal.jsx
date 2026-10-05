import { useState } from 'react';


export const CancelBookingModal = ({
  bookingId,
  isOpen,
  onClose,
  onConfirmCancel,
}) => {
  const [reason, setReason] = useState('Tôi muốn huỷ');

  if (!isOpen) return null;

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 backdrop-blur-sm bg-[#14221A]/45 animate-in fade-in duration-200"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-white w-full rounded-[20px] p-6 flex flex-col relative shadow-[0_24px_48px_rgba(20,34,26,0.18)] max-w-[440px] animate-in zoom-in-95 duration-200"
      >
        <div className="flex items-center justify-between mb-4">
          <div className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-[#FEE4E2]">
            <span className="material-symbols-outlined text-[22px] text-[#B42318]">warning</span>
          </div>
          <button
            aria-label="Đóng"
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-[#F3F4F6] text-[#14221A] hover:bg-gray-200 flex items-center justify-center transition-colors cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        <h3 className="text-xl font-semibold text-[#14221A] leading-tight mb-2">Huỷ đơn này?</h3>

        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#E8F6EE] text-[#15803D] font-mono text-xs font-semibold w-max mb-3">
          <span className="w-1.5 h-1.5 rounded-full bg-[#15803D]"></span>
          {bookingId}
        </div>

        <p className="text-sm text-[#14221A]/70 mb-5 leading-relaxed">
          Đơn đã thanh toán cọc. Số hoàn lại tính theo thời điểm huỷ.
        </p>

        <div className="flex flex-col gap-2 mb-6">
          <label className="text-sm font-medium text-[#14221A]" htmlFor="cancel-reason-select">
            Lý do
          </label>
          <div className="relative">
            <select
              id="cancel-reason-select"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full bg-[#F7F8F6] border border-[#E6EBE8] rounded-[12px] h-[44px] px-3.5 pr-10 text-[#14221A] text-sm focus:border-[#15803D] focus:ring-1 focus:ring-[#15803D] appearance-none cursor-pointer outline-none"
            >
              <option value="Tôi muốn huỷ">Tôi muốn huỷ</option>
              <option value="Thay đổi kế hoạch di chuyển">Thay đổi kế hoạch di chuyển</option>
              <option value="Muốn đổi sang dòng xe khác">Muốn đổi sang dòng xe khác</option>
              <option value="Khác">Lý do khác</option>
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-3 text-[#14221A]/60">
              <span className="material-symbols-outlined text-[20px]">expand_more</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={onClose}
            className="flex-1 h-[44px] rounded-[12px] bg-white border border-[#E6EBE8] hover:bg-[#F7F8F6] text-[#14221A] text-sm font-semibold transition-colors cursor-pointer"
            type="button"
          >
            Giữ đơn
          </button>
          <button
            onClick={() => onConfirmCancel(bookingId, reason)}
            className="flex-1 h-[44px] rounded-[12px] bg-[#B42318] text-white text-sm font-semibold hover:bg-red-700 transition-colors flex items-center justify-center gap-1.5 shadow-xs cursor-pointer active:scale-98"
            type="button"
          >
            Xác nhận huỷ
          </button>
        </div>
      </div>
    </div>
  );
};
