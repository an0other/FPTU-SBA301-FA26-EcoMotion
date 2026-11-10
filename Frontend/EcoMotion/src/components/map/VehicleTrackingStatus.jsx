import LiveVehicleMap from './LiveVehicleMap'

/**
 * Live tracking panel: signal status and last update on top, the map of the vehicle and pickup point,
 * then the pickup address. Data comes from the same tracking payload as before.
 */
const TONE = {
  live: { dot: 'bg-emerald-500', pill: 'bg-emerald-50 text-emerald-800 border-emerald-200', label: 'Đang theo dõi trực tiếp' },
  stale: { dot: 'bg-amber-500', pill: 'bg-amber-50 text-amber-800 border-amber-200', label: 'Tín hiệu GPS bị trễ' },
  none: { dot: 'bg-slate-400', pill: 'bg-slate-100 text-slate-700 border-slate-200', label: 'Chưa có vị trí' },
}

export default function VehicleTrackingStatus({ vehicleLocation, pickupAddress, pickupLatitude, pickupLongitude, licensePlate }) {
  const unavailable = vehicleLocation?.latitude == null
    || vehicleLocation?.longitude == null
    || ['UNAVAILABLE', 'OFFLINE'].includes(vehicleLocation?.trackingState)
  const stale = !unavailable && Boolean(vehicleLocation?.isStale || vehicleLocation?.trackingState === 'STALE')
  const tone = TONE[unavailable ? 'none' : stale ? 'stale' : 'live']
  const receivedAt = vehicleLocation?.receivedAt ? new Date(vehicleLocation.receivedAt) : null

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <span className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 font-semibold ${tone.pill}`}>
          <span className={`h-2 w-2 rounded-full ${tone.dot}`} />
          {tone.label}
        </span>
        <span className="font-mono text-sm font-bold text-slate-800">{licensePlate || '—'}</span>
        <span className="text-slate-500">
          {receivedAt ? `Cập nhật ${receivedAt.toLocaleTimeString('vi-VN')}` : 'Chưa có thời điểm cập nhật'}
        </span>
      </div>

      <LiveVehicleMap
        vehicle={unavailable ? null : vehicleLocation}
        pickup={{ latitude: pickupLatitude, longitude: pickupLongitude }}
        stale={stale}
        height="340px"
      />

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
        <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full bg-blue-500" /> Vị trí xe</span>
        <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Điểm đón</span>
      </div>
      <p className="text-xs text-slate-700"><span className="font-semibold">Điểm đón:</span> {pickupAddress || '—'}</p>
      {unavailable ? <p className="text-xs text-slate-500">Chưa nhận được tọa độ từ thiết bị trên xe.</p> : null}
      {stale ? <p className="text-xs text-amber-800">Tín hiệu GPS đang trễ hoặc mất kết nối; vị trí hiển thị có thể đã cũ.</p> : null}
    </div>
  )
}
