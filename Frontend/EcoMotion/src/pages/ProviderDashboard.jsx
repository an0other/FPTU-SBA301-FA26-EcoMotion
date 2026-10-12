import { Link } from 'react-router-dom'
import ProviderLayout from '../components/provider/ProviderLayout'

export default function ProviderDashboard() {
  return (
    <ProviderLayout active="Tổng quan">
      <div className="max-w-4xl">
        <h1 className="text-2xl font-bold tracking-tight text-on-surface">Tổng quan nhà cung cấp</h1>
        <p className="mt-1 text-sm text-on-surface-variant">Chào mừng bạn quay lại hệ thống quản lý xe EcoMotion.</p>

        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Link
            to="/provider/bookings"
            className="flex items-center justify-between rounded-xl border border-outline-variant/35 bg-surface-container-lowest p-5 shadow-sm transition hover:border-primary hover:shadow-md"
          >
            <div>
              <h2 className="font-semibold text-on-surface">Hàng đợi đơn thuê (O07)</h2>
              <p className="mt-1 text-xs text-on-surface-variant">Xem danh sách, kiểm tra và duyệt các yêu cầu đặt xe.</p>
            </div>
            <span className="material-symbols-rounded text-primary">arrow_forward</span>
          </Link>
        </div>
      </div>
    </ProviderLayout>
  )
}

