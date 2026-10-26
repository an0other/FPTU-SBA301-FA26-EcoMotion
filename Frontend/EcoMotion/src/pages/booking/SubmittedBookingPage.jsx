import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { RequestSubmitted } from '../../components/booking/RequestSubmitted'
import { readErrorMessage } from '../../services/apiClient'
import { getMyBooking } from '../../services/bookingService'

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

  useEffect(() => {
    let cancelled = false
    getMyBooking(bookingCode)
      .then((detail) => {
        if (!cancelled) setBooking(fromDetail(detail))
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [bookingCode])

  if (!booking) {
    return (
      <div className="max-w-xl mx-auto px-6 py-16 text-center">
        <p className="text-sm text-[#14221A] mb-4">{error || 'Đang tải thông tin đơn thuê...'}</p>
        <Link to="/renter/bookings" className="text-[#15803D] font-semibold">Về đơn của tôi</Link>
      </div>
    )
  }

  return (
    <RequestSubmitted
      booking={booking}
      onPayDeposit={() => navigate(`/renter/bookings/${booking.id}/payment`)}
      onGoToMyBookings={() => navigate('/renter/bookings')}
      onGoToCarPage={() => navigate('/')}
    />
  )
}
