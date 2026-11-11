import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import AppHeader from '../components/AppHeader'
import ReviewPhotoDialog from '../components/reviews/ReviewPhotoDialog'
import { reviewService } from '../services/reviewService'
import { formatBookingWhen } from '../utils/bookingDateTime'
import { initials, reviewPhotos, reviewGallery, selectReviews } from '../utils/reviewPresentation'

function Stars({ value = 0, large = false }) {
  const rating = Math.max(0, Math.min(5, Number(value) || 0))
  return <span className={`relative inline-block whitespace-nowrap leading-none ${large ? 'text-2xl' : 'text-base'}`} role="img" aria-label={`${rating} trên 5 sao`}>
    <span className="text-slate-200" aria-hidden="true">★★★★★</span>
    <span className="absolute inset-y-0 left-0 overflow-hidden whitespace-nowrap text-amber-500" style={{ width: `${rating / 5 * 100}%` }} aria-hidden="true">★★★★★</span>
  </span>
}

function Avatar({ src, name, vehicle = false }) {
  const [failed, setFailed] = useState(false)
  return src && !failed ? <img src={src} alt={name} onError={() => setFailed(true)} className={`${vehicle ? 'h-24 w-36 rounded-xl' : 'h-20 w-20 rounded-full'} object-cover ring-4 ring-emerald-50`} />
    : <span className="flex h-20 w-20 items-center justify-center rounded-full bg-emerald-100 text-2xl font-bold text-emerald-800" aria-label={name}>{initials(name)}</span>
}

function Thumbnail({ url, onClick, label, overlay }) {
  const [failed, setFailed] = useState(false)
  return <button type="button" onClick={onClick} aria-label={label} className="relative aspect-square w-24 shrink-0 overflow-hidden rounded-xl border border-slate-200 bg-slate-100 text-xs focus-visible:outline-2 focus-visible:outline-emerald-700 sm:w-28">
    {failed ? <span className="px-2 text-slate-500">Ảnh không khả dụng</span> : <img src={url} alt="" loading="lazy" onError={() => setFailed(true)} className="h-full w-full object-cover transition-transform hover:scale-105" />}
    {overlay && <span className="absolute inset-0 flex items-center justify-center bg-black/55 text-lg font-bold text-white">{overlay}</span>}
  </button>
}

const DEFAULT_FILTERS = { stars: 0, photos: false, query: '', order: 'newest' }

export default function PublicReviewsPage({ kind }) {
  const { id } = useParams()
  return <ReviewsContent key={`${kind}:${id}`} kind={kind} id={id} />
}

