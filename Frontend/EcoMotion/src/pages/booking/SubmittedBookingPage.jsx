import { pricingDescription, rentalDurationLabel } from '../../utils/rentalPricing'
import { estimatedGrandTotal } from '../../utils/bookingFinancialSummary'
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { RequestSubmitted } from '../../components/booking/RequestSubmitted'
import { readErrorMessage } from '../../services/apiClient'
import { getMyBooking } from '../../services/bookingService'
import { BookingErrorDialog } from '../../components/booking/BookingErrorDialog'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { providerFinanceService } from '../../services/providerFinanceService'
import Toast from '../../components/Toast'
import { isDepositAwaitingProvider } from '../../utils/paymentDisplay'

function fromDetail(detail) {
  return {
    id: detail.bookingCode,
    status: detail.status,
    canPay: Boolean(detail.canPay),
    vehicle: {
      name: detail.vehicleName,
      year: String(detail.manufactureYear),
      plateNumber: detail.licensePlate,
      image: detail.imageUrl,
    },
    slots: detail.slotCount,
    durationLabel: detail.endTime ? rentalDurationLabel((new Date(detail.endTime) - new Date(detail.pickupTime)) / 3600000) : '',
    pricingDescription: pricingDescription(detail.pricingItems),
    planType: detail.planType,
    formattedStart: formatBookingWhen(detail.pickupTime),
    formattedEnd: formatBookingWhen(detail.endTime),
    pickupAddress: detail.pickupAddress,
    depositAmount: Number(detail.depositAmount ?? 0),
    totalAmount: Number(detail.totalAmount),
    // Tổng dự kiến gồm phí đón ngoài bán kính — khớp màn xác nhận và trang cọc.
    estimatedTotal: estimatedGrandTotal({ totalAmount: detail.totalAmount, pickupFee: detail.pickupFee, charges: detail.charges }),
    holdExpiresAt: detail.holdExpiresAt,
  }
}

const OPEN_DEPOSIT_STATUSES = new Set(['AWAITING_DEPOSIT', 'ACCEPTED'])

export function SubmittedBookingPage() {
  const { bookingCode } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [booking, setBooking] = useState(location.state?.booking ?? null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retry, setRetry] = useState(0)
  const [reissuing, setReissuing] = useState(false)
  const [toast, setToast] = useState({ message: '', type: 'success' })
  const [depositPaymentStatus, setDepositPaymentStatus] = useState(null)

  const refreshBooking = useCallback(async () => {
    const [detail, paymentRequests] = await Promise.all([
      getMyBooking(bookingCode),
      providerFinanceService.getPaymentRequestsByBooking(bookingCode).catch(() => []),
    ])
    if (detail.planType === 'YEARLY') {
      navigate('/renter/bookings/' + encodeURIComponent(bookingCode), { replace: true })
      return detail
    }
    const depositRequest = (paymentRequests || []).find((item) => item.paymentType === 'DEPOSIT') || null
    setDepositPaymentStatus(depositRequest?.status ?? null)
    const holdExpiresAt = depositRequest?.expiresAt ?? detail.holdExpiresAt
    setBooking(fromDetail({ ...detail, holdExpiresAt }))
    return detail
  }, [bookingCode, navigate])

  useEffect(() => {
    let cancelled = false
    queueMicrotask(() => {
      if (cancelled) return
      refreshBooking()
        .catch((err) => {
          if (!cancelled) setError(readErrorMessage(err))
        })
        .finally(() => { if (!cancelled) setLoading(false) })
    })
    return () => {
      cancelled = true
    }
  }, [bookingCode, retry, refreshBooking])

  const keepPollingBooking = Boolean(booking) && (
    OPEN_DEPOSIT_STATUSES.has(booking.status)
    || isDepositAwaitingProvider(depositPaymentStatus)
  )

  useEffect(() => {
    if (!keepPollingBooking) return undefined
    const timer = window.setInterval(() => {
      refreshBooking().catch(() => {})
    }, 10_000)
    return () => window.clearInterval(timer)
  }, [keepPollingBooking, refreshBooking])

  const retryLoading = () => {
    setError('')
    setLoading(true)
    setRetry((value) => value + 1)
  }

  const handleReissueDeposit = async () => {
    if (!booking?.id || reissuing) return
    setReissuing(true)
    try {
      await providerFinanceService.reissueDepositPayment(booking.id)
      await refreshBooking()
      setToast({ message: 'Đã cấp lại yêu cầu thanh toán cọc. Bạn có thể thanh toán trong thời hạn mới.', type: 'success' })
    } catch (err) {
      setToast({ message: readErrorMessage(err) || 'Không thể cấp lại yêu cầu thanh toán', type: 'error' })
    } finally {
      setReissuing(false)
    }
  }

  const errorDialog = <BookingErrorDialog title="Không tải được thông tin đơn thuê"
    message={error} onClose={() => setError('')} onRetry={retryLoading} />

  if (!booking) {
    return (
      <div className="max-w-xl mx-auto px-6 py-16 text-center">
        {errorDialog}
        <p className="text-sm text-[#14221A] mb-4">{loading ? 'Đang tải thông tin đơn thuê...' : 'Chưa tải được thông tin đơn thuê.'}</p>
        {!loading ? <button type="button" onClick={retryLoading} className="mb-4 block mx-auto rounded-xl border border-[#E6EBE8] px-4 py-2 text-sm font-semibold text-[#15803D]">Tải lại đơn thuê</button> : null}
        <Link to="/renter/bookings" className="text-[#15803D] font-semibold">Về đơn của tôi</Link>
      </div>
    )
  }

  return (
    <>
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'success' })} />
      {errorDialog}
      <RequestSubmitted
        booking={booking}
        depositPaymentStatus={depositPaymentStatus}
        reissuing={reissuing}
        onPayDeposit={() => navigate(`/renter/bookings/${booking.id}/payment`)}
        onReissueDeposit={handleReissueDeposit}
        onGoToMyBookings={() => navigate('/renter/bookings')}
        onGoToCarPage={() => navigate('/')}
        onRefreshBooking={() => refreshBooking().catch(() => {})}
      />
    </>
  )
}
