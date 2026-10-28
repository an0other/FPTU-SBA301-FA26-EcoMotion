import { useCallback, useEffect, useRef, useState } from 'react'
import { driverStatusService } from '../../services/driverStatusService'
import { useRealtimeSync } from '../../hooks/useRealtimeSync'
import DriverStatusBadge from './DriverStatusBadge'
import { DRIVER_STATUS_LABELS } from '../../utils/driverStatus'
export default function DriverAvailability() {
  const [status, setStatus] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const request = useRef(0)
  const refresh = useCallback(async () => {
    const id = ++request.current
    try {
      const data = await driverStatusService.getStatus()
      if (id === request.current) { setStatus(data); setError('') }
    } catch (err) {
      if (id === request.current) setError(err?.response?.data?.message || 'Không thể tải trạng thái')
    }
  }, [])
  useEffect(() => {
    const timer = setTimeout(refresh, 0)
    return () => { clearTimeout(timer); request.current++ }
  }, [refresh])
  useRealtimeSync({ onResync: refresh })
  async function update(activityStatus) {
    setSaving(true)
    ++request.current
    try { await driverStatusService.updateStatus(activityStatus); await refresh() }
    catch (err) { await refresh(); setError(err?.response?.data?.message || 'Không thể cập nhật trạng thái') }
    finally { setSaving(false) }
  }
  return <section className="mb-4 rounded-lg border border-slate-200 bg-white p-4" aria-label="Trạng thái nhận chuyến">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <DriverStatusBadge status={status?.operationalStatus} />
      <select aria-label="Sẵn sàng nhận chuyến" className="rounded border border-slate-300 p-2 text-sm" value={status?.activityStatus || ''} disabled={!status || saving || status.operationalStatus === 'ON_TRIP'} onChange={e => update(e.target.value)}>
        {!status && <option value="">Đang tải...</option>}
        {['AVAILABLE', 'UNAVAILABLE', 'OFFLINE'].map(value => <option key={value} value={value}>{DRIVER_STATUS_LABELS[value]}</option>)}
      </select>
    </div>
    {status?.activeTrip && <p className="mt-2 text-sm">Đang chạy: {status.activeTrip.bookingCode}</p>}
    {status?.nextTrip && <p className="mt-2 text-sm">Chuyến tiếp theo: {status.nextTrip.bookingCode} · {new Date(status.nextTrip.pickupTime).toLocaleString('vi-VN')}</p>}
    {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
  </section>
}