function ReviewsContent({ kind, id }) {
  const navigate = useNavigate()
  const [state, setState] = useState({ loading: true, data: null, error: '' })
  const [retry, setRetry] = useState(0)
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [visible, setVisible] = useState(10)
  const [selected, setSelected] = useState(null)
  useEffect(() => {
    let cancelled = false
    const request = kind === 'driver' ? reviewService.getDriverReviews(id) : reviewService.getVehicleReviews(id)
    request.then((data) => { if (!cancelled) setState({ loading: false, data, error: '' }) })
      .catch((error) => { if (!cancelled) setState({ loading: false, data: null, error: error.message || 'Không tải được đánh giá.' }) })
    return () => { cancelled = true }
  }, [id, kind, retry])
  function update(change) { setFilters((previous) => ({ ...previous, ...change })); setVisible(10) }
  function reset() { setFilters(DEFAULT_FILTERS); setVisible(10) }
  const { loading, data, error } = state
  const all = data?.items || []
  const gallery = reviewGallery(all)
  const items = selectReviews(all, filters)
  const withImages = all.filter((item) => reviewPhotos(item).length).length
  const count = Number(data?.reviewCount) || 0
  const average = Number(data?.averageRating) || 0
  const title = kind === 'driver' ? 'Đánh giá tài xế' : 'Đánh giá xe'
  const openPhotos = (photos, index) => setSelected({ photos, index })
  return <div className="min-h-screen bg-slate-50">
    <AppHeader />
    <main className="mx-auto max-w-[1120px] space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <nav aria-label="Đường dẫn trang" className="flex flex-wrap items-center gap-2 text-sm text-slate-500">
        <button type="button" onClick={() => navigate(-1)} className="font-semibold text-emerald-800 hover:underline">← Quay lại</button>
        {kind === 'vehicle' && <><span>/</span><Link to={`/vehicles/${id}`} className="hover:text-emerald-800">Chi tiết xe</Link></>}
        <span>/</span><span aria-current="page">{title}</span>
      </nav>
      {loading && <section role="status" className="animate-pulse rounded-2xl border bg-white p-10 text-slate-500">Đang tải thông tin và đánh giá…</section>}
      {error && <section role="alert" className="rounded-2xl border border-rose-200 bg-white p-6"><p className="text-rose-700">{error}</p>
        <button type="button" onClick={() => { setState({ loading: true, data: null, error: '' }); setRetry((n) => n + 1) }} className="mt-3 font-semibold text-emerald-800 underline">Thử lại</button></section>}
      {data && <>
        <section aria-label="Tổng quan đánh giá" className="grid gap-6 rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7 md:grid-cols-[1fr_1fr]">
          <div className="flex flex-col gap-5 md:border-r md:border-slate-100 md:pr-7">
            <div className="flex items-center gap-5"><Avatar key={data.targetImageUrl || 'fallback'} src={data.targetImageUrl} name={data.targetName || title} vehicle={kind === 'vehicle'} />
              <div><p className="mb-1 text-xs font-semibold uppercase tracking-wider text-emerald-700">{title}</p><h1 className="text-xl font-bold text-slate-900 sm:text-2xl">{data.targetName || title}</h1></div></div>
            <div className="flex items-center gap-5"><p className="text-5xl font-bold tabular-nums text-slate-900">{count ? average.toFixed(1) : '—'}<span className="ml-1 text-base font-normal text-slate-400">/ 5</span></p>
              <div><Stars value={count ? average : 0} large /><p className="mt-2 text-sm text-slate-500">{count ? `${count} lượt đánh giá` : 'Chưa có lượt đánh giá'}</p></div></div>
            <p className="text-sm text-slate-500">Nhận xét và hình ảnh khách thuê chia sẻ về trải nghiệm chuyến đi.</p>
          </div>
          <div className="self-center space-y-3" aria-label="Phân bố số sao">
            {[5, 4, 3, 2, 1].map((star) => {
              const n = Number(data.distribution?.[star - 1]) || 0
              return <button key={star} type="button" aria-label={`Lọc ${star} sao, ${n} đánh giá`} aria-pressed={filters.stars === star} onClick={() => update({ stars: filters.stars === star ? 0 : star })} className="flex w-full items-center gap-3 rounded-lg px-1 py-1 text-sm focus-visible:outline-2 focus-visible:outline-emerald-700">
                <span className="w-10 shrink-0 text-slate-600">{star} sao</span><span className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100"><span className="block h-full rounded-full bg-amber-400" style={{ width: `${count ? Math.min(100, n / count * 100) : 0}%` }} /></span><span className="w-8 text-right tabular-nums text-slate-500">{n}</span>
              </button>
            })}
          </div>
        </section>
        {gallery.length > 0 && <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <div className="mb-4 flex items-center justify-between gap-3"><h2 className="font-semibold text-slate-900">Hình ảnh từ khách thuê <span className="font-normal text-slate-500">({gallery.length})</span></h2><button type="button" onClick={() => openPhotos(gallery, 0)} className="text-sm font-semibold text-emerald-800 hover:underline">Xem tất cả ảnh</button></div>
          <div className="flex gap-3 overflow-x-auto pb-2">{gallery.slice(0, 8).map((photo, index) => <Thumbnail key={photo.url} url={photo.url} label={`Xem ảnh khách chia sẻ ${index + 1}`} onClick={() => openPhotos(gallery, index)} overlay={index === 7 && gallery.length > 8 ? `+${gallery.length - 8}` : null} />)}</div>
        </section>}
        <section aria-label="Lọc đánh giá" className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 sm:p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end"><label className="flex-1 text-sm text-slate-600">Tìm trong nhận xét<input type="search" value={filters.query} onChange={(e) => update({ query: e.target.value })} placeholder="Ví dụ: đúng giờ, an toàn…" className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-slate-900 focus:outline-emerald-700" /></label>
            <label className="text-sm text-slate-600">Sắp xếp<select value={filters.order} onChange={(e) => update({ order: e.target.value })} className="mt-1 block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-slate-900"><option value="newest">Mới nhất</option><option value="oldest">Cũ nhất</option><option value="highest">Điểm cao nhất</option><option value="lowest">Điểm thấp nhất</option></select></label></div>
          <div className="flex gap-2 overflow-x-auto pb-1">{[0, 5, 4, 3, 2, 1].map((star) => <button key={star} type="button" aria-pressed={filters.stars === star} onClick={() => update({ stars: star })} className={`shrink-0 rounded-full border px-3 py-2 text-sm ${filters.stars === star ? 'border-emerald-700 bg-emerald-50 font-semibold text-emerald-900' : 'border-slate-200 text-slate-600'}`}>{star ? `${star} sao (${all.filter((item) => Number(item.rating) === star).length})` : `Tất cả (${all.length})`}</button>)}
            <button type="button" aria-pressed={filters.photos} onClick={() => update({ photos: !filters.photos })} className={`shrink-0 rounded-full border px-3 py-2 text-sm ${filters.photos ? 'border-emerald-700 bg-emerald-50 text-emerald-900' : 'border-slate-200 text-slate-600'}`}>Có hình ảnh ({withImages})</button></div>
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500"><p aria-live="polite">Hiển thị {Math.min(visible, items.length)} / {items.length} nhận xét phù hợp trong {all.length} nhận xét đã tải.</p><button type="button" onClick={reset} className="font-semibold text-emerald-800 hover:underline">Bỏ bộ lọc</button></div>
          {count > all.length && <p className="rounded-lg bg-amber-50 p-3 text-xs text-amber-900">Điểm tổng hợp tính trên {count} lượt. API hiện trả tối đa 100 nhận xét gần nhất; tìm kiếm, bộ lọc, ảnh và sắp xếp chỉ áp dụng cho {all.length} nhận xét đã tải.</p>}
        </section>
        {!items.length ? <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center"><h2 className="font-semibold text-slate-800">{count ? 'Không có nhận xét phù hợp' : 'Chưa có đánh giá nào'}</h2><p className="mt-2 text-sm text-slate-500">{count ? 'Thử bỏ bộ lọc hoặc tìm kiếm bằng từ khóa khác.' : 'Nhận xét sẽ xuất hiện khi khách thuê chia sẻ đánh giá.'}</p>{count > 0 && <button type="button" onClick={reset} className="mt-4 font-semibold text-emerald-800 underline">Xem các nhận xét đã tải</button>}</section>
          : <ul className="space-y-4">{items.slice(0, visible).map((item) => {
            const photos = reviewPhotos(item).map((url) => ({ url, reviewerName: item.reviewerName || 'Khách thuê' }))
            return <li key={item.id}><article className="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6"><header className="flex flex-wrap items-start justify-between gap-3"><div className="flex items-center gap-3"><span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-sm font-bold text-emerald-800">{initials(item.reviewerName)}</span><div><h3 className="text-sm font-semibold text-slate-900">{item.reviewerName || 'Khách thuê'}</h3><div className="mt-1"><Stars value={item.rating} /></div></div></div><time dateTime={item.createdAt || undefined} className="text-xs text-slate-500">{formatBookingWhen(item.createdAt)}</time></header>
              <p className="mt-4 whitespace-pre-wrap break-words text-sm leading-7 text-slate-700">{item.comment || 'Khách thuê chỉ chấm điểm, không để lại nhận xét.'}</p>
              {!!photos.length && <div className="mt-4 flex gap-2 overflow-x-auto pb-1">{photos.map((photo, index) => <Thumbnail key={photo.url} url={photo.url} label={`Xem ảnh ${index + 1} trong đánh giá của ${photo.reviewerName}`} onClick={() => openPhotos(photos, index)} />)}</div>}
            </article></li>
          })}</ul>}
        {items.length > visible && <div className="text-center"><button type="button" onClick={() => setVisible((n) => n + 10)} className="rounded-xl border border-emerald-700 bg-white px-6 py-3 text-sm font-semibold text-emerald-900">Xem thêm nhận xét ({items.length - visible})</button></div>}
      </>}
    </main>
    {selected && <ReviewPhotoDialog photos={selected.photos} initialIndex={selected.index} onClose={() => setSelected(null)} />}
  </div>
}
