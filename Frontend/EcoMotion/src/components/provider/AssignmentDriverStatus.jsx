import { assignmentStatuses, assignmentReasons, currentOperationalStatuses } from '../../utils/assignmentAvailability'

export default function AssignmentDriverStatus({ driver }) {
  const state = driver?.availability
  if (!state) return <p className="text-xs text-slate-500">Chưa có dữ liệu trạng thái.</p>

  // 1. Current operational status
  const currentKey = state.currentOperationalStatus || state.workStatus || 'AVAILABLE'
  const currentBadge = currentOperationalStatuses[currentKey] || currentOperationalStatuses.AVAILABLE

  // 2. Booking-specific availability badge
  const bookingBadge = driver.eligible
    ? { label: 'Có thể phân công', color: 'bg-emerald-100 text-emerald-800' }
    : assignmentStatuses[state.availabilityStatus] || { label: 'Không thể phân công', color: 'bg-rose-100 text-rose-800' }

  return (
    <div className="mt-2 space-y-1.5 text-xs text-slate-600">
      {/* 2 distinct status pills */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-1">
          <span className="text-[11px] font-medium text-slate-500">Hiện tại:</span>
          <span className={`inline-block rounded-full px-2 py-0.5 font-semibold text-[11px] ${currentBadge.color}`}>
            {currentBadge.label}
          </span>
        </span>

        <span className="inline-flex items-center gap-1">
          <span className="text-[11px] font-medium text-slate-500">Booking này:</span>
          <span className={`inline-block rounded-full px-2 py-0.5 font-semibold text-[11px] ${bookingBadge.color}`}>
            {bookingBadge.label}
          </span>
        </span>
      </div>
      {/* Ineligibility reason */}
      {!driver.eligible && (
        <p className="text-[11px] font-medium text-red-700">
          {assignmentReasons[state.reason] || 'T?i x? ch?a ?? ?i?u ki?n cho ??n n?y.'}
        </p>
      )}
    </div>
  )
}
