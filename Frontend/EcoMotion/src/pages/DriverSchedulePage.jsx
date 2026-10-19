import { useState } from 'react'
import DriverLayout from '../components/driver/DriverLayout'
import ActiveTripBanner from '../components/driver/ActiveTripBanner'
import DriverTripCard from '../components/driver/DriverTripCard'
import TripListSkeleton from '../components/driver/TripListSkeleton'
import { useDriverSchedule } from '../hooks/useDriverSchedule'
import {
  STATUS_FILTER_OPTIONS,
  TRIP_VIEWS,
} from '../../src/utils/driverTripUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
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
    hasMore,
    loading,
    error,
    setView,
    setStatus,
    setDate,
    setPage,
    refetch,
  } = useDriverSchedule()

  const [dateDraft, setDateDraft] = useState(date || '')

  const totalPages = Math.ceil(total / size) || 1
  const isFiltered = status !== 'ALL' || Boolean(date)

  const handleDateChange = (e) => {
    const val = e.target.value
    setDateDraft(val)
    setDate(val || null)
  }

  const handleClearDate = () => {
    setDateDraft('')
    setDate(null)
  }

  return (
    <DriverLayout activeTab="schedule">
      {/* Top Banner & Header info */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
            Lịch trình tài xế
          </h1>
          <p className="mt-0.5 text-xs text-slate-500">
            Xem và theo dõi các chuyến xe đã được phân công
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
              className={`flex-1 rounded-lg py-2 text-xs font-bold transition ${
                isActive
                  ? 'bg-brand-600 text-white shadow-xs'
                  : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              {item.label}
            </button>
          )
        })}
      </div>

      {/* Filter and Date Bar */}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200/80 bg-white p-3 shadow-xs">
        {/* Status Dropdown */}
        <div className="flex items-center gap-2">
          <label htmlFor="status-filter" className="text-xs font-medium text-slate-500">
            Trạng thái:
          </label>
          <select
            id="status-filter"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-semibold text-slate-800 outline-none transition focus:border-brand-500 focus:bg-white"
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
          <label htmlFor="date-filter" className="text-xs font-medium text-slate-500">
            Ngày:
          </label>
          <div className="flex items-center gap-1">
            <input
              id="date-filter"
              type="date"
              value={dateDraft}
              onChange={handleDateChange}
              className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-800 outline-none transition focus:border-brand-500 focus:bg-white"
            />
            {dateDraft && (
              <button
                type="button"
                onClick={handleClearDate}
                title="Bỏ lọc theo ngày"
                className="rounded p-1 text-slate-400 hover:text-slate-600"
              >
                <Icon name="close" className="text-[14px]" />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Content Section: Loading, Error, Empty or List */}
      {loading ? (
        <TripListSkeleton />
      ) : error ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50/50 p-6 text-center">
          <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
            <Icon name="error_outline" className="text-[24px]" />
          </span>
          <h3 className="mt-3 text-sm font-bold text-slate-900">Không thể tải dữ liệu</h3>
          <p className="mt-1 text-xs text-rose-700">{error}</p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2 text-xs font-bold text-white shadow-xs transition hover:bg-brand-700"
          >
            <Icon name="refresh" className="text-[16px]" />
            <span>Thử lại</span>
          </button>
        </div>
      ) : trips.length === 0 ? (
        <div className="rounded-2xl border border-slate-200/80 bg-white p-8 text-center shadow-xs">
          <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
            <Icon name="no_crash" className="text-[24px]" />
          </span>
          <h3 className="mt-3 text-sm font-bold text-slate-900">
            {isFiltered
              ? 'Không tìm thấy chuyến phù hợp.'
              : 'Bạn chưa có chuyến được phân công.'}
          </h3>
          <p className="mt-1 text-xs text-slate-500">
            {isFiltered
              ? 'Hãy thử thay đổi bộ lọc trạng thái hoặc ngày tìm kiếm.'
              : 'Khi nhà xe phân công chuyến mới, thông tin sẽ xuất hiện tại đây.'}
          </p>
          {isFiltered && (
            <button
              type="button"
              onClick={() => {
                setStatus('ALL')
                handleClearDate()
              }}
              className="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-100"
            >
              Xoá bộ lọc
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between px-1 text-xs text-slate-500">
            <span>
              Tìm thấy <strong className="text-slate-800">{total}</strong> chuyến
            </span>
            <span>
              Trang <strong>{page + 1}</strong> / {totalPages}
            </span>
          </div>

          <div className="space-y-4">
            {trips.map((trip) => (
              <DriverTripCard key={trip.tripId || trip.bookingCode} trip={trip} />
            ))}
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3 shadow-xs">
              <button
                type="button"
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-40"
              >
                <Icon name="chevron_left" className="text-[16px]" />
                <span>Trang trước</span>
              </button>

              <span className="text-xs font-medium text-slate-600">
                {page + 1} / {totalPages}
              </span>

              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page + 1 >= totalPages}
                className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-40"
              >
                <span>Trang sau</span>
                <Icon name="chevron_right" className="text-[16px]" />
              </button>
            </div>
          )}
        </div>
      )}
    </DriverLayout>
  )
}
