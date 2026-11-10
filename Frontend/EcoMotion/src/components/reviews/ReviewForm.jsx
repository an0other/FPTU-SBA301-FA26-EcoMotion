import { useState } from 'react'
import StarRating from './StarRating'

const MAX_COMMENT_LENGTH = 1000
const MAX_IMAGES = 5
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const QUICK_TAGS = {
  VEHICLE: ['Xe sạch sẽ', 'Xe đúng mô tả', 'Máy lạnh tốt', 'Xe êm, vận hành ổn', 'Xe có mùi', 'Xe chưa vệ sinh'],
  DRIVER: ['Đúng giờ', 'Lái xe an toàn', 'Thân thiện, lịch sự', 'Hỗ trợ hành lý', 'Đến trễ', 'Lái xe ẩu'],
}

const RATING_HINTS = ['', 'Rất tệ', 'Chưa tốt', 'Bình thường', 'Tốt', 'Tuyệt vời']

export default function ReviewForm({
  target = 'VEHICLE',
  initialReview,
  busy,
  error,
  onSubmit,
  onCancel,
  onUploadImage,
}) {
  const [rating, setRating] = useState(initialReview?.rating ?? 0)
  const [comment, setComment] = useState(initialReview?.comment ?? '')
  const [images, setImages] = useState(() => (initialReview?.mediaUrls ?? []).map((url) => ({ url, uploading: false })))
  const [validationError, setValidationError] = useState('')
  const uploading = images.some((image) => image.uploading)

  function toggleTag(tag) {
    setComment((current) => {
      if (current.includes(tag)) {
        return current.replace(new RegExp(`(^|[,.]\\s*)${tag.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`), '').replace(/^[,.\s]+/, '').trim()
      }
      const base = current.trim()
      return (base ? `${base}${/[.,!?]$/.test(base) ? ' ' : ', '}` : '') + tag
    })
  }

  async function handleFiles(fileList) {
    const files = Array.from(fileList || [])
    if (!files.length || !onUploadImage) return
    const room = MAX_IMAGES - images.length
    if (room <= 0) {
      setValidationError(`Tối đa ${MAX_IMAGES} ảnh cho mỗi đánh giá.`)
      return
    }
    setValidationError('')
    for (const file of files.slice(0, room)) {
      if (!ACCEPTED_TYPES.includes(file.type)) {
        setValidationError('Chỉ chấp nhận ảnh JPG, PNG hoặc WebP.')
        continue
      }
      if (file.size > MAX_IMAGE_BYTES) {
        setValidationError('Mỗi ảnh tối đa 5 MB.')
        continue
      }
      const preview = URL.createObjectURL(file)
      setImages((current) => [...current, { preview, uploading: true }])
      try {
        const url = await onUploadImage(file)
        setImages((current) => current.map((image) => (image.preview === preview ? { url, preview, uploading: false } : image)))
      } catch (err) {
        URL.revokeObjectURL(preview)
        setImages((current) => current.filter((image) => image.preview !== preview))
        setValidationError(err?.response?.data?.message || err?.message || 'Tải ảnh thất bại.')
      }
    }
  }

  function removeImage(index) {
    setImages((current) => {
      const removed = current[index]
      if (removed?.preview) URL.revokeObjectURL(removed.preview)
      return current.filter((_, i) => i !== index)
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (rating < 1 || rating > 5) {
      setValidationError('Vui lòng chọn số sao đánh giá.')
      return
    }
    if (uploading) {
      setValidationError('Đang tải ảnh, vui lòng đợi trong giây lát.')
      return
    }
    setValidationError('')
    await onSubmit({
      rating,
      comment: comment.trim() || null,
      mediaUrls: images.map((image) => image.url).filter(Boolean),
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <StarRating value={rating} onChange={setRating} disabled={busy} />
        {rating > 0 ? <span className="text-sm font-semibold text-amber-700">{RATING_HINTS[rating]}</span> : null}
      </div>

      <div>
        <p className="text-sm font-semibold text-slate-800">Gợi ý nhanh</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {(QUICK_TAGS[target] || QUICK_TAGS.VEHICLE).map((tag) => {
            const active = comment.includes(tag)
            return (
              <button
                key={tag}
                type="button"
                disabled={busy}
                aria-pressed={active}
                onClick={() => toggleTag(tag)}
                className={`rounded-full border px-3 py-1 text-xs font-semibold transition ${
                  active ? 'border-brand-600 bg-brand-50 text-brand-800' : 'border-slate-200 text-slate-600 hover:border-slate-300'
                }`}
              >
                {tag}
              </button>
            )
          })}
        </div>
      </div>

      <label className="block">
        <span className="text-sm font-semibold text-slate-800">
          Nhận xét
          <span className="ml-2 font-normal text-slate-400">Không bắt buộc</span>
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
        <span className="mt-1 flex justify-between text-xs text-slate-400">
          <span>Vui lòng dùng ngôn từ lịch sự; nội dung xúc phạm sẽ không được đăng.</span>
          <span>{comment.length}/{MAX_COMMENT_LENGTH}</span>
        </span>
      </label>

      {onUploadImage && (
        <div>
          <p className="text-sm font-semibold text-slate-800">
            Ảnh minh hoạ <span className="ml-1 font-normal text-slate-400">Tối đa {MAX_IMAGES} ảnh, JPG/PNG/WebP ≤ 5 MB</span>
          </p>
          <div className="mt-2 flex flex-wrap gap-3">
            {images.map((image, index) => (
              <div key={image.url || image.preview} className="relative h-20 w-20 overflow-hidden rounded-lg border border-slate-200">
                <img src={image.preview || image.url} alt={`Ảnh đánh giá ${index + 1}`} className="h-full w-full object-cover" />
                {image.uploading ? (
                  <span className="absolute inset-0 flex items-center justify-center bg-white/70 text-[11px] font-semibold text-slate-700">Đang tải…</span>
                ) : (
                  <button
                    type="button"
                    onClick={() => removeImage(index)}
                    disabled={busy}
                    aria-label={`Xoá ảnh ${index + 1}`}
                    className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-xs text-white"
                  >
                    ×
                  </button>
                )}
              </div>
            ))}
            {images.length < MAX_IMAGES && (
              <label className="flex h-20 w-20 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 text-xs font-semibold text-slate-500 hover:border-brand-500 hover:text-brand-700">
                <span className="text-lg leading-none">+</span>
                Thêm ảnh
                <input
                  type="file"
                  accept={ACCEPTED_TYPES.join(',')}
                  multiple
                  disabled={busy}
                  className="sr-only"
                  onChange={(event) => {
                    handleFiles(event.target.files)
                    event.target.value = ''
                  }}
                />
              </label>
            )}
          </div>
        </div>
      )}

      {(validationError || error) && (
        <p role="alert" className="text-sm text-on-error-container">
          {validationError || error}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <button
          type="submit"
          disabled={busy || uploading}
          className="rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy ? 'Đang lưu…' : initialReview ? 'Lưu thay đổi' : 'Gửi đánh giá'}
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
