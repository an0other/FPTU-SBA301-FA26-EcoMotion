import { IconClock, IconCheckCircle, IconAlertTriangle } from '../icons/Icons'

const EVENT_CONFIG = {
  BOOKING_CREATED: {
    color: 'bg-blue-500 text-white',
    ring: 'ring-blue-100',
    title: 'Khách tạo đơn',
  },
  BOOKING_ACCEPTED: {
    color: 'bg-emerald-500 text-white',
    ring: 'ring-emerald-100',
    title: 'Nhà xe xác nhận',
  },
  DRIVER_ASSIGNED: {
    color: 'bg-indigo-500 text-white',
    ring: 'ring-indigo-100',
    title: 'Gán tài xế',
  },
  TRIP_STARTED: {
    color: 'bg-emerald-600 text-white',
    ring: 'ring-emerald-100',
    title: 'Bắt đầu hành trình',
  },
  TRIP_COMPLETED: {
    color: 'bg-slate-700 text-white',
    ring: 'ring-slate-100',
    title: 'Hoàn thành chuyến đi',
  },
  BOOKING_REJECTED: {
    color: 'bg-red-500 text-white',
    ring: 'ring-red-100',
    title: 'Nhà xe từ chối',
  },
  BOOKING_CANCELLED: {
    color: 'bg-rose-500 text-white',
    ring: 'ring-rose-100',
    title: 'Hủy đơn đặt xe',
  },
  CHARGE_ADDED: {
    color: 'bg-amber-500 text-white',
    ring: 'ring-amber-100',
    title: 'Phụ thu phát sinh',
  },
}

export default function BookingTimelineCard({ timeline, formatDateTime }) {
  if (!timeline || timeline.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2 mb-3">
          <IconClock className="h-5 w-5 text-emerald-600" />
          Dòng thời gian hoạt động
        </h3>
        <p className="text-sm text-slate-500">Chưa có lịch sử hoạt động ghi nhận.</p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconClock className="h-5 w-5 text-emerald-600" />
          Dòng thời gian hoạt động (Timeline)
        </h3>
        <span className="text-xs text-slate-400">{timeline.length} sự kiện</span>
      </div>

      <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200">
        {timeline.map((event, idx) => {
          const config = EVENT_CONFIG[event.eventType] || {
            color: 'bg-slate-500 text-white',
            ring: 'ring-slate-100',
            title: event.eventType,
          }

          return (
            <div key={idx} className="relative group">
              {/* Dot */}
              <div
                className={`absolute -left-6 top-0.5 flex h-5 w-5 items-center justify-center rounded-full ring-4 ${config.ring} ${config.color}`}
              >
                {event.eventType === 'BOOKING_REJECTED' || event.eventType === 'BOOKING_CANCELLED' ? (
                  <IconAlertTriangle className="h-3 w-3" />
                ) : (
                  <IconCheckCircle className="h-3 w-3" />
                )}
              </div>

              {/* Event content */}
              <div>
                <div className="flex items-center justify-between gap-2">
                  <h4 className="text-xs font-bold text-slate-900">
                    {config.title || event.eventType}
                  </h4>
                  <span className="text-[11px] font-medium text-slate-400">
                    {formatDateTime(event.timestamp)}
                  </span>
                </div>
                <p className="mt-0.5 text-xs text-slate-600">{event.description}</p>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
