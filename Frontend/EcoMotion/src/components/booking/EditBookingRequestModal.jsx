import { useEffect, useMemo, useState } from 'react'
import { PickupMap } from './PickupMap'
import { availabilityWindow, hasValidPickup, overlapsOccupied } from '../../utils/bookingAvailability'
import { vehicleService } from '../../services/vehicleService'

function localDateTime(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return shifted.toISOString().slice(0, 16)
}

export function EditBookingRequestModal({ booking, onClose, onSave }) {
  const [pickupTime, setPickupTime] = useState(localDateTime(booking.pickupTime))
  const [pickup, setPickup] = useState({ address: booking.pickupAddress, latitude: booking.pickupLatitude, longitude: booking.pickupLongitude })
  const [passengerCount, setPassengerCount] = useState(booking.passengerCount)
  const [driverNote, setDriverNote] = useState(booking.driverNote ?? '')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [occupiedSlots, setOccupiedSlots] = useState([])
  const [checkingAvailability, setCheckingAvailability] = useState(false)
  const [availabilityError, setAvailabilityError] = useState('')
  const [availabilityRetry, setAvailabilityRetry] = useState(0)
  const durationMs = useMemo(() => Math.max(0,
    new Date(booking.endTime).getTime() - new Date(booking.pickupTime).getTime()),
  [booking.endTime, booking.pickupTime])
  const requestedRange = useMemo(() => {
    const start = new Date(pickupTime)
    if (!pickupTime || Number.isNaN(start.getTime()) || durationMs <= 0) return null
    return { start, end: new Date(start.getTime() + durationMs) }
  }, [durationMs, pickupTime])
  const conflicts = requestedRange
    ? overlapsOccupied(requestedRange.start, requestedRange.end, occupiedSlots)
    : false

  useEffect(() => {
    if (!booking.vehicleId || !requestedRange) return undefined
    let cancelled = false
    const { from, to } = availabilityWindow(requestedRange.start, requestedRange.end)
    const timer = window.setTimeout(() => {
      setCheckingAvailability(true)
      setAvailabilityError('')
      vehicleService.getAvailability(booking.vehicleId, from, to)
        .then((response) => {
          if (!cancelled) setOccupiedSlots(response.occupiedSlots ?? [])
        })
        .catch((err) => {
          if (!cancelled) {
            setOccupiedSlots([])
            setAvailabilityError(err.message || 'Không kiểm tra được lịch xe')
          }
        })
        .finally(() => { if (!cancelled) setCheckingAvailability(false) })
    }, 0)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [availabilityRetry, booking.vehicleId, requestedRange])

  const submit = async (event) => {
    event.preventDefault()
    if (!hasValidPickup(pickup)) return setError('Vui lòng chọn điểm đón hợp lệ trên bản đồ')
    if (!requestedRange || requestedRange.start <= new Date()) return setError('Thời gian nhận xe phải ở tương lai')
    if (checkingAvailability || availabilityError) return setError('Vui lòng kiểm tra lại lịch xe trước khi lưu')
    if (conflicts) return setError('Xe đã có lịch trong khoảng thời gian này')
    setSaving(true); setError('')
    try {
      await onSave({
        pickupTime: new Date(pickupTime).toISOString(),
        pickupAddress: pickup.address,
        pickupLatitude: Number(pickup.latitude),
        pickupLongitude: Number(pickup.longitude),
        passengerCount: Number(passengerCount),
        driverNote: driverNote.trim() || null,
      })
      onClose()
    } catch (err) {
      setError(err.message || 'Không thể cập nhật yêu cầu')
      if (err.status === 409) setAvailabilityRetry((value) => value + 1)
    } finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/50 p-4">
      <form onSubmit={submit} className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl space-y-4">
        <div className="flex items-center justify-between"><h2 className="text-lg font-bold">Chỉnh sửa yêu cầu đặt xe</h2><button type="button" onClick={onClose}>✕</button></div>
        <label className="block text-sm font-semibold">Thời gian nhận xe<input required type="datetime-local" value={pickupTime} onChange={(e) => setPickupTime(e.target.value)} className="mt-1 w-full rounded-xl border p-2.5 font-normal" /></label>
        <div className={`rounded-xl border px-3 py-2 text-sm ${conflicts || availabilityError ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>
          {checkingAvailability
            ? 'Đang kiểm tra lịch xe...'
            : availabilityError
              ? <span>{availabilityError} <button type="button" className="font-semibold underline" onClick={() => setAvailabilityRetry((value) => value + 1)}>Thử lại</button></span>
              : conflicts ? 'Xe đã có lịch trong khoảng thời gian này.' : 'Xe đang trống trong khoảng thời gian đã chọn.'}
        </div>
        <PickupMap value={pickup} onChange={setPickup} onError={setError} />
        <label className="block text-sm font-semibold">Số khách<input required min="1" type="number" value={passengerCount} onChange={(e) => setPassengerCount(e.target.value)} className="mt-1 w-full rounded-xl border p-2.5 font-normal" /></label>
        <label className="block text-sm font-semibold">Ghi chú cho tài xế<textarea maxLength="160" value={driverNote} onChange={(e) => setDriverNote(e.target.value.replace(/[\r\n]+/g, ' '))} className="mt-1 w-full rounded-xl border p-2.5 font-normal" /></label>
        {error ? <p className="text-sm text-red-700">{error}</p> : null}
        <div className="flex justify-end gap-2"><button type="button" onClick={onClose} className="rounded-xl border px-4 py-2">Huỷ</button><button disabled={saving || checkingAvailability || Boolean(availabilityError) || conflicts} className="rounded-xl bg-[#15803D] px-4 py-2 font-semibold text-white disabled:opacity-50">{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button></div>
      </form>
    </div>
  )
}
