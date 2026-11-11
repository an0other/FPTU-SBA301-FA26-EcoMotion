import { useEffect, useState } from 'react'
import { formatBookingWhen } from '../../utils/bookingDateTime'
import { END_CONFIRMATION_METHOD_LABELS, formatCountdown, tripEndWindow } from '../../utils/tripEndWindow'

/**
 * Renter: "Tài xế kết thúc lúc X — Đúng / Không đúng" trong 12 giờ.
 * Provider (readOnly): chỉ hiện trạng thái khách đã chốt giờ kết thúc hay chưa.
 */
export default function TripEndConfirmationBanner({ workflow, readOnly = false, onConfirm, onObject }) {
  const [now, setNow] = useState(() => Date.now())
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const view = tripEndWindow(workflow, now)

  useEffect(() => {
    if (view.phase !== 'WAITING') return undefined
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [view.phase])

  if (view.phase === 'NONE') return null
  const reported = view.reportedAt ? formatBookingWhen(view.reportedAt) : '—'

  if (view.phase === 'CONFIRMED') {
    return (
      <div role="status" className="rounded-[16px] border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
        Giờ kết thúc chuyến đã chốt: <strong>{reported}</strong>
        {view.confirmationMethod ? <span className="text-emerald-800"> · {END_CONFIRMATION_METHOD_LABELS[view.confirmationMethod] || view.confirmationMethod}</span> : null}
      </div>
    )
  }

  if (view.phase === 'DISPUTED') {
    return (
      <div role="status" className="rounded-[16px] border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
        {readOnly ? 'Khách phản đối giờ kết thúc' : 'Bạn đã phản đối giờ kết thúc'} (<strong>{reported}</strong>). Admin đang xác minh; quyết toán tạm dừng đến khi có kết luận.
      </div>
    )
  }

  if (readOnly) {
    return (
      <div role="status" className="rounded-[16px] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        Khách chưa xác nhận giờ kết thúc <strong>{reported}</strong>. Bạn vẫn thêm phụ phí và chốt quyết toán bình thường; đơn sẽ hoàn tất khi khách xác nhận, thanh toán số dư, hoặc tự chốt sau{' '}
        <strong className="tabular-nums">{formatCountdown(view.secondsLeft)}</strong>.
      </div>
    )
  }

  async function handleConfirm() {
    setSubmitting(true)
    setError('')
    try {
      await onConfirm?.()
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Không xác nhận được, vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section aria-label="Xác nhận giờ kết thúc chuyến" className="rounded-[16px] border border-amber-300 bg-amber-50 p-4 shadow-[0_8px_24px_rgba(20,34,26,0.06)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-bold text-amber-950">Tài xế đã kết thúc chuyến lúc {reported}. Có đúng không?</h2>
          <p className="mt-1 text-xs text-amber-900">
            Giờ kết thúc dùng để tính phí quá giờ. Nếu không phản hồi, hệ thống tự chốt sau{' '}
            <strong className="tabular-nums">{view.phase === 'WAITING' ? formatCountdown(view.secondsLeft) : 'ít phút nữa'}</strong>.
            Thanh toán số dư cũng được xem là đồng ý.
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button type="button" onClick={onObject} disabled={submitting}
            className="rounded-xl border border-red-300 bg-white px-4 py-2 text-sm font-semibold text-red-700 disabled:opacity-50">
            Không đúng
          </button>
          <button type="button" onClick={handleConfirm} disabled={submitting}
            className="rounded-xl bg-brand-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {submitting ? 'Đang xác nhận…' : 'Đúng'}
          </button>
        </div>
      </div>
      {error ? <p role="alert" className="mt-2 text-xs font-semibold text-red-700">{error}</p> : null}
    </section>
  )
}
