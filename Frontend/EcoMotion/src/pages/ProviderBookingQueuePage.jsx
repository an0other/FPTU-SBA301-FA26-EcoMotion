import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { bookingService } from '../services/bookingService'
import { useAuth } from '../hooks/useAuth'
import { IconCar } from '../components/icons/Icons'

const STATUS_FILTERS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ duyệt' },
  { key: 'ACCEPTED', label: 'Đã chấp nhận' },
  { key: 'CONFIRMED', label: 'Đã xác nhận' },
  { key: 'IN_PROGRESS', label: 'Đang di chuyển' },
  { key: 'COMPLETED', label: 'Đã hoàn thành' },
  { key: 'REJECTED', label: 'Đã từ chối' },
  { key: 'CANCELLED', label: 'Đã hủy' },
]

const STATUS_BADGE_STYLES = {
  PENDING: 'bg-amber-100 text-amber-800 border-amber-200',
  ACCEPTED: 'bg-blue-100 text-blue-800 border-blue-200',
  CONFIRMED: 'bg-indigo-100 text-indigo-800 border-indigo-200',
  IN_PROGRESS: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  COMPLETED: 'bg-slate-100 text-slate-700 border-slate-200',
  REJECTED: 'bg-red-100 text-red-800 border-red-200',
  CANCELLED: 'bg-rose-100 text-rose-700 border-rose-200',
}

const STATUS_LABELS = {
  PENDING: 'Chờ duyệt',
  ACCEPTED: 'Đã chấp nhận',
  CONFIRMED: 'Đã xác nhận',
  IN_PROGRESS: 'Đang di chuyển',
  COMPLETED: 'Đã hoàn thành',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã hủy',
}

export default function ProviderBookingQueuePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [activeFilter, setActiveFilter] = useState('ALL')
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchBookings(activeFilter)
  }, [activeFilter])

  async function fetchBookings(status) {
    setLoading(true)
    setError(null)
    try {
      const data = await bookingService.getProviderBookings(status)
      setBookings(data || [])
    } catch (err) {
      setError(err.message || 'Không thể tải danh sách booking. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  function formatDateTime(isoString) {
    if (!isoString) return '-'
    try {
      const date = new Date(isoString)
      return date.toLocaleString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  function formatCurrency(amount) {
    if (amount == null) return '0 ₫'
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
    }).format(amount)
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Top Header */}
      <header className="flex items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-3">
          <Link to="/provider" className="flex items-center gap-2 text-lg font-extrabold tracking-tight text-slate-900">
            <IconCar className="h-5 w-5 text-emerald-600" />
            EcoMotion <span className="text-[11px] font-semibold text-emerald-600">Provider</span>
          </Link>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/me"
            className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
          >
            Trang cá nhân
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
          >
            Đăng xuất
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Danh sách đơn đặt xe (Booking Queue)</h1>
            <p className="text-sm text-slate-500">Quản lý và tiếp nhận các yêu cầu đặt xe từ khách hàng.</p>
          </div>
        </div>

        {/* Filter Tabs */}
        <div className="mb-6 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
          {STATUS_FILTERS.map((filter) => {
            const isActive = activeFilter === filter.key
            return (
              <button
                key={filter.key}
                type="button"
                onClick={() => setActiveFilter(filter.key)}
                className={`rounded-full px-4 py-1.5 text-xs font-medium transition ${
                  isActive
                    ? 'bg-emerald-600 text-white shadow-sm'
                    : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-100'
                }`}
              >
                {filter.label}
              </button>
            )
          })}
        </div>

        {/* Error State */}
        {error && (
          <div className="mb-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <div className="flex items-center justify-between">
              <span>{error}</span>
              <button
                type="button"
                onClick={() => fetchBookings(activeFilter)}
                className="rounded-md bg-red-100 px-3 py-1 text-xs font-semibold text-red-800 hover:bg-red-200"
              >
                Thử lại
              </button>
            </div>
          </div>
        )}

        {/* Loading State */}
        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((idx) => (
              <div key={idx} className="animate-pulse rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="mb-3 h-4 w-1/2 rounded bg-slate-200" />
                <div className="mb-2 h-5 w-3/4 rounded bg-slate-200" />
                <div className="mb-2 h-4 w-2/3 rounded bg-slate-200" />
                <div className="mt-4 h-6 w-1/3 rounded bg-slate-200" />
              </div>
            ))}
          </div>
        ) : bookings.length === 0 ? (
          /* Empty State */
          <div className="rounded-xl border border-dashed border-slate-300 bg-white p-12 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
              <IconCar className="h-6 w-6" />
            </div>
            <h3 className="text-base font-semibold text-slate-800">Không tìm thấy đơn đặt xe</h3>
            <p className="mt-1 text-sm text-slate-500">
              Hiện chưa có đơn đặt xe nào {activeFilter !== 'ALL' ? `ở trạng thái "${STATUS_FILTERS.find(f => f.key === activeFilter)?.label}"` : ''}.
            </p>
          </div>
        ) : (
          /* Booking Grid List */
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {bookings.map((booking) => (
              <div
                key={booking.bookingCode}
                onClick={() => navigate(`/provider/bookings/${booking.bookingCode}`)}
                className="group flex flex-col justify-between rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-emerald-300 hover:shadow-md cursor-pointer"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-3">
                    <span className="font-mono text-xs font-bold text-slate-500">
                      #{booking.bookingCode}
                    </span>
                    <span
                      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-semibold ${
                        STATUS_BADGE_STYLES[booking.status] || 'bg-slate-100 text-slate-700 border-slate-200'
                      }`}
                    >
                      {STATUS_LABELS[booking.status] || booking.status}
                    </span>
                  </div>

                  <div className="mt-3 space-y-2">
                    <div>
                      <h4 className="text-base font-bold text-slate-900 group-hover:text-emerald-700 transition">
                        {booking.vehicleName || 'Xe không tên'}
                      </h4>
                      <p className="text-xs text-slate-500">Biển số: {booking.licensePlate || 'Chưa cập nhật'}</p>
                    </div>

                    <div className="text-xs text-slate-600">
                      <span className="font-medium text-slate-400">Khách hàng: </span>
                      <span className="font-semibold text-slate-800">{booking.renterName || 'Ẩn danh'}</span>
                    </div>

                    <div className="text-xs text-slate-600">
                      <span className="font-medium text-slate-400">Thời gian nhận: </span>
                      <span>{formatDateTime(booking.pickupTime)}</span>
                    </div>

                    <div className="text-xs text-slate-600 line-clamp-2">
                      <span className="font-medium text-slate-400">Địa điểm: </span>
                      <span>{booking.pickupAddress || '-'}</span>
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3">
                  <span className="text-xs font-medium text-slate-400">Tổng tiền</span>
                  <span className="text-base font-bold text-emerald-600">
                    {formatCurrency(booking.totalAmount)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
