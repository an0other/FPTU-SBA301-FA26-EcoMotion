import { Link, useLocation, useNavigate } from 'react-router-dom'
import { RequestSubmitted } from '../../components/booking/RequestSubmitted'

export function SubmittedBookingPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const booking = location.state?.booking

  if (!booking) {
    return (
      <div className="max-w-xl mx-auto px-6 py-16 text-center">
        <p className="text-sm text-[#14221A] mb-4">Chưa có yêu cầu vừa gửi.</p>
        <Link to="/" className="text-[#15803D] font-semibold">Khám phá xe</Link>
      </div>
    )
  }

  return (
    <RequestSubmitted
      booking={booking}
      onGoToMyBookings={() => navigate('/renter/bookings')}
      onGoToCarPage={() => navigate('/')}
    />
  )
}
