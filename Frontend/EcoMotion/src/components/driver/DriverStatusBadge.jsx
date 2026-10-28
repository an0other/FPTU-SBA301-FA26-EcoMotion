import { DRIVER_STATUS_LABELS } from '../../utils/driverStatus'
const colors = { AVAILABLE: 'bg-green-50 text-green-800', ON_TRIP: 'bg-amber-50 text-amber-800', SCHEDULED: 'bg-blue-50 text-blue-800' }
export default function DriverStatusBadge({ status }) {
  return <span className={`inline-block rounded px-2 py-1 text-xs font-semibold ${colors[status] || 'bg-slate-100 text-slate-700'}`}>{DRIVER_STATUS_LABELS[status] || 'Đang tải trạng thái'}</span>
}
