import { IconChevronRight } from '../icons/Icons'

const PLAN_PHRASES = {
  HOURLY: 'theo giờ',
  DAILY: 'theo ngày',
  MONTHLY: 'dài hạn theo tháng',
  YEARLY: 'dài hạn trọn gói theo năm',
}

export default function VehicleListToolbar({
  planType,
  location,
  keyword,
  total,
  offset,
  shown,
  onHome,
}) {
  const planPhrase = PLAN_PHRASES[planType] ?? 'theo giờ'
  const start = shown === 0 ? 0 : offset + 1
  const end = offset + shown

  return (
    <div className="border-b border-slate-200 bg-white py-3">
      <div className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-3 px-4 text-xs sm:px-6">
        <div className="flex flex-wrap items-center gap-2 text-slate-500">
          <button
            type="button"
            onClick={onHome}
            className="transition-colors hover:text-brand-600"
          >
            Trang chủ
          </button>
          <IconChevronRight className="h-3.5 w-3.5 text-slate-400" />
          <span className="font-semibold text-slate-800">
            {`Thuê xe ${planPhrase} tại ${location || 'toàn quốc'}`}
          </span>
          {keyword && (
            <span className="rounded-md bg-slate-100 px-2 py-0.5 font-medium text-slate-700">
              {`Từ khoá: "${keyword}"`}
            </span>
          )}
        </div>

        <span className="font-medium text-slate-500">
          {total === 0
            ? 'Không có xe nào phù hợp'
            : `Hiển thị ${start}–${end} trong ${total} xe`}
        </span>
      </div>
    </div>
  )
}
