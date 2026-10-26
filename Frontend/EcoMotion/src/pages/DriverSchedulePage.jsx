import { useState } from 'react'
import DriverLayout from '../components/driver/DriverLayout'
import ActiveTripBanner from '../components/driver/ActiveTripBanner'
import DriverTripCard from '../components/driver/DriverTripCard'
import TripListSkeleton from '../components/driver/TripListSkeleton'
import DriverRejectTripModal from '../components/driver/DriverRejectTripModal'
import Toast from '../components/Toast'
import { useDriverSchedule } from '../hooks/useDriverSchedule'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { driverTripService } from '../services/driverTripService'
import {
  STATUS_FILTER_OPTIONS,
  TRIP_VIEWS,
} from '../../src/utils/driverTripUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

export default function DriverSchedulePage() {
  const {
    view,
    status,
    date,
    page,
    size,
    trips,
    activeTrip,
    total,
    loading,
    error,
    setView,
    setStatus,
    setDate,
    setPage,
    refetch,
  } = useDriverSchedule()

  const [dateDraft, setDateDraft] = useState(date || '')
  const [rejectingTrip, setRejectingTrip] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.DRIVER_ASSIGNED]: () => {
        refetch()
        setToast({ message: 'Bạn có một chuyến đi mới vừa được phân công!', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_REASSIGNED]: () => {
        refetch()
        setToast({ message: 'Thông báo: Có thay đổi phân công lịch trình.', type: 'warning' })
      },
    },
    onResync: () => refetch(),
  })

  const totalPages = Math.ceil(total / size) || 1

  const handleDateChange = (e) => {
    const val = e.target.value
    setDateDraft(val)
    setDate(val || null)
  }

  const handleClearDate = () => {
    setDateDraft('')
    setDate(null)
  }

  // Confirm assigned trip directly from card
  const handleConfirmTrip = async (trip) => {
    setActionLoading(true)
    try {
      const tripId = trip.tripId || trip.bookingCode
      await driverTripService.confirmTrip(tripId)
      setToast({ message: 'Đã xác nhận nhận chuyến thành công! Vui lòng chuẩn bị di chuyển.', type: 'success' })
      refetch()
    } catch (err) {
      setToast({ message: err?.response?.data?.message || err?.message || 'Không thể xác nhận chuyến', type: 'error' })
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <DriverLayout activeTab="schedule">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />

      {/* Top Banner & Header info */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
            Lịch trình tác nghiệp
          </h1>
          <p className="mt-0.5 text-xs text-slate-500">
            Theo dõi danh sách cuốc xe và tiếp nhận chuyến được phân công
          </p>
        </div>

        <button
          type="button"
          onClick={() => refetch()}
          disabled={loading}
          aria-label="Làm mới lịch trình"
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-xs transition hover:bg-slate-50 disabled:opacity-50"
        >
          <Icon name="refresh" className={`text-[16px] ${loading ? 'animate-spin' : ''}`} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* Active Trip Banner */}
      <ActiveTripBanner trip={activeTrip} />

      {/* View Tabs: TODAY | UPCOMING | HISTORY */}
      <div className="mb-4 flex rounded-xl border border-slate-200 bg-white p-1 shadow-xs">
        {Object.values(TRIP_VIEWS).map((item) => {
          const isActive = view === item.key
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => setView(item.key)}
              className={`flex-1 rounded-lg py-2.5 text-xs font-bold transition ${
                isActive
                  ? 'bg-brand-700 text-white shadow-xs'
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              {item.label}
            </button>
          )
        })}
      </div>

      {/* Filter and Date Bar */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2.5 rounded-lg border border-slate-200 bg-white p-3 shadow-xs">
        {/* Status Dropdown */}
        <div className="flex items-center gap-2">
          <label htmlFor="status-filter" className="text-xs font-semibold text-slate-500">
            Trạng thái:
          </label>
          <select
            id="status-filter"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-semibold text-slate-800 outline-none transition focus:border-brand-600 focus:bg-white"
          >
            {STATUS_FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* Date Selector */}
        <div className="flex items-center gap-2">
          <label htmlFor="date-filter" className="text-xs font-semibold text-slate-500">
            Ngày:
          </label>
          <div className="flex items-center gap-1">
            <input
              id="date-filter"
              type="date"
              value={dateDraft}
              onChange={handleDateChange}
              className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-800 outline-none transition focus:border-brand-600 focus:bg-white"
            />
            {dateDraft && (
              <button
                type="button"
                onClick={handleClearDate}
                title="Bỏ lọc theo ngày"
                className="rounded p-1 text-slate-400 hover:text-slate-600"
              >
                <Icon name="close" className="text-[16px]" />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800 flex items-start gap-2">
          <Icon name="error" className="text-[18px] text-red-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Lỗi tải dữ liệu: </span>
            {error}
          </div>
        </div>
      )}

      {/* Trip Cards List */}
      {loading ? (
        <TripListSkeleton />
      ) : trips.length === 0 ? (
        <div className="my-8 rounded-lg border border-dashed border-slate-200 bg-white p-8 text-center">
          <Icon name="event_busy" className="mx-auto mb-2 text-4xl text-slate-300" />
          <h3 className="text-sm font-bold text-slate-800">Không có chuyến đi nào phù hợp</h3>
          <p className="mt-1 text-xs text-slate-500">
            Bạn có thể thử chọn tab khác hoặc thay đổi bộ lọc ngày để xem các chuyến khác.
          </p>
        </div>
      ) : (
        <div className="space-y-3.5">
          {trips.map((trip) => (
            <DriverTripCard
              key={trip.tripId || trip.bookingCode}
              trip={trip}
              onConfirm={handleConfirmTrip}
              onReject={(t) => setRejectingTrip(t)}
              actionLoading={actionLoading}
            />
          ))}
        </div>
      )}

      {/* Pagination controls */}
      {totalPages > 1 && (
        <div className="mt-5 flex items-center justify-between border-t border-slate-200 pt-4 text-xs text-slate-600">
          <span>Tổng số {total} chuyến</span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page <= 0}
              className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-semibold hover:bg-slate-50 disabled:opacity-40"
            >
              Trước
            </button>
            <span className="px-2 font-bold text-slate-800">
              {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-semibold hover:bg-slate-50 disabled:opacity-40"
            >
              Sau
            </button>
          </div>
        </div>
      )}

      {/* Reject Trip Modal */}
      <DriverRejectTripModal
        trip={rejectingTrip}
        isOpen={Boolean(rejectingTrip)}
        onClose={() => setRejectingTrip(null)}
        onSuccess={(msg) => {
          refetch()
          setToast({ message: msg, type: 'info' })
        }}
      />
    </DriverLayout>
  )
}
