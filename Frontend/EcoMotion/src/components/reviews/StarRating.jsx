import { useId } from 'react'

const RATING_LABELS = {
  1: 'Rất không hài lòng',
  2: 'Không hài lòng',
  3: 'Bình thường',
  4: 'Hài lòng',
  5: 'Rất hài lòng',
}

export default function StarRating({
  value,
  onChange,
  disabled = false,
}) {
  const name = useId()

  return (
    <fieldset disabled={disabled}>
      <legend className="text-sm font-semibold text-slate-800">
        Mức độ hài lòng
      </legend>

      <div className="mt-3 flex items-center gap-2">
        {[1, 2, 3, 4, 5].map((rating) => (
          <label key={rating} className="relative cursor-pointer">
            <input
              type="radio"
              name={name}
              value={rating}
              checked={value === rating}
              onChange={() => onChange(rating)}
              className="peer sr-only"
              required
            />

            <span
              aria-hidden="true"
              className={`flex h-11 w-11 items-center justify-center rounded-lg text-3xl transition peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-600 ${
                rating <= value
                  ? 'bg-amber-50 text-amber-500'
                  : 'bg-slate-50 text-slate-300'
              } ${disabled ? 'cursor-not-allowed opacity-60' : 'hover:bg-amber-50'}`}
            >
              ★
            </span>

            <span className="sr-only">
              {rating} sao — {RATING_LABELS[rating]}
            </span>
          </label>
        ))}
      </div>

      <p aria-live="polite" className="mt-2 min-h-5 text-xs text-slate-500">
        {value ? RATING_LABELS[value] : 'Chọn từ 1 đến 5 sao'}
      </p>
    </fieldset>
  )
}