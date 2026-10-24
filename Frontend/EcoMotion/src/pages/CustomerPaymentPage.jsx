import { useState, useEffect, useCallback } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import Toast from '../components/Toast'
import { paymentService } from '../services/paymentService'
import { getMyBooking, bookingService } from '../services/bookingService'

function formatVnd(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '0 ₫'
  return Number(amount).toLocaleString('vi-VN') + ' ₫'
}

function formatDateTime(isoString) {
  if (!isoString) return '—'
  try {
    const d = new Date(isoString)
    if (Number.isNaN(d.getTime())) return isoString
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

function Icon({ name, className = '' }) {
  return <span className={`material-symbols-rounded select-none ${className}`}>{name}</span>
}

export default function CustomerPaymentPage() {
  const { bookingId } = useParams()
  const navigate = useNavigate()

  const [booking, setBooking] = useState(null)
  const [settlement, setSettlement] = useState(null)
  const [loading, setLoading] = useState(true)
  const [paymentCreating, setPaymentCreating] = useState(false)
  const [fetchError, setFetchError] = useState(null)
  const [inlineError, setInlineError] = useState(null)
  const [toast, setToast] = useState({ message: '', type: 'success' })

  // Fetch Booking Details reusing existing single-booking detail API
  const fetchBooking = useCallback(async () => {
    if (!bookingId) return
    try {
      setLoading(true)
      setFetchError(null)
      setInlineError(null)
      // Call getMyBooking directly or fallback to bookingService method
      const fetchFn = getMyBooking || bookingService.getMyBooking || bookingService.getBookingDetail
      const data = await fetchFn(bookingId)
      setBooking(data)

      // If booking is waiting for final payment, fetch settlement breakdown
      if (data?.status === 'AWAITING_FINAL_PAYMENT') {
        try {
          const sett = await paymentService.getSettlement(data.bookingCode || bookingId)
          setSettlement(sett)
        } catch (settErr) {
          console.warn('Failed to load settlement details:', settErr)
        }
      }
    } catch (err) {
      console.error('Failed to load booking for payment:', err)
      const status = err?.status || err?.original?.response?.status || err?.response?.status
      const message =
        err?.response?.data?.message ||
        err?.original?.response?.data?.message ||
        err?.message ||
        'Không thể tải thông tin đơn thuê.'
      setFetchError({ status, message })
    } finally {
      setLoading(false)
    }
  }, [bookingId])

  useEffect(() => {
    let ignore = false
    if (!bookingId) return undefined
    const fetchFn = getMyBooking || bookingService.getMyBooking || bookingService.getBookingDetail
    fetchFn(bookingId)
      .then(async (data) => {
        if (ignore) return
        setBooking(data)
        if (data?.status === 'AWAITING_FINAL_PAYMENT') {
          try {
            const sett = await paymentService.getSettlement(data.bookingCode || bookingId)
            if (!ignore) setSettlement(sett)
          } catch (settErr) {
            console.warn('Failed to load settlement details:', settErr)
          }
        }
      })
      .catch((err) => {
        if (ignore) return
        const status = err?.status || err?.original?.response?.status || err?.response?.status
        const message =
          err?.response?.data?.message ||
          err?.original?.response?.data?.message ||
          err?.message ||
          'Không thể tải thông tin đơn thuê.'
        setFetchError({ status, message })
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })

    return () => {
      ignore = true
    }
  }, [bookingId])

  const isFinalPayment = booking?.status === 'AWAITING_FINAL_PAYMENT'

  // Initiate VNPAY Sandbox Payment
  const handlePayWithVnpay = async () => {
    if (!booking || paymentCreating) return

    const code = booking.bookingCode || bookingId

    setPaymentCreating(true)
    setInlineError(null)

    try {
      // If final payment is 0 VND, complete directly without calling VNPay
      if (isFinalPayment && settlement?.finalPayable === 0) {
        await paymentService.createFinalPaymentUrl(code)
        setToast({ message: 'Đơn thuê đã hoàn tất quyết toán thành công!', type: 'success' })
        setTimeout(() => {
          navigate(`/renter/bookings/${code}`)
        }, 1200)
        return
      }

      const payload = {
        bookingCode: code,
        paymentType: isFinalPayment ? 'FINAL_PAYMENT' : 'DEPOSIT',
        language: 'vn',
      }

      const res = await paymentService.createVnpayPaymentUrl(payload)
      if (res?.paymentUrl) {
        // Redirect browser to VNPAY Sandbox checkout gateway
        window.location.href = res.paymentUrl
      } else if (res?.amount === 0 || !res?.paymentUrl) {
        setToast({ message: 'Đơn thuê đã hoàn tất quyết toán thành công!', type: 'success' })
        setTimeout(() => {
          navigate(`/renter/bookings/${code}`)
        }, 1200)
      } else {
        throw new Error('Cổng VNPAY không trả về đường dẫn thanh toán hợp lệ.')
      }
    } catch (err) {
      console.error('Failed to create VNPAY payment url:', err)
      setInlineError(
        err?.response?.data?.message ||
        err?.original?.response?.data?.message ||
        err?.message ||
        'Không thể kết nối cổng thanh toán VNPAY.'
      )
      setPaymentCreating(false)
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="flex items-center gap-2 text-slate-600 text-sm font-semibold">
          <Icon name="sync" className="animate-spin text-xl text-brand-700" />
          <span>Đang tải thông tin đặt cọc chuyến đi...</span>
        </div>
      </div>
    )
  }

  if (fetchError && !booking) {
    const isUnauthorized = fetchError.status === 401
    const isForbidden = fetchError.status === 403
    const isConflict = fetchError.status === 409
    const isNotFound = fetchError.status === 404
    const isServerError = fetchError.status >= 500

    let errorTitle = 'Lỗi tải dữ liệu đơn thuê'
    let errorDesc = fetchError.message

    if (isUnauthorized) {
      errorTitle = 'Phiên đăng nhập đã hết hạn'
      errorDesc = 'Vui lòng đăng nhập lại tài khoản Renter để tiếp tục thanh toán cho đơn thuê này.'
    } else if (isForbidden) {
      errorTitle = 'Từ chối quyền truy cập'
      errorDesc = 'Bạn không có quyền truy cập hoặc thanh toán cho đơn thuê này.'
    } else if (isConflict) {
      errorTitle = 'Trạng thái đơn không hợp lệ'
      errorDesc = fetchError.message || 'Đơn thuê hiện tại chưa thể thanh toán đặt cọc.'
    } else if (isNotFound) {
      errorTitle = 'Không tìm thấy thông tin đơn thuê'
      errorDesc = `Không tìm thấy thông tin đơn thuê #${bookingId}. Vui lòng kiểm tra lại mã đơn.`
    } else if (isServerError) {
      errorTitle = 'Lỗi máy chủ hệ thống'
      errorDesc = fetchError.message || 'Máy chủ đang gặp sự cố gián đoạn. Vui lòng thử lại sau.'
    }

    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <div className="rounded-lg border border-red-200 bg-white p-6 max-w-md w-full text-center shadow-xs space-y-4">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <Icon name={isForbidden || isUnauthorized ? 'lock' : 'error_outline'} className="text-2xl" />
          </div>
          <h2 className="text-base font-bold text-slate-900">{errorTitle}</h2>
          <p className="text-xs text-slate-500">{errorDesc}</p>
          <div className="flex justify-center gap-3 pt-2">
            {isUnauthorized ? (
              <Link to="/login" className="rounded-xl bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800">
                Đăng nhập
              </Link>
            ) : (
              <>
                <Link to="/renter/bookings" className="rounded-xl bg-slate-100 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200">
                  Đơn thuê của tôi
                </Link>
                <button
                  type="button"
                  onClick={fetchBooking}
                  className="rounded-xl bg-brand-700 px-4 py-2 text-xs font-bold text-white hover:bg-brand-800"
                >
                  Thử lại
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  const isSettled = booking?.status === 'COMPLETED' || settlement?.isSettled === true
  const isDepositPaid = ['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'AWAITING_EXTRA_CHARGE_CONFIRMATION'].includes(booking?.status)
  const isCancelledOrRejected = ['CANCELLED', 'REJECTED'].includes(booking?.status)

  const depositCharge = (booking?.charges || []).find((c) => c.chargeType === 'DEPOSIT')
  const depositAmount = depositCharge != null
    ? Number(depositCharge.amount)
    : (booking?.depositAmount || Math.round((Number(booking?.totalAmount) || 0) * 0.3))

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />

      <div className="mx-auto max-w-2xl space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-bold tracking-tight text-slate-900">
                {isFinalPayment
                  ? 'Quyết toán số dư chuyến đi'
                  : isSettled
                  ? 'Chi tiết quyết toán chuyến đi'
                  : 'Thanh toán tiền đặt cọc'}
              </h1>
              <span className="rounded bg-amber-100 px-2 py-0.5 font-mono text-[11px] font-bold text-amber-900 border border-amber-300">
                VNPAY SANDBOX
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Đơn thuê <span className="font-mono font-bold text-slate-800">#{booking?.bookingCode || bookingId}</span> •{' '}
              {isFinalPayment
                ? 'Quyết toán nghĩa vụ thanh toán cuối cùng qua cổng VNPAY'
                : isSettled
                ? 'Đơn thuê đã hoàn tất toàn bộ nghĩa vụ tài chính'
                : 'Cổng thanh toán trực tuyến VNPAY'}
            </p>
          </div>

          <Link
            to={`/renter/bookings/${booking?.bookingCode || bookingId}`}
            className="text-xs font-semibold text-slate-500 hover:text-slate-800 flex items-center gap-1"
          >
            <Icon name="arrow_back" className="text-[16px]" />
            <span>Chi tiết đơn</span>
          </Link>
        </div>

        {inlineError && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800 flex items-start gap-2">
            <Icon name="error" className="text-red-600 text-[18px] shrink-0 mt-0.5" />
            <span>{inlineError}</span>
          </div>
        )}

        {/* Main Payment Checkout Box */}
        <div className="rounded-lg border border-slate-200 bg-white p-6 sm:p-7 shadow-xs space-y-6">
          {/* Booking Summary */}
          <div className="rounded-xl bg-slate-50 p-4 space-y-3 text-xs border border-slate-200">
            {/* Booking Code & Statuses */}
            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Mã đơn thuê:</span>
              <span className="font-mono font-bold text-slate-900">#{booking?.bookingCode || bookingId}</span>
            </div>

            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Trạng thái đơn:</span>
              <span className="inline-flex items-center gap-1.5 font-bold">
                {booking?.status === 'ACCEPTED' && (
                  <span className="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                    Đã được chấp nhận - Chờ đặt cọc
                  </span>
                )}
                {booking?.status === 'PENDING' && (
                  <span className="text-amber-700 bg-amber-50 px-2 py-0.5 rounded border border-amber-200">
                    Chờ duyệt
                  </span>
                )}
                {isDepositPaid && (
                  <span className="text-blue-700 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
                    Đã cọc - {booking?.status}
                  </span>
                )}
                {isFinalPayment && (
                  <span className="text-purple-700 bg-purple-50 px-2 py-0.5 rounded border border-purple-200">
                    Chờ thanh toán quyết toán
                  </span>
                )}
                {isSettled && (
                  <span className="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                    Đã hoàn thành chuyến đi
                  </span>
                )}
                {isCancelledOrRejected && (
                  <span className="text-rose-700 bg-rose-50 px-2 py-0.5 rounded border border-rose-200">
                    {booking?.status === 'CANCELLED' ? 'Đã hủy' : 'Đã từ chối'}
                  </span>
                )}
              </span>
            </div>

            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Nghĩa vụ tài chính:</span>
              <span className="inline-flex items-center gap-1 font-bold">
                {isSettled ? (
                  <span className="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 flex items-center gap-1">
                    <Icon name="check_circle" className="text-[14px]" />
                    Đã hoàn tất quyết toán (SETTLED)
                  </span>
                ) : isFinalPayment ? (
                  <span className="text-purple-800 bg-purple-50 px-2 py-0.5 rounded border border-purple-200 flex items-center gap-1">
                    <Icon name="account_balance_wallet" className="text-[14px]" />
                    Chờ thanh toán số dư (FINAL_PAYMENT)
                  </span>
                ) : isDepositPaid ? (
                  <span className="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 flex items-center gap-1">
                    <Icon name="check_circle" className="text-[14px]" />
                    Đã thanh toán cọc (DEPOSIT PAID)
                  </span>
                ) : (
                  <span className="text-amber-800 bg-amber-50 px-2 py-0.5 rounded border border-amber-200 flex items-center gap-1">
                    <Icon name="pending" className="text-[14px]" />
                    Chờ đặt cọc (DEPOSIT PENDING)
                  </span>
                )}
              </span>
            </div>

            {/* Vehicle Details */}
            <div className="flex justify-between items-start border-b border-slate-200 pb-2 gap-3">
              <span className="text-slate-500 shrink-0">Thông tin xe:</span>
              <div className="text-right">
                <div className="font-bold text-slate-900 text-sm">
                  {booking?.vehicleName || `${booking?.vehicle?.brand || ''} ${booking?.vehicle?.model || ''}`}
                </div>
                {(booking?.licensePlate || booking?.manufactureYear) && (
                  <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                    {booking?.licensePlate && <span>{booking.licensePlate}</span>}
                    {booking?.licensePlate && booking?.manufactureYear && <span> • </span>}
                    {booking?.manufactureYear && <span>Đời {booking.manufactureYear}</span>}
                  </div>
                )}
              </div>
            </div>

            {/* Provider */}
            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <span className="text-slate-500">Nhà cung cấp (Provider):</span>
              <span className="font-semibold text-slate-800">
                {booking?.providerName || booking?.provider?.displayName || 'Đối tác EcoMotion'}
              </span>
            </div>

            {/* Rental Duration */}
            <div className="flex justify-between items-start border-b border-slate-200 pb-2 gap-3">
              <span className="text-slate-500 shrink-0">Thời gian thuê:</span>
              <div className="text-right space-y-0.5">
                <div>
                  <span className="text-slate-400 text-[10px]">Nhận xe: </span>
                  <strong className="text-slate-800">{formatDateTime(booking?.pickupTime || booking?.scheduledPickupAt)}</strong>
                </div>
                {booking?.endTime && (
                  <div>
                    <span className="text-slate-400 text-[10px]">Trả xe: </span>
                    <strong className="text-slate-800">{formatDateTime(booking?.endTime)}</strong>
                  </div>
                )}
              </div>
            </div>

            <div className="flex justify-between items-start border-b border-slate-200 pb-2">
              <span className="text-slate-500 shrink-0">Địa chỉ đón xe:</span>
              <span className="font-medium text-slate-800 text-right max-w-[280px]">{booking?.pickupAddress}</span>
            </div>

            <div className="flex justify-between items-center pt-1">
              <span className="text-slate-500">Tổng giá trị đơn:</span>
              <span className="font-mono font-semibold text-slate-700">{formatVnd(booking?.totalAmount)}</span>
            </div>
          </div>

          {/* FINAL PAYMENT BREAKDOWN (If Awaiting Final Payment or Settled) */}
          {(isFinalPayment || isSettled) ? (
            <div className="rounded-xl border border-purple-200 bg-purple-50/40 p-5 space-y-3">
              <div className="flex items-center justify-between border-b border-purple-200/80 pb-2">
                <span className="text-xs font-bold uppercase tracking-wider text-purple-900 flex items-center gap-1.5">
                  <Icon name="receipt_long" className="text-purple-700 text-sm" />
                  <span>Bảng chi tiết quyết toán sau chuyến</span>
                </span>
                <span className="text-[11px] font-mono font-bold text-purple-700 bg-purple-100 px-2 py-0.5 rounded border border-purple-200">
                  FINAL SETTLEMENT
                </span>
              </div>

              <div className="space-y-2 text-xs">
                {/* Base Rental */}
                <div className="flex justify-between items-center text-slate-700">
                  <span>Giá thuê cơ bản (Hợp đồng):</span>
                  <span className="font-mono font-semibold">
                    {formatVnd(settlement?.baseRental ?? booking?.totalAmount)}
                  </span>
                </div>

                {/* Surcharges if any */}
                {settlement?.charges && settlement.charges.length > 0 && (
                  <div className="space-y-1.5 pt-1 border-t border-purple-100">
                    <div className="text-[11px] font-bold text-slate-600">Phụ phí phát sinh hợp lệ:</div>
                    {settlement.charges.map((ch, idx) => (
                      <div key={idx} className="flex justify-between items-center text-slate-600 pl-2 text-[11px]">
                        <span>• {ch.description || ch.chargeType}:</span>
                        <span className="font-mono font-medium text-amber-900">+{formatVnd(ch.amount)}</span>
                      </div>
                    ))}
                    <div className="flex justify-between items-center font-semibold text-slate-800 pt-0.5 border-t border-dashed border-purple-200">
                      <span>Tổng phụ phí:</span>
                      <span className="font-mono text-amber-900">+{formatVnd(settlement?.totalCharges || 0)}</span>
                    </div>
                  </div>
                )}

                {/* Deductions: Deposit & Others */}
                <div className="space-y-1 pt-1 border-t border-purple-100">
                  <div className="flex justify-between items-center text-slate-700">
                    <span className="text-emerald-700">Tiền cọc đã thanh toán (khấu trừ):</span>
                    <span className="font-mono font-semibold text-emerald-700">
                      -{formatVnd(settlement?.depositPaid || depositAmount || 0)}
                    </span>
                  </div>
                  {Number(settlement?.otherPaid) > 0 && (
                    <div className="flex justify-between items-center text-slate-700">
                      <span className="text-emerald-700">Các khoản đã thanh toán khác:</span>
                      <span className="font-mono font-semibold text-emerald-700">
                        -{formatVnd(settlement.otherPaid)}
                      </span>
                    </div>
                  )}
                  {Number(settlement?.discountsOrRefunds) > 0 && (
                    <div className="flex justify-between items-center text-slate-700">
                      <span className="text-emerald-700">Giảm trừ / Hoàn tiền:</span>
                      <span className="font-mono font-semibold text-emerald-700">
                        -{formatVnd(settlement.discountsOrRefunds)}
                      </span>
                    </div>
                  )}
                </div>

                {/* Overpayment if any */}
                {Number(settlement?.overpayment) > 0 && (
                  <div className="flex justify-between items-center pt-2 border-t border-purple-200 text-blue-700 font-semibold bg-blue-50/60 p-2 rounded">
                    <span>Khoản hoàn lại cho Renter (Overpayment):</span>
                    <span className="font-mono font-bold">+{formatVnd(settlement.overpayment)}</span>
                  </div>
                )}
              </div>

              {/* Big Payable Highlight Box */}
              <div className="rounded-lg bg-white border border-purple-300 p-4 text-center space-y-1 mt-3 shadow-xs">
                <span className="text-xs font-bold uppercase tracking-wider text-purple-900 block">
                  {isSettled ? 'Số dư đã quyết toán' : 'Số dư cần thanh toán'}
                </span>
                <div className="text-3xl sm:text-4xl font-black font-mono text-purple-900">
                  {formatVnd(settlement?.finalPayable ?? 0)}
                </div>
                <p className="text-[11px] text-slate-500 max-w-sm mx-auto">
                  {isSettled
                    ? 'Toàn bộ nghĩa vụ tài chính của chuyến đi đã hoàn tất thành công.'
                    : settlement?.finalPayable === 0
                    ? 'Tiền cọc đã đủ bù đắp chi phí chuyến đi. Không phát sinh số dư cần thanh toán.'
                    : 'Số dư quyết toán cuối cùng sau khi khấu trừ tiền cọc và cộng các khoản phụ phí phát sinh.'}
                </p>
              </div>
            </div>
          ) : (
            /* DEPOSIT HIGHLIGHT BOX */
            <div className="rounded-lg border border-brand-200 bg-slate-50 p-5 text-center space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-brand-900 block">
                Số tiền cọc cần thanh toán
              </span>
              <div className="text-3xl sm:text-4xl font-black font-mono text-brand-900">
                {formatVnd(depositAmount)}
              </div>
              <p className="text-xs text-slate-600 max-w-sm mx-auto">
                Tiền đặt cọc được giữ an toàn trên cổng VNPAY để xác nhận giữ xe và đảm bảo có tài xế phục vụ đúng giờ.
              </p>
            </div>
          )}

          {/* Payment Method Selection */}
          <div className="space-y-2">
            <label className="text-xs font-bold uppercase tracking-wider text-slate-700 block">
              Phương thức thanh toán
            </label>
            <div className="rounded-lg border-2 border-brand-600 bg-emerald-50/40 p-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-brand-600 bg-white">
                  <div className="h-2.5 w-2.5 rounded-full bg-brand-600"></div>
                </div>
                <div>
                  <div className="text-xs font-bold text-slate-900 flex items-center gap-2">
                    <span>Cổng thanh toán VNPAY Sandbox</span>
                    <span className="rounded bg-brand-100 px-1.5 py-0.5 text-[10px] font-bold text-brand-800">
                      Mặc định
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-500">
                    Thanh toán an toàn qua thẻ ATM nội địa, thẻ quốc tế hoặc QR Pay VNPAY
                  </div>
                </div>
              </div>
              <span className="font-mono text-xs font-black tracking-wider text-brand-800">
                VNPAY
              </span>
            </div>
          </div>

          {/* Sandbox Test Card Guidance Box */}
          <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-4 space-y-2 text-xs">
            <div className="flex items-center gap-1.5 font-bold text-amber-900">
              <Icon name="info" className="text-amber-700 text-base" />
              <span>Thông tin thẻ kiểm thử VNPAY Sandbox (Môi trường DEMO)</span>
            </div>
            <p className="text-[11px] text-amber-800">
              Đây là thông tin thẻ kiểm thử của ngân hàng thử nghiệm NCB, <strong>không phải STK nhận tiền</strong> và không trừ tiền thật. Vui lòng nhập thông tin sau khi được chuyển hướng sang cổng VNPAY:
            </p>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1 font-mono text-[11px] bg-white/70 p-2.5 rounded-lg border border-amber-200">
              <div>
                <span className="text-slate-500 block text-[10px]">Ngân hàng:</span>
                <strong className="text-slate-900">NCB</strong>
              </div>
              <div>
                <span className="text-slate-500 block text-[10px]">Số thẻ:</span>
                <strong className="text-slate-900 select-all">9704198526191432198</strong>
              </div>
              <div>
                <span className="text-slate-500 block text-[10px]">Tên chủ thẻ:</span>
                <strong className="text-slate-900">NGUYEN VAN A</strong>
              </div>
              <div>
                <span className="text-slate-500 block text-[10px]">Ngày phát hành / OTP:</span>
                <strong className="text-slate-900">07/15 • 123456</strong>
              </div>
            </div>
          </div>

          {/* Security Guarantees */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-slate-600">
            <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 border border-slate-100">
              <Icon name="verified_user" className="text-brand-700 text-lg shrink-0" />
              <span>Bảo mật chuẩn SSL / PCI DSS</span>
            </div>
            <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 border border-slate-100">
              <Icon name="credit_card" className="text-brand-700 text-lg shrink-0" />
              <span>Hỗ trợ thẻ ATM nội địa NCB</span>
            </div>
            <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-50 border border-slate-100">
              <Icon name="history" className="text-brand-700 text-lg shrink-0" />
              <span>Đối soát và quyết toán tự động</span>
            </div>
          </div>

          {/* Payment CTA Actions */}
          {isSettled ? (
            <div className="rounded-xl bg-emerald-50 border border-emerald-200 p-4 text-center space-y-2">
              <div className="text-emerald-800 font-bold text-sm flex items-center justify-center gap-1.5">
                <Icon name="check_circle" className="text-emerald-600" />
                <span>Đơn thuê này đã hoàn tất quyết toán thành công!</span>
              </div>
              <p className="text-xs text-slate-600">
                Toàn bộ các khoản thanh toán đã được đối soát. Bạn có thể theo dõi lại hành trình chuyến đi bất cứ lúc nào.
              </p>
              <Link
                to={`/renter/bookings/${booking?.bookingCode || bookingId}`}
                className="inline-block rounded-xl bg-brand-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-brand-800"
              >
                Xem chi tiết đơn thuê
              </Link>
            </div>
          ) : isFinalPayment ? (
            <div className="space-y-3">
              {settlement?.finalPayable === 0 ? (
                <button
                  type="button"
                  onClick={handlePayWithVnpay}
                  disabled={paymentCreating}
                  className="w-full rounded-lg bg-purple-700 py-3 text-sm font-bold text-white shadow-xs hover:bg-purple-800 transition disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
                >
                  {paymentCreating && <Icon name="sync" className="animate-spin text-xl" />}
                  <span>
                    {paymentCreating
                      ? 'Đang xác nhận hoàn tất đơn thuê...'
                      : 'Xác nhận hoàn tất quyết toán (0 ₫)'}
                  </span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handlePayWithVnpay}
                  disabled={paymentCreating}
                  className="w-full rounded-lg bg-purple-700 py-3 text-sm font-bold text-white shadow-xs hover:bg-purple-800 transition disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
                >
                  {paymentCreating && <Icon name="sync" className="animate-spin text-xl" />}
                  <span>
                    {paymentCreating
                      ? 'Đang kết nối cổng thanh toán VNPAY...'
                      : `Thanh toán số dư ${formatVnd(settlement?.finalPayable)} qua VNPAY`}
                  </span>
                </button>
              )}
              <div className="text-center text-[11px] text-slate-400">
                {settlement?.finalPayable === 0
                  ? 'Số dư bằng 0 ₫. Bấm xác nhận để cập nhật trạng thái đơn thành HOÀN THÀNH mà không cần qua cổng ngân hàng.'
                  : 'Bạn sẽ được chuyển hướng an toàn đến cổng thanh toán VNPAY Sandbox để hoàn tất số dư còn lại.'}
              </div>
            </div>
          ) : isDepositPaid ? (
            <div className="rounded-xl bg-emerald-50 border border-emerald-200 p-4 text-center space-y-2">
              <div className="text-emerald-800 font-bold text-sm flex items-center justify-center gap-1.5">
                <Icon name="check_circle" className="text-emerald-600" />
                <span>Tiền cọc cho đơn thuê này đã được thanh toán thành công!</span>
              </div>
              <p className="text-xs text-slate-600">
                Lịch xe đã được khóa. Bạn có thể theo dõi tiến trình đón xe của tài xế.
              </p>
              <Link
                to={`/renter/bookings/${booking?.bookingCode || bookingId}`}
                className="inline-block rounded-xl bg-brand-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-brand-800"
              >
                Theo dõi chuyến đi
              </Link>
            </div>
          ) : isCancelledOrRejected ? (
            <div className="rounded-xl bg-rose-50 border border-rose-200 p-4 text-center space-y-2">
              <div className="text-rose-800 font-bold text-sm flex items-center justify-center gap-1.5">
                <Icon name="cancel" className="text-rose-600" />
                <span>
                  {booking?.status === 'CANCELLED'
                    ? 'Đơn thuê này đã bị hủy, không thể tiến hành thanh toán.'
                    : 'Đơn thuê này đã bị từ chối bởi nhà cung cấp.'}
                </span>
              </div>
              <Link
                to="/renter/bookings"
                className="inline-block rounded-xl bg-slate-700 px-5 py-2.5 text-xs font-bold text-white hover:bg-slate-800"
              >
                Quay về danh sách đơn
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              <button
                type="button"
                onClick={handlePayWithVnpay}
                disabled={paymentCreating}
                className="w-full rounded-lg bg-brand-700 py-3 text-sm font-bold text-white shadow-xs hover:bg-brand-800 transition disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
              >
                {paymentCreating && <Icon name="sync" className="animate-spin text-xl" />}
                <span>
                  {paymentCreating
                    ? 'Đang kết nối cổng thanh toán VNPAY...'
                    : 'Thanh toán đặt cọc qua VNPay'}
                </span>
              </button>
              <div className="text-center text-[11px] text-slate-400">
                Bạn sẽ được chuyển hướng an toàn đến trang thanh toán của VNPAY Sandbox để hoàn tất.
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

