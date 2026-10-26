import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MyBookingsList } from '../../components/booking/MyBookingsList'
import { readErrorMessage } from '../../services/apiClient'
import { listMyBookings } from '../../services/bookingService'

const PAGE_SIZE = 10

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
    canPay: Boolean(item.canPay),
    holdExpiresAt: item.holdExpiresAt,
    atRisk: Boolean(item.atRisk),
    riskMessage: item.riskMessage,
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
  const [activeFilter, setActiveFilter] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [pageInfo, setPageInfo] = useState({ total: 0, hasMore: false })
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    const load = () => listMyBookings({ group: activeFilter, keyword, page, size: PAGE_SIZE })
      .then((result) => {
        if (!cancelled) {
          setBookings(result.items.map(toCard))
          setPageInfo({ total: result.total, hasMore: result.hasMore })
          setError('')
        }
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    load()
    const timer = window.setInterval(load, 15000)
    const handleFocus = () => load()
    window.addEventListener('focus', handleFocus)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      window.removeEventListener('focus', handleFocus)
    }
  }, [activeFilter, keyword, page])

  if (!bookings) {
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải đơn...'}</p>
  }

  return (
    <MyBookingsList
      bookings={bookings}
      activeFilter={activeFilter}
      keyword={keyword}
      page={page}
      pageSize={PAGE_SIZE}
      total={pageInfo.total}
      hasMore={pageInfo.hasMore}
      onFilterChange={(nextFilter) => {
        setActiveFilter(nextFilter)
        setPage(0)
      }}
      onKeywordChange={(nextKeyword) => {
        setKeyword(nextKeyword)
        setPage(0)
      }}
      onPageChange={setPage}
      onSelectBooking={(booking) => navigate(`/renter/bookings/${booking.id}`)}
      onPayDeposit={(booking) => navigate(`/renter/bookings/${booking.id}/payment`)}
      onBookCar={() => navigate('/')}
    />
  )
}
