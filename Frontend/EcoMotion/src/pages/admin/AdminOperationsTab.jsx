import { AdminRefundReviewPanel } from '../../components/booking/FinancialReviewPanels'
import { disputeVerdictLabel } from '../../utils/disputeVerdict'
import { useState, useEffect } from 'react'
import { adminService } from '../../services/adminService'
import {
  IconClock,
  IconAlertTriangle,
  IconWallet,
  IconSearch,
  IconX,
  IconImage,
} from '../../components/icons/Icons'
import BookingDetailModal from './modals/BookingDetailModal'
import DisputeDetailModal from './modals/DisputeDetailModal'
import PaymentDetailModal from './modals/PaymentDetailModal'
import AdminModalShell from './modals/AdminModalShell'
import AdminPopupModal from './modals/AdminPopupModal'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

function countEvidence(urls) {
  if (!urls) return 0
  if (Array.isArray(urls)) return urls.filter(Boolean).length
  if (typeof urls === 'string') {
    const trimmed = urls.trim()
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed)
        if (Array.isArray(parsed)) return parsed.filter(Boolean).length
      } catch {}
    }
    return trimmed ? 1 : 0
  }
  return 0
}

const BOOKING_STATUS_BADGES = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  ACCEPTED: 'bg-blue-50 text-blue-700 border-blue-200',
  CONFIRMED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200',
  DISPUTED: 'bg-purple-50 text-purple-700 border-purple-200',
}

const DISPUTE_STATUS_BADGES = {
  OPEN: 'bg-rose-50 text-rose-700 border-rose-200',
  IN_REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
  RESOLVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  REJECTED: 'bg-slate-100 text-slate-700 border-slate-200',
}

