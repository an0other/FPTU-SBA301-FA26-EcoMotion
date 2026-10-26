import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ProviderLayout from '../components/provider/ProviderLayout'
import BookingStatusBadge from '../components/provider/BookingStatusBadge'
import Toast from '../components/Toast'
import AcceptBookingModal from '../components/provider/modals/AcceptBookingModal'
import DenyBookingModal from '../components/provider/modals/DenyBookingModal'
import AssignDriverModal from '../components/provider/modals/AssignDriverModal'
import AddChargeModal from '../components/provider/modals/AddChargeModal'
import LiveMapViewer from '../components/map/LiveMapViewer'
import { useProviderBookingDetail } from '../hooks/useProviderBookingDetail'
import { useProviderTripTracking } from '../hooks/useProviderTripTracking'
import { useRealtimeSync, TV5_REALTIME_EVENTS } from '../hooks/useRealtimeSync'
import { providerBookingService } from '../services/providerBookingService'
import { getOptimizedImageUrl } from '../utils/cloudinaryUtils'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

function formatVnd(amount) {
  if (amount == null) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    const hours = String(d.getHours()).padStart(2, '0')
    const mins = String(d.getMinutes()).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const year = d.getFullYear()
    return `${hours}:${mins} ${day}/${month}/${year}`
  } catch {
    return isoString
  }
}

