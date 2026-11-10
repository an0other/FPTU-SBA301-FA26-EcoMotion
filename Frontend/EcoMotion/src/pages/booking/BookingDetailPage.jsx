import { TV5_REALTIME_EVENTS, useRealtimeSync } from '../../hooks/useRealtimeSync'
import Toast from '../../components/Toast'
import { useEffect, useState, useCallback, useRef } from 'react'
import { pricingDescription, rentalDurationLabel } from '../../utils/rentalPricing'
import { useNavigate, useParams } from 'react-router-dom'
import { BookingDetail } from '../../components/booking/BookingDetail'
import { readErrorMessage } from '../../services/apiClient'
import { acceptCharge, cancelMyBooking, getMyBooking, getMyBookingOperationalRisk, openDispute, uploadDisputeEvidence, updateBookingRequestDetails } from '../../services/bookingService'
import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'
import { useRenterTripTracking } from '../../hooks/useRenterTripTracking'
import BookingWorkflowSummary from '../../components/booking/BookingWorkflowSummary'
import { RefundAccountModal } from '../../components/booking/RefundAccountModal'
import { bookingWorkflowService } from '../../services/bookingWorkflowService'

const CHARGE_LABELS = {
  BASE_RENTAL: 'Tiền thuê',
  DEPOSIT: 'Cọc giữ chỗ',
  OVERTIME: 'Quá giờ',
  WAITING: 'Chờ',
  WEEKEND: 'Cuối tuần',
  OUT_OF_RADIUS: 'Phí đón ngoài phạm vi',
  TOLL: 'Phí cầu đường',
  PARKING: 'Phí gửi xe',
  OTHER: 'Phụ phí khác',
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
    bookingCode: detail.bookingCode,
    renterName: detail.renterName,
    providerName: detail.providerName,
    vehicleId: detail.vehicleId,
    status: detail.status,
    planType: detail.planType,
    planTitle: pricingDescription(detail.pricingItems) || label.title,
    upfront: detail.planType === 'YEARLY',
    createdAt: formatWhen(detail.createdAt),
    actualStartTime: formatWhen(detail.actualStartAt),
    actualStartAt: detail.actualStartAt,
    actualEndAt: detail.actualEndAt,
    formattedActualStart: formatWhen(detail.actualStartAt),
    formattedActualEnd: formatWhen(detail.actualEndAt),
    canCancel: detail.canCancel,
    canEditRequest: detail.canEditRequest,
    canPay: detail.canPay,
    holdExpiresAt: detail.holdExpiresAt,
    depositAmount: Number(detail.depositAmount ?? 0),
    refundExplanation: detail.refundPreview?.explanation ?? '',
    refundAmount: Number(detail.refundPreview?.amount ?? 0),
    refundRequiresBankAccount: detail.refundPreview?.requiresBankAccount ?? Number(detail.refundPreview?.amount ?? 0) > 0,
    disputeCategories: detail.disputeCategories ?? [],
    charges: (detail.charges ?? []).map((charge) => ({
      chargeId: charge.chargeId,
      chargeType: charge.chargeType,
      label: CHARGE_LABELS[charge.chargeType] ?? charge.chargeType,
      description: charge.description,
      formula: formula(charge),
      amount: Number(charge.effectiveAmount ?? charge.amount),
      isWaived: Boolean(charge.isWaived),
      confirmationStatus: charge.confirmationStatus,
      evidenceUrl: charge.evidenceUrl,
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
    durationLabel: detail.endTime ? rentalDurationLabel((new Date(detail.endTime) - new Date(detail.pickupTime)) / 3600000) : '',
    pricingDescription: pricingDescription(detail.pricingItems),
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
    dispatchOriginLatitude: detail.dispatchOriginLatitude,
    dispatchOriginLongitude: detail.dispatchOriginLongitude,
    distanceKm: detail.distanceKm,
    freeRadiusKm: detail.freeRadiusKm,
    chargeableKm: detail.chargeableKm,
    outOfRadiusRate: detail.outOfRadiusRate,
    pickupFee: detail.pickupFee,
    driverNote: detail.driverNote,
    driverReplacementRequired: Boolean(detail.driverReplacementRequired),
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
  const [operationalRisk, setOperationalRisk] = useState(null)
  const [error, setError] = useState('')
  const [workflow, setWorkflow] = useState(null)
  const [workflowError, setWorkflowError] = useState(null)
  const [tripNotice, setTripNotice] = useState('')
  const [refundAccountOpen, setRefundAccountOpen] = useState(false)
  const promptedRefundKey = useRef('')
  const trackingEnabled = Boolean(detail && ['DRIVER_ARRIVED', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION'].includes(detail.status))
  const { trackingData, loading: trackingLoading, error: trackingError, refetch: refetchTracking } = useRenterTripTracking(
    bookingCode,
    trackingEnabled,
  )

  const sequence = useRef(0)
  const refreshDetail = useCallback(async () => {
    const id = ++sequence.current
    try {
      const [row, progress, risk] = await Promise.allSettled([
        getMyBooking(bookingCode),
        bookingWorkflowService.get(bookingCode),
        getMyBookingOperationalRisk(bookingCode),
      ])
      if (id !== sequence.current) return
      if (row.status === 'rejected') throw row.reason
      setDetail(row.value); setError('')
      setOperationalRisk(risk.status === 'fulfilled' ? risk.value : null)
      setWorkflow(progress.status === 'fulfilled' ? progress.value : null)
      setWorkflowError(progress.status === 'rejected' ? progress.reason.message : null)
    } catch (err) {
      if (id === sequence.current) setError(readErrorMessage(err))
    }
  }, [bookingCode])
  useEffect(() => {
    const timer = setTimeout(refreshDetail, 0)
    return () => {
      clearTimeout(timer)
      // This ref is a numeric request generation, not a rendered DOM ref.
      // eslint-disable-next-line react-hooks/exhaustive-deps
      ++sequence.current
    }
  }, [refreshDetail])
  useRealtimeSync({
    handlers: {
      [TV5_REALTIME_EVENTS.TRIP_STARTED]: () => setTripNotice('Tài xế đã bắt đầu chuyến đi.'),
      [TV5_REALTIME_EVENTS.TRIP_COMPLETED]: () => setTripNotice('Tài xế đã kết thúc chuyến đi.'),
    },
    onResync: refreshDetail,
  })

  const manualRefunds = ['CANCELLED', 'CANCELLATION_REVIEW'].includes(detail?.status)
    ? (workflow?.refunds || []).filter(refund => refund.refundMethod !== 'VNPAY'
      && !['REFUNDED', 'REJECTED'].includes(refund.status))
    : []
  const refundAccount = manualRefunds.find(refund => refund.recipientAccountNumber)
  const missingRefundAccount = manualRefunds.some(refund => !refund.recipientAccountNumber)
  const refundPromptKey = manualRefunds
    .map(refund => `${refund.id}:${refund.recipientAccountNumber || ''}`)
    .join('|')

  useEffect(() => {
    if (!missingRefundAccount) return
    if (promptedRefundKey.current === refundPromptKey) return
    promptedRefundKey.current = refundPromptKey
    setRefundAccountOpen(true)
  }, [missingRefundAccount, refundPromptKey])

  if (!detail || detail.bookingCode?.toUpperCase() !== bookingCode?.toUpperCase()) {
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải đơn...'}</p>
  }

  const booking = toCard(detail)

  return (
    <>
      {error && <p role="alert" className="mx-auto max-w-5xl p-4 text-sm text-red-800">Không thể cập nhật trạng thái: {error}. Dữ liệu đang hiển thị có thể đã cũ.</p>}
      {detail.rejectionReason && <p className="mx-auto max-w-5xl p-4 text-sm text-red-800">Lý do nhà cung cấp từ chối: {detail.rejectionReason}</p>}
      {tripNotice && <Toast message={tripNotice} type="success" onClose={() => setTripNotice('')} />}
      {manualRefunds.length > 0 && (
        <section className="mx-auto mt-6 max-w-[1120px] px-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-4">
            <div>
              <p className="text-sm font-semibold text-slate-900">Tài khoản nhận hoàn</p>
              {missingRefundAccount ? (
                <p role="status" className="mt-1 text-sm text-amber-800">Nhà xe đang chờ thông tin tài khoản để chuyển khoản hoàn tiền.</p>
              ) : (
                <p className="mt-1 text-sm text-slate-600">
                  {refundAccount.recipientAccountHolder} · {refundAccount.recipientBankName} ·{' '}
                  <span className="tabular-nums">{refundAccount.recipientAccountNumber}</span>
                </p>
              )}
            </div>
            <button type="button" onClick={() => setRefundAccountOpen(true)}
              className="min-h-11 rounded-lg bg-emerald-800 px-4 text-sm font-semibold text-white hover:bg-emerald-900">
              {missingRefundAccount ? 'Nhập tài khoản nhận hoàn' : 'Cập nhật tài khoản nhận hoàn'}
            </button>
          </div>
        </section>
      )}
      {detail.planType !== 'YEARLY' && <div className="mx-auto mt-6 max-w-[1120px] px-4"><BookingWorkflowSummary key={detail.bookingCode} workflow={workflow} booking={workflow ? { ...detail, driverName: detail.driver?.fullName } : {}} error={workflowError} onRefresh={refreshDetail} /></div>}
      <BookingDetail
        onYearlyChanged={refreshDetail}
        booking={{ ...booking, depositCollected: Number(workflow?.confirmedPaid || 0) > 0 }}
        workflow={workflow}
        operationalRisk={operationalRisk}
        onGoBack={() => navigate('/renter/bookings')}
        onGoToCarPage={() => navigate('/')}
        onPayDeposit={() => navigate(`/renter/bookings/${booking.id}/payment`)}
        onCancelBooking={async (_code, account) => {
          const updated = await cancelMyBooking(booking.id, account)
          navigate(`/renter/bookings/${updated.bookingCode}`, { replace: true })
          await refreshDetail()
        }}
        onAcceptCharge={async (chargeId) => {
          await acceptCharge(booking.id, chargeId)
          await refreshDetail()
        }}
        onOpenDispute={async body => { const result = await openDispute(booking.id, body); await refreshDetail(); return result }}
        onUploadDisputeEvidence={(file) => uploadDisputeEvidence(booking.id, file)}
        onEditRequest={async (body) => {
          await updateBookingRequestDetails(booking.id, body)
          await refreshDetail()
        }}
        trackingData={trackingData}
        trackingLoading={trackingLoading}
        trackingError={trackingError}
        onRefreshTracking={refetchTracking}
      />
      {refundAccountOpen && manualRefunds.length > 0 && (
        <RefundAccountModal
          key={`${booking.id}:${manualRefunds[0].id}`}
          bookingCode={booking.id}
          refunds={manualRefunds}
          onClose={() => setRefundAccountOpen(false)}
          onSaved={refreshDetail}
        />
      )}
    </>
  )
}
