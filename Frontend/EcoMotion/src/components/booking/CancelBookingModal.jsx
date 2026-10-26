export const CancelBookingModal = ({
  bookingCode,
  isOpen,
  onClose,
  onConfirmCancel,
  refundExplanation,
}) => {
  if (!isOpen) return null;

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-white w-full rounded-lg p-5 flex flex-col relative shadow-md border border-slate-200 max-w-[440px]"
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
          {bookingCode}
        </div>

        <p className="text-sm text-[#14221A]/70 mb-5 leading-relaxed">
          {refundExplanation || 'Lý do được ghi là khách tự huỷ.'}
        </p>

        <div className="flex items-center gap-3">
          <button
            onClick={onClose}
            className="flex-1 h-[40px] rounded-lg bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-colors cursor-pointer"
            type="button"
          >
            Giữ đơn
          </button>
          <button
            onClick={() => onConfirmCancel(bookingCode)}
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
