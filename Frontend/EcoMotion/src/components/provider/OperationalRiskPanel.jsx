import { useEffect, useState } from 'react'
import { providerService } from '../../services/providerService'
import { providerBookingService } from '../../services/providerBookingService'
import { vehicleService } from '../../services/vehicleService'
import { vietnamInputToInstant } from '../../utils/bookingWorkflow'
import { overlapsOccupied } from '../../utils/bookingAvailability'
import { formatBookingWhen } from '../../utils/bookingDateTime'

const SLOT_TYPE_LABELS = { BOOKED: 'Đã có chuyến', MAINTENANCE: 'Bảo trì', BLOCKED: 'Bị khóa' }

function slotLabel(slot) {
  return `${formatBookingWhen(slot.startAt)} → ${formatBookingWhen(slot.endAt)} · ${SLOT_TYPE_LABELS[slot.type] ?? slot.type}`
}

function safeInstant(value) {
  try { return vietnamInputToInstant(value) } catch { return null }
}

export default function OperationalRiskPanel({ booking, risk, workflow, onSuccess }) {
  const [vehicles, setVehicles] = useState([])
  const [resolution, setResolution] = useState('CHANGE_VEHICLE')
  const [vehicleId, setVehicleId] = useState('')
  const [pickup, setPickup] = useState('')
  // Giờ VN (UTC+7) sớm nhất: hiện tại + 1 giờ, khớp kiểm tra đề xuất đổi giờ ở backend.
  const [minPickup] = useState(() => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 16))
  const [reason, setReason] = useState(risk?.userMessage || 'Chưa có tài xế phù hợp, cần thống nhất phương án với khách thuê.')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [open, setOpen] = useState(false)
  // Lịch xe: lưu kèm khóa khoảng thời gian để không dùng kết quả của khoảng cũ.
  const [vehicleAvailability, setVehicleAvailability] = useState({})
  const [rescheduleAvailability, setRescheduleAvailability] = useState(null)

  const pendingProposal = workflow?.proposal?.status === 'PENDING'
  const awaitingDeposit = booking?.status === 'AWAITING_DEPOSIT' || booking?.status === 'ACCEPTED'
  const panelTitle = awaitingDeposit && !risk?.atRisk && !workflow?.needsDriver
    ? 'Không thể phục vụ chuyến này?'
    : risk?.atRisk ? 'Nguy cơ xe về trễ' : workflow?.needsDriver ? 'Chưa có tài xế phù hợp' : 'Phương án dịch vụ'

  const rentalStart = booking?.pickupTime || null
  const rentalEnd = booking?.plannedEndAt || null
  const hasWindow = Boolean(rentalStart && rentalEnd)
  const windowKey = `${rentalStart}|${rentalEnd}`
  const durationMs = hasWindow ? new Date(rentalEnd).getTime() - new Date(rentalStart).getTime() : 0

  // Dời lịch: khoảng mới = giờ nhận mới + đúng độ dài khoảng thuê hiện tại.
  const newStart = resolution === 'RESCHEDULE' && pickup ? safeInstant(pickup) : null
  const newEnd = newStart && durationMs > 0 ? new Date(new Date(newStart).getTime() + durationMs).toISOString() : null
  const rescheduleKey = newStart && newEnd ? `${newStart}|${newEnd}` : null

  useEffect(() => {
    let cancelled = false
    queueMicrotask(() => {
      if (!cancelled && awaitingDeposit && !risk?.atRisk && !workflow?.needsDriver && !workflow?.proposal) {
        setResolution('CANCEL_AND_REFUND')
        setReason('Nhà xe không thể cung cấp xe cho chuyến này.')
      }
    })
    return () => { cancelled = true }
  }, [awaitingDeposit, risk?.atRisk, workflow?.needsDriver, workflow?.proposal])

  useEffect(() => {
    let cancelled = false
    providerService.getVehicles({ limit: 100, status: 'APPROVED' }).then(result => {
      if (!cancelled) setVehicles((result?.items ?? result ?? []).filter(v => v.id !== booking.vehicleId))
    }).catch(err => { if (!cancelled) setError(err.message || 'Không thể tải xe thay thế.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [booking.vehicleId, retry])

  // Đổi xe: kiểm tra từng xe thay thế trong đúng khoảng thuê hiện tại.
  useEffect(() => {
    if (resolution !== 'CHANGE_VEHICLE' || !hasWindow || vehicles.length === 0) return undefined
    let cancelled = false
    vehicles.forEach((vehicle) => {
      vehicleService.getAvailability(vehicle.id, rentalStart, rentalEnd)
        .then((res) => {
          if (cancelled) return
          const slots = res?.occupiedSlots ?? []
          setVehicleAvailability(prev => ({
            ...prev,
            [vehicle.id]: { key: windowKey, status: overlapsOccupied(rentalStart, rentalEnd, slots) ? 'busy' : 'free', slots },
          }))
        })
        .catch(() => {
          if (!cancelled) setVehicleAvailability(prev => ({ ...prev, [vehicle.id]: { key: windowKey, status: 'error', slots: [] } }))
        })
    })
    return () => { cancelled = true }
  }, [resolution, vehicles, hasWindow, rentalStart, rentalEnd, windowKey])

  // Dời lịch: lịch của xe hiện tại trong khoảng giờ mới.
  useEffect(() => {
    if (!rescheduleKey || !booking.vehicleId) return undefined
    let cancelled = false
    vehicleService.getAvailability(booking.vehicleId, newStart, newEnd)
      .then((res) => {
        if (cancelled) return
        const slots = res?.occupiedSlots ?? []
        setRescheduleAvailability({
          key: rescheduleKey,
          status: overlapsOccupied(newStart, newEnd, slots) ? 'busy' : 'free',
          slots,
        })
      })
      .catch(() => {
        if (!cancelled) setRescheduleAvailability({ key: rescheduleKey, status: 'error', slots: [] })
      })
    return () => { cancelled = true }
  }, [rescheduleKey, newStart, newEnd, booking.vehicleId])

  function vehicleStatus(id) {
    const entry = vehicleAvailability[id]
    if (!entry || entry.key !== windowKey) return 'loading'
    return entry.status
  }

  const selectedVehicleStatus = vehicleId ? vehicleStatus(vehicleId) : null
  const rescheduleStatus = rescheduleAvailability && rescheduleAvailability.key === rescheduleKey
    ? rescheduleAvailability
    : null

  async function submit(event) {
    event.preventDefault()
    if (submitting) return
    if (resolution === 'CANCEL_AND_REFUND' && !window.confirm('Hủy đơn và bắt đầu xử lý hoàn tiền theo chính sách? Thao tác này chưa tự chuyển tiền vào tài khoản khách.')) return
    setSubmitting(true)
    setError('')
    try {
      const result = await providerBookingService.resolveOperationalRisk(booking.bookingCode, {
        resolution, reason: reason.trim(),
        ...(resolution === 'CHANGE_VEHICLE' ? { replacementVehicleId: Number(vehicleId) } : {}),
        ...(resolution === 'RESCHEDULE' ? { newPickupTime: vietnamInputToInstant(pickup) } : {}),
      })
      await onSuccess(result)
      setOpen(false)
    } catch (err) { setError(err.message || 'Không thể lưu phương án xử lý.') }
    finally { setSubmitting(false) }
  }

  // Đơn chưa cọc: không có gì để "đề xuất phương án" — chỉ cần huỷ đơn kèm lý do, không phát sinh hoàn tiền.
  const simpleCancel = awaitingDeposit && !risk?.atRisk && !workflow?.needsDriver && !workflow?.proposal
    && !(Number(workflow?.confirmedPaid) > 0)
  if (simpleCancel) {
    const cancelUnpaid = async (event) => {
      event.preventDefault()
      if (submitting || !reason.trim()) return
      setSubmitting(true)
      setError('')
      try {
        const result = await providerBookingService.resolveOperationalRisk(booking.bookingCode, {
          resolution: 'CANCEL_AND_REFUND', reason: reason.trim(),
        })
        await onSuccess(result)
        setOpen(false)
      } catch (err) { setError(err.message || 'Không thể huỷ đơn.') }
      finally { setSubmitting(false) }
    }
    return (
      <section className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-sm font-bold text-slate-900">Không nhận được chuyến này?</h2>
            <p className="mt-0.5 text-xs text-slate-600">
              Khách chưa đặt cọc nên không phát sinh hoàn tiền. Huỷ đơn sẽ giải phóng lịch xe và báo cho khách.
            </p>
          </div>
          {!open && (
            <button type="button" onClick={() => setOpen(true)}
              className="h-10 shrink-0 rounded-lg border border-rose-300 bg-white px-4 text-sm font-bold text-rose-700 hover:bg-rose-50">
              Huỷ đơn (khách chưa cọc)
            </button>
          )}
        </div>
        {open && (
          <form onSubmit={cancelUnpaid} className="mt-3 space-y-2">
            <label className="block text-xs font-semibold text-slate-700">Lý do huỷ (khách sẽ thấy)
              <textarea required maxLength={500} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} disabled={submitting}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal" />
            </label>
            <p className="text-[11px] text-amber-800">Nếu khách vừa báo đã chuyển khoản, hãy kiểm tra sao kê trước khi huỷ.</p>
            {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setOpen(false)} disabled={submitting}
                className="h-9 rounded-lg border border-slate-300 px-3 text-xs font-semibold text-slate-700">Không huỷ</button>
              <button type="submit" disabled={submitting || !reason.trim()}
                className="h-9 rounded-lg bg-rose-700 px-4 text-xs font-bold text-white hover:bg-rose-800 disabled:opacity-50">
                {submitting ? 'Đang huỷ...' : 'Xác nhận huỷ đơn'}
              </button>
            </div>
          </form>
        )}
      </section>
    )
  }

  const windowText = hasWindow
    ? `${formatBookingWhen(rentalStart)} → ${formatBookingWhen(rentalEnd)}`
    : 'chưa có khoảng thuê'

  return (
    <>
      <section className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-950">
        <div className="min-w-0">
          <h2 className="text-sm font-bold">{panelTitle}</h2>
          <p className="mt-0.5 line-clamp-2 text-xs text-amber-900">
            {(risk?.userMessage || risk?.message) || 'Chọn phương án xử lý và gửi cho khách thuê.'}
          </p>
          {risk?.atRisk && risk?.blockingBookingCode ? (
            <dl className="mt-2 grid gap-x-4 gap-y-1 text-xs text-amber-950 sm:grid-cols-3">
              <div><dt className="text-amber-800">Chuyến đang giữ xe</dt><dd className="font-mono font-semibold">{risk.blockingBookingCode}</dd></div>
              <div><dt className="text-amber-800">Lịch trả xe của chuyến đó</dt><dd className="font-semibold">{formatBookingWhen(risk.blockingPlannedEndAt)}</dd></div>
              <div>
                <dt className="text-amber-800">Dự kiến sẵn sàng</dt>
                <dd className="font-semibold">
                  {formatBookingWhen(risk.estimatedReadyAt)}
                  {risk.estimatedDelayMinutes ? <span className="text-red-700"> · trễ ~{risk.estimatedDelayMinutes} phút</span> : null}
                </dd>
              </div>
            </dl>
          ) : null}
        </div>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="h-10 shrink-0 rounded-lg bg-amber-700 px-4 text-sm font-bold text-white hover:bg-amber-800 focus:outline-none focus:ring-2 focus:ring-amber-500"
        >
          Xử lý phương án
        </button>
      </section>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
          onClick={() => { if (!submitting) setOpen(false) }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={panelTitle}
            onKeyDown={(e) => { if (e.key === 'Escape' && !submitting) setOpen(false) }}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-950 shadow-xl"
          >
            <div className="flex items-start justify-between gap-3">
              <h2 className="text-base font-bold">{panelTitle}</h2>
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={submitting}
                className="rounded-lg px-2.5 py-1 text-sm font-semibold text-amber-900 hover:bg-amber-100 disabled:opacity-50"
              >
                Đóng
              </button>
            </div>
            <p className="mt-1">
              {awaitingDeposit && !risk?.atRisk && !workflow?.needsDriver
                ? 'Khách chưa cọc hoặc chưa gửi biên lai. Nếu nhà xe không nhận chuyến, chọn Hủy và hoàn tiền — không dùng nút Từ chối ở tab Tài chính khi chưa có biên lai.'
                : ((risk?.userMessage || risk?.message) || 'Thử tìm tài xế lại ở mục phân công, hoặc gửi phương án khác cho khách. Không đổi xe, giờ đón hay giá khi khách chưa đồng ý.')}
            </p>
            {pendingProposal && <p className="mt-2 text-xs">Đang chờ khách phản hồi; không thể gửi đè một đề xuất khác. Nếu không tiếp tục cung cấp dịch vụ, nhà xe có thể hủy và bắt đầu hoàn tiền.</p>}

            <form onSubmit={submit} className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-xs font-semibold text-amber-900">
                Phương án
                <select aria-label="Phương án xử lý" value={resolution} onChange={e => setResolution(e.target.value)} disabled={submitting} className="h-10 w-full rounded-lg border border-amber-300 bg-white px-3 text-sm font-normal text-slate-900 focus:outline-none focus:ring-2 focus:ring-amber-500">
                  <option value="CHANGE_VEHICLE">Đề xuất đổi xe</option><option value="RESCHEDULE">Đề xuất dời lịch</option><option value="CANCEL_AND_REFUND">Hủy và hoàn tiền</option><option value="OTHER">Khác (tự mô tả phương án)</option>
                </select>
              </label>
              {resolution === 'OTHER' && (
                <p className="self-end text-[11px] text-amber-900 sm:col-span-1">
                  Mô tả cụ thể phương án ở ô bên dưới (tối thiểu 10 ký tự). Khách đồng ý thì phương án được ghi nhận; hệ thống không tự đổi xe hay giờ.
                </p>
              )}

              {resolution === 'RESCHEDULE' && (
                <label className="flex flex-col gap-1 text-xs font-semibold text-amber-900">
                  Giờ nhận xe mới
                  <input aria-label="Giờ nhận xe mới" type="datetime-local" required min={minPickup} value={pickup} onChange={e => setPickup(e.target.value)} disabled={submitting} className="h-10 w-full rounded-lg border border-amber-300 bg-white px-3 text-sm font-normal text-slate-900 focus:outline-none focus:ring-2 focus:ring-amber-500" />
                </label>
              )}

              {resolution === 'CHANGE_VEHICLE' && (
                <fieldset className="sm:col-span-2 space-y-2">
                  <legend className="text-xs font-semibold text-amber-900">
                    Xe thay thế · trống trong khoảng thuê {windowText}
                  </legend>
                  {loading && <p className="text-xs text-amber-900">Đang tải danh sách xe…</p>}
                  {!loading && vehicles.length === 0 && <p className="text-xs text-amber-900">Không có xe khác đã duyệt để thay thế.</p>}
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                    {vehicles.map((vehicle) => {
                      const status = vehicleStatus(vehicle.id)
                      const entry = vehicleAvailability[vehicle.id]
                      const selectable = status === 'free' && !submitting
                      const checked = String(vehicleId) === String(vehicle.id)
                      return (
                        <label
                          key={vehicle.id}
                          className={`flex cursor-pointer items-start gap-3 rounded-lg border bg-white p-3 ${
                            checked ? 'border-amber-600 ring-2 ring-amber-500' : 'border-amber-200'
                          } ${selectable ? '' : 'cursor-not-allowed opacity-70'}`}
                        >
                          <input
                            type="radio"
                            name="replacement-vehicle"
                            value={vehicle.id}
                            checked={checked}
                            disabled={!selectable}
                            onChange={() => setVehicleId(String(vehicle.id))}
                            className="mt-1"
                          />
                          <div className="min-w-0 flex-1">
                            <div className="font-semibold text-slate-900">
                              {vehicle.licensePlate} · {vehicle.brand} {vehicle.model}
                            </div>
                            {status === 'loading' && <div className="text-xs text-slate-500">Đang kiểm tra lịch…</div>}
                            {status === 'free' && <div className="text-xs font-semibold text-emerald-700">Trống trong khoảng thuê</div>}
                            {status === 'error' && <div className="text-xs font-semibold text-red-700">Không kiểm tra được lịch xe</div>}
                            {status === 'busy' && (
                              <div className="mt-1 space-y-0.5">
                                <div className="text-xs font-semibold text-rose-700">Bận trong khoảng thuê</div>
                                {entry?.slots.map((slot, i) => (
                                  <div key={`${slot.startAt}-${i}`} className="text-[11px] text-slate-600">{slotLabel(slot)}</div>
                                ))}
                              </div>
                            )}
                          </div>
                        </label>
                      )
                    })}
                  </div>
                </fieldset>
              )}

              {resolution === 'RESCHEDULE' && (
                <div className="sm:col-span-2 rounded-lg border border-amber-200 bg-white p-3 text-xs">
                  <div className="font-semibold text-amber-900">Lịch xe hiện tại trong khoảng giờ mới</div>
                  {!pickup && <p className="mt-1 text-slate-600">Chọn giờ nhận xe mới để kiểm tra lịch.</p>}
                  {pickup && !newStart && <p className="mt-1 text-slate-600">Giờ chưa hợp lệ.</p>}
                  {newStart && !rescheduleStatus && <p className="mt-1 text-slate-600">Đang kiểm tra lịch xe…</p>}
                  {rescheduleStatus?.status === 'free' && <p className="mt-1 font-semibold text-emerald-700">Xe trống trong khoảng giờ mới.</p>}
                  {rescheduleStatus?.status === 'error' && <p className="mt-1 font-semibold text-red-700">Không kiểm tra được lịch xe.</p>}
                  {rescheduleStatus?.status === 'busy' && (
                    <div className="mt-1 space-y-0.5">
                      <p className="font-semibold text-rose-700">Xe bận trong khoảng giờ mới:</p>
                      {rescheduleStatus.slots.map((slot, i) => (
                        <div key={`${slot.startAt}-${i}`} className="text-slate-600">{slotLabel(slot)}</div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              <label className="flex flex-col gap-1 text-xs font-semibold text-amber-900 sm:col-span-2">
                <span className="flex items-center justify-between">
                  <span>{resolution === 'OTHER' ? 'Phương án đề xuất cho khách' : 'Lý do'}</span>
                  <span className="font-normal text-amber-800">{reason.length}/500</span>
                </span>
                <textarea aria-label="Lý do xử lý" required maxLength={500} rows={2} value={reason} onChange={e => setReason(e.target.value)} disabled={submitting} className="w-full resize-y rounded-lg border border-amber-300 bg-white px-3 py-2 text-sm font-normal text-slate-900 focus:outline-none focus:ring-2 focus:ring-amber-500" />
              </label>

              <div className="flex flex-col-reverse gap-2 sm:col-span-2 sm:flex-row sm:items-center sm:justify-end">
                <button
                  type="submit"
                  disabled={
                    submitting
                    || !reason.trim()
                    || (resolution === 'OTHER' && reason.trim().length < 10)
                    || (pendingProposal && resolution !== 'CANCEL_AND_REFUND')
                    || (resolution === 'CHANGE_VEHICLE' && (!vehicleId || loading || selectedVehicleStatus !== 'free'))
                    || (resolution === 'RESCHEDULE' && (!newStart || rescheduleStatus?.status !== 'free'))
                  }
                  className="h-10 rounded-lg bg-brand-700 px-5 text-sm font-bold text-white hover:bg-brand-800 focus:outline-none focus:ring-2 focus:ring-brand-500 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600"
                >
                  {submitting ? 'Đang gửi...' : resolution === 'CANCEL_AND_REFUND' ? 'Hủy đơn và xử lý hoàn tiền' : 'Gửi đề xuất cho khách'}
                </button>
              </div>
            </form>
            {error && <div role="alert" className="mt-2 text-red-800">{error} <button type="button" disabled={loading} onClick={() => { setLoading(true); setError(''); setRetry(v => v + 1) }} className="font-semibold underline">Tải lại xe</button></div>}
          </div>
        </div>
      )}
    </>
  )
}
