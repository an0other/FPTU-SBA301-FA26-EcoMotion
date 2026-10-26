import { useState, useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import DriverLayout from '../components/driver/DriverLayout'
import LiveMapViewer from '../components/map/LiveMapViewer'
import { useDriverTripDetail } from '../hooks/useDriverTripDetail'
import { useDriverLocationTracker } from '../hooks/useDriverLocationTracker'
import { driverTripService } from '../services/driverTripService'
import {
  formatTripDate,
  formatTripDateTime,
  formatTripTime,
  deriveExecutionStatus,
  getExecutionStatusConfig,
} from '../utils/driverTripUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
}

export default function DriverTripDetailPage() {
  const { bookingCode } = useParams()
  const { trip, loading, error, refetch } = useDriverTripDetail(bookingCode)

  const [actionLoading, setActionLoading] = useState(false)
  const [showConfirmModal, setShowConfirmModal] = useState(false)
  const [actionError, setActionError] = useState(null)
  const [successToast, setSuccessToast] = useState(null)
  const [gpsEnabled, setGpsEnabled] = useState(true)
  const [showIssueModal, setShowIssueModal] = useState(false)
  const [issueReason, setIssueReason] = useState('')

  const executionStatus = useMemo(() => deriveExecutionStatus(trip), [trip])
  const statusConfig = useMemo(
    () => getExecutionStatusConfig(executionStatus),
    [executionStatus]
  )

  const isActionable = Boolean(statusConfig?.actionLabel)
  const isTripActive = ['CONFIRMED', 'ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS'].includes(executionStatus)
  const canReportUnavailable = ['ASSIGNED', 'CONFIRMED', 'ARRIVED', 'AWAITING_START_CONFIRMATION'].includes(executionStatus)

  const submitDriverIssue = async () => {
    if (!issueReason.trim()) {
      setActionError('Vui lòng nhập lý do không thể tiếp tục chuyến.')
      return
    }
    setActionLoading(true)
    setActionError(null)
    try {
      await driverTripService.reportUnavailable(trip.tripId, issueReason.trim())
      setShowIssueModal(false)
      setIssueReason('')
      setSuccessToast('Đã báo sự cố. Nhà cung cấp sẽ phân công tài xế thay thế.')
      await refetch()
    } catch (err) {
      setActionError(err.response?.data?.message || err.message || 'Không thể báo sự cố')
    } finally {
      setActionLoading(false)
    }
  }

  // Geolocation tracking hook
  const {
    trackingState,
    currentCoords,
    lastSentAt,
    errorMessage: gpsError,
  } = useDriverLocationTracker({
    tripId: trip?.tripId,
    enabled: isTripActive && gpsEnabled,
    minIntervalMs: 8000,
    onLocationSent: () => {
      // Background coordinates sent successfully
    },
  })

  const handleActionClick = () => {
    setActionError(null)
    setShowConfirmModal(true)
  }

  const executeTransition = async () => {
    if (!trip?.tripId || !statusConfig?.actionType) return
    setActionLoading(true)
    setActionError(null)

    try {
      if (statusConfig.actionType === 'confirm') {
        await driverTripService.confirmTrip(trip.tripId)
        setSuccessToast('Đã xác nhận nhận chuyến thành công!')
      } else if (statusConfig.actionType === 'arrive') {
        await driverTripService.arriveTrip(trip.tripId)
        setSuccessToast('Đã báo có mặt tại điểm đón!')
      } else if (statusConfig.actionType === 'start') {
        await driverTripService.startTrip(trip.tripId)
        setSuccessToast('Chuyến đi đã chính thức bắt đầu!')
      } else if (statusConfig.actionType === 'finish') {
        await driverTripService.finishTrip(trip.tripId)
        setSuccessToast('Hoàn thành chuyến đi thành công!')
      }

      setShowConfirmModal(false)
      await refetch()
    } catch (err) {
      const status = err.response?.status
      const msg =
        err.response?.data?.message ||
        err.message ||
        'Không thể thực hiện thao tác này'

      if (status === 409) {
        setActionError(
          'Trạng thái chuyến đã thay đổi hoặc có thao tác trùng lặp. Đang cập nhật dữ liệu mới nhất...'
        )
        await refetch()
      } else {
        setActionError(msg)
      }
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <DriverLayout activeTab="schedule">
      {/* Toast Alert */}
      {successToast && (
        <div className="fixed top-5 right-5 z-50 flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-xs font-bold text-white shadow-lg animate-bounce">
          <Icon name="check_circle" className="text-[18px]" />
          <span>{successToast}</span>
          <button
            type="button"
            onClick={() => setSuccessToast(null)}
            className="ml-2 hover:opacity-80"
          >
            <Icon name="close" className="text-[14px]" />
          </button>
        </div>
      )}

      {/* Back button & status header */}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/driver/schedule"
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-xs transition hover:bg-slate-50"
        >
          <Icon name="arrow_back" className="text-[16px]" />
          <span>Quay lại lịch trình</span>
        </Link>

        {trip && (
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${statusConfig.badgeClass}`}
            >
              <span className={`h-2 w-2 rounded-full ${statusConfig.dotClass}`} />
              {statusConfig.label}
            </span>
          </div>
        )}
      </div>

      {loading ? (
        <div className="animate-pulse space-y-4 rounded-2xl border border-slate-200/80 bg-white p-6">
          <div className="h-6 w-48 rounded bg-slate-200" />
          <div className="h-4 w-32 rounded bg-slate-200" />
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            <div className="h-28 rounded-xl bg-slate-100" />
            <div className="h-28 rounded-xl bg-slate-100" />
          </div>
        </div>
      ) : error ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50/50 p-6 text-center">
          <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
            <Icon name="error_outline" className="text-[24px]" />
          </span>
          <h3 className="mt-3 text-sm font-bold text-slate-900">
            Không thể xem chuyến đi
          </h3>
          <p className="mt-1 text-xs text-rose-700">{error}</p>
          <div className="mt-4 flex justify-center gap-2">
            <button
              type="button"
              onClick={() => refetch()}
              className="inline-flex items-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2 text-xs font-bold text-white shadow-xs transition hover:bg-brand-700"
            >
              <Icon name="refresh" className="text-[16px]" />
              <span>Thử lại</span>
            </button>
            <Link
              to="/driver/schedule"
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
            >
              Về trang lịch trình
            </Link>
          </div>
        </div>
      ) : !trip ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center">
          <p className="text-sm text-slate-600">Không tìm thấy thông tin chuyến đi.</p>
        </div>
      ) : (
        <div className="space-y-5">
          {/* Action Execution Banner (D02 Driver State Machine Action) */}
          {isActionable && (
            <div className="rounded-2xl border border-teal-200 bg-gradient-to-r from-teal-50 to-emerald-50/60 p-5 shadow-xs">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-teal-800">
                    Hành động khả dụng cho tài xế
                  </span>
                  <h2 className="text-base font-bold text-slate-900 mt-0.5">
                    {statusConfig.label}
                  </h2>
                  <p className="text-xs text-slate-600 mt-1 max-w-xl">
                    {executionStatus === 'ASSIGNED' &&
                      'Vui lòng nhấn xác nhận để nhà xe và khách hàng biết bạn đã sẵn sàng phục vụ chuyến đi này.'}
                    {executionStatus === 'CONFIRMED' &&
                      'Hãy di chuyển đến điểm đón. Khi đến nơi, nhấn nút thông báo bạn đã có mặt.'}
                    {executionStatus === 'ARRIVED' &&
                      'Khách hàng đã lên xe an toàn? Hãy bấm bắt đầu để tính thời gian và cung đường thực tế.'}
                    {executionStatus === 'IN_PROGRESS' &&
                      'Chuyến đi đang diễn ra. Khi khách đã đến nơi và xuống xe an toàn, hãy kết thúc chuyến.'}
                  </p>
                </div>

                <button
                  type="button"
                  onClick={handleActionClick}
                  disabled={actionLoading}
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-6 py-3 text-sm font-bold text-white shadow-md transition hover:bg-teal-700 active:scale-95 disabled:opacity-50 shrink-0"
                >
                  <Icon
                    name={
                      executionStatus === 'ASSIGNED'
                        ? 'check_circle'
                        : executionStatus === 'CONFIRMED'
                        ? 'pin_drop'
                        : executionStatus === 'ARRIVED'
                        ? 'play_arrow'
                        : 'flag'
                    }
                    className="text-[20px]"
                  />
                  <span>{statusConfig.actionLabel}</span>
                </button>
              </div>

              {actionError && (
                <div className="mt-3 rounded-lg bg-rose-100/80 p-2.5 text-xs font-semibold text-rose-800">
                  {actionError}
                </div>
              )}
            </div>
          )}

          {canReportUnavailable ? (
            <div className="flex justify-end">
              <button type="button" onClick={() => setShowIssueModal(true)} className="rounded-xl border border-rose-200 bg-white px-4 py-2 text-xs font-bold text-rose-700 hover:bg-rose-50">
                Báo sự cố / không thể tiếp tục chuyến
              </button>
            </div>
          ) : null}

          {/* Foreground GPS Tracking Control Bar */}
          {isTripActive && (
            <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span
                    className={`flex h-9 w-9 items-center justify-center rounded-xl ${
                      trackingState === 'ACTIVE'
                        ? 'bg-emerald-100 text-emerald-700 animate-pulse'
                        : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    <Icon name="gps_fixed" className="text-[20px]" />
                  </span>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-slate-800">
                        Định vị GPS xe trực tiếp
                      </span>
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          trackingState === 'ACTIVE'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {trackingState === 'ACTIVE'
                          ? 'Đang gửi tọa độ'
                          : trackingState === 'DENIED'
                          ? 'Quyền GPS bị chặn'
                          : 'Tạm dừng'}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-0.5">
                      {currentCoords
                        ? `Tọa độ: ${currentCoords.latitude.toFixed(5)}, ${currentCoords.longitude.toFixed(5)} (Độ chính xác: ~${Math.round(currentCoords.accuracy)}m)`
                        : 'Đang kết nối GPS trên thiết bị...'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setGpsEnabled(!gpsEnabled)}
                    className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
                      gpsEnabled
                        ? 'border-emerald-300 bg-emerald-50 text-emerald-800 hover:bg-emerald-100'
                        : 'border-slate-300 bg-slate-50 text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    {gpsEnabled ? 'Tắt chia sẻ vị trí' : 'Bật chia sẻ vị trí'}
                  </button>
                </div>
              </div>

              {gpsError && (
                <div className="mt-2 text-[11px] text-amber-700 bg-amber-50 rounded-lg p-2 border border-amber-200">
                  {gpsError}
                </div>
              )}
            </div>
          )}

          {/* Live Map Monitoring */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
              <div className="flex items-center gap-2">
                <Icon name="map" className="text-[20px] text-teal-600" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                  Bản đồ lộ trình & vị trí xe
                </h2>
              </div>
              <span className="text-[11px] text-slate-400">
                Nguồn: OpenStreetMap
              </span>
            </div>

            <LiveMapViewer
              vehicleLocation={
                currentCoords
                  ? {
                      latitude: currentCoords.latitude,
                      longitude: currentCoords.longitude,
                      accuracyMeters: currentCoords.accuracy,
                      receivedAt: lastSentAt,
                      trackingState: trackingState === 'ACTIVE' ? 'LIVE' : 'OFFLINE',
                      isStale: false,
                    }
                  : trip.latestLocation
              }
              pickupLocation={{
                latitude: trip.pickupLatitude || trip.latestLocation?.latitude,
                longitude: trip.pickupLongitude || trip.latestLocation?.longitude,
                address: trip.pickupAddress,
              }}
              vehicleInfo={trip.vehicle}
              height="360px"
            />
          </div>

          {/* Trip Lifecycle Progress Timeline */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs">
            <div className="flex items-center gap-2 border-b border-slate-100 pb-3 mb-4">
              <Icon name="timeline" className="text-[20px] text-slate-500" />
              <h2 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                Tiến trình thực hiện chuyến đi
              </h2>
            </div>

            <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200">
              {/* Step 1: Assigned */}
              <div className="relative flex items-start gap-3">
                <span
                  className={`absolute -left-6 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ring-4 ring-white ${
                    trip.assignedAt
                      ? 'bg-teal-600 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  1
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">
                    Phân công cho tài xế
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {trip.assignedAt ? formatTripDateTime(trip.assignedAt) : 'Chưa phân công'}
                  </div>
                </div>
              </div>

              {/* Step 2: Confirmed */}
              <div className="relative flex items-start gap-3">
                <span
                  className={`absolute -left-6 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ring-4 ring-white ${
                    trip.driverConfirmedAt
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  2
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">
                    Tài xế xác nhận nhận chuyến
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {trip.driverConfirmedAt
                      ? formatTripDateTime(trip.driverConfirmedAt)
                      : 'Đang chờ tài xế xác nhận'}
                  </div>
                </div>
              </div>

              {/* Step 3: Arrived */}
              <div className="relative flex items-start gap-3">
                <span
                  className={`absolute -left-6 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ring-4 ring-white ${
                    trip.actualArrivedAt
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  3
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">
                    Có mặt tại điểm đón
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {trip.actualArrivedAt
                      ? formatTripDateTime(trip.actualArrivedAt)
                      : 'Đang di chuyển đến điểm đón'}
                  </div>
                </div>
              </div>

              {/* Step 4: Started */}
              <div className="relative flex items-start gap-3">
                <span
                  className={`absolute -left-6 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ring-4 ring-white ${
                    trip.actualStartAt
                      ? 'bg-emerald-600 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  4
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">
                    Bắt đầu hành trình
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {trip.actualStartAt
                      ? formatTripDateTime(trip.actualStartAt)
                      : 'Chưa khởi hành'}
                  </div>
                </div>
              </div>

              {/* Step 5: Completed */}
              <div className="relative flex items-start gap-3">
                <span
                  className={`absolute -left-6 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ring-4 ring-white ${
                    trip.actualEndAt
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  5
                </span>
                <div>
                  <div className="text-xs font-bold text-slate-900">
                    Hoàn thành chuyến đi
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {trip.actualEndAt
                      ? formatTripDateTime(trip.actualEndAt)
                      : 'Chưa hoàn thành'}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Main Trip Card Info */}
          <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-4">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Mã chuyến đi
                </span>
                <h1 className="font-mono text-xl font-bold text-slate-900">
                  {trip.bookingCode}
                </h1>
              </div>

              <div className="text-right">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Hình thức thuê
                </span>
                <div className="text-xs font-bold text-slate-800">
                  {trip.rentalType === 'HOURLY' ? 'Thuê xe theo giờ' : 'Thuê xe theo ngày'}
                </div>
              </div>
            </div>

            {/* Time & Route */}
            <div className="mt-4 grid gap-4 rounded-xl bg-slate-50/70 p-4 sm:grid-cols-2">
              <div className="flex items-start gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-100 text-teal-700">
                  <Icon name="schedule" className="text-[18px]" />
                </span>
                <div>
                  <span className="block text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                    Thời gian đón
                  </span>
                  <div className="text-sm font-bold text-slate-900">
                    {formatTripTime(trip.scheduledPickupAt)}
                  </div>
                  <div className="text-xs text-slate-500">
                    {formatTripDate(trip.scheduledPickupAt)}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                  <Icon name="pin_drop" className="text-[18px]" />
                </span>
                <div>
                  <span className="block text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                    Điểm đón khách
                  </span>
                  <p className="text-xs font-medium text-slate-800 leading-relaxed">
                    {trip.pickupAddress || 'Đang cập nhật'}
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Cards Grid: Vehicle & Customer */}
          <div className="grid gap-5 sm:grid-cols-2">
            {/* Vehicle Card */}
            <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                <Icon name="directions_car" className="text-[20px] text-slate-500" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                  Thông tin phương tiện
                </h2>
              </div>

              <div className="mt-3 space-y-2.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-slate-400">Mẫu xe:</span>
                  <span className="font-bold text-slate-900">
                    {trip.vehicle?.brand} {trip.vehicle?.name || trip.vehicle?.model}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Biển kiểm soát:</span>
                  <span className="rounded bg-slate-900 px-2 py-0.5 font-mono text-xs font-bold text-amber-300">
                    {trip.vehicle?.licensePlate || 'Chưa cập nhật'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Số chỗ ngồi:</span>
                  <span className="font-semibold text-slate-800">
                    {trip.vehicle?.seatCount ? `${trip.vehicle.seatCount} chỗ` : '--'}
                  </span>
                </div>
                {trip.vehicle?.color && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Màu xe:</span>
                    <span className="font-semibold text-slate-800">{trip.vehicle.color}</span>
                  </div>
                )}
                {trip.vehicle?.transmission && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Hộp số:</span>
                    <span className="font-semibold text-slate-800">
                      {trip.vehicle.transmission}
                    </span>
                  </div>
                )}
                {trip.vehicle?.fuelType && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Nhiên liệu:</span>
                    <span className="font-semibold text-slate-800">{trip.vehicle.fuelType}</span>
                  </div>
                )}
              </div>
            </div>

            {/* Customer & Provider Card */}
            <div className="space-y-5">
              {/* Customer */}
              <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs">
                <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                  <Icon name="person" className="text-[20px] text-slate-500" />
                  <h2 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                    Thông tin hành khách
                  </h2>
                </div>

                <div className="mt-3 space-y-2.5 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Họ và tên:</span>
                    <span className="font-bold text-slate-900">
                      {trip.customer?.fullName || 'Khách thuê'}
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-400">Liên hệ điện thoại:</span>
                    {trip.customer?.phone ? (
                      <a
                        href={`tel:${trip.customer.phone}`}
                        className="inline-flex items-center gap-1 rounded-lg bg-teal-50 px-2.5 py-1 font-mono text-xs font-bold text-teal-700 hover:bg-teal-100"
                      >
                        <Icon name="call" className="text-[14px]" />
                        <span>{trip.customer.phone}</span>
                      </a>
                    ) : (
                      <span className="text-slate-400">Bảo mật</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Provider Information */}
              {trip.provider && (
                <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-xs">
                  <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                    <Icon name="storefront" className="text-[20px] text-slate-500" />
                    <h2 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      Đơn vị vận hành
                    </h2>
                  </div>

                  <div className="mt-3 space-y-2.5 text-xs">
                    <div className="flex justify-between">
                      <span className="text-slate-400">Nhà xe:</span>
                      <span className="font-bold text-slate-900">
                        {trip.provider.displayName}
                      </span>
                    </div>
                    {trip.provider.hotline && (
                      <div className="flex justify-between items-center">
                        <span className="text-slate-400">Hotline hỗ trợ:</span>
                        <a
                          href={`tel:${trip.provider.hotline}`}
                          className="font-mono font-bold text-brand-600 hover:underline"
                        >
                          {trip.provider.hotline}
                        </a>
                      </div>
                    )}
                    {trip.provider.depotAddress && (
                      <div className="flex justify-between">
                        <span className="text-slate-400">Bãi xe:</span>
                        <span className="text-right text-slate-700 max-w-[200px] truncate">
                          {trip.provider.depotAddress}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal */}
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl animate-scale-up">
            <div className="flex items-center gap-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-100 text-teal-700">
                <Icon name="help_outline" className="text-[22px]" />
              </span>
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  {statusConfig.confirmTitle}
                </h3>
                <span className="text-xs text-slate-400">Mã chuyến: {trip.bookingCode}</span>
              </div>
            </div>

            <p className="mt-4 text-xs text-slate-600 leading-relaxed">
              {statusConfig.confirmMessage}
            </p>

            {actionError && (
              <div className="mt-3 rounded-lg bg-rose-50 p-2 text-xs font-semibold text-rose-700 border border-rose-200">
                {actionError}
              </div>
            )}

            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={actionLoading}
                className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={executeTransition}
                disabled={actionLoading}
                className="inline-flex items-center gap-1.5 rounded-xl bg-teal-600 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-teal-700 disabled:opacity-50"
              >
                {actionLoading && <Icon name="progress_activity" className="animate-spin text-[16px]" />}
                <span>Xác nhận thực hiện</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {showIssueModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-base font-bold text-slate-900">Báo sự cố tài xế</h3>
            <p className="mt-1 text-xs text-slate-500">Chỉ dùng khi bạn không thể tiếp tục chuyến. Nhà cung cấp sẽ nhận cảnh báo để đổi tài xế.</p>
            <textarea value={issueReason} onChange={(event) => setIssueReason(event.target.value)} maxLength={500} rows={4} placeholder="Mô tả sự cố..." className="mt-4 w-full rounded-xl border border-slate-200 p-3 text-sm outline-none focus:border-rose-400" />
            {actionError ? <p className="mt-2 text-xs font-semibold text-rose-700">{actionError}</p> : null}
            <div className="mt-5 flex justify-end gap-2">
              <button type="button" onClick={() => setShowIssueModal(false)} disabled={actionLoading} className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold">Đóng</button>
              <button type="button" onClick={submitDriverIssue} disabled={actionLoading} className="rounded-xl bg-rose-600 px-4 py-2 text-xs font-bold text-white disabled:opacity-50">Gửi báo cáo</button>
            </div>
          </div>
        </div>
      )}
    </DriverLayout>
  )
}
