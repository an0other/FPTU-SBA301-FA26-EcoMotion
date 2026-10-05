import { useNavigate } from 'react-router-dom'
import { MyBookingsList } from '../../components/booking/MyBookingsList'

export function MyBookingsPage() {
  const navigate = useNavigate()
  return (
    <MyBookingsList
      bookings={[]}
      onSelectBooking={() => {}}
      onPayDeposit={() => {}}
      onBookCar={() => navigate('/renter/bookings/new')}
    />
  )
}
