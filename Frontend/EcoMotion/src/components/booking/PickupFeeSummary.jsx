import { formatCurrency } from '../../utils/formatCurrency'

export function PickupFeeSummary({ quote }) {
  if (!quote) return null
  return (
    <div className="rounded-xl border border-[#E6EBE8] bg-[#F7F8F6] p-4 text-sm text-[#14221A]">
      <p className="font-semibold">Phí đón ngoài bán kính miễn phí</p>
      <p className="mt-1 text-[#3D4A42]">
        Từ bãi xe đến điểm đón: {Number(quote.distanceKm).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} km
        {' · '}Miễn phí {quote.freeRadiusKm} km
      </p>
      <p className="mt-2">
        {quote.chargeableKm > 0
          ? `${quote.chargeableKm} km vượt (làm tròn lên) × ${formatCurrency(quote.outOfRadiusRate)}/km = `
          : 'Điểm đón trong bán kính miễn phí: '}
        <strong>{formatCurrency(quote.pickupFee)}</strong>
      </p>
      <p className="mt-1 text-xs text-[#3D4A42]">Khoảng cách tính theo đường chim bay, phí tính một lần cho đơn thuê.</p>
    </div>
  )
}
