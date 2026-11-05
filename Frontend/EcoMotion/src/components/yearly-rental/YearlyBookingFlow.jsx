import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useVehicle } from '../../hooks/useVehicle'
import { createBooking, quotePickup } from '../../services/bookingService'
import { readErrorMessage } from '../../services/apiClient'
import { formatCurrency } from '../../utils/formatCurrency'
import { BookingErrorDialog } from '../booking/BookingErrorDialog'
import { PickupStep } from '../booking/PickupStep'

const DAY_MS = 24 * 60 * 60 * 1000
const YEAR_MS = 365 * DAY_MS
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000

function vietnamInputValue(timestamp) {
  return new Date(timestamp + VIETNAM_OFFSET_MS).toISOString().slice(0, 16)
}

function parseVietnamInput(value) {
  if (!value) return null
  const date = new Date(`${value}:00+07:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

function vietnamDate(timestamp) {
  return new Date(timestamp + VIETNAM_OFFSET_MS).toISOString().slice(0, 10)
}

function formatVietnamDateTime(value) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(value)
}

export default function YearlyBookingFlow({
  vehicleId,
  planId,
  license,
  licenseLoading,
}) {
  const navigate = useNavigate()
  const { vehicle, loading, error: vehicleError, notFound, reload } = useVehicle(vehicleId)
  const [step, setStep] = useState(1)
  const [start, setStart] = useState(() => vietnamInputValue(Date.now() + 30 * 60 * 60 * 1000))
  const [pickup, setPickup] = useState({ address: '', latitude: null, longitude: null })
  const [passengerCount, setPassengerCount] = useState(1)
  const [pickupState, setPickupState] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [minimumStart] = useState(() =>
    vietnamInputValue(Date.now() + 3 * 60 * 60 * 1000),
  )

  const plan = vehicle?.plans?.find((item) => String(item.id) === String(planId))
  const pickupTime = useMemo(() => parseVietnamInput(start), [start])
  const plannedEnd = pickupTime ? new Date(pickupTime.getTime() + YEAR_MS) : null
  const licenseCoversTerm = Boolean(
    license?.yearlyBookingEligible
      && plannedEnd
      && license.licenseExpiry
      && license.licenseExpiry >= vietnamDate(plannedEnd.getTime()),
  )
  const validVehicle = Boolean(vehicle && plan && plan.planType === 'YEARLY')
  const maxPassengers = Math.max(1, (vehicle?.seatCount ?? 2) - 1)
  const effectivePassengers = Math.min(passengerCount, maxPassengers)

  const pickupRequest = useMemo(() => {
    if (!validVehicle || pickup.latitude == null || pickup.longitude == null) return null
    return {
      vehicleId: Number(vehicleId),
      planId: Number(planId),
      pickupLatitude: Number(pickup.latitude),
      pickupLongitude: Number(pickup.longitude),
    }
  }, [validVehicle, vehicleId, planId, pickup.latitude, pickup.longitude])

  useEffect(() => {
    if (!pickupRequest) return undefined

    let active = true

    quotePickup(pickupRequest)
      .then((data) => {
        if (active) setPickupState({ request: pickupRequest, data })
      })
      .catch((requestError) => {
        if (active) {
          setPickupState({ request: pickupRequest, error: readErrorMessage(requestError) })
        }
      })

    return () => {
      active = false
    }
  }, [pickupRequest])

  const currentPickup = pickupState?.request === pickupRequest ? pickupState : null
  const pickupQuote = currentPickup?.data ?? null
  const pickupPending = Boolean(pickupRequest && !currentPickup)
  const currentPickupError = currentPickup?.error ?? ''

  async function submitBooking() {
    if (!vehicle || !plan || !pickupTime || !plannedEnd || !pickupQuote || submitting) return
    if (pickupTime.getTime() < Date.now() + 3 * 60 * 60 * 1000) {
      setError('Thời gian nhận xe phải cách lúc gửi yêu cầu ít nhất 3 giờ.')
      setStep(1)
      return
    }
    if (!licenseCoversTerm) {
      setError('GPLX chưa đủ điều kiện hoặc không còn hạn hết toàn bộ thời gian thuê năm.')
      setStep(1)
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const booking = await createBooking({
        vehicleId: Number(vehicle.id),
        planId: Number(plan.id),
        slotCount: 1,
        pickupTime: pickupTime.toISOString(),
        pickupAddress: pickup.address.trim(),
        pickupLatitude: Number(pickup.latitude),
        pickupLongitude: Number(pickup.longitude),
        driverNote: '',
        expectedPickupFee: pickupQuote.pickupFee,
        passengerCount: effectivePassengers,
      })

      if (!booking?.bookingCode) {
        throw new Error('Máy chủ chưa trả về mã đơn thuê. Vui lòng kiểm tra danh sách đơn trước khi gửi lại.')
      }
      navigate(`/renter/bookings/${encodeURIComponent(booking.bookingCode)}/yearly-rental`)
    } catch (submitError) {
      setError(readErrorMessage(submitError))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading || licenseLoading) {
    return <p role="status" className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
      Đang tải thông tin xe và GPLX…
    </p>
  }

  if (vehicleError || notFound || !validVehicle) {
    return (
      <section className="rounded-xl border border-slate-200 bg-white p-6">
        <p role="alert" className="text-sm text-on-error-container">
          {vehicleError || (notFound
            ? 'Không tìm thấy xe.'
            : 'Gói năm không còn khả dụng cho xe này.')}
        </p>
        <button type="button" onClick={reload} className="mt-3 rounded-lg border px-4 py-2 text-sm font-semibold">
          Thử tải lại
        </button>
      </section>
    )
  }

  const vehicleName = [vehicle.brand, vehicle.model].filter(Boolean).join(' ')
  const coverImage = vehicle.imageUrls?.[0]
  const planPrice = Number(plan.slotPrice ?? 0)
  const upfrontAmount = Number(plan.upfrontAmount ?? 0)
  const pickupFee = Number(pickupQuote?.pickupFee ?? 0)

  return (
    <section className="space-y-5">
      <BookingErrorDialog
        title="Chưa thể gửi yêu cầu thuê năm"
        message={error}
        onClose={() => setError('')}
      />

      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
        <div className="flex flex-wrap items-start gap-4">
          {coverImage && (
            <img src={coverImage} alt={vehicleName} className="h-24 w-36 rounded-lg bg-slate-100 object-cover" />
          )}
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold uppercase tracking-wide text-brand-700">Thuê tự lái · 365 ngày</p>
            <h2 className="mt-1 text-lg font-bold text-slate-900">{vehicleName}</h2>
            <p className="mt-1 text-sm text-slate-500">
              Tổng giá kỳ thuê: {formatCurrency(planPrice)} · Trả trước sau khi ký hợp đồng: {formatCurrency(upfrontAmount)}
            </p>
          </div>
        </div>

        <ol className="mt-5 grid gap-2 text-xs text-slate-600 sm:grid-cols-4">
          <li className={`rounded-lg p-3 ${step === 1 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>1. Chọn ngày nhận</li>
          <li className={`rounded-lg p-3 ${step === 2 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>2. Điểm nhận xe</li>
          <li className={`rounded-lg p-3 ${step === 3 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>3. Xem lại yêu cầu</li>
          <li className="rounded-lg bg-slate-50 p-3">4. Nhà cung cấp duyệt → ký hợp đồng → trả trước</li>
        </ol>
      </div>

      {step === 1 && (
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
          <h3 className="text-lg font-bold text-slate-900">Thời hạn thuê</h3>
          <label className="mt-4 block max-w-sm text-sm font-semibold text-slate-700">
            Thời gian nhận xe
            <input
              type="datetime-local"
              value={start}
              min={minimumStart}
              onChange={(event) => {
                setStart(event.target.value)
                setStep(1)
              }}
              className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2.5 text-sm"
            />
          </label>
          {pickupTime && plannedEnd && (
            <p className="mt-3 text-sm text-slate-600">
              Thời hạn dự kiến: {formatVietnamDateTime(pickupTime)} đến {formatVietnamDateTime(plannedEnd)}
            </p>
          )}

          <div className={`mt-4 rounded-lg p-4 text-sm ${
            licenseCoversTerm ? 'bg-brand-50 text-brand-900' : 'bg-amber-50 text-amber-900'
          }`}>
            {licenseCoversTerm
              ? `GPLX hạng ${license.licenseClass} còn hạn đến hết kỳ thuê dự kiến.`
              : license?.yearlyBookingIneligibleReason
                || 'Cần GPLX đạt chính sách demo và còn hạn đến hết thời gian thuê 365 ngày.'}
            {license?.licenseVerified && !licenseCoversTerm && (
              <span> Kiểm tra ngày hết hạn theo thời gian thuê trước khi gửi yêu cầu.</span>
            )}
            {!licenseCoversTerm && (
              <div>
                <a href="#driving-license" className="mt-2 inline-block font-semibold underline">
                  Tải hoặc cập nhật GPLX
                </a>
              </div>
            )}
          </div>
          {vehicle.seatCount > 9 && (
            <p role="alert" className="mt-3 text-sm text-on-error-container">
              Bản demo chỉ hỗ trợ xe tối đa 9 chỗ tính cả người lái.
            </p>
          )}
          <button
            type="button"
            onClick={() => setStep(2)}
            disabled={!licenseCoversTerm || vehicle.seatCount > 9 || !pickupTime || pickupTime < new Date(minimumStart + ':00+07:00')}
            className="mt-5 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Tiếp tục chọn điểm nhận
          </button>
        </div>
      )}

      {step === 2 && (
        <PickupStep
          pickup={pickup}
          setPickup={setPickup}
          pickupQuote={pickupQuote}
          quotePending={pickupPending}
          quoteError={currentPickupError}
          driverNote=""
          setDriverNote={() => {}}
          passengerCount={effectivePassengers}
          setPassengerCount={setPassengerCount}
          seatCount={vehicle.seatCount}
          driverIncluded
          selfDrive
          maxPassengers={maxPassengers}
          onBack={() => setStep(1)}
          onNext={() => {
            if (pickupQuote && !pickupPending && !currentPickupError) setStep(3)
          }}
        />
      )}

      {step === 3 && (
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
          <h3 className="text-lg font-bold text-slate-900">Kiểm tra yêu cầu thuê năm</h3>
          <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
            <div><dt className="text-slate-500">Xe</dt><dd className="font-semibold">{vehicleName}</dd></div>
            <div><dt className="text-slate-500">Thời gian</dt><dd className="font-semibold">{formatVietnamDateTime(pickupTime)} · 365 ngày</dd></div>
            <div><dt className="text-slate-500">Điểm nhận</dt><dd className="font-semibold">{pickup.address}</dd></div>
            <div><dt className="text-slate-500">Hành khách ngoài người lái</dt><dd className="font-semibold">{effectivePassengers}</dd></div>
            <div><dt className="text-slate-500">Giá thuê cả kỳ</dt><dd className="font-semibold">{formatCurrency(planPrice)}</dd></div>
            <div><dt className="text-slate-500">Phí giao/nhận dự kiến</dt><dd className="font-semibold">{formatCurrency(pickupFee)}</dd></div>
            <div><dt className="text-slate-500">Khoản trả trước sau khi có hợp đồng</dt><dd className="font-semibold">{formatCurrency(upfrontAmount)}</dd></div>
          </dl>
          <p className="mt-4 rounded-lg bg-slate-50 p-4 text-sm leading-relaxed text-slate-600">
            Gửi yêu cầu chưa phát sinh thanh toán. Nhà cung cấp có 24 giờ để phản hồi; nếu chấp nhận, bạn cần tải hợp đồng đã ký
            trong thời hạn giữ xe, sau đó thanh toán khoản trả trước. Đơn thuê năm tự lái, không phân công tài xế.
          </p>
          <div className="mt-5 flex flex-wrap justify-between gap-3">
            <button type="button" onClick={() => setStep(2)} className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold">
              Quay lại
            </button>
            <button
              type="button"
              onClick={submitBooking}
              disabled={submitting || !pickupQuote || pickupPending || Boolean(currentPickupError)}
              className="rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
            >
              {submitting ? 'Đang gửi yêu cầu…' : 'Gửi yêu cầu thuê năm'}
            </button>
          </div>
        </div>
      )}

      <p className="text-xs text-slate-500">
        Muốn chọn xe khác? <Link to="/" className="font-semibold text-brand-700 underline">Quay lại danh sách xe</Link>
      </p>
    </section>
  )
}
