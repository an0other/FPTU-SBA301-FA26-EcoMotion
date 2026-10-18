import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { providerService } from '../../services/providerService'
import ProviderHeader from '../../components/provider/ProviderHeader'

export default function ProviderHomePage() {
  const { user } = useAuth()
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    async function fetchSummary() {
      try {
        setLoading(true)
        setError(null)
        const data = await providerService.getDashboardSummary()
        setSummary(data)
      } catch (err) {
        console.error('Failed to load dashboard summary:', err)
        setError('Không thể tải dữ liệu thống kê từ hệ thống.')
      } finally {
        setLoading(false)
      }
    }
    fetchSummary()
  }, [])

  const providerName = summary?.providerName || user?.fullName || 'Nhà xe đối tác'
  const depotAddress = summary?.depotAddress || '120 Phổ Quang, Q. Tân Bình, TP.HCM'
  const hotline = summary?.hotline || '1900 6868'

  const pendingCount = summary?.pendingVehicles || 0
  const hiddenCount = summary?.hiddenVehicles || 0
  const activeBlocks = summary?.activeBlocks || 0
  const totalDrivers = summary?.totalDrivers || 0
  const totalVehicles = summary?.totalVehicles || 0
  const approvedCount = summary?.approvedVehicles || 0
  const rejectedCount = Math.max(0, totalVehicles - pendingCount - approvedCount - hiddenCount)

  const notices = []

  if (rejectedCount > 0) {
    notices.push({
      id: 'rejected',
      icon: 'error',
      boxClass: 'bg-rose-50 border-rose-200',
      iconColor: 'text-rose-700',
      titleColor: 'text-rose-800',
      descColor: 'text-rose-700',
      linkColor: 'text-rose-800',
      title: `${rejectedCount} xe bị từ chối duyệt hồ sơ`,
      description: 'Hồ sơ phương tiện chưa đạt tiêu chuẩn kiểm duyệt. Vui lòng kiểm tra lại thông tin và ảnh giấy tờ xe.',
      linkTo: '/provider/vehicles?status=REJECTED',
      actionText: 'Cập nhật hồ sơ xe',
    })
  }

  if (pendingCount > 0) {
    notices.push({
      id: 'pending',
      icon: 'pending_actions',
      boxClass: 'bg-amber-50 border-amber-200',
      iconColor: 'text-amber-700',
      titleColor: 'text-amber-800',
      descColor: 'text-amber-700',
      linkColor: 'text-amber-800',
      title: `${pendingCount} xe đang chờ Quản trị viên duyệt`,
      description: 'Phương tiện mới đăng ký đang được Ban quản trị kiểm tra hồ sơ trước khi đưa vào vận hành thực tế.',
      linkTo: '/provider/vehicles?status=PENDING',
      actionText: 'Xem danh sách xe',
    })
  }

  if (hiddenCount > 0) {
    notices.push({
      id: 'hidden',
      icon: 'visibility_off',
      boxClass: 'bg-slate-50 border-slate-200',
      iconColor: 'text-slate-700',
      titleColor: 'text-[#0b1c30]',
      descColor: 'text-slate-600',
      linkColor: 'text-emerald-700',
      title: `${hiddenCount} xe đang tạm dừng nhận khách`,
      description: 'Xe ở trạng thái ẩn (HIDDEN), không xuất hiện trên ứng dụng khách. Bạn có thể bật nhận khách lại bất cứ lúc nào.',
      linkTo: '/provider/vehicles?status=HIDDEN',
      actionText: 'Quản lý hiển thị xe',
    })
  }

  if (activeBlocks > 0) {
    notices.push({
      id: 'blocks',
      icon: 'event_busy',
      boxClass: 'bg-sky-50 border-sky-200',
      iconColor: 'text-sky-700',
      titleColor: 'text-sky-900',
      descColor: 'text-sky-700',
      linkColor: 'text-sky-800',
      title: `${activeBlocks} lịch chặn / bảo trì đang kích hoạt`,
      description: 'Đội xe đang có khung giờ chặn lịch (BLOCKED / MAINTENANCE) để bảo dưỡng hoặc nghỉ ngơi.',
      linkTo: '/provider/vehicles',
      actionText: 'Xem lịch trình xe',
    })
  }

  if (totalDrivers === 0) {
    notices.push({
      id: 'drivers',
      icon: 'person_add',
      boxClass: 'bg-purple-50 border-purple-200',
      iconColor: 'text-purple-700',
      titleColor: 'text-purple-900',
      descColor: 'text-purple-700',
      linkColor: 'text-purple-800',
      title: 'Chưa có tài xế nào trong đội xe',
      description: 'Mô hình thuê xe kèm tài xế cần có tài xế nhận chuyến. Hãy tạo tài khoản tài xế hộ để sẵn sàng nhận chuyến.',
      linkTo: '/provider/drivers',
      actionText: 'Tạo tài xế hộ',
    })
  }

  return (
    <div className="min-h-screen bg-[#f8f9ff] text-[#334155] font-sans">
      <ProviderHeader
        activeTab="home"
        providerName={providerName}
        depotAddress={depotAddress}
      />
      {/* Main Content */}
      <main className="w-full">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 py-8">
          <div className="flex flex-col w-full gap-8">
            {/* Top Section: Provider Header and Quick Actions */}
            <section className="bg-white rounded-xl p-6 sm:p-8 shadow-sm border border-slate-200 flex flex-col lg:flex-row lg:items-center justify-between gap-6">
              <div className="flex flex-col gap-2">
                <div className="flex flex-wrap items-center gap-3">
                  <h1 className="text-[28px] sm:text-[30px] font-bold text-[#0b1c30] tracking-tight">
                    Xin chào, {providerName}
                  </h1>
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-[11px] font-semibold border border-emerald-200">
                    <span className="material-symbols-outlined text-[16px]">verified</span>
                    <span>Nhà xe đối tác xác thực</span>
                  </div>
                </div>

                <p className="text-[14px] text-slate-500 flex flex-wrap items-center gap-y-1">
                  <span className="inline-flex items-center gap-1 mr-3">
                    <span className="material-symbols-outlined text-[18px] text-emerald-600">pin_drop</span>
                    Kho xe: {depotAddress}
                  </span>
                  <span className="hidden sm:inline text-slate-300 mr-3">•</span>
                  <span className="inline-flex items-center gap-1">
                    <span className="material-symbols-outlined text-[18px] text-emerald-600">headset_mic</span>
                    Hotline: {hotline}
                  </span>
                </p>
              </div>

              {/* Quick CTAs */}
              <div className="flex flex-wrap items-center gap-3 shrink-0">
                <Link
                  to="/provider/drivers"
                  className="inline-flex items-center gap-2 bg-white hover:bg-slate-50 text-[#0b1c30] border border-slate-200 px-4 py-2.5 rounded-xl text-[14px] font-semibold shadow-xs transition-all duration-200"
                >
                  <span className="material-symbols-outlined text-slate-600 text-[20px]">person_add</span>
                  <span>Tạo tài xế hộ</span>
                </Link>
                <Link
                  to="/provider/vehicles/new"
                  className="inline-flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2.5 rounded-xl text-[14px] font-semibold shadow-md shadow-emerald-600/20 transition-all duration-200"
                >
                  <span className="material-symbols-outlined text-[20px]">add_circle</span>
                  <span>Thêm xe mới</span>
                </Link>
              </div>
            </section>

            {/* Error Banner */}
            {error && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 flex items-center justify-between">
                <span>{error}</span>
                <button
                  type="button"
                  onClick={() => window.location.reload()}
                  className="font-semibold underline hover:text-red-900"
                >
                  Tải lại
                </button>
              </div>
            )}

            {/* Quick Counters Grid (6 Stat Cards) */}
            <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
              {/* Card 1: Tổng số xe */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Tổng số xe</span>
                  <div className="w-10 h-10 rounded-xl bg-slate-100 text-emerald-700 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[22px]">directions_car</span>
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-[#0b1c30]">
                      {loading ? '—' : (summary?.totalVehicles ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">xe</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Quy mô toàn đội xe</p>
                </div>
              </div>

              {/* Card 2: Xe chờ duyệt */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Xe chờ duyệt</span>
                  <span className="px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-[11px] font-semibold">
                    Chờ duyệt
                  </span>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-amber-700">
                      {loading ? '—' : (summary?.pendingVehicles ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">xe</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Đang chờ Admin duyệt</p>
                </div>
              </div>

              {/* Card 3: Xe đã duyệt */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Xe đã duyệt</span>
                  <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-[11px] font-semibold">
                    Hoạt động
                  </span>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-emerald-700">
                      {loading ? '—' : (summary?.approvedVehicles ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">xe</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Sẵn sàng nhận khách</p>
                </div>
              </div>

              {/* Card 4: Xe đang ẩn */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Xe đang ẩn</span>
                  <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-300 text-[11px] font-semibold">
                    Đang ẩn
                  </span>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-[#0b1c30]">
                      {loading ? '—' : (summary?.hiddenVehicles ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">xe</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Tạm dừng hiển thị</p>
                </div>
              </div>

              {/* Card 5: Tổng tài xế */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Tổng tài xế</span>
                  <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-700 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[22px]">badge</span>
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-[#0b1c30]">
                      {loading ? '—' : (summary?.totalDrivers ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">tài xế</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Trực thuộc đội xe</p>
                </div>
              </div>

              {/* Card 6: Lịch đang chặn */}
              <div className="bg-white rounded-xl p-5 shadow-xs border border-slate-200 flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-[12px] font-medium text-slate-500">Lịch đang chặn</span>
                  <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-700 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[22px]">event_busy</span>
                  </div>
                </div>
                <div>
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[28px] font-bold text-rose-700">
                      {loading ? '—' : (summary?.activeBlocks ?? 0)}
                    </span>
                    <span className="text-[13px] text-slate-500">lịch</span>
                  </div>
                  <p className="text-[11px] font-medium text-slate-500 mt-1">Bảo dưỡng hoặc bận</p>
                </div>
              </div>
            </section>

            {/* Attention & Action Center: Left (7 Cols) and Right (5 Cols) */}
            <section className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Left Column: Notices requiring attention (7 cols) */}
              <div className="lg:col-span-7 bg-white rounded-xl p-6 sm:p-7 shadow-xs border border-slate-200 flex flex-col gap-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <div className={`w-2.5 h-2.5 rounded-full ${notices.length > 0 ? 'bg-amber-500' : 'bg-emerald-600'}`}></div>
                    <h2 className="text-[18px] font-bold text-[#0b1c30]">Thông báo cần chú ý</h2>
                  </div>
                  <span className={`text-[11px] font-semibold px-2.5 py-0.5 rounded-full border ${
                    notices.length > 0 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  }`}>
                    {notices.length > 0 ? `${notices.length} việc cần lưu ý` : 'Hệ thống ổn định'}
                  </span>
                </div>

                <div className="flex flex-col gap-3.5">
                  {notices.length > 0 ? (
                    notices.map((notice) => (
                      <div
                        key={notice.id}
                        className={`${notice.boxClass} border rounded-xl p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4`}
                      >
                        <div className="flex items-start gap-3">
                          <span className={`material-symbols-outlined ${notice.iconColor} text-[24px] shrink-0 mt-0.5`}>
                            {notice.icon}
                          </span>
                          <div className="flex flex-col">
                            <span className={`text-[14px] font-bold ${notice.titleColor}`}>
                              {notice.title}
                            </span>
                            <p className={`text-[13px] ${notice.descColor} mt-0.5`}>
                              {notice.description}
                            </p>
                          </div>
                        </div>
                        <Link
                          to={notice.linkTo}
                          className={`inline-flex items-center gap-1.5 text-[12px] font-bold ${notice.linkColor} hover:underline whitespace-nowrap self-end sm:self-center shrink-0`}
                        >
                          <span>{notice.actionText}</span>
                          <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                        </Link>
                      </div>
                    ))
                  ) : (
                    <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                      <div className="flex items-start gap-3">
                        <span className="material-symbols-outlined text-emerald-700 text-[26px] shrink-0 mt-0.5">verified</span>
                        <div className="flex flex-col">
                          <span className="text-[14px] font-bold text-emerald-800">Tất cả phương tiện đang vận hành bình thường</span>
                          <p className="text-[13px] text-emerald-700 mt-0.5">
                            Không có cảnh báo khẩn cấp, không có xe bị từ chối hay chờ duyệt. Toàn bộ xe đã sẵn sàng tiếp nhận khách hàng.
                          </p>
                        </div>
                      </div>
                      <Link
                        to="/provider/vehicles"
                        className="inline-flex items-center gap-1.5 text-[12px] font-bold text-emerald-800 hover:underline whitespace-nowrap self-end sm:self-center shrink-0"
                      >
                        <span>Xem đội xe</span>
                        <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                      </Link>
                    </div>
                  )}
                </div>
              </div>

              {/* Right Column: Shortcuts (5 cols) */}
              <div className="lg:col-span-5 bg-white rounded-xl p-6 sm:p-7 shadow-xs border border-slate-200 flex flex-col gap-5">
                <div className="flex items-center justify-between">
                  <h2 className="text-[18px] font-bold text-[#0b1c30]">Lối tắt quản lý</h2>
                  <span className="material-symbols-outlined text-slate-400 text-[20px]">tune</span>
                </div>

                <div className="flex flex-col gap-3">
                  {/* Shortcut 1: Đội xe */}
                  <Link
                    to="/provider/vehicles"
                    className="group p-4 rounded-xl bg-slate-50 hover:bg-slate-100/80 transition-colors flex items-center justify-between gap-4 border border-slate-100"
                  >
                    <div className="flex items-center gap-3.5">
                      <div className="w-11 h-11 rounded-xl bg-white text-emerald-700 flex items-center justify-center shrink-0 shadow-xs group-hover:scale-105 transition-transform">
                        <span className="material-symbols-outlined text-[24px]">garage</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[14px] font-bold text-[#0b1c30] group-hover:text-emerald-700 transition-colors">
                          Đội xe của tôi
                        </span>
                        <span className="text-[13px] text-slate-500 line-clamp-1">
                          Quản lý danh sách, duyệt hồ sơ xe &amp; bật/tắt hiển thị
                        </span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-slate-400 group-hover:text-emerald-700 group-hover:translate-x-1 transition-all text-[20px]">
                      chevron_right
                    </span>
                  </Link>

                  {/* Shortcut 2: Đội ngũ tài xế */}
                  <Link
                    to="/provider/drivers"
                    className="group p-4 rounded-xl bg-slate-50 hover:bg-slate-100/80 transition-colors flex items-center justify-between gap-4 border border-slate-100"
                  >
                    <div className="flex items-center gap-3.5">
                      <div className="w-11 h-11 rounded-xl bg-white text-emerald-700 flex items-center justify-center shrink-0 shadow-xs group-hover:scale-105 transition-transform">
                        <span className="material-symbols-outlined text-[24px]">group</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[14px] font-bold text-[#0b1c30] group-hover:text-emerald-700 transition-colors">
                          Đội ngũ tài xế
                        </span>
                        <span className="text-[13px] text-slate-500 line-clamp-1">
                          Danh sách tài xế thuộc nhà xe &amp; hỗ trợ tạo tài khoản hộ
                        </span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-slate-400 group-hover:text-emerald-700 group-hover:translate-x-1 transition-all text-[20px]">
                      chevron_right
                    </span>
                  </Link>

                  {/* Shortcut 3: Chặn lịch xe */}
                  <Link
                    to="/provider/vehicles"
                    className="group p-4 rounded-xl bg-slate-50 hover:bg-slate-100/80 transition-colors flex items-center justify-between gap-4 border border-slate-100"
                  >
                    <div className="flex items-center gap-3.5">
                      <div className="w-11 h-11 rounded-xl bg-white text-emerald-700 flex items-center justify-center shrink-0 shadow-xs group-hover:scale-105 transition-transform">
                        <span className="material-symbols-outlined text-[24px]">calendar_month</span>
                      </div>
                      <div className="flex flex-col">
                        <span className="text-[14px] font-bold text-[#0b1c30] group-hover:text-emerald-700 transition-colors">
                          Chặn lịch xe
                        </span>
                        <span className="text-[13px] text-slate-500 line-clamp-1">
                          Thiết lập lịch bảo dưỡng định kỳ hoặc khóa xe bận
                        </span>
                      </div>
                    </div>
                    <span className="material-symbols-outlined text-slate-400 group-hover:text-emerald-700 group-hover:translate-x-1 transition-all text-[20px]">
                      chevron_right
                    </span>
                  </Link>
                </div>
              </div>
            </section>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full bg-white py-8 border-t border-slate-200 mt-12">
        <div className="max-w-7xl mx-auto px-6 lg:px-12 flex flex-col sm:flex-row items-center justify-between gap-4 text-[13px] text-slate-500">
          <div>© 2026 EcoMotion VN. Nền tảng chia sẻ và quản trị phương tiện di chuyển xanh.</div>
          <div className="flex items-center gap-6">
            <span className="hover:text-emerald-700 transition-colors cursor-pointer">Quy chuẩn vận hành</span>
            <span className="hover:text-emerald-700 transition-colors cursor-pointer">Hỗ trợ trạm depot</span>
            <span className="hover:text-emerald-700 transition-colors cursor-pointer">Chính sách bảo mật</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
