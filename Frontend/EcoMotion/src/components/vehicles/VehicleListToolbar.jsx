export default function VehicleListToolbar({
  total,
  offset,
  shown,
}) {
  const start = shown === 0 ? 0 : offset + 1
  const end = offset + shown

  return (
    <div className="border-b border-slate-200 bg-white py-3">
      <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-3 px-4 text-xs sm:px-6">
        <span className="font-semibold text-slate-800" aria-current="page">Trang chủ</span>

        <span className="font-medium text-slate-500">
          {total === 0
            ? 'Không có xe nào phù hợp'
            : `Hiển thị ${start}–${end} trong ${total} xe`}
        </span>
      </div>
    </div>
  )
}
