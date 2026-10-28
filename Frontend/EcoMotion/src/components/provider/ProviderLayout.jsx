import ProviderHeader from './ProviderHeader'

export default function ProviderLayout({ active, children }) {
  const activeTabMap = {
    'Tổng quan': 'home',
    'Xe của tôi': 'vehicles',
    'Đội xe của tôi': 'vehicles',
    'Tài xế': 'drivers',
    'Đơn thuê': 'bookings',
    'Chặn lịch': 'schedules',
    'Tài chính': 'finance',
  }
  const activeTab = activeTabMap[active] || 'home'

  return (
    <div className="min-h-screen bg-[#f8f9ff] text-slate-800 font-sans">
      
      <ProviderHeader activeTab={activeTab} />

      <main className="w-full px-4 sm:px-6 lg:px-8 xl:px-12 py-8">
        {children}
      </main>

      <footer className="mt-12 border-t border-slate-200 bg-white py-6">
        <div className="w-full px-6 lg:px-8 xl:px-12 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <span>© 2026 EcoMotion VN. Nền tảng điều phối vận tải &amp; chia sẻ phương tiện di chuyển xanh.</span>
          <div className="flex items-center gap-6">
            <span className="hover:text-emerald-700 transition cursor-pointer">Quy chế hoạt động</span>
            <span className="hover:text-emerald-700 transition cursor-pointer">Chính sách đối tác</span>
            <span className="hover:text-emerald-700 transition cursor-pointer">Biểu phí</span>
          </div>
        </div>
      </footer>
    </div>
  )
}

