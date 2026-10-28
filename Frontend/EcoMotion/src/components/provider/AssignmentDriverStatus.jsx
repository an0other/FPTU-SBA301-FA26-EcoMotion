import { assignmentStatuses, assignmentReasons } from '../../utils/assignmentAvailability'

const time = value => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : 'Chưa xác định'

export default function AssignmentDriverStatus({ driver }) {
  const state = driver.availability
  const badge = assignmentStatuses[state?.availabilityStatus]
  if (!state) return <p className="text-xs text-slate-500">Chưa có dữ liệu trạng thái.</p>
  return (
    <div className="mt-2 space-y-1 text-xs text-slate-600">
      <span className={`inline-block rounded-full px-2 py-1 font-semibold ${badge?.color || 'bg-slate-100'}`}>
        {badge?.label || 'Chưa xác định'}
      </span>
      <p>{driver.eligible ? 'Có thể phân công trong khung giờ đang chọn.' : 'Không thể phân công trong khung giờ đang chọn.'}</p>
      <p>Sẵn sàng: {state.workStatus === 'AVAILABLE' ? 'Đang nhận chuyến' : state.workStatus === 'OFFLINE' ? 'Ngoại tuyến' : 'Tạm nghỉ'}</p>
      {state.currentBookingCode ? <p>Đang chạy: <strong>{state.currentBookingCode}</strong> · Bắt đầu: {time(state.currentTripStartedAt)}</p> : <p>Không có chuyến đang chạy.</p>}
      {state.conflictingBookingCode && <p>Lịch bị chiếm: {state.conflictingBookingCode} · {time(state.conflictStart)} – {time(state.conflictEnd)}</p>}
      {state.nextBookingCode && <p>Chuyến tiếp theo: {state.nextBookingCode} · {time(state.nextPickupAt)}</p>}
      {!driver.eligible && <p className="text-red-700">{assignmentReasons[state.reason] || driver.ineligibilityReason || state.reason}</p>}
      {state.nextAvailableAt && <p>Hết lịch và khoảng đệm: {time(state.nextAvailableAt)}</p>}
    </div>
  )
}
