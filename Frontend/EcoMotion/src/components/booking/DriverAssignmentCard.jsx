import { IconUser, IconPhone, IconStar, IconIdCard, IconAlertTriangle } from '../icons/Icons'

export default function DriverAssignmentCard({
  assignedDriver,
  planType,
  bookingStatus,
  onOpenAssignDriverModal,
}) {
  const isSelfDrive = planType === 'YEARLY'
  const canAssignDriver = !isSelfDrive && ['PENDING', 'CONFIRMED'].includes(bookingStatus)

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconUser className="h-5 w-5 text-indigo-600" />
          Tài xế phục vụ
        </h3>

        {/* Action Button */}
        {canAssignDriver && (
          <button
            type="button"
            onClick={onOpenAssignDriverModal}
            className="rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700 transition hover:bg-indigo-100 hover:border-indigo-300"
          >
            {assignedDriver ? 'Đổi tài xế' : '+ Gán tài xế'}
          </button>
        )}
      </div>

      {isSelfDrive ? (
        <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 text-xs text-slate-600 flex items-center gap-2.5">
          <IconAlertTriangle className="h-4 w-4 text-slate-400 shrink-0" />
          <span>
            Đơn hàng thuộc gói thuê dài hạn tự lái (Rent-to-Own). Khách hàng trực tiếp lái xe, không yêu cầu chỉ định tài xế.
          </span>
        </div>
      ) : assignedDriver ? (
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-indigo-100 text-indigo-700 font-bold text-base">
              {assignedDriver.fullName ? assignedDriver.fullName.charAt(0).toUpperCase() : 'TX'}
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-900">{assignedDriver.fullName}</h4>
              <p className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                <IconPhone className="h-3.5 w-3.5" />
                <a href={`tel:${assignedDriver.phone}`} className="text-indigo-600 hover:underline">
                  {assignedDriver.phone || 'Chưa cập nhật SĐT'}
                </a>
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="inline-flex items-center gap-1 rounded bg-slate-100 px-2.5 py-1 text-slate-700 font-medium">
              <IconIdCard className="h-3.5 w-3.5 text-slate-500" />
              {assignedDriver.licenseClass} - {assignedDriver.licenseNo}
            </span>
            <span className="inline-flex items-center gap-1 rounded bg-amber-50 text-amber-700 border border-amber-200 px-2.5 py-1 font-bold">
              <IconStar className="h-3.5 w-3.5 text-amber-500" />
              {assignedDriver.ratingAvg ? Number(assignedDriver.ratingAvg).toFixed(1) : '5.0'}
            </span>
          </div>
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-amber-300 bg-amber-50/50 p-4 text-center">
          <p className="text-xs font-medium text-amber-800">
            Chưa có tài xế nào được chỉ định cho chuyến đi này.
          </p>
          {canAssignDriver ? (
            <button
              type="button"
              onClick={onOpenAssignDriverModal}
              className="mt-2.5 inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-1.5 text-xs font-semibold text-white transition hover:bg-indigo-700 shadow-xs"
            >
              Chọn tài xế ngay
            </button>
          ) : (
            <p className="mt-1 text-[11px] text-amber-600">
              Trạng thái đơn hiện tại không cho phép thay đổi tài xế.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
