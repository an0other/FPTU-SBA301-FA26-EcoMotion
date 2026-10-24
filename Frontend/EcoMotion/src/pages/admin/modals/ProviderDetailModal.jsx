import { useState } from 'react'
import { IconX, IconShield, IconCheckCircle, IconLock, IconAlertTriangle } from '../../../components/icons/Icons'

const STATUS_BADGES = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function ProviderDetailModal({ provider, onClose, onStatusUpdate, actionLoading }) {
  if (!provider) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
                <IconShield className="h-5 w-5" />
              </span>
              <div>
                <h3 className="text-lg font-bold text-slate-900">
                  {provider.displayName || 'Hồ sơ Nhà cung cấp'}
                </h3>
                <p className="text-xs text-slate-500">
                  Mã đối tác: #{provider.id} • Loại hình: {provider.providerType === 'COMPANY' ? 'Doanh nghiệp' : 'Hộ kinh doanh / Cá nhân'}
                </p>
              </div>
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

        {/* Content Body */}
        <div className="mt-5 space-y-5 text-xs">
          {/* Section 1: Legal & Business */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>🏛️</span> Thông tin pháp lý & Doanh nghiệp
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Tên doanh nghiệp / Đơn vị:</span>
                <strong className="text-slate-900 text-sm">{provider.displayName || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Mã số thuế (MST):</span>
                <strong className="text-slate-900 font-mono">{provider.taxCode || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số Giấy phép KD (GPKD):</span>
                <strong className="text-slate-900 font-mono">{provider.businessLicenseNo || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Đánh giá trung bình:</span>
                <strong className="text-slate-900">
                  ★ {provider.ratingAvg || '5.0'} ({provider.ratingCount || 0} đánh giá)
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Ngày đăng ký tham gia:</span>
                <strong className="text-slate-900">
                  {provider.createdAt ? new Date(provider.createdAt).toLocaleString('vi-VN') : '—'}
                </strong>
              </div>
            </div>
          </div>

          {/* Section 2: Contact & Depot */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>📍</span> Người đại diện & Trụ sở Bãi xe (Depot)
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Họ và tên người đại diện:</span>
                <strong className="text-slate-900">{provider.userFullName || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Email tài khoản:</span>
                <strong className="text-slate-900">{provider.userEmail || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số điện thoại liên hệ:</span>
                <strong className="text-slate-900">{provider.userPhone || '—'}</strong>
              </div>
              <div className="sm:col-span-2">
                <span className="text-slate-500 block">Hotline hỗ trợ 24/7:</span>
                <strong className="text-brand-700 font-bold">{provider.hotline || 'Chưa cung cấp'}</strong>
              </div>
              <div className="sm:col-span-3">
                <span className="text-slate-500 block">Địa chỉ bãi đỗ / Trụ sở đón khách (Depot Address):</span>
                <p className="mt-1 rounded-xl border border-slate-200 bg-white p-2.5 font-medium text-slate-800">
                  {provider.depotAddress || 'Chưa cung cấp'}
                </p>
              </div>
            </div>
          </div>

          {/* Section 3: Financial & Banking */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50/60 p-4">
            <h4 className="font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
              <span>💳</span> Tài khoản thanh toán & Chính sách tài chính
            </h4>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <div>
                <span className="text-slate-500 block">Ngân hàng thụ hưởng:</span>
                <strong className="text-slate-900">{provider.bankName || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Số tài khoản:</span>
                <strong className="text-slate-900 font-mono text-sm">{provider.bankAccountNo || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Chủ tài khoản:</span>
                <strong className="text-slate-900 uppercase">{provider.bankAccountHolder || '—'}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Tỷ lệ hoa hồng sàn:</span>
                <strong className="text-brand-700 font-bold">
                  {provider.commissionRate != null ? `${provider.commissionRate}%` : 'Mặc định (10%)'}
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Tỷ lệ tiền cọc tối thiểu:</span>
                <strong className="text-slate-900 font-bold">
                  {provider.depositRatio != null ? `${provider.depositRatio}%` : 'Mặc định (30%)'}
                </strong>
              </div>
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

          <div className="flex items-center gap-2">
            {provider.status !== 'ACTIVE' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(provider.id, 'ACTIVE')}
                className="flex items-center gap-1.5 rounded-xl bg-emerald-600 px-5 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50"
              >
                <IconCheckCircle className="h-4 w-4" />
                <span>{provider.status === 'PENDING' ? 'Phê duyệt & Kích hoạt đối tác' : 'Mở khóa đối tác'}</span>
              </button>
            )}

            {provider.status === 'ACTIVE' && (
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => onStatusUpdate(provider.id, 'LOCKED')}
                className="flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-xs font-bold text-rose-700 hover:bg-rose-100 disabled:opacity-50"
              >
                <IconLock className="h-4 w-4" />
                <span>Tạm khóa nhà cung cấp</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
