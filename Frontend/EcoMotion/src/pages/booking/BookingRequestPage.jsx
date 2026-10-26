import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { BookingWizardShell } from '../../components/booking/BookingWizardShell'
import { ChoosePlanStep } from '../../components/booking/ChoosePlanStep'
import { ConfirmRequestStep } from '../../components/booking/ConfirmRequestStep'
import { PickupStep } from '../../components/booking/PickupStep'
import { createBooking, listVehiclesForBooking } from '../../services/bookingService'
import { readErrorMessage } from '../../services/apiClient'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatSlotPrice, planLabel, rentalSpanLabel } from '../../utils/planLabels'
import { formatBookingWhen, parseVietnamDateTime } from '../../utils/bookingDateTime'
import { buildBookingPayload, estimateBookingTotals } from '../../utils/bookingAvailability'

export function BookingRequestPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [vehicles, setVehicles] = useState([])
  const [vehicleId, setVehicleId] = useState(null)
  const [selectedPlanId, setSelectedPlanId] = useState(null)
  const [step, setStep] = useState(1)
  const [slots, setSlots] = useState(3)
  const [startDate, setStartDate] = useState('')
  const [startTime, setStartTime] = useState('')
  const [pickup, setPickup] = useState({ address: '', latitude: null, longitude: null })
  const [driverNote, setDriverNote] = useState('')
  const [passengerCount, setPassengerCount] = useState(3)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    listVehiclesForBooking()
      .then((rows) => {
        if (cancelled) return
        setVehicles(rows)
        const requested = Number(searchParams.get('vehicleId'))
        const match = rows.find((row) => row.id === requested) ?? rows[0]
        if (!match) return
        setVehicleId(match.id)
        const requestedPlanId = Number(searchParams.get('planId'))
        const byId = match.plans.find((plan) => plan.id === requestedPlanId)
        const daily = match.plans.find((plan) => plan.planType === 'DAILY') ?? match.plans[0]
        setSelectedPlanId(byId?.id ?? daily?.id ?? null)
      })
      .catch((err) => {
        if (!cancelled) setError(readErrorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [searchParams])

  const vehicle = vehicles.find((row) => row.id === vehicleId) ?? null
  const plan = vehicle?.plans.find((row) => row.id === selectedPlanId) ?? null
  const driverIncluded = plan?.planType !== 'YEARLY'
  const maxPassengers = vehicle
    ? driverIncluded
      ? Math.max(1, vehicle.seatCount - 1)
      : vehicle.seatCount
    : 1
  const effectivePassengerCount = Math.min(passengerCount, maxPassengers)
  const { totalAmount, depositAmount } = plan
    ? estimateBookingTotals({
      slotPrice: plan.slotPrice,
      slots,
      depositRatio: vehicle?.depositRatio,
      minimumDepositAmount: vehicle?.minimumDepositAmount,
      planType: plan.planType,
      upfrontAmount: plan.upfrontAmount,
    })
    : { totalAmount: 0, depositAmount: 0 }

  const startInstant = useMemo(
    () => parseVietnamDateTime(startDate, startTime),
    [startDate, startTime],
  )
  const endInstant = useMemo(() => {
    if (!plan || !startInstant) return null
    return new Date(startInstant.getTime() + slots * plan.slotLengthMinutes * 60 * 1000)
  }, [plan, slots, startInstant])
  const formattedStart = startInstant ? formatBookingWhen(startInstant) : ''
  const projectedEnd = endInstant ? formatBookingWhen(endInstant) : ''
  const spanLabel = plan ? rentalSpanLabel(plan.slotLengthMinutes, slots) : ''
  const timeRangeLabel = formattedStart && projectedEnd
    ? `Nhận ${formattedStart} → Trả ${projectedEnd}`
    : ''
  const durationLabel = spanLabel
    ? `${spanLabel}${slots > 1 ? ` (${slots} slot liên tiếp)` : ''}`
    : plan ? `${slots} slot` : ''

  useEffect(() => {
    if (plan?.planType === 'YEARLY' && step !== 1) setStep(1)
  }, [plan?.planType, step])

  function selectPlan(nextPlanId) {
    if (nextPlanId === selectedPlanId) return
    setSelectedPlanId(nextPlanId)
    setFieldErrors({})
  }

  async function handleSubmit() {
    if (!vehicle || !plan || submitting || !startInstant || plan.planType === 'YEARLY') return
    setSubmitting(true)
    setError('')
    try {
      const created = await createBooking(buildBookingPayload({
        vehicleId: vehicle.id,
        planId: plan.id,
        slotCount: slots,
        pickupTime: startInstant.toISOString(),
        pickup,
        passengerCount: effectivePassengerCount,
        driverNote,
      }))
      navigate(`/renter/bookings/${created.bookingCode}/submitted`, {
        state: {
          booking: {
            id: created.bookingCode,
            vehicle: {
              name: vehicle.name,
              year: String(vehicle.manufactureYear),
              plateNumber: vehicle.licensePlate,
              fuelType: vehicle.fuelType,
              image: vehicle.imageUrl,
            },
            slots,
            planType: plan.planType,
            formattedStart,
            pickupAddress: pickup.address,
            depositAmount: Number(created.depositAmount),
            totalAmount: Number(created.totalAmount),
            holdExpiresAt: created.holdExpiresAt,
          },
        },
      })
    } catch (err) {
      setError(readErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (!vehicle) {
    return <p className="mx-auto max-w-3xl px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải xe...'}</p>
  }

  return (
    <div>
      {error ? (
        <p role="alert" className="mx-auto max-w-7xl px-6 pt-4 text-sm text-[#B42318]">{error}</p>
      ) : null}
      <BookingWizardShell
        vehicleId={vehicle.id}
        vehicleName={vehicle.name}
        vehicleImage={vehicle.imageUrl}
        plateNumber={vehicle.licensePlate}
        planTitle={plan ? planLabel(plan.planType).title : ''}
        timeRangeLabel={timeRangeLabel}
        totalLabel={plan ? formatCurrency(totalAmount) : ''}
        depositAmount={depositAmount}
        currentStep={step}
        onStepSelect={(next) => next < step && setStep(next)}
        contentWidth={step === 1 ? 'wide' : 'form'}
      >
        {step === 1 ? (
          <ChoosePlanStep
            vehicleId={vehicle.id}
            plans={vehicle.plans}
            selectedPlanId={selectedPlanId}
            onSelectPlan={selectPlan}
            slots={slots}
            setSlots={setSlots}
            startDate={startDate}
            setStartDate={setStartDate}
            startTime={startTime}
            setStartTime={setStartTime}
            formattedStart={formattedStart}
            projectedEnd={projectedEnd}
            spanLabel={spanLabel}
            fieldErrors={fieldErrors}
            setFieldErrors={setFieldErrors}
            totalAmount={totalAmount}
            depositAmount={depositAmount}
            depositRatio={Number(vehicle?.depositRatio ?? 0)}
            onNext={() => {
              if (plan?.planType === 'YEARLY') {
                navigate(`/renter/yearly-rental?vehicleId=${vehicle.id}&planId=${plan.id}`)
                return
              }
              const errs = {}
              if (!startDate) errs.startDate = 'Vui lòng chọn ngày nhận xe'
              if (!startTime) errs.startTime = 'Vui lòng chọn giờ nhận xe'
              if (startInstant && startInstant <= new Date()) {
                errs.startTime = 'Giờ nhận xe đã qua, vui lòng chọn thời gian trong tương lai'
              }
              setFieldErrors(errs)
              if (Object.keys(errs).length === 0) setStep(2)
            }}
          />
        ) : null}
        {step === 2 ? (
          <PickupStep
            pickup={pickup}
            setPickup={setPickup}
            driverNote={driverNote}
            setDriverNote={setDriverNote}
            passengerCount={effectivePassengerCount}
            setPassengerCount={setPassengerCount}
            seatCount={vehicle.seatCount}
            driverIncluded={driverIncluded}
            maxPassengers={maxPassengers}
            onBack={() => setStep(1)}
            onNext={() => setStep(3)}
          />
        ) : null}
        {step === 3 ? (
          <ConfirmRequestStep
            vehicleName={vehicle.name}
            vehicleImage={vehicle.imageUrl}
            plateNumber={vehicle.licensePlate}
            vehicleYear={vehicle.manufactureYear}
            planTitle={plan ? planLabel(plan.planType).title : ''}
            durationLabel={durationLabel}
            formattedStart={formattedStart}
            projectedEnd={projectedEnd}
            priceNote={plan ? `${slots} × ${formatSlotPrice(plan.slotPrice, plan.slotLengthMinutes)}` : ''}
            depositNote={plan?.planType === 'YEARLY'
              ? 'Trả trước theo hợp đồng'
              : `${formatCurrency(totalAmount)} × ${Math.round(Number(vehicle.depositRatio ?? 0) * 100)}%`}
            pickupAddress={pickup.address}
            pickup={pickup}
            driverNote={driverNote}
            passengerCount={effectivePassengerCount}
            totalAmount={totalAmount}
            depositAmount={depositAmount}
            submitting={submitting}
            onBack={() => setStep(2)}
            onSubmit={handleSubmit}
          />
        ) : null}
      </BookingWizardShell>
    </div>
  )
}
