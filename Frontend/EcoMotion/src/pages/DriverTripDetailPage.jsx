import { useState, useMemo, useEffect, useCallback } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import DriverLayout from '../components/driver/DriverLayout'
import LiveMapViewer from '../components/map/LiveMapViewer'
import DriverRejectTripModal from '../components/driver/DriverRejectTripModal'
import DriverIncidentReportModal from '../components/driver/DriverIncidentReportModal'
import DriverEvidenceUploadModal from '../components/driver/DriverEvidenceUploadModal'
import Toast from '../components/Toast'
import { useDriverTripDetail } from '../hooks/useDriverTripDetail'
import { useDeadlineExpired } from '../hooks/useDeadlineExpired'
import { useDriverLocationTracker } from '../hooks/useDriverLocationTracker'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { driverTripService } from '../services/driverTripService'
import {
  formatTripDate,
  formatTripDateTime,
  formatTripTime,
  deriveExecutionStatus,
  getExecutionStatusConfig,
  isLocationStale,
  rentalTypeLabel,
} from '../utils/driverTripUtils'
import { getOptimizedImageUrl, EVIDENCE_TYPE_LABELS } from '../utils/cloudinaryUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

export default function DriverTripDetailPage() {
  const { bookingCode } = useParams()
  const navigate = useNavigate()
  const { trip, loading, error, refetch } = useDriverTripDetail(bookingCode)

  const [actionLoading, setActionLoading] = useState(false)
  const [showConfirmModal, setShowConfirmModal] = useState(false)
  const [showRejectModal, setShowRejectModal] = useState(false)
  const [showIncidentModal, setShowIncidentModal] = useState(false)
  const [showEvidenceModal, setShowEvidenceModal] = useState(false)
  const [evidenceList, setEvidenceList] = useState([])
  const [lightboxImage, setLightboxImage] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })
  const [actionError, setActionError] = useState(null)
  const [gpsEnabled] = useState(true)

  const fetchEvidence = useCallback(async () => {
    if (!bookingCode) return
    try {
      const data = await driverTripService.listEvidence(bookingCode)
      setEvidenceList(Array.isArray(data) ? data : [])
    } catch (err) {
      console.warn('Could not load evidence:', err)
    }
  }, [bookingCode])

  useEffect(() => {
    const timer = setTimeout(fetchEvidence, 0)
    return () => clearTimeout(timer)
  }, [fetchEvidence])

  const handleDeleteEvidence = async (evidenceId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa ảnh minh chứng này?')) return
    try {
      await driverTripService.deleteEvidence(bookingCode, evidenceId)
      setToast({ message: 'Đã xóa ảnh minh chứng thành công', type: 'success' })
      fetchEvidence()
    } catch (err) {
      setToast({
        message: err?.response?.data?.message || 'Không thể xóa ảnh minh chứng',
        type: 'error',
      })
    }
  }

  // Realtime Sync Hook
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.DRIVER_REASSIGNED]: () => {
        setToast({ message: 'Thông báo: Có cập nhật phân công từ chủ xe.', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.PAYMENT_VERIFIED]: () => {
        setToast({ message: 'Thông tin thanh toán đã cập nhật; vui lòng kiểm tra quyết toán.', type: 'success' })
      },
    },
    onResync: () => refetch(),
  })

  const executionStatus = useMemo(() => deriveExecutionStatus(trip), [trip])
  const statusConfig = useMemo(
    () => getExecutionStatusConfig(executionStatus),
    [executionStatus]
  )

  const isTripActive = ['CONFIRMED', 'ARRIVED', 'IN_PROGRESS'].includes(executionStatus)
  const assignmentExpired = useDeadlineExpired(trip?.assignmentExpiresAt)

  // Geolocation tracking hook
  const {
    currentCoords,
    lastSentAt,
  } = useDriverLocationTracker({
    tripId: trip?.tripId || trip?.bookingCode,
    enabled: isTripActive && gpsEnabled,
    minIntervalMs: 8000,
  })

  // Location staleness (>60s)
  const isGpsStale = isTripActive && isLocationStale(lastSentAt, 60)

  const handleActionClick = () => {
    setActionError(null)
    setShowConfirmModal(true)
  }

  const executeTransition = async () => {
    const tripId = trip?.tripId || trip?.bookingCode
    if (!tripId || !statusConfig?.actionType) return
    setActionLoading(true)
    setActionError(null)

    try {
      if (statusConfig.actionType === 'confirm') {
        await driverTripService.confirmTrip(tripId, trip.assignedAt)
        setToast({ message: 'Đã xác nhận nhận chuyến thành công!', type: 'success' })
      } else if (statusConfig.actionType === 'arrive') {
        await driverTripService.arriveTrip(tripId)
        setToast({ message: 'Đã báo có mặt tại điểm đón khách!', type: 'success' })
      } else if (statusConfig.actionType === 'start') {
        const started = await driverTripService.startTrip(tripId)
        setToast({ message: started.status === 'AWAITING_START_CONFIRMATION' ? 'Đã xác nhận bắt đầu; đang chờ người thuê xác nhận.' : 'Chuyến đi đã bắt đầu.', type: 'success' })
      } else if (statusConfig.actionType === 'finish') {
        await driverTripService.finishTrip(tripId)
        setToast({ message: 'Đã ghi nhận kết thúc. Cần xác nhận hai phía và xử lý quyết toán.', type: 'success' })
      }

      setShowConfirmModal(false)
      await refetch()
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Không thể thực hiện thao tác này'
      setActionError(msg)
    } finally {
      setActionLoading(false)
    }
  }

  const googleMapsUrl = trip?.pickupAddress
    ? `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(trip.pickupAddress)}`
    : null

  return (
    <DriverLayout activeTab="schedule">
      <Toast
        message={toast.message}
        type={toast.type}
        onClose={() => setToast({ message: '', type: 'success' })}
      />

      {/* Top Header: Back & Status */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/driver/schedule"
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 transition"
        >
          <Icon name="arrow_back" className="text-[16px]" />
          <span>Lịch trình</span>
        </Link>

        {trip && (
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${statusConfig.badgeClass}`}
            >
              <span className={`h-2 w-2 rounded-full ${statusConfig.dotClass}`} />
              <span>{statusConfig.label}</span>
            </span>
          </div>
        )}
      </div>

      {loading ? (
        <div className="py-20 text-center text-xs text-slate-500">
          <Icon name="sync" className="animate-spin text-3xl text-brand-700 mx-auto mb-2" />
          Đang tải thông tin tác nghiệp chuyến xe...
        </div>
      ) : error || !trip ? (
        <div className="rounded-lg border border-red-200 bg-white p-8 text-center shadow-xs">
          <Icon name="error_outline" className="text-4xl text-red-500 mx-auto mb-2" />
          <h2 className="text-base font-bold text-slate-900">Không tìm thấy chuyến đi</h2>
          <p className="mt-1 text-xs text-slate-500">{error || 'Chuyến đi không tồn tại hoặc đã bị hủy.'}</p>
          <Link
            to="/driver/schedule"
            className="mt-4 inline-block rounded-xl bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800"
          >
            Quay lại lịch trình
          </Link>
        </div>
      ) : (
        <div className="space-y-4 pb-24">
          {/* GPS Status Indicator & Staleness Warning */}
          {isTripActive && (
            <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-xs space-y-2">
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <span className={`h-2.5 w-2.5 rounded-full ${isGpsStale ? 'bg-red-500' : 'bg-emerald-500'}`} />
                  <span className="font-semibold text-slate-800">Định vị GPS thiết bị</span>
                  <span className="text-slate-400 font-mono text-[11px]">
                    {currentCoords ? `(${currentCoords.latitude.toFixed(4)}, ${currentCoords.longitude.toFixed(4)})` : 'Chưa có tọa độ'}
                  </span>
                </div>
                <span className="text-[11px] text-slate-500 font-mono">
                  {lastSentAt ? `Cập nhật: ${formatTripTime(lastSentAt)}` : 'Đang tìm kiếm vệ tinh...'}
                </span>
              </div>

              {isGpsStale && (
                <div className="rounded-lg bg-red-50 p-2 text-xs font-semibold text-red-800 border border-red-200 flex items-center gap-2">
                  <Icon name="satellite_alt" className="text-red-600 text-[16px] shrink-0" />
                  <span>
                    Cảnh báo mất tín hiệu GPS (&gt;60s). Vui lòng kiểm tra quyền định vị và kết nối mạng của điện thoại!
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Quick Action Bar for Emergency & Evidence */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            <button
              type="button"
              onClick={() => setShowIncidentModal(true)}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-red-200 bg-red-50 p-2.5 text-xs font-bold text-red-800 hover:bg-red-100 shadow-2xs transition"
            >
              <Icon name="warning" className="text-red-600 text-[18px]" />
              <span>Báo sự cố khẩn cấp</span>
            </button>

            <button
              type="button"
              onClick={() => setShowEvidenceModal(true)}
              className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-slate-200 bg-white p-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 shadow-2xs transition"
            >
              <Icon name="photo_camera" className="text-brand-700 text-[18px]" />
              <span>Tải ảnh minh chứng</span>
            </button>

            {googleMapsUrl && (
              <a
                href={googleMapsUrl}
                target="_blank"
                rel="noreferrer"
                className="col-span-2 sm:col-span-1 inline-flex items-center justify-center gap-1.5 rounded-xl border border-blue-200 bg-blue-50 p-2.5 text-xs font-bold text-blue-800 hover:bg-blue-100 shadow-2xs transition"
              >
                <Icon name="navigation" className="text-blue-700 text-[18px]" />
                <span>Chỉ đường đón khách</span>
              </a>
            )}
          </div>

          {/* Live Map Box */}
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs">
            <LiveMapViewer
              vehicleLocation={currentCoords}
              pickupLocation={{
                latitude: trip.pickupLatitude,
                longitude: trip.pickupLongitude,
                address: trip.pickupAddress,
              }}
              vehicleInfo={{
                licensePlate: trip.vehicle?.licensePlate,
                name: `${trip.vehicle?.brand || ''} ${trip.vehicle?.name || trip.vehicle?.model || ''}`.trim(),
                color: trip.vehicle?.color,
              }}
              height="300px"
            />
          </div>

          {/* Pickup and Passenger Information */}
          <div className="rounded-lg border border-slate-200 bg-white p-4 sm:p-5 shadow-xs space-y-3.5">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Mã đơn đặt xe
                </span>
                <div className="font-mono text-lg font-bold text-slate-900">
                  #{trip.bookingCode}
                </div>
              </div>
              <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                {rentalTypeLabel(trip.rentalType)}
              </span>
            </div>

            {/* Pickup details */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="rounded-xl bg-slate-50 p-3 space-y-1">
                <span className="text-slate-400 font-bold uppercase text-[10px] block">Thời gian đón</span>
                <div className="text-sm font-bold text-slate-900">
                  {formatTripTime(trip.scheduledPickupAt)}
                </div>
                <div className="text-slate-500 text-[11px]">{formatTripDate(trip.scheduledPickupAt)}</div>
              </div>

              <div className="rounded-xl bg-slate-50 p-3 space-y-1">
                <span className="text-slate-400 font-bold uppercase text-[10px] block">Địa chỉ đón</span>
                <div className="text-xs font-semibold text-slate-900 leading-snug">
                  {trip.pickupAddress || 'Đang cập nhật địa chỉ'}
                </div>
              </div>
            </div>

            {/* Passenger & Vehicle grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1 text-xs">
              {/* Passenger Card */}
              <div className="rounded-xl border border-slate-200 p-3 space-y-2">
                <div className="font-bold text-slate-700 flex items-center gap-1.5">
                  <Icon name="person" className="text-slate-400 text-[16px]" />
                  <span>Hành khách</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="font-bold text-slate-900 text-sm">{trip.customer?.fullName || 'Khách thuê'}</span>
                  {trip.customer?.phone && (
                    <a
                      href={`tel:${trip.customer.phone}`}
                      className="inline-flex items-center gap-1 rounded-lg bg-brand-50 px-2.5 py-1 font-mono font-bold text-brand-800 hover:bg-brand-100"
                    >
                      <Icon name="call" className="text-[13px]" />
                      <span>{trip.customer.phone}</span>
                    </a>
                  )}
                </div>
              </div>

              {/* Vehicle Card */}
              <div className="rounded-xl border border-slate-200 p-3 space-y-2">
                <div className="font-bold text-slate-700 flex items-center gap-1.5">
                  <Icon name="directions_car" className="text-slate-400 text-[16px]" />
                  <span>Xe phân công</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="font-bold text-slate-900">
                    {trip.vehicle?.brand} {trip.vehicle?.name || trip.vehicle?.model}
                  </span>
                  <span className="rounded bg-slate-900 px-2 py-0.5 font-mono text-xs font-bold text-amber-300">
                    {trip.vehicle?.licensePlate}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Evidence Gallery Card */}
          <div className="rounded-lg border border-slate-200 bg-white p-4 sm:p-5 shadow-xs space-y-3.5">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <Icon name="photo_library" className="text-brand-700 text-[20px]" />
                <h3 className="text-sm font-bold text-slate-900">
                  Ảnh biên bản &amp; Minh chứng tác nghiệp ({evidenceList.length})
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowEvidenceModal(true)}
                className="inline-flex items-center gap-1 text-xs font-bold text-brand-700 hover:text-brand-800"
              >
                <Icon name="add_a_photo" className="text-[16px]" />
                <span>Thêm ảnh</span>
              </button>
            </div>

            {evidenceList.length === 0 ? (
              <div className="py-6 text-center text-xs text-slate-400">
                Chưa có ảnh minh chứng nào được tải lên cho chuyến đi này.
              </div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {evidenceList.map((ev) => (
                  <div
                    key={ev.id}
                    className="group relative rounded-lg border border-slate-200 overflow-hidden bg-slate-50 text-xs shadow-2xs hover:border-slate-300 transition"
                  >
                    <div
                      onClick={() => setLightboxImage(ev.imageUrl)}
                      className="aspect-[4/3] bg-slate-200 cursor-pointer overflow-hidden relative"
                    >
                      <img
                        src={getOptimizedImageUrl(ev.imageUrl, { width: 350 })}
                        alt={ev.note || 'Evidence'}
                        className="h-full w-full object-cover group-hover:scale-105 transition duration-200"
                        loading="lazy"
                      />
                      <div className="absolute inset-0 bg-slate-900/0 group-hover:bg-slate-900/20 transition flex items-center justify-center">
                        <Icon name="zoom_in" className="text-white opacity-0 group-hover:opacity-100 text-2xl drop-shadow" />
                      </div>
                    </div>
                    <div className="p-2 space-y-1">
                      <div className="flex items-center justify-between gap-1">
                        <span className="font-bold text-slate-800 block text-[11px] truncate" title={EVIDENCE_TYPE_LABELS[ev.type] || ev.type}>
                          {EVIDENCE_TYPE_LABELS[ev.type] || ev.type}
                        </span>
                        <button
                          type="button"
                          onClick={() => handleDeleteEvidence(ev.id)}
                          className="text-slate-400 hover:text-rose-600 p-0.5 rounded transition"
                          title="Xóa minh chứng"
                        >
                          <Icon name="delete" className="text-[14px]" />
                        </button>
                      </div>
                      {ev.note && (
                        <p className="text-slate-600 text-[10px] line-clamp-2 leading-tight">
                          {ev.note}
                        </p>
                      )}
                      <div className="flex items-center justify-between text-[9px] text-slate-400 font-mono pt-0.5 border-t border-slate-100">
                        <span>{ev.uploadedBy?.name || 'Tài xế'}</span>
                        <span>{formatTripDateTime(ev.createdAt)}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Sticky Operational Bottom Action Bar for Mobile */}
          <div className="fixed bottom-0 inset-x-0 z-40 border-t border-slate-200 bg-white p-3 sm:p-4 shadow-sm">
            <div className="mx-auto max-w-lg flex items-center gap-3">
              {executionStatus === 'ASSIGNED' ? (
                <>
                  <button
                    type="button"
                    onClick={() => setShowRejectModal(true)}
                    disabled={actionLoading || assignmentExpired}
                    className="flex-1 rounded-lg border border-slate-300 bg-white py-3 text-xs font-bold text-rose-700 hover:bg-rose-50"
                  >
                    Từ chối nhận
                  </button>
                  <button
                    type="button"
                    onClick={handleActionClick}
                    disabled={actionLoading || assignmentExpired}
                    className="flex-2 rounded-lg bg-brand-700 py-3 text-xs font-bold text-white shadow-xs hover:bg-brand-800 disabled:opacity-50"
                  >
                    Xác nhận nhận chuyến
                  </button>
                </>
              ) : statusConfig?.actionLabel ? (
                <button
                  type="button"
                  onClick={handleActionClick}
                  disabled={actionLoading}
                  className="w-full rounded-lg bg-brand-700 py-3 text-sm font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50 flex items-center justify-center gap-2"
                >
                  {actionLoading && <Icon name="sync" className="animate-spin text-[18px]" />}
                  <span>{statusConfig.actionLabel}</span>
                </button>
              ) : (
                <div className="w-full text-center py-2 text-xs font-semibold text-slate-500">
                  Hiện chưa có thao tác tiếp theo; vui lòng theo dõi trạng thái chuyến.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal */}
      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
            <h3 className="text-base font-bold text-slate-900 mb-2">
              {statusConfig?.confirmTitle || 'Xác nhận tác nghiệp'}
            </h3>
            <p className="text-xs text-slate-600 leading-relaxed mb-4">
              {statusConfig?.confirmMessage || 'Bạn có chắc chắn muốn thực hiện bước tiếp theo này?'}
            </p>

            {actionError && (
              <div className="mb-4 rounded-lg bg-red-50 p-2.5 text-xs text-red-800 border border-red-200">
                {actionError}
              </div>
            )}

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                disabled={actionLoading}
                className="rounded-lg border border-slate-200 px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={executeTransition}
                disabled={actionLoading}
                className="inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
              >
                {actionLoading && <Icon name="sync" className="animate-spin text-[16px]" />}
                <span>Xác nhận thực hiện</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      <DriverRejectTripModal
        trip={trip}
        isOpen={showRejectModal}
        onClose={() => setShowRejectModal(false)}
        onSuccess={(msg) => {
          setToast({ message: msg, type: 'info' })
          navigate('/driver/schedule')
        }}
      />

      {/* Emergency Incident Modal */}
      <DriverIncidentReportModal
        trip={trip}
        currentCoords={currentCoords}
        isOpen={showIncidentModal}
        onClose={() => setShowIncidentModal(false)}
        onSuccess={(msg) => {
          setToast({ message: msg, type: 'success' })
          refetch()
        }}
      />

      {/* Evidence Upload Modal */}
      <DriverEvidenceUploadModal
        trip={trip}
        isOpen={showEvidenceModal}
        onClose={() => setShowEvidenceModal(false)}
        onSuccess={(msg) => {
          setToast({ message: msg, type: 'success' })
          fetchEvidence()
          refetch()
        }}
      />

      {/* Lightbox Preview Modal */}
      {lightboxImage && (
        <div
          onClick={() => setLightboxImage(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 animate-in fade-in duration-150 backdrop-blur-xs"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="relative max-h-[90vh] max-w-3xl overflow-hidden rounded-lg bg-black shadow-2xl"
          >
            <div className="absolute right-2 top-2 z-10 flex items-center gap-2">
              <a
                href={lightboxImage}
                target="_blank"
                rel="noreferrer"
                download
                className="rounded-full bg-slate-900/70 p-2 text-white hover:bg-slate-900"
                title="Mở ảnh gốc trong tab mới"
              >
                <Icon name="open_in_new" className="text-lg" />
              </a>
              <button
                type="button"
                onClick={() => setLightboxImage(null)}
                className="rounded-full bg-slate-900/70 p-2 text-white hover:bg-slate-900"
                title="Đóng"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>
            <img
              src={lightboxImage}
              alt="Minh chứng phóng to"
              className="max-h-[85vh] w-auto object-contain"
            />
          </div>
        </div>
      )}
    </DriverLayout>
  )
}
