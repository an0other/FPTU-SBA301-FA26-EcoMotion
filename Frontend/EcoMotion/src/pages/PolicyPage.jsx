

export const PolicyPage = ({ onBookNow }) => {
  return (
    <div className="w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Title */}
      <div className="text-center space-y-2 max-w-2xl mx-auto">
        <span className="inline-block px-3 py-1 rounded-full bg-[#E8F6EE] text-[#15803D] text-xs font-semibold uppercase tracking-wider">
          Chính sách minh bạch
        </span>
        <h1 className="text-2xl sm:text-3xl font-bold text-[#14221A]">
          Quy chế Đặt cọc & Hoàn hủy EcoMotion
        </h1>
        <p className="text-sm text-gray-600">
          Cam kết bảo vệ quyền lợi khách hàng, minh bạch chi phí và hoàn cọc tự động.
        </p>
      </div>

      {/* Grid of Policies */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white p-6 rounded-[16px] border border-[#E6EBE8] shadow-xs space-y-3">
          <div className="w-10 h-10 rounded-full bg-[#E8F6EE] text-[#15803D] flex items-center justify-center">
            <span className="material-symbols-outlined text-[22px]">verified</span>
          </div>
          <h3 className="font-bold text-base text-[#14221A]">Chưa thu cọc khi chưa duyệt</h3>
          <p className="text-xs text-gray-600 leading-relaxed">
            Bạn chỉ phải thanh toán 30% tiền cọc giữ chỗ sau khi chủ xe hoặc hệ thống EcoMotion duyệt chấp nhận yêu cầu của bạn.
          </p>
        </div>

        <div className="bg-white p-6 rounded-[16px] border border-[#E6EBE8] shadow-xs space-y-3">
          <div className="w-10 h-10 rounded-full bg-[#E8F6EE] text-[#15803D] flex items-center justify-center">
            <span className="material-symbols-outlined text-[22px]">published_with_changes</span>
          </div>
          <h3 className="font-bold text-base text-[#14221A]">Hoàn cọc 100% trước 24h</h3>
          <p className="text-xs text-gray-600 leading-relaxed">
            Nếu quý khách hủy chuyến trước 24 giờ kể từ thời điểm khởi hành, số tiền cọc sẽ được tự động hoàn 100% về tài khoản.
          </p>
        </div>

        <div className="bg-white p-6 rounded-[16px] border border-[#E6EBE8] shadow-xs space-y-3">
          <div className="w-10 h-10 rounded-full bg-[#E8F6EE] text-[#15803D] flex items-center justify-center">
            <span className="material-symbols-outlined text-[22px]">support_agent</span>
          </div>
          <h3 className="font-bold text-base text-[#14221A]">Hỗ trợ khiếu nại 24/7</h3>
          <p className="text-xs text-gray-600 leading-relaxed">
            Mọi thắc mắc về chất lượng dịch vụ hoặc tài xế được CSKH xử lý trực tiếp qua Hotline miễn cước <strong>1900 8899</strong>.
          </p>
        </div>
      </div>

      {/* CTA Box */}
      <div className="bg-[#15803D] text-white p-8 rounded-[20px] shadow-md flex flex-col sm:flex-row items-center justify-between gap-6">
        <div className="space-y-1 text-center sm:text-left">
          <h2 className="text-xl font-bold">Sẵn sàng trải nghiệm di chuyển xanh?</h2>
          <p className="text-xs sm:text-sm text-white/90">
            Đặt xe ngay hôm nay với mức giá ưu đãi kèm tài xế chuyên trách.
          </p>
        </div>
        <button
          onClick={onBookNow}
          className="px-6 py-3 rounded-xl bg-white text-[#15803D] font-bold text-sm hover:bg-emerald-50 transition-all shadow-xs cursor-pointer active:scale-95 shrink-0"
        >
          Khám phá xe ngay
        </button>
      </div>
    </div>
  );
};
