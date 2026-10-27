import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { RequestSubmitted } from '../../components/booking/RequestSubmitted'
import { readErrorMessage } from '../../services/apiClient'
import { getMyBooking } from '../../services/bookingService'
import { BookingErrorDialog } from '../../components/booking/BookingErrorDialog'

function formatWhen(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function fromDetail(detail) {
  return {
    id: detail.bookingCode,
    vehicle: {
      name: detail.vehicleName,
      year: String(detail.manufactureYear),
      plateNumber: detail.licensePlate,
      image: detail.imageUrl,
    },
    slots: detail.slotCount,
    planType: detail.planType,
    formattedStart: formatWhen(detail.pickupTime),
    pickupAddress: detail.pickupAddress,
    depositAmount: Number(detail.depositAmount ?? 0),
    totalAmount: Number(detail.totalAmount),
  }
}

export function SubmittedBookingPage() {
  const { bookingCode } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [booking, setBooking] = useState(location.state?.booking ?? null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let cancelled = false
    getMyBooking(bookingCode)
      .then((detail) => {
        if (!cancelled) setBooking(fromDetail(detail))
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => {
      cancelled = true
    }
  }, [bookingCode, retry])

  const retryLoading = () => {
    setError('')
    setLoading(true)
    setRetry((value) => value + 1)
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
      {errorDialog}
      <RequestSubmitted
        booking={booking}
        onPayDeposit={() => navigate(`/renter/bookings/${booking.id}/payment`)}
        onGoToMyBookings={() => navigate('/renter/bookings')}
        onGoToCarPage={() => navigate('/')}
      />
    </>
  )
}
