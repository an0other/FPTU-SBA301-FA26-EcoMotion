import RoadRouteMap from './RoadRouteMap'

export default function LiveMapViewer({ vehicleLocation, pickupLocation, vehicleInfo, height = '420px', interactive = true }) {
  const unavailable = vehicleLocation?.latitude == null || vehicleLocation?.longitude == null || ['UNAVAILABLE', 'OFFLINE'].includes(vehicleLocation?.trackingState)
  const stale = vehicleLocation?.isStale || vehicleLocation?.trackingState === 'STALE'

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-wrap justify-between gap-2 text-xs">
        <strong>{unavailable ? 'Chưa có vị trí' : stale ? 'Tín hiệu GPS bị trễ / Mất kết nối' : 'Đang theo dõi trực tiếp'}</strong>
        <span>{vehicleInfo?.licensePlate || ''}</span>
        {vehicleLocation?.receivedAt && <span>Cập nhật: {new Date(vehicleLocation.receivedAt).toLocaleTimeString('vi-VN')}</span>}
      </div>
      <RoadRouteMap from={unavailable || stale ? null : vehicleLocation} to={pickupLocation} height={height} interactive={interactive} />
      {pickupLocation?.address && <p className="text-xs text-slate-700">Điểm đón: {pickupLocation.address}</p>}
    </div>
  )
}
