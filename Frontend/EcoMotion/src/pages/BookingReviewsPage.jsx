import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import ReviewTargetCard from '../components/reviews/ReviewTargetCard'
import { useBookingReviews } from '../hooks/useBookingReviews'
import { reviewService } from '../services/reviewService'
import { BOOKING_STATUS_LABELS } from '../utils/paymentDisplay'

export default function BookingReviewsPage() {
  const { bookingCode } = useParams()
  const { data, loading, error, reload } = useBookingReviews(bookingCode)

  const operationInFlight = useRef(false)
  const [busyTarget, setBusyTarget] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [message, setMessage] = useState('')

  async function handleSave(target, values, updating) {
    if (operationInFlight.current) return

    operationInFlight.current = true
    setBusyTarget(target)
    setActionError(null)
    setMessage('')

    try {
      if (updating) {
        await reviewService.updateReview(bookingCode, target, values)
      } else {
        await reviewService.createReview(bookingCode, {
          target,
          ...values,
        })
      }

      setMessage(updating ? 'Đã cập nhật đánh giá.' : 'Đã gửi đánh giá.')
      reload()
    } catch (error) {
      setActionError({
        target,
        message: error.message || 'Không lưu được đánh giá.',
      })
    } finally {
      operationInFlight.current = false
      setBusyTarget(null)
    }
  }

  async function handleDelete(target) {
    if (operationInFlight.current) return

    operationInFlight.current = true
    setBusyTarget(target)
    setActionError(null)
    setMessage('')

    try {
      await reviewService.deleteReview(bookingCode, target)
      setMessage('Đã xóa đánh giá.')
      reload()
    } catch (error) {
      setActionError({
        target,
        message: error.message || 'Không xóa được đánh giá.',
      })
    } finally {
      operationInFlight.current = false
      setBusyTarget(null)
    }
  }

  const reviews = data?.reviews ?? []

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader />

      <main className="mx-auto max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <nav aria-label="Đường dẫn trang" className="flex flex-wrap gap-2 text-xs">
          <Link to="/" className="text-slate-500 hover:text-brand-700">
            Trang chủ
          </Link>
          <span aria-hidden="true" className="text-slate-300">/</span>
          <span aria-current="page" className="font-semibold text-slate-800">
            Đánh giá đơn thuê
          </span>
        </nav>

        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Đánh giá trải nghiệm
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Đơn thuê <span className="font-semibold">{bookingCode}</span>
          </p>
        </div>

        {message && (
          <p
            role="status"
            className="rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm font-medium text-brand-900"
          >
            {message}
          </p>
        )}

        {loading && (
          <div role="status" className="grid gap-6 md:grid-cols-2">
            <span className="sr-only">Đang tải thông tin đánh giá…</span>
            {[1, 2].map((item) => (
              <div
                key={item}
                className="h-80 animate-pulse rounded-xl bg-slate-200"
              />
            ))}
          </div>
        )}

        {!loading && error && (
          <div
            role="alert"
            className="rounded-xl border border-error-container bg-white p-8"
          >
            <p className="text-sm text-on-error-container">{error}</p>
            <button
              type="button"
              onClick={reload}
              className="mt-4 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white"
            >
              Thử lại
            </button>
          </div>
        )}

        {!loading && !error && data && (
          <>
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
              <p className="font-semibold text-slate-900">{data.vehicleName}</p>
              <p className="mt-1 text-xs text-slate-500">
                {BOOKING_STATUS_LABELS[data.bookingStatus] ?? data.bookingStatus}
              </p>
            </div>

            <div className={`grid gap-6 ${data.hasDriver ? 'md:grid-cols-2' : ''}`}>
              {['VEHICLE', ...(data.hasDriver ? ['DRIVER'] : [])].map((target) => {
                const review = reviews.find((item) => item.target === target)

                return (
                  <ReviewTargetCard
                    key={`${bookingCode}-${target}-${review?.rating ?? 'new'}-${review?.comment ?? ''}`}
                    target={target}
                    name={
                      target === 'VEHICLE'
                        ? data.vehicleName
                        : data.driverName || 'Tài xế của chuyến'
                    }
                    review={review}
                    canReview={data.canReview}
                    restrictionReason={data.restrictionReason}
                    busy={busyTarget !== null}
                    error={actionError?.target === target ? actionError.message : ''}
                    onSave={handleSave}
                    onDelete={handleDelete}
                  />
                )
              })}
            </div>
          </>
        )}
      </main>
    </div>
  )
}