import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
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
import { providerBookingService } from '../services/providerBookingService'

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded ${className}`}>{name}</span>
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

  const isPending = booking?.status === 'PENDING'
  const isRejected = booking?.status === 'REJECTED'
  const isCancelled = booking?.status === 'CANCELLED'

  const { trackingData, loading: trackingLoading, refetchTracking } = useProviderTripTracking(bookingCode, {
    enabled: Boolean(booking?.driverName && !isCancelled && !isRejected),
  })

  const handleActionSuccess = (_, message) => {
    refetch()
    setToast({ message, type: 'success' })
  }

  const handleFinalizeSettlement = async () => {
    setFinalizing(true)
    try {
      const result = await providerBookingService.finalizeSettlement(booking.bookingCode)
      setToast({
        message: result.status === 'COMPLETED'
          ? 'Đã chốt quyết toán. Đơn thuê đã hoàn thành.'
          : 'Đã chốt phụ phí và tạo yêu cầu thanh toán VietQR.',
        type: 'success',
      })
      refetch()
    } catch (err) {
      setToast({ message: err.message || 'Không thể chốt quyết toán', type: 'error' })
    } finally {
      setFinalizing(false)
    }
  }

  if (loading) {
    return (
      <ProviderLayout active="Đơn thuê">
        <div className="mx-auto max-w-7xl py-12 text-center">
          <Icon name="hourglass_empty" className="animate-spin text-4xl text-[#064e3b] mx-auto mb-3" />
          <p className="text-sm font-semibold text-slate-700">Đang tải thông tin chi tiết đơn thuê...</p>
          <p className="text-xs text-slate-400 mt-1">Vui lòng đợi trong giây lát</p>
        </div>
      </ProviderLayout>
    )
  }

  if (error || !booking) {
    return (
      <ProviderLayout active="Đơn thuê">
        <div className="mx-auto max-w-2xl py-12">
          <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center">
            <Icon name="error" className="text-4xl text-red-600 mx-auto mb-2" />
            <h2 className="text-base font-bold text-red-900">Không tìm thấy đơn thuê</h2>
            <p className="text-xs text-red-700 mt-1">{error || 'Đơn thuê không tồn tại hoặc bạn không có quyền truy cập.'}</p>
            <div className="mt-5 flex justify-center gap-3">
              <button
                type="button"
                onClick={() => navigate('/provider/bookings')}
                className="rounded-lg bg-white border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
              >
                Quay lại danh sách
              </button>
              <button
                type="button"
                onClick={() => refetch()}
                className="rounded-lg bg-red-600 px-4 py-2 text-xs font-bold text-white shadow-2xs hover:bg-red-700 transition"
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

  return (
    <ProviderLayout active="Đơn thuê">
      <div className="mx-auto max-w-7xl space-y-6">
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast({ message: '', type: 'success' })}
        />

        {/* 1. Header with Breadcrumb, Status, and Action Buttons */}
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center border-b border-slate-200/80 pb-5">
          <div>
            <div className="mb-1 flex items-center gap-1.5 text-xs font-semibold text-slate-500">
              <Link to="/provider/bookings" className="hover:text-slate-700 transition">
                Hàng đợi đơn thuê
              </Link>
              <Icon name="chevron_right" className="text-[14px] text-slate-400" />
              <span className="font-mono text-[#064e3b] font-bold">{booking.bookingCode}</span>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-black tracking-tight text-slate-900 lg:text-3xl font-mono">
                {booking.bookingCode}
              </h1>
              <BookingStatusBadge status={booking.status} />
              <span className="text-xs text-slate-500 font-medium">
                Tạo lúc: {formatDateTime(booking.createdAt)}
              </span>
            </div>
          </div>

          {/* Quick Action Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => navigate('/provider/bookings')}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 shadow-2xs hover:bg-slate-50 transition"
            >
              <Icon name="arrow_back" className="text-[17px]" />
              <span>Quay lại</span>
            </button>

            {isPending && (
              <>
                <button
                  type="button"
                  onClick={() => setShowDenyModal(true)}
                  className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3.5 text-xs font-bold text-red-600 shadow-2xs hover:bg-red-50 transition"
                >
                  <Icon name="close" className="text-[18px]" />
                  <span>Từ chối</span>
                </button>
                <button
                  type="button"
                  onClick={() => setShowAcceptModal(true)}
                  className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-[#064e3b] px-4 text-xs font-bold text-white shadow-xs hover:bg-[#054031] transition"
                >
                  <Icon name="check" className="text-[18px]" />
                  <span>Chấp nhận đơn</span>
                </button>
              </>
            )}

            {booking.status === 'CONFIRMED' && (
              <button
                type="button"
                onClick={() => setShowAssignModal(true)}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-teal-200 bg-teal-50 px-3.5 text-xs font-bold text-teal-800 shadow-2xs hover:bg-teal-100 transition"
              >
                <Icon name="badge" className="text-[18px]" />
                <span>{booking.driverName ? 'Đổi tài xế' : 'Gán tài xế'}</span>
              </button>
            )}

            {booking.status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION' && (
              <button
                type="button"
                onClick={() => setShowAddChargeModal(true)}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-indigo-200 bg-indigo-50 px-3.5 text-xs font-bold text-indigo-800 shadow-2xs hover:bg-indigo-100 transition"
              >
                <Icon name="add_circle" className="text-[18px]" />
                <span>Thêm phụ phí</span>
              </button>
            )}

            {booking.status === 'AWAITING_EXTRA_CHARGE_CONFIRMATION'
              && booking.providerConfirmedAt && booking.renterConfirmedAt && (
              <button
                type="button"
                onClick={handleFinalizeSettlement}
                disabled={finalizing}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-purple-700 px-4 text-xs font-bold text-white shadow-xs hover:bg-purple-800 disabled:opacity-50"
              >
                <Icon name="price_check" className="text-[18px]" />
                <span>{finalizing ? 'Đang chốt...' : 'Chốt quyết toán'}</span>
              </button>
            )}
          </div>
        </div>

        {/* 2-Column Main Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* LEFT COLUMN: Customer, Vehicle, Trip, Driver (8 cols) */}
          <div className="lg:col-span-8 space-y-6">
            {/* Customer & Trip Overview Card */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-5">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Icon name="person" className="text-[20px] text-[#064e3b]" />
                <span>Thông tin khách hàng &amp; Chuyến đi</span>
              </h2>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
                {/* Customer card */}
                <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-4 flex items-start gap-3.5">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-sm font-bold text-[#064e3b] shadow-2xs">
                    {initialsOf(booking.renterName)}
                  </div>
                  <div className="space-y-1">
                    <div className="font-bold text-slate-900 text-sm">{booking.renterName}</div>
                    <div className="flex items-center gap-1 text-xs text-slate-600">
                      <Icon name="phone" className="text-[14px] text-slate-400" />
                      <span>{booking.renterPhone || '—'}</span>
                    </div>
                    <div className="flex items-center gap-1 text-xs text-slate-500">
                      <Icon name="mail" className="text-[14px] text-slate-400" />
                      <span>{booking.renterEmail}</span>
                    </div>
                  </div>
                </div>

                {/* Trip Quick Info */}
                <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-4 space-y-2 text-xs">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Hình thức thuê:</span>
                    <strong className="text-[#064e3b] font-semibold">{booking.rentalType || 'Theo ngày'}</strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Thời lượng dự kiến:</span>
                    <strong className="text-slate-800">{booking.rentalDuration || '1 ngày'}</strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Số lượng khách:</span>
                    <strong className="text-slate-800">{booking.passengerCount ? `${booking.passengerCount} người` : '1 người'}</strong>
                  </div>
                  <div className="flex justify-between border-t border-slate-200/80 pt-1.5">
                    <span className="text-slate-500">Giờ đón xe:</span>
                    <strong className="text-slate-900 font-bold">{formatDateTime(booking.pickupTime)}</strong>
                  </div>
                </div>
              </div>

              {/* Pickup Address */}
              <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3.5 flex items-start gap-2.5 text-xs">
                <Icon name="location_on" className="text-[20px] text-emerald-600 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold text-slate-700 block">Địa chỉ điểm đón:</span>
                  <span className="text-slate-900 font-medium">{booking.pickupAddress}</span>
                </div>
              </div>

              {/* Actual Start & End Timestamps */}
              {(booking.actualStartAt || booking.actualEndAt) && (
                <div className="grid grid-cols-2 gap-3 pt-1 border-t border-slate-100 text-xs">
                  <div>
                    <span className="text-slate-500 block">Bắt đầu thực tế:</span>
                    <strong className="text-slate-800">{formatDateTime(booking.actualStartAt)}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Kết thúc thực tế:</span>
                    <strong className="text-slate-800">{formatDateTime(booking.actualEndAt)}</strong>
                  </div>
                </div>
              )}
            </div>

            {/* Vehicle Information Card with Gallery */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Icon name="directions_car" className="text-[20px] text-[#064e3b]" />
                  <span>Thông tin phương tiện</span>
                </h2>
                <span className="rounded bg-slate-100 px-2.5 py-0.5 font-mono text-xs font-bold text-slate-700">
                  {booking.licensePlate}
                </span>
              </div>

              {/* Vehicle Gallery & Specs */}
              <div className="grid grid-cols-1 md:grid-cols-12 gap-5">
                {/* Images */}
                <div className="md:col-span-6 space-y-2">
                  <div className="aspect-[16/10] overflow-hidden rounded-xl bg-slate-100 border border-slate-200 shadow-2xs">
                    {images[activeImageIndex] ? (
                      <img
                        src={images[activeImageIndex]}
                        alt={booking.vehicleName}
                        className="h-full w-full object-cover transition-all"
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-slate-400">
                        <Icon name="directions_car" className="text-5xl" />
                      </div>
                    )}
                  </div>
                  {images.length > 1 && (
                    <div className="flex items-center gap-2 overflow-x-auto pb-1">
                      {images.map((img, idx) => (
                        <button
                          key={img}
                          type="button"
                          onClick={() => setActiveImageIndex(idx)}
                          className={`h-12 w-16 shrink-0 overflow-hidden rounded-lg border-2 transition ${activeImageIndex === idx ? 'border-emerald-600 ring-2 ring-emerald-600/30' : 'border-transparent opacity-70 hover:opacity-100'
                            }`}
                        >
                          <img src={img} alt="thumbnail" className="h-full w-full object-cover" />
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* Specs Table */}
                <div className="md:col-span-6 grid grid-cols-2 gap-3 text-xs content-center">
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Hãng &amp; Dòng xe</span>
                    <strong className="text-slate-900 font-bold text-sm">{booking.vehicleName}</strong>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Biển kiểm soát</span>
                    <strong className="text-slate-900 font-mono font-bold text-sm">{booking.licensePlate}</strong>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Năm sản xuất</span>
                    <strong className="text-slate-800 font-bold">{booking.modelYear || '2024'}</strong>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Số chỗ ngồi</span>
                    <strong className="text-slate-800 font-bold">{booking.seats ? `${booking.seats} chỗ` : '5 chỗ'}</strong>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Hộp số</span>
                    <strong className="text-slate-800 font-bold">{booking.transmission === 'AUTOMATIC' ? 'Tự động' : 'Số sàn'}</strong>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-[#f9fdfa] p-3">
                    <span className="text-slate-400 block text-[11px]">Nhiên liệu</span>
                    <strong className="text-emerald-700 font-bold">
                      {booking.fuelType === 'EV' ? 'Xe điện (EV)' : booking.fuelType === 'HYBRID' ? 'Hybrid' : 'Xăng/Dầu'}
                    </strong>
                  </div>
                </div>
              </div>
            </div>

            {/* Assigned Driver Card */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Icon name="badge" className="text-[20px] text-[#064e3b]" />
                  <span>Tài xế phân công</span>
                </h2>
                {booking.status === 'CONFIRMED' && (
                  <button
                    type="button"
                    onClick={() => setShowAssignModal(true)}
                    className="text-xs font-bold text-teal-700 hover:text-teal-800 transition"
                  >
                    {booking.driverName ? 'Đổi tài xế khác' : '+ Phân công tài xế ngay'}
                  </button>
                )}
              </div>

              {booking.driverName ? (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-teal-100 bg-teal-50/40 p-4">
                  <div className="flex items-center gap-3.5">
                    {booking.driverPortraitUrl ? (
                      <img
                        src={booking.driverPortraitUrl}
                        alt={booking.driverName}
                        className="h-12 w-12 rounded-full object-cover shadow-2xs border border-white"
                      />
                    ) : (
                      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-200 font-bold text-teal-800 text-sm">
                        {initialsOf(booking.driverName)}
                      </div>
                    )}
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-900 text-sm sm:text-base">{booking.driverName}</span>
                        {booking.driverLicenseClass && (
                          <span className="rounded bg-teal-100 px-1.5 py-0.5 text-[10px] font-bold text-teal-800">
                            Hạng {booking.driverLicenseClass}
                          </span>
                        )}
                      </div>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600 mt-1">
                        <span>SĐT: <strong className="text-slate-800">{booking.driverPhone || '—'}</strong></span>
                        <span>GPLX: <strong className="text-slate-800">{booking.driverLicenseNo || '—'}</strong></span>
                      </div>
                    </div>
                  </div>

                  <div className="text-right shrink-0">
                    <div className="flex items-center sm:justify-end gap-1 text-xs font-bold text-amber-600">
                      <Icon name="star" className="text-[16px] fill-current" />
                      <span>{Number(booking.driverRatingAvg || 5).toFixed(1)} / 5.0</span>
                    </div>
                    <span className="inline-block mt-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-[10px] font-bold text-emerald-800">
                      Đã gán cho chuyến
                    </span>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-6 px-4 rounded-xl border border-dashed border-amber-300 bg-amber-50/60 text-center">
                  <Icon name="warning" className="text-3xl text-amber-600 mb-1" />
                  <span className="text-xs font-bold text-amber-900">Chuyến xe này chưa được gán tài xế</span>
                  <p className="text-[11px] text-amber-700 mt-0.5 max-w-sm">
                    Vui lòng chọn tài xế thuộc đội xe của bạn trước thời điểm khởi hành để tài xế kịp nhận xe.
                  </p>
                  {!isRejected && !isCancelled && (
                    <button
                      type="button"
                      onClick={() => setShowAssignModal(true)}
                      className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-teal-700 px-4 py-1.5 text-xs font-bold text-white shadow-2xs hover:bg-teal-800 transition"
                    >
                      <Icon name="person_add" className="text-[16px]" />
                      <span>Phân công tài xế</span>
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Live Vehicle Tracking & OpenStreetMap Map (O08) */}
            {booking.driverName && (
              <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2">
                    <Icon name="satellite_alt" className="text-[20px] text-[#064e3b]" />
                    <div>
                      <h2 className="text-base font-bold text-slate-900">
                        Giám sát phương tiện & Vị trí trực tiếp
                      </h2>
                      <p className="text-xs text-slate-500">
                        Theo dõi vị trí phương tiện phục vụ chuyến xe theo thời gian thực (Cập nhật tự động 10s)
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-0.5 text-xs font-bold ${booking.actualEndAt || booking.status === 'COMPLETED'
                          ? 'bg-slate-100 text-slate-700 border-slate-200'
                          : booking.actualStartAt || booking.status === 'IN_PROGRESS'
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200 animate-pulse'
                            : booking.actualArrivedAt
                              ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
                              : booking.driverConfirmedAt
                                ? 'bg-blue-50 text-blue-700 border-blue-200'
                                : 'bg-amber-50 text-amber-700 border-amber-200'
                        }`}
                    >
                      <span
                        className={`h-2 w-2 rounded-full ${booking.actualEndAt || booking.status === 'COMPLETED'
                            ? 'bg-slate-400'
                            : booking.actualStartAt || booking.status === 'IN_PROGRESS'
                              ? 'bg-emerald-500'
                              : booking.actualArrivedAt
                                ? 'bg-indigo-500'
                                : booking.driverConfirmedAt
                                  ? 'bg-blue-500'
                                  : 'bg-amber-500'
                          }`}
                      />
                      {booking.actualEndAt || booking.status === 'COMPLETED'
                        ? 'Chuyến đã hoàn thành'
                        : booking.actualStartAt || booking.status === 'IN_PROGRESS'
                          ? 'Đang trên hành trình'
                          : booking.actualArrivedAt
                            ? 'Tài xế đã đến điểm đón'
                            : booking.driverConfirmedAt
                              ? 'Tài xế đã nhận chuyến'
                              : 'Chờ tài xế xác nhận'}
                    </span>

                    <button
                      type="button"
                      onClick={() => refetchTracking()}
                      className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition"
                      title="Làm mới vị trí"
                    >
                      <Icon name="refresh" className={`text-[14px] ${trackingLoading ? 'animate-spin' : ''}`} />
                      <span>Làm mới</span>
                    </button>
                  </div>
                </div>

                {/* Map Viewer */}
                <LiveMapViewer
                  vehicleLocation={trackingData?.latestLocation}
                  pickupLocation={{
                    latitude: trackingData?.pickupLatitude,
                    longitude: trackingData?.pickupLongitude,
                    address: booking.pickupAddress,
                  }}
                  vehicleInfo={{
                    licensePlate: booking.licensePlate,
                    name: `${booking.brand || ''} ${booking.model || ''}`.trim(),
                    color: booking.color,
                  }}
                  height="380px"
                />

                {/* Execution Timeline */}
                <div className="mt-4 rounded-xl border border-slate-100 bg-[#f9fdfa] p-4">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700 mb-3 flex items-center gap-1.5">
                    <Icon name="timeline" className="text-[16px] text-teal-700" />
                    <span>Dấu mốc thực hiện thực tế (Hệ thống ghi nhận)</span>
                  </h3>

                  <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs">
                    <div className="rounded-lg bg-white p-2.5 border border-slate-200/70">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase">1. Phân công</span>
                      <strong className="text-slate-800 text-[11px] block mt-0.5">
                        {formatDateTime(booking.assignedAt || trackingData?.assignedAt)}
                      </strong>
                    </div>

                    <div className="rounded-lg bg-white p-2.5 border border-slate-200/70">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase">2. Tài xế nhận</span>
                      <strong className={`text-[11px] block mt-0.5 ${booking.driverConfirmedAt || trackingData?.driverConfirmedAt ? 'text-blue-700 font-bold' : 'text-slate-400'}`}>
                        {formatDateTime(booking.driverConfirmedAt || trackingData?.driverConfirmedAt)}
                      </strong>
                    </div>

                    <div className="rounded-lg bg-white p-2.5 border border-slate-200/70">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase">3. Đến điểm đón</span>
                      <strong className={`text-[11px] block mt-0.5 ${booking.actualArrivedAt || trackingData?.actualArrivedAt ? 'text-indigo-700 font-bold' : 'text-slate-400'}`}>
                        {formatDateTime(booking.actualArrivedAt || trackingData?.actualArrivedAt)}
                      </strong>
                    </div>

                    <div className="rounded-lg bg-white p-2.5 border border-slate-200/70">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase">4. Bắt đầu chuyến</span>
                      <strong className={`text-[11px] block mt-0.5 ${booking.actualStartAt || trackingData?.actualStartAt ? 'text-emerald-700 font-bold' : 'text-slate-400'}`}>
                        {formatDateTime(booking.actualStartAt || trackingData?.actualStartAt)}
                      </strong>
                    </div>

                    <div className="rounded-lg bg-white p-2.5 border border-slate-200/70">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase">5. Kết thúc</span>
                      <strong className={`text-[11px] block mt-0.5 ${booking.actualEndAt || trackingData?.actualEndAt ? 'text-slate-900 font-bold' : 'text-slate-400'}`}>
                        {formatDateTime(booking.actualEndAt || trackingData?.actualEndAt)}
                      </strong>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Additional Charges Table */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <Icon name="receipt_long" className="text-[20px] text-[#064e3b]" />
                    <span>Bóc tách phụ phí phát sinh ({booking.charges?.length || 0})</span>
                  </h2>
                  <p className="text-xs text-slate-500">Các khoản phí vượt giờ, chờ, km ngoài bán kính hoặc ghi nhận thủ công</p>
                </div>
                {!isRejected && !isCancelled && (
                  <button
                    type="button"
                    onClick={() => setShowAddChargeModal(true)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-bold text-indigo-800 shadow-2xs hover:bg-indigo-100 transition"
                  >
                    <Icon name="add" className="text-[16px]" />
                    <span>Thêm phụ phí</span>
                  </button>
                )}
              </div>

              {booking.charges && booking.charges.length > 0 ? (
                <div className="overflow-x-auto rounded-xl border border-slate-100">
                  <table className="w-full min-w-[600px] border-collapse text-left text-xs">
                    <thead>
                      <tr className="border-b border-slate-100 bg-[#f4fbf7] text-[11px] font-bold text-slate-500 uppercase">
                        <th className="px-4 py-2.5">Khoản phụ phí</th>
                        <th className="px-4 py-2.5">Số lượng</th>
                        <th className="px-4 py-2.5 text-right">Đơn giá</th>
                        <th className="px-4 py-2.5 text-right">Hệ số</th>
                        <th className="px-4 py-2.5 text-right">Thành tiền</th>
                        <th className="px-4 py-2.5">Ghi chú</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {booking.charges.map((charge) => (
                        <tr key={charge.id} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3 font-semibold text-slate-800">
                            <div>{charge.description}</div>
                            <span className="text-[10px] font-bold text-indigo-700 bg-indigo-50 px-1.5 py-0.2 rounded inline-block mt-0.5">
                              {charge.chargeType}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-slate-600">{charge.quantity ?? '—'}</td>
                          <td className="px-4 py-3 text-right font-mono text-slate-600">
                            {formatVnd(charge.unitPrice)}
                          </td>
                          <td className="px-4 py-3 text-right text-slate-600">
                            {charge.appliedRate ? `${charge.appliedRate}x` : '—'}
                          </td>
                          <td className="px-4 py-3 text-right font-mono font-bold text-slate-900">
                            {formatVnd(charge.amount)}
                          </td>
                          <td className="px-4 py-3 text-slate-500 text-[11px]">
                            {charge.evidenceUrl ? (
                              <a
                                href={charge.evidenceUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="text-emerald-700 hover:underline flex items-center gap-1"
                              >
                                <Icon name="attachment" className="text-[14px]" />
                                <span>Xem bằng chứng</span>
                              </a>
                            ) : (
                              <span>Bởi {charge.createdByName}</span>
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

          {/* RIGHT COLUMN: Pricing, Policy Snapshot, Payments, Timeline (4 cols) */}
          <div className="lg:col-span-4 space-y-6">
            {/* Price Summary Card */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Icon name="payments" className="text-[20px] text-[#064e3b]" />
                <span>Chi phí &amp; Thanh toán</span>
              </h2>

              <div className="rounded-xl border border-emerald-200/60 bg-[#f2f9f5] p-4 text-center">
                <span className="text-xs font-semibold text-slate-600 uppercase tracking-wider block">Tổng giá trị đơn</span>
                <span className="text-2xl sm:text-3xl font-black font-mono tracking-tight text-[#064e3b] mt-1 block">
                  {formatVnd(booking.totalAmount)}
                </span>
                <span className="text-[11px] font-medium text-emerald-800 mt-1 block">
                  {booking.status === 'COMPLETED' ? 'Đã thanh toán đủ' : 'Bao gồm chi phí thuê và phụ phí'}
                </span>
              </div>

              {/* Policy Snapshot breakdown */}
              <div className="space-y-2 text-xs border-t border-slate-100 pt-3">
                <div className="flex justify-between text-slate-600">
                  <span>Giá gốc 1 slot:</span>
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
                    <span>Vượt giờ cho phép:</span>
                    <strong className="text-slate-900">{booking.allowedOvertimeMinutes} phút</strong>
                  </div>
                )}
                {booking.freeRadiusKm != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Bán kính miễn phí:</span>
                    <strong className="text-slate-900">{booking.freeRadiusKm} km</strong>
                  </div>
                )}
                {booking.outOfRadiusRate != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Phụ thu ngoài bán kính:</span>
                    <strong className="text-slate-900 font-mono">{formatVnd(booking.outOfRadiusRate)} / km</strong>
                  </div>
                )}
                {booking.waitingFreeMinutes != null && (
                  <div className="flex justify-between text-slate-600">
                    <span>Chờ miễn phí:</span>
                    <strong className="text-slate-900">{booking.waitingFreeMinutes} phút</strong>
                  </div>
                )}
              </div>

              {/* Payment Transactions list */}
              {booking.paymentTransactions && booking.paymentTransactions.length > 0 && (
                <div className="border-t border-slate-100 pt-3 space-y-2">
                  <span className="text-xs font-bold text-slate-800 block">Lịch sử giao dịch:</span>
                  <div className="space-y-2">
                    {booking.paymentTransactions.map((tx) => (
                      <div key={tx.id} className="rounded-lg border border-slate-100 bg-slate-50 p-2.5 text-xs flex justify-between items-center">
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

            {/* Booking Timeline Card */}
            <div className="rounded-2xl border border-[#dbece0] bg-white p-6 shadow-2xs space-y-4">
              <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <Icon name="timeline" className="text-[20px] text-[#064e3b]" />
                <span>Tiến trình đơn thuê</span>
              </h2>

              <div className="relative pl-6 space-y-5 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200">
                {booking.timeline && booking.timeline.map((event, idx) => {
                  const isDone = event.status === 'COMPLETED'
                  const isCurrent = event.status === 'CURRENT'
                  const isError = event.status === 'ERROR' || event.status === 'CANCELLED'

                  return (
                    <div key={idx} className="relative">
                      {/* Dot icon */}
                      <span
                        className={`absolute -left-6 top-0.5 flex h-5 w-5 items-center justify-center rounded-full text-white text-[12px] shadow-2xs ${isError
                            ? 'bg-red-500'
                            : isDone
                              ? 'bg-emerald-600'
                              : isCurrent
                                ? 'bg-amber-500 animate-pulse'
                                : 'bg-slate-300'
                          }`}
                      >
                        <Icon name={event.icon || 'circle'} className="text-[12px]" />
                      </span>

                      <div>
                        <div className="flex items-center justify-between">
                          <span className={`text-xs font-bold ${isError ? 'text-red-700' : 'text-slate-800'}`}>
                            {event.title}
                          </span>
                          <span className="text-[10px] text-slate-400 font-mono">
                            {formatDateTime(event.timestamp)}
                          </span>
                        </div>
                        <p className="text-[11px] text-slate-500 mt-0.5">{event.description}</p>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        </div>
      </div>

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
    </ProviderLayout>
  )
}