function initialsOf(name) {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

const URGENCY_STYLES = {
  CRITICAL: 'bg-red-100 text-red-800 border-red-300 font-bold',
  HIGH: 'bg-orange-100 text-orange-800 border-orange-300 font-bold',
  MEDIUM: 'bg-amber-100 text-amber-800 border-amber-300 font-medium',
  LOW: 'bg-blue-100 text-blue-800 border-blue-200 font-medium',
}

const INCIDENT_TYPE_LABELS = {
  ACCIDENT: 'Tai nạn giao thông',
  HEALTH_ISSUE: 'Vấn đề sức khỏe',
  VEHICLE_BREAKDOWN: 'Sự cố xe hỏng hóc',
  CANNOT_REACH_PICKUP: 'Không tiếp cận được điểm đón',
  LOST_CONTACT_WITH_CUSTOMER: 'Mất liên lạc với khách thuê',
  TRAFFIC_JAM: 'Ùn tắc giao thông nghiêm trọng',
  OTHER: 'Sự cố khác',
}

const EVIDENCE_TYPE_LABELS = {
  PICKUP_ARRIVAL: 'Ảnh có mặt tại điểm đón',
  VEHICLE_HANDOVER: 'Ảnh bàn giao xe',
  PRE_TRIP_CONDITION: 'Ảnh tình trạng xe trước chuyến',
  POST_TRIP_CONDITION: 'Ảnh tình trạng xe sau chuyến',
  INCIDENT: 'Ảnh phát sinh sự cố',
  INCIDENT_PROOF: 'Ảnh phát sinh sự cố',
  TRIP_COMPLETION: 'Ảnh hoàn tất chuyến đi',
  OTHER: 'Hình ảnh khác',
}

export default function ProviderBookingDetailPage() {
  const { bookingCode } = useParams()
  const navigate = useNavigate()
  const { booking, loading, error, refetch } = useProviderBookingDetail(bookingCode)

  const [activeImageIndex, setActiveImageIndex] = useState(0)
  const [showAcceptModal, setShowAcceptModal] = useState(false)
  const [showDenyModal, setShowDenyModal] = useState(false)
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [showAddChargeModal, setShowAddChargeModal] = useState(false)
  const [toast, setToast] = useState({ message: '', type: 'success' })
  const [finalizing, setFinalizing] = useState(false)

  // Incident & Evidence state
  const [incidents, setIncidents] = useState([])
  const [evidenceList, setEvidenceList] = useState([])
  const [selectedEvidenceImage, setSelectedEvidenceImage] = useState(null)
  const [resolvingIncident, setResolvingIncident] = useState(null)
  const [resolutionNotes, setResolutionNotes] = useState('')
  const [submittingResolution, setSubmittingResolution] = useState(false)

  const isPending = booking?.status === 'PENDING'
  const isRejected = booking?.status === 'REJECTED'
  const isCancelled = booking?.status === 'CANCELLED'

  const { trackingData, loading: trackingLoading, refetchTracking } = useProviderTripTracking(bookingCode, {
    enabled: Boolean(booking?.driverName && !isCancelled && !isRejected),
  })

  // Load Incidents and Evidence
  const fetchIncidentsAndEvidence = useCallback(async () => {
    if (!bookingCode) return
    try {
      const [incData, evData] = await Promise.allSettled([
        providerBookingService.getBookingIncidents(bookingCode),
        providerBookingService.getBookingEvidence(bookingCode),
      ])
      if (incData.status === 'fulfilled') setIncidents(incData.value || [])
      if (evData.status === 'fulfilled') setEvidenceList(evData.value || [])
    } catch {
      // Quiet fail if not yet mounted
    }
  }, [bookingCode])

  useEffect(() => {
    if (bookingCode) {
      fetchIncidentsAndEvidence()
    }
  }, [bookingCode, fetchIncidentsAndEvidence])

  const handleDeleteEvidence = async (evidenceId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa ảnh minh chứng này khỏi hệ thống?')) return
    try {
      await providerBookingService.deleteBookingEvidence(bookingCode, evidenceId)
      setToast({ message: 'Đã xóa ảnh minh chứng thành công', type: 'success' })
      fetchIncidentsAndEvidence()
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
      [TV5_REALTIME_EVENTS.DRIVER_ASSIGNED]: () => {
        refetch()
        setToast({ message: 'Tài xế đã được phân công cho chuyến xe.', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_ACCEPTED]: () => {
        refetch()
        setToast({ message: 'Tài xế đã xác nhận tiếp nhận chuyến!', type: 'success' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_REJECTED]: () => {
        refetch()
        setToast({ message: 'Cảnh báo: Tài xế đã từ chối nhận chuyến!', type: 'warning' })
      },
      [TV5_REALTIME_EVENTS.DRIVER_INCIDENT]: () => {
        refetch()
        fetchIncidentsAndEvidence()
        setToast({ message: 'Cảnh báo khẩn: Tài xế vừa báo cáo sự cố phát sinh!', type: 'error' })
      },
      [TV5_REALTIME_EVENTS.TRIP_STARTED]: () => {
        refetch()
        setToast({ message: 'Chuyến xe đã chính thức bắt đầu hành trình.', type: 'info' })
      },
      [TV5_REALTIME_EVENTS.TRIP_COMPLETED]: () => {
        refetch()
        fetchIncidentsAndEvidence()
        setToast({ message: 'Chuyến xe đã kết thúc an toàn.', type: 'success' })
      },
    },
    onResync: () => {
      refetch()
      fetchIncidentsAndEvidence()
    },
  })

  const handleActionSuccess = (_, message) => {
    refetch()
    fetchIncidentsAndEvidence()
    setToast({ message, type: 'success' })
  }

  const handleFinalizeSettlement = async () => {
    setFinalizing(true)
    try {
      const result = await providerBookingService.finalizeSettlement(booking.bookingCode)
      setToast({
        message: result?.status === 'COMPLETED'
          ? 'Đã chốt quyết toán. Đơn thuê đã hoàn thành.'
          : 'Đã chốt phụ phí và tạo yêu cầu thanh toán VNPAY/quyết toán.',
        type: 'success',
      })
      refetch()
    } catch (err) {
      setToast({ message: err?.response?.data?.message || err?.message || 'Không thể chốt quyết toán', type: 'error' })
    } finally {
      setFinalizing(false)
    }
  }

  const handleResolveIncident = async (e) => {
    e.preventDefault()
    if (!resolvingIncident || !resolutionNotes.trim()) return

    setSubmittingResolution(true)
    try {
      await providerBookingService.resolveIncident(booking.bookingCode, resolvingIncident.id, {
        resolutionNotes: resolutionNotes.trim(),
        newStatus: 'RESOLVED',
      })
      setToast({ message: 'Đã cập nhật phương án xử lý sự cố thành công!', type: 'success' })
      setResolvingIncident(null)
      setResolutionNotes('')
      fetchIncidentsAndEvidence()
    } catch (err) {
      setToast({ message: err?.response?.data?.message || err?.message || 'Không thể lưu xử lý sự cố', type: 'error' })
    } finally {
      setSubmittingResolution(false)
    }
  }

  if (loading) {
    return (
      <ProviderLayout active="Đơn thuê">
        <div className="mx-auto max-w-7xl py-16 text-center">
          <Icon name="sync" className="animate-spin text-3xl text-brand-700 mx-auto mb-3" />
          <p className="text-sm font-semibold text-slate-800">Đang tải thông tin chi tiết đơn thuê...</p>
          <p className="text-xs text-slate-400 mt-1">Hệ thống đang truy xuất dữ liệu nghiệp vụ</p>
        </div>
      </ProviderLayout>
    )
  }

  if (error || !booking) {
    return (
      <ProviderLayout active="Đơn thuê">
        <div className="mx-auto max-w-xl py-12">
          <div className="rounded-lg border border-red-200 bg-white p-6 text-center shadow-xs">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600 mb-3">
              <Icon name="error_outline" className="text-2xl" />
            </div>
            <h2 className="text-base font-bold text-slate-900">Không tìm thấy đơn thuê</h2>
            <p className="text-xs text-slate-500 mt-1">{error || 'Đơn thuê không tồn tại hoặc bạn không có quyền truy cập.'}</p>
            <div className="mt-5 flex justify-center gap-3">
              <button
                type="button"
                onClick={() => navigate('/provider/bookings')}
                className="rounded-lg bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200"
              >
                Quay lại danh sách
              </button>
              <button
                type="button"
                onClick={() => refetch()}
                className="rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800"
              >
                Thử lại
              </button>
            </div>
          </div>
        </div>
      </ProviderLayout>
    )
  }

  const images = booking.vehicleImageUrls && booking.vehicleImageUrls.length > 0
    ? booking.vehicleImageUrls
    : (booking.vehicleImageUrl ? [booking.vehicleImageUrl] : [])

  const activeIncidents = incidents.filter((inc) => inc.status !== 'RESOLVED' && inc.status !== 'CANCELLED')

  return (
    <ProviderLayout active="Đơn thuê">
      <div className="mx-auto max-w-7xl space-y-5">
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: '', type: 'success' })}
        />

        {/* Breadcrumb & Navigation */}
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <button
            type="button"
            onClick={() => navigate('/provider/bookings')}
            className="hover:text-brand-800 transition flex items-center gap-1 font-semibold"
          >
            <Icon name="arrow_back" className="text-[16px]" />
            <span>Quản lý đơn đặt xe</span>
          </button>
          <span>/</span>
          <span className="font-mono font-bold text-slate-800">{booking.bookingCode}</span>
        </div>

        {/* Urgent Incident Alert Banner if active incidents exist */}
        {activeIncidents.length > 0 && (
          <div className="rounded-xl border border-red-300 bg-red-50 p-4 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <Icon name="warning" className="text-2xl text-red-600 shrink-0 mt-0.5" />
                <div>
                  <h3 className="text-sm font-bold text-red-900">
                    Cảnh báo sự cố chuyến đi đang chờ xử lý ({activeIncidents.length})
                  </h3>
                  <div className="mt-1 space-y-1 text-xs text-red-800">
                    {activeIncidents.map((inc) => (
                      <div key={inc.id} className="flex items-center gap-2">
                        <span className="font-bold">
                          [{INCIDENT_TYPE_LABELS[inc.incidentType] || inc.incidentType}]
                        </span>
                        <span>{inc.description}</span>
                        <span className={`px-2 py-0.5 rounded text-[10px] border ${URGENCY_STYLES[inc.urgency] || 'bg-slate-100'}`}>
                          {inc.urgency}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setResolvingIncident(activeIncidents[0])}
                className="shrink-0 rounded-lg bg-red-600 px-3 py-1.5 text-xs font-bold text-white shadow-xs hover:bg-red-700"
              >
                Xử lý sự cố ngay
              </button>
            </div>
          </div>
        )}

        {/* Master Detail Header Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs flex flex-wrap items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold font-mono text-slate-900">#{booking.bookingCode}</h1>
              <BookingStatusBadge status={booking.status} />
              {booking.paymentStatus && (
                <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700 border border-slate-200">
                  Thanh toán: {booking.paymentStatus}
                </span>
              )}
            </div>
            <p className="text-xs text-slate-500">
              Khởi tạo lúc: {formatDateTime(booking.createdAt)} • Cập nhật cuối: {formatDateTime(booking.updatedAt)}
            </p>
          </div>

          {/* Action Button Strip */}
          <div className="flex flex-wrap items-center gap-2">
            {isPending && (
              <>
                <button
                  type="button"
                  onClick={() => setShowDenyModal(true)}
                  className="rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                >
                  Từ chối
                </button>
                <button
                  type="button"
                  onClick={() => setShowAcceptModal(true)}
                  className="rounded-lg bg-brand-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-brand-800 transition"
                >
                  Chấp nhận đơn
                </button>
              </>
            )}

            {!isRejected && !isCancelled && booking.status !== 'PENDING' && (
              <button
                type="button"
                onClick={() => setShowAssignModal(true)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-brand-300 bg-brand-50 px-3.5 py-2 text-xs font-bold text-brand-900 hover:bg-brand-100 transition shadow-2xs"
              >
                <Icon name="assignment_ind" className="text-[18px]" />
                <span>{booking.driverName ? 'Điều phối lại tài xế' : 'Phân công tài xế'}</span>
              </button>
            )}

            {booking.status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' && (
              <button
                type="button"
                onClick={() => setShowAddChargeModal(true)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 bg-indigo-50 px-3.5 py-2 text-xs font-bold text-indigo-900 hover:bg-indigo-100 transition shadow-2xs"
              >
                <Icon name="add_card" className="text-[18px]" />
                <span>Thêm phụ phí</span>
              </button>
            )}

            {booking.status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' &&
              booking.providerConfirmedAt && booking.renterConfirmedAt && (
              <button
                type="button"
                onClick={handleFinalizeSettlement}
                disabled={finalizing}
                className="inline-flex items-center gap-1.5 rounded-lg bg-purple-700 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-purple-800 disabled:opacity-50"
              >
                <Icon name="price_check" className="text-[18px]" />
                <span>{finalizing ? 'Đang chốt...' : 'Chốt quyết toán'}</span>
              </button>
            )}
          </div>
        </div>

        {/* 2-Column Responsive Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          {/* LEFT COLUMN: Customer, Vehicle, Driver, Map, Surcharges, Incidents, Evidence (8 cols) */}
          <div className="lg:col-span-8 space-y-5">
            {/* Customer & Trip Overview Card */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-4">
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Icon name="person" className="text-[18px] text-brand-700" />
                <span>Khách hàng &amp; Lịch trình đón</span>
              </h2>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Customer card */}
                <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3.5 flex items-start gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-800">
                    {initialsOf(booking.renterName)}
                  </div>
                  <div className="space-y-0.5 text-xs">
                    <div className="font-bold text-slate-900 text-sm">{booking.renterName}</div>
                    <div className="text-slate-600 flex items-center gap-1">
                      <Icon name="phone" className="text-[13px] text-slate-400" />
                      <span>{booking.renterPhone || '—'}</span>
                    </div>
                    <div className="text-slate-500 flex items-center gap-1 text-[11px]">
                      <Icon name="mail" className="text-[13px] text-slate-400" />
                      <span>{booking.renterEmail}</span>
                    </div>
                  </div>
                </div>

                {/* Trip info */}
                <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-3.5 space-y-1.5 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Hình thức thuê:</span>
                    <strong className="text-slate-800">{booking.rentalType || 'Theo ngày'}</strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Thời gian thuê:</span>
                    <strong className="text-slate-800">{booking.rentalDuration || '1 ngày'}</strong>
                  </div>
                  <div className="flex justify-between border-t border-slate-200 pt-1">
                    <span className="text-slate-500">Giờ đón khách:</span>
                    <strong className="text-brand-900 font-bold">{formatDateTime(booking.pickupTime)}</strong>
                  </div>
                </div>
              </div>

              {/* Pickup Address */}
              <div className="rounded-xl border border-slate-200 bg-white p-3 flex items-start gap-2.5 text-xs">
                <Icon name="pin_drop" className="text-[18px] text-brand-600 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold text-slate-700 block">Điểm đón khách:</span>
                  <span className="text-slate-900 font-medium">{booking.pickupAddress}</span>
                </div>
              </div>
            </div>

            {/* Vehicle Information Card */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <Icon name="directions_car" className="text-[18px] text-brand-700" />
                  <span>Phương tiện phục vụ</span>
                </h2>
                <span className="rounded bg-slate-900 px-2 py-0.5 font-mono text-xs font-bold text-amber-300">
                  {booking.licensePlate}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
                <div className="md:col-span-5 aspect-[16/10] overflow-hidden rounded-xl bg-slate-100 border border-slate-200">
                  {images[activeImageIndex] ? (
                    <img
                      src={images[activeImageIndex]}
                      alt={booking.vehicleName}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-slate-400">
                      <Icon name="directions_car" className="text-4xl" />
                    </div>
                  )}
                </div>

                <div className="md:col-span-7 grid grid-cols-2 gap-2 text-xs content-center">
                  <div className="rounded-lg border border-slate-100 bg-slate-50 p-2.5">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">Mẫu xe</span>
                    <strong className="text-slate-900 text-sm">{booking.vehicleName}</strong>
                  </div>
                  <div className="rounded-lg border border-slate-100 bg-slate-50 p-2.5">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">Biển số</span>
                    <strong className="text-slate-900 font-mono text-sm">{booking.licensePlate}</strong>
                  </div>
                  <div className="rounded-lg border border-slate-100 bg-slate-50 p-2.5">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">Số ghế</span>
                    <strong className="text-slate-800">{booking.seats ? `${booking.seats} chỗ` : '5 chỗ'}</strong>
                  </div>
                  <div className="rounded-lg border border-slate-100 bg-slate-50 p-2.5">
                    <span className="text-slate-400 block text-[10px] uppercase font-bold">Động cơ</span>
                    <strong className="text-emerald-700">
                      {booking.fuelType === 'EV' ? 'Điện (EV)' : booking.fuelType === 'HYBRID' ? 'Hybrid' : 'Xăng/Dầu'}
                    </strong>
                  </div>
                </div>
              </div>
            </div>

            {/* Assigned Driver Card */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                  <Icon name="badge" className="text-[18px] text-brand-700" />
                  <span>Tài xế phân công</span>
                </h2>
                {!isRejected && !isCancelled && (
                  <button
                    type="button"
                    onClick={() => setShowAssignModal(true)}
                    className="text-xs font-bold text-brand-700 hover:text-brand-900 transition"
                  >
                    {booking.driverName ? 'Thay đổi tài xế' : '+ Phân công tài xế ngay'}
                  </button>
                )}
              </div>

              {booking.driverName ? (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-brand-200 bg-brand-50/40 p-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-full bg-brand-200 font-bold text-brand-800 text-sm">
                      {initialsOf(booking.driverName)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900 text-sm">{booking.driverName}</span>
                        {booking.driverLicenseClass && (
                          <span className="rounded bg-brand-100 px-1.5 py-0.2 text-[10px] font-bold text-brand-800">
                            Hạng {booking.driverLicenseClass}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-3 text-xs text-slate-600 mt-0.5">
                        <span>SĐT: <strong className="text-slate-800 font-mono">{booking.driverPhone || '—'}</strong></span>
                        {booking.driverLicenseNo && (
                          <span>GPLX: <strong className="text-slate-800 font-mono">{booking.driverLicenseNo}</strong></span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-[10px] font-bold text-emerald-800">
                      Đã gán cho chuyến
                    </span>
                  </div>
                </div>
              ) : (
                <div className="py-6 px-4 rounded-xl border border-dashed border-amber-300 bg-amber-50/50 text-center">
                  <span className="text-xs font-bold text-amber-900 block">Chưa phân công tài xế</span>
                  <p className="text-[11px] text-amber-700 mt-0.5">
                    Hãy mở bộ điều phối để phân công tài xế tự động hoặc chọn từ danh sách đề xuất.
                  </p>
                  {!isRejected && !isCancelled && (
                    <button
                      type="button"
                      onClick={() => setShowAssignModal(true)}
                      className="mt-2.5 inline-flex items-center gap-1.5 rounded-lg bg-brand-700 px-3.5 py-1.5 text-xs font-bold text-white shadow-2xs hover:bg-brand-800"
                    >
                      <Icon name="assignment_ind" className="text-[16px]" />
                      <span>Mở giao diện điều phối</span>
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Live GPS Map & Execution Milestones */}
            {booking.driverName && (
              <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-4">
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div>
                    <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                      <Icon name="map" className="text-[18px] text-brand-700" />
                      <span>Giám sát vị trí trực tiếp</span>
                    </h2>
                    <p className="text-xs text-slate-500">Cập nhật tọa độ phương tiện thời gian thực</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => refetchTracking()}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Icon name="refresh" className={`text-[14px] ${trackingLoading ? 'animate-spin' : ''}`} />
                    <span>Làm mới</span>
                  </button>
                </div>

                <LiveMapViewer
                  vehicleLocation={trackingData?.latestLocation}
                  pickupLocation={{
                    latitude: trackingData?.pickupLatitude,
                    longitude: trackingData?.pickupLongitude,
                    address: booking.pickupAddress,
                  }}
                  vehicleInfo={{
                    licensePlate: booking.licensePlate,
                    name: booking.vehicleName,
                    color: booking.color,
                  }}
                  height="340px"
                />

                {/* Timestamps */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 text-xs pt-1">
                  <div className="rounded-lg bg-slate-50 p-2 border border-slate-200">
                    <span className="text-[10px] text-slate-400 block font-bold uppercase">1. Phân công</span>
                    <strong className="text-slate-800 text-[11px] block mt-0.5">
                      {formatDateTime(booking.assignedAt || trackingData?.assignedAt)}
                    </strong>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-2 border border-slate-200">
                    <span className="text-[10px] text-slate-400 block font-bold uppercase">2. Đã nhận</span>
                    <strong className="text-slate-800 text-[11px] block mt-0.5">
                      {formatDateTime(booking.driverConfirmedAt || trackingData?.driverConfirmedAt)}
                    </strong>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-2 border border-slate-200">
                    <span className="text-[10px] text-slate-400 block font-bold uppercase">3. Đã đến</span>
                    <strong className="text-slate-800 text-[11px] block mt-0.5">
                      {formatDateTime(booking.actualArrivedAt || trackingData?.actualArrivedAt)}
                    </strong>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-2 border border-slate-200">
                    <span className="text-[10px] text-slate-400 block font-bold uppercase">4. Bắt đầu</span>
                    <strong className="text-slate-800 text-[11px] block mt-0.5">
                      {formatDateTime(booking.actualStartAt || trackingData?.actualStartAt)}
                    </strong>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-2 border border-slate-200">
                    <span className="text-[10px] text-slate-400 block font-bold uppercase">5. Kết thúc</span>
                    <strong className="text-slate-800 text-[11px] block mt-0.5">
                      {formatDateTime(booking.actualEndAt || trackingData?.actualEndAt)}
                    </strong>
                  </div>
                </div>
              </div>
            )}

            {/* Incident Records Section */}
            {incidents.length > 0 && (
              <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-3">
                <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
                  <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Icon name="report_problem" className="text-[18px] text-red-600" />
                    <span>Lịch sử sự cố ghi nhận ({incidents.length})</span>
                  </h2>
                </div>

                <div className="space-y-2.5">
                  {incidents.map((inc) => (
                    <div key={inc.id} className="rounded-xl border border-slate-200 bg-slate-50/70 p-3.5 text-xs space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className={`px-2 py-0.5 rounded text-[10px] border ${URGENCY_STYLES[inc.urgency] || 'bg-slate-100'}`}>
                            {inc.urgency}
                          </span>
                          <span className="font-bold text-slate-900">
                            {INCIDENT_TYPE_LABELS[inc.incidentType] || inc.incidentType}
                          </span>
                        </div>
                        <span className="text-slate-400 font-mono text-[11px]">
                          {formatDateTime(inc.reportedAt || inc.createdAt)}
                        </span>
                      </div>

                      <p className="text-slate-700 leading-relaxed">{inc.description}</p>

                      {inc.evidenceUrls && (
                        <div className="text-[11px]">
                          <a
                            href={inc.evidenceUrls}
                            target="_blank"
                            rel="noreferrer"
                            className="text-brand-700 underline font-medium"
                          >
                            Xem ảnh minh chứng sự cố
                          </a>
                        </div>
                      )}

                      {inc.status === 'RESOLVED' ? (
                        <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-2 text-emerald-900 text-[11px]">
                          <span className="font-bold">Đã xử lý: </span>
                          <span>{inc.resolutionNotes || 'Đã ghi nhận phương án giải quyết.'}</span>
                        </div>
                      ) : (
                        <div className="flex justify-end pt-1">
                          <button
                            type="button"
                            onClick={() => setResolvingIncident(inc)}
                            className="rounded-lg bg-white border border-slate-200 px-3 py-1 text-xs font-semibold text-slate-800 hover:bg-slate-100"
                          >
                            Ghi nhận xử lý sự cố
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Trip Evidence Gallery */}
            {evidenceList.length > 0 && (
              <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Icon name="photo_camera" className="text-[18px] text-brand-700" />
                    <span>Hình ảnh biên bản &amp; Minh chứng ({evidenceList.length})</span>
                  </h2>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  {evidenceList.map((ev) => (
                    <div
                      key={ev.id}
                      className="group relative rounded-lg border border-slate-200 overflow-hidden bg-slate-50 text-xs shadow-2xs hover:border-slate-300 transition"
                    >
                      <div
                        onClick={() => setSelectedEvidenceImage(ev.imageUrl)}
                        className="aspect-[4/3] bg-slate-200 cursor-pointer overflow-hidden relative"
                      >
                        <img
                          src={getOptimizedImageUrl(ev.imageUrl, { width: 400 })}
                          alt={ev.note || 'Evidence'}
                          className="h-full w-full object-cover group-hover:scale-105 transition duration-200"
                          loading="lazy"
                        />
                        <div className="absolute inset-0 bg-slate-900/0 group-hover:bg-slate-900/20 transition flex items-center justify-center">
                          <Icon name="zoom_in" className="text-white opacity-0 group-hover:opacity-100 text-2xl drop-shadow" />
                        </div>
                      </div>
                      <div className="p-2 space-y-0.5">
                        <div className="flex items-center justify-between gap-1">
                          <span
                            className="font-bold text-slate-800 block text-[11px] truncate"
                            title={EVIDENCE_TYPE_LABELS[ev.type || ev.evidenceType] || ev.type || ev.evidenceType}
                          >
                            {EVIDENCE_TYPE_LABELS[ev.type || ev.evidenceType] || ev.type || ev.evidenceType}
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
                          <span className="text-slate-500 text-[10px] block truncate" title={ev.note}>
                            {ev.note}
                          </span>
                        )}
                        <div className="flex items-center justify-between text-[9px] text-slate-400 font-mono pt-1 border-t border-slate-100">
                          <span className="truncate max-w-[90px]">{ev.uploadedBy?.name || 'Tài xế'}</span>
                          <span>{formatDateTime(ev.capturedAt || ev.createdAt)}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Surcharges Breakdown Table */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Icon name="receipt_long" className="text-[18px] text-brand-700" />
                    <span>Bóc tách phụ phí phát sinh ({booking.charges?.length || 0})</span>
                  </h2>
                  <p className="text-xs text-slate-500">Các khoản phụ thu quá giờ, chờ đợi, bán kính ngoài</p>
                </div>
                {!isRejected && !isCancelled && (
                  <button
                    type="button"
                    onClick={() => setShowAddChargeModal(true)}
                    className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Icon name="add" className="text-[16px]" />
                    <span>Thêm phụ phí</span>
                  </button>
                )}
              </div>

              {booking.charges && booking.charges.length > 0 ? (
                <div className="overflow-x-auto rounded-xl border border-slate-200">
                  <table className="w-full min-w-[500px] border-collapse text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-500 uppercase">
                        <th className="px-3.5 py-2.5">Khoản phụ phí</th>
                        <th className="px-3.5 py-2.5">Số lượng</th>
                        <th className="px-3.5 py-2.5 text-right">Đơn giá</th>
                        <th className="px-3.5 py-2.5 text-right">Thành tiền</th>
                        <th className="px-3.5 py-2.5">Minh chứng</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {booking.charges.map((c) => (
                        <tr key={c.id} className="hover:bg-slate-50/50">
                          <td className="px-3.5 py-2.5 font-semibold text-slate-800">
                            <div>{c.description}</div>
                            <span className="text-[10px] font-bold text-indigo-700 bg-indigo-50 px-1.5 py-0.2 rounded inline-block mt-0.5">
                              {c.chargeType}
                            </span>
                          </td>
                          <td className="px-3.5 py-2.5 text-slate-600">{c.quantity ?? '1'}</td>
                          <td className="px-3.5 py-2.5 text-right font-mono text-slate-600">{formatVnd(c.unitPrice)}</td>
                          <td className="px-3.5 py-2.5 text-right font-mono font-bold text-slate-900">{formatVnd(c.amount)}</td>
                          <td className="px-3.5 py-2.5 text-slate-500">
                            {c.evidenceUrl ? (
                              <a href={c.evidenceUrl} target="_blank" rel="noreferrer" className="text-brand-700 underline">
                                Xem link
                              </a>
                            ) : (
                              '—'
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="rounded-xl border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">
                  Chưa có phụ phí phát sinh nào được ghi nhận cho đơn này
                </div>
              )}
            </div>
          </div>

          {/* RIGHT COLUMN: Financial Summary, Policies, Timeline (4 cols) */}
          <div className="lg:col-span-4 space-y-5">
            {/* Price Summary Card */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-4">
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Icon name="payments" className="text-[18px] text-brand-700" />
                <span>Chi phí &amp; Quyết toán</span>
              </h2>

              <div className="rounded-lg border border-brand-200 bg-slate-50 p-4 text-center">
                <span className="text-xs font-semibold text-slate-600 uppercase tracking-wider block">Tổng chi phí</span>
                <span className="text-2xl font-black font-mono text-brand-900 mt-1 block">
                  {formatVnd(booking.totalAmount)}
                </span>
                <span className="text-[11px] text-slate-500 mt-0.5 block">
                  {booking.status === 'COMPLETED' ? 'Đã hoàn tất thanh toán' : 'Bao gồm tiền thuê gốc & phụ phí'}
                </span>
              </div>

              {/* Policy Snapshot breakdown */}
              <div className="space-y-2 text-xs border-t border-slate-100 pt-3">
                <div className="flex justify-between text-slate-600">
                  <span>Giá 1 slot thuê:</span>
                  <strong className="text-slate-900 font-mono">{formatVnd(booking.slotPrice)}</strong>
                </div>
                {booking.depositRatio != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Tỷ lệ đặt cọc:</span>
                    <strong className="text-slate-900">{Number(booking.depositRatio * 100).toFixed(0)}%</strong>
                  </div>
                )}
                {booking.allowedOvertimeMinutes != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Vượt giờ miễn phí:</span>
                    <strong className="text-slate-900">{booking.allowedOvertimeMinutes} phút</strong>
                  </div>
                )}
                {booking.freeRadiusKm != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Bán kính miễn phí:</span>
                    <strong className="text-slate-900">{booking.freeRadiusKm} km</strong>
                  </div>
                )}
              </div>

              {/* Payment Transactions list */}
              {booking.paymentTransactions && booking.paymentTransactions.length > 0 && (
                <div className="border-t border-slate-100 pt-3 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Lịch sử giao dịch VNPAY/Cọc:</span>
                  <div className="space-y-1.5">
                    {booking.paymentTransactions.map((tx) => (
                      <div key={tx.id} className="rounded-lg border border-slate-100 bg-slate-50 p-2 text-xs flex justify-between items-center">
                        <div>
                          <div className="font-semibold text-slate-900">{tx.type}</div>
                          <div className="text-[10px] text-slate-400">{formatDateTime(tx.paidAt || tx.createdAt)}</div>
                        </div>
                        <div className="text-right">
                          <div className="font-mono font-bold text-slate-900">{formatVnd(tx.amount)}</div>
                          <span className="text-[10px] text-emerald-700 font-semibold">{tx.status}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Timeline */}
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs space-y-3">
              <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Icon name="history" className="text-[18px] text-brand-700" />
                <span>Tiến trình đơn</span>
              </h2>

              <div className="relative pl-5 space-y-4 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200 text-xs">
                {booking.timeline && booking.timeline.map((event, idx) => (
                  <div key={idx} className="relative">
                    <span className="absolute -left-5 top-1 h-2.5 w-2.5 rounded-full bg-brand-600 ring-2 ring-white" />
                    <div className="font-bold text-slate-800">{event.title}</div>
                    <div className="text-[10px] text-slate-400 font-mono">{formatDateTime(event.timestamp)}</div>
                    {event.description && <p className="text-[11px] text-slate-500 mt-0.5">{event.description}</p>}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Incident Resolution Dialog */}
      {resolvingIncident && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50">
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-md border border-slate-200">
            <h3 className="text-base font-bold text-slate-900 mb-2">
              Xử lý sự cố: {INCIDENT_TYPE_LABELS[resolvingIncident.incidentType] || resolvingIncident.incidentType}
            </h3>
            <p className="text-xs text-slate-600 mb-4">{resolvingIncident.description}</p>
            <form onSubmit={handleResolveIncident} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Phương án giải quyết &amp; Ghi chú nghiệp vụ <span className="text-red-500">*</span>
                </label>
                <textarea
                  rows="3"
                  value={resolutionNotes}
                  onChange={(e) => setResolutionNotes(e.target.value)}
                  placeholder="Ví dụ: Đã liên hệ điều xe cứu hộ / Khách đã đổi giờ đón..."
                  className="w-full rounded-xl border border-slate-200 p-2.5 text-xs text-slate-800 focus:border-brand-600 focus:outline-none"
                  required
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setResolvingIncident(null)}
                  className="rounded-lg border border-slate-200 px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  disabled={submittingResolution}
                  className="rounded-lg bg-brand-700 px-4 py-1.5 text-xs font-bold text-white hover:bg-brand-800 disabled:opacity-50"
                >
                  {submittingResolution ? 'Đang lưu...' : 'Xác nhận giải quyết'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Action Modals */}
      <AcceptBookingModal
        booking={booking}
        isOpen={showAcceptModal}
        onClose={() => setShowAcceptModal(false)}
        onSuccess={handleActionSuccess}
      />

      <DenyBookingModal
        booking={booking}
        isOpen={showDenyModal}
        onClose={() => setShowDenyModal(false)}
        onSuccess={handleActionSuccess}
      />

      <AssignDriverModal
        booking={booking}
        currentDriverId={booking.driverId}
        isOpen={showAssignModal}
        onClose={() => setShowAssignModal(false)}
        onSuccess={handleActionSuccess}
      />

      <AddChargeModal
        booking={booking}
        isOpen={showAddChargeModal}
        onClose={() => setShowAddChargeModal(false)}
        onSuccess={handleActionSuccess}
      />

      {/* Lightbox Preview Modal */}
      {selectedEvidenceImage && (
        <div
          onClick={() => setSelectedEvidenceImage(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 animate-in fade-in duration-150 backdrop-blur-xs"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="relative max-h-[90vh] max-w-3xl overflow-hidden rounded-lg bg-black shadow-2xl"
          >
            <div className="absolute right-2 top-2 z-10 flex items-center gap-2">
              <a
                href={selectedEvidenceImage}
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
                onClick={() => setSelectedEvidenceImage(null)}
                className="rounded-full bg-slate-900/70 p-2 text-white hover:bg-slate-900"
                title="Đóng"
              >
                <Icon name="close" className="text-lg" />
              </button>
            </div>
            <img
              src={selectedEvidenceImage}
              alt="Minh chứng phóng to"
              className="max-h-[85vh] w-auto object-contain"
            />
          </div>
        </div>
      )}
    </ProviderLayout>
  )
}
