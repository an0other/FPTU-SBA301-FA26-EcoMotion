import { IconX, IconShield, IconCheckCircle, IconLock } from '../../../components/icons/Icons'
import AdminModalShell from './AdminModalShell'
import { formatAdminRating } from '../../../utils/adminRating'

const STATUS_BADGES = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  APPROVED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
  REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function ProviderDetailModal({
  provider,
  onClose,
  onStatusUpdate,
  actionLoading,
  onSelectUser,
}) {
  if (!provider) return null

  const providerRating = formatAdminRating(provider.ratingAvg, provider.ratingCount, 'đánh giá')

  return (
    <AdminModalShell
      maxWidth="max-w-5xl"
      header={(
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
              <IconShield className="h-5 w-5" />
            </span>
            <div>
              <h3 className="text-lg font-bold text-slate-900">
                {provider.displayName || 'Hồ sơ Nhà cung cấp'}
              </h3>
              <p className="text-xs text-slate-500">
                Mã đối tác: #{provider.id} • Loại hình: {provider.providerType === 'BUSINESS' || provider.providerType === 'COMPANY' ? 'Doanh nghiệp' : 'Hộ kinh doanh / Cá nhân'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className={`rounded-full border px-3 py-1 text-xs font-bold tracking-wider ${
                STATUS_BADGES[provider.status] || 'bg-slate-100 text-slate-700'
              }`}
            >
              {provider.status}
            </span>
            <button
              type="button"
              onClick={onClose}
              className="rounded-full p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            >
              <IconX className="h-5 w-5" />
            </button>
          </div>
        </div>
      )}
      footer={(
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>

          <div className="flex items-center gap-2">
            {provider.status !== 'APPROVED' && provider.status !== 'ACTIVE' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(provider.id, 'APPROVED')}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>{provider.status === 'PENDING' ? 'Phê duyệt & Kích hoạt đối tác' : 'Mở khóa đối tác'}</span>
              </button>
            )}

            {provider.status !== 'LOCKED' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(provider.id, 'LOCKED')}
                className="flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                <IconLock className="h-4 w-4" />
                <span>{provider.status === 'PENDING' ? 'Từ chối / Khóa đối tác' : 'Tạm khóa nhà cung cấp'}</span>
              </button>
            )}
          </div>
        </div>
      )}
    >
      <div className="space-y-3 text-xs">
        {/* Top Row: Legal & Banking Side-by-Side */}
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {/* Section 1: Legal & Business */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
            <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span>🏛️</span> Thông tin pháp lý &amp; Doanh nghiệp
            </h4>
            <div className="grid grid-cols-2 gap-2.5">
              <div className="col-span-2">
                <span className="block text-slate-500">Tên doanh nghiệp / Đơn vị:</span>
                <strong className="text-sm text-slate-900">{provider.displayName || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Mã số thuế (MST):</span>
                <strong className="font-mono text-slate-900">{provider.taxCode || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Số Giấy phép KD (GPKD):</span>
                <strong className="font-mono text-slate-900">{provider.businessLicenseNo || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Đánh giá trung bình:</span>
                <strong className={providerRating ? 'text-slate-900' : 'text-slate-500'}>
                  {providerRating || 'Chưa có'}
                </strong>
              </div>
              <div>
                <span className="block text-slate-500">Ngày đăng ký tham gia:</span>
                <strong className="text-slate-900">
                  {provider.createdAt ? new Date(provider.createdAt).toLocaleString('vi-VN') : '—'}
                </strong>
              </div>
            </div>
          </div>

          {/* Section 3: Financial & Banking */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
            <h4 className="mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span>💳</span> Tài khoản thanh toán &amp; Chính sách tài chính
            </h4>
            <div className="grid grid-cols-2 gap-2.5">
              <div>
                <span className="block text-slate-500">Ngân hàng thụ hưởng:</span>
                <strong className="text-slate-900">{provider.bankName || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Số tài khoản:</span>
                <strong className="font-mono text-sm text-slate-900">{provider.bankAccountNo || '—'}</strong>
              </div>
              <div className="col-span-2">
                <span className="block text-slate-500">Chủ tài khoản:</span>
                <strong className="uppercase text-slate-900">{provider.bankAccountHolder || '—'}</strong>
              </div>
              <div>
                <span className="block text-slate-500">Tỷ lệ hoa hồng sàn:</span>
                <strong className="font-bold text-brand-700">
                  {provider.commissionRate != null ? `${Number(provider.commissionRate) * 100}%` : 'Mặc định (10%)'}
                </strong>
              </div>
              <div>
                <span className="block text-slate-500">Tỷ lệ tiền cọc tối thiểu:</span>
                <strong className="font-bold text-slate-900">
                  {provider.depositRatio != null ? `${Number(provider.depositRatio) * 100}%` : 'Mặc định (30%)'}
                </strong>
              </div>
            </div>
          </div>
        </div>

        {/* Section 2: Contact & Depot */}
        <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-3">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
            <h4 className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span>📍</span> Người đại diện &amp; Trụ sở Bãi xe (Depot)
            </h4>
            {provider.userId && onSelectUser && (
              <button
                type="button"
                onClick={() => onSelectUser(provider.userId)}
                className="text-[11px] font-bold text-brand-600 hover:underline"
              >
                Xem tài khoản người đại diện (User #{provider.userId}) →
              </button>
            )}
          </div>
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
            <div>
              <span className="block text-slate-500">Người đại diện:</span>
              {provider.userId && onSelectUser ? (
                <button
                  type="button"
                  onClick={() => onSelectUser(provider.userId)}
                  className="text-left font-bold text-brand-700 hover:underline"
                >
                  {provider.userFullName || `User #${provider.userId}`} →
                </button>
              ) : (
                <strong className="text-slate-900">{provider.userFullName || '—'}</strong>
              )}
            </div>
            <div>
              <span className="block text-slate-500">Email tài khoản:</span>
              <strong className="break-all text-slate-900">{provider.userEmail || '—'}</strong>
            </div>
            <div>
              <span className="block text-slate-500">Số điện thoại liên hệ:</span>
              <strong className="text-slate-900">{provider.userPhone || '—'}</strong>
            </div>
            <div>
              <span className="block text-slate-500">Hotline hỗ trợ 24/7:</span>
              <strong className="font-bold text-brand-700">{provider.hotline || 'Chưa cung cấp'}</strong>
            </div>
            <div className="col-span-2 sm:col-span-4">
              <span className="block text-slate-500">Địa chỉ bãi đỗ / Trụ sở đón khách (Depot Address):</span>
              <p className="mt-1 rounded-xl border border-slate-200 bg-white px-2.5 py-2 font-medium text-slate-800">
                {provider.depotAddress || 'Chưa cung cấp'}
              </p>
            </div>
          </div>
        </div>
      </div>
    </AdminModalShell>
  )
}
