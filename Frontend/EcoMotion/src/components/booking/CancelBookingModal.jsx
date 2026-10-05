export const CancelBookingModal = ({
  bookingId,
  isOpen,
  onClose,
  onConfirmCancel,
  refundExplanation,
}) => {
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
          {refundExplanation || 'Lý do được ghi là khách tự huỷ.'}
        </p>

        <div className="flex items-center gap-3">
          <button
            onClick={onClose}
            className="flex-1 h-[44px] rounded-[12px] bg-white border border-[#E6EBE8] hover:bg-[#F7F8F6] text-[#14221A] text-sm font-semibold transition-colors cursor-pointer"
            type="button"
          >
            Giữ đơn
          </button>
          <button
            onClick={() => onConfirmCancel(bookingId)}
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
