import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MyBookingsList } from '../../components/booking/MyBookingsList'
import { readErrorMessage } from '../../services/apiClient'
import { listMyBookings } from '../../services/bookingService'

function formatWhen(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function toCard(item) {
  return {
    id: item.bookingCode,
    status: item.status,
    planType: item.planType,
    pickupAddress: item.pickupAddress,
    totalAmount: Number(item.totalAmount),
    formattedStart: formatWhen(item.pickupTime),
    vehicle: {
      name: item.vehicleName,
      year: item.manufactureYear,
      image: item.imageUrl,
    },
  }
}

export function MyBookingsPage() {
  const navigate = useNavigate()
  const [bookings, setBookings] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    listMyBookings()
      .then((rows) => {
        if (!cancelled) setBookings(rows.map(toCard))
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!bookings) {
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải đơn...'}</p>
  }

  return (
    <MyBookingsList
      bookings={bookings}
      onSelectBooking={(booking) => navigate(`/renter/bookings/${booking.id}`)}
      onPayDeposit={(booking) => navigate(`/renter/bookings/${booking.id}/checkout`)}
      onBookCar={() => navigate('/')}
    />
  )
}
