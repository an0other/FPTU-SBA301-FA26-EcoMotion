import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ChoosePlanStep } from '../../components/booking/ChoosePlanStep'
import { ConfirmRequestStep } from '../../components/booking/ConfirmRequestStep'
import { PickupStep } from '../../components/booking/PickupStep'
import { createBooking, listVehiclesForBooking } from '../../services/bookingService'
import { readErrorMessage } from '../../services/apiClient'
import { formatCurrency } from '../../utils/formatCurrency'
import { planLabel } from '../../utils/planLabels'

export function BookingRequestPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [vehicles, setVehicles] = useState([])
  const [vehicleId, setVehicleId] = useState(null)
  const [selectedPlanId, setSelectedPlanId] = useState(null)
  const [step, setStep] = useState(1)
  const [slots, setSlots] = useState(3)
  const [startDate, setStartDate] = useState('2026-10-25')
  const [startTime, setStartTime] = useState('08:00')
  const [pickupAddress, setPickupAddress] = useState('18 Hoàng Hoa Thám, Phường 13, Quận Tân Bình, TP. Hồ Chí Minh')
  const [passengerCount, setPassengerCount] = useState(3)
  const [error, setError] = useState('')
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
        const daily = match.plans.find((plan) => plan.planType === 'DAILY') ?? match.plans[0]
        setSelectedPlanId(daily?.id ?? null)
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
  const totalAmount = plan ? Number(plan.slotPrice) * slots : 0
  const depositAmount = plan?.planType === 'YEARLY'
    ? Number(plan.upfrontAmount ?? 0)
    : Math.round(totalAmount * Number(vehicle?.depositRatio ?? 0))

  const projectedEnd = useMemo(() => {
    if (!plan || !startDate || !startTime) return ''
    const start = new Date(`${startDate}T${startTime}:00+07:00`)
    if (Number.isNaN(start.getTime())) return ''
    const end = new Date(start.getTime() + slots * plan.slotLengthMinutes * 60 * 1000)
    return new Intl.DateTimeFormat('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      weekday: 'long',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'Asia/Ho_Chi_Minh',
    }).format(end)
  }, [plan, slots, startDate, startTime])

  async function handleSubmit() {
    if (!vehicle || !plan) return
    setSubmitting(true)
    setError('')
    try {
      const created = await createBooking({
        vehicleId: vehicle.id,
        planId: plan.id,
        slotCount: slots,
        pickupTime: new Date(`${startDate}T${startTime}:00+07:00`).toISOString(),
        pickupAddress,
        passengerCount,
      })
      navigate(`/renter/bookings/${created.bookingCode}/submitted`, {
        state: {
          booking: {
            id: created.bookingCode,
            vehicle: {
              name: vehicle.name,
              year: String(vehicle.manufactureYear),
              plateNumber: vehicle.licensePlate,
              image: vehicle.imageUrl,
            },
            slots,
            formattedStart: `${startTime}, ${startDate}`,
            pickupAddress,
            depositAmount,
            totalAmount: Number(created.totalAmount),
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
    return <p className="max-w-3xl mx-auto px-6 py-16 text-sm text-[#14221A]">{error || 'Đang tải xe...'}</p>
  }

  return (
    <div>
      {error ? <p className="max-w-7xl mx-auto px-6 pt-4 text-sm text-[#B42318]">{error}</p> : null}
      {step === 1 ? (
        <ChoosePlanStep
          vehicleName={vehicle.name}
          plateNumber={vehicle.licensePlate}
          vehicleType={`${vehicle.seatCount} chỗ`}
          vehicleImage={vehicle.imageUrl}
          plans={vehicle.plans}
          selectedPlanId={selectedPlanId}
          onSelectPlan={setSelectedPlanId}
          slots={slots}
          setSlots={setSlots}
          startDate={startDate}
          setStartDate={setStartDate}
          startTime={startTime}
          setStartTime={setStartTime}
          projectedEnd={projectedEnd}
          onNext={() => setStep(2)}
        />
      ) : null}
      {step === 2 ? (
        <PickupStep
          vehicleName={vehicle.name}
          plateNumber={vehicle.licensePlate}
          vehicleImage={vehicle.imageUrl}
          slots={slots}
          pickupAddress={pickupAddress}
          setPickupAddress={setPickupAddress}
          passengerCount={passengerCount}
          setPassengerCount={setPassengerCount}
          maxPassengers={vehicle.seatCount}
          onBack={() => setStep(1)}
          onNext={() => setStep(3)}
        />
      ) : null}
      {step === 3 ? (
        <ConfirmRequestStep
          vehicleName={vehicle.name}
          plateNumber={vehicle.licensePlate}
          vehicleType={planLabel(plan?.planType).title}
          vehicleImage={vehicle.imageUrl}
          vehicleYear={vehicle.manufactureYear}
          planTitle={plan ? `${planLabel(plan.planType).title} (${plan.planType})` : ''}
          durationLabel={plan ? `${slots} slot liên tiếp` : ''}
          formattedStart={`${startTime}, ${startDate}`}
          projectedEnd={projectedEnd}
          priceNote={plan ? `${slots} slot × ${formatCurrency(plan.slotPrice)}` : ''}
          depositNote={plan?.planType === 'YEARLY'
            ? 'Trả trước theo hợp đồng'
            : `${formatCurrency(totalAmount)} × ${Math.round(Number(vehicle.depositRatio ?? 0) * 100)}%`}
          pickupAddress={pickupAddress}
          passengerCount={passengerCount}
          totalAmount={totalAmount}
          depositAmount={depositAmount}
          onBack={() => setStep(2)}
          onSubmit={submitting ? () => {} : handleSubmit}
        />
      ) : null}
    </div>
  )
}
