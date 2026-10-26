import { IconX, IconLock, IconCheckCircle, IconUsers, IconShield } from '../../../components/icons/Icons'

const ROLE_LABELS = {
  RENTER: 'Khách thuê (RENTER)',
  PROVIDER: 'Chủ xe / NCC (PROVIDER)',
  DRIVER: 'Tài xế (DRIVER)',
  ADMIN: 'Quản trị viên (ADMIN)',
}

const STATUS_BADGES = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  VERIFIED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function UserDetailModal({ user, onClose, onToggleStatus, actionLoading }) {
  if (!user) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-600">
              <IconUsers className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-bold text-slate-900">
                  {user.fullName || 'Người dùng hệ thống'}
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    STATUS_BADGES[user.status] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {user.status}
                </span>
              </div>
              <p className="text-xs text-slate-500">
                User #{user.id} • Vai trò: <strong>{ROLE_LABELS[user.role] || user.role}</strong>
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <IconX className="h-5 w-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="mt-5 space-y-4 text-xs">
          <div className="grid grid-cols-1 gap-3 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2">
            <div>
              <span className="text-slate-500 block">Địa chỉ Email:</span>
              <strong className="text-slate-900 font-semibold">{user.email || '—'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Số điện thoại liên hệ:</span>
              <strong className="text-slate-900 font-semibold">{user.phone || 'Chưa cập nhật'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Trạng thái định danh eKYC:</span>
              <span className="inline-block mt-0.5 rounded-full border px-2 py-0.5 text-[10px] font-bold bg-slate-100">
                {user.ekycStatus || 'PENDING'}
              </span>
            </div>
            <div>
              <span className="text-slate-500 block">Thời điểm tạo tài khoản:</span>
              <strong className="text-slate-900">
                {user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
          </div>
        </div>

        {/* Action Controls */}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>

          {user.role !== 'ADMIN' && (
            <button
              type="button"
              disabled={actionLoading}
              onClick={() => onToggleStatus(user)}
              className={`flex items-center gap-1.5 rounded-xl px-5 py-2.5 text-xs font-bold transition-all ${
                user.status === 'LOCKED'
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100'
              }`}
            >
              {user.status === 'LOCKED' ? (
                <>
                  <IconCheckCircle className="h-4 w-4" />
                  <span>Mở khóa tài khoản</span>
                </>
              ) : (
                <>
                  <IconLock className="h-4 w-4" />
                  <span>Khóa tài khoản</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
