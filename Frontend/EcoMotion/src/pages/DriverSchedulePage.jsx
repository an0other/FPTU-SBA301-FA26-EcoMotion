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
  return (
    <span className={`material-symbols-rounded select-none leading-none inline-flex items-center justify-center shrink-0 ${className}`}>
      {name}
    </span>
  )
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
        setToast({ message: 'Bạn có một chuyến đi mới vừa được phân công!', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_REASSIGNED]: () => {
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
      await driverTripService.confirmTrip(tripId, trip.assignedAt)
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
          className="inline-flex h-10 items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 text-xs font-semibold text-slate-700 shadow-2xs transition hover:bg-slate-50 disabled:opacity-50"
        >
          <Icon name="refresh" className={`text-[18px] ${loading ? 'animate-spin' : ''}`} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* Active Trip Banner */}
      <ActiveTripBanner trip={activeTrip} />

      {/* View Tabs: TODAY | UPCOMING | HISTORY */}
      <div className="mb-4 flex flex-wrap rounded-xl border border-slate-200 bg-white p-1 shadow-2xs">
        {Object.values(TRIP_VIEWS).map((item) => {
          const isActive = view === item.key
          return (
            <button
              key={item.key}
              type="button"
              onClick={() => { setStatus('ALL'); setView(item.key) }}
              className={`flex-1 h-10 rounded-lg text-xs font-bold transition ${
                isActive
                  ? 'bg-brand-700 text-white shadow-2xs'
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              {item.label}
            </button>
          )
        })}
      </div>

      {/* Filter and Date Bar */}
      <div className="mb-4 grid grid-cols-1 sm:grid-cols-2 gap-3 rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs items-end">
        {/* Status Dropdown */}
        <div>
          <label htmlFor="status-filter" className="mb-1.5 block text-xs font-semibold text-slate-600">
            Trạng thái chuyến
          </label>
          <div className="relative h-10 sm:h-[42px] w-full">
            <select
              id="status-filter"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="h-full w-full appearance-none rounded-lg sm:rounded-xl border border-slate-200 bg-white pl-3.5 pr-9 text-xs sm:text-[13px] font-semibold text-slate-800 outline-none transition hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
            >
              {STATUS_FILTER_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
              <Icon name="expand_more" className="text-[18px]" />
            </div>
          </div>
        </div>

        {/* Date Selector */}
        <div>
          <label htmlFor="date-filter" className="mb-1.5 block text-xs font-semibold text-slate-600">
            Lọc theo ngày
          </label>
          <div className="flex items-center gap-2">
            <input
              id="date-filter"
              type="date"
              lang="vi-VN"
              value={dateDraft}
              onChange={handleDateChange}
              className="h-10 sm:h-[42px] flex-1 rounded-lg sm:rounded-xl border border-slate-200 bg-white px-3 text-xs sm:text-[13px] text-slate-800 outline-none transition hover:border-slate-300 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600"
            />
            {(dateDraft || status !== 'ALL') && (
              <button
                type="button"
                onClick={() => {
                  handleClearDate()
                  setStatus('ALL')
                }}
                title="Xóa bộ lọc"
                className="inline-flex h-10 sm:h-[42px] shrink-0 items-center justify-center gap-1 rounded-lg sm:rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100"
              >
                <Icon name="filter_alt_off" className="text-[16px]" />
                <span>Xóa lọc</span>
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
        <div className="my-8 rounded-2xl border border-dashed border-slate-200 bg-white p-8 text-center">
          <Icon name="event_busy" className="mx-auto mb-2 text-4xl text-slate-300" />
          <h3 className="text-sm font-bold text-slate-800">
            {view === 'TODAY'
              ? 'Hôm nay bạn chưa có chuyến đi nào'
              : view === 'UPCOMING'
              ? 'Không có chuyến đi sắp tới'
              : view === 'HISTORY'
              ? 'Chưa có lịch sử chuyến đi'
              : 'Không có chuyến đi nào phù hợp'}
          </h3>
          <p className="mt-1 text-xs text-slate-500 max-w-md mx-auto leading-normal">
            {dateDraft || status !== 'ALL'
              ? 'Không tìm thấy chuyến nào phù hợp với bộ lọc hiện tại. Thử xóa lọc để xem toàn bộ lịch trình.'
              : view === 'TODAY'
              ? 'Lịch trình hôm nay của bạn đang trống. Bạn có thể mở tab "Sắp tới" để kiểm tra các cuốc xe đã được lên lịch cho những ngày tiếp theo.'
              : view === 'UPCOMING'
              ? 'Chưa có chuyến đi mới nào được phân công trong thời gian tới.'
              : 'Bạn có thể thử chọn tab khác hoặc thay đổi bộ lọc ngày để xem các chuyến khác.'}
          </p>
          {view === 'TODAY' && !dateDraft && status === 'ALL' && (
            <button
              type="button"
              onClick={() => { setStatus('ALL'); setView('UPCOMING') }}
              className="mt-3.5 inline-flex items-center gap-1.5 rounded-xl bg-brand-50 px-4 py-2 text-xs font-bold text-brand-800 hover:bg-brand-100 transition border border-brand-200 shadow-2xs"
            >
              <Icon name="event_upcoming" className="text-[16px]" />
              <span>Xem chuyến sắp tới</span>
            </button>
          )}
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
