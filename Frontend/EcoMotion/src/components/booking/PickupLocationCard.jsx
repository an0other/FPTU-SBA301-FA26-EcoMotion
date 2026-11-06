import { PickupMap } from './PickupMap'
import { formatCurrency } from '../../utils/formatCurrency'

export function PickupDistanceProof({
  pickupAddress,
  pickupLatitude,
  pickupLongitude,
  originLatitude,
  originLongitude,
  distanceKm,
  freeRadiusKm,
  chargeableKm,
  outOfRadiusRate,
  pickupFee,
}) {
  const hasPickup = pickupLatitude != null && pickupLongitude != null
  const hasOrigin = originLatitude != null && originLongitude != null
  const hasFeeRecord = pickupFee != null || distanceKm != null || hasOrigin
  if (!hasPickup && !hasFeeRecord) return null

  return (
    <div className="space-y-3">
      {hasPickup ? (
        <PickupMap
          readOnly
          value={{ address: pickupAddress, latitude: pickupLatitude, longitude: pickupLongitude }}
          origin={hasOrigin ? { latitude: originLatitude, longitude: originLongitude } : null}
        />
      ) : (
        <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Đơn cũ chưa lưu tọa độ; đang hiển thị địa chỉ dạng văn bản.</p>
      )}
      {hasOrigin ? (
        <p className="text-xs text-[#3D4A42]">
          <span className="mr-3 inline-flex items-center gap-1"><span className="inline-block h-2.5 w-2.5 rounded-full bg-[#1d4ed8]" /> Điểm xuất phát đã lưu</span>
          <span className="inline-flex items-center gap-1"><span className="inline-block h-2.5 w-2.5 rounded-full bg-[#15803d]" /> Điểm đón</span>
        </p>
      ) : hasFeeRecord ? (
        <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Đơn này chưa lưu điểm xuất phát, nên không vẽ được khoảng cách đã dùng để tính phí đón.</p>
      ) : null}
      {distanceKm != null && freeRadiusKm != null ? (
        <div className="rounded-xl border border-[#E6EBE8] bg-[#F7F8F6] p-3 text-sm text-[#14221A]">
          <p className="font-semibold">Phí đón đã chốt lúc đặt</p>
          <p className="mt-1 text-[#3D4A42]">
            Khoảng cách đã lưu khi đặt từ điểm xuất phát đến điểm đón: {Number(distanceKm).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} km
            {' · '}Miễn phí {freeRadiusKm} km
          </p>
          <p className="mt-2">
            {Number(chargeableKm) > 0
              ? `${chargeableKm} km vượt (làm tròn lên) × ${formatCurrency(outOfRadiusRate)}/km = `
              : 'Điểm đón trong bán kính miễn phí: '}
            <strong>{formatCurrency(pickupFee ?? 0)}</strong>
          </p>
          <p className="mt-1 text-xs text-[#3D4A42]">Số liệu lấy từ bản ghi lúc tạo đơn, không tính lại. Mỗi km vượt được làm tròn lên và chỉ tính một lần.</p>
        </div>
      ) : null}
    </div>
  )
}

export function PickupLocationCard({
  pickupAddress,
  pickupLatitude,
  pickupLongitude,
  driverNote,
  originLatitude,
  originLongitude,
  distanceKm,
  freeRadiusKm,
  chargeableKm,
  outOfRadiusRate,
  pickupFee,
}) {
  return (
    <div className="space-y-3">
      <PickupDistanceProof
        pickupAddress={pickupAddress}
        pickupLatitude={pickupLatitude}
        pickupLongitude={pickupLongitude}
        originLatitude={originLatitude}
        originLongitude={originLongitude}
        distanceKm={distanceKm}
        freeRadiusKm={freeRadiusKm}
        chargeableKm={chargeableKm}
        outOfRadiusRate={outOfRadiusRate}
        pickupFee={pickupFee}
      />
      <p className="text-sm font-semibold text-[#14221A]">{pickupAddress}</p>
      {driverNote ? <p className="rounded-xl bg-[#F7F8F6] p-3 text-xs text-slate-700">Ghi chú cho tài xế: {driverNote}</p> : null}

    </div>
  )
}
