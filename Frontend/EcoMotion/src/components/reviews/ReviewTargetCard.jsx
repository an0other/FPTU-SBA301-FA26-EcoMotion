import { useState } from 'react'
import ReviewForm from './ReviewForm'
import { formatPaymentDate } from '../../utils/paymentDisplay'

export default function ReviewTargetCard({
  target,
  name,
  review,
  canReview,
  restrictionReason,
  busy,
  error,
  onSave,
  onDelete,
}) {
  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const title = target === 'VEHICLE' ? 'Đánh giá xe' : 'Đánh giá tài xế'

  return (
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card">
      <div className="border-b border-slate-100 p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold text-slate-900">{title}</h2>

          {review && (
            <span className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-800">
              Đã đánh giá
            </span>
          )}
        </div>

        <p className="mt-1 text-sm text-slate-500">{name}</p>
      </div>

      <div className="p-5 sm:p-6">
        {!canReview && (
          <p className="mb-4 rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
            {restrictionReason || 'Đơn thuê chưa đủ điều kiện đánh giá.'}
          </p>
        )}

        {review && !editing ? (
          <div>
            <p
              aria-label={`${review.rating} trên 5 sao`}
              className="text-2xl tracking-wider text-amber-500"
            >
              {'★'.repeat(review.rating)}
              <span aria-hidden="true" className="text-slate-200">
                {'★'.repeat(5 - review.rating)}
              </span>
            </p>

            <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-700">
              {review.comment || 'Bạn chưa để lại nhận xét.'}
            </p>

            <p className="mt-3 text-xs text-slate-400">
              Đã tạo: {formatPaymentDate(review.createdAt)}
            </p>

            {error && (
              <p role="alert" className="mt-4 text-sm text-on-error-container">
                {error}
              </p>
            )}

            {canReview && (
              <>
                <div className="mt-5 flex gap-3">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setConfirmingDelete(false)
                      setEditing(true)
                    }}
                    className="rounded-lg border border-brand-200 px-4 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-50"
                  >
                    Chỉnh sửa
                  </button>

                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => setConfirmingDelete(true)}
                    className="rounded-lg border border-red-200 px-4 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
                  >
                    Xóa đánh giá
                  </button>
                </div>

                {confirmingDelete && (
                  <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4">
                    <p className="text-sm text-red-900">
                      Bạn muốn xóa đánh giá này? Sau khi xóa, bạn có thể đánh giá lại.
                    </p>

                    <div className="mt-3 flex gap-3">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onDelete(target)}
                        className="rounded-lg bg-red-600 px-4 py-2 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                      >
                        {busy ? 'Đang xử lý…' : 'Xác nhận xóa'}
                      </button>

                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => setConfirmingDelete(false)}
                        className="rounded-lg border border-red-200 px-4 py-2 text-xs font-semibold text-red-800 disabled:opacity-50"
                      >
                        Giữ lại
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        ) : canReview ? (
          <ReviewForm
            initialReview={review}
            busy={busy}
            error={error}
            onSubmit={(values) => onSave(target, values, Boolean(review))}
            onCancel={editing ? () => setEditing(false) : undefined}
          />
        ) : (
          <p className="text-sm text-slate-500">
            Chưa có đánh giá cho đối tượng này.
          </p>
        )}
      </div>
    </section>
  )
}