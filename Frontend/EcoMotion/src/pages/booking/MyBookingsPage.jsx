import { useEffect, useMemo, useState, useCallback } from 'react'
import { TV5_REALTIME_EVENTS, useRealtimeSync } from '../../hooks/useRealtimeSync'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { MyBookingsList } from '../../components/booking/MyBookingsList'
import Toast from '../../components/Toast'
import { readErrorMessage } from '../../services/apiClient'
import { listMyBookings } from '../../services/bookingService'
import { BOOKING_FILTERS, BOOKING_SORTS } from '../../utils/bookingStatus'

const PAGE_SIZE = 10
const VALID_GROUPS = new Set(BOOKING_FILTERS.map((filter) => filter.id))
const VALID_SORTS = new Set(BOOKING_SORTS.map((option) => option.id))

function formatWhen(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(value))
}

function readGroup(params) {
  const value = (params.get('group') || 'ALL').toUpperCase()
  return VALID_GROUPS.has(value) ? value : 'ALL'
}

function readSort(params) {
  const value = (params.get('sort') || 'PICKUP').toUpperCase()
  return VALID_SORTS.has(value) ? value : 'PICKUP'
}

function toCard(item) {
  return {
    id: item.bookingCode,
    status: item.status,
    planType: item.planType,
    pickupAddress: item.pickupAddress,
    totalAmount: Number(item.totalAmount),
    depositAmount: item.depositAmount == null ? null : Number(item.depositAmount),
    canPay: Boolean(item.canPay),
    actionKind: item.actionKind || 'NONE',
    depositPaymentStatus: item.depositPaymentStatus ?? null,
    depositRejectionReason: item.depositRejectionReason ?? null,
    holdExpiresAt: item.holdExpiresAt,
    atRisk: Boolean(item.atRisk),
    riskMessage: item.riskMessage,
    driverName: item.driverName || '',
    formattedStart: formatWhen(item.pickupTime),
    formattedEnd: formatWhen(item.endTime),
    vehicle: {
      name: item.vehicleName,
      year: item.manufactureYear,
      image: item.imageUrl,
    },
  }
}

export function MyBookingsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeFilter = readGroup(searchParams)
  const keyword = searchParams.get('q') ?? ''
  const sort = readSort(searchParams)
  const [bookings, setBookings] = useState(null)
  const [page, setPage] = useState(0)
  const [pageInfo, setPageInfo] = useState({ total: 0, hasMore: false })
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [tripNotice, setTripNotice] = useState('')
  const [pendingActionId, setPendingActionId] = useState('')

  const queryKey = useMemo(
    () => `${activeFilter}|${keyword}|${sort}|${page}`,
    [activeFilter, keyword, sort, page],
  )

  useEffect(() => {
    let cancelled = false
    const load = () => listMyBookings({ group: activeFilter, keyword, sort, page, size: PAGE_SIZE })
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
  }, [queryKey, activeFilter, keyword, sort, page])

  const writeParams = (next, { replace = false } = {}) => {
    const merged = new URLSearchParams()
    const nextGroup = next.group ?? activeFilter
    const nextKeyword = next.q ?? keyword
    const nextSort = next.sort ?? sort
    if (nextGroup && nextGroup !== 'ALL') merged.set('group', nextGroup)
    if (nextKeyword.trim()) merged.set('q', nextKeyword)
    if (nextSort && nextSort !== 'PICKUP') merged.set('sort', nextSort)
    setSearchParams(merged, { replace })
  }

  const refreshList = useCallback(() => listMyBookings({
    group: activeFilter, keyword, sort, page, size: PAGE_SIZE,
  }).then((result) => {
    setBookings(result.items.map(toCard))
    setPageInfo({ total: result.total, hasMore: result.hasMore })
  }), [activeFilter, keyword, sort, page])

  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.TRIP_STARTED]: () => setTripNotice('Tài xế đã bắt đầu chuyến đi.'),
      [TV5_REALTIME_EVENTS.TRIP_COMPLETED]: () => setTripNotice('Tài xế đã kết thúc chuyến đi.'),
    },
    onResync: refreshList,
  })

  const runCardAction = async (booking, action) => {
    setActionError('')
    setPendingActionId(booking.id)
    try {
      if (booking.planType === 'YEARLY') {
        navigate('/renter/bookings/' + encodeURIComponent(booking.id))
        return
      }
      if (action === 'PAY_DEPOSIT' || action === 'PAY_SETTLEMENT') {
        navigate(`/renter/bookings/${booking.id}/payment`)
        return
      }
      await refreshList()
    } catch (err) {
      setActionError(readErrorMessage(err))
    } finally {
      setPendingActionId('')
    }
  }

  return (
    <>
    <MyBookingsList
      bookings={bookings}
      loading={!bookings}
      error={error || actionError}
      activeFilter={activeFilter}
      keyword={keyword}
      sort={sort}
      page={page}
      pageSize={PAGE_SIZE}
      total={pageInfo.total}
      hasMore={pageInfo.hasMore}
      pendingActionId={pendingActionId}
      onFilterChange={(nextFilter) => {
        setPage(0)
        writeParams({ group: nextFilter })
      }}
      onKeywordChange={(nextKeyword) => {
        setPage(0)
        writeParams({ q: nextKeyword }, { replace: true })
      }}
      onSortChange={(nextSort) => {
        setPage(0)
        writeParams({ sort: nextSort })
      }}
      onPageChange={setPage}
      onCardAction={runCardAction}
      onBookCar={() => navigate('/')}
    />
    {tripNotice && <Toast message={tripNotice} type="success" onClose={() => setTripNotice('')} />}
    </>
  )
}
