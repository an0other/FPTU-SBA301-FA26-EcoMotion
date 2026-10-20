import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { BookingDetail } from '../../components/booking/BookingDetail'
import { readErrorMessage } from '../../services/apiClient'
import { cancelMyBooking, confirmTripEnd, getMyBooking, openDispute } from '../../services/bookingService'
import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'

const CHARGE_LABELS = {
  BASE_RENTAL: 'Tiền thuê',
  DEPOSIT: 'Cọc giữ chỗ',
  OVERTIME: 'Quá giờ',
  WAITING: 'Chờ',
  WEEKEND: 'Cuối tuần',
  OUT_OF_RADIUS: 'Ngoài bán kính',
}

function formatWhen(value) {
  if (!value) return ''
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

function formula(charge) {
  const rate = charge.appliedRate == null ? '' : ` × ${Math.round(Number(charge.appliedRate) * 100)}%`
  if (charge.quantity == null) {
    return `${formatCurrency(charge.unitPrice)}${rate}`
  }
  return `${charge.quantity} × ${formatCurrency(charge.unitPrice)}${rate}`
}

function toCard(detail) {
  const label = planLabel(detail.planType)
  const deposit = (detail.charges ?? []).find((charge) => charge.chargeType === 'DEPOSIT')
  return {
    id: detail.bookingCode,
    status: detail.status,
    planType: detail.planType,
    planTitle: label.title,
    upfront: detail.planType === 'YEARLY',
    createdAt: formatWhen(detail.createdAt),
    actualStartTime: formatWhen(detail.actualStartAt),
    canConfirmEnd: detail.canConfirmEnd,
    depositAmount: deposit ? Number(deposit.amount) : 0,
    refundExplanation: detail.refundPreview?.explanation ?? '',
    disputeCategories: detail.disputeCategories ?? [],
    charges: (detail.charges ?? []).map((charge) => ({
      chargeType: charge.chargeType,
      label: CHARGE_LABELS[charge.chargeType] ?? charge.chargeType,
      description: charge.description,
      formula: formula(charge),
      amount: Number(charge.amount),
    })),
    driver: detail.driver
      ? {
          name: detail.driver.fullName,
          phone: detail.driver.phone,
          avatar: detail.driver.portraitUrl,
          ratingAvg: detail.driver.ratingAvg,
          ratingCount: detail.driver.ratingCount,
        }
      : null,
    slots: detail.slotCount,
    totalAmount: Number(detail.totalAmount),
    vehicle: {
      name: detail.vehicleName,
      year: detail.manufactureYear,
      plateNumber: detail.licensePlate,
      image: detail.imageUrl,
    },
    pickupAddress: detail.pickupAddress,
    formattedStart: formatWhen(detail.pickupTime),
    formattedEnd: formatWhen(detail.endTime),
    passengerCount: detail.passengerCount,
    timeline: (detail.timeline ?? []).map((step) => ({
      title: step.title,
      timestamp: formatWhen(step.timestamp),
      description: step.description,
      completed: step.completed,
      isFailed: step.failed,
    })),
  }
}

export function BookingDetailPage() {
  const { bookingCode } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getMyBooking(bookingCode)
      .then((row) => {
        if (!cancelled) setDetail(row)
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [bookingCode])

  if (!detail) {
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải đơn...'}</p>
  }

  const booking = toCard(detail)

  return (
    <>
      <BookingDetail
        booking={booking}
        onGoBack={() => navigate('/renter/bookings')}
        onGoToCarPage={() => navigate('/')}
        onPayDeposit={() => navigate(`/renter/bookings/${booking.id}/payment`)}
        onCancelBooking={async () => {
          const updated = await cancelMyBooking(booking.id)
          navigate(`/renter/bookings/${updated.bookingCode}`, { replace: true })
          setDetail(await getMyBooking(updated.bookingCode))
        }}
        onCompleteBooking={async () => {
          await confirmTripEnd(booking.id)
          setDetail(await getMyBooking(booking.id))
        }}
        onOpenDispute={(body) => openDispute(booking.id, body)}
      />
      <p className="max-w-[1120px] mx-auto px-4 pb-8 text-sm">
        <Link to="/renter/bookings" className="text-[#15803D] font-semibold">Về đơn của tôi</Link>
      </p>
    </>
  )
}
