import { IconX, IconWallet, IconCheckCircle, IconEye } from '../../../components/icons/Icons'

function formatVND(amount) {
  if (amount == null) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
}

const PAYMENT_STATUS_BADGES = {
  COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  SUCCESS: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PAID: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  FAILED: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function PaymentDetailModal({ payment, onClose }) {
  if (!payment) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
      <div className="relative max-h-[92vh] w-full max-w-xl overflow-y-auto rounded-3xl border border-slate-200 bg-white p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
              <IconWallet className="h-6 w-6" />
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xl font-bold text-slate-900">
                  Giao dịch #{payment.id}
                </h3>
                <span
                  className={`rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
                    PAYMENT_STATUS_BADGES[payment.status] || 'bg-slate-100 text-slate-700'
                  }`}
                >
                  {payment.status}
                </span>
              </div>
              <p className="mt-0.5 text-xs text-slate-500">
                Mã đơn thuê: <strong>#{payment.bookingCode || payment.bookingId}</strong>
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
          <div className="grid grid-cols-2 gap-3 rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
            <div>
              <span className="text-slate-500 block">Số tiền giao dịch:</span>
              <strong className="text-base font-extrabold text-brand-700">
                {formatVND(payment.amount)}
              </strong>
            </div>
            <div>
              <span className="text-slate-500 block">Chiều giao dịch:</span>
              <span
                className={`inline-block mt-0.5 rounded-full px-2 py-0.5 text-[10px] font-bold ${
                  payment.direction === 'INBOUND'
                    ? 'bg-blue-100 text-blue-800'
                    : 'bg-amber-100 text-amber-800'
                }`}
              >
                {payment.direction === 'INBOUND' ? 'Thu vào (INBOUND)' : 'Chi ra / Hoàn tiền (OUTBOUND)'}
              </span>
            </div>
            <div>
              <span className="text-slate-500 block">Mục đích giao dịch:</span>
              <strong className="text-slate-900">{payment.type || 'DEPOSIT'}</strong>
            </div>
            <div>
              <span className="text-slate-500 block">Phương thức:</span>
              <strong className="text-slate-900">{payment.method || 'VIETQR'}</strong>
            </div>
            {payment.transactionCode && (
              <div className="col-span-2">
                <span className="text-slate-500 block">Mã thanh toán nội bộ:</span>
                <strong className="text-brand-700 font-mono text-xs block">
                  {payment.transactionCode}
                </strong>
              </div>
            )}
            <div className="col-span-2">
              <span className="text-slate-500 block">Mã tham chiếu Gateway (Gateway Ref):</span>
              <strong className="text-slate-900 font-mono text-[11px] block break-all">
                {payment.gatewayRef || '—'}
              </strong>
            </div>
            <div>
              <span className="text-slate-500 block">Thời gian thanh toán:</span>
              <strong className="text-slate-900">
                {payment.paidAt ? new Date(payment.paidAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
            <div>
              <span className="text-slate-500 block">Thời điểm tạo lệnh:</span>
              <strong className="text-slate-900">
                {payment.createdAt ? new Date(payment.createdAt).toLocaleString('vi-VN') : '—'}
              </strong>
            </div>
          </div>

          {payment.receiptUrl && (
            <div className="rounded-2xl border border-slate-200 bg-white p-3.5 flex items-center justify-between">
              <div>
                <strong className="block text-slate-800">Biên lai điện tử:</strong>
                <span className="text-slate-500 text-[11px]">{payment.receiptUrl}</span>
              </div>
              <a
                href={payment.receiptUrl}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-1 rounded-xl bg-slate-900 px-3 py-1.5 text-xs font-bold text-white hover:bg-slate-800"
              >
                <IconEye className="h-3.5 w-3.5" />
                <span>Xem biên lai</span>
              </a>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="mt-6 flex justify-end border-t border-slate-100 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 bg-white px-5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  )
}
