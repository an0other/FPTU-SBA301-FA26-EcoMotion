import { useEffect, useState } from 'react'
import { providerService } from '../../services/providerService'
import { providerBookingService } from '../../services/providerBookingService'
import { vietnamInputToInstant } from '../../utils/bookingWorkflow'

export default function OperationalRiskPanel({ booking, risk, workflow, onSuccess }) {
  const [vehicles, setVehicles] = useState([])
  const [resolution, setResolution] = useState('CHANGE_VEHICLE')
  const [vehicleId, setVehicleId] = useState('')
  const [pickup, setPickup] = useState('')
  const [reason, setReason] = useState(risk?.message || 'Chưa có tài xế phù hợp, cần thống nhất phương án với khách thuê.')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const pendingProposal = workflow?.proposal?.status === 'PENDING'
  useEffect(() => {
    let cancelled = false
    providerService.getVehicles({ limit: 100, status: 'APPROVED' }).then(result => {
      if (!cancelled) setVehicles((result?.items ?? result ?? []).filter(v => v.id !== booking.vehicleId))
    }).catch(err => { if (!cancelled) setError(err.message || 'Không thể tải xe thay thế.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [booking.vehicleId, retry])

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
    } catch (err) { setError(err.message || 'Không thể lưu phương án xử lý.') }
    finally { setSubmitting(false) }
  }

  return (
    <section className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950">
      <h2 className="font-bold">{risk?.atRisk ? 'Nguy cơ xe về trễ' : workflow?.needsDriver ? 'Chưa có tài xế phù hợp' : 'Phương án dịch vụ'}</h2>
      <p className="mt-1">{risk?.message || 'Thử tìm tài xế lại ở mục phân công, hoặc gửi phương án khác cho khách. Không đổi xe, giờ đón hay giá khi khách chưa đồng ý.'}</p>
      {pendingProposal && <p className="mt-2 text-xs">Đang chờ khách phản hồi; không thể gửi đè một đề xuất khác. Nếu không tiếp tục cung cấp dịch vụ, nhà xe có thể hủy và bắt đầu hoàn tiền.</p>}
      <form onSubmit={submit} className="mt-3 flex flex-wrap items-end gap-3">
        <label className="text-xs">Phương án
          <select aria-label="Phương án xử lý" value={resolution} onChange={e => setResolution(e.target.value)} disabled={submitting} className="block h-10 rounded border border-amber-300 bg-white px-2">
            <option value="CHANGE_VEHICLE">Đề xuất đổi xe</option><option value="RESCHEDULE">Đề xuất dời lịch</option><option value="CANCEL_AND_REFUND">Hủy và hoàn tiền</option>
          </select>
        </label>
        {resolution === 'CHANGE_VEHICLE' && <label className="text-xs">Xe thay thế
          <select aria-label="Xe thay thế" required value={vehicleId} onChange={e => setVehicleId(e.target.value)} disabled={loading || submitting} className="block h-10 rounded border border-amber-300 bg-white px-2">
            <option value="">{loading ? 'Đang tải...' : 'Chọn xe'}</option>
            {vehicles.map(v => <option key={v.id} value={v.id}>{v.licensePlate} · {v.brand} {v.model}</option>)}
          </select>
        </label>}
        {resolution === 'RESCHEDULE' && <label className="text-xs">Giờ nhận xe mới<input aria-label="Giờ nhận xe mới" type="datetime-local" required value={pickup} onChange={e => setPickup(e.target.value)} disabled={submitting} className="block h-10 rounded border border-amber-300 bg-white px-2" /></label>}
        <label className="text-xs grow">Lý do<input aria-label="Lý do xử lý" required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} disabled={submitting} className="block h-10 w-full rounded border border-amber-300 bg-white px-2" /></label>
        <button type="submit" disabled={submitting || !reason.trim() || pendingProposal && resolution !== 'CANCEL_AND_REFUND' || resolution === 'CHANGE_VEHICLE' && (!vehicleId || loading)} className="h-10 rounded bg-brand-700 px-4 text-xs font-bold text-white disabled:opacity-50">{submitting ? 'Đang gửi...' : resolution === 'CANCEL_AND_REFUND' ? 'Hủy đơn và xử lý hoàn tiền' : 'Gửi đề xuất cho khách'}</button>
      </form>
      {error && <div role="alert" className="mt-2 text-red-800">{error} <button type="button" disabled={loading} onClick={() => { setLoading(true); setError(''); setRetry(v => v + 1) }} className="font-semibold underline">Tải lại xe</button></div>}
    </section>
  )
}
