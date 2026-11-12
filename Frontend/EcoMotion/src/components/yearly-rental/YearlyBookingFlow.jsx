import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { vehicleService } from '../../services/vehicleService'
import YearlyPickupCalendar from './YearlyPickupCalendar'
import { useVehicle } from '../../hooks/useVehicle'
import { createBooking, quotePickup, getBookingStartPolicy } from '../../services/bookingService'
import { readErrorMessage } from '../../services/apiClient'
import { formatCurrency } from '../../utils/formatCurrency'
import { BookingErrorDialog } from '../booking/BookingErrorDialog'
import { PickupStep } from '../booking/PickupStep'

function calendarYearEnd(date) {
  const local = new Date(date.getTime() + 7 * 60 * 60 * 1000)
  const year = local.getUTCFullYear() + 1
  const month = local.getUTCMonth()
  const day = Math.min(local.getUTCDate(), new Date(Date.UTC(year, month + 1, 0)).getUTCDate())
  local.setUTCDate(1)
  local.setUTCFullYear(year, month, day)
  return new Date(local.getTime() - 7 * 60 * 60 * 1000)
}
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
  const [availability, setAvailability] = useState(null)
  const [calendar, setCalendar] = useState(null)
  const [calendarRefresh, setCalendarRefresh] = useState(0)
  const [startPolicy, setStartPolicy] = useState(null)
  const [startPolicyError, setStartPolicyError] = useState('')
  const [start, setStart] = useState(() => vietnamInputValue(Date.now() + 30 * 60 * 60 * 1000))
  const [pickup, setPickup] = useState({ address: '', latitude: null, longitude: null })
  const [pickupState, setPickupState] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [minimumStart] = useState(() =>
    vietnamInputValue(Date.now() + 3 * 60 * 60 * 1000),
  )

  useEffect(() => {
    let active = true
    getBookingStartPolicy().then(data => {
      if (active) setStartPolicy(data)
    }).catch(e => { if (active) setStartPolicyError(readErrorMessage(e)) })
    return () => { active = false }
  }, [])

  const plan = vehicle?.plans?.find((item) => String(item.id) === String(planId))
  const pickupTime = useMemo(() => parseVietnamInput(start), [start])
  const withinStartWindow = Boolean(pickupTime && startPolicy
    && pickupTime.getTime() <= new Date(startPolicy.latestPickupTime).getTime()
    && pickupTime.getTime() >= new Date(minimumStart + ':00+07:00').getTime())
  const plannedEnd = pickupTime ? calendarYearEnd(pickupTime) : null
  const licenseCoversTerm = Boolean(
    license?.yearlyBookingEligible
      && plannedEnd
      && license.licenseExpiry
      && license.licenseExpiry >= vietnamDate(plannedEnd.getTime()),
  )
  const validVehicle = Boolean(vehicle && plan && plan.planType === 'YEARLY')
  const calendarFrom = `${minimumStart}:00+07:00`
  const calendarTo = startPolicy?.latestPickupTime ? calendarYearEnd(new Date(startPolicy.latestPickupTime)) : null
  const calendarEnd = calendarTo ? new Date(`${vietnamDate(calendarTo.getTime()+86400000)}T00:00:00+07:00`).toISOString() : ''
  const calendarKey = validVehicle && calendarEnd ? `${vehicleId}/${calendarFrom}/${calendarEnd}/${calendarRefresh}` : ''
  useEffect(()=>{
    if(!calendarKey)return
    let alive=true
    vehicleService.getAvailability(vehicleId,calendarFrom,calendarEnd).then(data=>{if(alive)setCalendar({key:calendarKey,slots:data.occupiedSlots,error:''})})
      .catch(()=>{if(alive)setCalendar({key:calendarKey,slots:[],error:'Chưa tải được lịch xe. Bấm cập nhật lịch để thử lại.'})})
    return ()=>{alive=false}
  },[calendarKey,vehicleId,calendarFrom,calendarEnd])
  const availabilityFrom = pickupTime?.toISOString()
  const availabilityTo = plannedEnd ? new Date(`${vietnamDate(plannedEnd.getTime() + 24 * 60 * 60 * 1000)}T00:00:00+07:00`).toISOString() : undefined
  const availabilityKey = validVehicle && availabilityFrom && availabilityTo ? `${vehicleId}/${availabilityFrom}/${availabilityTo}` : ''
  useEffect(() => {
    if (!availabilityKey) return
    let alive = true
    const timer=setTimeout(() => vehicleService.getAvailability(vehicleId,availabilityFrom,availabilityTo)
      .then(value=>{if(alive)setAvailability({key:availabilityKey,slots:value.occupiedSlots || [],error:''})})
      .catch(()=>{if(alive)setAvailability({key:availabilityKey,slots:[],error:'Chưa kiểm tra được lịch xe. Vui lòng thử lại hoặc chọn xe khác.'})}),350)
    return ()=>{alive=false;clearTimeout(timer)}
  },[availabilityKey,vehicleId,availabilityFrom,availabilityTo])
  const availabilityPending = !!availabilityKey && availability?.key !== availabilityKey
  const occupied = availability?.key === availabilityKey && availability.slots.some(slot=>new Date(slot.startAt)<new Date(availabilityTo) && new Date(slot.endAt)>pickupTime)
  const availabilityError = availability?.key === availabilityKey ? availability.error : ''
  const maxPassengers = Math.max(1, (vehicle?.seatCount ?? 2) - 1)
  const effectivePassengers = 1

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
    if (!withinStartWindow) {
      setError('Ngày nhận xe vượt giới hạn đặt trước của hệ thống. Vui lòng chọn lại.')
      setStep(1)
      return
    }
    if (!licenseCoversTerm) {
      setError('GPLX chưa đủ điều kiện hoặc không còn hạn hết toàn bộ thời gian thuê năm.')
      setStep(1)
      return
    }

    if (availabilityPending || occupied || availabilityError) { setStep(1);setError('Hãy chọn thời gian xe còn trống và kiểm tra lịch xe trước khi tiếp tục.');return }
    setSubmitting(true)
    setError('')
    try {
      const latestAvailability=await vehicleService.getAvailability(vehicleId,availabilityFrom,availabilityTo)
      if ((latestAvailability.occupiedSlots || []).some(slot=>new Date(slot.startAt)<new Date(availabilityTo) && new Date(slot.endAt)>pickupTime)) {
        setAvailability({key:availabilityKey,slots:latestAvailability.occupiedSlots,error:''});setStep(1)
        throw new Error('Lịch xe vừa thay đổi. Vui lòng chọn khoảng thời gian hoặc xe khác.')
      }
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
  const upfrontAmount = Number(plan.securityDepositAmount ?? plan.upfrontAmount ?? 0)
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
            <p className="text-xs font-semibold uppercase tracking-wide text-brand-700">Thuê tự lái · 12 tháng</p>
            <h2 className="mt-1 text-lg font-bold text-slate-900">{vehicleName}</h2>
            <p className="mt-1 text-sm text-slate-500">
              Tổng giá kỳ thuê: {formatCurrency(planPrice)} · Tiền bảo đảm riêng: {formatCurrency(upfrontAmount)}
            </p>
          </div>
        </div>

        <ol className="mt-5 grid gap-2 text-xs text-slate-600 sm:grid-cols-4">
          <li className={`rounded-lg p-3 ${step === 1 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>1. Chọn ngày nhận</li>
          <li className={`rounded-lg p-3 ${step === 2 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>2. Điểm nhận xe</li>
          <li className={`rounded-lg p-3 ${step === 3 ? 'bg-brand-50 font-semibold text-brand-800' : 'bg-slate-50'}`}>3. Xem lại yêu cầu</li>
          <li className="rounded-lg bg-slate-50 p-3">4. Nhà cung cấp duyệt → chốt điều khoản → ký hợp đồng → cọc và tháng đầu</li>
        </ol>
      </div>

      {step === 1 && (
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card sm:p-6">
          <h3 className="text-lg font-bold text-slate-900">Thời hạn thuê</h3>
          <YearlyPickupCalendar value={start} minimum={minimumStart} maximum={startPolicy?.latestPickupTime}
            slots={calendar?.key===calendarKey?calendar.slots:null} loading={!calendarKey || calendar?.key!==calendarKey}
            error={calendar?.key===calendarKey?calendar.error:''} onChange={setStart} onRefresh={()=>setCalendarRefresh(value=>value+1)} />
          {startPolicyError && <p role="alert" className="mt-2 text-sm text-red-700">Chưa tải được giới hạn ngày: {startPolicyError}. Vui lòng tải lại trang.</p>}
          {startPolicy && <p className="mt-2 text-sm text-slate-600">Ngày nhận xe cách hiện tại ít nhất 3 giờ và tối đa {startPolicy.maxAdvanceMonths} tháng.</p>}
          {startPolicy && pickupTime && !withinStartWindow && <p role="alert" className="mt-2 text-sm text-red-700">Ngày nhận xe phải nằm trong giới hạn đặt trước.</p>}
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
                || 'Cần GPLX đạt chính sách demo và còn hạn đến hết thời gian thuê 12 tháng.'}
            {license?.licenseVerified && !licenseCoversTerm && (
              <span> Kiểm tra ngày hết hạn theo thời gian thuê trước khi gửi yêu cầu.</span>
            )}
            {!licenseCoversTerm && (
              <div>
                <a href="#driving-license" onClick={() => { const section = document.getElementById('driving-license'); if (section) section.open = true }} className="mt-2 inline-block font-semibold underline">
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
          {availabilityPending && <p role="status" className="mt-3 rounded-lg bg-blue-50 p-3 text-sm text-blue-900">Đang kiểm tra lịch xe cho toàn bộ 12 tháng…</p>}
          {(occupied || availabilityError) && <div role="alert" className="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950">{availabilityError || 'Xe đã có lịch trong kỳ thuê này, bao gồm thời gian chuẩn bị xe. Hãy đổi ngày nhận hoặc chọn xe khác trước khi tiếp tục.'}<Link className="ml-2 font-semibold underline" to="/">Chọn xe khác</Link></div>}
          {!availabilityPending && !occupied && !availabilityError && availability?.key === availabilityKey && <p className="mt-3 text-sm text-emerald-800">Xe hiện còn trống trong kỳ thuê đã chọn. Lịch được kiểm tra lại khi gửi yêu cầu.</p>}
          <button
            type="button"
            onClick={() => setStep(2)}
            disabled={!licenseCoversTerm || vehicle.seatCount > 9 || !withinStartWindow || availabilityPending || occupied || !!availabilityError}
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
          setPassengerCount={() => {}}
          showPassengers={false}
          seatCount={vehicle.seatCount}
          driverIncluded={false}
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
            <div><dt className="text-slate-500">Thời gian</dt><dd className="font-semibold">{formatVietnamDateTime(pickupTime)} · 12 tháng</dd></div>
            <div><dt className="text-slate-500">Điểm nhận</dt><dd className="font-semibold">{pickup.address}</dd></div>
            <div><dt className="text-slate-500">Hình thức</dt><dd className="font-semibold">Tự lái · sử dụng xe trong 12 tháng</dd></div>
            <div><dt className="text-slate-500">Giá thuê cả kỳ</dt><dd className="font-semibold">{formatCurrency(planPrice)}</dd></div>
            <div><dt className="text-slate-500">Phí giao/nhận dự kiến</dt><dd className="font-semibold">{formatCurrency(pickupFee)}</dd></div>
            <div><dt className="text-slate-500">Tiền bảo đảm (chưa gồm tiền thuê tháng đầu)</dt><dd className="font-semibold">{formatCurrency(upfrontAmount)}</dd></div>
          </dl>
          <p className="mt-4 rounded-lg bg-slate-50 p-4 text-sm leading-relaxed text-slate-600">
            Gửi yêu cầu chưa phát sinh thanh toán. Nhà cung cấp có 24 giờ để phản hồi; nếu chấp nhận, bạn cần tải hợp đồng đã ký
            trong thời hạn giữ xe, sau đó thanh toán tiền bảo đảm và tiền thuê tháng đầu. Đơn thuê năm tự lái, không phân công tài xế.
          </p>
          <section className="mt-4 space-y-3 rounded-xl border border-blue-200 bg-blue-50 p-4" aria-label="Lộ trình thanh toán">
            <h4 className="font-bold">Bạn sẽ thanh toán như thế nào?</h4>
            <ol className="list-decimal space-y-2 pl-5 text-sm">
              <li><strong>Gửi yêu cầu: 0 đ.</strong> Provider kiểm tra xe và phản hồi. Chưa chuyển tiền ở bước này.</li>
              <li><strong>Chốt điều khoản và ký hợp đồng.</strong> Kiểm tra km, phí vượt km, trả sớm/trễ và hạn giữ xe trước khi đồng ý.</li>
              <li><strong>Trước nhận xe:</strong> thanh toán riêng tiền bảo đảm {formatCurrency(upfrontAmount)} và kỳ thuê tháng đầu theo lịch hợp đồng. Phí giao/nhận dự kiến {formatCurrency(pickupFee)} được ghi rõ trong hợp đồng và yêu cầu thanh toán.</li>
              <li><strong>Trong kỳ thuê:</strong> trả trước từng tháng theo số tiền, ngày đến hạn và tài khoản nhận tiền trên mỗi yêu cầu. Tổng tiền thuê 12 tháng là {formatCurrency(planPrice)}, không phải số phải trả ngay.</li>
              <li><strong>Kết thúc:</strong> kiểm định xe, đối soát phí và tiền đã trả; hoàn phần tiền bảo đảm còn lại sau các khấu trừ được thống nhất.</li>
            </ol>
            <p className="text-xs text-slate-600">Thông tin chuyển khoản và nội dung thanh toán chỉ sử dụng từ yêu cầu thanh toán của hợp đồng. Báo đã chuyển tiền chưa đồng nghĩa Provider đã xác nhận nhận tiền.</p>
          </section>
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
