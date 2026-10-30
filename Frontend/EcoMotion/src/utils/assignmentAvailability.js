export const assignmentStatuses = {
  UNCERTAIN: { label: 'Chưa xác định giờ về', color: 'bg-amber-100 text-amber-800' },
  AVAILABLE: { label: 'Rảnh', color: 'bg-emerald-100 text-emerald-800' },
  ON_TRIP: { label: 'Đang chạy chuyến', color: 'bg-red-100 text-red-800' },
  SCHEDULE_CONFLICT: { label: 'Trùng lịch', color: 'bg-orange-100 text-orange-800' },
  PENDING_ASSIGNMENT: { label: 'Chờ xác nhận', color: 'bg-yellow-100 text-yellow-800' },
  OFFLINE: { label: 'Ngoại tuyến', color: 'bg-slate-100 text-slate-700' },
  UNAVAILABLE: { label: 'Tạm nghỉ', color: 'bg-slate-100 text-slate-700' },
}

export const assignmentReasons = {
  DRIVER_RETURN_UNCERTAIN: 'Chuyến hiện tại đã quá giờ hoặc thiếu dữ liệu thời gian. Cần xác nhận giờ về trước khi phân công.',
  DRIVER_ON_TRIP: 'Tài xế chưa kết thúc chuyến hiện tại.',
  DRIVER_SCHEDULE_CONFLICT: 'Đã có chuyến trong khung giờ hoặc khoảng đệm này.',
  ASSIGNMENT_PENDING: 'Khung giờ đang được giữ cho yêu cầu chờ xác nhận.',
  DRIVER_OFFLINE: 'Tài xế đang ngoại tuyến.',
  DRIVER_UNAVAILABLE: 'Tài xế đang tạm nghỉ.',
}

export function filterAssignmentDrivers(drivers, query, status) {
  const q = query.trim().toLocaleLowerCase('vi')
  return drivers.filter(d => {
    const matchesStatus = status === 'ALL' || (status === 'ELIGIBLE'
      ? d.eligible : d.availability?.availabilityStatus === status)
    return matchesStatus && [d.fullName, d.phone, d.licenseNo]
      .some(value => (value || '').toLocaleLowerCase('vi').includes(q))
  }).sort((a, b) => Number(b.eligible) - Number(a.eligible))
}
