export default function YearlyVehicleCard({ vehicle, title }) {
  if (!vehicle) return <p className="text-sm text-slate-500">{title}: chưa tải được thông tin xe.</p>
  const transmission = { AUTOMATIC: 'Tự động', MANUAL: 'Số sàn' }[vehicle.transmission] || vehicle.transmission || 'Chưa ghi nhận'
  const fuel = { ELECTRIC: 'Điện', GASOLINE: 'Xăng', DIESEL: 'Dầu', HYBRID: 'Hybrid' }[vehicle.fuelType] || vehicle.fuelType || 'Chưa ghi nhận'
  return <section className="space-y-3 rounded-xl border bg-white p-4">
    <h3 className="font-bold">{title}</h3>
    {vehicle.imageUrl && <img src={vehicle.imageUrl} alt={vehicle.name} className="h-40 w-full rounded-lg object-cover" />}
    <p className="font-semibold">{vehicle.name}</p>
    <dl className="grid grid-cols-2 gap-2 text-sm">
      <div><dt className="text-slate-500">Biển số</dt><dd>{vehicle.licensePlate}</dd></div>
      <div><dt className="text-slate-500">Năm sản xuất</dt><dd>{vehicle.manufactureYear ?? 'Chưa ghi nhận'}</dd></div>
      <div><dt className="text-slate-500">Số chỗ</dt><dd>{vehicle.seatCount ?? 'Chưa ghi nhận'}</dd></div>
      <div><dt className="text-slate-500">Hộp số / nhiên liệu</dt><dd>{transmission} / {fuel}</dd></div>
    </dl>
  </section>
}
