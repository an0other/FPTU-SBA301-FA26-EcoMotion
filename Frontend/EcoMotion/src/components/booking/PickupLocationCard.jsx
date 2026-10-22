import { PickupMap } from './PickupMap'

export function PickupLocationCard({ pickupAddress, pickupLatitude, pickupLongitude, driverNote }) {
  const hasPoint = pickupLatitude != null && pickupLongitude != null
  const navigationUrl = hasPoint
    ? `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(`${pickupLatitude},${pickupLongitude}`)}`
    : null
  return (
    <div className="space-y-3">
      {hasPoint ? (
        <PickupMap readOnly value={{ address: pickupAddress, latitude: pickupLatitude, longitude: pickupLongitude }} />
      ) : (
        <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Đơn cũ chưa lưu tọa độ; đang hiển thị địa chỉ dạng văn bản.</p>
      )}
      <p className="text-sm font-semibold text-[#14221A]">{pickupAddress}</p>
      {driverNote ? <p className="rounded-xl bg-[#F7F8F6] p-3 text-xs text-slate-700">Ghi chú cho tài xế: {driverNote}</p> : null}
      {navigationUrl ? <a href={navigationUrl} target="_blank" rel="noreferrer" className="inline-flex text-xs font-semibold text-[#15803D] hover:underline">Mở chỉ đường trên Google Maps →</a> : null}
    </div>
  )
}
