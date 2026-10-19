import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { bookingService } from '../services/bookingService'
import { useAuth } from '../hooks/useAuth'
import { useProviderBookingDetail } from '../hooks/useProviderBookingDetail'
import Toast from '../components/Toast'
import {
  IconCar,
  IconArrowLeft,
  IconCheckCircle,
  IconAlertTriangle,
  IconShield,
} from '../components/icons/Icons'

import BookingOverviewCard from '../components/booking/BookingOverviewCard'
import CustomerInfoCard from '../components/booking/CustomerInfoCard'
import VehicleInfoCard from '../components/booking/VehicleInfoCard'
import DriverAssignmentCard from '../components/booking/DriverAssignmentCard'
import BookingChargesCard from '../components/booking/BookingChargesCard'
import BookingTimelineCard from '../components/booking/BookingTimelineCard'
import AcceptBookingModal from '../components/booking/AcceptBookingModal'
import RejectBookingModal from '../components/booking/RejectBookingModal'
import AssignDriverModal from '../components/booking/AssignDriverModal'
import AddChargeModal from '../components/booking/AddChargeModal'

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

export default function ProviderBookingDetailPage() {
  const { bookingId } = useParams()
  const { logout } = useAuth()
  const navigate = useNavigate()

  // Booking details from custom hook
  const { booking, loading, error, reload: fetchDetail } = useProviderBookingDetail(bookingId)
  const [toast, setToast] = useState(null)

  // Modals
  const [showAcceptModal, setShowAcceptModal] = useState(false)
  const [showRejectModal, setShowRejectModal] = useState(false)
  const [showAssignDriverModal, setShowAssignDriverModal] = useState(false)
  const [showAddChargeModal, setShowAddChargeModal] = useState(false)

  // Submitting flags
  const [submittingAction, setSubmittingAction] = useState(false)
  const [deletingChargeId, setDeletingChargeId] = useState(null)

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  function showToastMessage(message, type = 'success') {
    setToast({ message, type })
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

  // Action handlers
  async function handleAcceptConfirm() {
    setSubmittingAction(true)
    try {
      await bookingService.acceptBooking(bookingId)
      showToastMessage('Đã chấp nhận đơn đặt xe thành công!', 'success')
      setShowAcceptModal(false)
      await fetchDetail()
    } catch (err) {
      showToastMessage(err.message || 'Không thể chấp nhận đơn. Vui lòng thử lại.', 'error')
    } finally {
      setSubmittingAction(false)
    }
  }

  async function handleRejectConfirm(payload) {
    setSubmittingAction(true)
    try {
      await bookingService.rejectBooking(bookingId, payload)
      showToastMessage('Đã từ chối đơn đặt xe thành công.', 'success')
      setShowRejectModal(false)
      await fetchDetail()
    } catch (err) {
      showToastMessage(err.message || 'Không thể từ chối đơn đặt xe.', 'error')
    } finally {
      setSubmittingAction(false)
    }
  }

  async function handleAssignDriverConfirm(driverId) {
    setSubmittingAction(true)
    try {
      await bookingService.assignDriver(bookingId, driverId)
      showToastMessage('Đã chỉ định tài xế thành công!', 'success')
      setShowAssignDriverModal(false)
      await fetchDetail()
    } catch (err) {
      showToastMessage(err.message || 'Không thể chỉ định tài xế.', 'error')
    } finally {
      setSubmittingAction(false)
    }
  }

  async function handleAddChargeConfirm(payload) {
    setSubmittingAction(true)
    try {
      await bookingService.addManualCharge(bookingId, payload)
      showToastMessage('Đã thêm phụ phí thành công!', 'success')
      setShowAddChargeModal(false)
      await fetchDetail()
    } catch (err) {
      showToastMessage(err.message || 'Không thể thêm phụ phí.', 'error')
    } finally {
      setSubmittingAction(false)
    }
  }

  async function handleDeleteCharge(chargeId) {
    if (!window.confirm('Bạn có chắc chắn muốn xóa khoản phụ phí này? Tổng tiền sẽ được cập nhật lại.')) {
      return
    }
    setDeletingChargeId(chargeId)
    try {
      await bookingService.removeManualCharge(bookingId, chargeId)
      showToastMessage('Đã xóa phụ phí và cập nhật lại tổng tiền.', 'success')
      await fetchDetail()
    } catch (err) {
      showToastMessage(err.message || 'Không thể xóa phụ phí.', 'error')
    } finally {
      setDeletingChargeId(null)
    }
  }

  const isPending = booking?.status === 'PENDING'
  const isConfirmed = booking?.status === 'CONFIRMED'
  const isInProgress = booking?.status === 'IN_PROGRESS'
  const isSelfDrive = booking?.plan?.planType === 'YEARLY'

  return (
    <div className="min-h-screen bg-slate-50 pb-16">
      {/* Toast Notification */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

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

      {/* Main Content Area */}
      <main className="mx-auto max-w-6xl px-6 pt-6">
        {/* Breadcrumb & Navigation */}
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Link to="/provider" className="hover:text-emerald-600">
              Provider
            </Link>
            <span>/</span>
            <Link to="/provider/bookings" className="hover:text-emerald-600">
              Booking Queue
            </Link>
            <span>/</span>
            <span className="font-semibold text-slate-800">
              #{booking?.bookingCode || bookingId}
            </span>
          </div>

          <Link
            to="/provider/bookings"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-emerald-700 transition"
          >
            <IconArrowLeft className="h-4 w-4" />
            Quay lại danh sách đơn
          </Link>
        </div>

        {/* Loading State */}
        {loading ? (
          <div className="space-y-6 animate-pulse py-6">
            <div className="h-14 rounded-xl bg-slate-200" />
            <div className="grid gap-6 lg:grid-cols-3">
              <div className="space-y-6 lg:col-span-2">
                <div className="h-48 rounded-xl bg-slate-200" />
                <div className="h-40 rounded-xl bg-slate-200" />
                <div className="h-40 rounded-xl bg-slate-200" />
              </div>
              <div className="space-y-6">
                <div className="h-64 rounded-xl bg-slate-200" />
                <div className="h-48 rounded-xl bg-slate-200" />
              </div>
            </div>
          </div>
        ) : error ? (
          /* Error State */
          <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center my-8">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
              <IconAlertTriangle className="h-6 w-6" />
            </div>
            <h3 className="text-base font-bold text-red-900">Không thể tải thông tin đơn đặt xe</h3>
            <p className="mt-1 text-sm text-red-700">{error}</p>
            <div className="mt-5 flex justify-center gap-3">
              <button
                type="button"
                onClick={fetchDetail}
                className="rounded-lg bg-red-600 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-red-700"
              >
                Thử lại
              </button>
              <Link
                to="/provider/bookings"
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                Trở về hàng đợi
              </Link>
            </div>
          </div>
        ) : booking ? (
          <>
            {/* Header Card with Status & Action Bar */}
            <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
                      Đơn đặt xe #{booking.bookingCode}
                    </h1>
                    <span
                      className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-bold ${
                        STATUS_BADGE_STYLES[booking.status] || 'bg-slate-100 text-slate-700 border-slate-200'
                      }`}
                    >
                      {STATUS_LABELS[booking.status] || booking.status}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Khởi tạo lúc: <span className="font-medium text-slate-700">{formatDateTime(booking.createdAt)}</span>
                  </p>
                </div>

                {/* Conditional Action Buttons */}
                <div className="flex flex-wrap items-center gap-2.5">
                  {isPending && (
                    <>
                      <button
                        type="button"
                        onClick={() => setShowAcceptModal(true)}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-emerald-700"
                      >
                        <IconCheckCircle className="h-4 w-4" />
                        Duyệt đơn (Accept)
                      </button>
                      <button
                        type="button"
                        onClick={() => setShowRejectModal(true)}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-4 py-2 text-xs font-bold text-red-600 transition hover:bg-red-50 hover:border-red-400"
                      >
                        Từ chối (Reject)
                      </button>
                    </>
                  )}

                  {isConfirmed && !isSelfDrive && (
                    <button
                      type="button"
                      onClick={() => setShowAssignDriverModal(true)}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-bold text-white shadow-sm transition hover:bg-indigo-700"
                    >
                      {booking.assignedDriver ? 'Đổi tài xế' : '+ Gán tài xế'}
                    </button>
                  )}

                  {(isConfirmed || isInProgress) && (
                    <button
                      type="button"
                      onClick={() => setShowAddChargeModal(true)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-4 py-2 text-xs font-bold text-slate-700 transition hover:bg-slate-50 hover:border-slate-400"
                    >
                      + Thêm phụ phí
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* 2-Column Responsive Layout */}
            <div className="grid gap-6 lg:grid-cols-3">
              {/* Left Column (2 Cols on Desktop) */}
              <div className="space-y-6 lg:col-span-2">
                <BookingOverviewCard
                  booking={booking}
                  formatDateTime={formatDateTime}
                />

                <CustomerInfoCard renter={booking.renter} />

                <VehicleInfoCard
                  vehicle={booking.vehicle}
                  plan={booking.plan}
                  formatCurrency={formatCurrency}
                />

                <DriverAssignmentCard
                  assignedDriver={booking.assignedDriver}
                  planType={booking.plan?.planType}
                  bookingStatus={booking.status}
                  onOpenAssignDriverModal={() => setShowAssignDriverModal(true)}
                />

                <BookingTimelineCard
                  timeline={booking.timeline}
                  formatDateTime={formatDateTime}
                />
              </div>

              {/* Right Column (1 Col on Desktop) */}
              <div className="space-y-6">
                <BookingChargesCard
                  financials={booking.financials}
                  bookingStatus={booking.status}
                  formatCurrency={formatCurrency}
                  formatDateTime={formatDateTime}
                  onOpenAddChargeModal={() => setShowAddChargeModal(true)}
                  onDeleteCharge={handleDeleteCharge}
                  deletingChargeId={deletingChargeId}
                />

                {/* Rental Policy Card */}
                {booking.policySnapshot && (
                  <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm text-xs space-y-2.5">
                    <h4 className="font-bold text-slate-900 flex items-center gap-1.5">
                      <IconShield className="h-4 w-4 text-emerald-600" />
                      Chính sách hủy & Đặt cọc
                    </h4>
                    <p className="text-slate-600 leading-relaxed">
                      {booking.policySnapshot.cancellationPolicy || 'Áp dụng chính sách hủy xe tiêu chuẩn của EcoMotion.'}
                    </p>
                    {booking.policySnapshot.depositRequired && (
                      <div className="rounded bg-amber-50 p-2.5 border border-amber-200 text-amber-900">
                        Yêu cầu đặt cọc trước:{' '}
                        <strong>
                          {formatCurrency(booking.policySnapshot.depositAmount)}
                        </strong>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </>
        ) : null}
      </main>

      {/* Interactive Modals */}
      <AcceptBookingModal
        isOpen={showAcceptModal}
        onClose={() => setShowAcceptModal(false)}
        onConfirm={handleAcceptConfirm}
        booking={booking}
        submitting={submittingAction}
        formatDateTime={formatDateTime}
      />

      <RejectBookingModal
        isOpen={showRejectModal}
        onClose={() => setShowRejectModal(false)}
        onConfirm={handleRejectConfirm}
        booking={booking}
        submitting={submittingAction}
      />

      <AssignDriverModal
        isOpen={showAssignDriverModal}
        onClose={() => setShowAssignDriverModal(false)}
        onConfirm={handleAssignDriverConfirm}
        bookingId={booking?.bookingCode || booking?.bookingId || bookingId}
        bookingCode={booking?.bookingCode || bookingId}
        currentDriverId={booking?.assignedDriver?.driverId}
        submitting={submittingAction}
      />

      <AddChargeModal
        isOpen={showAddChargeModal}
        onClose={() => setShowAddChargeModal(false)}
        onConfirm={handleAddChargeConfirm}
        bookingCode={booking?.bookingCode || bookingId}
        submitting={submittingAction}
        formatCurrency={formatCurrency}
      />
    </div>
  )
}
