import { assignmentStatuses, assignmentReasons, currentOperationalStatuses } from '../../utils/assignmentAvailability'

const time = value => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : 'Chưa xác định'

export default function AssignmentDriverStatus({ driver }) {
  const state = driver?.availability
  if (!state) return <p className="text-xs text-slate-500">Chưa có dữ liệu trạng thái.</p>

  // 1. Current operational status
  const currentKey = state.currentOperationalStatus
    || (state.currentBookingCode ? 'ON_TRIP'
      : state.workStatus === 'OFFLINE' ? 'OFFLINE'
      : state.workStatus === 'UNAVAILABLE' ? 'UNAVAILABLE'
      : state.nextBookingCode ? 'SCHEDULED' : 'AVAILABLE')
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

      {/* Details on current trip if on trip */}
      {state.currentBookingCode && (
        <p className="text-[11px] text-blue-700">
          Đang chạy: <strong className="font-mono">{state.currentBookingCode}</strong> (Bắt đầu: {time(state.currentTripStartedAt)})
        </p>
      )}

      {/* Conflicting slot if schedule conflict */}
      {state.conflictingBookingCode && (
        <p className="text-[11px] text-orange-800">
          Trùng lịch: <strong className="font-mono">{state.conflictingBookingCode}</strong> ({time(state.conflictStart)} – {time(state.conflictEnd)})
        </p>
      )}

      {/* Buffer conflict details */}
      {state.availabilityStatus === 'BUFFER_CONFLICT' && state.conflictEnd && (
        <p className="text-[11px] text-amber-800">
          Chuyến trước kết thúc lúc {time(state.conflictEnd)}, không đủ khoảng đệm bảo dưỡng/di chuyển.
        </p>
      )}

      {/* Next booking info if scheduled */}
      {state.nextBookingCode && !state.conflictingBookingCode && (
        <p className="text-[11px] text-slate-500">
          Chuyến tiếp theo: <span className="font-mono">{state.nextBookingCode}</span> lúc {time(state.nextPickupAt)}
        </p>
      )}

      {/* Ineligibility reason */}
      {!driver.eligible && (
        <p className="text-[11px] font-medium text-red-700">
          {assignmentReasons[state.reason] || driver.ineligibilityReason || state.reason}
        </p>
      )}

      {/* Expected available at */}
      {state.nextAvailableAt && (
        <p className="text-[11px] text-slate-500">
          Dự kiến sẵn sàng sau: {time(state.nextAvailableAt)}
        </p>
      )}
    </div>
  )
}
