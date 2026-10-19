import { useState } from 'react'
import StarRating from './StarRating'

const MAX_COMMENT_LENGTH = 1000

export default function ReviewForm({
  initialReview,
  busy,
  error,
  onSubmit,
  onCancel,
}) {
  const [rating, setRating] = useState(initialReview?.rating ?? 0)
  const [comment, setComment] = useState(initialReview?.comment ?? '')
  const [validationError, setValidationError] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()

    if (rating < 1 || rating > 5) {
      setValidationError('Vui lòng chọn số sao đánh giá.')
      return
    }

    setValidationError('')

    await onSubmit({
      rating,
      comment: comment.trim() || null,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <StarRating
        value={rating}
        onChange={setRating}
        disabled={busy}
      />

      <label className="block">
        <span className="text-sm font-semibold text-slate-800">
          Nhận xét
          <span className="ml-2 font-normal text-slate-400">
            Không bắt buộc
          </span>
        </span>

        <textarea
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          maxLength={MAX_COMMENT_LENGTH}
          disabled={busy}
          rows={4}
          placeholder="Chia sẻ trải nghiệm thực tế của bạn…"
          className="mt-2 w-full resize-y rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:opacity-60"
        />

        <span className="mt-1 block text-right text-xs text-slate-400">
          {comment.length}/{MAX_COMMENT_LENGTH}
        </span>
      </label>

      {(validationError || error) && (
        <p role="alert" className="text-sm text-on-error-container">
          {validationError || error}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <button
          type="submit"
          disabled={busy}
          className="rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy
            ? 'Đang lưu…'
            : initialReview
              ? 'Lưu thay đổi'
              : 'Gửi đánh giá'}
        </button>

        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
          >
            Hủy chỉnh sửa
          </button>
        )}
      </div>
    </form>
  )
}