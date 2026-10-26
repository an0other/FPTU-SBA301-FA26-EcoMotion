import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { BookingDetail } from '../../components/booking/BookingDetail'
import { readErrorMessage } from '../../services/apiClient'
import { cancelMyBooking, confirmTripEnd, confirmTripStart, getMyBooking, getMyBookingOperationalRisk, openDispute, updateBookingRequestDetails } from '../../services/bookingService'
import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'
import { useRenterTripTracking } from '../../hooks/useRenterTripTracking'

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
  return {
    id: detail.bookingCode,
    vehicleId: detail.vehicleId,
    status: detail.status,
    planType: detail.planType,
    planTitle: label.title,
    upfront: detail.planType === 'YEARLY',
    createdAt: formatWhen(detail.createdAt),
    actualStartTime: formatWhen(detail.actualStartAt),
    canConfirmStart: detail.canConfirmStart,
    canConfirmEnd: detail.canConfirmEnd,
    canCancel: detail.canCancel,
    canEditRequest: detail.canEditRequest,
    canPay: detail.canPay,
    holdExpiresAt: detail.holdExpiresAt,
    depositAmount: Number(detail.depositAmount ?? 0),
    refundExplanation: detail.refundPreview?.explanation ?? '',
    disputeCategories: detail.disputeCategories ?? [],
    charges: (detail.charges ?? []).map((charge) => ({
      chargeId: charge.chargeId,
      chargeType: charge.chargeType,
      label: CHARGE_LABELS[charge.chargeType] ?? charge.chargeType,
      description: charge.description,
      formula: formula(charge),
      amount: Number(charge.effectiveAmount ?? charge.amount),
      isWaived: Boolean(charge.isWaived),
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
    pickupLatitude: detail.pickupLatitude,
    pickupLongitude: detail.pickupLongitude,
    driverNote: detail.driverNote,
    pickupTime: detail.pickupTime,
    endTime: detail.endTime,
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
  const [operationalRisk, setOperationalRisk] = useState(null)
  const trackingEnabled = Boolean(detail && ['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION'].includes(detail.status))
  const { trackingData, loading: trackingLoading, error: trackingError, refetch: refetchTracking } = useRenterTripTracking(
    bookingCode,
    trackingEnabled,
  )

  useEffect(() => {
    let cancelled = false
    Promise.all([getMyBooking(bookingCode), getMyBookingOperationalRisk(bookingCode)])
      .then(([row, risk]) => {
        if (!cancelled) {
          setDetail(row)
          setOperationalRisk(risk)
        }
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
        operationalRisk={operationalRisk}
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
        onConfirmStart={async () => {
          await confirmTripStart(booking.id)
          setDetail(await getMyBooking(booking.id))
        }}
        onOpenDispute={(body) => openDispute(booking.id, body)}
        onEditRequest={async (body) => {
          await updateBookingRequestDetails(booking.id, body)
          setDetail(await getMyBooking(booking.id))
        }}
        trackingData={trackingData}
        trackingLoading={trackingLoading}
        trackingError={trackingError}
        onRefreshTracking={refetchTracking}
      />
    </>
  )
}
