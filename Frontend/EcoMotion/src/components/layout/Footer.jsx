

import { Link } from 'react-router-dom'

export const Footer = () => {
  return (
    <footer className="w-full bg-white border-t border-[#E6EBE8] mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 pb-8 border-b border-[#E6EBE8]">
          {/* Column 1: Brand & Tagline */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-xl bg-[#E8F6EE] flex items-center justify-center text-[#15803D]">
                <span className="material-symbols-outlined text-[20px]">electric_car</span>
              </div>
              <span className="font-bold text-lg text-[#15803D]">EcoMotion</span>
            </div>
            <p className="text-xs sm:text-sm text-[#14221A]/70 leading-relaxed max-w-sm">
              Dịch vụ thuê xe thuần điện kèm tài xế chuyên trách hàng đầu Việt Nam. Nền tảng di chuyển xanh, văn minh và minh bạch cọc.
            </p>
          </div>

          {/* Column 2: Quick Links */}
          <div className="space-y-3">
            <h4 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-[#14221A]">
              Liên kết nhanh
            </h4>
            <ul className="space-y-2 text-xs sm:text-sm text-[#14221A]/70">
              <li>
                <Link to="/" className="hover:text-[#15803D] transition-colors">
                  Khám phá xe
                </Link>
              </li>
              <li>
                <Link to="/renter/bookings" className="hover:text-[#15803D] transition-colors">
                  Đơn của tôi
                </Link>
              </li>
              <li>
                <Link to="/policy" className="hover:text-[#15803D] transition-colors">
                  Chính sách cọc
                </Link>
              </li>
            </ul>
          </div>

          {/* Column 3: Support */}
          <div className="space-y-3">
            <h4 className="text-xs sm:text-sm font-bold uppercase tracking-wider text-[#14221A]">
              Hỗ trợ khách hàng
            </h4>
            <p className="text-xs sm:text-sm text-[#14221A]/70">Hotline tư vấn và tiếp nhận (24/7):</p>
            <a
              href="tel:19008899"
              className="text-xl font-bold text-[#15803D] hover:underline block"
            >
              1900 8899
            </a>
            <p className="text-xs text-[#14221A]/50">Hỗ trợ 24/7 trong suốt hành trình di chuyển</p>
          </div>
        </div>

        <div className="pt-6 flex flex-col sm:flex-row items-center justify-between text-xs text-[#14221A]/60 gap-2">
          <p>© 2026 EcoMotion Vietnam. Bảo lưu mọi quyền.</p>
          <p>Hà Nội • TP. Hồ Chí Minh • Đà Nẵng</p>
        </div>
      </div>
    </footer>
  );
};
