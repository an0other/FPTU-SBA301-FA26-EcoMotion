import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { BookingWizardShell } from '../../components/booking/BookingWizardShell'
import { ChoosePlanStep } from '../../components/booking/ChoosePlanStep'
import { ConfirmRequestStep } from '../../components/booking/ConfirmRequestStep'
import { PickupStep } from '../../components/booking/PickupStep'
import { BookingErrorDialog } from '../../components/booking/BookingErrorDialog'
import { createBooking, listVehiclesForBooking, quotePickup, quoteRental } from '../../services/bookingService'
import { readErrorMessage } from '../../services/apiClient'
import { formatCurrency } from '../../utils/formatCurrency'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { hasValidPickup } from '../../utils/bookingAvailability'
import { pricingDescription, rentalSelection, rentalPayload, rentalTimeInput, rentalTimeError } from '../../utils/rentalPricing'

export function BookingRequestPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [vehicles, setVehicles] = useState([])
  const [step, setStep] = useState(1)
  const [choice, setChoice] = useState(null)
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [pickup, setPickup] = useState({ address: '', latitude: null, longitude: null })
  const [driverNote, setDriverNote] = useState('')
  const [passengerCount, setPassengerCount] = useState(3)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [loadingVehicles, setLoadingVehicles] = useState(true)
  const [rentalState, setRentalState] = useState(null)
  const [rentalRetry, setRentalRetry] = useState(0)
  const [pickupState, setPickupState] = useState(null)
  const requestedVehicleId = Number(searchParams.get('vehicleId'))
  const vehicle = vehicles.find(row => row.id === requestedVehicleId) ?? vehicles[0] ?? null
  const vehicleId = vehicle?.id

  useEffect(() => {
    let cancelled = false
    listVehiclesForBooking().then(rows => { if (!cancelled) setVehicles(rows) })
      .catch(err => { if (!cancelled) setError(readErrorMessage(err)) })
      .finally(() => { if (!cancelled) setLoadingVehicles(false) })
    return () => { cancelled = true }
  }, [])

  const timeError = rentalTimeError(start, end)
  const request = useMemo(() => rentalSelection(vehicleId, start, end), [vehicleId, start, end])
  const currentRental = rentalState?.request === request && rentalState?.retry === rentalRetry ? rentalState : null
  const rentalPending = Boolean(request && !currentRental)
  const selected = choice?.quote === currentRental && currentRental?.data ? choice.option : null
  useEffect(() => {
    if (!request) return undefined
    let cancelled = false
    const timer = setTimeout(() => {
      quoteRental(request).then(data => {
        if (!cancelled) setRentalState({ request, retry: rentalRetry, data })
      }).catch(err => {
        if (!cancelled) setRentalState({ request, retry: rentalRetry, error: readErrorMessage(err) })
      })
    }, 250)
    return () => { cancelled = true; clearTimeout(timer) }
  }, [request, rentalRetry])

  const pickupRequest = useMemo(() => selected && hasValidPickup(pickup)
    ? { vehicleId, planId: selected.policyPlanId, pickupLatitude: Number(pickup.latitude), pickupLongitude: Number(pickup.longitude) }
    : null, [vehicleId, selected, pickup])
  const currentPickup = pickupState?.request === pickupRequest ? pickupState : null
  const pickupQuote = currentPickup?.data ?? null
  const pickupPending = Boolean(pickupRequest && !currentPickup)
  const pickupError = currentPickup?.error ?? ''
  useEffect(() => {
    if (!pickupRequest) return undefined
    let cancelled = false
    quotePickup(pickupRequest).then(data => {
      if (!cancelled) setPickupState({ request: pickupRequest, data })
    }).catch(err => {
      if (!cancelled) setPickupState({ request: pickupRequest, error: readErrorMessage(err) })
    })
    return () => { cancelled = true }
  }, [pickupRequest])

  const maxPassengers = Math.max(1, (vehicle?.seatCount ?? 2) - 1)
  const effectivePassengers = Math.min(passengerCount, maxPassengers)
  const baseAmount = Number(selected?.rentalAmount ?? 0)
  const total = baseAmount + Number(pickupQuote?.pickupFee ?? 0)
  const deposit = Number(selected?.depositAmount ?? 0)
  const formattedStart = request ? formatBookingWhen(request.pickupTime) : ''
  const formattedEnd = selected ? formatBookingWhen(selected.plannedEndAt) : ''
  const description = pricingDescription(selected?.items)

  async function handleSubmit() {
    if (!vehicle || !request || !selected || !pickupQuote || pickupPending || pickupError || submitting) return
    setSubmitting(true); setError('')
    try {
      const created = await createBooking(rentalPayload(vehicle.id, request, selected, pickup, effectivePassengers, driverNote))
      navigate('/renter/bookings/' + created.bookingCode + '/submitted')
    } catch (err) {
      setError(readErrorMessage(err))
      if (err?.status === 409) { setStep(1); setRentalRetry(value => value + 1) }
    } finally { setSubmitting(false) }
  }
  function changeTime(setter, value) { setter(value); setStep(1) }
  function chooseWindow(window) {
    const hours = request ? (new Date(request.requestedEndAt) - new Date(request.pickupTime)) / 3600000 : 1
    setStart(rentalTimeInput(window.startAt))
    setEnd(rentalTimeInput(new Date(new Date(window.startAt).getTime() + hours * 3600000)))
    setRentalRetry(value => value + 1)
  }
  const dialog = <BookingErrorDialog message={error} onClose={() => setError('')} title="Chưa thể đặt xe" />
  if (!vehicle) return <div className="mx-auto max-w-3xl space-y-4 px-6 py-16">{dialog}
    <p>{loadingVehicles ? 'Đang tải xe…' : 'Chưa tải được thông tin xe.'}</p>
    </div>

  return <div>{dialog}<BookingWizardShell vehicleId={vehicle.id} vehicleName={vehicle.name} vehicleImage={vehicle.imageUrl}
    plateNumber={vehicle.licensePlate} planTitle={description} currentStep={step}
    timeRangeLabel={selected ? 'Nhận ' + formattedStart + ' → Trả ' + formattedEnd : ''}
    totalLabel={selected ? formatCurrency(total) : ''} depositAmount={deposit}
    onStepSelect={next => next < step && setStep(next)} contentWidth={step === 1 ? 'wide' : 'form'}>
    {step === 1 ? <ChoosePlanStep plans={vehicle.plans} start={start} end={end}
      onStart={value => changeTime(setStart, value)} onEnd={value => changeTime(setEnd, value)}
      quote={currentRental?.data} pending={rentalPending} requestedEnd={request?.requestedEndAt}
      error={timeError || currentRental?.error || ''} timeError={timeError}
      selected={selected} onSelect={option => setChoice({ quote: currentRental, option })}
      onWindow={chooseWindow}
      onNext={() => selected && setStep(2)}
      onYearly={planId => navigate('/renter/yearly-rental?vehicleId=' + vehicle.id + '&planId=' + planId)} /> : null}
    {step === 2 ? <PickupStep pickup={pickup} setPickup={setPickup} pickupQuote={pickupQuote}
      quotePending={pickupPending} quoteError={pickupError}
      driverNote={driverNote} setDriverNote={setDriverNote} passengerCount={effectivePassengers}
      setPassengerCount={setPassengerCount} seatCount={vehicle.seatCount} driverIncluded maxPassengers={maxPassengers}
      onBack={() => setStep(1)} onNext={() => pickupQuote && !pickupPending && !pickupError && setStep(3)} /> : null}
    {step === 3 ? <ConfirmRequestStep vehicleName={vehicle.name} vehicleImage={vehicle.imageUrl}
      plateNumber={vehicle.licensePlate} vehicleYear={vehicle.manufactureYear}
      formattedStart={formattedStart} pricingItems={selected?.items ?? []}
      projectedEnd={formattedEnd} depositNote="Tiền cọc theo báo giá đã chọn"
      pickupAddress={pickup.address} pickupQuote={pickupQuote} baseRentalAmount={baseAmount} pickup={pickup}
      driverNote={driverNote} passengerCount={effectivePassengers} totalAmount={total} depositAmount={deposit}
      submitting={submitting} onBack={() => setStep(2)} onSubmit={handleSubmit} /> : null}
  </BookingWizardShell></div>
}