export default function AdminOperationsTab({
  initialSubTab = 'bookings',
  initialFilter = {},
  onStatsUpdate,
  onNavigate,
}) {
  const [subTab, setSubTab] = useState(initialSubTab)
  const [loading, setLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [message, setMessage] = useState(null)

  // SubTab 1: Bookings
  const [bookings, setBookings] = useState([])
  const [bookingStatus, setBookingStatus] = useState(initialFilter.status || 'ALL')
  const [bookingKeyword, setBookingKeyword] = useState('')
  const [selectedBooking, setSelectedBooking] = useState(null)

  // SubTab 2: Disputes
  const [disputes, setDisputes] = useState([])
  const [disputeStatus, setDisputeStatus] = useState(initialFilter.status || 'ALL')
  const [resolvingDispute, setResolvingDispute] = useState(null)

  // SubTab 3: Payments & Refund
  const [payments, setPayments] = useState([])
  const [selectedPayment, setSelectedPayment] = useState(null)
  const [showRefundModal, setShowRefundModal] = useState(false)
  const [refundForm, setRefundForm] = useState({ bookingId: '', amount: '', reason: '' })
  const [popupModal, setPopupModal] = useState(null)

  useEffect(() => {
    if (!initialSubTab) return undefined
    const timerId = window.setTimeout(() => setSubTab(initialSubTab), 0)
    return () => window.clearTimeout(timerId)
  }, [initialSubTab])

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      if (initialFilter?.targetBookingId) {
        setSubTab('bookings')
        setBookingStatus('ALL')
        openBookingDetail(initialFilter.targetBookingId)
      } else if (initialFilter?.targetDisputeId) {
        setSubTab('disputes')
        setDisputeStatus('ALL')
        adminService.getDisputes('ALL').then((list) => {
          setDisputes(list)
          const found = list.find((d) => Number(d.id) === Number(initialFilter.targetDisputeId))
          if (found) setResolvingDispute(found)
        })
      }
    }, 0)
    return () => window.clearTimeout(timerId)
  }, [initialFilter?.navKey, initialFilter?.targetBookingId, initialFilter?.targetDisputeId])

  useEffect(() => {
    loadData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, bookingStatus, disputeStatus])

  async function loadData() {
    setLoading(true)
    setMessage(null)
    try {
      if (subTab === 'bookings') {
        const data = await adminService.getBookings({ status: bookingStatus, keyword: bookingKeyword })
        setBookings(data)
      } else if (subTab === 'disputes') {
        const data = await adminService.getDisputes(disputeStatus)
        setDisputes(data)
      } else if (subTab === 'payments') {
        const data = await adminService.getPayments()
        setPayments(data)
      }
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Không thể tải dữ liệu vận hành' })
    } finally {
      setLoading(false)
    }
  }

  // Booking Detail Modal
  async function openBookingDetail(bookingId) {
    setSelectedBooking(null)
    try {
      const full = await adminService.getBookingDetail(bookingId)
      setSelectedBooking(full)
    } catch (err) {
      setPopupModal({
        mode: 'alert',
        tone: 'error',
        title: 'Không thể tải chi tiết đơn',
        message: err.message || 'Không thể tải chi tiết đơn thuê.',
        confirmText: 'Đóng',
      })
    }
  }

  // Cross-Navigation Helpers
  function navigateToUser(userId, email) {
    setSelectedBooking(null)
    setResolvingDispute(null)
    setSelectedPayment(null)
    if (onNavigate) {
      onNavigate('users', {
        subTab: 'users',
        status: 'ALL',
        targetUserId: userId,
        targetUserEmail: email,
      })
    }
  }

  function navigateToProvider(providerId) {
    setSelectedBooking(null)
    setResolvingDispute(null)
    setSelectedPayment(null)
    if (onNavigate) {
      onNavigate('users', {
        subTab: 'providers',
        status: 'ALL',
        targetProviderId: providerId,
      })
    }
  }

  function navigateToDriver(driverId) {
    setSelectedBooking(null)
    setResolvingDispute(null)
    setSelectedPayment(null)
    if (onNavigate) {
      onNavigate('users', {
        subTab: 'drivers',
        status: 'ALL',
        targetDriverId: driverId,
      })
    }
  }

  function navigateToVehicle(vehicleId) {
    setSelectedBooking(null)
    setResolvingDispute(null)
    setSelectedPayment(null)
    if (onNavigate) {
      onNavigate('vehicles', {
        status: 'ALL',
        targetVehicleId: vehicleId,
      })
    }
  }

  // Dispute Resolve
  async function handleResolveDispute(disputeId, status, decision, resolution, refundAwardTotal, tripEndAction, correctedEndAt) {
    setActionLoading(true)
    try {
      await adminService.resolveDispute(
        disputeId,
        status,
        decision,
        resolution,
        refundAwardTotal,
        tripEndAction,
        correctedEndAt,
      )
      setMessage({ type: 'success', text: 'Đã cập nhật phân xử tranh chấp thành công!' })
      setResolvingDispute(null)
      loadData()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi khi phân xử khiếu nại' })
    } finally {
      setActionLoading(false)
    }
  }

  // Refund Submit
  async function handleRefundSubmit(e) {
    e.preventDefault()
    const amountNum = parseFloat(refundForm.amount)
    if (!refundForm.bookingId || isNaN(amountNum) || amountNum <= 0) {
      setPopupModal({
        mode: 'alert',
        tone: 'warning',
        title: 'Thông tin hoàn tiền chưa hợp lệ',
        message: 'Vui lòng nhập Mã đơn hợp lệ và số tiền hoàn lớn hơn 0.',
        confirmText: 'Đã hiểu',
      })
      return
    }
    if (!refundForm.reason.trim()) {
      setPopupModal({
        mode: 'alert',
        tone: 'warning',
        title: 'Thiếu lý do hoàn tiền',
        message: 'Vui lòng nhập lý do hoàn tiền.',
        confirmText: 'Đã hiểu',
      })
      return
    }

    setActionLoading(true)
    try {
      await adminService.refundBooking(refundForm.bookingId, amountNum, refundForm.reason.trim())
      setMessage({ type: 'success', text: `Đã tạo yêu cầu hoàn ${formatVND(amountNum)} cho đơn #${refundForm.bookingId}. Trạng thái hoàn tiền cập nhật riêng và chưa có nghĩa là tiền đã về.` })
      setShowRefundModal(false)
      setRefundForm({ bookingId: '', amount: '', reason: '' })
      loadData()
      if (onStatsUpdate) onStatsUpdate()
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Lỗi khi thực hiện hoàn tiền' })
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      <AdminRefundReviewPanel />
      {/* Sub Navigation Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-4">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setSubTab('bookings')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'bookings'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconClock className="h-4 w-4" />
            <span>Đơn thuê (Bookings)</span>
          </button>

          <button
            type="button"
            onClick={() => setSubTab('disputes')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'disputes'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconAlertTriangle className="h-4 w-4" />
            <span>Khiếu nại &amp; Dispute</span>
          </button>

          <button
            type="button"
            onClick={() => setSubTab('payments')}
            className={`inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
              subTab === 'payments'
                ? 'bg-brand-600 text-white shadow-xs'
                : 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50'
            }`}
          >
            <IconWallet className="h-4 w-4" />
            <span>Lịch sử thanh toán</span>
          </button>
        </div>

        <button
          type="button"
          onClick={loadData}
          disabled={loading}
          className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 transition-all hover:bg-slate-50"
        >
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </div>

      {/* Inline Feedback Message */}
      {message && (
        <div
          className={`flex items-center justify-between gap-3 rounded-xl border p-4 text-xs font-medium ${
            message.type === 'error'
              ? 'border-red-200 bg-red-50 text-red-800'
              : 'border-emerald-200 bg-emerald-50 text-emerald-800'
          }`}
        >
          <span>{message.text}</span>
          <button type="button" onClick={() => setMessage(null)} className="p-1 hover:opacity-75">
            <IconX className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* 1. BOOKINGS QUEUE */}
      {subTab === 'bookings' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-card">
            <form
              onSubmit={(e) => {
                e.preventDefault()
                loadData()
              }}
              className="relative min-w-[240px] flex-1 max-w-sm"
            >
              <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                <IconSearch className="h-4 w-4" />
              </span>
              <input
                type="text"
                placeholder="Tìm theo mã đơn (BK-...), điểm đón..."
                value={bookingKeyword}
                onChange={(e) => setBookingKeyword(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 pl-9 pr-3 text-xs outline-none focus:border-brand-500 focus:bg-white"
              />
            </form>

            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-semibold text-slate-500">Trạng thái:</span>
              {['ALL', 'PENDING', 'ACCEPTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'DISPUTED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setBookingStatus(st)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                    bookingStatus === st
                      ? 'bg-brand-600 text-white shadow-xs'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {st === 'ALL' ? 'Tất cả' : st}
                </button>
              ))}
            </div>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-slate-200 bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3.5">Mã đơn</th>
                    <th className="px-5 py-3.5">Thời gian đón</th>
                    <th className="px-5 py-3.5">Địa chỉ đón</th>
                    <th className="px-5 py-3.5">Khách thuê</th>
                    <th className="px-5 py-3.5">Tài xế</th>
                    <th className="px-5 py-3.5">Tổng tiền</th>
                    <th className="px-5 py-3.5">Trạng thái</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {bookings.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="py-8 text-center text-slate-400">
                        {loading ? 'Đang tải danh sách đơn...' : 'Không có đơn thuê nào phù hợp'}
                      </td>
                    </tr>
                  ) : (
                    bookings.map((b) => (
                      <tr key={b.id} className="transition-colors hover:bg-slate-50/70">
                        <td className="px-5 py-3.5 font-mono font-bold text-slate-900">
                          <button
                            type="button"
                            onClick={() => openBookingDetail(b.id)}
                            className="hover:text-brand-600 hover:underline"
                          >
                            {b.bookingCode}
                          </button>
                        </td>
                        <td className="px-5 py-3.5 text-slate-600">
                          {b.pickupTime ? new Date(b.pickupTime).toLocaleString('vi-VN') : '—'}
                        </td>
                        <td className="px-5 py-3.5 max-w-[180px] truncate text-slate-700" title={b.pickupAddress}>
                          {b.pickupAddress || '—'}
                        </td>
                        <td className="px-5 py-3.5">
                          {(b.renterUserId || b.renterEmail) ? (
                            <button
                              type="button"
                              onClick={() => navigateToUser(b.renterUserId, b.renterEmail)}
                              className="font-semibold text-brand-700 hover:underline text-left"
                            >
                              {b.renterName || `Khách #${b.renterId}`}
                            </button>
                          ) : (
                            <span className="font-semibold text-slate-700">
                              {b.renterName || `${b.passengerCount} người`}
                            </span>
                          )}
                        </td>
                        <td className="px-5 py-3.5">
                          {b.driverId ? (
                            <button
                              type="button"
                              onClick={() => navigateToDriver(b.driverId)}
                              className="font-semibold text-amber-700 hover:underline text-left"
                            >
                              {b.driverName || `Tài xế #${b.driverId}`}
                            </button>
                          ) : (
                            <span className="text-slate-400 italic">Chưa gán</span>
                          )}
                        </td>
                        <td className="px-5 py-3.5 font-bold text-slate-900">{formatVND(b.totalAmount)}</td>
                        <td className="px-5 py-3.5">
                          <span
                            className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                              BOOKING_STATUS_BADGES[b.status] || 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {b.status}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 text-right">
                          <button
                            type="button"
                            onClick={() => openBookingDetail(b.id)}
                            className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-700 hover:border-brand-500 hover:text-brand-600"
                          >
                            Chi tiết
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 2. DISPUTES TAB */}
      {subTab === 'disputes' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-card">
            <span className="text-xs font-bold text-slate-600">Lọc theo trạng thái tranh chấp:</span>
            <div className="flex gap-2">
              {['ALL', 'OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED'].map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setDisputeStatus(st)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                    disputeStatus === st
                      ? 'bg-brand-600 text-white shadow-xs'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {st === 'ALL' ? 'Tất cả' : st}
                </button>
              ))}
            </div>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-slate-200 bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3.5">Mã khiếu nại</th>
                    <th className="px-5 py-3.5">Đơn hàng</th>
                    <th className="px-5 py-3.5">Danh mục</th>
                    <th className="px-5 py-3.5">Người khiếu nại</th>
                    <th className="px-5 py-3.5">Nội dung &amp; Bằng chứng</th>
                    <th className="px-5 py-3.5">Trạng thái</th>
                    <th className="px-5 py-3.5">Phán quyết / Ghi chú</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {disputes.length === 0 ? (
                    <tr>
                      <td colSpan="8" className="py-8 text-center text-slate-400">
                        {loading ? 'Đang tải danh sách khiếu nại...' : 'Không có khiếu nại nào ở trạng thái này'}
                      </td>
                    </tr>
                  ) : (
                    disputes.map((d) => (
                      <tr key={d.id} className="transition-colors hover:bg-slate-50/70">
                        <td className="px-5 py-3.5 font-bold text-slate-900">DISP-#{d.id}</td>
                        <td className="px-5 py-3.5 font-mono">
                          {d.bookingId ? (
                            <button
                              type="button"
                              onClick={() => openBookingDetail(d.bookingId)}
                              className="font-bold text-brand-700 hover:underline"
                            >
                              #{d.bookingCode || `Đơn #${d.bookingId}`}
                            </button>
                          ) : (
                            <span className="text-slate-600">—</span>
                          )}
                        </td>
                        <td className="px-5 py-3.5">
                          <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-700">
                            {d.category || 'OTHER'}
                          </span>
                        </td>
                        <td className="px-5 py-3.5">
                          {d.raisedById ? (
                            <button
                              type="button"
                              onClick={() => navigateToUser(d.raisedById, d.raisedByEmail)}
                              className="font-semibold text-brand-700 hover:underline text-left"
                            >
                              {d.raisedByName || `User #${d.raisedById}`}
                            </button>
                          ) : (
                            <span className="text-slate-600">{d.raisedByName || '—'}</span>
                          )}
                        </td>
                        <td className="px-5 py-3.5 max-w-[240px]">
                          <p className="line-clamp-2 font-medium text-slate-800" title={d.description || ''}>
                            {d.description || <span className="text-slate-400 italic font-normal">Chưa có mô tả</span>}
                          </p>
                          {countEvidence(d.evidenceUrls) > 0 && (
                            <span className="mt-1 inline-flex items-center gap-1 rounded-md bg-purple-50 px-1.5 py-0.5 text-[10px] font-bold text-purple-700 border border-purple-200">
                              <IconImage className="h-3 w-3" />
                              {countEvidence(d.evidenceUrls)} ảnh bằng chứng
                            </span>
                          )}
                        </td>
                        <td className="px-5 py-3.5">
                          <span
                            className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                              DISPUTE_STATUS_BADGES[d.status] || 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {d.status}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 max-w-[240px] text-slate-700" title={d.resolution}>
                          {disputeVerdictLabel(d) && (
                            <div className="mb-1">
                              <span className={`inline-block rounded-md border px-2 py-0.5 text-[10px] font-bold ${d.status === 'RESOLVED'
                                ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
                                {disputeVerdictLabel(d)}
                              </span>
                            </div>
                          )}
                          {d.status === 'IN_REVIEW' && d.resolution && (
                            <div className="mb-1"><span className="inline-block rounded-md border border-sky-200 bg-sky-50 px-2 py-0.5 text-[10px] font-bold text-sky-800">Đang xử lý theo phương án khác</span></div>
                          )}
                          <div className="truncate">
                            {d.resolution || <span className="text-slate-400 italic">Chưa có kết luận</span>}
                          </div>
                        </td>
                        <td className="px-5 py-3.5 text-right">
                          <button
                            type="button"
                            onClick={() => {
                              setMessage(null)
                              setResolvingDispute(d)

                            }}
                            className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-bold text-white hover:bg-brand-600"
                          >
                            Phân xử &amp; Chi tiết
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* 3. PAYMENTS HISTORY TAB */}
      {subTab === 'payments' && (
        <div className="space-y-4">
          <div className="flex flex-col gap-2 rounded-2xl border border-slate-200 bg-white p-5 shadow-card sm:flex-row sm:items-center sm:justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 block">Lịch sử thanh toán &amp; Dòng tiền giao dịch</span>
              <p className="text-xs text-slate-600">
                Tra cứu lịch sử thanh toán tiền cọc, tiền thuê cơ bản, phụ phí phát sinh và các giao dịch hoàn trả trên hệ thống.
              </p>
            </div>
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-slate-200 bg-slate-50 font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-5 py-3.5">Mã GD</th>
                    <th className="px-5 py-3.5">Mã đơn</th>
                    <th className="px-5 py-3.5">Loại giao dịch</th>
                    <th className="px-5 py-3.5">Hướng</th>
                    <th className="px-5 py-3.5">Số tiền</th>
                    <th className="px-5 py-3.5">Phương thức</th>
                    <th className="px-5 py-3.5">Trạng thái</th>
                    <th className="px-5 py-3.5">Thời gian</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {payments.length === 0 ? (
                    <tr>
                      <td colSpan="9" className="py-8 text-center text-slate-400">
                        {loading ? 'Đang tải lịch sử giao dịch...' : 'Chưa có giao dịch nào'}
                      </td>
                    </tr>
                  ) : (
                    payments.map((p) => (
                      <tr key={p.id} className="transition-colors hover:bg-slate-50/70">
                        <td className="px-5 py-3.5 font-mono font-bold text-slate-900">TXN-#{p.id}</td>
                        <td className="px-5 py-3.5 font-mono">
                          {p.bookingId ? (
                            <button
                              type="button"
                              onClick={() => openBookingDetail(p.bookingId)}
                              className="font-bold text-brand-700 hover:underline"
                            >
                              #{p.bookingCode || `Đơn #${p.bookingId}`}
                            </button>
                          ) : (
                            <span className="text-slate-600">—</span>
                          )}
                        </td>
                        <td className="px-5 py-3.5 font-semibold text-slate-800">{p.type}</td>
                        <td className="px-5 py-3.5">
                          <span
                            className={`inline-block rounded px-2 py-0.5 text-[10px] font-bold ${
                              p.direction === 'OUT' || p.direction === 'OUTBOUND'
                                ? 'bg-rose-100 text-rose-800'
                                : 'bg-emerald-100 text-emerald-800'
                            }`}
                          >
                            {p.direction === 'OUT' || p.direction === 'OUTBOUND' ? 'Tiền ra (OUT)' : 'Thu vào (IN)'}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 font-bold text-slate-900">
                          {p.direction === 'OUT' || p.direction === 'OUTBOUND' ? '-' : '+'}
                          {formatVND(p.amount)}
                        </td>
                        <td className="px-5 py-3.5 text-slate-600">{p.method || 'VNPAY'}</td>
                        <td className="px-5 py-3.5">
                          <span className="inline-block rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-700">
                            {p.status}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 text-slate-500">
                          {p.paidAt ? new Date(p.paidAt).toLocaleString('vi-VN') : '—'}
                        </td>
                        <td className="px-5 py-3.5 text-right whitespace-nowrap">
                          <button
                            type="button"
                            onClick={() => setSelectedPayment(p)}
                            className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-all"
                          >
                            Chi tiết
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Operation Detail Modals */}
      <BookingDetailModal
        booking={selectedBooking}
        onClose={() => setSelectedBooking(null)}
        onSelectRenter={(b) => navigateToUser(b.renterUserId, b.renterEmail)}
        onSelectVehicle={(vehicleId) => navigateToVehicle(vehicleId)}
        onSelectProvider={(providerId) => navigateToProvider(providerId)}
        onSelectDriver={(driverId) => navigateToDriver(driverId)}
      />

      <DisputeDetailModal
        dispute={resolvingDispute}
          onChanged={loadData}
        error={message?.type === 'error' ? message.text : ''}
        onClose={() => setResolvingDispute(null)}
        onResolve={handleResolveDispute}
        actionLoading={actionLoading}
        onSelectBooking={(bookingId) => {
          setResolvingDispute(null)
          openBookingDetail(bookingId)
        }}
        onSelectUser={(userId) => navigateToUser(userId)}
        onSelectVehicle={(vehicleId) => navigateToVehicle(vehicleId)}
        onSelectProvider={(providerId) => navigateToProvider(providerId)}
        onSelectDriver={(driverId) => navigateToDriver(driverId)}
      />

      <PaymentDetailModal
        payment={selectedPayment}
        onClose={() => setSelectedPayment(null)}
        onSelectBooking={(bookingId) => {
          setSelectedPayment(null)
          openBookingDetail(bookingId)
        }}
        onSelectUser={(userId) => navigateToUser(userId)}
        onSelectVehicle={(vehicleId) => navigateToVehicle(vehicleId)}
        onSelectProvider={(providerId) => navigateToProvider(providerId)}
        onSelectDriver={(driverId) => navigateToDriver(driverId)}
      />

      {/* Mock Refund Modal */}
      {showRefundModal && (
        <AdminModalShell
          maxWidth="max-w-md"
          header={(
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  Tạo giao dịch hoàn tiền (Mock Refund)
                </h3>
                <span className="text-xs text-slate-500">
                  Mô phỏng hoàn tiền về ví/thẻ của khách thuê qua cổng thanh toán
                </span>
              </div>
              <button
                type="button"
                onClick={() => setShowRefundModal(false)}
                className="rounded-lg p-1 text-slate-400 hover:text-slate-600"
              >
                <IconX className="h-5 w-5" />
              </button>
            </div>
          )}
          footer={(
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowRefundModal(false)}
                className="rounded-xl border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Hủy
              </button>
              <button
                type="submit"
                form="admin-mock-refund-form"
                disabled={actionLoading}
                className="rounded-xl bg-rose-600 px-4 py-2 text-xs font-bold text-white hover:bg-rose-700 disabled:opacity-50"
              >
                {actionLoading ? 'Đang thực hiện...' : 'Xác nhận hoàn tiền'}
              </button>
            </div>
          )}
        >
          <form id="admin-mock-refund-form" onSubmit={handleRefundSubmit} noValidate className="space-y-3 text-xs">
            <div>
              <label className="mb-1 block font-bold text-slate-700">
                Mã đơn hàng (Booking ID) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                required
                placeholder="Ví dụ: 1"
                value={refundForm.bookingId}
                onChange={(e) => setRefundForm((prev) => ({ ...prev, bookingId: e.target.value }))}
                className="w-full rounded-xl border border-slate-300 p-2.5 outline-none focus:border-rose-500"
              />
            </div>

            <div>
              <label className="mb-1 block font-bold text-slate-700">
                Số tiền hoàn (VND) <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                required
                placeholder="Ví dụ: 100000"
                value={refundForm.amount}
                onChange={(e) => setRefundForm((prev) => ({ ...prev, amount: e.target.value }))}
                className="w-full rounded-xl border border-slate-300 p-2.5 outline-none focus:border-rose-500"
              />
            </div>

            <div>
              <label className="mb-1 block font-bold text-slate-700">
                Lý do hoàn tiền <span className="text-red-500">*</span>
              </label>
              <textarea
                rows="3"
                required
                placeholder="Ví dụ: Khách huỷ đơn trước hạn, Bồi hoàn phụ phí theo phán quyết dispute..."
                value={refundForm.reason}
                onChange={(e) => setRefundForm((prev) => ({ ...prev, reason: e.target.value }))}
                className="w-full rounded-xl border border-slate-300 p-2.5 outline-none focus:border-rose-500"
              />
            </div>
          </form>
        </AdminModalShell>
      )}

      {/* Custom Popup Modal for Alerts / Confirmations */}
      <AdminPopupModal
        open={Boolean(popupModal)}
        mode={popupModal?.mode}
        tone={popupModal?.tone}
        title={popupModal?.title}
        message={popupModal?.message}
        confirmText={popupModal?.confirmText}
        cancelText={popupModal?.cancelText}
        loading={actionLoading}
        onClose={() => setPopupModal(null)}
        onConfirm={popupModal?.onConfirm}
      />
    </div>
  )
}
