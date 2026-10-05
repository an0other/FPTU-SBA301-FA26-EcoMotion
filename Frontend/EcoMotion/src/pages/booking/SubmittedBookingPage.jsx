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
        <Link to="/renter/bookings/new" className="text-[#15803D] font-semibold">Gửi yêu cầu thuê</Link>
      </div>
    )
  }

  return (
    <RequestSubmitted
      booking={booking}
      onGoToMyBookings={() => navigate('/renter/bookings')}
      onGoToCarPage={() => navigate('/renter/bookings/new')}
    />
  )
}
