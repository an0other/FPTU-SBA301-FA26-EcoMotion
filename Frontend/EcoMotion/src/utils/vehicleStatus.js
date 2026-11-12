/**
 * Valid filter tabs for Admin A03 Vehicle Approval matching backend `VehicleStatus`.
 */
export const VEHICLE_STATUS_FILTERS = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'HIDDEN']

export const VEHICLE_STATUS_LABELS = {
  ALL: 'Tất cả',
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  HIDDEN: 'Tạm ẩn',
}

export const VEHICLE_STATUS_TITLES = {
  HIDDEN: 'Xe đang được nhà xe tạm ẩn khỏi danh sách mở bán',
}

/**
 * Shared Tailwind badge styles for backend `VehicleStatus` and `DocumentStatus` values.
 */
export const VEHICLE_STATUS_BADGES = {
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  VERIFIED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
  EXPIRED: 'bg-rose-50 text-rose-700 border-rose-200',
  HIDDEN: 'bg-slate-100 text-slate-700 border-slate-300',
}

