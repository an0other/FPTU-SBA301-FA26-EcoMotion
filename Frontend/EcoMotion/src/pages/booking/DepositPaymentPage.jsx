import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { DepositPayment } from '../../components/booking/DepositPayment'
import { readErrorMessage } from '../../services/apiClient'
import { checkout, checkoutPreview } from '../../services/bookingService'
import { planLabel } from '../../utils/planLabels'

const METHODS = {
  card: 'CARD',
  wallet: 'E_WALLET',
  cash: 'CASH',
}

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

export function DepositPaymentPage() {
  const { bookingCode } = useParams()
  const navigate = useNavigate()
  const [preview, setPreview] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    checkoutPreview(bookingCode)
      .then((row) => {
        if (!cancelled) setPreview(row)
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [bookingCode])

  if (!preview) {
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải đơn...'}</p>
  }

  const label = planLabel(preview.planType)
  const booking = {
    id: preview.bookingCode,
    vehicle: {
      name: preview.vehicleName,
      year: String(preview.manufactureYear),
      plateNumber: preview.licensePlate,
      image: preview.imageUrl,
    },
    slots: preview.slotCount,
    formattedStart: formatWhen(preview.pickupTime),
    formattedEnd: formatWhen(preview.endTime),
    pickupAddress: preview.pickupAddress,
    passengerCount: preview.passengerCount,
    totalAmount: Number(preview.totalAmount),
    depositAmount: Number(preview.amountDue),
  }

  return (
    <DepositPayment
      booking={booking}
      planSummary={`${label.title} (${preview.slotCount} slot liên tiếp) • ${label.footer}`}
      payTitle={preview.upfront ? 'Trả trước theo hợp đồng' : 'Cọc giữ chỗ'}
      ratioText={preview.upfront ? null : `${Math.round(Number(preview.depositRatio) * 100)}%`}
      payHint={preview.upfront
        ? 'Đây là khoản trả trước, không phải cọc giữ chỗ'
        : 'Đây là tiền cọc giữ chỗ, không phải cả tiền thuê'}
      amountLabel={preview.upfront ? 'Số tiền trả trước:' : 'Số tiền cọc:'}
      successText={preview.upfront
        ? `Bạn đã thanh toán khoản trả trước thành công cho đơn ${preview.bookingCode}. Xe được giữ cho bạn theo hợp đồng.`
        : `Bạn đã thanh toán cọc giữ chỗ thành công cho đơn ${preview.bookingCode}. Tài xế sẽ liên hệ đón đúng giờ hẹn.`}
      onGoToMyBookings={() => navigate('/renter/bookings')}
      onViewDetail={() => navigate(`/renter/bookings/${preview.bookingCode}`)}
      onPay={(method) => checkout(preview.bookingCode, METHODS[method])}
    />
  )
}
