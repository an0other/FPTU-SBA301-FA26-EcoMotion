import { IconUser, IconPhone, IconMail, IconCheckCircle, IconAlertTriangle } from '../icons/Icons'

export default function CustomerInfoCard({ renter }) {
  if (!renter) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2 mb-3">
          <IconUser className="h-5 w-5 text-emerald-600" />
          Thông tin khách hàng
        </h3>
        <p className="text-sm text-slate-500">Chưa có thông tin khách hàng.</p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconUser className="h-5 w-5 text-emerald-600" />
          Thông tin khách hàng
        </h3>
        {renter.licenseVerified ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
            <IconCheckCircle className="h-3.5 w-3.5" />
            Đã xác thực GPLX
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
            <IconAlertTriangle className="h-3.5 w-3.5" />
            Chưa xác thực GPLX
          </span>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <span className="text-xs font-medium text-slate-400">Họ và tên khách hàng</span>
          <p className="text-sm font-bold text-slate-900">{renter.fullName || 'Ẩn danh'}</p>
        </div>

        <div>
          <span className="text-xs font-medium text-slate-400">Số điện thoại liên hệ</span>
          <p className="text-sm font-semibold text-slate-800 flex items-center gap-1.5">
            <IconPhone className="h-4 w-4 text-slate-400" />
            {renter.phone ? (
              <a href={`tel:${renter.phone}`} className="text-emerald-600 hover:underline">
                {renter.phone}
              </a>
            ) : (
              'Chưa cập nhật'
            )}
          </p>
        </div>

        <div className="sm:col-span-2">
          <span className="text-xs font-medium text-slate-400">Email</span>
          <p className="text-sm font-medium text-slate-700 flex items-center gap-1.5">
            <IconMail className="h-4 w-4 text-slate-400" />
            {renter.email ? (
              <a href={`mailto:${renter.email}`} className="text-slate-700 hover:text-emerald-600 hover:underline">
                {renter.email}
              </a>
            ) : (
              'Chưa cập nhật'
            )}
          </p>
        </div>
      </div>
    </div>
  )
}
