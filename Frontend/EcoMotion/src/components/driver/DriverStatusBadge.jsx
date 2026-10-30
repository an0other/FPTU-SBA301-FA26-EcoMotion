import { DRIVER_STATUS_LABELS } from '../../utils/driverStatus'

const BADGE_STYLES = {
  AVAILABLE: {
    container: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    dot: 'bg-emerald-500',
  },
  ON_TRIP: {
    container: 'bg-emerald-100 text-emerald-900 border-emerald-300 font-bold',
    dot: 'bg-emerald-600 animate-pulse',
  },
  SCHEDULED: {
    container: 'bg-blue-50 text-blue-800 border-blue-200',
    dot: 'bg-blue-500',
  },
  UNAVAILABLE: {
    container: 'bg-slate-100 text-slate-700 border-slate-200',
    dot: 'bg-slate-400',
  },
  OFFLINE: {
    container: 'bg-slate-100 text-slate-500 border-slate-200',
    dot: 'bg-slate-300',
  },
}

export default function DriverStatusBadge({ status }) {
  const meta = BADGE_STYLES[status] || {
    container: 'bg-slate-100 text-slate-700 border-slate-200',
    dot: 'bg-slate-400',
  }

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${meta.container}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} aria-hidden="true" />
      <span>{DRIVER_STATUS_LABELS[status] || 'Đang tải trạng thái'}</span>
    </span>
  )
}
