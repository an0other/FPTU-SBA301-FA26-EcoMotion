import { useEffect, useRef, useState } from 'react'

function Photo({ src, alt }) {
  const [failed, setFailed] = useState(false)
  return failed ? <p role="status" className="p-16 text-center text-white">Không tải được ảnh. Bạn có thể chuyển sang ảnh khác.</p>
    : <img src={src} alt={alt} onError={() => setFailed(true)} className="mx-auto max-h-[65vh] w-full object-contain" />
}

export default function ReviewPhotoDialog({ photos, initialIndex = 0, onClose }) {
  const dialog = useRef(null)
  const [index, setIndex] = useState(initialIndex)
  const move = (step) => setIndex((current) => (current + step + photos.length) % photos.length)
  useEffect(() => {
    const element = dialog.current
    const previous = document.body.style.overflow
    element.showModal()
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previous; element.close() }
  }, [])
  const current = photos[index]
  return <dialog ref={dialog} aria-label="Ảnh trong đánh giá" onCancel={onClose}
    onClick={(event) => { if (event.target === event.currentTarget) onClose() }}
    onKeyDown={(event) => {
      if (event.key === 'ArrowRight') { event.preventDefault(); move(1) }
      if (event.key === 'ArrowLeft') { event.preventDefault(); move(-1) }
    }} className="m-auto max-h-[95dvh] w-[min(96vw,1024px)] rounded-2xl bg-slate-950 p-0 text-white shadow-2xl backdrop:bg-slate-950/80">
    <header className="flex items-center justify-between gap-4 border-b border-white/15 p-4">
      <div><h2 className="font-semibold">Ảnh khách chia sẻ</h2><p className="text-xs text-slate-300">{current.reviewerName} · {index + 1}/{photos.length}</p></div>
      <button type="button" onClick={onClose} autoFocus className="rounded-lg border border-white/30 px-3 py-2 text-sm">Đóng</button>
    </header>
    <div className="p-3 sm:p-6"><Photo key={current.url} src={current.url} alt={`Ảnh đánh giá ${index + 1} của ${current.reviewerName}`} /></div>
    <footer className="flex items-center justify-between border-t border-white/15 p-4">
      <button type="button" disabled={photos.length < 2} onClick={() => move(-1)} aria-label="Ảnh trước" className="rounded-lg border border-white/30 px-4 py-2 disabled:opacity-40">← Trước</button>
      <span className="text-xs text-slate-300" aria-live="polite">{index + 1} / {photos.length}</span>
      <button type="button" disabled={photos.length < 2} onClick={() => move(1)} aria-label="Ảnh tiếp theo" className="rounded-lg border border-white/30 px-4 py-2 disabled:opacity-40">Sau →</button>
    </footer>
  </dialog>
}
