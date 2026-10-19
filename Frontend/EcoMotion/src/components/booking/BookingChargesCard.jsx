import { IconWallet, IconPlus, IconTrash } from '../icons/Icons'

const CHARGE_TYPE_LABELS = {
  BASE_RENTAL: 'Giá thuê cơ bản',
  DEPOSIT: 'Tiền đặt cọc',
  OVERTIME: 'Phí quá giờ',
  WAITING: 'Phí chờ đợi',
  WEEKEND: 'Phụ phí cuối tuần',
  OUT_OF_RADIUS: 'Phụ phí ngoài bán kính',
}

const CHARGE_BADGE_COLORS = {
  BASE_RENTAL: 'bg-slate-100 text-slate-700 border-slate-200',
  DEPOSIT: 'bg-purple-50 text-purple-700 border-purple-200',
  OVERTIME: 'bg-amber-50 text-amber-700 border-amber-200',
  WAITING: 'bg-blue-50 text-blue-700 border-blue-200',
  WEEKEND: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  OUT_OF_RADIUS: 'bg-rose-50 text-rose-700 border-rose-200',
}

export default function BookingChargesCard({
  financials,
  bookingStatus,
  formatCurrency,
  formatDateTime,
  onOpenAddChargeModal,
  onDeleteCharge,
  deletingChargeId,
}) {
  const canAddCharge = ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(bookingStatus)
  const isPaid = financials?.payments?.some((p) => p.status === 'PAID')

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <IconWallet className="h-5 w-5 text-emerald-600" />
          Chi phí & Phụ thu
        </h3>

        {canAddCharge && (
          <button
            type="button"
            onClick={onOpenAddChargeModal}
            className="inline-flex items-center gap-1 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 transition hover:bg-emerald-100 hover:border-emerald-300"
          >
            <IconPlus className="h-3.5 w-3.5" />
            Thêm phụ phí
          </button>
        )}
      </div>

      {/* Itemized Charges List */}
      <div className="space-y-3">
        <div className="flex justify-between items-center text-sm py-1 border-b border-slate-100">
          <span className="text-slate-600 font-medium">Tiền thuê cơ bản</span>
          <span className="font-semibold text-slate-900">
            {formatCurrency(financials?.baseRentalAmount ?? 0)}
          </span>
        </div>

        {financials?.itemizedCharges && financials.itemizedCharges.length > 0 && (
          <div className="pt-1 space-y-2.5">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Các khoản phụ phí đã tạo
            </span>

            {financials.itemizedCharges.map((charge) => {
              const isBase = charge.chargeType === 'BASE_RENTAL'
              const canDelete = !isBase && !isPaid && canAddCharge

              return (
                <div
                  key={charge.id}
                  className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 bg-slate-50/70 p-3 text-xs"
                >
                  <div className="flex-1 space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span
                        className={`inline-block rounded border px-2 py-0.5 text-[10px] font-bold ${
                          CHARGE_BADGE_COLORS[charge.chargeType] || 'bg-slate-100 text-slate-700 border-slate-200'
                        }`}
                      >
                        {CHARGE_TYPE_LABELS[charge.chargeType] || charge.chargeType}
                      </span>
                      {charge.createdAt && (
                        <span className="text-[11px] text-slate-400">
                          {formatDateTime(charge.createdAt)}
                        </span>
                      )}
                    </div>
                    {charge.description && (
                      <p className="text-slate-600 font-medium">{charge.description}</p>
                    )}
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    <span className="font-bold text-slate-900 text-sm">
                      +{formatCurrency(charge.amount)}
                    </span>

                    {canDelete && (
                      <button
                        type="button"
                        title="Xóa phụ phí này"
                        disabled={deletingChargeId === charge.id}
                        onClick={() => onDeleteCharge(charge.id)}
                        className="rounded p-1 text-slate-400 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                      >
                        <IconTrash className="h-4 w-4" />
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Final Total Summary */}
        <div className="mt-4 rounded-xl bg-gradient-to-br from-emerald-500/10 via-slate-50 to-slate-100 p-4 border border-emerald-100">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
                Tổng thanh toán dự kiến
              </span>
              <p className="text-[11px] text-slate-400">Đã bao gồm thuế và các phụ phí</p>
            </div>
            <div className="text-right">
              <span className="text-xl font-extrabold text-emerald-700">
                {formatCurrency(financials?.totalAmount ?? 0)}
              </span>
            </div>
          </div>
        </div>

        {/* Payment History / Status */}
        {financials?.payments && financials.payments.length > 0 ? (
          <div className="mt-4 pt-3 border-t border-slate-100 space-y-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Lịch sử giao dịch thanh toán
            </span>
            {financials.payments.map((p) => (
              <div
                key={p.id}
                className="flex items-center justify-between text-xs rounded border border-slate-200 bg-white p-2.5"
              >
                <div>
                  <span className="font-semibold text-slate-800">
                    {p.paymentMethod || 'Cổng thanh toán'}
                  </span>
                  <span className="text-slate-400 block text-[11px]">
                    Mã GD: {p.transactionRef || `#${p.id}`}
                  </span>
                </div>
                <div className="text-right">
                  <span className="font-bold text-slate-900">{formatCurrency(p.amount)}</span>
                  <span
                    className={`block text-[10px] font-bold ${
                      p.status === 'PAID' ? 'text-emerald-600' : 'text-amber-600'
                    }`}
                  >
                    {p.status === 'PAID' ? 'Đã thanh toán' : p.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-2 text-center text-[11px] text-slate-400">
            Chưa có giao dịch trực tuyến nào được thực hiện.
          </p>
        )}
      </div>
    </div>
  )
}
