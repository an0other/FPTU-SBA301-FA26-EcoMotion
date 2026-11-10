import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import { reviewService } from '../services/reviewService'
import { formatBookingWhen } from '../utils/bookingDateTime'

const STAR_SIZES = { 'text-sm': 16, 'text-base': 18, 'text-xl': 24 }

function Stars({ value, size = 'text-base' }) {
  return (
    <span className="inline-flex" aria-label={`${value} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={`material-symbols-rounded leading-none ${n <= Math.round(value) ? 'text-amber-500' : 'text-slate-300'}`}
          style={{ fontSize: STAR_SIZES[size] || 18, fontVariationSettings: "'FILL' 1" }} aria-hidden="true">star</span>
      ))}
    </span>
  )
}

const STAR_FILTERS = [0, 5, 4, 3, 2, 1]

/** Trang đánh giá công khai của một xe (/vehicles/:id/reviews) hoặc một tài xế (/drivers/:id/reviews). */
export default function PublicReviewsPage({ kind }) {
  const { id } = useParams()
  const navigate = useNavigate()
  const [state, setState] = useState({ loading: true, data: null, error: '' })
  const [starFilter, setStarFilter] = useState(0)
  const [withPhotos, setWithPhotos] = useState(false)

  useEffect(() => {
    let cancelled = false
    const load = kind === 'driver' ? reviewService.getDriverReviews(id) : reviewService.getVehicleReviews(id)
    load
      .then((data) => { if (!cancelled) setState({ loading: false, data, error: '' }) })
      .catch((err) => { if (!cancelled) setState({ loading: false, data: null, error: err.message || 'Không tải được đánh giá.' }) })
    return () => { cancelled = true }
  }, [id, kind])

  const { loading, data, error } = state
  const title = kind === 'driver' ? 'Đánh giá tài xế' : 'Đánh giá xe'
  const items = (data?.items || [])
    .filter((item) => !starFilter || item.rating === starFilter)
    .filter((item) => !withPhotos || item.mediaUrls?.length)
  const maxCount = Math.max(1, ...(data?.distribution || [0]))

  return (
    <div className="min-h-screen bg-surface">
      <AppHeader />
      <main className="mx-auto max-w-[960px] space-y-6 px-4 py-8 sm:px-6">
        <nav aria-label="Đường dẫn trang" className="flex flex-wrap items-center gap-2 text-xs">
          <button type="button" onClick={() => navigate(-1)} className="font-semibold text-brand-700 hover:underline">← Quay lại</button>
          {kind === 'vehicle' && <><span className="text-slate-400">/</span><Link to={`/vehicles/${id}`} className="text-slate-500 hover:text-brand-700">Chi tiết xe</Link></>}
          <span className="text-slate-400">/</span><span className="text-slate-700">{title}</span>
        </nav>

        {loading && <p className="text-sm text-slate-600">Đang tải đánh giá...</p>}
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

        {data && (
          <>
            <section className="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:grid-cols-[220px_1fr]">
              <div className="flex flex-col items-center justify-center gap-2 text-center sm:border-r sm:border-slate-100 sm:pr-6">
                {data.targetImageUrl && <img src={data.targetImageUrl} alt="" className="h-16 w-16 rounded-full object-cover" />}
                <h1 className="text-base font-bold text-ink">{data.targetName}</h1>
                <p className="text-4xl font-extrabold tabular-nums text-ink">{data.reviewCount ? data.averageRating.toFixed(1) : '—'}</p>
                <Stars value={data.averageRating} size="text-xl" />
                <p className="text-xs text-slate-500">{data.reviewCount} đánh giá</p>
              </div>
              <ul className="space-y-2 self-center" aria-label="Phân bố số sao">
                {[5, 4, 3, 2, 1].map((star) => {
                  const count = data.distribution?.[star - 1] ?? 0
                  return (
                    <li key={star} className="flex items-center gap-3 text-xs">
                      <span className="w-10 shrink-0 text-slate-600">{star} sao</span>
                      <span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                        <span className="block h-full rounded-full bg-amber-400" style={{ width: `${(count / maxCount) * 100}%` }} />
                      </span>
                      <span className="w-8 shrink-0 text-right tabular-nums text-slate-600">{count}</span>
                    </li>
                  )
                })}
              </ul>
            </section>

            <div className="flex flex-wrap items-center gap-2">
              {STAR_FILTERS.map((star) => (
                <button key={star} type="button" onClick={() => setStarFilter(star)}
                  className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${starFilter === star ? 'border-brand-600 bg-brand-50 text-brand-800' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}>
                  {star ? `${star} sao` : 'Tất cả'}
                </button>
              ))}
              <label className="ml-auto flex items-center gap-1.5 text-xs text-slate-600">
                <input type="checkbox" checked={withPhotos} onChange={(e) => setWithPhotos(e.target.checked)} /> Có hình ảnh
              </label>
            </div>

            {items.length === 0 ? (
              <p className="rounded-2xl border border-dashed border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
                {data.reviewCount ? 'Không có đánh giá phù hợp bộ lọc.' : 'Chưa có đánh giá nào.'}
              </p>
            ) : (
              <ul className="space-y-3">
                {items.map((item) => (
                  <li key={item.id} className="rounded-2xl border border-slate-200 bg-white p-5">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-ink">{item.reviewerName}</span>
                        <Stars value={item.rating} size="text-sm" />
                      </span>
                      <span className="text-xs text-slate-500">{formatBookingWhen(item.createdAt)}</span>
                    </div>
                    {item.comment && <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{item.comment}</p>}
                    {item.mediaUrls?.length > 0 && (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {item.mediaUrls.map((url) => (
                          <a key={url} href={url} target="_blank" rel="noreferrer">
                            <img src={url} alt="Ảnh đánh giá" className="h-20 w-20 rounded-lg border border-slate-200 object-cover" />
                          </a>
                        ))}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </main>
    </div>
  )
}
